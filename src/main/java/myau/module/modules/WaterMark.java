package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String TEXT = "MyauAP";

    public final ModeProperty style = new ModeProperty("style", 0, new String[]{"PLAIN", "BOXED"});
    public final BooleanProperty alignHud = new BooleanProperty("align-hud", true);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "MIDDLE", "RIGHT"}, () -> !this.alignHud.getValue());
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "MIDDLE", "BOTTOM"}, () -> !this.alignHud.getValue());
    public final IntProperty offX = new IntProperty("offset-x", 2, 0, 255, () -> !this.alignHud.getValue());
    public final IntProperty offY = new IntProperty("offset-y", 2, 0, 255, () -> !this.alignHud.getValue());
    public final FloatProperty scale = new FloatProperty("scale", 1.2F, 0.5F, 1.5F);
//    public final FloatProperty gap = new FloatProperty("gap", 0.0F, -10.0F, 20.0F, () -> this.alignHud.getValue());
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"CUSTOM", "HUD"});
    public final ColorProperty customColor = new ColorProperty("custom-color", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 0);
    public final ColorProperty boxColor = new ColorProperty("box-color", Color.WHITE.getRGB(), () -> this.style.getValue() == 1);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty showFps = new BooleanProperty("show-fps", false);

    public WaterMark() {
        super("WaterMark", false);
    }

    private int getColor() {
        if (this.colorMode.getValue() == 1) {
            HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
            return hud.getColor(System.currentTimeMillis()).getRGB();
        }
        return this.customColor.getValue();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            ScaledResolution sr = new ScaledResolution(mc);
            float padding = 3.0F;
            String displayText = this.showFps.getValue() ? TEXT + " (" + Minecraft.getDebugFPS() + "fps)" : TEXT;
            float textW = (float) mc.fontRendererObj.getStringWidth(displayText);
            float textH = (float) mc.fontRendererObj.FONT_HEIGHT;
            boolean isBoxed = this.style.getValue() == 1;

            float totalW = isBoxed ? textW + padding * 2.0F : textW;
            float totalH = isBoxed ? textH + padding * 2.0F : textH;

            float x;
            float y;

            if (this.alignHud.getValue()) {
                HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                float alignX = (float) hud.offsetX.getValue()
                        + (1.0F + (hud.showBar.getValue() ? (hud.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * hud.scale.getValue();
                y = (float) hud.offsetY.getValue() + 1.0F * hud.scale.getValue();
                if (hud.posX.getValue() == 1) {
                    // RIGHT: right-align text to alignX from right edge
                    x = (float) sr.getScaledWidth() - alignX - totalW * this.scale.getValue();
                } else {
                    // LEFT: text starts at alignX
                    x = alignX;
                }
            } else {
                x = (float) this.offX.getValue();
                switch (this.posX.getValue()) {
                    case 1:
                        x = (float) sr.getScaledWidth() / 2.0F - totalW * this.scale.getValue() / 2.0F;
                        break;
                    case 2:
                        x = (float) sr.getScaledWidth() - (float) this.offX.getValue() - totalW * this.scale.getValue();
                        break;
                }

                y = (float) this.offY.getValue();
                switch (this.posY.getValue()) {
                    case 1:
                        y = (float) sr.getScaledHeight() / 2.0F - totalH * this.scale.getValue() / 2.0F;
                        break;
                    case 2:
                        y = (float) sr.getScaledHeight() - (float) this.offY.getValue() - totalH * this.scale.getValue();
                        break;
                }
            }

            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

            float sx = x / this.scale.getValue();
            float sy = y / this.scale.getValue();

            int color = this.getColor();

            if (isBoxed) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        sx, sy,
                        sx + textW + padding * 2.0F, sy + textH + padding * 2.0F,
                        1.5F, 0, this.boxColor.getValue()
                );
                RenderUtil.disableRenderState();

                sx += padding;
                sy += padding;
            }

            GlStateManager.disableDepth();
            float sx2 = sx;
            mc.fontRendererObj.drawString("M", sx2, sy, color, this.shadow.getValue());
            sx2 += mc.fontRendererObj.getStringWidth("M");
            String suffix = displayText.substring(1);
            mc.fontRendererObj.drawString(suffix, sx2, sy, Color.WHITE.getRGB(), this.shadow.getValue());
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();
        }
    }
}
