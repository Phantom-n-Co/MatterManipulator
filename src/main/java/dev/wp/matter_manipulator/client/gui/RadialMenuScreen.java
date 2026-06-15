package dev.wp.matter_manipulator.client.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.ScreenWrapper;

/**
 * Full-screen radial menu wrapper for ModularUI.
 */
@OnlyIn(Dist.CLIENT)
public class RadialMenuScreen extends ScreenWrapper {
    public RadialMenuScreen(RadialMenuBuilder builder) {
        super(createModularScreen(builder));
    }

    private static ModularScreen createModularScreen(RadialMenuBuilder builder) {
        ModularPanel<?> panel = new ModularPanel<>("radial_menu")
                .fullScreenInvisible()
                .child(builder.build().full());
        return new ModularScreen("matter_manipulator", panel);
    }
}
