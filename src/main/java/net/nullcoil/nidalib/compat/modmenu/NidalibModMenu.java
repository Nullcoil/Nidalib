package net.nullcoil.nidalib.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.nullcoil.nidalib.client.config.NidalibConfigScreen;

/**
 * Mod Menu's config button for Nidalib. Only Mod Menu loads this entrypoint, so Nidalib
 * runs fine without Mod Menu installed.
 */
public class NidalibModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NidalibConfigScreen::new;
    }
}
