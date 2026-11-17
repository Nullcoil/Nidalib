package net.nullcoil.nidalib.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nullcoil.nidalib.client.lava.LavaShaderBlend;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Nidalib's settings screen (opened from Mod Menu): a slider for the lava blend steps
 * under shaders, and a button picking dithering or pre-blending. Leaving the screen saves,
 * and reloads resources if anything changed, since both live in the block atlas.
 */
@Environment(EnvType.CLIENT)
public class NidalibConfigScreen extends Screen {
    private static final List<Integer> LEVELS = LavaShaderBlend.LEVEL_OPTIONS;

    @Nullable
    private final Screen parent;
    private final int originalLevels;
    private final LavaShaderBlend.Mode originalMode;
    private int levels;
    private LavaShaderBlend.Mode mode;

    public NidalibConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("nidalib.config.title"));
        this.parent = parent;
        NidalibClientConfig config = NidalibClientConfig.get();
        this.originalLevels = config.blendLevels();
        this.originalMode = config.shaderBlendMode();
        this.levels = this.originalLevels;
        this.mode = this.originalMode;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 38;

        LevelsSlider slider = new LevelsSlider(x, y);
        slider.setTooltip(Tooltip.create(Component.translatable("nidalib.config.blend_levels.tooltip")));
        this.addRenderableWidget(slider);

        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
                    LavaShaderBlend.Mode[] modes = LavaShaderBlend.Mode.values();
                    this.mode = modes[(this.mode.ordinal() + 1) % modes.length];
                    button.setMessage(modeLabel());
                })
                .bounds(x, y + 24, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("nidalib.config.shader_blend.tooltip")))
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(x, y + 56, 200, 20)
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
        config.save();

        this.minecraft.setScreenAndShow(this.parent);
        if (this.levels != this.originalLevels || this.mode != this.originalMode) {
            // Rebuilds the block atlas with the new textures (and re-meshes the world)
            this.minecraft.reloadResourcePacks();
        }
    }

    /** Steps through {@link LavaShaderBlend#LEVEL_OPTIONS}, snapping to each. */
    private class LevelsSlider extends AbstractSliderButton {
        LevelsSlider(int x, int y) {
            super(x, y, 200, 20, Component.empty(), LEVELS.indexOf(NidalibConfigScreen.this.levels) / (double) (LEVELS.size() - 1));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("nidalib.config.blend_levels", NidalibConfigScreen.this.levels));
        }

        @Override
        protected void applyValue() {
            int index = (int) Math.round(this.value * (LEVELS.size() - 1));
            NidalibConfigScreen.this.levels = LEVELS.get(index);
            this.value = index / (double) (LEVELS.size() - 1);
        }
    }
}
