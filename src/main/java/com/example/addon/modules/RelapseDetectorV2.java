package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.EnumSet;

/** Block changes are always sent with real coordinates. Anyone building/mining down there shows up here. */
public class RelapseDetectorV2 extends RelapseBase {
    public RelapseDetectorV2() {
        super("relapse-detector-v2", "Marks block change packets below your Y filter (base/mining activity).");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        forEachPos(event.packet, EnumSet.of(Kind.BLOCK), (k, pos) -> mark(pos));
    }
}
