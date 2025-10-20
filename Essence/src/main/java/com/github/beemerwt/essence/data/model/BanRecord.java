package com.github.beemerwt.essence.data.model;

import java.time.Instant;
import java.util.UUID;

public record BanRecord(
        long id,
        UUID player,
        UUID by,
        String byName,
        String reason,
        Instant createdAt,
        Instant expiresAt, // null -> permanent
        boolean active
) {
}
