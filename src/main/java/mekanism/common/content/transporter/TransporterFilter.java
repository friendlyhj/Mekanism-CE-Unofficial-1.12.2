package mekanism.common.content.transporter;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.common.content.filter.IFilter;
import mekanism.common.util.TransporterUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public abstract class TransporterFilter implements IFilter {

    public static final int MAX_LENGTH = 24;

    public static final List<Character> SPECIAL_CHARS = Arrays.asList('*', '-', ' ', '|', '_', '\'');

    public EnumColor color;

    public boolean allowDefault;

    public static TransporterFilter readFromNBT(NBTTagCompound nbtTags) {
        TransporterFilter filter = getType(nbtTags.getInteger("type"));
        filter.read(nbtTags);
        return filter;
    }

    public static TransporterFilter readFromPacket(ByteBuf dataStream) {
        TransporterFilter filter = getType(dataStream.readInt());
        filter.read(dataStream);
        return filter;
    }

    @Nullable
    private static TransporterFilter getType(int type) {
        TransporterFilter filter = null;
        if (type == 0) {
            filter = new TItemStackFilter();
        } else if (type == 1) {
            filter = new TOreDictFilter();
        } else if (type == 2) {
            filter = new TMaterialFilter();
        } else if (type == 3) {
            filter = new TModIDFilter();
        }
        return filter;
    }

    public boolean canFilter(ItemStack itemStack, boolean strict) {
        return !itemStack.isEmpty();
    }

    public abstract Finder getFinder();

    public InvStack getStackFromInventory(StackSearcher searcher, boolean singleItem) {
        return searcher.takeTopStack(getFinder(), singleItem ? 1 : 64);
    }

    public void write(NBTTagCompound nbtTags) {
        nbtTags.setBoolean("allowDefault", allowDefault);
        if (color != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
        }
    }

    protected void read(NBTTagCompound nbtTags) {
        allowDefault = nbtTags.getBoolean("allowDefault");
        if (nbtTags.hasKey("color")) {
            color = TransporterUtils.colors.get(nbtTags.getInteger("color"));
        }
    }

    public void write(TileNetworkList data) {
        data.add(allowDefault);
        if (color != null) {
            data.add(TransporterUtils.colors.indexOf(color));
        } else {
            data.add(-1);
        }
    }

    protected void read(ByteBuf dataStream) {
        allowDefault = dataStream.readBoolean();
        int c = dataStream.readInt();
        if (c != -1) {
            color = TransporterUtils.colors.get(c);
        } else {
            color = null;
        }
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + (color != null ? color.ordinal() : -1);
        return code;
    }

    @Override
    public boolean equals(Object filter) {
        return filter instanceof TransporterFilter filter1&& filter1.color == color;
    }
}
