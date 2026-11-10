package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

import java.util.EnumSet;

/** Block-entity update packets plus storage-type entity spawns (chest/hopper minecarts, chest boats). */
public class RelapseV4 extends RelapseBase {
    public RelapseV4() {
        super("relapse-v4", "Marks block entity updates and storage entities below your Y filter.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        forEachPos(event.packet, EnumSet.of(Kind.BLOCK_ENTITY), (k, pos) -> mark(pos));
        if (event.packet instanceof EntitySpawnS2CPacket p) {
            EntityType<?> t = p.getEntityType();
            if (t == EntityType.CHEST_MINECART || t == EntityType.HOPPER_MINECART || t == EntityType.OAK_CHEST_BOAT
                || t == EntityType.SPAWNER_MINECART) {
                mark(p.getX(), p.getY(), p.getZ());
            }
        }
    }
}
