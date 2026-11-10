package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

/** Marks item frames / glow item frames underground. Remembers them from the spawn packet even if the server later culls them. */
public class RelapseChunkFinder extends RelapseBase {
    private final Setting<Boolean> normal = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("item-frames").defaultValue(true).build());
    private final Setting<Boolean> glow = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("glow-item-frames").defaultValue(true).build());

    public RelapseChunkFinder() {
        super("relapse-chunk-finder", "Marks chunks that contain underground item frames.");
    }

    private boolean match(EntityType<?> t) {
        return (normal.get() && t == EntityType.ITEM_FRAME) || (glow.get() && t == EntityType.GLOW_ITEM_FRAME);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (match(e.getType())) mark(e.getX(), e.getY(), e.getZ());
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof EntitySpawnS2CPacket p && match(p.getEntityType())) {
            mark(p.getX(), p.getY(), p.getZ());
        }
    }
}
