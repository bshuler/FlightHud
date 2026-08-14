package net.torocraft.flighthud.gametest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.torocraft.flighthud.FlightHud;
import net.torocraft.flighthud.HudComponent;
import net.torocraft.flighthud.config.SettingsConfig.DisplayMode;

/**
 * Tier 3: the mod running inside a <strong>real Minecraft client</strong> --
 * real window, real render thread, real game loop, real world.
 *
 * <p>This exists because of one specific hole the other tiers cannot cover.
 * The unit suite proves the maths; Tier 1 ({@code LoadedGameTest}, via
 * fabric-loader-junit) proves a real loader discovers this mod and that its
 * {@code fabric.mod.json} ranges resolve. Neither of them renders a single
 * frame, so neither can tell you whether {@code HudHooks.register()} picked
 * the <em>right</em> hook for this Minecraft version. That is exactly the
 * thing that broke on 26.x -- {@code Gui} lost the {@code render(...)} method
 * the mod used to mixin into -- and a mod whose HUD silently never draws
 * still compiles, still loads, and still passes every other test in this
 * repo. This test fails in that situation.
 *
 * <p>What it asserts:
 *
 * <ol>
 *   <li>the client reaches the title screen at all -- i.e. the mod's client
 *       entrypoint ran without killing startup;</li>
 *   <li>the loader running that client really has {@code flighthud} loaded
 *       (a guard on the harness: without it, everything below could pass with
 *       the mod jar absent from the classpath);</li>
 *   <li>the HUD render hook <strong>actually fires in-world</strong>, proven
 *       by {@code HudComponent.CONFIG} -- which is only ever assigned by
 *       {@code HudRenderer.render(...)}, the first statement in it -- going
 *       from null to non-null while a world is open;</li>
 *   <li>rendering the full component chain throws nothing. This needs its own
 *       mechanism: {@code HudRenderer.render(...)} wraps the whole draw in
 *       {@code catch (Exception e) { e.printStackTrace(); }}, so a broken
 *       component prints a stack trace and the game carries on looking fine.
 *       Stderr is therefore tee'd for the duration and any FlightHud frame in
 *       it fails the test.</li>
 * </ol>
 *
 * <p>Screenshots of the title screen and of the in-world HUD are written to
 * the run directory on every run as evidence.
 *
 * <p><strong>Written to need no version branching, though it could have it.</strong>
 * Stonecutter does preprocess this source set -- Loom's {@code createSourceSet}
 * leaves {@code gametest} on Gradle's default convention and Stonecutter picks
 * it up from there, generating it through {@code stonecutterGenerateGametest}
 * exactly like {@code main} and {@code test} (verified by running that task and
 * reading the generated tree). So {@code //? if} markers would work here. There
 * are none because they are not needed: the file is shared by the 1.21.4 and
 * 26.2 Fabric cells (see {@code build.gradle.kts}, {@code clientGameTestSupported}),
 * which ship client-gametest API v4.1.1 and v6.0.0 respectively, and only the
 * API surface those two versions have in common is used -- checked by javap
 * against both jars and then confirmed by compiling this one un-branched source
 * on both cells. In particular {@code TestSingleplayerContext.getClientWorld()}
 * (v4 only) and {@code getConnection()} (v6 only) are both avoided.
 */
public class FlightHudClientGameTest implements FabricClientGameTest {
  /** Ticks to sit in the world with the HUD forced on. */
  private static final int IN_WORLD_TICKS = 40;

  @Override
  public void runTest(ClientGameTestContext context) {
    context.waitForScreen(TitleScreen.class);
    context.takeScreenshot("flighthud-title-screen");

    if (!FabricLoader.getInstance().isModLoaded(FlightHud.MODID)) {
      throw new AssertionError("the client booted without the '" + FlightHud.MODID
          + "' mod loaded - everything below this point would have passed vacuously");
    }

    // The HUD only draws when the display mode for the player's current state
    // is MIN or FULL, and the shipped default for "not flying" is NONE. The
    // test does not fly (that would need an elytra, altitude and sustained
    // input), so force the not-flying mode to FULL, which drives every one of
    // the seven components through a real draw.
    String originalMode = FlightHud.CONFIG_SETTINGS.displayModeWhenNotFlying;
    context.runOnClient(client -> {
      FlightHud.CONFIG_SETTINGS.displayModeWhenNotFlying = DisplayMode.FULL.toString();
      // Clear it so a non-null reading later can only have come from a render
      // that happened after this point.
      HudComponent.CONFIG = null;
    });

    PrintStream originalErr = System.err;
    ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(new TeeOutputStream(originalErr, capturedErr), true,
        StandardCharsets.UTF_8));

    try {
      try (TestSingleplayerContext singleplayer =
          context.worldBuilder().setUseConsistentSettings(true).create()) {
        context.waitTicks(IN_WORLD_TICKS);

        boolean hookFired = context.computeOnClient(client -> HudComponent.CONFIG != null);
        if (!hookFired) {
          throw new AssertionError("the HUD render hook never fired: after " + IN_WORLD_TICKS
              + " in-world ticks HudComponent.CONFIG is still null, which means "
              + "HudRenderer.render(...) was never called. HudHooks.register() has "
              + "registered against a hook this Minecraft version does not fire.");
        }

        context.takeScreenshot("flighthud-in-world");
        // Keep the singleplayer context referenced until here so it is
        // unambiguous that the assertions above ran with the world open.
        singleplayer.getWorldSave();
      }
    } finally {
      System.setErr(originalErr);
      context.runOnClient(
          client -> FlightHud.CONFIG_SETTINGS.displayModeWhenNotFlying = originalMode);
    }

    String stderr = capturedErr.toString(StandardCharsets.UTF_8);
    if (stderr.contains("net.torocraft.flighthud")) {
      throw new AssertionError("something in the HUD render path threw while the world was "
          + "open. HudRenderer.render(...) swallows exceptions and only printStackTrace()s "
          + "them, so this would otherwise be a silent pass. Captured stderr:\n" + stderr);
    }

    // The client gametest runner requires the test to end on the title screen
    // with no server running and no connection open.
    context.waitForScreen(TitleScreen.class);
  }

  /**
   * Writes to both streams. Deliberately hand-rolled rather than using
   * commons-io's {@code TeeOutputStream}: commons-io is a transitive Minecraft
   * dependency, not a declared one, and this class must not acquire a
   * dependency that a future Minecraft version could drop.
   */
  private static final class TeeOutputStream extends OutputStream {
    private final OutputStream first;
    private final OutputStream second;

    private TeeOutputStream(OutputStream first, OutputStream second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public void write(int b) throws IOException {
      first.write(b);
      second.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      first.write(b, off, len);
      second.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      first.flush();
      second.flush();
    }
  }
}
