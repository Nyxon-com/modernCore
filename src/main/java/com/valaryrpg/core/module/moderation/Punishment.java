package com.valaryrpg.core.module.moderation;

import java.util.UUID;

public record Punishment(
        long id,
        PunishmentType type,
        UUID targetUuid,
        String targetName,
        String targetIp,
        String staff,
        String reason,
        long createdAt,
        Long expiresAt,
        boolean active,
        Long revokedAt,
        String revokedBy,
        String revokeReason
) {
    public boolean expired() {
        return this.expiresAt != null && this.expiresAt <= System.currentTimeMillis();
    }

    public boolean revoked() {
        return this.revokedAt != null && this.revokedAt > 0L;
    }
}
