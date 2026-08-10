package net.torocraft.flighthud;

//? if fabric && <1.20 {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?} elif fabric && <1.21.2 {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
*///?} elif fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
*///?} elif fabric {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
//?} elif forge && <1.19 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;
*///?} elif forge && <1.20 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
*///?} elif forge {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
*///?}

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * Loader/version-conditional HUD render-hook registration. Replaces the old
 * {@code mixin/InGameHudMixin.java} (removed): MC 26.1+'s {@code Gui} class has
 * no {@code render(...)} method to inject into at all any more -- it now
 * exposes {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}
 * instead, a genuine render-state-extraction architecture change, not a rename
 * -- so hooking every loader's own official HUD-render event/registry directly
 * (exactly like the sibling critical-flight-details/critical-orientation mods
 * do for their own cross-cutting hooks) is both more portable AND unblocks
 * 26.x without needing Mixin at all.
 *
 * <p>The two NeoForge target versions (1.21.4 and 26.2) share the exact same
 * source below: {@code RenderGuiEvent.Post#getGuiGraphics()} returns {@code
 * GuiGraphics} on 1.21.4 and {@code GuiGraphicsExtractor} on 26.2 (confirmed
 * via javap against both real jars) -- NeoForge itself already adapted this
 * event to the 26.x architecture, and {@link PlatformCanvas} has a matching
 * constructor overload compiled per node, so the identical call site {@code
 * new PlatformCanvas(event.getGuiGraphics())} resolves correctly either way
 * with zero further branching needed.
 */
public final class HudHooks {
  private static final HudRenderer HUD = new HudRenderer();

  private HudHooks() {}

  /** Called from the Fabric/NeoForge/Forge&lt;1.19 imperative registration sites. */
  public static void register() {
    //? if fabric && <1.20 {
    /*HudRenderCallback.EVENT.register((poseStack, tickDelta) ->
        HUD.render(new PlatformCanvas(poseStack), tickDelta, Minecraft.getInstance()));
    *///?} elif fabric && <1.21.2 {
    /*HudRenderCallback.EVENT.register((GuiGraphics context, float tickDelta) ->
        HUD.render(new PlatformCanvas(context), tickDelta, Minecraft.getInstance()));
    *///?} elif fabric && >=26.1 {
    /*HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("flighthud", "hud"),
        (GuiGraphicsExtractor context, DeltaTracker deltaTracker) ->
            HUD.render(new PlatformCanvas(context),
                deltaTracker.getGameTimeDeltaPartialTick(false), Minecraft.getInstance()));
    *///?} elif fabric {
    /*HudRenderCallback.EVENT.register((GuiGraphics context, DeltaTracker deltaTracker) ->
        HUD.render(new PlatformCanvas(context),
            deltaTracker.getGameTimeDeltaPartialTick(false), Minecraft.getInstance()));
    *///?} elif neoforge {
    NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) ->
        HUD.render(new PlatformCanvas(event.getGuiGraphics()),
            event.getPartialTick().getGameTimeDeltaPartialTick(false), Minecraft.getInstance()));
    //?} elif forge && <1.19 {
    /*OverlayRegistry.registerOverlayTop("flighthud",
        (ForgeIngameGui gui, PoseStack poseStack, float tickDelta, int width, int height) ->
            HUD.render(new PlatformCanvas(poseStack), tickDelta, Minecraft.getInstance()));
    *///?}
    // forge>=1.19 is intentionally absent here: RegisterGuiOverlaysEvent must be
    // registered on the mod event bus at construction time, not called
    // imperatively -- see registerOverlays(...) below and its wiring in
    // FlightHud.java's constructor.
  }

  //? if forge && <1.20 {
  /*public static void registerOverlays(RegisterGuiOverlaysEvent event) {
    event.registerAboveAll("flighthud",
        (ForgeGui gui, PoseStack poseStack, float tickDelta, int width, int height) ->
            HUD.render(new PlatformCanvas(poseStack), tickDelta, Minecraft.getInstance()));
  }
  *///?} elif forge {
  /*public static void registerOverlays(RegisterGuiOverlaysEvent event) {
    event.registerAboveAll("flighthud",
        (ForgeGui gui, GuiGraphics context, float tickDelta, int width, int height) ->
            HUD.render(new PlatformCanvas(context), tickDelta, Minecraft.getInstance()));
  }
  *///?}
}
