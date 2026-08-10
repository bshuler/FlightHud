package net.torocraft.flighthud;

//? if <1.20 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?} elif >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
*///?} else {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
//?}

import net.minecraft.client.gui.Font;

/**
 * The ONE version-conditional file behind the {@link FlightCanvas} abstraction.
 * Every other rendering-logic file ({@code HudComponent}, {@code HudRenderer},
 * every {@code components/*} class) is written once against {@link FlightCanvas}
 * and never branches on Stonecutter conditions at all.
 *
 * <p>Three real API shapes exist across the matrix (all confirmed via {@code
 * javap} against the real per-node jars, not assumption):
 * <ul>
 *   <li>{@code <1.20} (1.18.2, 1.19.4): no {@code GuiGraphics} class exists yet.
 *       Drawing goes through a raw {@code PoseStack} plus the static helper
 *       {@code GuiComponent.fill(PoseStack, ...)}; text goes through
 *       {@code Font.draw(PoseStack, String, float, float, int)}.</li>
 *   <li>{@code >=26.1} (26.2): {@code GuiGraphics} was removed outright and
 *       replaced by {@code GuiGraphicsExtractor}, a render-state-extraction
 *       object with renamed methods ({@code text(...)} not {@code
 *       drawString(...)}) and a 2D-only {@code org.joml.Matrix3x2fStack} (not
 *       {@code PoseStack}) behind {@code pose()}.</li>
 *   <li>else (1.20.1, 1.21.4): the "classic" {@code GuiGraphics} shape, wrapping
 *       a {@code PoseStack} behind {@code pose()}.</li>
 * </ul>
 */
public final class PlatformCanvas implements FlightCanvas {

  //? if <1.20 {
  /*private final PoseStack pose;

  public PlatformCanvas(PoseStack pose) {
    this.pose = pose;
  }

  @Override
  public void fill(int x1, int y1, int x2, int y2, int color) {
    GuiComponent.fill(pose, x1, y1, x2, y2, color);
  }

  @Override
  public void drawText(Font font, String text, int x, int y, int color, boolean shadow) {
    if (shadow) {
      font.drawShadow(pose, text, (float) x, (float) y, color);
    } else {
      font.draw(pose, text, (float) x, (float) y, color);
    }
  }

  @Override
  public void pushPose() {
    pose.pushPose();
  }

  @Override
  public void popPose() {
    pose.popPose();
  }

  @Override
  public void translate(float x, float y) {
    pose.translate(x, y, 0);
  }

  @Override
  public void rotateDegrees(float degrees) {
    // com.mojang.math.Axis doesn't exist in 1.18.2 (confirmed via javap: it
    // was introduced alongside PoseStack.mulPose(Quaternion) -> mulPose(JOML
    // Quaternionf) switching between 1.18.2 and 1.19.4) -- 1.18.2 needs the
    // older Vector3f.ZP.rotationDegrees(float), which returns the pre-JOML
    // com.mojang.math.Quaternion matching its PoseStack.mulPose(Quaternion)
    // overload; 1.19.4 already has Axis (returning org.joml.Quaternionf,
    // matching its PoseStack.mulPose(Quaternionf) overload).
    //? if <1.19 {
    /^pose.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(degrees));
    ^///?} else {
    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
    //?}
  }

  @Override
  public void scale(float x, float y) {
    pose.scale(x, y, 1);
  }
  *///?} elif >=26.1 {
  /*private final GuiGraphicsExtractor context;

  public PlatformCanvas(GuiGraphicsExtractor context) {
    this.context = context;
  }

  @Override
  public void fill(int x1, int y1, int x2, int y2, int color) {
    context.fill(x1, y1, x2, y2, color);
  }

  @Override
  public void drawText(Font font, String text, int x, int y, int color, boolean shadow) {
    context.text(font, text, x, y, color, shadow);
  }

  @Override
  public void pushPose() {
    context.pose().pushMatrix();
  }

  @Override
  public void popPose() {
    context.pose().popMatrix();
  }

  @Override
  public void translate(float x, float y) {
    context.pose().translate(x, y);
  }

  @Override
  public void rotateDegrees(float degrees) {
    context.pose().rotate((float) Math.toRadians(degrees));
  }

  @Override
  public void scale(float x, float y) {
    context.pose().scale(x, y);
  }
  *///?} else {
  private final GuiGraphics context;

  public PlatformCanvas(GuiGraphics context) {
    this.context = context;
  }

  @Override
  public void fill(int x1, int y1, int x2, int y2, int color) {
    context.fill(x1, y1, x2, y2, color);
  }

  @Override
  public void drawText(Font font, String text, int x, int y, int color, boolean shadow) {
    context.drawString(font, text, x, y, color, shadow);
  }

  @Override
  public void pushPose() {
    context.pose().pushPose();
  }

  @Override
  public void popPose() {
    context.pose().popPose();
  }

  @Override
  public void translate(float x, float y) {
    context.pose().translate(x, y, 0);
  }

  @Override
  public void rotateDegrees(float degrees) {
    context.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
  }

  @Override
  public void scale(float x, float y) {
    context.pose().scale(x, y, 1);
  }
  //?}
}
