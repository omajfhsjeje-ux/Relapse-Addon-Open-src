package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts every world-referencing packet that points at or below the Y filter and prints a summary. */
public class RelapseDebugV5 extends RelapseBase {
    private final Setting<Boolean> summary = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("chat-summary").description("Print a packet summary every 5 seconds.").defaultValue(true).build());

    private final Map<Kind, AtomicInteger> counts = new EnumMap<>(Kind.class);
    private int ticks;

    public RelapseDebugV5() {
        super("relapse-debug-v5", "Logs which packet types leak positions below your Y filter.");
        for (Kind k : Kind.values()) counts.put(k, new AtomicInteger());
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        forEachPos(event.packet, EnumSet.allOf(Kind.class), (kind, pos) -> {
            if (pos.getY() > belowY.get()) return;
            counts.get(kind).incrementAndGet();
            mark(pos);
        });
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++ticks < 100) return;
        ticks = 0;
        StringBuilder sb = new StringBuilder();
        for (Kind k : Kind.values()) {
            int n = counts.get(k).getAndSet(0);
            if (n > 0) sb.append(k).append('=').append(n).append(' ');
        }
        if (summary.get() && sb.length() > 0) info("Leaks: " + sb.toString().trim());
    }
}
