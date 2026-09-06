package com.valaryrpg.core.module.players;

import java.util.UUID;

public record PlayerProfile(
        UUID uuid,
        String name,
        String nick,
        boolean fly,
        boolean socialSpy,
        long firstJoin,
        long lastJoin,
        String lastIp
) {
}
