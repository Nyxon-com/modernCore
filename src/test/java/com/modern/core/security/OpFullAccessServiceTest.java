//made with love by nyxon

package com.modern.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpFullAccessServiceTest {

  @Test
  void stripsSlashAndMinecraftNamespace() {
    assertEquals("deop Notch", OpFullAccessService.stripNamespace("/minecraft:deop Notch"));
    assertEquals("op Notch", OpFullAccessService.stripNamespace("op Notch"));
    assertEquals("deop Notch", OpFullAccessService.stripNamespace("minecraft:deop Notch"));
  }
}
