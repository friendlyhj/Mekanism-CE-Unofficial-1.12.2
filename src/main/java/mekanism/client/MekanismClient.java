package mekanism.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPI.BoxBlacklistEvent;
import mekanism.client.render.obj.TransmitterModel;
import mekanism.client.voice.VoiceClient;
import mekanism.common.Mekanism;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.network.PacketKey.KeyMessage;
import mekanism.common.security.SecurityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.UUID;

public class MekanismClient extends Mekanism {

    public static Map<UUID, SecurityData> clientSecurityMap = new Object2ObjectOpenHashMap<>();
    public static Map<UUID, String> clientUUIDMap = new Object2ObjectOpenHashMap<>();

    public static VoiceClient voiceClient;
    public static long ticksPassed = 0;

    public static void updateKey(KeyBinding key, int type) {
        boolean down = Minecraft.getMinecraft().currentScreen == null && key.isKeyDown();
        if (down != keyMap.has(Minecraft.getMinecraft().player, type)) {
            Mekanism.packetHandler.sendToServer(new KeyMessage(type, down));
            keyMap.update(Minecraft.getMinecraft().player, type, down);
        }
    }

    public static void updateKey(boolean pressed, int type) {
        if (Minecraft.getMinecraft().player != null) {
            boolean down =  Minecraft.getMinecraft().currentScreen == null && pressed;
            if (down != Mekanism.keyMap.has(Minecraft.getMinecraft().player, type)) {
                Mekanism.packetHandler.sendToServer(new KeyMessage(type, down));
                keyMap.update(Minecraft.getMinecraft().player, type, down);
            }
        }
    }

    public static void reset() {
        clientSecurityMap.clear();
        clientUUIDMap.clear();

        if (MekanismConfig.current().general.voiceServerEnabled.val()) {
            if (MekanismClient.voiceClient != null) {
                MekanismClient.voiceClient.disconnect();
                MekanismClient.voiceClient = null;
            }
        }

        ClientTickHandler.tickingSet.clear();
        ClientTickHandler.portableTeleports.clear();

        TransmitterModel.clearCache();

        MekanismAPI.getBoxIgnore().clear();
        MekanismAPI.getBoxModIgnore().clear();
        MinecraftForge.EVENT_BUS.post(new BoxBlacklistEvent());

        Mekanism.playerState.clear();
        Mekanism.activeVibrators.clear();
        Mekanism.freeRunnerOn.clear();
        Mekanism.jumpBoostOn.clear();
        Mekanism.stepAssistOn.clear();

        SynchronizedBoilerData.clientHotMap.clear();

        MekanismConfig.setSyncedConfig(null);
        Mekanism.proxy.onConfigSync(false);

        for (IModule module : Mekanism.modulesLoaded) {
            module.resetClient();
        }
    }
}
