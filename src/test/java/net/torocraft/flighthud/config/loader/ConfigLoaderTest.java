package net.torocraft.flighthud.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.torocraft.flighthud.config.SettingsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir
  File tempDir;

  private SettingsConfig newDefaultConfig(boolean watch) {
    SettingsConfig config = new SettingsConfig();
    config.watchForConfigChanges = watch;
    return config;
  }

  @Test
  void load_writesDefaultConfigWhenFileIsMissingAndInvokesCallback() {
    File file = new File(tempDir, "settings.json");
    List<SettingsConfig> loaded = new ArrayList<>();
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), file, loaded::add);

    assertFalse(file.exists());
    loader.load();

    assertTrue(file.exists());
    assertEquals(1, loaded.size());
    assertEquals(SettingsConfig.DisplayMode.FULL.toString(), loaded.get(0).displayModeWhenFlying);
    assertFalse(loader.isWatching());
  }

  @Test
  void load_readsExistingFileInsteadOfOverwritingIt() throws IOException {
    File file = new File(tempDir, "settings.json");
    try (FileWriter writer = new FileWriter(file)) {
      writer.write("{\"watchForConfigChanges\":false,"
          + "\"displayModeWhenFlying\":\"MIN\","
          + "\"displayModeWhenNotFlying\":\"NONE\","
          + "\"calculateRoll\":false,"
          + "\"rollTurningForce\":2.0,"
          + "\"rollSmoothing\":0.1}");
    }

    List<SettingsConfig> loaded = new ArrayList<>();
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(true), file, loaded::add);
    loader.load();

    assertEquals(1, loaded.size());
    SettingsConfig config = loaded.get(0);
    // Values came from the pre-existing file, not the constructor's defaults.
    assertEquals("MIN", config.displayModeWhenFlying);
    assertFalse(config.calculateRoll);
    assertEquals(2.0f, config.rollTurningForce, 1e-6f);
  }

  @Test
  void load_startsWatchingWhenLoadedConfigRequestsIt() {
    File file = new File(tempDir, "settings.json");
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(true), file, config -> { });

    loader.load();

    assertTrue(loader.isWatching());
  }

  @Test
  void watch_isIdempotentWhenCalledTwice() {
    File file = new File(tempDir, "settings.json");
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), file, config -> { });

    assertFalse(loader.isWatching());
    loader.watch(file);
    assertTrue(loader.isWatching());

    // Second call must hit the early-return guard, not create/replace a watcher.
    loader.watch(file);
    assertTrue(loader.isWatching());
  }

  @Test
  void reload_reReadsFileAndInvokesCallbackAgain() {
    // reload() is the exact callback watch() wires up to the FileWatcher's
    // background thread; invoking it directly verifies that callback path
    // without waiting on a real filesystem-change event to fire it.
    File file = new File(tempDir, "settings.json");
    List<SettingsConfig> loaded = new ArrayList<>();
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), file, loaded::add);

    loader.load();
    assertEquals(1, loaded.size());

    loader.reload();
    assertEquals(2, loaded.size());
  }

  @Test
  void read_returnsDefaultConfigWhenFileDoesNotExist() {
    File file = new File(tempDir, "missing.json");
    SettingsConfig defaultConfig = newDefaultConfig(false);
    ConfigLoader<SettingsConfig> loader = new ConfigLoader<>(defaultConfig, file, config -> { });

    SettingsConfig result = loader.read();

    assertEquals(defaultConfig, result);
  }

  @Test
  void read_returnsDefaultConfigWhenFileContentsAreInvalidJson() throws IOException {
    File file = new File(tempDir, "settings.json");
    try (FileWriter writer = new FileWriter(file)) {
      writer.write("not valid json {{{");
    }

    SettingsConfig defaultConfig = newDefaultConfig(false);
    ConfigLoader<SettingsConfig> loader = new ConfigLoader<>(defaultConfig, file, config -> { });

    SettingsConfig result = loader.read();

    assertEquals(defaultConfig, result);
  }

  @Test
  void read_parsesWellFormedJsonIntoTheConfigType() throws IOException {
    File file = new File(tempDir, "settings.json");
    try (FileWriter writer = new FileWriter(file)) {
      writer.write("{\"rollTurningForce\":9.5}");
    }

    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), file, config -> { });

    SettingsConfig result = loader.read();

    assertEquals(9.5f, result.rollTurningForce, 1e-6f);
  }

  @Test
  void save_writesJsonRepresentationOfConfigToFile() throws IOException {
    File file = new File(tempDir, "settings.json");
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), file, config -> { });
    SettingsConfig toSave = newDefaultConfig(false);
    toSave.rollTurningForce = 3.5f;

    loader.save(toSave);

    SettingsConfig readBack = loader.read();
    assertEquals(3.5f, readBack.rollTurningForce, 1e-6f);
  }

  @Test
  void save_swallowsIoFailureWhenTargetCannotBeWritten() {
    // A directory can never be opened as a FileWriter target -- exercises
    // save()'s catch branch without needing filesystem-permission tricks.
    File directoryAsFile = tempDir;
    ConfigLoader<SettingsConfig> loader =
        new ConfigLoader<>(newDefaultConfig(false), directoryAsFile, config -> { });

    loader.save(newDefaultConfig(false));
    // No exception propagates; the failure is only logged internally.
  }
}
