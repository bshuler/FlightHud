package net.torocraft.flighthud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.torocraft.flighthud.config.HudConfig;

public abstract class HudComponent {
  public abstract void render(GuiGraphics context, float tickDelta, Minecraft client);

  public static HudConfig CONFIG;

  protected int i(double d) {
    return (int) Math.round(d);
  }

  protected void drawPointer(GuiGraphics context, float x, float y, float rot) {
    PoseStack pose = context.pose();
    pose.pushPose();
    pose.translate(x, y, 0);
    pose.mulPose(Axis.ZP.rotationDegrees(rot + 45));
    drawVerticalLine(context, 0, 0, 5);
    drawHorizontalLine(context, 0, 5, 0);
    pose.popPose();
  }

  protected float wrapHeading(float degrees) {
    degrees = degrees % 360;
    while (degrees < 0) {
      degrees += 360;
    }
    return degrees;
  }

  protected void drawFont(Minecraft mc, GuiGraphics context, String s, float x, float y) {
    drawFont(mc, context, s, x, y, CONFIG.color);
  }

  protected void drawFont(Minecraft mc, GuiGraphics context, String s, float x, float y,
      int color) {
    context.drawString(mc.font, s, (int) x, (int) y, CONFIG.color, false);
  }

  protected void drawRightAlignedFont(Minecraft mc, GuiGraphics context, String s, float x,
      float y) {
    int w = mc.font.width(s);
    drawFont(mc, context, s, x - w, y);
  }

  protected void drawBox(GuiGraphics context, float x, float y, float w, float h) {
    drawHorizontalLine(context, x, x + w, y);
    drawHorizontalLine(context, x, x + w, y + h);
    drawVerticalLine(context, x, y, y + h);
    drawVerticalLine(context, x + w, y, y + h);
  }

  protected void drawHorizontalLineDashed(GuiGraphics context, float x1, float x2, float y,
      int dashCount) {
    float width = x2 - x1;
    int segmentCount = dashCount * 2 - 1;
    float dashSize = width / segmentCount;
    for (int i = 0; i < segmentCount; i++) {
      if (i % 2 != 0) {
        continue;
      }
      float dx1 = i * dashSize + x1;
      float dx2;
      if (i == segmentCount - 1) {
        dx2 = x2;
      } else {
        dx2 = ((i + 1) * dashSize) + x1;
      }
      drawHorizontalLine(context, dx1, dx2, y);
    }
  }

  protected void drawHorizontalLine(GuiGraphics context, float x1, float x2, float y) {
    if (x2 < x1) {
      float i = x1;
      x1 = x2;
      x2 = i;
    }
    fill(context, x1 - CONFIG.halfThickness, y - CONFIG.halfThickness, x2 + CONFIG.halfThickness,
        y + CONFIG.halfThickness);
  }

  protected void drawVerticalLine(GuiGraphics context, float x, float y1, float y2) {
    if (y2 < y1) {
      float i = y1;
      y1 = y2;
      y2 = i;
    }

    fill(context, x - CONFIG.halfThickness, y1 + CONFIG.halfThickness, x + CONFIG.halfThickness,
        y2 - CONFIG.halfThickness);
  }

  // GuiGraphics.fill() is a built-in, pose-aware colored-quad primitive that
  // already applies context.pose()'s current transform -- no need to hand-roll
  // the immediate-mode Tessellator/BufferBuilder/RenderSystem plumbing the
  // pre-1.20 codebase used, since DrawContext/GuiGraphics didn't exist then.
  protected void fill(GuiGraphics context, float x1, float y1, float x2, float y2) {
    int color = CONFIG.color;
    context.fill(i(Math.min(x1, x2)), i(Math.min(y1, y2)), i(Math.max(x1, x2)),
        i(Math.max(y1, y2)), color);
  }
}
