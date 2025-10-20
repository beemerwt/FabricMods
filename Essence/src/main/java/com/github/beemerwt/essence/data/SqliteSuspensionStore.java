package com.github.beemerwt.essence.data;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.model.*;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** SQLite store for bans, jails, and jail locations. */
public final class SqliteSuspensionStore implements SuspensionStore, Closeable {
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("Essence");
    private final Path dbPath = dir.resolve("suspensions.db");
    private final Connection conn;

    public SqliteSuspensionStore() {
        try {
            Files.createDirectories(dir);
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initPragmas(conn);
            createSchema(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init SqliteSuspensionStore", e);
        }
    }

    private static void initPragmas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
            CREATE TABLE IF NOT EXISTS bans(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              by_uuid TEXT NOT NULL,
              by_name TEXT NOT NULL,
              reason TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER,
              active INTEGER NOT NULL DEFAULT 1
            );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_bans_player_active ON bans(player_uuid, active)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_bans_active_created ON bans(active, created_at DESC)");

            s.execute("""
            CREATE TABLE IF NOT EXISTS jails(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              by_uuid TEXT NOT NULL,
              by_name TEXT NOT NULL,
              jail_name TEXT NOT NULL,
              reason TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL,
              active INTEGER NOT NULL DEFAULT 1
            );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_jails_player_active ON jails(player_uuid, active)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_jails_active_created ON jails(active, created_at DESC)");

            s.execute("""
            CREATE TABLE IF NOT EXISTS jail_locations(
              name TEXT PRIMARY KEY,
              world_key TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL
            );
            """);

            s.execute("""
            CREATE TABLE IF NOT EXISTS mutes(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              by_uuid TEXT NOT NULL,
              by_name TEXT NOT NULL,
              reason TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER,
              active INTEGER NOT NULL DEFAULT 1
            );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_mutes_player_active ON mutes(player_uuid, active)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_mutes_active_created ON mutes(active, created_at DESC)");

        }
    }

    private static long toEpoch(Instant i) { return i == null ? 0L : i.getEpochSecond(); }
    private static Instant fromEpoch(Long v) { return (v == null || v == 0L) ? null : Instant.ofEpochSecond(v); }

    /* ===== BANS ===== */

    @Override
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

    @Override
    public BanRecord banTemporary(UUID target, UUID by, String byName, String reason, Instant expiresAt) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO bans(player_uuid, by_uuid, by_name, reason, created_at, expires_at, active)
            VALUES(?,?,?,?,?,?,1)
        """, Statement.RETURN_GENERATED_KEYS)) {
            long now = Instant.now().getEpochSecond();
            ps.setString(1, target.toString());
            ps.setString(2, by.toString());
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

    @Override
    public Optional<BanRecord> getActiveBan(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, by_uuid, by_name, reason, created_at, expires_at
            FROM bans
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """))
        {
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

    @Override
    public boolean unban(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE bans SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
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

    @Override
    public int countActiveBans() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bans WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /* ===== MUTES ===== */

    @Override
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

    @Override
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

    @Override
    public java.util.Optional<MuteRecord> getActiveMute(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT id, by_uuid, by_name, reason, created_at, expires_at
            FROM mutes
            WHERE player_uuid=? AND active=1
            ORDER BY created_at DESC
            LIMIT 1
        """)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return java.util.Optional.empty();
                return java.util.Optional.of(new MuteRecord(
                        rs.getLong(1), target, java.util.UUID.fromString(rs.getString(2)),
                        rs.getString(3), rs.getString(4),
                        java.time.Instant.ofEpochSecond(rs.getLong(5)),
                        fromEpoch(rs.getLong(6)), true
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public boolean unmute(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE mutes SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public int countActiveMutes() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM mutes WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public java.util.List<MuteRecord> listMutes(int offset, int limit) {
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public boolean unjail(UUID target) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE jails SET active=0 WHERE player_uuid=? AND active=1
        """)) {
            ps.setString(1, target.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
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

    @Override
    public int countActiveJails() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jails WHERE active=1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /* ===== JAIL LOCATIONS ===== */

    @Override
    public boolean setJail(String name, StoredLocation loc) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO jail_locations(name, world_key, x, y, z, yaw, pitch)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(name) DO UPDATE SET world_key=excluded.world_key, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch
        """)) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            ps.setString(2, loc.worldKey());
            ps.setDouble(3, loc.x());
            ps.setDouble(4, loc.y());
            ps.setDouble(5, loc.z());
            ps.setFloat(6, loc.yaw());
            ps.setFloat(7, loc.pitch());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
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

    @Override
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
