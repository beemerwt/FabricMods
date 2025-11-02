package com.github.beemerwt.essence.core.data;

import com.github.beemerwt.essence.core.Essence;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.*;
import java.sql.*;

public final class Database implements AutoCloseable {
    private static volatile Database INSTANCE;
    private static final int SCHEMA_VERSION = 2;

    public static Database get() {
        Database d = INSTANCE;
        if (d == null) {
            synchronized (Database.class) {
                d = INSTANCE;
                if (d == null) INSTANCE = d = new Database();
            }
        }
        return d;
    }

    private final Connection conn;

    private Database() {
        try {
            Path dbPath = resolveDbPath(Essence.getServer());
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initPragmas(conn);
            createSchema(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init essence.db", e);
        }
    }

    public Connection conn() {
        return conn;
    }

    /**
     * Determine the appropriate database path based on server type.
     * Since this mod can be used by singleplayer, LAN, and dedicated servers,
     * we need to choose the database location accordingly.
     *
     * @param server The Minecraft server instance
     * @return Path to the database file
     */
    public static Path resolveDbPath(MinecraftServer server) {
        if (server.isDedicated()) {
            // Centralized DB for a dedicated server
            Path base = FabricLoader.getInstance().getConfigDir().resolve("Essence");
            ensureDir(base);
            return base.resolve("essence.db");
        } else {
            // Per-world DB for integrated server (singleplayer or Open-to-LAN)
            Path worldData = server.getSavePath(WorldSavePath.ROOT).resolve("data");
            ensureDir(worldData); // should already exist, but just in case
            return worldData.resolve("essence.db");
        }
    }

    private static void ensureDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
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

    /**
     * Unified schema. Column names match your existing tables to simplify migration.
     */
    private static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            createMetaTable(s);
            createPlayersTable(s);
            createLocationsTable(s);
            createBansTable(s);
            createMutesTable(s);
            createJailsTable(s);
            createJailLocationsTable(s);
        }
    }

    private static void createMetaTable(Statement s) throws SQLException {
        // meta
        s.execute("""
            CREATE TABLE IF NOT EXISTS meta(
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
        """);
    }

    private static void createPlayersTable(Statement s) throws SQLException {
        // players
        s.execute("""
            CREATE TABLE IF NOT EXISTS players(
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              noclip BOOLEAN NOT NULL DEFAULT FALSE,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
        """);

        // idx_players_name (case-sensitive). Add optional NOCASE index:
        s.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_players_name_nocase ON players(name COLLATE NOCASE)");
    }

    private static void createLocationsTable(Statement s) throws SQLException {
        s.execute("""
            CREATE TABLE IF NOT EXISTS locations(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid  TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
              type  TEXT NOT NULL,
              key   TEXT NOT NULL,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              UNIQUE(uuid, type, key) ON CONFLICT REPLACE
            )
        """);
        s.execute("CREATE INDEX IF NOT EXISTS idx_loc_uuid ON locations(uuid)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_loc_uuid_type ON locations(uuid,type)");
    }

    private static void createBansTable(Statement s) throws SQLException {
        // bans
        s.execute("""
            CREATE TABLE IF NOT EXISTS bans(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
              by_uuid TEXT NOT NULL,
              by_name TEXT NOT NULL,
              reason TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER,
              active INTEGER NOT NULL DEFAULT 1
            )
        """);
        s.execute("CREATE INDEX IF NOT EXISTS idx_bans_player_active ON bans(player_uuid, active)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_bans_active_created ON bans(active, created_at DESC)");
    }

    private static void createMutesTable(Statement s) throws SQLException {
        // mutes
        s.execute("""
            CREATE TABLE IF NOT EXISTS mutes(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
              by_uuid TEXT NOT NULL,
              by_name TEXT NOT NULL,
              reason TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER,
              active INTEGER NOT NULL DEFAULT 1
            )
        """);
        s.execute("CREATE INDEX IF NOT EXISTS idx_mutes_player_active ON mutes(player_uuid, active)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_mutes_active_created ON mutes(active, created_at DESC)");
    }

    private static void createJailsTable(Statement s) throws SQLException {
        // jails
        s.execute("""
                CREATE TABLE IF NOT EXISTS jails(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                  by_uuid TEXT NOT NULL,
                  by_name TEXT NOT NULL,
                  jail_name TEXT NOT NULL,
                  reason TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  expires_at INTEGER NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1
                )
            """);
        s.execute("CREATE INDEX IF NOT EXISTS idx_jails_player_active ON jails(player_uuid, active)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_jails_active_created ON jails(active, created_at DESC)");
    }

    private static void createJailLocationsTable(Statement s) throws SQLException {
        // jail locations
        s.execute("""
            CREATE TABLE IF NOT EXISTS jail_locations(
              name TEXT PRIMARY KEY,
              world_key TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL
            )
        """);
    }

    private static String esc(Path p) {
        return p.toString().replace("'", "''");
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }
}
