package net.torocraft.flighthud;

import net.minecraft.client.Minecraft;
import net.torocraft.flighthud.config.HudConfig;

public abstract class HudComponent {
  public abstract void render(FlightCanvas context, float tickDelta, Minecraft client);

  public static HudConfig CONFIG;

  protected int i(double d) {
    return FlightComputerMath.roundHalfUp(d);
  }

  protected void drawPointer(FlightCanvas context, float x, float y, float rot) {
    context.pushPose();
    context.translate(x, y);
    context.rotateDegrees(rot + 45);
    drawVerticalLine(context, 0, 0, 5);
    drawHorizontalLine(context, 0, 5, 0);
    context.popPose();
  }

  protected float wrapHeading(float degrees) {
    return FlightComputerMath.wrapHeading(degrees);
  }

  protected void drawFont(Minecraft mc, FlightCanvas context, String s, float x, float y) {
    drawFont(mc, context, s, x, y, CONFIG.color);
  }

  protected void drawFont(Minecraft mc, FlightCanvas context, String s, float x, float y,
      int color) {
    context.drawText(mc.font, s, (int) x, (int) y, CONFIG.color, false);
  }

  protected void drawRightAlignedFont(Minecraft mc, FlightCanvas context, String s, float x,
      float y) {
    int w = mc.font.width(s);
    drawFont(mc, context, s, x - w, y);
  }

  protected void drawBox(FlightCanvas context, float x, float y, float w, float h) {
    drawHorizontalLine(context, x, x + w, y);
    drawHorizontalLine(context, x, x + w, y + h);
    drawVerticalLine(context, x, y, y + h);
    drawVerticalLine(context, x + w, y, y + h);
  }

  protected void drawHorizontalLineDashed(FlightCanvas context, float x1, float x2, float y,
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

  protected void drawHorizontalLine(FlightCanvas context, float x1, float x2, float y) {
    if (x2 < x1) {
      float i = x1;
      x1 = x2;
      x2 = i;
    }
    fill(context, x1 - CONFIG.halfThickness, y - CONFIG.halfThickness, x2 + CONFIG.halfThickness,
        y + CONFIG.halfThickness);
  }

  protected void drawVerticalLine(FlightCanvas context, float x, float y1, float y2) {
    if (y2 < y1) {
      float i = y1;
      y1 = y2;
      y2 = i;
    }

    fill(context, x - CONFIG.halfThickness, y1 + CONFIG.halfThickness, x + CONFIG.halfThickness,
        y2 - CONFIG.halfThickness);
  }

  // FlightCanvas.fill() is a pose-aware colored-quad primitive; the version-
  // specific adapter (PlatformCanvas) is responsible for applying whatever the
  // current platform's transform-stack equivalent is (PoseStack pre-26.1,
  // Matrix3x2fStack at 26.1+) -- this class never touches either directly.
  protected void fill(FlightCanvas context, float x1, float y1, float x2, float y2) {
    int color = CONFIG.color;
    context.fill(i(Math.min(x1, x2)), i(Math.min(y1, y2)), i(Math.max(x1, x2)),
        i(Math.max(y1, y2)), color);
  }
}
