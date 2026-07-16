package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.common.items.MMItems;
import dev.wp.matter_manipulator.common.items.manipulator.MMTier;

import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;

import static dev.wp.matter_manipulator.MMMod.MODID;

public class MMLanguageProvider extends LanguageProvider {
    public MMLanguageProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.matter_manipulator", "Matter Manipulator");

        add(MMItems.MANIPULATOR_PROTO.get(), "Matter Manipulator (Prototype)");
        add(MMItems.MANIPULATOR_MK1.get(), "Matter Manipulator MK I");
        add(MMItems.MANIPULATOR_MK2.get(), "Matter Manipulator MK II");
        add(MMItems.MANIPULATOR_MK3.get(), "Matter Manipulator MK III");

        for (var tier : MMTier.values()) {
            String mark = tier.ordinal() == 0 ? "proto_" : "mk" + tier.ordinal() + "_";
            mark = "part_" + mark;

            String tierName = tier.ordinal() == 0 ? "Prototype" : "MK " + toRoman(tier.ordinal());

            add(itemKey(mark + "frame"), tierName + " Frame");
            add(itemKey(mark + "lens"), tierName + " Lens");
            add(itemKey(mark + "computer_core"), tierName + " Computer Core");
            add(itemKey(mark + "power_core"), tierName + " Power Core");
            add(itemKey(mark + "teleporter_core"), tierName + " Teleporter Core");
        }

        add(itemKey("upgrade_blank"), "Blank Upgrade");
        add(itemKey("upgrade_power_p2p"), "Power P2P Upgrade");
        add(itemKey("upgrade_mining"), "Mining Upgrade");
        add(itemKey("upgrade_speed"), "Speed Upgrade");
        add(itemKey("upgrade_power_eff"), "Power Efficiency Upgrade");

        // AE2 uplink block (registered only with AE2; key added unconditionally so the name
        // resolves whenever the block is present).
        add(blockKey("ae2_uplink"), "Matter Manipulator AE2 Uplink");
        add(itemKey("plan_token"), "Build Plan");
        add(itemKey("plan_token.named"), "Build Plan (%s blocks)");

        add("key.categories.matter_manipulator", "Matter Manipulator");
        add(keyKey("lock_positions"), "Lock Position");
        add(keyKey("copy"), "Mark Copy");
        add(keyKey("cut"), "Mark Cut");
        add(keyKey("paste"), "Mark Paste");
        add(keyKey("reset"), "Reset Coords");
        add(keyKey("start_build"), "Start Build");
        add(keyKey("cancel_build"), "Cancel Build");

        addRadialMenuKeys();
        addTransformKeys();
        addInfoKeys();
    }

    /**
     * Transform screen button/header labels.
     */
    private void addTransformKeys() {
        add(transformKey("info"), "Flip: %s\nUp: %s\nForward: %s");
        add(transformKey("button.rotate_x-"), "Rotate X-");
        add(transformKey("button.rotate_x+"), "Rotate X+");
        add(transformKey("button.rotate_y-"), "Rotate Y-");
        add(transformKey("button.rotate_y+"), "Rotate Y+");
        add(transformKey("button.rotate_z-"), "Rotate Z-");
        add(transformKey("button.rotate_z+"), "Rotate Z+");
        add(transformKey("button.flip_x"), "Flip X");
        add(transformKey("button.flip_y"), "Flip Y");
        add(transformKey("button.flip_z"), "Flip Z");
        add(transformKey("button.reset"), "Reset");
        add(transformKey("header.copy_a"), "Copy (A)");
        add(transformKey("header.copy_b"), "Copy (B)");
        add(transformKey("header.move_a"), "Move (A)");
        add(transformKey("header.move_b"), "Move (B)");
        add(transformKey("header.paste"), "Paste");
        add(transformKey("header.stacking"), "Stacking");
        add(transformKey("header.offset"), "Offset");
        add(transformKey("header.from"), "From");
        add(transformKey("header.to"), "To");
    }

    /**
     * Radial menu labels.
     */
    private void addRadialMenuKeys() {
        add(guiKey("title"), "Matter Manipulator");
        add(guiKey("mode"), "Place Mode");
        add(guiKey("shape"), "Shape");
        add(guiKey("remove"), "Remove Mode");
        add(guiKey("clear_coords"), "Clear Coords");
        add(guiKey("set_mode"), "Set Mode");
        add(guiKey("set_remove_mode"), "Set Remove Mode");
        add(guiKey("remove_none"), "Remove None");
        add(guiKey("remove_replaceable"), "Remove Replaceable");
        add(guiKey("remove_all"), "Remove All");
        add(guiKey("geometry"), "Geometry");
        add(guiKey("moving"), "Moving");
        add(guiKey("copying"), "Copying");
        add(guiKey("exchanging"), "Exchanging");
        add(guiKey("cables"), "Cables");
        add(guiKey("select_blocks"), "Select Blocks");
        add(guiKey("set_corners"), "Set Corners");
        add(guiKey("set_edges"), "Set Edges");
        add(guiKey("set_faces"), "Set Faces");
        add(guiKey("set_volumes"), "Set Volumes");
        add(guiKey("set_all"), "Set All");
        add(guiKey("clear_all"), "Clear All");
        add(guiKey("set_shape"), "Set Shape");
        add(guiKey("line"), "Line");
        add(guiKey("cube"), "Cube");
        add(guiKey("sphere"), "Sphere");
        add(guiKey("cylinder"), "Cylinder");
        add(guiKey("move_coords"), "Move Coords");
        add(guiKey("move_coord_a"), "Move Coord A");
        add(guiKey("move_all"), "Move All");
        add(guiKey("move_coord_b"), "Move Coord B");
        add(guiKey("move_here"), "Move Here");
        add(guiKey("mark_copy"), "Mark Copy");
        add(guiKey("edit_stack"), "Edit Stack");
        add(guiKey("reset"), "Reset");
        add(guiKey("mark"), "Mark");
        add(guiKey("planning"), "Planning");
        add(guiKey("plan_all_manual"), "Plan (All)");
        add(guiKey("plan_missing_manual"), "Plan (Missing)");
        add(guiKey("create_plan_all"), "Create Plan (All)");
        add(guiKey("create_plan_missing"), "Create Plan (Missing)");
        add(guiKey("autocraft_all"), "Auto-Craft (All)");
        add(guiKey("autocraft_missing"), "Auto-Craft (Missing)");
        add(guiKey("clear_plan"), "Clear Plans");
        add(guiKey("link_uplink"), "Link to AE2 Uplink");
        add(guiKey("edit_transform"), "Edit Transform");
        add(guiKey("mark_cut"), "Mark Cut");
        add(guiKey("mark_paste"), "Mark Paste");
        add(guiKey("edit_replace_whitelist"), "Edit Replace Whitelist");
        add(guiKey("clear_replace_whitelist"), "Clear");
        add(guiKey("add_block"), "Add Block");
        add(guiKey("set_block"), "Set Block");
        add(guiKey("set_block_to_replace_with"), "Set Block To Replace With");
        add(guiKey("set_cable"), "Set Cable");
        add(guiKey("smart_copy"), "Smart Copy");
        add(guiKey("back"), "Back");
        add(guiKey("replace_cribs"), "Replace Pattern Buffers with Proxies");
        add(guiKey("link_external_hubs"), "Link External Hubs");
        add(guiKey("tether_empty_hubs"), "Tether Unlinked Hubs");
    }

    /**
     * Chat feedback messages.
     */
    private void addInfoKeys() {
        add(infoKey("building"), "Building %s blocks...");
        add(infoKey("error.out_of_eu"), "Matter Manipulator ran out of energy.");
        add(infoKey("error.could_not_place"), "Could not place %s remaining blocks.");
        add(infoKey("finished_placing"), "Finished placing blocks.");
        add(infoKey("process_move"), "Moved %s blocks (%s remaining)");
        add(infoKey("finished_move"), "Finished moving blocks.");
        add(infoKey("replace_cribs"), "Replace Pattern Buffers with proxies: %s");
        add(infoKey("link_external_hubs"), "Link external wireless hubs: %s");
        add(infoKey("tether_empty_hubs"), "Tether unlinked wireless hubs: %s");
        add(infoKey("warning.could_not_find"), "Could not find item, %s block(s) will be skipped:");
        add(infoKey("placed_remaining"), "Placed %s blocks (%s remaining)");
        add(infoKey("warning.protected_area"), "Tried to break/place a block in a protected area!");
        add(infoKey("added"), "Added %s to %s");
        add(infoKey("set"), "Set %s to: %s");
        add(infoKey("set_block_to_replace_with"), "Set block to replace with to: %s");
        add(infoKey("added_block_to_exchange_whitelist"), "Added block to exchange whitelist: %s");
        add(infoKey("set_exchange_whitelist_to_only_contain"), "Set exchange whitelist to only contain: %s");
        add(infoKey("set_cable"), "Set cables to: %s");
        add(infoKey("cleared_cable"), "Cleared cable block.");
        add(infoKey("error.cannot_mark_copy"), "Cannot mark array: copy region is invalid");
        add(infoKey("error.cannot_mark_paste"), "Cannot mark array: paste coordinate is invalid");
        add(infoKey("error.must_have_copy_region"), "Must have copy region marked.");
        add(infoKey("error.must_have_paste_region"), "Must have paste region marked to detect missing items.");
        add(infoKey("required_items"), "Required items:");
        add(infoKey("required_fluids"), "Required fluids:");
        add(infoKey("none"), "None");
        add(infoKey("uplink_bound"), "Bound to AE2 uplink at %s.");
        add(infoKey("uplink_cleared"), "Cleared AE2 uplink binding.");
        add(infoKey("error.not_an_uplink"), "That isn't an AE2 uplink block.");
        add(infoKey("error.no_uplink"), "No AE2 uplink is bound.");
        add(infoKey("plan_created"), "Plan created — request the Build Plan from your ME terminal to craft it.");
        add(infoKey("autocraft_started"), "Auto-crafting the plan's materials into your ME network.");
        add(infoKey("error.autocraft_failed"), "The network could not craft the plan's materials.");
        add(infoKey("plan_full"), "The uplink has no free plan slots; clear some plans first.");
        add(infoKey("plan_cleared"), "Cleared %s plan(s) from the uplink.");
        add(infoKey("locked"), "Positions are locked. Press the lock key to unlock.");
        add(infoKey("transform_reset"), "Transform reset.");
        add(infoKey("coord_set"), "Coord %s set to %s");
        add(infoKey("corner_a_set"), "Corner A set at %s. Right-click to set corner B.");
        add(infoKey("corner_b_set"), "Corner B set at %s.");
        add(infoKey("copy_a_set"), "Copy A set. Right-click to set copy B.");
        add(infoKey("copy_b_set"), "Copy B set.");
        add(infoKey("cut_a_set"), "Cut A set. Right-click to set cut B.");
        add(infoKey("cut_b_set"), "Cut B set.");
        add(infoKey("paste_set"), "Paste target set.");
        add(infoKey("coords_moved"), "Coordinates moved.");
        add(infoKey("array_marked"), "Array span set to %s x %s x %s.");
    }

    private String transformKey(String key) {
        return MODID + ".transform." + key;
    }

    private String itemKey(String key) {
        return "item." + MODID + "." + key;
    }

    private String blockKey(String key) {
        return "block." + MODID + "." + key;
    }

    private String infoKey(String key) {
        return MODID + ".info." + key;
    }

    private String guiKey(String key) {
        return "gui." + MODID + key;
    }

    private String keyKey(String key) {
        return "key." + MODID + "." + key;
    }

    private String toRoman(int num) {
        String[] roman = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" };
        return roman[num];
    }
}
