package dev.browserlink.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.browserlink.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
public final class TvRenderer implements BlockEntityRenderer<TvEntity> {
    private static final ResourceLocation IDLE=new ResourceLocation("browserlink","textures/block/tv_screen.png");
    public TvRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TvEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        DisplayDemand.block(e.getBlockPos(),true);
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(-e.getBlockState().getValue(TvBlock.FACING).toYRot()));pose.translate(-.5,-.5,-.5);
        float[] s=TvGeometry.SCREEN;ScreenQuad.draw(pose,buffers,s[0],s[1],s[2],s[3],s[4]+.0003f,IDLE);pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(TvEntity entity){return true;}
}
