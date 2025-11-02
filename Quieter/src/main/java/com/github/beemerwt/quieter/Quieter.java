package com.github.beemerwt.quieter;

import com.github.beemerwt.resourcelib.ResourceApi;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.io.File;

public class Quieter implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        var api = ResourceApi.api()
                .orElseThrow(() -> new IllegalStateException("Quieter requires ResourceLib to function. Please install ResourceLib."));

        var dir = api.getPackDirectory().resolve("Quieter");
        api.addBedrockPack(new File(dir.resolve("Quieter-Bedrock.zip").toUri()));
        api.addJavaPack(new File(dir.resolve("Quieter-Java.zip").toUri()));
    }
}
