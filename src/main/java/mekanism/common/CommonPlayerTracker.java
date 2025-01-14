package mekanism.common;

import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketBoxBlacklist.BoxBlacklistMessage;
import mekanism.common.network.PacketConfigSync.ConfigSyncMessage;
import mekanism.common.network.PacketFlamethrowerData.FlamethrowerDataMessage;
import mekanism.common.network.PacketFreeRunnerData;
import mekanism.common.network.PacketJetpackData.JetpackDataMessage;
import mekanism.common.network.PacketJumpBoostData;
import mekanism.common.network.PacketScubaTankData.ScubaTankDataMessage;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.network.PacketStepAssistData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

public class CommonPlayerTracker {

    public CommonPlayerTracker() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoginEvent(PlayerLoggedInEvent event) {
        MinecraftServer server = event.player.getServer();
        if (!event.player.world.isRemote) {
            if (server == null || !server.isSinglePlayer()) {
                Mekanism.packetHandler.sendTo(new ConfigSyncMessage(MekanismConfig.local()), (EntityPlayerMP) event.player);
                Mekanism.logger.info("Sent config to '" + event.player.getDisplayNameString() + ".'");
            }
            Mekanism.packetHandler.sendTo(new BoxBlacklistMessage(), (EntityPlayerMP) event.player);
            syncChangedData((EntityPlayerMP) event.player);
            Mekanism.packetHandler.sendTo(new SecurityUpdateMessage(SecurityPacket.FULL, null, null), (EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogoutEvent(PlayerLoggedOutEvent event) {
        Mekanism.playerState.clearPlayer(event.player.getUniqueID());
        Mekanism.freeRunnerOn.remove(event.player.getUniqueID());
        Mekanism.jumpBoostOn.remove(event.player.getUniqueID());
        Mekanism.stepAssistOn.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerDimChangedEvent(PlayerChangedDimensionEvent event) {
        Mekanism.playerState.clearPlayer(event.player.getUniqueID());
        Mekanism.freeRunnerOn.remove(event.player.getUniqueID());
        Mekanism.jumpBoostOn.remove(event.player.getUniqueID());
        Mekanism.stepAssistOn.remove(event.player.getUniqueID());
        if (!event.player.world.isRemote) {
            syncChangedData((EntityPlayerMP) event.player);
        }
    }

    private void syncChangedData(EntityPlayerMP player) {
        // TODO: Coalesce all these sync events into a single message
        Mekanism.packetHandler.sendTo(JetpackDataMessage.FULL(Mekanism.playerState.getActiveJetpacks()), player);
        Mekanism.packetHandler.sendTo(ScubaTankDataMessage.FULL(Mekanism.playerState.getActiveGasmasks()), player);
        Mekanism.packetHandler.sendTo(FlamethrowerDataMessage.FULL(Mekanism.playerState.getActiveFlamethrowers()), player);
        Mekanism.packetHandler.sendTo(new PacketFreeRunnerData.FreeRunnerDataMessage(PacketFreeRunnerData.FreeRunnerPacket.FULL, null, false), player);
        Mekanism.packetHandler.sendTo(new PacketJumpBoostData.JumpBoostDataMessage(PacketJumpBoostData.JumpBoostPacket.FULL, null, false), player);
        Mekanism.packetHandler.sendTo(new PacketStepAssistData.StepAssistDataMessage(PacketStepAssistData.StepAssistPacket.FULL, null, false), player);
    }
}
