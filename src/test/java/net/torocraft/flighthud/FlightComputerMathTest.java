package net.torocraft.flighthud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightComputerMathTest {

  private static final float DELTA = 1e-4f;

  @Test
  void toHeading_wrapsYawIntoZeroToThreeSixty() {
    assertEquals(180f, FlightComputerMath.toHeading(0f), DELTA);
    assertEquals(0f, FlightComputerMath.toHeading(180f), DELTA);
    assertEquals(0f, FlightComputerMath.toHeading(-180f), DELTA);
    assertEquals(270f, FlightComputerMath.toHeading(90f), DELTA);
  }

  @Test
  void wrapHeading_leavesInRangeValueUnchanged() {
    assertEquals(45f, FlightComputerMath.wrapHeading(45f), DELTA);
    assertEquals(0f, FlightComputerMath.wrapHeading(0f), DELTA);
  }

  @Test
  void wrapHeading_wrapsValueAtOrAboveThreeSixty() {
    assertEquals(0f, FlightComputerMath.wrapHeading(360f), DELTA);
    assertEquals(10f, FlightComputerMath.wrapHeading(370f), DELTA);
    assertEquals(10f, FlightComputerMath.wrapHeading(730f), DELTA);
  }

  @Test
  void wrapHeading_wrapsNegativeValueViaWhileLoop() {
    assertEquals(350f, FlightComputerMath.wrapHeading(-10f), DELTA);
    // Exercises multiple while-loop iterations, not just a single +360 step.
    assertEquals(350f, FlightComputerMath.wrapHeading(-370f), DELTA);
  }

  @Test
  void roundHalfUp_roundsTiesAwayFromZeroForPositiveInput() {
    assertEquals(3, FlightComputerMath.roundHalfUp(2.5));
    assertEquals(2, FlightComputerMath.roundHalfUp(2.4));
    assertEquals(0, FlightComputerMath.roundHalfUp(0.0));
  }

  @Test
  void computeFlightPitch_fallsBackToCameraPitchWhenVelocityIsNegligible() {
    assertEquals(12.5f, FlightComputerMath.computeFlightPitch(0.0, 0.0, 0.0, 12.5f), DELTA);
    assertEquals(12.5f, FlightComputerMath.computeFlightPitch(0.001, 0.001, 0.001, 12.5f), DELTA);
  }

  @Test
  void computeFlightPitch_levelFlightIsApproximatelyZero() {
    // Purely horizontal velocity (no vertical component) should read ~0 pitch.
    assertEquals(0f, FlightComputerMath.computeFlightPitch(1.0, 0.0, 0.0, 99f), DELTA);
  }

  @Test
  void computeFlightPitch_straightDownIsMinusNinety() {
    assertEquals(-90f, FlightComputerMath.computeFlightPitch(0.0, -1.0, 0.0, 0f), DELTA);
  }

  @Test
  void computeFlightPitch_straightUpIsNinety() {
    assertEquals(90f, FlightComputerMath.computeFlightPitch(0.0, 1.0, 0.0, 0f), DELTA);
  }

  @Test
  void computeFlightHeading_fallsBackToCameraHeadingWhenVelocityIsNegligible() {
    assertEquals(77f, FlightComputerMath.computeFlightHeading(0.0, 0.0, 0.0, 77f), DELTA);
    assertEquals(77f, FlightComputerMath.computeFlightHeading(0.001, 0.0, 0.001, 77f), DELTA);
  }

  @Test
  void computeFlightHeading_derivedFromVelocityDirectionWhenSignificant() {
    // Moving along +Z should read a stable, non-fallback heading.
    float heading = FlightComputerMath.computeFlightHeading(0.0, 0.0, 1.0, 999f);
    assertTrue(heading >= 0f && heading < 360f);
    assertEquals(180f, heading, DELTA);
  }

  @Test
  void computeRollAngle_zeroWhenFacingVectorIsZero() {
    float roll = FlightComputerMath.computeRollAngle(0.0, 0.0, 1.0, 1.0, 1.25f, 0.0f, 5.0f);
    // rollSmoothing = 0 -> result is exactly the freshly computed (zero) rollAngle.
    assertEquals(0f, roll, DELTA);
  }

  @Test
  void computeRollAngle_zeroWhenVelocityVectorIsZero() {
    float roll = FlightComputerMath.computeRollAngle(1.0, 1.0, 0.0, 0.0, 1.25f, 0.0f, 5.0f);
    assertEquals(0f, roll, DELTA);
  }

  @Test
  void computeRollAngle_nonZeroWhenBankingAcrossFacing() {
    // Facing straight along +Z, moving diagonally: a genuine banking angle should result.
    float roll = FlightComputerMath.computeRollAngle(0.0, 1.0, 1.0, 1.0, 1.25f, 0.0f, 0.0f);
    assertTrue(Math.abs(roll) > 0.01f, "expected a non-trivial roll angle, got " + roll);
  }

  @Test
  void computeRollAngle_dotIsClampedAtParallelFacingAndVelocity() {
    // facing == velocity direction exactly -> dot would compute to 1.0 without
    // clamping; clamped or not, acos(1.0) = 0, so this exercises the dot=1 path
    // without producing NaN.
    float roll = FlightComputerMath.computeRollAngle(1.0, 0.0, 2.0, 0.0, 1.25f, 0.0f, 0.0f);
    assertEquals(0f, roll, DELTA);
    assertTrue(!Float.isNaN(roll));
  }

  @Test
  void computeRollAngle_opposingFacingAndVelocityDoesNotProduceNaN() {
    // facing and velocity anti-parallel -> dot would compute to -1.0 without
    // clamping; verifies the Math.max/min clamp keeps acos() well-defined.
    float roll = FlightComputerMath.computeRollAngle(1.0, 0.0, -2.0, 0.0, 1.25f, 0.0f, 0.0f);
    assertTrue(!Float.isNaN(roll));
  }

  @Test
  void computeRollAngle_smoothingBlendsTowardPreviousAngle() {
    // rollSmoothing = 1.0 -> result is exactly the previous angle, regardless
    // of the freshly computed one.
    float roll = FlightComputerMath.computeRollAngle(0.0, 1.0, 1.0, 1.0, 1.25f, 1.0f, 42.0f);
    assertEquals(42.0f, roll, DELTA);
  }

  @Test
  void computeDistanceFromGround_nullWhenNoGroundLevel() {
    assertNull(FlightComputerMath.computeDistanceFromGround(64.0f, null));
  }

  @Test
  void computeDistanceFromGround_differenceWhenAboveGround() {
    assertEquals(10.0f, FlightComputerMath.computeDistanceFromGround(74.0f, 64), DELTA);
  }

  @Test
  void computeDistanceFromGround_clampedToZeroWhenBelowGroundLevel() {
    assertEquals(0.0f, FlightComputerMath.computeDistanceFromGround(50.0f, 64), DELTA);
  }

  // elytraHealthPercent is also asserted in LoadedGameTest against a real
  // ItemStack(Items.ELYTRA)'s durability, which is the more meaningful check --
  // but that test is Fabric-only (`//? if fabric` wraps the whole file), so on
  // the Forge cells it is not compiled and this method would otherwise be the
  // one uncovered line in the whole bundle, failing the 100% coverage gate.
  // These cases carry the arithmetic on every cell; LoadedGameTest carries the
  // agreement-with-vanilla claim on the cells that can make it.

  @Test
  void elytraHealthPercent_fullWhenUndamaged() {
    assertEquals(100f, FlightComputerMath.elytraHealthPercent(432, 0), DELTA);
  }

  @Test
  void elytraHealthPercent_halfWhenHalfDamaged() {
    assertEquals(50f, FlightComputerMath.elytraHealthPercent(432, 216), DELTA);
  }

  @Test
  void elytraHealthPercent_zeroWhenFullyDamaged() {
    assertEquals(0f, FlightComputerMath.elytraHealthPercent(432, 432), DELTA);
  }
}
