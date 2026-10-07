package com.valaryrpg.core.module.tomb;

import java.util.UUID;

public record Tomb(
        long id,
        UUID uuid,
        String name,
        long createdAt,
        String world,
        double x,
        double y,
        double z,
        String cause,
        String killer,
        String inventory,
        int xp
) {
}
