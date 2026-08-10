package net.torocraft.flighthud.config;

import net.torocraft.flighthud.config.loader.IConfig;

public class SettingsConfig implements IConfig {

  public enum DisplayMode {
    NONE, MIN, FULL
  }

  public boolean watchForConfigChanges = true;
  public String displayModeWhenFlying = DisplayMode.FULL.toString();
  public String displayModeWhenNotFlying = DisplayMode.NONE.toString();
  public boolean calculateRoll = true;
  public float rollTurningForce = 1.25f;
  public float rollSmoothing = 0.85f;

  @Override
  public void update() {
  }

  @Override
  public boolean shouldWatch() {
    return watchForConfigChanges;
  }

  private static String toggle(String curr) {
    DisplayMode m = parseDisplayMode(curr);
    int i = (m.ordinal() + 1) % DisplayMode.values().length;
    return DisplayMode.values()[i].toString();
  }

  /**
   * Pure state mutation, deliberately split from the live-game orchestration
   * (reading {@code Minecraft.getInstance().player.isFallFlying()} and
   * persisting via {@code FlightHud.CONFIG_LOADER_SETTINGS.save(this)}) so
   * this method is unit-testable headless. Callers ({@code FlightHud}'s
   * {@code onClientTick()} and {@code SwitchDisplayModeCommand.run()}) own
   * both of those Minecraft-touching steps.
   */
  public void toggleDisplayMode(boolean isFallFlying) {
    if (isFallFlying) {
      displayModeWhenFlying = toggle(displayModeWhenFlying);
    } else {
      displayModeWhenNotFlying = toggle(displayModeWhenNotFlying);
    }
  }

  public static DisplayMode parseDisplayMode(String s) {
    try {
      return DisplayMode.valueOf(s);
    } catch (Exception e) {
      return DisplayMode.NONE;
    }
  }

}
