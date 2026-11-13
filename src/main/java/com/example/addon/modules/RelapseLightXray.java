package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * Light Xray: finds light sources leaking below the Y filter (below y level 0 by default) and marks the
 * whole chunk, rendered through walls - a chunk finder for hidden bases/lights.
 *
 * <p>Every detection path relies on data sent by the server, never on something the player did:
 * <ul>
 *     <li><b>Particle leaks</b> - natural caves under the Y filter never emit server -&gt; client particles.
 *     A light related particle (flame, smoke, lava...) below the filter is a leaked light source.</li>
 *     <li><b>Light packets</b> - the server syncs block light sections to the client (chunk load and light
 *     update packets). Any non - zero nibble below the filter means a light source exists, even if the
 *     block itself is hidden from the client's world view.</li>
 *     <li><b>Block updates</b> - the server tells the client about changed blocks; a light - emitting block
 *     update below the filter is a placed light.</li>
 *     <li><b>Xray scan</b> - last resort when particles and light data are hidden too: loaded chunks are
 *     scanned for blocks with light emission &gt; 0 (works when the server does not hide the blocks).</li>
 * </ul>
 *
 * Detections within the self radius of the player are ignored so your own torches don't flag chunks.
 */
public class RelapseLightXray extends RelapseBase {
    private static final Set<String> LIGHT_PARTICLES = Set.of(
        "flame", "soul_fire_flame", "small_flame", "copper_fire_flame",
        "lava", "dripping_lava", "falling_lava", "landing_lava", "dripping_dripstone_lava",
        "campfire_cosy_smoke", "campfire_signal_smoke",
        "smoke", "large_smoke", "ash", "white_ash"
    );

    private static final int MAX_QUEUE = 64;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> particles = sgGeneral.add(new BoolSetting.Builder()
        .name("particle-leaks")
        .description("Mark server-sent light-related particles below the Y filter.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> lightPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("light-packets")
        .description("Read the block light data the server syncs (chunk light and light update packets) and mark chunks with any non-zero light below the Y filter.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockUpdates = sgGeneral.add(new BoolSetting.Builder()
        .name("block-updates")
        .description("Mark server-sent block updates (single and section updates) for light-emitting blocks below the Y filter.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> xrayScan = sgGeneral.add(new BoolSetting.Builder()
        .name("xray-scan")
        .description("Scan loaded chunks for light-emitting blocks below the Y filter. Works as an xray when the server does not hide the blocks themselves.")
        .defaultValue(true)
        .build());

    // chunk positions waiting for a (re)scan, deduplicated
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
    private final java.util.HashSet<Long> scanQueued = new java.util.HashSet<>();
    private int rescanTimer;

    public RelapseLightXray() {
        super("light-xray", "Finds hidden light sources below the Y filter from server-sent data (light packets, particle leaks, block updates) plus an xray scan, and marks the chunk.");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        scanQueue.clear();
        scanQueued.clear();
        rescanTimer = 0;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!xrayScan.get() && !lightPackets.get()) return;
        LevelChunk chunk = event.chunk();
        enqueueScan(chunk.getPos().x(), chunk.getPos().z());
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.level == null || mc.player == null) return;
        Packet<?> packet = event.packet;

        if (lightPackets.get() && packet instanceof ClientboundLightUpdatePacket lightUpdate) {
            handleLightUpdate(lightUpdate);
            return;
        }

        if (particles.get() && packet instanceof ClientboundLevelParticlesPacket particle) {
            handleParticle(particle);
            return;
        }

        if (blockUpdates.get()) {
            if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
                if (blockUpdate.getBlockState().getLightEmission() > 0) mark(blockUpdate.getPos());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
                sectionUpdate.runUpdates((pos, state) -> {
                    if (state.getLightEmission() > 0) mark(pos);
                });
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            scanQueue.clear();
            scanQueued.clear();
            return;
        }

        // Re-scan the 3x3 chunks around the player every second so fresh placements get caught.
        if (++rescanTimer >= 20) {
            rescanTimer = 0;
            if (xrayScan.get() || lightPackets.get()) {
                int centerCx = mc.player.getBlockX() >> 4, centerCz = mc.player.getBlockZ() >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        enqueueScan(centerCx + dx, centerCz + dz);
                    }
                }
            }
        }

        for (int i = 0; i < 2; i++) {
            Long key = scanQueue.poll();
            if (key == null) break;
            scanQueued.remove(key);
            scanChunk((int) (key >> 32), (int) (long) key);
        }
    }

    private void enqueueScan(int cx, int cz) {
        long key = chunkKey(cx, cz);
        if (!scanQueued.add(key)) return;
        if (scanQueue.size() >= MAX_QUEUE) {
            scanQueued.remove(key);
            return;
        }
        scanQueue.addLast(key);
    }

    /**
     * Scans one chunk below the Y filter. Checks the block light stored in the client's light engine
     * (synced from the server) and, as an xray fallback, light-emitting blocks themselves.
     */
    private void scanChunk(int cx, int cz) {
        Level level = mc.level;
        if (level == null) return;

        int minY = level.getMinY();
        int max = maxY();
        boolean checkLight = lightPackets.get();
        boolean checkBlocks = xrayScan.get();
        if (!checkLight && !checkBlocks) return;

        int baseX = cx << 4, baseZ = cz << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= max; y++) {
            for (int x = baseX; x < baseX + 16; x++) {
                for (int z = baseZ; z < baseZ + 16; z++) {
                    if (withinSelfRadius(x + 0.5, y + 0.5, z + 0.5)) continue;
                    pos.set(x, y, z);

                    if (checkLight && level.getBrightness(LightLayer.BLOCK, pos) > 0) {
                        addScannedChunk(cx, cz);
                        return;
                    }

                    if (checkBlocks && level.getBlockState(pos).getLightEmission() > 0) {
                        addScannedChunk(cx, cz);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Marks a scanned chunk. The self radius was already applied per position, so this bypasses the
     * chunk level filter (a light in the far corner of the player's own chunk should still count).
     */
    private void addScannedChunk(int cx, int cz) {
        forceMarkChunk(cx, cz);
    }

    private void handleParticle(ClientboundLevelParticlesPacket packet) {
        if (packet.getY() > maxY()) return;
        if (withinSelfRadius(packet.getX(), packet.getY(), packet.getZ())) return;

        Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType());
        if (id == null || !LIGHT_PARTICLES.contains(id.getPath())) return;

        mark(packet.getX(), packet.getY(), packet.getZ());
    }

    /**
     * Decodes the block light sections carried by a light update packet. Any non-zero nibble at or below
     * the Y filter marks the chunk. Section indices map to world sections through the light engine's
     * minimum light section; each section is 16^3 nibbles packed little endian (x fastest, y slowest).
     */
    private void handleLightUpdate(ClientboundLightUpdatePacket packet) {
        LevelLightEngine engine = mc.level.getLightEngine();
        int minSection = engine.getMinLightSection();
        int max = maxY();

        BitSet blockMask = packet.getLightData().getBlockYMask();
        List<byte[]> blockUpdates = packet.getLightData().getBlockUpdates();

        int updateIndex = 0;
        for (int section = blockMask.nextSetBit(0); section >= 0; section = blockMask.nextSetBit(section + 1)) {
            if (updateIndex >= blockUpdates.size()) break;
            byte[] data = blockUpdates.get(updateIndex++);

            int baseY = (minSection + section) << 4;
            if (baseY > max) break; // sections ascend, nothing above the filter can match anymore
            if (data == null || data.length == 0) continue;

            for (int i = 0; i < data.length * 2; i++) {
                int linearY = (i >> 8) & 0xf;
                if (baseY + linearY > max) break;

                int nibble = (i & 1) == 0 ? data[i >> 1] & 0xf : (data[i >> 1] >> 4) & 0xf;
                if (nibble != 0) {
                    markChunk(packet.getX(), packet.getZ());
                    return;
                }
            }
        }
    }
}
