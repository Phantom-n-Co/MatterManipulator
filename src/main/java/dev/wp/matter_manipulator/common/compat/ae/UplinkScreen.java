package dev.wp.matter_manipulator.common.compat.ae;

import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Container screen for the uplink linking GUI. Uses a programmatic background (no texture asset):
 * a panel, slot cells, a status line, and an Unlink button.
 */
public class UplinkScreen extends AbstractContainerScreen<UplinkMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;

    public UplinkScreen(UplinkMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Unlink"), b -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, UplinkMenu.UNLINK_BUTTON);
                    }
                })
                .bounds(leftPos + 108, topPos + 31, 56, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);

        drawSlotCell(g, x + 80, y + 35);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotCell(g, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotCell(g, x + 8 + col * 18, y + 142);
        }
    }

    private static void drawSlotCell(GuiGraphics g, int slotX, int slotY) {
        g.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, SLOT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        Component status;
        ItemStack stack = menu.getLinkedStack();
        if (stack.getItem() instanceof ItemMatterManipulator) {
            var loc = MMState.of(stack).getConfig().uplink;
            status = loc != null
                    ? Component.literal("Bound: " + loc.pos.toShortString())
                    : Component.literal("Linking...");
        } else {
            status = Component.literal("Drop a manipulator to link");
        }
        g.drawString(font, status, 8, 20, 0x404040, false);
    }
}
