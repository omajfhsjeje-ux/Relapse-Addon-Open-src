package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectEndEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Shared base for the Relapse finders. Instead of block-sized ESP boxes, every finder marks whole
 * chunks ("chunk finder" style) and the marked slab is rendered through walls, so suspects can be
 * spotted from far away.
 *
 * Marking rules (same for every finder):
 * <ul>
 *     <li>Only detections at or below the {@code max-y} setting are marked (under y level 0 by default).</li>
 *     <li>Detections within {@code self-radius} blocks of you are ignored (your own torches/blocks).</li>
 *     <li>Marks are chunk granularity: the whole 16x16 slab up to the Y filter is highlighted.</li>
 * </ul>
 *
 * Subclasses implement the actual detection and call {@link #mark(BlockPos)} / {@link #mark(double, double, double)}
 * (position based, applies the Y filter and the self radius) or {@link #markChunk(int, int)} (chunk based,
 * applies only the chunk level self filter, used for signals that have no exact block position).
 */
public abstract class RelapseBase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Only mark detections at or below this Y level. Default is below y = 0.")
        .defaultValue(-1)
        .range(-64, 320)
        .sliderRange(-64, 64)
        .build());

    public final Setting<Integer> selfRadius = sgGeneral.add(new IntSetting.Builder()
        .name("self-radius")
        .description("Ignore detections within this many blocks of you (your own blocks and torches). 0 disables the filter.")
        .defaultValue(4)
        .range(0, 32)
        .sliderRange(0, 16)
        .build());

    public final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the marked chunks.")
        .defaultValue(new SettingColor(255, 176, 0, 110))
        .build());

    public final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Fill alpha of the marked chunks (0 - 255).")
        .defaultValue(110)
        .range(0, 255)
        .sliderRange(0, 255)
        .build());

    private final Set<Long> markedChunks = new HashSet<>();

    protected RelapseBase(String name, String description) {
        super(AddonTemplate.CATEGORY, name, description);
    }

    // --- marking API ---

    protected int maxY() {
        return maxY.get();
    }

    protected static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    protected boolean withinSelfRadius(double x, double y, double z) {
        int r = selfRadius.get();
        if (r <= 0 || mc.player == null) return false;
        double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) r * r;
    }

    /**
     * @return true if the chunk slab (from the world bottom to the Y filter) is close enough to the player
     * that a mark on it is most likely caused by the player themselves.
     */
    protected boolean chunkWithinSelfRadius(int cx, int cz) {
        int r = selfRadius.get();
        if (r <= 0 || mc.player == null || mc.level == null) return false;
        double minX = cx * 16.0, maxX = minX + 16.0;
        double minZ = cz * 16.0, maxZ = minZ + 16.0;
        double minY = mc.level.getMinY(), max = maxY.get();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double nx = clamp(px, minX, maxX), ny = clamp(py, minY, max), nz = clamp(pz, minZ, maxZ);
        double dx = px - nx, dy = py - ny, dz = pz - nz;
        return dx * dx + dy * dy + dz * dz <= (double) r * r;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Marks the chunk containing the given position. Applies the Y filter and the self radius. */
    protected void mark(BlockPos pos) {
        if (pos == null) return;
        mark(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** Marks the chunk containing the given position. Applies the Y filter and the self radius. */
    protected void mark(double x, double y, double z) {
        if (y > maxY.get()) return;
        if (withinSelfRadius(x, y, z)) return;
        addChunk(floorDiv16(x), floorDiv16(z));
    }

    /** Marks a whole chunk. Applies only the chunk level self filter (for signals without an exact position). */
    protected void markChunk(int cx, int cz) {
        if (chunkWithinSelfRadius(cx, cz)) return;
        addChunk(cx, cz);
    }

    /**
     * Marks a whole chunk without any self filter. For detections that already applied the self radius
     * per position (chunk scans), so a light in the far corner of the player's own chunk still counts.
     */
    protected void forceMarkChunk(int cx, int cz) {
        addChunk(cx, cz);
    }

    private void addChunk(int cx, int cz) {
        markedChunks.add(chunkKey(cx, cz));
    }

    private static int floorDiv16(double v) {
        return (int) Math.floor(v) >> 4;
    }

    // --- packet position extraction ---

    public enum Kind {
        BLOCK_ENTITY,
        SOUND,
        PARTICLE,
        WORLD_EVENT
    }

    /**
     * Extracts the positions carried by server -&gt; client packets and feeds them to the consumer.
     * Everything here originates from the server, never from the player's own client logic.
     */
    protected void forEachPos(Packet<?> packet, EnumSet<Kind> kinds, BiConsumer<Kind, BlockPos> consumer) {
        if (kinds.contains(Kind.BLOCK_ENTITY)) {
            if (packet instanceof ClientboundBlockEntityDataPacket p) {
                consumer.accept(Kind.BLOCK_ENTITY, p.getPos());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
                p.runUpdates((pos, state) -> consumer.accept(Kind.BLOCK_ENTITY, pos));
            }
        }

        if (kinds.contains(Kind.SOUND) && packet instanceof ClientboundSoundPacket p) {
            consumer.accept(Kind.SOUND, BlockPos.containing(p.getX(), p.getY(), p.getZ()));
        }

        if (kinds.contains(Kind.PARTICLE) && packet instanceof ClientboundLevelParticlesPacket p) {
            consumer.accept(Kind.PARTICLE, BlockPos.containing(p.getX(), p.getY(), p.getZ()));
        }

        if (kinds.contains(Kind.WORLD_EVENT) && packet instanceof ClientboundLevelEventPacket p) {
            consumer.accept(Kind.WORLD_EVENT, p.getPos());
        }
    }

    // --- lifecycle & rendering ---

    @Override
    public void onDeactivate() {
        markedChunks.clear();
    }

    @EventHandler
    private void onRelapseDisconnect(ServerConnectEndEvent event) {
        markedChunks.clear();
    }

    @EventHandler
    private void onRelapseRender3D(Render3DEvent event) {
        if (markedChunks.isEmpty() || mc.level == null) return;

        double minY = mc.level.getMinY();
        double topY = maxY.get() + 1.0;
        SettingColor c = color.get();
        Color fill = new Color(c.r, c.g, c.b, alpha.get());
        Color line = new Color(c.r, c.g, c.b, 255);

        for (long key : markedChunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            event.renderer.box(cx * 16.0, minY, cz * 16.0, cx * 16.0 + 16.0, topY, cz * 16.0 + 16.0,
                fill, line, ShapeMode.Both, 0);
        }
    }
}
