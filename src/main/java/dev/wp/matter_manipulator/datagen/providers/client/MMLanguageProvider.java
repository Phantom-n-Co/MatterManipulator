package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.items.MMItems;

import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public class MMLanguageProvider extends LanguageProvider {
    public MMLanguageProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), MMMod.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.matter_manipulator", "Matter Manipulator");

        add(MMItems.MANIPULATOR_PROTO.get(), "Matter Manipulator (Prototype)");
        add(MMItems.MANIPULATOR_MK1.get(), "Matter Manipulator MK I");
        add(MMItems.MANIPULATOR_MK2.get(), "Matter Manipulator MK II");
        add(MMItems.MANIPULATOR_MK3.get(), "Matter Manipulator MK III");

        // AE2 uplink block (registered only with AE2; key added unconditionally so the name
        // resolves whenever the block is present).
        add("block.matter_manipulator.ae2_uplink", "Matter Manipulator AE2 Uplink");
        add("item.matter_manipulator.plan_token", "Build Plan");
        add("item.matter_manipulator.plan_token.named", "Build Plan (%s blocks)");

        add(guiKey("title"), "Matter Manipulator");
        add(guiKey("mode"), "Place Mode");
        add(guiKey("shape"), "Shape");
        add(guiKey("remove"), "Remove Mode");
        add(guiKey("start"), "Start Build");
        add(guiKey("cancel"), "Cancel Build");
        add(guiKey("clear"), "Clear Coords");

        add("key.categories.matter_manipulator", "Matter Manipulator");
        add(keyKey("ctrl"), "Control");
        add(keyKey("lock_positions"), "Lock Position");
        add(keyKey("copy"), "Mark Copy");
        add(keyKey("cut"), "Mark Cut");
        add(keyKey("paste"), "Mark Paste");
        add(keyKey("reset"), "Reset Coords");

        addRadialMenuKeys();
        addTransformKeys();
        addInfoKeys();
    }

    /** Transform screen button/header labels. */
    private void addTransformKeys() {
        add("mm.transform.info", "Flip: %s\nUp: %s\nForward: %s");
        add("mm.transform.button.rotate_x-", "Rotate X-");
        add("mm.transform.button.rotate_x+", "Rotate X+");
        add("mm.transform.button.rotate_y-", "Rotate Y-");
        add("mm.transform.button.rotate_y+", "Rotate Y+");
        add("mm.transform.button.rotate_z-", "Rotate Z-");
        add("mm.transform.button.rotate_z+", "Rotate Z+");
        add("mm.transform.button.flip_x", "Flip X");
        add("mm.transform.button.flip_y", "Flip Y");
        add("mm.transform.button.flip_z", "Flip Z");
        add("mm.transform.button.reset", "Reset");
        add("mm.transform.header.copy", "Copy");
        add("mm.transform.header.copy_a", "Copy (A)");
        add("mm.transform.header.copy_b", "Copy (B)");
        add("mm.transform.header.paste", "Paste");
        add("mm.transform.header.stacking", "Stacking");
    }

    /** Radial menu labels. */
    private void addRadialMenuKeys() {
        add("mm.gui.set_mode", "Set Mode");
        add("mm.gui.set_remove_mode", "Set Remove Mode");
        add("mm.gui.remove_none", "Remove None");
        add("mm.gui.remove_replaceable", "Remove Replaceable");
        add("mm.gui.remove_all", "Remove All");
        add("mm.gui.geometry", "Geometry");
        add("mm.gui.moving", "Moving");
        add("mm.gui.copying", "Copying");
        add("mm.gui.exchanging", "Exchanging");
        add("mm.gui.cables", "Cables");
        add("mm.gui.select_blocks", "Select Blocks");
        add("mm.gui.set_corners", "Set Corners");
        add("mm.gui.set_edges", "Set Edges");
        add("mm.gui.set_faces", "Set Faces");
        add("mm.gui.set_volumes", "Set Volumes");
        add("mm.gui.set_all", "Set All");
        add("mm.gui.clear_all", "Clear All");
        add("mm.gui.set_shape", "Set Shape");
        add("mm.gui.line", "Line");
        add("mm.gui.cube", "Cube");
        add("mm.gui.sphere", "Sphere");
        add("mm.gui.cylinder", "Cylinder");
        add("mm.gui.move_coords", "Move Coords");
        add("mm.gui.move_coord_a", "Move Coord A");
        add("mm.gui.move_all", "Move All");
        add("mm.gui.move_coord_b", "Move Coord B");
        add("mm.gui.move_here", "Move Here");
        add("mm.gui.mark_copy", "Mark Copy");
        add("mm.gui.edit_stack", "Edit Stack");
        add("mm.gui.reset", "Reset");
        add("mm.gui.mark", "Mark");
        add("mm.gui.planning", "Planning");
        add("mm.gui.plan_all_manual", "Plan (All)");
        add("mm.gui.plan_missing_manual", "Plan (Missing)");
        add("mm.gui.create_plan_all", "Create Plan (All)");
        add("mm.gui.create_plan_missing", "Create Plan (Missing)");
        add("mm.gui.autocraft_all", "Auto-Craft (All)");
        add("mm.gui.autocraft_missing", "Auto-Craft (Missing)");
        add("mm.gui.clear_plan", "Clear Plans");
        add("mm.gui.link_uplink", "Link to AE2 Uplink");
        add("mm.gui.edit_transform", "Edit Transform");
        add("mm.gui.mark_cut", "Mark Cut");
        add("mm.gui.mark_paste", "Mark Paste");
        add("mm.gui.edit_replace_whitelist", "Edit Replace Whitelist");
        add("mm.gui.clear", "Clear");
        add("mm.gui.add_block", "Add Block");
        add("mm.gui.set_block", "Set Block");
        add("mm.gui.set_block_to_replace_with", "Set Block To Replace With");
        add("mm.gui.set_cable", "Set Cable");
        add("mm.gui.start_build", "Start Build");
        add("mm.gui.cancel_build", "Cancel Build");
    }

    /** Chat feedback messages. */
    private void addInfoKeys() {
        add("mm.info.building", "Building %s blocks...");
        add("mm.info.error.out_of_eu", "Matter Manipulator ran out of energy.");
        add("mm.info.error.could_not_place", "Could not place %s remaining blocks.");
        add("mm.info.finished_placing", "Finished placing blocks.");
        add("mm.info.process_move", "Moved %s blocks (%s remaining)");
        add("mm.info.finished_move", "Finished moving blocks.");
        add("mm.info.warning.could_not_find", "Could not find item, %s block(s) will be skipped:");
        add("mm.info.placed_remaining", "Placed %s blocks (%s remaining)");
        add("mm.info.warning.protected_area", "Tried to break/place a block in a protected area!");
        add("mm.info.added", "Added %s to %s");
        add("mm.info.set", "Set %s to: %s");
        add("mm.info.set_block_to_replace_with", "Set block to replace with to: %s");
        add("mm.info.added_block_to_exchange_whitelist", "Added block to exchange whitelist: %s");
        add("mm.info.set_exchange_whitelist_to_only_contain", "Set exchange whitelist to only contain: %s");
        add("mm.info.set_cable", "Set cables to: %s");
        add("mm.info.cleared_cable", "Cleared cable block.");
        add("mm.info.error.cannot_mark_copy", "Cannot mark array: copy region is invalid");
        add("mm.info.error.cannot_mark_paste", "Cannot mark array: paste coordinate is invalid");
        add("mm.info.error.must_have_copy_region", "Must have copy region marked.");
        add("mm.info.error.must_have_paste_region", "Must have paste region marked to detect missing items.");
        add("mm.info.required_items", "Required items:");
        add("mm.info.required_fluids", "Required fluids:");
        add("mm.info.none", "None");
        add("mm.info.uplink_bound", "Bound to AE2 uplink at %s.");
        add("mm.info.uplink_cleared", "Cleared AE2 uplink binding.");
        add("mm.info.error.not_an_uplink", "That isn't an AE2 uplink block.");
        add("mm.info.error.no_uplink", "No AE2 uplink is bound.");
        add("mm.info.plan_created", "Plan created — request the Build Plan from your ME terminal to craft it.");
        add("mm.info.autocraft_started", "Auto-crafting the plan's materials into your ME network.");
        add("mm.info.error.autocraft_failed", "The network could not craft the plan's materials.");
        add("mm.info.plan_full", "The uplink has no free plan slots; clear some plans first.");
        add("mm.info.plan_cleared", "Cleared %s plan(s) from the uplink.");
        add("mm.info.locked", "Positions are locked. Press the lock key to unlock.");
        add("mm.info.transform_reset", "Transform reset.");
        add("mm.info.coord_set", "Coord %s set to %s");
        add("mm.info.corner_a_set", "Corner A set at %s. Right-click to set corner B.");
        add("mm.info.corner_b_set", "Corner B set at %s.");
        add("mm.info.copy_a_set", "Copy A set. Right-click to set copy B.");
        add("mm.info.copy_b_set", "Copy B set.");
        add("mm.info.cut_a_set", "Cut A set. Right-click to set cut B.");
        add("mm.info.cut_b_set", "Cut B set.");
        add("mm.info.paste_set", "Paste target set.");
        add("mm.info.coords_moved", "Coordinates moved.");
        add("mm.info.array_marked", "Array span set to %s x %s x %s.");
    }

    private String guiKey(String key) {
        return "gui.matter_manipulator." + key;
    }

    private String keyKey(String key) {
        return "key.matter_manipulator." + key;
    }
}
