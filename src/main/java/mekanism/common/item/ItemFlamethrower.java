package mekanism.common.item;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismFluids;
import mekanism.common.base.IItemNetwork;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemFlamethrower extends ItemMekanism implements IGasItem, IItemNetwork, IItemHUDProvider {

    public int TRANSFER_RATE = 16;

    public ItemFlamethrower() {
        super();
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        GasStack gasStack = getGas(itemstack);
        if (gasStack == null) {
            list.add(LangUtils.localize("tooltip.noGas") + ".");
        } else {
            list.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
        }
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.mode") + ": " + EnumColor.GREY + getMode(itemstack).getName());
    }

    public void useGas(ItemStack stack) {
        GasStack gas = getGas(stack);
        if (gas != null) {
            setGas(stack, new GasStack(gas.getGas(), gas.amount - 1));
        }
    }

    @Override
    public int getMaxGas(ItemStack itemstack) {
        return MekanismConfig.current().general.maxFlamethrowerGas.val();
    }

    @Override
    public int getRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    @Override
    public int addGas(ItemStack itemstack, GasStack stack) {
        if (getGas(itemstack) != null && getGas(itemstack).getGas() != stack.getGas()) {
            return 0;
        }
        if (stack.getGas() != MekanismFluids.Hydrogen) {
            return 0;
        }
        int toUse = Math.min(getMaxGas(itemstack) - getStored(itemstack), Math.min(getRate(itemstack), stack.amount));
        setGas(itemstack, new GasStack(stack.getGas(), getStored(itemstack) + toUse));
        return toUse;
    }

    @Override
    public GasStack removeGas(ItemStack itemstack, int amount) {
        return null;
    }

    public int getStored(ItemStack itemstack) {
        return getGas(itemstack) != null ? getGas(itemstack).amount : 0;
    }

    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        return type == MekanismFluids.Hydrogen;
    }

    @Override
    public boolean canProvideGas(ItemStack itemstack, Gas type) {
        return false;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getStored(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1D - ((getGas(stack) != null ? (double) getGas(stack).amount : 0D) / (double) getMaxGas(stack));
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        GasStack gas = getGas(stack);
        if (gas != null) {
            MekanismRenderer.color(gas);
            return gas.getGas().getTint();
        } else {
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public GasStack getGas(ItemStack itemstack) {
        return GasStack.readFromNBT(ItemDataUtils.getCompound(itemstack, "stored"));
    }

    @Override
    public void setGas(ItemStack itemstack, GasStack stack) {
        if (stack == null || stack.amount == 0) {
            ItemDataUtils.removeData(itemstack, "stored");
        } else {
            int amount = Math.max(0, Math.min(stack.amount, getMaxGas(itemstack)));
            GasStack gasStack = new GasStack(stack.getGas(), amount);
            ItemDataUtils.setCompound(itemstack, "stored", gasStack.write(new NBTTagCompound()));
        }
    }

    public ItemStack getEmptyItem() {
        ItemStack empty = new ItemStack(this);
        setGas(empty, null);
        return empty;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack empty = new ItemStack(this);
        setGas(empty, null);
        list.add(empty);

        ItemStack filled = new ItemStack(this);
        setGas(filled, new GasStack(MekanismFluids.Hydrogen, ((IGasItem) filled.getItem()).getMaxGas(filled)));
        list.add(filled);
    }

    public void incrementMode(ItemStack stack) {
        setMode(stack, getMode(stack).increment());
    }

    public FlamethrowerMode getMode(ItemStack stack) {
        return FlamethrowerMode.values()[ItemDataUtils.getInt(stack, "mode")];
    }

    public void setMode(ItemStack stack, FlamethrowerMode mode) {
        ItemDataUtils.setInt(stack, "mode", mode.ordinal());
    }

    @Override
    public void handlePacketData(ItemStack stack, ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int state = dataStream.readInt();
            setMode(stack, FlamethrowerMode.values()[state]);
        }
    }

    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        list.add(LangUtils.localize("tooltip.flamethrower.mode") + " " + getMode(stack).getName());
        if (getStored(stack) > 0) {
            list.add(LangUtils.localize("tooltip.flamethrower.stored") + " " + EnumColor.ORANGE + getStored(stack));
        }else {
            list.add(LangUtils.localize("tooltip.flamethrower.stored") + " " + EnumColor.ORANGE + LangUtils.localize("tooltip.noGas"));
        }
    }

    public enum FlamethrowerMode {
        COMBAT("tooltip.flamethrower.combat", EnumColor.YELLOW),
        HEAT("tooltip.flamethrower.heat", EnumColor.ORANGE),
        INFERNO("tooltip.flamethrower.inferno", EnumColor.DARK_RED);

        private String unlocalized;
        private EnumColor color;

        FlamethrowerMode(String s, EnumColor c) {
            unlocalized = s;
            color = c;
        }

        public FlamethrowerMode increment() {
            return ordinal() < values().length - 1 ? values()[ordinal() + 1] : values()[0];
        }

        public String getName() {
            return color + LangUtils.localize(unlocalized);
        }

        public TextComponentTranslation getTextComponent() {
            TextComponentTranslation component = new TextComponentTranslation(unlocalized);
            component.getStyle().setColor(color.textFormatting);
            return component;
        }
    }
}
