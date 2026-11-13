package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.EnumSet;

/** Block-entity update packets plus storage-type entity spawns (chest/hopper minecarts, chest boats). */
public class RelapseV4 extends RelapseBase {
    public RelapseV4() {
        super("relapse-v4", "Marks block entity updates and storage entities below your Y filter.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        forEachPos(event.packet, EnumSet.of(Kind.BLOCK_ENTITY), (k, pos) -> mark(pos));
        if (event.packet instanceof ClientboundAddEntityPacket p) {
            EntityType<?> t = p.getType();
            boolean storageMinecart = t == EntityTypes.CHEST_MINECART
                || t == EntityTypes.HOPPER_MINECART
                || t == EntityTypes.SPAWNER_MINECART;
            boolean chestBoat = false;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (id != null) {
                String path = id.getPath();
                chestBoat = path.endsWith("_chest_boat") || path.endsWith("_chest_raft");
            }
            if (storageMinecart || chestBoat) {
                mark(p.getX(), p.getY(), p.getZ());
            }
        }
    }
}
