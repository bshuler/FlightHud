package net.torocraft.flighthud;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FlightComputer {
  private static final float TICKS_PER_SECOND = 20;

  private float previousRollAngle = 0.0f;

  public Vec3 velocity;
  public float speed;
  public float pitch;
  public float heading;
  public Vec3 flightPath;
  public float flightPitch;
  public float flightHeading;
  public float roll;
  public float altitude;
  public Integer groundLevel;
  public Float distanceFromGround;
  public Float elytraHealth;

  public void update(Minecraft client, float partial) {
    velocity = client.player.getDeltaMovement();
    pitch = computePitch(client, partial);
    speed = computeSpeed(client);
    roll = computeRoll(client, partial);
    heading = computeHeading(client);
    altitude = computeAltitude(client);
    groundLevel = computeGroundLevel(client);
    distanceFromGround = computeDistanceFromGround(client, altitude, groundLevel);
    flightPitch = computeFlightPitch(velocity, pitch);
    flightHeading = computeFlightHeading(velocity, heading);
    elytraHealth = computeElytraHealth(client);
  }

  private Float computeElytraHealth(Minecraft client) {
    ItemStack stack = client.player.getItemBySlot(EquipmentSlot.CHEST);
    if (stack != null && stack.getItem() == Items.ELYTRA) {
      return FlightComputerMath.elytraHealthPercent(stack.getMaxDamage(), stack.getDamageValue());
    }
    return null;
  }

  private float computeFlightPitch(Vec3 velocity, float pitch) {
    return FlightComputerMath.computeFlightPitch(velocity.x, velocity.y, velocity.z, pitch);
  }

  private float computeFlightHeading(Vec3 velocity, float heading) {
    return FlightComputerMath.computeFlightHeading(velocity.x, velocity.y, velocity.z, heading);
  }

  /**
   * Roll logic is from:
   * https://github.com/Jorbon/cool_elytra/blob/main/src/main/java/edu/jorbonism/cool_elytra/mixin/GameRendererMixin.java
   * to enable both mods will sync up when used together.
   */
  private float computeRoll(Minecraft client, float partial) {
    if (!FlightHud.CONFIG_SETTINGS.calculateRoll) {
      return 0;
    }

    float wingPower = FlightHud.CONFIG_SETTINGS.rollTurningForce;
    float rollSmoothing = FlightHud.CONFIG_SETTINGS.rollSmoothing;
    Vec3 facing = client.player.getViewVector(partial);
    Vec3 velocity = client.player.getDeltaMovement();

    float rollAngle = FlightComputerMath.computeRollAngle(facing.x, facing.z, velocity.x, velocity.z, wingPower,
        rollSmoothing, previousRollAngle);
    previousRollAngle = rollAngle;

    return rollAngle;
  }

  private float computePitch(Minecraft client, float parital) {
    return client.player.getViewXRot(parital) * -1;
  }

  private boolean isGround(BlockPos pos, Minecraft client) {
    BlockState block = client.level.getBlockState(pos);
    return !block.isAir();
  }

  public BlockPos findGround(Minecraft client) {
    BlockPos pos = client.player.blockPosition();
    while (pos.getY() >= 0) {
      pos = pos.below();
      if (isGround(pos, client)) {
        return pos;
      }
    }
    return null;
  }

  private Integer computeGroundLevel(Minecraft client) {
    BlockPos ground = findGround(client);
    return ground == null ? null : ground.getY();
  }

  private Float computeDistanceFromGround(Minecraft client, float altitude,
      Integer groundLevel) {
    return FlightComputerMath.computeDistanceFromGround(altitude, groundLevel);
  }

  private float computeAltitude(Minecraft client) {
    return (float) client.player.position().y - 1;
  }

  private float computeHeading(Minecraft client) {
    return FlightComputerMath.toHeading(client.player.getYRot());
  }

  private float computeSpeed(Minecraft client) {
    float speed = 0;
    var player = client.player;
    if (player.getVehicle() != null) {
      Entity entity = player.getVehicle();
      speed = (float) entity.getDeltaMovement().length() * TICKS_PER_SECOND;
    } else {
      speed = (float) client.player.getDeltaMovement().length() * TICKS_PER_SECOND;
    }
    return speed;
  }
}
