package com.github.beemerwt.essence.data.model;

import com.github.beemerwt.essence.data.SqliteSuspensionStore;
import com.github.beemerwt.essence.data.StoredLocation;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.time.Instant;
import java.util.*;

public interface SuspensionStore extends Closeable {
    static SuspensionStore create() { return new SqliteSuspensionStore(); }

    /* ===== BANS ===== */
    BanRecord banPermanent(UUID target, @Nullable UUID by, String byName, String reason);
    BanRecord banTemporary(UUID target, @Nullable UUID by, String byName, String reason, Instant expiresAt);
    Optional<BanRecord> getActiveBan(UUID target);
    boolean unban(UUID target); // sets active=0 for active bans
    List<BanRecord> listBans(int offset, int limit); // active only, newest first
    int countActiveBans();

    /* ===== MUTES ===== */
    MuteRecord mutePermanent(UUID target, @Nullable UUID by, String byName, String reason);
    MuteRecord muteTemporary(UUID target, @Nullable UUID by, String byName, String reason, Instant expiresAt);
    Optional<MuteRecord> getActiveMute(UUID target);
    boolean isMuted(UUID player); // active + not expired
    boolean unmute(UUID target);
    List<MuteRecord> listMutes(int offset, int limit); // active only, newest first
    int countActiveMutes();

    /* ===== JAILS ===== */
    JailRecord jailTemporary(UUID target, UUID by, String byName, String jailName, String reason, Instant expiresAt);
    Optional<JailRecord> getActiveJail(UUID target);
    boolean isJailed(UUID player); // active + not expired
    boolean unjail(UUID target);
    List<JailRecord> listJails(int offset, int limit); // active only, newest first
    int countActiveJails();

    /* ===== JAIL LOCATIONS ===== */
    boolean setJail(String name, StoredLocation loc);
    Optional<StoredLocation> getJail(String name);
    Map<String, StoredLocation> listAllJails();
}
