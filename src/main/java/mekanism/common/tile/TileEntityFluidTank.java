package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.IConfigurable;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.*;
import mekanism.common.util.FluidContainerUtils.ContainerEditMode;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileEntityFluidTank extends TileEntityContainerBlock implements IActiveState, IConfigurable, IFluidHandlerWrapper, ISustainedTank, IFluidContainerManager,
        ITankManager, ISecurityTile, ITierUpgradeable, ITieredTile, IComparatorSupport {

    public boolean isActive;

    public boolean clientActive;

    public FluidTank fluidTank;

    public ContainerEditMode editMode = ContainerEditMode.BOTH;

    public FluidTankTier tier = FluidTankTier.BASIC;

    public int updateDelay;

    public int prevAmount;

    public int valve;
    public FluidStack valveFluid;

    public float prevScale;

    public boolean needsPacket;

    public int currentRedstoneLevel;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntityFluidTank() {
        super("FluidTank");
        fluidTank = new FluidTankSync(tier.getStorage());
        inventory = NonNullListSynchronized.withSize(2, ItemStack.EMPTY);
    }

    @Override
    public boolean upgrade(BaseTier upgradeTier) {
        if (upgradeTier.ordinal() != tier.ordinal() + 1) {
            return false;
        }
        tier = FluidTankTier.values()[upgradeTier.ordinal()];
        fluidTank.setCapacity(tier.getStorage());
        Mekanism.packetHandler.sendUpdatePacket(this);
        markNoUpdateSync();
        return true;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return false;
    }

    @Override
    public void onUpdate() {
        if (world.isRemote) {
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0 && clientActive != isActive) {
                    isActive = clientActive;
                    MekanismUtils.updateBlock(world, getPos());
                }
            }

            float targetScale = (float) (fluidTank.getFluid() != null ? fluidTank.getFluid().amount : 0) / fluidTank.getCapacity();
            if (Math.abs(prevScale - targetScale) > 0.01) {
                prevScale = (9 * prevScale + targetScale) / 10;
            }
        } else {
            if (fluidTank.getFluid() != null && fluidTank.getFluidAmount() == 0) {
                fluidTank.setFluid(null);
            }
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0 && clientActive != isActive) {
                    needsPacket = true;
                }
            }

            if (valve > 0) {
                valve--;
                if (valve == 0) {
                    valveFluid = null;
                    needsPacket = true;
                }
            }

            if (fluidTank.getFluidAmount() != prevAmount) {
                MekanismUtils.saveChunk(this);
                needsPacket = true;
            }

            prevAmount = fluidTank.getFluidAmount();
            if (!inventory.get(0).isEmpty()) {
                manageInventory();
            }
            if (isActive) {
                activeEmit();
            }

            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                markNoUpdateSync();
                currentRedstoneLevel = newRedstoneLevel;
            }
            if (needsPacket) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
            needsPacket = false;
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.FluidTank" + tier.getBaseTier().getSimpleName() + ".name");
    }

    private void activeEmit() {
        if (fluidTank.getFluid() != null) {
            TileEntity tileEntity = Coord4D.get(this).offset(EnumFacing.DOWN).getTileEntity(world);
            if (CapabilityUtils.hasCapability(tileEntity, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.UP)) {
                IFluidHandler handler = CapabilityUtils.getCapability(tileEntity, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.UP);
                FluidStack toDrain = new FluidStack(fluidTank.getFluid(), Math.min(tier.getOutput(), fluidTank.getFluidAmount()));
                fluidTank.drain(handler.fill(toDrain, true), tier != FluidTankTier.CREATIVE);
            }
        }
    }

    private void manageInventory() {
        if (FluidContainerUtils.isFluidContainer(inventory.get(0))) {
            FluidStack ret = FluidContainerUtils.handleContainerItem(this, inventory, editMode, fluidTank.getFluid(), getCurrentNeeded(), 0, 1, null);

            if (ret != null) {
                fluidTank.setFluid(PipeUtils.copy(ret, Math.min(fluidTank.getCapacity(), ret.amount)));
                if (tier == FluidTankTier.CREATIVE) {
                    FluidStack fluid = fluidTank.getFluid();
                    if (fluid != null) {
                        fluid.amount = Integer.MAX_VALUE;
                    }
                } else {
                    int rejects = Math.max(0, ret.amount - fluidTank.getCapacity());
                    if (rejects > 0) {
                        pushUp(PipeUtils.copy(ret, rejects), true);
                    }
                }
            } else if (tier != FluidTankTier.CREATIVE) {
                fluidTank.setFluid(null);
            }
        }
    }

    public int pushUp(FluidStack fluid, boolean doFill) {
        Coord4D up = Coord4D.get(this).offset(EnumFacing.UP);
        TileEntity tileEntity = up.getTileEntity(world);
        if (tileEntity instanceof TileEntityFluidTank) {
            IFluidHandler handler = CapabilityUtils.getCapability(tileEntity, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.DOWN);
            if (PipeUtils.canFill(handler, fluid)) {
                return handler.fill(fluid, doFill);
            }
        }
        return 0;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        return slotID == 1;
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            return FluidContainerUtils.isFluidContainer(itemstack);
        }
        return false;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        if (side == EnumFacing.DOWN) {
            return new int[]{1};
        } else if (side == EnumFacing.UP) {
            return new int[]{0};
        }
        return InventoryUtils.EMPTY;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("editMode", editMode.ordinal());
        if (fluidTank.getFluid() != null) {
            nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        tier = FluidTankTier.values()[nbtTags.getInteger("tier")];
        clientActive = isActive = nbtTags.getBoolean("isActive");
        editMode = ContainerEditMode.values()[nbtTags.getInteger("editMode")];
        //Needs to be outside the hasKey check because this is just based on the tier which is known information
        fluidTank.setCapacity(tier.getStorage());
        if (nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            FluidTankTier prevTier = tier;
            tier = FluidTankTier.values()[dataStream.readInt()];
            fluidTank.setCapacity(tier.getStorage());

            clientActive = dataStream.readBoolean();
            valve = dataStream.readInt();
            editMode = ContainerEditMode.values()[dataStream.readInt()];
            if (valve > 0) {
                valveFluid = TileUtils.readFluidStack(dataStream);
            } else {
                valveFluid = null;
            }

            TileUtils.readTankData(dataStream, fluidTank);
            if (prevTier != tier || (updateDelay == 0 && clientActive != isActive)) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }

    public int getCurrentNeeded() {
        int needed = fluidTank.getCapacity() - fluidTank.getFluidAmount();
        if (tier == FluidTankTier.CREATIVE) {
            return Integer.MAX_VALUE;
        }
        Coord4D top = Coord4D.get(this).offset(EnumFacing.UP);
        TileEntity topTile = top.getTileEntity(world);
        if (topTile instanceof TileEntityFluidTank topTank) {
            if (fluidTank.getFluid() != null && topTank.fluidTank.getFluid() != null) {
                if (fluidTank.getFluid().getFluid() != topTank.fluidTank.getFluid().getFluid()) {
                    return needed;
                }
            }
            needed += topTank.getCurrentNeeded();
        }
        return needed;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(tier.ordinal());
        data.add(isActive);
        data.add(valve);
        data.add(editMode.ordinal());
        if (valve > 0) {
            TileUtils.addFluidStack(data, valveFluid);
        }
        TileUtils.addTankData(data, fluidTank);
        return data;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active && updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
            clientActive = active;
        }
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!world.isRemote) {
            setActive(!getActive());
            world.playSound(null, getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIGURABLE_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return Capabilities.CONFIGURABLE_CAPABILITY.cast(this);
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return side != null && side != EnumFacing.DOWN && side != EnumFacing.UP;
        }
        return super.isCapabilityDisabled(capability, side);
    }


    @Override
    public int fill(EnumFacing from, @Nonnull FluidStack resource, boolean doFill) {
        if (tier == FluidTankTier.CREATIVE) {
            return resource.amount;
        }
        int filled = fluidTank.fill(resource, doFill);
        if (filled < resource.amount && !isActive) {
            filled += pushUp(PipeUtils.copy(resource, resource.amount - filled), doFill);
        }
        if (filled > 0 && from == EnumFacing.UP) {
            if (valve == 0) {
                needsPacket = true;
            }
            valve = 20;
            valveFluid = new FluidStack(resource, 1);
        }
        return filled;
    }

    @Override
    @Nullable
    public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
        return fluidTank.drain(maxDrain, tier != FluidTankTier.CREATIVE && doDrain);
    }

    @Override
    public boolean canFill(EnumFacing from, @Nonnull FluidStack fluid) {
        TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(EnumFacing.DOWN));
        if (from == EnumFacing.DOWN && isActive && !(tile instanceof TileEntityFluidTank)) {
            return false;
        }
        if (tier == FluidTankTier.CREATIVE) {
            return true;
        }
        if (isActive && tile instanceof TileEntityFluidTank tank) { // Only fill if tanks underneath have same fluid.
            return fluidTank.getFluid() == null ? tank.canFill(EnumFacing.UP, fluid) : fluidTank.getFluid().isFluidEqual(fluid);
        }
        return FluidContainerUtils.canFill(fluidTank.getFluid(), fluid);
    }

    @Override
    public boolean canDrain(EnumFacing from, @Nullable FluidStack fluid) {
        return fluidTank != null && FluidContainerUtils.canDrain(fluidTank.getFluid(), fluid) &&  (!isActive || from != EnumFacing.DOWN);
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        return new FluidTankInfo[]{fluidTank.getInfo()};
    }


    @Override
    public FluidTankInfo[] getAllTanks() {
        return new FluidTankInfo[]{fluidTank.getInfo()};
    }

    @Override
    public void setFluidStack(FluidStack fluidStack, Object... data) {
        fluidTank.setFluid(fluidStack);
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        return fluidTank.getFluid();
    }

    @Override
    public boolean hasTank(Object... data) {
        return true;
    }

    @Override
    public ContainerEditMode getContainerEditMode() {
        return editMode;
    }

    @Override
    public void setContainerEditMode(ContainerEditMode mode) {
        editMode = mode;
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{fluidTank};
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public BaseTier getTier() {
        return tier.getBaseTier();
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }
}
