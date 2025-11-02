package com.github.beemerwt.essence.core.permission;

public final class Perms {
    private Perms() {}

    // Admin/Moderation
    public static final Perm BAN        = Perm.of("essence.ban",        OpLevel.SUPER_MOD);
    public static final Perm BAN_INFO   = Perm.of("essence.baninfo",    OpLevel.MODERATOR);
    public static final Perm TEMP_BAN   = Perm.of("essence.tempban",    OpLevel.SUPER_MOD);
    public static final Perm UNBAN      = Perm.of("essence.unban",      OpLevel.SUPER_MOD);
    public static final Perm MUTE       = Perm.of("essence.mute",       OpLevel.SUPER_MOD);
    public static final Perm TEMP_MUTE  = Perm.of("essence.tempmute",   OpLevel.MODERATOR);
    public static final Perm UNMUTE     = Perm.of("essence.unmute",     OpLevel.SUPER_MOD);
    public static final Perm KICK       = Perm.of("essence.kick",       OpLevel.MODERATOR);

    public static final Perm SET_JAIL   = Perm.of("essence.setjail",    OpLevel.ADMIN);
    public static final Perm DEL_JAIL   = Perm.of("essence.deljail",    OpLevel.ADMIN);
    public static final Perm JAIL_INFO  = Perm.of("essence.jail.view",  OpLevel.MODERATOR);
    public static final Perm JAIL_LIST  = Perm.of("essence.jail.view",  OpLevel.MODERATOR);
    public static final Perm JAIL       = Perm.of("essence.jail",       OpLevel.MODERATOR);
    public static final Perm UNJAIL     = Perm.of("essence.unjail",     OpLevel.MODERATOR);

    // Teleportation / QoL
    public static final Perm TP         = Perm.of("essence.tp",         OpLevel.SUPER_MOD);
    public static final Perm TPA        = Perm.of("essence.tpa",        OpLevel.NONE);
    public static final Perm HOME       = Perm.of("essence.home",       OpLevel.NONE);
    public static final Perm SET_HOME   = Perm.of("essence.sethome",    OpLevel.SUPER_MOD);
    public static final Perm DEL_HOME   = Perm.of("essence.delhome",    OpLevel.SUPER_MOD);
    public static final Perm WARP       = Perm.of("essence.warp",       OpLevel.NONE);
    public static final Perm SET_WARP   = Perm.of("essence.setwarp",    OpLevel.ADMIN);
    public static final Perm DEL_WARP   = Perm.of("essence.delwarp",    OpLevel.ADMIN);
    public static final Perm BACK       = Perm.of("essence.back",       OpLevel.SUPER_MOD);
    public static final Perm TOP        = Perm.of("essence.top",        OpLevel.SUPER_MOD);
    public static final Perm BOTTOM     = Perm.of("essence.bottom",     OpLevel.SUPER_MOD);
    public static final Perm UP         = Perm.of("essence.up",         OpLevel.SUPER_MOD);
    public static final Perm DOWN       = Perm.of("essence.down",       OpLevel.SUPER_MOD);
    public static final Perm SPAWN      = Perm.of("essence.spawn",      OpLevel.NONE);
    public static final Perm SET_SPAWN  = Perm.of("essence.setspawn",   OpLevel.ADMIN);
    public static final Perm NOCLIP     = Perm.of("essence.noclip",     OpLevel.ADMIN);

    // MISC
    public static final Perm HEAL       = Perm.of("essence.heal",       OpLevel.MODERATOR);
    public static final Perm FEED       = Perm.of("essence.feed",       OpLevel.MODERATOR);
    public static final Perm FLY        = Perm.of("essence.fly",        OpLevel.ADMIN);
    public static final Perm ENCHANT    = Perm.of("essence.enchant",    OpLevel.SUPER_MOD);
    public static final Perm SUMMON     = Perm.of("essence.summon",     OpLevel.SUPER_MOD);

    public static final Perm FIND_ITEM  = Perm.of("essence.finditem",   OpLevel.ADMIN);
    public static final Perm INV_SEE    = Perm.of("essence.invsee",     OpLevel.ADMIN);
}

