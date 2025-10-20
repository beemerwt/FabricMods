package com.github.beemerwt.essence.data.model;

import java.time.Instant;
import java.util.UUID;

public record JailRecord(
        long id,
        UUID player,
        UUID by,
        String byName,
        String jailName,
        String reason,
        Instant createdAt,
        Instant expiresAt, // required for our flows, but could allow null → indefinite
        boolean active
) {
}
