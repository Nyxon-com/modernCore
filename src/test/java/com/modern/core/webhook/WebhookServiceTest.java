//made with love by nyxon

package com.modern.core.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookServiceTest {

  @Test
  void ignoresMemoryErrorsAndKeepsOtherSevere() {
    assertTrue(WebhookService.memoryNoise("java.lang.OutOfMemoryError: Java heap space", null));
    assertTrue(WebhookService.memoryNoise("GC overhead limit exceeded", new OutOfMemoryError("heap")));
    assertFalse(WebhookService.memoryNoise("Cannot load worlds.yml", new IllegalStateException("bad yaml")));
  }
}
