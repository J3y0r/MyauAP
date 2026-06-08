package myau.ui.clientinfo;

import myau.Myau;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static org.lwjgl.opengl.GL11.glEnd;

public class ClientInfo extends GuiScreen {

    private static final String CLIENT_NAME = "MyauAP";
    private static final String CLIENT_VERSION = "v " + Myau.version;
    private static final String CLIENT_WEBSITE = "ysxiaoyu.top";
    private static final String CLIENT_DESC = "基于 OpenMyau 二次修改的 Hypixel 客户端";

    private static final String DEV_TITLE = "感谢 OpticalShield 提供的混淆方案";
    private static final String DEV_NAME = "Jeyor";

    private static final String[][] TEAM_LIST = {
            {"Jeyor", "核心开发"},
            {"ysxiaoyu", "UI 设计"},
    };

    // 特别鸣谢列表
    private static final String[] THANKS_LIST = {
            "OpenMyau",
            "Raven B3",
            "Haedus",
            "GuardFix Team"
    };

    // 开源引用列表（仓库 - 描述）
    private static final String[][] OPEN_SOURCE = {
            {"https://github.com/60124808866/OpenMyau", "OpenMyau"},
    };

    private static final String COPYRIGHT = "© MyauAP Team 2026";

    // ═══ 主题色 ═══
    private static final Color ACCENT = new Color(60, 162, 253);
    private static final Color ACCENT_DIM = new Color(60, 162, 253, 80);
    private static final Color PANEL_BG = new Color(17, 17, 24, 250);
    private static final Color OVERLAY = new Color(0, 0, 0, 140);
    private static final Color TITLE_COL = new Color(224, 224, 238);
    private static final Color LABEL_COL = new Color(125, 125, 148);
    private static final Color FOOTER_COL = new Color(70, 70, 88);
    private static final Color SEP_COL = new Color(255, 255, 255, 18);
    private static final Color BORDER_COL = new Color(60, 162, 253, 75);

    // ═══ 面板尺寸 ═══
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 240;
    private static final int RADIUS = 10;
    private static final int PAD = 16;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int px = (sw - PANEL_W) / 2;
        int py = (sh - PANEL_H) / 2;

        // 背景遮罩
        drawRect(0, 0, sw, sh, OVERLAY.getRGB());

        // 面板背景
        drawRoundedRect(px, py, px + PANEL_W, py + PANEL_H, RADIUS, PANEL_BG.getRGB());
        // 面板边框
        drawRoundedOutline(px, py, px + PANEL_W, py + PANEL_H, RADIUS, 1.2f, BORDER_COL.getRGB());

        int y = py + 12;

        // ══ 顶部左右两栏 ══
        // 左：客户端名 + 描述
        mc.fontRendererObj.drawStringWithShadow(
                CLIENT_NAME, px + PAD, y, TITLE_COL.getRGB());
        mc.fontRendererObj.drawStringWithShadow(
                "\u00a77- " + CLIENT_DESC, px + PAD, y + 11, LABEL_COL.getRGB());

        // 右：开发及维护（右对齐）
        int rightX = px + PANEL_W - PAD;
        mc.fontRendererObj.drawStringWithShadow(
                DEV_TITLE,
                rightX - mc.fontRendererObj.getStringWidth(DEV_TITLE),
                y, ACCENT.getRGB());
        String devLine1 = CLIENT_WEBSITE;
        String devLine2 = "DEV: " + DEV_NAME;
        mc.fontRendererObj.drawString(
                devLine1,
                rightX - mc.fontRendererObj.getStringWidth(devLine1),
                y + 11, LABEL_COL.getRGB());
        mc.fontRendererObj.drawString(
                devLine2,
                rightX - mc.fontRendererObj.getStringWidth(devLine2),
                y + 20, LABEL_COL.getRGB());

        // 分隔线
        y += 34;
        drawHLine(px + PAD, px + PANEL_W - PAD, y, SEP_COL.getRGB());
        y += 6;

        // ══ 大名字居中 ══
        drawCenteredScaled(CLIENT_NAME, sw / 2, y, ACCENT.getRGB(), 1.6f);
        y += 22;
        drawCenteredScaled(CLIENT_VERSION + "  \u00b7  " + CLIENT_WEBSITE,
                sw / 2, y, new Color(60, 162, 253, 110).getRGB(), 0.8f);
        y += 12;

        // 分隔线
        drawHLine(px + PAD, px + PANEL_W - PAD, y, SEP_COL.getRGB());
        y += 8;

        // ══ 两栏：开发团队 / 特别鸣谢 ══
        int col1X = px + PAD;
        int col2X = px + PANEL_W / 2 + 4;

        // 列标题
        mc.fontRendererObj.drawStringWithShadow("\u00a7f开发团队", col1X, y, TITLE_COL.getRGB());
        mc.fontRendererObj.drawStringWithShadow("\u00a7fSpecial Thanks", col2X, y, TITLE_COL.getRGB());
        y += 11;

        // 列内容
        int maxRows = Math.max(TEAM_LIST.length, THANKS_LIST.length);
        for (int i = 0; i < maxRows; i++) {
            if (i < TEAM_LIST.length) {
                String line = "\u00a77- \u00a7r" + TEAM_LIST[i][0]
                        + " \u00a78\u2013 \u00a77" + TEAM_LIST[i][1];
                mc.fontRendererObj.drawString(line, col1X, y, LABEL_COL.getRGB());
            }
            if (i < THANKS_LIST.length) {
                mc.fontRendererObj.drawString(
                        "\u00a77- " + THANKS_LIST[i], col2X, y, LABEL_COL.getRGB());
            }
            y += 10;
        }

        // 分隔线
        y += 2;
        drawHLine(px + PAD, px + PANEL_W - PAD, y, SEP_COL.getRGB());
        y += 5;

        // ══ 开源引用小字密排 ══
        mc.fontRendererObj.drawString("\u00a78开源引用", px + PAD, y, FOOTER_COL.getRGB());
        y += 9;
        for (String[] entry : OPEN_SOURCE) {
            mc.fontRendererObj.drawString(
                    "\u00a78- " + entry[0] + " \u00b7 " + entry[1],
                    px + PAD, y, FOOTER_COL.getRGB());
            y += 8;
        }

        // ══ 版权 ══
        drawCenteredScaled(COPYRIGHT, sw / 2, py + PANEL_H - 10, FOOTER_COL.getRGB(), 0.82f);
    }

    // ═══ 工具方法 ═══

    private void drawCenteredScaled(String text, int cx, int y, int color, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1f);
        int tw = mc.fontRendererObj.getStringWidth(text);
        mc.fontRendererObj.drawStringWithShadow(text, cx / scale - tw / 2f, y / scale, color);
        GlStateManager.popMatrix();
    }

    private void drawHLine(int x1, int x2, int y, int color) {
        drawRect(x1, y, x2, y + 1, color);
    }

    // ═══ 圆角矩形（修复版，1.8.9 GL11）═══

    private void drawRoundedRect(float x1, float y1, float x2, float y2, float r, int color) {
        float[] c = rgba(color);
        glBegin(c);
        quad(x1 + r, y1, x2 - r, y2);
        quad(x1, y1 + r, x1 + r, y2 - r);
        quad(x2 - r, y1 + r, x2, y2 - r);
        glEnd();
        // 四角扇形（Y轴向下，sin 正向）
        fan(x1 + r, y1 + r, r, 180, 270, c);
        fan(x2 - r, y1 + r, r, 270, 360, c);
        fan(x2 - r, y2 - r, r, 0, 90, c);
        fan(x1 + r, y2 - r, r, 90, 180, c);
        glCleanup();
    }

    private void drawRoundedOutline(float x1, float y1, float x2, float y2,
                                    float r, float lw, int color) {
        float[] c = rgba(color);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(c[0], c[1], c[2], c[3]);
        GL11.glLineWidth(lw);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1 + r, y1);
        GL11.glVertex2f(x2 - r, y1);
        GL11.glVertex2f(x1 + r, y2);
        GL11.glVertex2f(x2 - r, y2);
        GL11.glVertex2f(x1, y1 + r);
        GL11.glVertex2f(x1, y2 - r);
        GL11.glVertex2f(x2, y1 + r);
        GL11.glVertex2f(x2, y2 - r);
        glEnd();

        arc(x1 + r, y1 + r, r, 180, 270);
        arc(x2 - r, y1 + r, r, 270, 360);
        arc(x2 - r, y2 - r, r, 0, 90);
        arc(x1 + r, y2 - r, r, 90, 180);

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);
        glCleanup();
    }

    private void fan(float cx, float cy, float r, float start, float end, float[] c) {
        GL11.glColor4f(c[0], c[1], c[2], c[3]);
        int segs = 10;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segs; i++) {
            double a = Math.toRadians(start + (end - start) * i / (double) segs);
            GL11.glVertex2f(cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r));
        }
        glEnd();
    }

    private void arc(float cx, float cy, float r, float start, float end) {
        int steps = 10;
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(start + (end - start) * i / (double) steps);
            GL11.glVertex2f(cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r));
        }
        glEnd();
    }

    private void quad(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        glEnd();
    }

    private void glBegin(float[] c) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(c[0], c[1], c[2], c[3]);
    }

    private void glCleanup() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    private float[] rgba(int color) {
        return new float[]{
                (color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f,
                (color & 255) / 255f,
                (color >> 24 & 255) / 255f
        };
    }

    // ═══ GuiScreen ═══

    @Override
    public void initGui() {
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}