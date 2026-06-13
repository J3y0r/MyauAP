package myau.module.modules;

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
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final IntProperty offX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final ColorProperty textColor = new ColorProperty("text-color", Color.WHITE.getRGB());
    public final ColorProperty boxColor = new ColorProperty("box-color", Color.WHITE.getRGB(), () -> this.style.getValue() == 1);
    public final PercentProperty background = new PercentProperty("background", 25, () -> this.style.getValue() == 1);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);

    public WaterMark() {
        super("WaterMark", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            ScaledResolution sr = new ScaledResolution(mc);
            float padding = 3.0F;
            float textW = (float) mc.fontRendererObj.getStringWidth(TEXT);
            float textH = (float) mc.fontRendererObj.FONT_HEIGHT;
            boolean isBoxed = this.style.getValue() == 1;

            // Calculate raw position before scaling
            float totalW = isBoxed ? textW + padding * 2.0F : textW;
            float totalH = isBoxed ? textH + padding * 2.0F : textH;

            float x = (float) this.offX.getValue();
            switch (this.posX.getValue()) {
                case 1: // MIDDLE
                    x = (float) sr.getScaledWidth() / 2.0F - totalW * this.scale.getValue() / 2.0F;
                    break;
                case 2: // RIGHT
                    x = (float) sr.getScaledWidth() - (float) this.offX.getValue() - totalW * this.scale.getValue();
                    break;
            }

            float y = (float) this.offY.getValue();
            switch (this.posY.getValue()) {
                case 1: // MIDDLE
                    y = (float) sr.getScaledHeight() / 2.0F - totalH * this.scale.getValue() / 2.0F;
                    break;
                case 2: // BOTTOM
                    y = (float) sr.getScaledHeight() - (float) this.offY.getValue() - totalH * this.scale.getValue();
                    break;
            }

            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

            float sx = x / this.scale.getValue();
            float sy = y / this.scale.getValue();

            if (isBoxed) {
                RenderUtil.enableRenderState();
                // Background
                if (this.background.getValue() > 0) {
                    RenderUtil.drawRect(
                            sx, sy,
                            sx + textW + padding * 2.0F, sy + textH + padding * 2.0F,
                            new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB()
                    );
                }
                // Outline
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
            mc.fontRendererObj.drawString(
                    TEXT,
                    sx, sy,
                    this.textColor.getValue(),
                    this.shadow.getValue()
            );
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();
        }
    }
}
