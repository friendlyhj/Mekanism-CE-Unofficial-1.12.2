package mekanism.client.render.tileentity;

import mekanism.client.model.ModelAntiprotonicNucleosynthesizer;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.tile.TileEntityAntiprotonicNucleosynthesizer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderAntiprotonicNucleosynthesizer extends TileEntitySpecialRenderer<TileEntityAntiprotonicNucleosynthesizer> {

    private ModelAntiprotonicNucleosynthesizer model = new ModelAntiprotonicNucleosynthesizer();

    @Override
    public void render(TileEntityAntiprotonicNucleosynthesizer tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "AntiprotonicNucleosynthesizer.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F);
        GlStateManager.popMatrix();
        MekanismRenderer.machineRenderer().render(tileEntity, x, y, z, partialTick, destroyStage, alpha);
    }
}