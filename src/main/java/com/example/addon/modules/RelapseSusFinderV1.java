package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.EnumSet;

/** Sounds, particles and world events (doors, pistons, portals...) carry exact positions regardless of hidden blocks. */
public class RelapseSusFinderV1 extends RelapseBase {
    public RelapseSusFinderV1() {
        super("relapse-sus-finder-v1", "Marks sounds/particles/world events below your Y filter.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        forEachPos(event.packet, EnumSet.of(Kind.SOUND, Kind.PARTICLE, Kind.WORLD_EVENT), (k, pos) -> mark(pos));
    }
}
