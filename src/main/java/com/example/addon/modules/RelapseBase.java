package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Shared mark storage + rendering + packet side-channel extraction for every Relapse module. */
public abstract class RelapseBase extends Module {
    public enum Mode { Block, Pillar, Chunk }
    public enum Kind { BLOCK, BLOCK_ENTITY, SOUND, PARTICLE, WORLD_EVENT, ENTITY }

    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgRender = settings.createGroup("Render");

    protected final Setting<Integer> belowY = sgFilter.add(new IntSetting.Builder()
        .name("at-or-below-y").description("Only mark things at or under this Y level.")
        .defaultValue(0).range(-64, 320).sliderRange(-64, 320).build());
    protected final Setting<Integer> maxMarks = sgFilter.add(new IntSetting.Builder()
        .name("max-marks").description("Marks are cleared when this many are stored.")
        .defaultValue(3000).range(10, 50000).sliderRange(100, 10000).build());

    protected final Setting<Mode> mode = sgRender.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Mark a single block, a full pillar, or the whole chunk slab.")
        .defaultValue(Mode.Pillar).build());
    protected final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 40, 40, 40)).build());
    protected final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 40, 40, 255)).build());
    protected final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(false).build());

    protected final Set<BlockPos> marks = ConcurrentHashMap.newKeySet();

    protected RelapseBase(String name, String description) {
        super(AddonTemplate.CATEGORY, name, description);
    }

    protected void mark(BlockPos pos) {
        if (pos.getY() > belowY.get()) return;
        if (marks.size() >= maxMarks.get()) marks.clear();
        marks.add(pos.toImmutable());
    }

    protected void mark(double x, double y, double z) {
        mark(BlockPos.ofFloored(x, y, z));
    }

    @Override
    public void onDeactivate() {
        marks.clear();
    }

    @EventHandler
    private void onRenderBase(Render3DEvent event) {
        for (BlockPos p : marks) {
            int x = p.getX(), y = p.getY(), z = p.getZ();
            switch (mode.get()) {
                case Block -> event.renderer.box(p, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
                case Pillar -> event.renderer.box(x, -64, z, x + 1, 320, z + 1, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
                case Chunk -> {
                    int cx = x & ~15, cz = z & ~15;
                    event.renderer.box(cx, y, cz, cx + 16, y + 1, cz + 16, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
                }
            }
            if (tracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    x + 0.5, y + 0.5, z + 0.5, lineColor.get());
            }
        }
    }

    /**
     * Pulls a position out of any server packet that references the world.
     * Anti-ESP engines hide blocks/entities in the initial chunk data, but many of these
     * follow-up packets still carry real coordinates, so they are the side channel we read.
     */
    protected static void forEachPos(Object pk, EnumSet<Kind> allowed, BiConsumer<Kind, BlockPos> out) {
        if (pk instanceof BlockUpdateS2CPacket p) {
            if (allowed.contains(Kind.BLOCK)) out.accept(Kind.BLOCK, p.getPos());
        } else if (pk instanceof ChunkDeltaUpdateS2CPacket p) {
            if (allowed.contains(Kind.BLOCK)) p.visitUpdates((pos, state) -> out.accept(Kind.BLOCK, pos.toImmutable()));
        } else if (pk instanceof BlockEntityUpdateS2CPacket p) {
            if (allowed.contains(Kind.BLOCK_ENTITY)) out.accept(Kind.BLOCK_ENTITY, p.getPos());
        } else if (pk instanceof PlaySoundS2CPacket p) {
            if (allowed.contains(Kind.SOUND)) out.accept(Kind.SOUND, BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()));
        } else if (pk instanceof ParticleS2CPacket p) {
            if (allowed.contains(Kind.PARTICLE)) out.accept(Kind.PARTICLE, BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()));
        } else if (pk instanceof WorldEventS2CPacket p) {
            if (allowed.contains(Kind.WORLD_EVENT)) out.accept(Kind.WORLD_EVENT, p.getPos());
        } else if (pk instanceof EntitySpawnS2CPacket p) {
            if (allowed.contains(Kind.ENTITY)) out.accept(Kind.ENTITY, BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()));
        }
    }
}
