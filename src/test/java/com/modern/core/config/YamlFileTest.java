package com.modern.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlFileTest {

  @TempDir
  Path temp;

  @Test
  void readsExternalChangesWithoutWritingTheFile() throws Exception {
    Path file = this.temp.resolve("mechanics.yml");
    Files.writeString(file, "holo-blocks:\n  view-range: 24\n", StandardCharsets.UTF_8);
    YamlConfiguration first = YamlFile.readStrict(file.toFile());
    assertEquals(24, first.getInt("holo-blocks.view-range"));

    String changed = "holo-blocks:\n  view-range: 48\n";
    Files.writeString(file, changed, StandardCharsets.UTF_8);
    YamlConfiguration second = YamlFile.readStrict(file.toFile());

    assertEquals(48, second.getInt("holo-blocks.view-range"));
    assertEquals(changed, Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test
  void rejectsMalformedYaml() throws Exception {
    Path file = this.temp.resolve("broken.yml");
    Files.writeString(file, "holo-blocks: [\n", StandardCharsets.UTF_8);

    assertThrows(Exception.class, () -> YamlFile.readStrict(file.toFile()));
  }
}
