package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

/**
 * Block entities (chests, spawners, shulkers, beds...) are stored in the chunk's block-entity map.
 * Some anti-ESP setups only scramble the block palette, so this can still list them. Server dependent.
 */
public class RelapseScannerV2 extends RelapseBase {
    private final Setting<Integer> radius = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("chunk-radius").defaultValue(8).range(1, 32).sliderRange(1, 32).build());
    private int ticks;

    public RelapseScannerV2() {
        super("relapse-scanner-v2", "Scans loaded chunks' block entities below your Y filter.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null || ++ticks < 40) return;
        ticks = 0;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4, r = radius.get();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    mark(p);
                }
            }
        }
    }
}
