package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Relapse");
    public static final HudGroup HUD_GROUP = new HudGroup("Relapse");

    @Override
    public void onInitialize() {
        LOG.info("Relapse Debug loading");
        MeteorClient.EVENT_BUS.registerLambdaFactory("com.example.addon",
            (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        Modules m = Modules.get();
        m.add(new RelapseChunkFinder());
        m.add(new RelapseDebugV5());
        m.add(new RelapseDetectorV2());
        m.add(new RelapseCustomBlockOutline());
        m.add(new RelapseSusFinderV1());
        m.add(new RelapseV4());
        m.add(new RelapseInspectorV7());
        m.add(new RelapseScannerV2());
        m.add(new RelapseReverseV1());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
