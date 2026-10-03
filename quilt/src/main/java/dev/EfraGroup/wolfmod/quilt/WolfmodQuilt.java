package dev.EfraGroup.wolfmod.quilt;

import dev.EfraGroup.wolfmod.Wolfmod;
import net.fabricmc.api.ModInitializer;
import org.quiltmc.loader.api.ModContainer;

/**
 * O Quilt e um fork do Fabric Loader e por isso aceita a mesma interface
 * ModInitializer. O modulo continua compilado em YARN, que e o namespace nativo
 * do Quilt.
 */
public class WolfmodQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer container) {
        Wolfmod.onInitialize();
    }
}
