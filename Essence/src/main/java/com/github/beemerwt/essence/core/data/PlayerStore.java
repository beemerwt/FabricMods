package com.github.beemerwt.essence.core.data;

import com.github.beemerwt.essence.core.data.model.PlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStore extends BaseStore implements AutoCloseable {
    // Hot cache for active players
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerStore() {
        super(Database.get());
    }

    // ---------- PlayerStore interface ----------

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

    public List<PlayerData> list() {
        return new ArrayList<>(cache.values());
    }

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

    public synchronized List<PlayerData> all() {
        List<PlayerData> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name, noclip FROM players")) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString(1));
                String nm = rs.getString(2);
                boolean nc = rs.getBoolean(3);
                PlayerData pd = new PlayerData(id, nm, nc);
                cache.put(id, pd); // replace cached entry immutably
                out.add(pd);
            }
        } catch (SQLException e) {
            throw new RuntimeException("all(): players scan failed", e);
        }
        return out;
    }

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
                    out.add(new PlayerData(id, nm));
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

        cache.compute(id, (k, oldVal) ->
            new PlayerData(id, newName, (oldVal != null && oldVal.noClip()))
        );
    }

    /**
     * Set and persist noclip for a player, updating cache + DB.
     */
    public void setNoClip(ServerPlayerEntity player, boolean enabled) {
        PlayerData pd = loadIntoCache(player.getUuid(), player.getStringifiedName());
        pd.setNoclip(enabled);
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE players
                   SET noclip=?,
                       updated_at=?
                 WHERE uuid=?
            """)) {
            ps.setBoolean(1, enabled);
            ps.setLong(2, now);
            ps.setString(3, player.getUuidAsString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("persist noclip failed", e);
        }
    }

    // ---------- Low-level helpers ----------

    private void ensurePlayerRow(UUID id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO players (uuid, name, noclip, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "  name = excluded.name, " +
                "  noclip = excluded.noclip, " +
                "  updated_at = excluded.updated_at"
        )) {
            ps.setString(1, id.toString());
            ps.setString(2, name != null ? name : id.toString());
            ps.setBoolean(3, false); // default noclip = false
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.setLong(5, Instant.now().getEpochSecond());
            ps.executeUpdate();
        }
    }

    /**
     * Load from DB into cache; if nameHint != null, prefer it when row is missing.
     */
    private PlayerData loadIntoCache(UUID id, String nameHint) {
        PlayerData cached = cache.get(id);
        if (cached != null) return cached;

        String name = nameHint;
        boolean noClip;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name, noclip FROM players WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) name = rs.getString(1);
                if (name == null) name = id.toString();
                noClip = rs.getBoolean(2);
            }
        } catch (SQLException e) {
            throw new RuntimeException("loadIntoCache: players fetch failed", e);
        }

        PlayerData pd = new PlayerData(id, name, noClip);
        cache.put(id, pd);
        return pd;
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
        cache.clear();
    }
}
