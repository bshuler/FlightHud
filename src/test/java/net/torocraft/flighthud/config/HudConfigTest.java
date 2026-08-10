package net.torocraft.flighthud.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class HudConfigTest {

  @Test
  void update_recomputesHalfThicknessFromThickness() {
    HudConfig config = new HudConfig();
    config.thickness = 3.0f;
    config.update();
    assertEquals(1.5f, config.halfThickness, 1e-6f);
  }

  @Test
  void update_recomputesColorFromRgbComponentsWhenValid() {
    HudConfig config = new HudConfig();
    config.color_red = 10;
    config.color_green = 20;
    config.color_blue = 30;
    config.update();
    assertEquals(new Color(10, 20, 30).getRGB(), config.color);
  }

  @Test
  void update_fallsBackToGreenWhenColorComponentsAreOutOfRange() {
    HudConfig config = new HudConfig();
    // java.awt.Color's constructor throws IllegalArgumentException for any
    // component outside [0, 255] -- exercises updateColor()'s catch branch.
    config.color_red = 999;
    config.color_green = 0;
    config.color_blue = 0;
    config.update();
    assertEquals(Color.GREEN.getRGB(), config.color);
  }

  @Test
  void shouldWatch_reflectsWatchForConfigChangesFlag() {
    HudConfig config = new HudConfig();
    assertTrue(config.shouldWatch());

    config.watchForConfigChanges = false;
    assertFalse(config.shouldWatch());
  }

  @Test
  void getDefaultMinSettings_disablesScalesAndExtraReadouts() {
    HudConfig config = HudConfig.getDefaultMinSettings();

    assertFalse(config.altitude_showScale);
    assertFalse(config.speed_showScale);
    assertFalse(config.heading_showScale);
    assertFalse(config.altitude_showGroundInfo);
    assertFalse(config.pitchLadder_showLadder);
    assertEquals(0f, config.pitchLadder_optimumClimbAngle);
    assertEquals(0f, config.pitchLadder_optimumGlideAngle);
    assertFalse(config.elytra_showHealth);
    assertFalse(config.flightPath_show);
    assertFalse(config.altitude_showHeight);
    assertFalse(config.pitchLadder_showHorizon);
  }
}
