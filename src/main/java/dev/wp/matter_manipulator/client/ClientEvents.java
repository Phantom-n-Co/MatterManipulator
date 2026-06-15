package dev.wp.matter_manipulator.client;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.client.gui.RadialMenuScreen;
import dev.wp.matter_manipulator.client.render.MMRenderer;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PendingAction;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.networking.MMNetwork;
import dev.wp.matter_manipulator.common.networking.PacketClearCoords;
import dev.wp.matter_manipulator.common.networking.PacketMMBPressed;
import dev.wp.matter_manipulator.common.networking.PacketResetTransform;
import dev.wp.matter_manipulator.common.networking.PacketSetPendingAction;
import dev.wp.matter_manipulator.common.networking.PacketSetPlaceMode;
import dev.wp.matter_manipulator.common.networking.PacketToggleLock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    @Nullable
    private static Screen deferredScreen;

    /**
     * Opens a screen once no other screen is open. Needed when opening from a radial
     * menu click: the menu tears its own screen down right after the click handler
     * runs (possibly over several animation frames), which would wipe a screen opened
     * synchronously.
     */
    public static void openDeferred(Screen screen) {
        deferredScreen = screen;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || deferredScreen == null) return;
        var mc = Minecraft.getInstance();
        if (mc.screen == null) {
            var screen = deferredScreen;
            deferredScreen = null;
            mc.setScreen(screen);
        } else if (!(mc.screen instanceof RadialMenuScreen)) {
            // Something else (chat, pause menu, ...) got there first; don't ambush the user later
            deferredScreen = null;
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        MMRenderer.onRender(event);
    }

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        String text = MMRenderer.hudText;
        if (text == null || text.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int tw = font.width(text);
        event.getGuiGraphics().drawString(font, text, (w - tw) / 2, h - 50, 0xFFFFFF, true);
    }

    /**
     * Drains accumulated clicks so they don't fire on the next tick. KeyMapping click
     * counts keep accumulating even when we early-return (e.g. while holding a non-manipulator
     * item), so they must be drained to prevent phantom presses after swapping items.
     */
    private static void drainClicks() {
        while (MMKeyMappings.LOCK_POSITIONS.consumeClick()) {}
        while (MMKeyMappings.CUT.consumeClick()) {}
        while (MMKeyMappings.COPY.consumeClick()) {}
        while (MMKeyMappings.PASTE.consumeClick()) {}
        while (MMKeyMappings.RESET.consumeClick()) {}
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        drainClicks();
    }

    /** Intercepts middle-mouse pick-block while holding the manipulator. */
    @SubscribeEvent
    public static void onPickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        if (!(player.getMainHandItem().getItem() instanceof ItemMatterManipulator)) return;

        event.setCanceled(true);
        event.setSwingHand(false);

        if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            MMNetwork.CHANNEL.sendToServer(new PacketMMBPressed(bhr.getBlockPos(), true));
        } else {
            MMNetwork.CHANNEL.sendToServer(new PacketMMBPressed(BlockPos.ZERO, false));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        var held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMatterManipulator)) {
            drainClicks();
            return;
        }

        // If the CONTROL modifier is bound it must be held; unbinding it makes the keys work bare.
        if (!MMKeyMappings.CONTROL.isUnbound() && !MMKeyMappings.CONTROL.isDown()) {
            drainClicks();
            return;
        }

        if (MMKeyMappings.LOCK_POSITIONS.consumeClick()) {
            MMNetwork.CHANNEL.sendToServer(new PacketToggleLock());
            return;
        }

        if (MMKeyMappings.CUT.consumeClick()) {
            if (MMState.of(held).getConfig().placeMode != PlaceMode.MOVING) {
                MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.MOVING));
            }
            if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                MMNetwork.CHANNEL.sendToServer(new PacketClearCoords());
                if (MMModConfig.RESET_TRANSFORM.get()) {
                    MMNetwork.CHANNEL.sendToServer(new PacketResetTransform());
                }
            }
            MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_CUT_A, null));
            return;
        }

        if (MMKeyMappings.COPY.consumeClick()) {
            if (MMState.of(held).getConfig().placeMode != PlaceMode.COPYING) {
                MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.COPYING));
            }
            if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                MMNetwork.CHANNEL.sendToServer(new PacketClearCoords());
                if (MMModConfig.RESET_TRANSFORM.get()) {
                    MMNetwork.CHANNEL.sendToServer(new PacketResetTransform());
                }
            }
            MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_COPY_A, null));
            return;
        }

        if (MMKeyMappings.PASTE.consumeClick()) {
            var state = MMState.of(held);
            var mode = state.getConfig().placeMode;
            if (mode != PlaceMode.COPYING && mode != PlaceMode.MOVING) {
                MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.COPYING));
            }
            MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_PASTE_A, null));
            return;
        }

        if (MMKeyMappings.RESET.consumeClick()) {
            MMNetwork.CHANNEL.sendToServer(new PacketClearCoords());
            if (MMModConfig.RESET_TRANSFORM.get()) {
                MMNetwork.CHANNEL.sendToServer(new PacketResetTransform());
            }
            return;
        }
    }
}
