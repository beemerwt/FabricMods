package com.github.beemerwt.essence.core.data;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.model.*;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** SQLite store for bans, jails, and jail locations. */
public final class SuspensionStore extends BaseStore implements AutoCloseable {
    public SuspensionStore() {
        super(Database.get());
        expireStaleBans();
    }

    private static long toEpoch(Instant i) { return i == null ? 0L : i.getEpochSecond(); }
    private static Instant fromEpoch(Long v) { return (v == null || v == 0L) ? null : Instant.ofEpochSecond(v); }

    /* ===== BANS ===== */

    public BanRecord banPermanent(UUID target, @Nullable UUID by, String byName, String reason) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO bans(player_uuid, by_uuid, by_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            var byIdStr = (by != null) ? by.toString() : "CONSOLE";
            long now = Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, byIdStr);
            ps.setString(3, byName);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setNull(6, Types.INTEGER);
            ps.executeUpdate();
            long id = getId(ps);
            return new BanRecord(id, target, by, byName, reason, Instant.ofEpochSecond(now), null, true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public BanRecord banTemporary(UUID target, @Nullable UUID by, String byName, String reason, Instant expiresAt) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO bans(player_uuid, by_uuid, by_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            var byIdStr = (by != null) ? by.toString() : "CONSOLE";
            long now = Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, byIdStr);
            ps.setString(3, byName);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setLong(6, toEpoch(expiresAt));
            ps.executeUpdate();
            long id = getId(ps);
            return new BanRecord(id, target, by, byName, reason, Instant.ofEpochSecond(now), expiresAt, true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<BanRecord> getActiveBan(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, by_uuid, by_name, reason, created_at, expires_at
            FROM bans
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new BanRecord(
                        rs.getLong(1), target, UUID.fromString(rs.getString(2)),
                        rs.getString(3), rs.getString(4),
                        Instant.ofEpochSecond(rs.getLong(5)),
                        fromEpoch(rs.getLong(6)), true
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean unban(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE bans SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<BanRecord> listBans(int offset, int limit) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, player_uuid, by_uuid, by_name, reason, created_at, expires_at
            FROM bans WHERE active=1
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            List<BanRecord> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BanRecord(
                            rs.getLong(1),
                            UUID.fromString(rs.getString(2)),
                            UUID.fromString(rs.getString(3)),
                            rs.getString(4),
                            rs.getString(5),
                            Instant.ofEpochSecond(rs.getLong(6)),
                            fromEpoch(rs.getLong(7)),
                            true
                    ));
                }
            }
            return out;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countActiveBans() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bans WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countBansByPrefix(String prefix) {
        final String like = (prefix == null || prefix.isEmpty()) ? "%" : prefix + "%";
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(DISTINCT p.name)
            FROM bans b
            JOIN players p ON p.uuid = b.player_uuid
            WHERE b.active = 1
              AND p.name LIKE ? COLLATE NOCASE
        """)) {
            ps.setString(1, like);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("countBansByPrefix failed", e);
        }
    }

    public List<String> listBansByPrefix(String prefix, int offset, int limit) {
        String like = (prefix == null || prefix.isEmpty()) ? "%" : (prefix + "%");
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT DISTINCT p.name
            FROM bans b
            JOIN players p ON p.uuid = b.player_uuid
            WHERE b.active = 1
              AND p.name LIKE ? COLLATE NOCASE
            ORDER BY b.created_at DESC
            LIMIT ? OFFSET ?
        """)) {
            ps.setString(1, like);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("listBansByPrefix failed", e);
        }

        return out;
    }

    /** One quick sweep: mark expired temp bans inactive. Call before list/count. */
    private void expireStaleBans() {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE bans
            SET active = 0
            WHERE active = 1
              AND expires_at IS NOT NULL
              AND expires_at <= ?
        """)) {
            ps.setLong(1, java.time.Instant.now().getEpochSecond());
            ps.executeUpdate(); // ignore result; best-effort cleanup
        } catch (SQLException e) {
            // Don't break suggestions if cleanup fails
            Essence.getLogger().warn(e, "expireStaleBans failed");
        }
    }


    /* ===== MUTES ===== */

    public MuteRecord mutePermanent(UUID target, UUID by, String byName, String reason) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO mutes(player_uuid, by_uuid, by_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            long now = java.time.Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, by.toString());
            ps.setString(3, byName);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setNull(6, Types.INTEGER);
            ps.executeUpdate();
            long id = getId(ps);
            return new MuteRecord(id, target, by, byName, reason, java.time.Instant.ofEpochSecond(now), null, true);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public MuteRecord muteTemporary(UUID target, UUID by, String byName, String reason, java.time.Instant expiresAt) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO mutes(player_uuid, by_uuid, by_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            long now = java.time.Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, by.toString());
            ps.setString(3, byName);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setLong(6, toEpoch(expiresAt));
            ps.executeUpdate();
            long id = getId(ps);
            return new MuteRecord(id, target, by, byName, reason, java.time.Instant.ofEpochSecond(now), expiresAt, true);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<MuteRecord> getActiveMute(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, by_uuid, by_name, reason, created_at, expires_at
            FROM mutes
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new MuteRecord(
                        rs.getLong(1), target, UUID.fromString(rs.getString(2)),
                        rs.getString(3), rs.getString(4),
                        Instant.ofEpochSecond(rs.getLong(5)),
                        fromEpoch(rs.getLong(6)), true
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean unmute(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE mutes SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countActiveMutes() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM mutes WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<MuteRecord> listMutes(int offset, int limit) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, player_uuid, by_uuid, by_name, reason, created_at, expires_at
            FROM mutes WHERE active=1
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            java.util.List<MuteRecord> out = new java.util.ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MuteRecord(
                            rs.getLong(1),
                            java.util.UUID.fromString(rs.getString(2)),
                            java.util.UUID.fromString(rs.getString(3)),
                            rs.getString(4),
                            rs.getString(5),
                            java.time.Instant.ofEpochSecond(rs.getLong(6)),
                            fromEpoch(rs.getLong(7)),
                            true
                    ));
                }
            }
            return out;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean isMuted(UUID player) {
        var m = getActiveMute(player);
        if (m.isEmpty()) return false;
        var mr = m.get();
        if (mr.expiresAt() != null && mr.expiresAt().isBefore(java.time.Instant.now())) {
            unmute(player); // auto-expire
            return false;
        }
        return true;
    }

    /* ===== JAILS ===== */

    public JailRecord jailTemporary(UUID target, UUID by, String byName, String jailName, String reason, Instant expiresAt) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO jails(player_uuid, by_uuid, by_name, jail_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            long now = Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, by.toString());
            ps.setString(3, byName);
            ps.setString(4, jailName);
            ps.setString(5, reason);
            ps.setLong(6, now);
            ps.setLong(7, toEpoch(expiresAt));
            ps.executeUpdate();
            long id = getId(ps);
            return new JailRecord(id, target, by, byName, jailName, reason,
                    Instant.ofEpochSecond(now), expiresAt, true);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<JailRecord> getActiveJail(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, by_uuid, by_name, jail_name, reason, created_at, expires_at
            FROM jails
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new JailRecord(
                        rs.getLong(1), target, UUID.fromString(rs.getString(2)),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        Instant.ofEpochSecond(rs.getLong(6)),
                        fromEpoch(rs.getLong(7)), true
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean isJailed(UUID player) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT expires_at FROM jails
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                long exp = rs.getLong(1); // 0 never occurs for jails in our schema
                boolean active = Instant.ofEpochSecond(exp).isAfter(Instant.now());
                if (!active) {
                    // auto-expire stale record
                    unjail(player);
                }
                return active;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean unjail(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE jails SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<JailRecord> listJails(int offset, int limit) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, player_uuid, by_uuid, by_name, jail_name, reason, created_at, expires_at
            FROM jails WHERE active=1
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            List<JailRecord> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new JailRecord(
                            rs.getLong(1),
                            UUID.fromString(rs.getString(2)),
                            UUID.fromString(rs.getString(3)),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            Instant.ofEpochSecond(rs.getLong(7)),
                            fromEpoch(rs.getLong(8)),
                            true
                    ));
                }
            }
            return out;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countActiveJails() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jails WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /* ===== JAIL LOCATIONS ===== */

    public boolean setJail(String name, StoredLocation loc) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO jail_locations(name, world_key, x, y, z, yaw, pitch)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(name) DO UPDATE SET world_key=excluded.world_key, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch
        """)) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            ps.setString(2, loc.worldKey().getValue().toString());
            ps.setDouble(3, loc.x());
            ps.setDouble(4, loc.y());
            ps.setDouble(5, loc.z());
            ps.setFloat(6, loc.yaw());
            ps.setFloat(7, loc.pitch());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<StoredLocation> getJail(String name) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT world_key, x, y, z, yaw, pitch FROM jail_locations WHERE name=?
        """)) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new StoredLocation(
                        rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getFloat(5), rs.getFloat(6)
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Map<String, StoredLocation> listAllJails() {
        Map<String, StoredLocation> out = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, world_key, x, y, z, yaw, pitch FROM jail_locations ORDER BY name")) {
            while (rs.next()) {
                out.put(rs.getString(1), new StoredLocation(
                        rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                        rs.getFloat(6), rs.getFloat(7)
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    private static long getId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
            throw new SQLException("No generated key returned");
        }
    }

    @Override public void close() { try { conn.close(); } catch (Exception ignored) {} }
}
