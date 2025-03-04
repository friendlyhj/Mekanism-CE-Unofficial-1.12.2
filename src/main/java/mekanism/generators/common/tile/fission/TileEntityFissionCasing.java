package mekanism.generators.common.tile.fission;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.IHeatTransfer;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.*;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.fission.FissionCache;
import mekanism.generators.common.content.fission.FissionUpdateProtocol;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.Set;

public class TileEntityFissionCasing extends TileEntityMultiblock<SynchronizedFissionData> implements IHeatTransfer {

    protected static final int[] INV_SLOTS = {0, 1};

    public Set<ValveData> valveViewing = new ObjectOpenHashSet<>();

    public int clientWaterCapacity;
    public int clientSteamCapacity;

    public float prevWaterScale;

    public TileEntityFissionCasing() {
        super("FissionCasing");
    }

    public TileEntityFissionCasing(String name) {
        super(name);
        inventory = NonNullListSynchronized.withSize(INV_SLOTS.length, ItemStack.EMPTY);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            if (structure != null && clientHasStructure && isRendering) {
                float targetScale = (float) (structure.waterStored != null ? structure.waterStored.amount : 0) / clientWaterCapacity;
                if (Math.abs(prevWaterScale - targetScale) > 0.01) {
                    prevWaterScale = (9 * prevWaterScale + targetScale) / 10;
                }
            }
            if (structure != null && clientHasStructure && isRendering) {
                float targetScale = (float) (structure.InputGas != null ? structure.InputGas.amount : 0) / clientWaterCapacity;
                if (Math.abs(prevWaterScale - targetScale) > 0.01) {
                    prevWaterScale = (9 * prevWaterScale + targetScale) / 10;
                }
            }
            if (!clientHasStructure || !isRendering) {
                for (ValveData data : valveViewing) {
                    TileEntityFissionCasing tileEntity = (TileEntityFissionCasing) data.location.getTileEntity(world);
                    if (tileEntity != null){
                        tileEntity.clientHasStructure = false;
                    }
                }
                valveViewing.clear();
            }
        }

        if (!world.isRemote) {
            if (structure != null) {
                if (structure.waterStored != null && structure.waterStored.amount <= 0) {
                    structure.waterStored = null;
                    markNoUpdateSync();
                }
                if (structure.steamStored != null && structure.steamStored.amount <= 0) {
                    structure.steamStored = null;
                    markNoUpdateSync();
                }
                if (structure.InputGas != null && structure.InputGas.amount <= 0) {
                    structure.InputGas = null;
                    markNoUpdateSync();
                }
                if (structure.OutputGas != null && structure.OutputGas.amount <= 0) {
                    structure.OutputGas = null;
                    markNoUpdateSync();
                }

                if (isRendering) {
                    boolean needsValveUpdate = false;
                    for (ValveData data : structure.valves) {
                        if (data.activeTicks > 0) {
                            data.activeTicks--;
                        }
                        if (data.activeTicks > 0 != data.prevActive) {
                            needsValveUpdate = true;
                        }
                        data.prevActive = data.activeTicks > 0;
                    }

                    boolean needsHotUpdate = false;
                    boolean newHot = structure.temperature >= SynchronizedFissionData.BASE_BOIL_TEMP - 0.01F;
                    if (newHot != structure.clientHot) {
                        needsHotUpdate = true;
                        structure.clientHot = newHot;
                    }

                    double[] d = structure.simulateHeat();
                    structure.applyTemperatureChange();
                    structure.lastEnvironmentLoss = d[1];
                    if (structure.InputGas != null) {
                        int OutputAmount = structure.OutputGas != null ? structure.OutputGas.amount : 0;
                        double heatAvailable = structure.getTemp();
                        structure.lastMaxBoil = (int) Math.floor(heatAvailable / SynchronizedFissionData.getHeatEnthalpy());
                        int amountToBoil = Math.min(structure.lastMaxBoil, structure.InputGas.amount);
                        amountToBoil = Math.min(amountToBoil, (structure.steamVolume * FissionUpdateProtocol.STEAM_PER_TANK) - OutputAmount);
                        structure.InputGas.amount -= amountToBoil;
                        if (structure.OutputGas == null) {
                            structure.OutputGas = new GasStack(MekanismFluids.Sodium, amountToBoil);
                        } else {
                            structure.OutputGas.amount += amountToBoil;
                        }
                        if (structure.OutputGas.amount != structure.steamVolume * FissionUpdateProtocol.STEAM_PER_TANK) {
                            structure.temperature += (amountToBoil * SynchronizedFissionData.getHeatEnthalpy()) / structure.locations.size();
                            structure.lastBoilRate = amountToBoil;
                        }
                    }

                    if (structure.temperature >= SynchronizedFissionData.BASE_BOIL_TEMP && structure.waterStored != null) {
                        int steamAmount = structure.steamStored != null ? structure.steamStored.amount : 0;
                        double heatAvailable = structure.getHeatAvailable();
                        structure.lastMaxBoil = (int) Math.floor(heatAvailable / SynchronizedFissionData.getHeatEnthalpy());
                        int amountToBoil = Math.min(structure.lastMaxBoil, structure.waterStored.amount);
                        amountToBoil = Math.min(amountToBoil, (structure.steamVolume * FissionUpdateProtocol.STEAM_PER_TANK) - steamAmount);
                        structure.waterStored.amount -= amountToBoil;
                        if (structure.steamStored == null) {
                            structure.steamStored = new FluidStack(FluidRegistry.getFluid("steam"), amountToBoil);
                        } else {
                            structure.steamStored.amount += amountToBoil;
                        }

                        structure.temperature -= (amountToBoil * SynchronizedFissionData.getHeatEnthalpy()) / structure.locations.size();
                        structure.lastBoilRate = amountToBoil;
                    } else {
                        structure.lastBoilRate = 0;
                        structure.lastMaxBoil = 0;
                    }


                    if (needsValveUpdate || structure.needsRenderUpdate() || needsHotUpdate) {
                        sendPacketToRenderer();
                    }
                    structure.prevWater = structure.waterStored != null ? structure.waterStored.copy() : null;
                    structure.prevSteam = structure.steamStored != null ? structure.steamStored.copy() : null;
                    structure.prevInputGas = structure.InputGas != null ? structure.InputGas.copy() : null;
                    structure.prevOutputGas = structure.OutputGas != null ? structure.OutputGas.copy() : null;
                    MekanismUtils.saveChunk(this);
                }
            }
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(MekanismGenerators.instance,-1, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    protected SynchronizedFissionData getNewStructure() {
        return new SynchronizedFissionData();
    }


    @Override
    public FissionCache getNewCache() {
        return new FissionCache();
    }

    @Override
    protected FissionUpdateProtocol getProtocol() {
        return new FissionUpdateProtocol(this);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        if (structure != null) {
            data.add(structure.waterVolume * FissionUpdateProtocol.WATER_PER_TANK);
            data.add(structure.steamVolume * FissionUpdateProtocol.STEAM_PER_TANK);
            data.add(structure.lastEnvironmentLoss);
            data.add(structure.lastBoilRate);
            data.add(structure.superheatingElements);
            data.add(structure.temperature);
            data.add(structure.lastMaxBoil);

            TileUtils.addFluidStack(data, structure.waterStored);
            TileUtils.addFluidStack(data, structure.steamStored);
            TileUtils.addGasStack(data, structure.InputGas);
            TileUtils.addGasStack(data, structure.OutputGas);
            structure.upperRenderLocation.write(data);

            if (isRendering) {
                data.add(structure.clientHot);
                Set<ValveData> toSend = new ObjectOpenHashSet<>();
                for (ValveData valveData : structure.valves) {
                    if (valveData.activeTicks > 0) {
                        toSend.add(valveData);
                    }
                }
                data.add(toSend.size());
                for (ValveData valveData : toSend) {
                    valveData.location.write(data);
                    data.add(valveData.side.ordinal());
                }
            }
        }
        return data;
    }


    @Override
    public MultiblockManager<SynchronizedFissionData> getManager() {
        return MekanismGenerators.fissionMangaer;
    }

    public double getLastEnvironmentLoss() {
        return structure != null ? structure.lastEnvironmentLoss : 0;
    }

    public double getTemperature() {
        return structure != null ? structure.temperature : 0;
    }

    public int getLastBoilRate() {
        return structure != null ? structure.lastBoilRate : 0;
    }

    public int getLastMaxBoil() {
        return structure != null ? structure.lastMaxBoil : 0;
    }

    public int getSuperheatingElements() {
        return structure != null ? structure.superheatingElements : 0;
    }



    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (clientHasStructure) {
                clientWaterCapacity = dataStream.readInt();
                clientSteamCapacity = dataStream.readInt();
                structure.lastEnvironmentLoss = dataStream.readDouble();
                structure.lastBoilRate = dataStream.readInt();
                structure.superheatingElements = dataStream.readInt();
                structure.temperature = dataStream.readDouble();
                structure.lastMaxBoil = dataStream.readInt();

                structure.waterStored = TileUtils.readFluidStack(dataStream);
                structure.steamStored = TileUtils.readFluidStack(dataStream);
                structure.InputGas = TileUtils.readGasStack(dataStream);
                structure.OutputGas = TileUtils.readGasStack(dataStream);
                structure.upperRenderLocation = Coord4D.read(dataStream);

                if (isRendering) {
                    structure.clientHot = dataStream.readBoolean();
                    SynchronizedFissionData.clientHotMap.put(structure.inventoryID, structure.clientHot);
                    int size = dataStream.readInt();
                    valveViewing.clear();
                    for (int i = 0; i < size; i++) {
                        ValveData data = new ValveData();
                        data.location = Coord4D.read(dataStream);
                        data.side = EnumFacing.byIndex(dataStream.readInt());

                        valveViewing.add(data);

                        TileEntityFissionCasing tileEntity = (TileEntityFissionCasing) data.location.getTileEntity(world);
                        if (tileEntity != null) {
                            tileEntity.clientHasStructure = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    public double getTemp() {
        return 0;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return SynchronizedFissionData.CASING_INVERSE_CONDUCTION_COEFFICIENT;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return SynchronizedFissionData.CASING_INSULATION_COEFFICIENT;
    }

    @Override
    public void transferHeatTo(double heat) {
        if (structure != null) {
            structure.heatToAbsorb += heat;
        }
    }

    @Override
    public double[] simulateHeat() {
        return new double[]{0, 0};
    }

    @Override
    public double applyTemperatureChange() {
        return 0;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return structure != null;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        return null;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.HEAT_TRANSFER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            return Capabilities.HEAT_TRANSFER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.fissionReactor");
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
}
