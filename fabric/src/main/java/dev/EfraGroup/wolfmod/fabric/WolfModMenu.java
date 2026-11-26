package dev.EfraGroup.wolfmod.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu entrypoint: routes to the YACL screen when YACL is installed. */
public final class WolfModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("yacl")) {
            return MissingYaclScreen::new;
        }
        return WolfConfigScreen::create;
    }
}
