//made with love by nyxon

package com.modern.core.module.players;

import java.util.UUID;

public record PlayerProfile(
    UUID uuid,
    String name,
    String nick,
    boolean fly,
    boolean socialSpy,
    String gamemode,
    long firstJoin,
    long lastJoin,
    long lastQuit,
    long playtimeMs,
    String lastIp
) {
}
