package mekanism.smartfactory.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;


public class SmartFactoryGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return MekanismSmartFactory.proxy.getServerGui(ID, player, world, new BlockPos(x, y, z));
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return MekanismSmartFactory.proxy.getClientGui(ID, player, world, new BlockPos(x, y, z));
    }
}
