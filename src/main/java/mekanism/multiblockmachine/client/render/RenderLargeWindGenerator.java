package mekanism.multiblockmachine.client.render;

import mekanism.client.render.MekanismRenderer;
import mekanism.multiblockmachine.client.model.ModelLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import mekanism.multiblockmachine.common.util.MekanismMultiblockMachineUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public class RenderLargeWindGenerator extends TileEntitySpecialRenderer<TileEntityLargeWindGenerator> {

    private ModelLargeWindGenerator model = new ModelLargeWindGenerator();

    @Override
    public void render(TileEntityLargeWindGenerator tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismMultiblockMachineUtils.getResource(MekanismMultiblockMachineUtils.ResourceType.RENDER, "LargeWindGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        double angle = tileEntity.getAngle();
        if (tileEntity.getActive()) {
            angle = (tileEntity.getAngle() + ((tileEntity.getPos().getY() + 4F) / TileEntityLargeWindGenerator.SPEED_SCALED) * partialTick) % 360;
        }
        MekanismRenderer.GlowInfo glowInfo = MekanismRenderer.enableGlow();
        model.render(0.0625F, angle);
        MekanismRenderer.disableGlow(glowInfo);
        GlStateManager.popMatrix();
    }
}
