package dev.wp.matter_manipulator.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wp.matter_manipulator.client.render.MMRenderer;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.networking.MMNetwork;
import dev.wp.matter_manipulator.common.networking.PacketResetTransform;
import dev.wp.matter_manipulator.common.networking.PacketRotateTransform;
import dev.wp.matter_manipulator.common.networking.PacketSetArraySpan;
import dev.wp.matter_manipulator.common.networking.PacketSetCoordDirect;
import dev.wp.matter_manipulator.common.networking.PacketToggleFlip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3i;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent full-screen overlay with rotate/flip/reset controls and an info panel on
 * the left, and coordinate editors for Copy / Copy (A) / Copy (B) / Paste / Stacking on
 * the right. Stacking is only shown in COPYING mode.
 */
@OnlyIn(Dist.CLIENT)
public class TransformScreen extends Screen {

    private static final int BORDER_COLOR = 0xFF888888;
    private static final int PANEL_COLOR = 0xFF111111;
    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int ROW_W = 130;
    private static final int GROUP_H = 80;

    private enum Coord {
        COPY("mm.transform.header.copy"),
        COPY_A("mm.transform.header.copy_a"),
        COPY_B("mm.transform.header.copy_b"),
        PASTE("mm.transform.header.paste"),
        STACK("mm.transform.header.stacking");

        final String headerKey;

        Coord(String headerKey) {
            this.headerKey = headerKey;
        }
    }

    private MMConfig cfg = new MMConfig();

    /** Per-frame label/value updaters (button step labels, field sync). */
    private final List<Runnable> tickers = new ArrayList<>();
    /** Static decorations (headers, N/A boxes, info panel) drawn behind the widgets. */
    private final List<Runnable> decorations = new ArrayList<>();

    private boolean refreshingFields;

    public TransformScreen() {
        super(Component.translatable("mm.gui.edit_transform"));
    }

    @Override
    protected void init() {
        tickers.clear();
        decorations.clear();
        refreshConfig();

        initLeftColumn();
        initRightColumns();
    }

    // ── Left column: rotate / flip / info / reset ────────────────────────────────

    private void initLeftColumn() {
        int x = 10;
        // 3 rotate rows + flip row (18 each, 10 gaps) + info panel (66)
        int totalH = 18 * 4 + 10 * 4 + 66;
        int y = (height - totalH) / 2;

        addRotateRow(x, y, "x", Direction.EAST);
        y += 28;
        addRotateRow(x, y, "y", Direction.UP);
        y += 28;
        addRotateRow(x, y, "z", Direction.SOUTH);
        y += 28;

        addFlipButton(x, y, "x", MMTransform.FLIP_X);
        addFlipButton(x + 45, y, "y", MMTransform.FLIP_Y);
        addFlipButton(x + 90, y, "z", MMTransform.FLIP_Z);
        y += 28;

        final int infoX = x, infoY = y;
        decorations.add(() -> drawInfoPanel(infoX, infoY));

        addRenderableWidget(Button.builder(
                        Component.translatable("mm.transform.button.reset"),
                        b -> MMNetwork.CHANNEL.sendToServer(new PacketResetTransform()))
                .bounds(x + 90, y + 24, 40, 18)
                .build());
    }

    private void addRotateRow(int x, int y, String axis, Direction dir) {
        addRenderableWidget(Button.builder(
                        Component.translatable("mm.transform.button.rotate_" + axis + "-"),
                        b -> MMNetwork.CHANNEL.sendToServer(new PacketRotateTransform(dir, false)))
                .bounds(x, y, 62, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("mm.transform.button.rotate_" + axis + "+"),
                        b -> MMNetwork.CHANNEL.sendToServer(new PacketRotateTransform(dir, true)))
                .bounds(x + 68, y, 62, 18)
                .build());
    }

    private void addFlipButton(int x, int y, String axis, int flag) {
        addRenderableWidget(Button.builder(
                        Component.translatable("mm.transform.button.flip_" + axis),
                        b -> MMNetwork.CHANNEL.sendToServer(new PacketToggleFlip(flag)))
                .bounds(x, y, 40, 18)
                .build());
    }

    private void drawInfoPanel(int x, int y) {
        // graphics reference captured via field set in render()
        var g = currentGraphics;
        if (g == null) return;

        drawPanelBox(g, x, y, 88, 66);

        var t = cfg.transform;
        List<String> flips = new ArrayList<>();
        if (t.flipX) flips.add("X");
        if (t.flipY) flips.add("Y");
        if (t.flipZ) flips.add("Z");

        String info = I18n.get("mm.transform.info",
                flips.isEmpty() ? "None" : String.join(", ", flips),
                directionName(t.up),
                directionName(t.forward));

        int ty = y + 4;
        for (String line : info.split("\n")) {
            g.drawString(font, line, x + 3, ty, TEXT_COLOR, false);
            ty += font.lineHeight + 1;
        }

        // Axis color legend
        int lx = x + 3;
        int ly = y + 36 + (30 - font.lineHeight) / 2;
        lx += drawLegend(g, "X+ ", 0xFFFF5555, lx, ly);
        lx += drawLegend(g, "Y+ ", 0xFF55FF55, lx, ly);
        drawLegend(g, "Z+", 0xFF5555FF, lx, ly);
    }

    private int drawLegend(GuiGraphics g, String text, int color, int x, int y) {
        g.drawString(font, text, x, y, color, false);
        return font.width(text);
    }

    private static String directionName(Direction dir) {
        return switch (dir) {
            case DOWN -> "Down";
            case UP -> "Up";
            case NORTH -> "North";
            case SOUTH -> "South";
            case WEST -> "West";
            case EAST -> "East";
        };
    }

    // ── Right columns: coordinate editors ────────────────────────────────────────

    private void initRightColumns() {
        // Stacking only applies to COPYING; the other modes place a single region
        boolean showStack = cfg.placeMode == PlaceMode.COPYING;

        if (minecraft != null && minecraft.getWindow().getGuiScale() <= 2) {
            // Plenty of vertical room: one column with all groups
            Coord[] groups = showStack
                    ? new Coord[]{Coord.COPY, Coord.COPY_A, Coord.COPY_B, Coord.PASTE, Coord.STACK}
                    : new Coord[]{Coord.COPY, Coord.COPY_A, Coord.COPY_B, Coord.PASTE};
            int x = width - ROW_W - 10;
            int y = (height - GROUP_H * groups.length) / 2;
            for (Coord coord : groups) {
                addGroup(x, y, coord);
                y += GROUP_H;
            }
        } else {
            Coord[] right = {Coord.COPY_A, Coord.COPY_B, Coord.PASTE};
            int x = width - ROW_W - 10;
            int y = (height - GROUP_H * right.length) / 2;
            for (Coord coord : right) {
                addGroup(x, y, coord);
                y += GROUP_H;
            }

            Coord[] lessRight = showStack
                    ? new Coord[]{Coord.COPY, Coord.STACK}
                    : new Coord[]{Coord.COPY};
            x = width - ROW_W * 2 - 20;
            y = (height - GROUP_H * lessRight.length) / 2;
            for (Coord coord : lessRight) {
                addGroup(x, y, coord);
                y += GROUP_H;
            }
        }
    }

    private void addGroup(int x, int y, Coord coord) {
        decorations.add(() -> drawHeader(x, y, coord.headerKey));
        for (int axis = 0; axis < 3; axis++) {
            addCoordRow(x, y + 20 + axis * 20, coord, axis);
        }
    }

    private void drawHeader(int x, int y, String key) {
        var g = currentGraphics;
        if (g == null) return;
        int bx = x + (ROW_W - 60) / 2;
        drawPanelBox(g, bx, y, 60, 18);
        String text = I18n.get(key);
        g.drawString(font, text, bx + (60 - font.width(text)) / 2, y + 5, TEXT_COLOR, false);
    }

    private void addCoordRow(int x, int y, Coord coord, int axis) {
        String axisName = switch (axis) {
            case 0 -> "X";
            case 1 -> "Y";
            default -> "Z";
        };
        StepSize step = new StepSize(coord, axis);

        Button minus = Button.builder(Component.empty(), b -> {
            int i = getComponent(coord, axis) - step.getOffset();
            setComponent(coord, axis, i);
        }).bounds(x, y, 40, 18).build();
        addRenderableWidget(minus);
        tickers.add(() -> minus.setMessage(Component.literal(axisName + " - " + step.getOffset())));

        if (coord == Coord.COPY) {
            // The Copy editor translates A and B; it has no absolute value to show
            decorations.add(() -> drawNABox(x + 45, y));
        } else {
            EditBox field = new EditBox(font, x + 45, y, 40, 18, Component.literal(axisName));
            field.setFilter(s -> s.isEmpty() || s.equals("-") || parseInt(s) != null);
            field.setResponder(s -> {
                if (refreshingFields) return;
                Integer v = parseInt(s);
                if (v == null) return;
                if (v == getVisual(coord, axis)) return;
                setComponent(coord, axis, fromVisual(coord, v));
            });
            addRenderableWidget(field);
            tickers.add(() -> {
                if (field.isFocused()) return;
                String want = String.valueOf(getVisual(coord, axis));
                if (!want.equals(field.getValue())) {
                    refreshingFields = true;
                    field.setValue(want);
                    refreshingFields = false;
                }
            });
        }

        Button plus = Button.builder(Component.empty(), b -> {
            int i = getComponent(coord, axis) + step.getOffset();
            setComponent(coord, axis, i);
        }).bounds(x + 90, y, 40, 18).build();
        addRenderableWidget(plus);
        tickers.add(() -> plus.setMessage(Component.literal(axisName + " + " + step.getOffset())));
    }

    private void drawNABox(int x, int y) {
        var g = currentGraphics;
        if (g == null) return;
        drawPanelBox(g, x, y, 40, 18);
        g.drawString(font, "N/A", x + (40 - font.width("N/A")) / 2, y + 5, TEXT_COLOR, false);
    }

    // ── Value plumbing ────────────────────────────────────────────────────────────

    private Vector3i getCoordVec(Coord coord) {
        return switch (coord) {
            case COPY -> new Vector3i(0);
            case COPY_A -> cfg.coordA != null ? vec(cfg.coordA.pos) : defaultLocation();
            case COPY_B -> cfg.coordB != null ? vec(cfg.coordB.pos) : defaultLocation();
            case PASTE -> cfg.coordC != null ? vec(cfg.coordC.pos) : defaultLocation();
            case STACK -> new Vector3i(cfg.arraySpan[0], cfg.arraySpan[1], cfg.arraySpan[2]);
        };
    }

    private int getComponent(Coord coord, int axis) {
        return axisGet(getCoordVec(coord), axis);
    }

    /** Stacking displays as a copy count: span 0 = 1 copy, span 1 = 2 copies, etc. */
    private int getVisual(Coord coord, int axis) {
        int k = getComponent(coord, axis);
        if (coord == Coord.STACK && k >= 0) k++;
        return k;
    }

    private static int fromVisual(Coord coord, int v) {
        if (coord == Coord.STACK && v >= 1) return v - 1;
        return v;
    }

    private void setComponent(Coord coord, int axis, int value) {
        Vector3i loc = getCoordVec(coord);
        axisSet(loc, axis, value);

        switch (coord) {
            case COPY -> {
                // loc is a delta here (the editor's base value is always 0)
                BlockPos delta = new BlockPos(loc.x, loc.y, loc.z);
                if (cfg.coordA != null) {
                    MMNetwork.CHANNEL.sendToServer(new PacketSetCoordDirect(0, cfg.coordA.pos.offset(delta)));
                }
                if (cfg.coordB != null) {
                    MMNetwork.CHANNEL.sendToServer(new PacketSetCoordDirect(1, cfg.coordB.pos.offset(delta)));
                }
            }
            case COPY_A -> MMNetwork.CHANNEL.sendToServer(new PacketSetCoordDirect(0, new BlockPos(loc.x, loc.y, loc.z)));
            case COPY_B -> MMNetwork.CHANNEL.sendToServer(new PacketSetCoordDirect(1, new BlockPos(loc.x, loc.y, loc.z)));
            case PASTE -> MMNetwork.CHANNEL.sendToServer(new PacketSetCoordDirect(2, new BlockPos(loc.x, loc.y, loc.z)));
            case STACK -> MMNetwork.CHANNEL.sendToServer(new PacketSetArraySpan(axis, value));
        }
    }

    private Vector3i defaultLocation() {
        BlockPos look = MMRenderer.getLookingAt(Minecraft.getInstance(), null);
        return look != null ? vec(look) : new Vector3i(0);
    }

    private static Vector3i vec(BlockPos pos) {
        return new Vector3i(pos.getX(), pos.getY(), pos.getZ());
    }

    private static int axisGet(Vector3i v, int axis) {
        return switch (axis) {
            case 0 -> v.x;
            case 1 -> v.y;
            default -> v.z;
        };
    }

    private static void axisSet(Vector3i v, int axis, int value) {
        switch (axis) {
            case 0 -> v.x = value;
            case 1 -> v.y = value;
            default -> v.z = value;
        }
    }

    @Nullable
    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Step size for the -/+ buttons: 1 normally, 10 with Shift, and the copy/paste
     * region's size on that axis with Ctrl (so one click steps a whole region).
     * The region size is captured once per Ctrl press and held until Ctrl is released.
     */
    private class StepSize {

        private final Coord coord;
        private final int axis;
        private Vector3i size;
        private boolean present;

        StepSize(Coord coord, int axis) {
            this.coord = coord;
            this.axis = axis;
        }

        int getOffset() {
            if (hasShiftDown()) return 10;
            if (coord != Coord.STACK && hasControlDown()) return axisGet(getSize(), axis);
            present = false;
            return 1;
        }

        private Vector3i getSize() {
            if (!present && hasControlDown()) {
                size = computeRegionSize();
                present = true;
            }
            if (!hasControlDown()) present = false;
            return present ? size : new Vector3i(1);
        }

        private Vector3i computeRegionSize() {
            if (cfg.coordA == null || cfg.coordB == null) return new Vector3i(1);
            Vector3i s = new Vector3i(
                    Math.abs(cfg.coordB.pos.getX() - cfg.coordA.pos.getX()) + 1,
                    Math.abs(cfg.coordB.pos.getY() - cfg.coordA.pos.getY()) + 1,
                    Math.abs(cfg.coordB.pos.getZ() - cfg.coordA.pos.getZ()) + 1);
            if (coord == Coord.PASTE && cfg.transform != null) {
                s = cfg.transform.apply(s);
                s.x = Math.abs(s.x);
                s.y = Math.abs(s.y);
                s.z = Math.abs(s.z);
            }
            return s;
        }
    }

    // ── Screen plumbing ───────────────────────────────────────────────────────────

    @Nullable
    private GuiGraphics currentGraphics;

    private void refreshConfig() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var state = MMState.ofHeld(mc.player.getMainHandItem());
        if (state != null) {
            cfg = state.getConfig();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof ItemMatterManipulator)) {
            onClose();
            return;
        }

        refreshConfig();
        for (Runnable ticker : tickers) ticker.run();

        currentGraphics = graphics;
        for (Runnable decoration : decorations) decoration.run();
        currentGraphics = null;

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanelBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BORDER_COLOR);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let Esc close even while a number field is focused
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Transparent overlay — no background fill
    }
}
