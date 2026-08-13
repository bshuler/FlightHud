package net.torocraft.flighthud;

/**
 * Pure, loader-agnostic flight math extracted from {@link FlightComputer} and
 * {@link HudComponent}: no Minecraft-class dependency at all (only
 * primitives), so it runs unmodified on every loader/version subproject and
 * is fully unit-testable, mirroring the sibling {@code critical-flight-details}
 * mod's {@code FlightHudMath} extraction pattern.
 *
 * <p>{@link FlightComputer}'s own {@code compute*} methods stay as thin
 * Minecraft-touching wrappers that pull primitives (velocity components,
 * yaw/pitch, config floats) out of the live {@code Minecraft}/{@code Vec3}
 * objects and hand them to the methods below - the actual arithmetic lives
 * here exactly once instead of being duplicated per call site.
 */
public final class FlightComputerMath {

  private FlightComputerMath() {}

  /** Normalizes a yaw in degrees to the mod's 0-360 heading convention. */
  public static float toHeading(float yawDegrees) {
    return (yawDegrees + 180) % 360;
  }

  /**
   * Wraps an arbitrary degree value into {@code [0, 360)}, handling negative
   * input (unlike the plain {@code %} operator, which can return a negative
   * result in Java).
   */
  public static float wrapHeading(float degrees) {
    degrees = degrees % 360;
    while (degrees < 0) {
      degrees += 360;
    }
    return degrees;
  }

  /** Rounds to the nearest {@code long}, half-up (ties away from zero for positive input). */
  public static int roundHalfUp(double d) {
    return (int) Math.round(d);
  }

  /**
   * The pitch of the current velocity vector itself (as opposed to the
   * camera pitch) - falls back to {@code pitch} when the velocity is too
   * small to have a meaningful direction.
   */
  public static float computeFlightPitch(double velocityX, double velocityY, double velocityZ, float pitch) {
    double length = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    if (length < 0.01) {
      return pitch;
    }
    double normalizedY = velocityY / length;
    return (float) (90 - Math.toDegrees(Math.acos(normalizedY)));
  }

  /**
   * The heading of the current velocity vector itself (as opposed to the
   * camera yaw) - falls back to {@code heading} when the velocity is too
   * small to have a meaningful direction.
   */
  public static float computeFlightHeading(double velocityX, double velocityY, double velocityZ, float heading) {
    double length = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    if (length < 0.01) {
      return heading;
    }
    return toHeading((float) Math.toDegrees(-Math.atan2(velocityX, velocityZ)));
  }

  /**
   * Elytra-style banking roll angle from the player's facing vs. velocity
   * vectors, smoothed against the previous frame's angle. Ported verbatim
   * (arithmetic unchanged) from {@code FlightComputer.computeRoll}; see:
   * https://github.com/Jorbon/cool_elytra/blob/main/src/main/java/edu/jorbonism/cool_elytra/mixin/GameRendererMixin.java
   */
  public static float computeRollAngle(double facingX, double facingZ, double velocityX, double velocityZ,
      float wingPower, float rollSmoothing, float previousRollAngle) {
    double horizontalFacing2 = facingX * facingX + facingZ * facingZ;
    double horizontalSpeed2 = velocityX * velocityX + velocityZ * velocityZ;

    float rollAngle = 0.0f;

    if (horizontalFacing2 > 0.0D && horizontalSpeed2 > 0.0D) {
      double dot = (velocityX * facingX + velocityZ * facingZ) / Math.sqrt(horizontalFacing2 * horizontalSpeed2);
      dot = Math.max(-1.0, Math.min(1.0, dot));
      double direction = Math.signum(velocityX * facingZ - velocityZ * facingX);
      rollAngle = (float) (Math.atan(Math.sqrt(horizontalSpeed2) * Math.acos(dot) * wingPower) * direction
          * 57.29577951308);
    }

    return (float) ((1.0 - rollSmoothing) * rollAngle + rollSmoothing * previousRollAngle);
  }

  /** Distance above the ground, clamped to non-negative; {@code null} when there's no ground reading. */
  public static Float computeDistanceFromGround(float altitude, Integer groundLevel) {
    if (groundLevel == null) {
      return null;
    }
    return Math.max(0f, altitude - groundLevel);
  }

  /**
   * Remaining elytra durability as a percentage, from an item stack's raw
   * max-damage and current-damage values.
   *
   * <p>Extracted from {@code FlightComputer.computeElytraHealth} so it can be
   * exercised against a <em>real</em> {@code ItemStack(Items.ELYTRA)} in
   * LoadedGameTest: elytra durability is game data, and it has changed
   * representation before (item field pre-1.20.5, data component after), so
   * the readout is worth pinning to the real item rather than to a constant
   * copied into a test.
   */
  public static float elytraHealthPercent(int maxDamage, int damageValue) {
    return ((float) maxDamage - (float) damageValue) / (float) maxDamage * 100f;
  }
}
