package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

/** Outlines chosen blocks: from loaded chunks, and from block-update packets (which are never obfuscated). */
public class RelapseCustomBlockOutline extends RelapseBase {
    private final Setting<List<Block>> blocks = settings.getDefaultGroup().add(new BlockListSetting.Builder()
        .name("blocks").defaultValue(Blocks.ANCIENT_DEBRIS, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE).build());
    private final Setting<Integer> radius = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("chunk-radius").defaultValue(4).range(1, 16).sliderRange(1, 16).build());
    private int ticks;

    public RelapseCustomBlockOutline() {
        super("relapse-custom-block-outline", "Outlines selected blocks, including ones revealed by update packets.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket p) {
            if (blocks.get().contains(p.getState().getBlock())) mark(p.getPos());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> { if (blocks.get().contains(state.getBlock())) mark(pos); });
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || ++ticks < 40) return;
        ticks = 0;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4, r = radius.get();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    if (baseY > belowY.get()) continue;
                    ChunkSection s = sections[i];
                    if (s.isEmpty() || !s.hasAny(st -> blocks.get().contains(st.getBlock()))) continue;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++)
                                if (blocks.get().contains(s.getBlockState(x, y, z).getBlock()))
                                    mark(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                }
            }
        }
    }
}
