package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Diagnostic: finds 16x16x16 sections that are one single block state. Real caves/bases are never uniform,
 * so uniform sections under your Y filter are where the server is withholding data.
 * Use it to learn which regions the other modules can actually matter in.
 */
public class RelapseInspectorV7 extends RelapseBase {
    private final Setting<Integer> radius = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("chunk-radius").defaultValue(3).range(1, 8).sliderRange(1, 8).build());
    private final Setting<Boolean> flagAir = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("flag-all-air").defaultValue(false).build());
    private int ticks;

    public RelapseInspectorV7() {
        super("relapse-inspector-v7", "Marks uniform (likely hidden) chunk sections below your Y filter.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || ++ticks < 60) return;
        ticks = 0;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4, r = radius.get();
        int found = 0;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    if (baseY > belowY.get()) continue;
                    ChunkSection s = sections[i];
                    if (s.isEmpty() ? !flagAir.get() : !uniform(s)) continue;
                    mark(new BlockPos((cx << 4) + 8, baseY + 8, (cz << 4) + 8));
                    found++;
                }
            }
        }
        if (found > 0) info("Uniform sections nearby: " + found);
    }

    private static boolean uniform(ChunkSection s) {
        BlockState first = s.getBlockState(0, 0, 0);
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    if (s.getBlockState(x, y, z) != first) return false;
        return true;
    }
}
