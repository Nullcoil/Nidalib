package net.nullcoil.nidalib.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nullcoil.nidalib.client.lava.LavaShaderBlend;
import net.nullcoil.nidalib.lava.LavaHeat;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Nidalib's settings screen (opened from Mod Menu): a slider for how wide lava fades at
 * biome borders, a slider for the lava blend steps under shaders, and a button picking
 * dithering or pre-blending. Leaving the screen saves. A new blend width rebuilds the
 * world's meshes; the shader settings live in the block atlas, so they reload resources.
 */
@Environment(EnvType.CLIENT)
public class NidalibConfigScreen extends Screen {
    @Nullable
    private final Screen parent;
    private final int originalLevels;
    private final LavaShaderBlend.Mode originalMode;
    private final int originalBlendWidth;
    private int blendWidth;
    private int levels;
    private LavaShaderBlend.Mode mode;

    public NidalibConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("nidalib.config.title"));
        this.parent = parent;
        NidalibClientConfig config = NidalibClientConfig.get();
        this.originalLevels = config.blendLevels();
        this.originalMode = config.shaderBlendMode();
        this.originalBlendWidth = config.blendWidth();
        this.blendWidth = this.originalBlendWidth;
        this.levels = this.originalLevels;
        this.mode = this.originalMode;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 50;

        OptionSlider widthSlider = new OptionSlider(x, y, LavaHeat.FADE_DISTANCE_OPTIONS, "nidalib.config.blend_width",
                () -> this.blendWidth, value -> this.blendWidth = value);
        widthSlider.setTooltip(Tooltip.create(Component.translatable("nidalib.config.blend_width.tooltip")));
        this.addRenderableWidget(widthSlider);

        OptionSlider levelsSlider = new OptionSlider(x, y + 24, LavaShaderBlend.LEVEL_OPTIONS, "nidalib.config.blend_levels",
                () -> this.levels, value -> this.levels = value);
        levelsSlider.setTooltip(Tooltip.create(Component.translatable("nidalib.config.blend_levels.tooltip")));
        this.addRenderableWidget(levelsSlider);

        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
                    LavaShaderBlend.Mode[] modes = LavaShaderBlend.Mode.values();
                    this.mode = modes[(this.mode.ordinal() + 1) % modes.length];
                    button.setMessage(modeLabel());
                })
                .bounds(x, y + 48, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("nidalib.config.shader_blend.tooltip")))
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(x, y + 80, 200, 20)
                .build());
    }

    private Component modeLabel() {
        return Component.translatable("nidalib.config.shader_blend",
                Component.translatable("nidalib.config.shader_blend." + this.mode.id));
    }

    @Override
    public void onClose() {
        NidalibClientConfig config = NidalibClientConfig.get();
        config.setBlendLevels(this.levels);
        config.setShaderBlendMode(this.mode);
        config.setBlendWidth(this.blendWidth);
        config.save();

        this.minecraft.setScreenAndShow(this.parent);
        if (this.levels != this.originalLevels || this.mode != this.originalMode) {
            // Rebuilds the block atlas with the new textures (and re-meshes the world)
            this.minecraft.reloadResourcePacks();
        } else if (this.blendWidth != this.originalBlendWidth && this.minecraft.level != null) {
            // Lava colors are baked into chunk meshes; rebuild them with the new fade
            this.minecraft.levelExtractor.allChanged();
        }
    }

    /** Steps through a fixed list of values, snapping to each. */
    private static class OptionSlider extends AbstractSliderButton {
        private final List<Integer> options;
        private final String key;
        private final IntSupplier getter;
        private final IntConsumer setter;

        OptionSlider(int x, int y, List<Integer> options, String key, IntSupplier getter, IntConsumer setter) {
            super(x, y, 200, 20, Component.empty(), Math.max(0, options.indexOf(getter.getAsInt())) / (double) (options.size() - 1));
            this.options = options;
            this.key = key;
            this.getter = getter;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            // Called by the super constructor too, before the fields are set
            if (this.getter == null) return;
            this.setMessage(Component.translatable(this.key, this.getter.getAsInt()));
        }

        @Override
        protected void applyValue() {
            int index = (int) Math.round(this.value * (this.options.size() - 1));
            this.setter.accept(this.options.get(index));
            this.value = index / (double) (this.options.size() - 1);
        }
    }
}
