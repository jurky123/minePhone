package dev.browserlink.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.browserlink.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
public final class ComputerRenderer implements BlockEntityRenderer<ComputerEntity> {
    public ComputerRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ComputerEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        DisplayDemand.block(entity.getBlockPos(),false);
        pose.pushPose();
        pose.translate(.5,.5,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getBlockState().getValue(ComputerBlock.FACING).toYRot()));
        pose.translate(-.5,-.5,-.5);
        // Keep the authored landscape visible while no browser is connected.
        if (ClientRuntime.TEXTURE.location() != null) {
            float[] s = ComputerGeometry.SCREEN;
            ScreenQuad.draw(pose, buffers, s[0], s[1], s[2], s[3], s[4]);
        }
        pose.popPose();
    }
}
