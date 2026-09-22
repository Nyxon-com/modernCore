package com.valaryrpg.core.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandFilterNormalizeTest {

    @Test
    void stripsSlashAndNamespace() {
        assertEquals("menu", CommandFilter.normalize("/menu"));
        assertEquals("set", CommandFilter.normalize("//set"));
        assertEquals("give", CommandFilter.normalize("minecraft:give"));
        assertEquals("cp", CommandFilter.normalize("CommandPanels:cp"));
    }
}
