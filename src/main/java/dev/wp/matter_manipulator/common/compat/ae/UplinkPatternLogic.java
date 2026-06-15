package dev.wp.matter_manipulator.common.compat.ae;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

/**
 * The {@link PatternProviderLogic} backing the uplink's plan patterns. Patterns are hidden from a
 * Pattern Access Terminal ({@code PATTERN_ACCESS_TERMINAL = NO}) so they can't be edited or taken
 * out, but remain usable by the autocrafting system (the Plan token shows as craftable in a
 * crafting terminal). {@link #pushPattern} forwards the crafted inputs straight to network storage
 * via {@link UplinkLogic}, so the uplink never holds items.
 */
public class UplinkPatternLogic extends PatternProviderLogic {

    private final UplinkLogic uplink;

    public UplinkPatternLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, UplinkLogic uplink, int slots) {
        super(mainNode, host, slots);
        this.uplink = uplink;
        getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.NO);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        uplink.onPatternPush(patternDetails, inputHolder);
        return true;
    }
}
