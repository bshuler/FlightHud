package net.torocraft.flighthud;

import net.minecraft.client.gui.Font;

/**
 * Loader/version-independent drawing surface for the HUD. {@code HudComponent},
 * {@code HudRenderer}, and every {@code components/*} class are written once
 * against this interface and never touch {@code GuiGraphics}/
 * {@code GuiGraphicsExtractor}/{@code PoseStack} directly, so none of them need
 * any Stonecutter {@code //? if} branching at all -- only {@link PlatformCanvas}
 * (one file, one small class per rendering era) and {@link HudHooks} (the
 * per-loader render-event registration) are version-conditional.
 *
 * <p>This sidesteps the real API-shape break at Minecraft 26.1+, where
 * {@code GuiGraphics} was replaced outright by {@code GuiGraphicsExtractor}
 * (a render-state-extraction object, not a direct draw context) with renamed
 * methods ({@code text(...)} instead of {@code drawString(...)}) and a 2D-only
 * {@code org.joml.Matrix3x2fStack} in place of {@code PoseStack} for
 * {@code pose()} -- and the older break at Minecraft 1.20, where
 * {@code GuiGraphics} did not exist yet at all and drawing went through a raw
 * {@code PoseStack} plus the static {@code GuiComponent.fill(...)} helper.
 */
public interface FlightCanvas {

  void fill(int x1, int y1, int x2, int y2, int color);

  void drawText(Font font, String text, int x, int y, int color, boolean shadow);

  void pushPose();

  void popPose();

  void translate(float x, float y);

  void rotateDegrees(float degrees);

  void scale(float x, float y);
}
