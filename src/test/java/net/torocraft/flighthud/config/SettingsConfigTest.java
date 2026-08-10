package net.torocraft.flighthud.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.torocraft.flighthud.config.SettingsConfig.DisplayMode;
import org.junit.jupiter.api.Test;

class SettingsConfigTest {

  @Test
  void toggleDisplayMode_cyclesFlyingModeThroughAllThreeValuesAndWraps() {
    SettingsConfig config = new SettingsConfig();
    assertEquals(DisplayMode.FULL.toString(), config.displayModeWhenFlying);

    config.toggleDisplayMode(true);
    assertEquals(DisplayMode.NONE.toString(), config.displayModeWhenFlying);

    config.toggleDisplayMode(true);
    assertEquals(DisplayMode.MIN.toString(), config.displayModeWhenFlying);

    config.toggleDisplayMode(true);
    assertEquals(DisplayMode.FULL.toString(), config.displayModeWhenFlying);

    // Only the "flying" field should ever move here.
    assertEquals(DisplayMode.NONE.toString(), config.displayModeWhenNotFlying);
  }

  @Test
  void toggleDisplayMode_cyclesNotFlyingModeIndependently() {
    SettingsConfig config = new SettingsConfig();
    assertEquals(DisplayMode.NONE.toString(), config.displayModeWhenNotFlying);

    config.toggleDisplayMode(false);
    assertEquals(DisplayMode.MIN.toString(), config.displayModeWhenNotFlying);
    // The "flying" field is untouched by a not-flying toggle.
    assertEquals(DisplayMode.FULL.toString(), config.displayModeWhenFlying);

    config.toggleDisplayMode(false);
    assertEquals(DisplayMode.FULL.toString(), config.displayModeWhenNotFlying);
  }

  @Test
  void parseDisplayMode_parsesEachKnownValue() {
    assertEquals(DisplayMode.NONE, SettingsConfig.parseDisplayMode("NONE"));
    assertEquals(DisplayMode.MIN, SettingsConfig.parseDisplayMode("MIN"));
    assertEquals(DisplayMode.FULL, SettingsConfig.parseDisplayMode("FULL"));
  }

  @Test
  void parseDisplayMode_fallsBackToNoneForUnknownOrNullInput() {
    assertEquals(DisplayMode.NONE, SettingsConfig.parseDisplayMode("not-a-mode"));
    assertEquals(DisplayMode.NONE, SettingsConfig.parseDisplayMode(null));
  }

  @Test
  void shouldWatch_reflectsWatchForConfigChangesFlag() {
    SettingsConfig config = new SettingsConfig();
    assertTrue(config.shouldWatch());

    config.watchForConfigChanges = false;
    assertFalse(config.shouldWatch());
  }

  @Test
  void update_isANoOpThatLeavesEveryFieldUnchanged() {
    SettingsConfig config = new SettingsConfig();
    config.toggleDisplayMode(true);
    String flying = config.displayModeWhenFlying;
    String notFlying = config.displayModeWhenNotFlying;
    boolean calculateRoll = config.calculateRoll;
    float rollTurningForce = config.rollTurningForce;
    float rollSmoothing = config.rollSmoothing;

    config.update();

    assertEquals(flying, config.displayModeWhenFlying);
    assertEquals(notFlying, config.displayModeWhenNotFlying);
    assertEquals(calculateRoll, config.calculateRoll);
    assertEquals(rollTurningForce, config.rollTurningForce);
    assertEquals(rollSmoothing, config.rollSmoothing);
  }
}
