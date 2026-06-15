package dev.wp.matter_manipulator.client.gui;

import dev.wp.matter_manipulator.client.gui.RadialMenu.RadialMenuClickHandler;
import dev.wp.matter_manipulator.client.gui.RadialMenu.RadialMenuOption;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Builder that helps constructing radial menus with options and nested sub-menus.
 */
@OnlyIn(Dist.CLIENT)
public class RadialMenuBuilder {
    public final List<RadialMenuOptionBuilder<RadialMenuBuilder>> options = new ArrayList<>();
    public float innerRadius = 0.25f, outerRadius = 0.60f;

    /** Sets the inner radius (fraction of the screen's shortest dimension). */
    public RadialMenuBuilder innerRadius(float r) {
        this.innerRadius = r;
        return this;
    }

    /** Sets the outer radius. */
    public RadialMenuBuilder outerRadius(float r) {
        this.outerRadius = r;
        return this;
    }

    /** Adds a leaf option. Call {@link RadialMenuOptionBuilderLeaf#done()} to return here. */
    public RadialMenuOptionBuilderLeaf<RadialMenuBuilder> option() {
        var leaf = new RadialMenuOptionBuilderLeaf<>(this);
        options.add(leaf);
        return leaf;
    }

    /** Adds a branch (sub-menu). Call {@link RadialMenuOptionBuilderBranch#done()} to return here. */
    public RadialMenuOptionBuilderBranch<RadialMenuBuilder> branch() {
        var branch = new RadialMenuOptionBuilderBranch<>(this);
        options.add(branch);
        return branch;
    }

    /** Builds the RadialMenu widget. */
    public RadialMenu build() {
        RadialMenu menu = new RadialMenu();
        menu.innerRadius = this.innerRadius;
        menu.outerRadius = this.outerRadius;
        for (var opt : options) opt.apply(menu);
        return menu;
    }

    // ── option builder base ────────────────────────────────────────────────────

    public abstract static class RadialMenuOptionBuilder<Parent> {
        final Parent parent;
        Supplier<String> label = () -> "?";
        double weight = 1;
        BooleanSupplier hidden = () -> false;

        RadialMenuOptionBuilder(Parent parent) {
            this.parent = parent;
        }

        public abstract void apply(RadialMenu menu);

        /** Returns to the parent builder. */
        public Parent done() {
            return parent;
        }
    }

    // ── leaf option ────────────────────────────────────────────────────────────

    public static class RadialMenuOptionBuilderLeaf<Parent> extends RadialMenuOptionBuilder<Parent> {
        private RadialMenuClickHandler onClicked;

        RadialMenuOptionBuilderLeaf(Parent parent) {
            super(parent);
        }

        public RadialMenuOptionBuilderLeaf<Parent> label(String s) {
            this.label = () -> s;
            return this;
        }

        public RadialMenuOptionBuilderLeaf<Parent> label(Supplier<String> s) {
            this.label = s;
            return this;
        }

        public RadialMenuOptionBuilderLeaf<Parent> weight(double w) {
            this.weight = w;
            return this;
        }

        public RadialMenuOptionBuilderLeaf<Parent> hidden(boolean h) {
            this.hidden = () -> h;
            return this;
        }

        public RadialMenuOptionBuilderLeaf<Parent> hidden(BooleanSupplier h) {
            this.hidden = h;
            return this;
        }

        /** Click handler — receives the menu, option, and mouse button. Menu is already closing. */
        public RadialMenuOptionBuilderLeaf<Parent> onClicked(RadialMenuClickHandler h) {
            this.onClicked = h;
            return this;
        }

        /** Convenience: plain Runnable, menu closes automatically after. */
        public RadialMenuOptionBuilderLeaf<Parent> onClicked(Runnable r) {
            this.onClicked = (menu, opt, btn) -> r.run();
            return this;
        }

        @Override
        public void apply(RadialMenu menu) {
            RadialMenuOption opt = new RadialMenuOption();
            opt.label = this.label;
            opt.weight = this.weight;
            opt.hidden = this.hidden;
            opt.onClick = this.onClicked;
            menu.options.add(opt);
        }
    }

    // ── branch (sub-menu) ──────────────────────────────────────────────────────

    public static class RadialMenuOptionBuilderBranch<Parent> extends RadialMenuOptionBuilder<Parent> {
        private final List<RadialMenuOptionBuilder<RadialMenuOptionBuilderBranch<Parent>>> children = new ArrayList<>();

        RadialMenuOptionBuilderBranch(Parent parent) {
            super(parent);
        }

        public RadialMenuOptionBuilderBranch<Parent> label(String s) {
            this.label = () -> s;
            return this;
        }

        public RadialMenuOptionBuilderBranch<Parent> label(Supplier<String> s) {
            this.label = s;
            return this;
        }

        public RadialMenuOptionBuilderBranch<Parent> weight(double w) {
            this.weight = w;
            return this;
        }

        public RadialMenuOptionBuilderBranch<Parent> hidden(boolean h) {
            this.hidden = () -> h;
            return this;
        }

        public RadialMenuOptionBuilderBranch<Parent> hidden(BooleanSupplier h) {
            this.hidden = h;
            return this;
        }

        public RadialMenuOptionBuilderLeaf<RadialMenuOptionBuilderBranch<Parent>> option() {
            var leaf = new RadialMenuOptionBuilderLeaf<>(this);
            children.add(leaf);
            return leaf;
        }

        public RadialMenuOptionBuilderBranch<RadialMenuOptionBuilderBranch<Parent>> branch() {
            var branch = new RadialMenuOptionBuilderBranch<>(this);
            children.add(branch);
            return branch;
        }

        @Override
        public void apply(RadialMenu menu) {
            RadialMenuOption opt = new RadialMenuOption();
            opt.label = this.label;
            opt.weight = this.weight;
            opt.hidden = this.hidden;
            opt.keepOpen = true;
            opt.onClick = (_menu, _opt, _btn) -> {
                _menu.options.clear();
                for (var child : children) child.apply(_menu);
            };
            menu.options.add(opt);
        }
    }
}
