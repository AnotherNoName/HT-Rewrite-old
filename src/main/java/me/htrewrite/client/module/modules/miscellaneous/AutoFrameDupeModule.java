package me.htrewrite.client.module.modules.miscellaneous;

import me.htrewrite.client.event.custom.player.PlayerUpdateEvent;
import me.htrewrite.client.module.Module;
import me.htrewrite.client.module.ModuleType;
import me.htrewrite.client.util.TickedTimer;
import me.htrewrite.exeterimports.mcapi.settings.ValueSetting;
import me.zero.alpine.fork.listener.EventHandler;
import me.zero.alpine.fork.listener.Listener;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public class AutoFrameDupeModule extends Module {
    public static final ValueSetting<Double> tickDelay = new ValueSetting<>("TickDelay", null, 1d, 0d, 20d);

    private TickedTimer tickedTimer;
    private boolean waiting = false;
    private boolean sending = false;
    private Entity entity;
    public AutoFrameDupeModule() {
        super("AutoFrameDupe", "Performs the frame dupe.", ModuleType.Miscellaneous, 0);
        addOption(tickDelay);
        endOption();

        tickedTimer = new TickedTimer();
        tickedTimer.stop();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        sending = false;
        tickedTimer.start();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        tickedTimer.stop();
    }

    private boolean isValidTileEntity(Entity entity) { return (entity instanceof EntityItemFrame)&&mc.player.getDistance(entity)<4f; }

    @EventHandler
    private Listener<PlayerUpdateEvent> updateEventListener = new Listener<>(event -> {
        if(!tickedTimer.passed(tickDelay.getValue().intValue()) || waiting)
            return;
        entity = mc.world.loadedEntityList.stream()
                .filter(loadedEntity -> isValidTileEntity(loadedEntity))
                .min(Comparator.comparing(loadedEntity -> mc.player.getDistance(loadedEntity.getPosition().getX(), loadedEntity.getPosition().getY(), loadedEntity.getPosition().getZ())))
                .orElse(null);
        if(entity == null) {
            sendMessage("&cItemFrame not found in a range of 4 blocks!");
            toggle();
            return;
        }

        mc.player.connection.sendPacket(sending?new CPacketUseEntity(entity):new CPacketUseEntity(entity, EnumHand.MAIN_HAND));
        sending = !sending;
        tickedTimer.reset();
    });
}