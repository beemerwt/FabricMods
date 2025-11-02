package com.github.beemerwt.essence.core.data.model;

import java.time.Instant;
import java.util.UUID;

public record MuteRecord(
        long id,
        UUID target,
        UUID by,
        String byName,
        String reason,
        Instant createdAt,
        Instant expiresAt,
        boolean active)
{ }
