package com.github.beemerwt.mcrpg.data;

import com.github.beemerwt.mcrpg.McRPG;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQLite-backed PlayerStore with RAII-style borrows.
 * - Online players live in cache (hydrated). Borrow close() does not evict.
 * - Offline players are hydrated per-borrow; close() saves if dirty and evicts.
 *
 * Tables:
 *   players(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER, updated_at INTEGER)
 *   player_skills(uuid TEXT NOT NULL, skill INTEGER NOT NULL, xp INTEGER NOT NULL,
 *                 PRIMARY KEY(uuid, skill), FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE)
 */
public final class SqlitePlayerStore implements PlayerStore, Closeable {
    private final Path dbPath = FabricLoader.getInstance().getConfigDir().resolve("McRPG").resolve("players.db");
    private final Connection conn;

    // Hot cache for ONLINE players only (hydrated)
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public SqlitePlayerStore() {
        try {
            Files.createDirectories(dbPath.getParent());
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initPragmas(conn);
            createSchema(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SqlitePlayerStore", e);
        }
    }

    // ---------- Schema / setup ----------

    private static void initPragmas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA temp_store=MEMORY");
            s.execute("PRAGMA mmap_size=30000000000"); // 30GB hint; SQLite caps internally
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS players(
                  uuid TEXT PRIMARY KEY,
                  name TEXT,
                  created_at INTEGER,
                  updated_at INTEGER
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_skills(
                  uuid TEXT NOT NULL,
                  skill INTEGER NOT NULL,
                  xp INTEGER NOT NULL,
                  PRIMARY KEY(uuid, skill),
                  FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(LOWER(name))");
            s.execute("CREATE INDEX IF NOT EXISTS idx_skills_uuid ON player_skills(uuid)");
        }
    }

    // ---------- PlayerStore high-level API ----------

    @Override
    public @NotNull PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, key -> {
            ensurePlayerRow(key);                 // creates row if missing; does NOT set a placeholder name
            PlayerData pd = loadOneFromDb(key);   // may return null only on hard failure
            return (pd != null) ? pd : new PlayerData(key); // name stays nullable
        });
    }

    @Override
    public @NotNull PlayerData get(ServerPlayerEntity player) {
        var pd = get(player.getUuid());

        // Last line of defense for name changes
        String name = player.getStringifiedName();
        if (pd.name == null || !pd.name.equals(name)) {
            McRPG.getLogger().debug("Updating name for {} to {}", pd.id, name);
            pd.setName(name);
        }

        return pd;
    }

    @Override
    public Optional<PlayerData> lookup(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT uuid, name FROM players
            WHERE name = ? COLLATE NOCASE LIMIT 1
        """)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    return Optional.of(get(id)); // hydrates cache & skills
                }
            }
        } catch (SQLException e) {
            McRPG.getLogger().error(e, "lookup exact failed for {}", name);
        }

        return Optional.empty();
    }

    @Override
    public synchronized int countByPrefix(String prefix) {
        String like = (prefix == null || prefix.isEmpty()) ? "%" : (prefix + "%");
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM players
            WHERE name LIKE ? COLLATE NOCASE
        """)) {
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
            SELECT uuid, name FROM players
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
                    out.add(pd);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("listByPrefix failed", e);
        }
        return out;
    }

    /**
     * List all ONLINE players in the hot cache.
     * @return List of PlayerData
     */
    @Override
    public List<PlayerData> list() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public void saveAll() {
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement upPlayers = conn.prepareStatement(
                "INSERT INTO players(uuid, name, created_at, updated_at) VALUES(?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, updated_at=excluded.updated_at"
            );
             PreparedStatement upSkill = conn.prepareStatement(
                 "INSERT INTO player_skills(uuid, skill, xp) VALUES(?,?,?) " +
                 "ON CONFLICT(uuid, skill) DO UPDATE SET xp=excluded.xp"
             )
            ) {
                long now = epochSeconds();
                for (PlayerData pd : cache.values()) {
                    if (!pd.dirty) continue;
                    upPlayers.setString(1, pd.id.toString());
                    upPlayers.setString(2, pd.getName());
                    upPlayers.setLong(3, now);
                    upPlayers.setLong(4, now);
                    upPlayers.addBatch();

                    for (var e : pd.xp.entrySet()) {
                        upSkill.setString(1, pd.id.toString());
                        upSkill.setString(2, e.getKey().name());
                        upSkill.setLong(3, e.getValue());
                        upSkill.addBatch();
                    }
                    pd.dirty = false;
                }
                upPlayers.executeBatch();
                upSkill.executeBatch();
                conn.commit();
            } catch (SQLException sqle) {
                conn.rollback();
                throw sqle;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            McRPG.getLogger().error(e, "Failed to saveAll players");
        }

        // Cache eviction of offline players
        var pm = McRPG.getServer().getPlayerManager();
        cache.entrySet().removeIf(e -> pm.getPlayer(e.getKey()) == null);
    }

    // ---------- Low-level store ops ----------

    @Override
    public void save(PlayerData pd) {
        try {
            conn.setAutoCommit(false);
            long now = epochSeconds();
            try (PreparedStatement upPlayer = conn.prepareStatement(
                "INSERT INTO players(uuid, name, created_at, updated_at) VALUES(?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, updated_at=excluded.updated_at"
            );
                 PreparedStatement upSkill = conn.prepareStatement(
                     "INSERT INTO player_skills(uuid, skill, xp) VALUES(?,?,?) " +
                     "ON CONFLICT(uuid, skill) DO UPDATE SET xp=excluded.xp"
                 )
            ) {
                upPlayer.setString(1, pd.id.toString());
                upPlayer.setString(2, pd.getName());
                upPlayer.setLong(3, now);
                upPlayer.setLong(4, now);
                upPlayer.executeUpdate();

                for (var e : pd.xp.entrySet()) {
                    upSkill.setString(1, pd.id.toString());
                    upSkill.setString(2, e.getKey().name());
                    upSkill.setLong(3, e.getValue());
                    upSkill.addBatch();
                }
                upSkill.executeBatch();
                conn.commit();
                pd.dirty = false;
            } catch (SQLException sqle) {
                conn.rollback();
                throw sqle;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // log if available
        }
    }

    private PlayerData loadOneFromDb(UUID id) {
        // Load base player row first
        String name = null;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name FROM players WHERE uuid=?"
        )) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) name = rs.getString(1);
            }
        } catch (SQLException e) {
            McRPG.getLogger().error(e, "Failed to load player row for {}", id);
            return null;
        }

        PlayerData pd = new PlayerData(id, name); // allow null name
        // Load skills
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT skill, xp FROM player_skills WHERE uuid=?"
        )) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        SkillType skill = SkillType.valueOf(rs.getString(1));
                        long total = rs.getLong(2);
                        pd.xp.put(skill, total);
                    } catch (IllegalArgumentException ex) {
                        McRPG.getLogger().warning("Failed to load skill for {}: invalid skill {}",
                            id, rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            McRPG.getLogger().error(e, "Failed to load skills for player {}", id);
        }

        pd.dirty = false;
        return pd;
    }

    /**
     * Ensure a players row exists. If absent, insert with given name or UUID string.
     * Update updated_at; update name if provided and different.
     */
    @Override
    public void ensurePlayerRow(UUID id, @Nullable String name) {
        long now = epochSeconds();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO players(uuid, name, created_at, updated_at) VALUES(?,?,?,?) " +
            "ON CONFLICT(uuid) DO UPDATE SET " +
            "  name = COALESCE(excluded.name, players.name), " +
            "  updated_at=excluded.updated_at"
        )) {
            ps.setString(1, id.toString());
            if (name == null || name.isBlank())
                ps.setNull(2, Types.VARCHAR);
            else
                ps.setString(2, name);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            // log
            McRPG.getLogger().error(e, "Failed to ensure player row for {}", id);
        }
    }

    private void ensurePlayerRow(UUID id) {
        ensurePlayerRow(id, null);
    }

    // ---------- Utilities ----------

    private static long epochSeconds() {
        return Instant.now().getEpochSecond();
    }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }
}
