package dev.wp.matter_manipulator.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.lang.Math.PI;

@OnlyIn(Dist.CLIENT)
public class RadialMenu extends Widget<RadialMenu> implements Interactable {
    private static final double TAU = PI * 2;

    public List<RadialMenuOption> options = new ArrayList<>();
    public float innerRadius = 0.25f, outerRadius = 0.60f;

    // Cached layout result from last draw call — re-used by onMousePressed
    private double cachedMouseRadius = 0, cachedMouseTheta = 0;
    private int cachedW = 0, cachedH = 0;

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int w = getArea().width;
        int h = getArea().height;
        cachedW = w;
        cachedH = h;

        layoutOptions();

        int dim = Math.min(w, h);
        double cx = w / 2.0;
        double cy = h / 2.0;

        // Mouse in widget-local space (widget fills screen so area.x/y == 0)
        double mx = context.getMouseX() - getArea().x;
        double my = context.getMouseY() - getArea().y;
        double nx = (mx - cx) / (dim / 2.0);
        double ny = (my - cy) / (dim / 2.0);
        cachedMouseRadius = Math.sqrt(nx * nx + ny * ny);
        cachedMouseTheta = modTau(Math.atan2(ny, nx));

        GuiGraphics graphics = context.getGraphics();
        Matrix4f matrix = graphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();

        for (RadialMenuOption option : options) {
            if (option.isHidden) continue;

            boolean hovered = cachedMouseRadius >= innerRadius
                    && cachedMouseRadius <= outerRadius
                    && isAngleBetween(cachedMouseTheta, option.startTheta, option.endTheta);

            float r = hovered ? 0.4f : 0.1f;
            float g = hovered ? 0.4f : 0.1f;
            float b2 = hovered ? 0.4f : 0.1f;
            float a = 0.88f;

            buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            double step = PI / 32;
            for (int i = 0;; i++) {
                double t = option.startTheta + i * step;
                double tc = Math.min(Math.max(t, option.startTheta), option.endTheta);

                float ox = (float) (Math.cos(tc) * outerRadius * dim / 2.0 + cx);
                float oy = (float) (Math.sin(tc) * outerRadius * dim / 2.0 + cy);
                float ix = (float) (Math.cos(tc) * innerRadius * dim / 2.0 + cx);
                float iy = (float) (Math.sin(tc) * innerRadius * dim / 2.0 + cy);

                buf.vertex(matrix, ox, oy, 0).color(r, g, b2, a).endVertex();
                buf.vertex(matrix, ix, iy, 0).color(r, g, b2, a).endVertex();

                if (t > option.endTheta) break;
            }

            BufferUploader.drawWithShader(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        // Draw labels
        var font = Minecraft.getInstance().font;
        for (RadialMenuOption option : options) {
            if (option.isHidden) continue;

            double midTheta = (option.startTheta + option.endTheta) / 2;
            double midRadius = (innerRadius + outerRadius) / 2.0;
            int lx = (int) (Math.cos(midTheta) * midRadius * dim / 2.0 + cx);
            int ly = (int) (Math.sin(midTheta) * midRadius * dim / 2.0 + cy);

            String label = option.label.get();
            var lines = font.split(Component.literal(label), 60);
            int totalHeight = lines.size() * font.lineHeight;
            int startY = ly - totalHeight / 2;

            for (var line : lines) {
                int textWidth = font.width(line);
                graphics.drawString(font, line, lx - textWidth / 2, startY, 0xFFCCCCCC, false);
                startY += font.lineHeight;
            }
        }
    }

    private void layoutOptions() {
        double weightSum = 0;
        for (RadialMenuOption opt : options) {
            opt.isHidden = opt.hidden.getAsBoolean();
            if (!opt.isHidden) weightSum += opt.weight;
        }

        double currentAngle = 0;
        for (RadialMenuOption opt : options) {
            if (opt.isHidden) {
                opt.startTheta = 0;
                opt.endTheta = 0;
                continue;
            }
            double slice = opt.weight / weightSum * TAU;
            opt.startTheta = currentAngle;
            currentAngle += slice;
            opt.endTheta = currentAngle;
        }

        // Rotate so the first visible option is centred at the top (−π/2)
        RadialMenuOption first = null;
        for (RadialMenuOption opt : options) {
            if (!opt.isHidden) {
                first = opt;
                break;
            }
        }
        if (first != null) {
            double offset = (first.endTheta - first.startTheta) / 2 + PI / 2;
            for (RadialMenuOption opt : options) {
                if (!opt.isHidden) {
                    opt.startTheta -= offset;
                    opt.endTheta -= offset;
                }
            }
        }
    }

    @Override
    public Result onMousePressed(int button) {
        var ctx = getContext();
        int dim = Math.min(cachedW > 0 ? cachedW : getArea().width, cachedH > 0 ? cachedH : getArea().height);
        double cx = (cachedW > 0 ? cachedW : getArea().width) / 2.0;
        double cy = (cachedH > 0 ? cachedH : getArea().height) / 2.0;

        // mouse coords are screen-absolute; widget is at area.x/y
        double lx = ctx.getMouseX() - getArea().x;
        double ly = ctx.getMouseY() - getArea().y;
        double nx = (lx - cx) / (dim / 2.0);
        double ny = (ly - cy) / (dim / 2.0);
        double mouseRadius = Math.sqrt(nx * nx + ny * ny);
        double mouseTheta = modTau(Math.atan2(ny, nx));

        for (RadialMenuOption opt : options) {
            if (opt.isHidden) continue;
            if (mouseRadius >= innerRadius
                    && mouseRadius <= outerRadius
                    && isAngleBetween(mouseTheta, opt.startTheta, opt.endTheta)) {
                if (opt.onClick != null) opt.onClick.onClick(this, opt, button);
                if (!opt.keepOpen) getPanel().closeIfOpen();
                return Result.SUCCESS;
            }
        }

        // Click in dead zone — keep the menu open
        return Result.ACCEPT;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static double modTau(double a) {
        return ((a % TAU) + TAU) % TAU;
    }

    private static boolean isAngleBetween(double target, double a1, double a2) {
        if (a2 < a1) return false;
        while (a1 < 0) {
            a1 += TAU;
            a2 += TAU;
        }
        while (a1 > TAU) {
            a1 -= TAU;
            a2 -= TAU;
        }
        return modTau(target - a1) < a2 - a1;
    }

    // ── inner types ───────────────────────────────────────────────────────────────

    public static class RadialMenuOption {
        public Supplier<String> label;
        public double weight = 1;
        public BooleanSupplier hidden = () -> false;
        public RadialMenuClickHandler onClick;
        /** True for branch options — click replaces options rather than closing the panel. */
        public boolean keepOpen = false;

        // computed during layout
        boolean isHidden;
        double startTheta, endTheta;
    }

    @FunctionalInterface
    public interface RadialMenuClickHandler {
        void onClick(RadialMenu menu, RadialMenuOption option, int mouseButton);
    }
}
