package com.github.beemerwt.essence.data;

import com.github.beemerwt.essence.data.model.PlayerStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SqlitePlayerStore implements PlayerStore, Closeable {

    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("Essence");
    private final Path dbPath = dir.resolve("players.db");
    private Connection conn;

    // Hot cache for active players
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public SqlitePlayerStore() {
        try {
            Files.createDirectories(dir);
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initPragmas(conn);
            createSchema(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init SqlitePlayerStore", e);
        }
    }

    private static void initPragmas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA temp_store=MEMORY");
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS players(
                  uuid TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  updated_at INTEGER NOT NULL
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name)");
        }
    }

    // ---------- PlayerStore interface ----------

    @Override
    public synchronized PlayerData get(ServerPlayerEntity player) {
        final UUID id = player.getUuid();
        final String name = player.getName().getString();
        try {
            ensurePlayerRow(id, name);
        } catch (SQLException e) {
            throw new RuntimeException("ensurePlayerRow failed", e);
        }
        return loadIntoCache(id, name);
    }

    @Override
    public synchronized PlayerData get(UUID id) {
        PlayerData cached = cache.get(id);
        String nameHint = (cached != null ? cached.name() : id.toString());
        try {
            ensurePlayerRow(id, nameHint);
        } catch (SQLException e) {
            throw new RuntimeException("ensurePlayerRow failed", e);
        }
        return loadIntoCache(id, null); // null => prefer DB name
    }

    @Override
    public List<PlayerData> list() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public synchronized Optional<PlayerData> lookup(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM players WHERE name = ? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID id = UUID.fromString(rs.getString(1));
                return Optional.of(get(id));
            }
        } catch (SQLException e) {
            throw new RuntimeException("lookup failed for name=" + name, e);
        }
    }

    @Override
    public synchronized List<PlayerData> all() {
        List<PlayerData> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name FROM players")) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString(1));
                String nm = rs.getString(2);
                PlayerData pd = new PlayerData(id, nm);
                cache.put(id, pd); // replace cached entry immutably
                out.add(pd);
            }
        } catch (SQLException e) {
            throw new RuntimeException("all(): players scan failed", e);
        }
        return out;
    }

    @Override
    public synchronized int countByPrefix(String prefix) {
        String like = (prefix == null || prefix.isEmpty()) ? "%" : (prefix + "%");
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM players
            WHERE name LIKE ? COLLATE NOCASE
        """))
        {
            ps.setString(1, like);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("countByPrefix failed", e);
        }
    }

    @Override
    public synchronized List<PlayerData> listByPrefix(String prefix, int offset, int limit) {
        String like = (prefix == null || prefix.isEmpty()) ? "%" : (prefix + "%");
        List<PlayerData> out = new ArrayList<>(limit);
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT uuid, name
            FROM players
            WHERE name LIKE ? COLLATE NOCASE
            ORDER BY updated_at DESC
            LIMIT ? OFFSET ?
        """)) {
            ps.setString(1, like);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    String nm = rs.getString(2);
                    PlayerData pd = new PlayerData(id, nm);
                    cache.put(id, pd); // keep cache fresh
                    out.add(pd);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("listByPrefix failed", e);
        }
        return out;
    }

    // ---------- Convenience ----------

    public synchronized void updatePlayerName(UUID id, String newName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET name=?, updated_at=? WHERE uuid=?")) {
            ps.setString(1, newName);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePlayerName failed", e);
        }
        // Replace cached instance with a new immutable object
        cache.compute(id, (k, oldVal) -> new PlayerData(id, newName));
    }

    // ---------- Low-level helpers ----------

    private void ensurePlayerRow(UUID id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players(uuid,name,updated_at) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, updated_at=excluded.updated_at")) {
            ps.setString(1, id.toString());
            ps.setString(2, name != null ? name : id.toString());
            ps.setLong(3, Instant.now().getEpochSecond());
            ps.executeUpdate();
        }
    }

    /** Load from DB into cache; if nameHint != null, prefer it when row is missing. */
    private PlayerData loadIntoCache(UUID id, String nameHint) {
        PlayerData cached = cache.get(id);
        if (cached != null) return cached;

        String name = nameHint;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM players WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) name = rs.getString(1);
                if (name == null) name = id.toString();
            }
        } catch (SQLException e) {
            throw new RuntimeException("loadIntoCache: players fetch failed", e);
        }

        PlayerData pd = new PlayerData(id, name);
        cache.put(id, pd);
        return pd;
    }

    @Override
    public synchronized void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        cache.clear();
    }
}
