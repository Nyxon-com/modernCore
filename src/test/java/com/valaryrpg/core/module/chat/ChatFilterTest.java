package com.valaryrpg.core.module.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterTest {

    @Test
    void blocksConfiguredWordAndIpAndLink() {
        ChatFilter filter = ChatFilter.compile(
                List.of("spamword"),
                List.of("(?i)token-[0-9]+"),
                true,
                true,
                List.of("(?i)discord\\.gg/")
        );

        assertEquals(ChatFilter.Hit.WORD, filter.match("this SPAMWORD here").orElseThrow());
        assertEquals(ChatFilter.Hit.REGEX, filter.match("use token-12 now").orElseThrow());
        assertEquals(ChatFilter.Hit.IP, filter.match("join 127.0.0.1 later").orElseThrow());
        assertEquals(ChatFilter.Hit.LINK, filter.match("discord.gg/valary").orElseThrow());
        assertTrue(filter.match("ciao a tutti").isEmpty());
    }
}
