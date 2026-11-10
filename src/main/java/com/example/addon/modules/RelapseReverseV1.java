package com.example.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental active probe. Sends ABORT_DESTROY_BLOCK for blocks in reach; if the server answers with a
 * block state that differs from what the client holds, the client was being fed a fake block there, and
 * that position is marked. Only works within your reach, and only on servers that answer with the real state.
 * Rate limited; too high a rate can get you kicked.
 */
public class RelapseReverseV1 extends RelapseBase {
    private final Setting<Integer> perTick = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("probes-per-tick").defaultValue(2).range(1, 20).sliderRange(1, 10).build());
    private final Setting<Integer> reach = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("reach").defaultValue(4).range(1, 5).sliderRange(1, 5).build());

    private final Set<BlockPos> probed = ConcurrentHashMap.newKeySet();

    public RelapseReverseV1() {
        super("relapse-reverse-v1", "Probes nearby blocks and marks ones where the server disagrees with your client.");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        probed.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        int r = reach.get(), sent = 0;
        BlockPos o = mc.player.getBlockPos();
        outer:
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = o.add(dx, dy, dz);
                    if (!probed.add(p)) continue;
                    mc.getNetworkHandler().sendPacket(
                        new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, p, Direction.UP, 0));
                    if (++sent >= perTick.get()) break outer;
                }
        if (probed.size() > 20000) probed.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof BlockUpdateS2CPacket p) || mc.world == null) return;
        BlockPos pos = p.getPos();
        if (probed.contains(pos) && mc.world.getBlockState(pos) != p.getState()) mark(pos);
    }
}
