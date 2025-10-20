package com.github.beemerwt.essence.data;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.model.LocationStore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SqliteLocationStore implements LocationStore, Closeable {
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("Essence");
    private final Path dbPath = dir.resolve("locations.db");

    private Connection conn;
    private final Map<UUID, PlayerLocations> cache = new ConcurrentHashMap<>();

    public SqliteLocationStore() {
        try {
            Files.createDirectories(dir);
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initPragmas(conn);
            createSchema(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init SqliteLocationStore", e);
        }
    }

    private static void initPragmas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA temp_store=MEMORY");
            // no foreign_keys: separate DB from players
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(
                    "CREATE TABLE IF NOT EXISTS locations (" +
                            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  uuid  TEXT NOT NULL," +
                            "  type  TEXT NOT NULL," +
                            "  key   TEXT NOT NULL," +
                            "  world TEXT NOT NULL," +
                            "  x REAL NOT NULL," +
                            "  y REAL NOT NULL," +
                            "  z REAL NOT NULL," +
                            "  yaw REAL NOT NULL," +
                            "  pitch REAL NOT NULL," +
                            "  UNIQUE(uuid, type, key) ON CONFLICT REPLACE" +
                            ")"
            );
            s.execute("CREATE INDEX IF NOT EXISTS idx_loc_uuid ON locations(uuid)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_loc_uuid_type ON locations(uuid,type)");
        }
    }

    // -------- Cache helpers using PlayerLocations --------
    private PlayerLocations ensureLoaded(UUID id) {
        // Atomically: if absent, load from DB; if present, return as-is
        return cache.compute(id, (k, existing) -> {
            if (existing != null) return existing;
            return loadPlayerFromDb(k);
        });
    }

    private PlayerLocations loadPlayerFromDb(UUID id) {
        var pl = new PlayerLocations();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT type, key, world, x, y, z, yaw, pitch FROM locations WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocationType type;
                    try { type = LocationType.valueOf(rs.getString(1)); }
                    catch (IllegalArgumentException ex) { continue; }
                    String key = rs.getString(2);
                    String world = rs.getString(3);
                    double x = rs.getDouble(4);
                    double y = rs.getDouble(5);
                    double z = rs.getDouble(6);
                    float yaw = (float) rs.getDouble(7);
                    float pitch = (float) rs.getDouble(8);
                    pl.put(type, key, new StoredLocation(world, x, y, z, yaw, pitch));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("hydratePlayer failed for " + id, e);
        }
        // Note: returning an empty PlayerLocations is fine if they have no rows yet.
        return pl;
    }

    private void hydratePlayer(UUID id) {
        var pl = new PlayerLocations();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT type, key, world, x, y, z, yaw, pitch FROM locations WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocationType type;
                    try { type = LocationType.valueOf(rs.getString(1)); }
                    catch (IllegalArgumentException ex) { continue; }
                    String key = rs.getString(2);
                    String world = rs.getString(3);
                    double x = rs.getDouble(4);
                    double y = rs.getDouble(5);
                    double z = rs.getDouble(6);
                    float yaw = (float) rs.getDouble(7);
                    float pitch = (float) rs.getDouble(8);
                    pl.put(type, key, new StoredLocation(world, x, y, z, yaw, pitch));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("hydratePlayer failed for " + id, e);
        }
        cache.put(id, pl);
    }

    // -------- LocationStore implementation --------

    @Override
    public synchronized boolean set(UUID playerId, LocationType type, String key, StoredLocation loc) {
        if (type == null) throw new IllegalArgumentException("type");
        if (key == null || key.isBlank()) key = "_";
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO locations(uuid,type,key,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid,type,key) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, type.name());
            ps.setString(3, key);
            ps.setString(4, loc.worldKey());
            ps.setDouble(5, loc.x());
            ps.setDouble(6, loc.y());
            ps.setDouble(7, loc.z());
            ps.setDouble(8, loc.yaw());
            ps.setDouble(9, loc.pitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            Essence.getLogger().warning("Failed to set location {} for player {}: {}", key, playerId, e.getMessage());
            return false;
        }

        ensureLoaded(playerId).put(type, key, loc);
        return true;
    }

    @Override
    public synchronized Optional<StoredLocation> get(UUID playerId, LocationType type, String key) {
        if (key == null || key.isBlank()) key = "_";
        var loc = ensureLoaded(playerId).get(type, key);
        // Because ensureLoaded pulls all rows, a miss here means "doesn't exist".
        return Optional.ofNullable(loc);
    }

    @Override
    public synchronized Map<String, StoredLocation> list(UUID playerId, LocationType type) {
        // Update the cached locations
        return ensureLoaded(playerId).of(type);
    }

    @Override
    public synchronized Map<LocationType, Map<String, StoredLocation>> listAll(UUID playerId) {
        return ensureLoaded(playerId).snapshot();
    }

    @Override
    public synchronized boolean delete(UUID playerId, LocationType type, String key) {
        if (key == null || key.isBlank()) key = "_";
        int n;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM locations WHERE uuid=? AND type=? AND key=?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, type.name());
            ps.setString(3, key);
            n = ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete location failed", e);
        }

        var pl = ensureLoaded(playerId);
        if (pl != null) pl.remove(type, key);
        return n > 0;
    }

    @Override
    public synchronized boolean deleteAllOfType(UUID playerId, LocationType type) {
        int n;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM locations WHERE uuid=? AND type=?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, type.name());
            n = ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteAllOfType failed", e);
        }

        var pl = ensureLoaded(playerId);
        var snap = pl.snapshot();
        snap.remove(type);
        cache.put(playerId, fromSnapshot(snap));
        return n > 0;
    }

    private static PlayerLocations fromSnapshot(Map<LocationType, Map<String, StoredLocation>> snap) {
        var pl = new PlayerLocations();
        for (var e : snap.entrySet()) {
            var type = e.getKey();
            for (var kv : e.getValue().entrySet()) {
                pl.put(type, kv.getKey(), kv.getValue());
            }
        }
        return pl;
    }

    @Override
    public synchronized void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        cache.clear();
    }
}
