package com.valaryrpg.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandListPagesTest {

    @Test
    void dumpContainsFullAllowedAndDeniedLists() {
        String dump = CommandListCommands.dump(List.of("menu", "spawn"), List.of("ban", "cp"));
        assertTrue(dump.contains("/menu"));
        assertTrue(dump.contains("/spawn"));
        assertTrue(dump.contains("/ban"));
        assertTrue(dump.contains("/cp"));
        assertEquals("""
                Puoi usare (2):
                /menu
                /spawn

                Non puoi usare (2):
                /ban
                /cp""", dump);
    }

    @Test
    void pageCountIsAtLeastOne() {
        assertEquals(1, CommandListPages.pageCount(0, 20));
        assertEquals(2, CommandListPages.pageCount(21, 20));
    }
}
