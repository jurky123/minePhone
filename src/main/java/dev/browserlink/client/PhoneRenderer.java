package dev.browserlink.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.*;
import dev.browserlink.PhoneGeometry;
import net.minecraft.resources.ResourceLocation;
public final class PhoneRenderer extends BlockEntityWithoutLevelRenderer {
    private static PhoneRenderer instance;
    private static final ModelResourceLocation BODY = new ModelResourceLocation("browserlink", "phone_body", "inventory");
    public static final ResourceLocation IDLE = new ResourceLocation("browserlink", "textures/phone_idle.png");
    private PhoneRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    public static PhoneRenderer instance() { if (instance == null) instance = new PhoneRenderer(); return instance; }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean held=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        boolean first=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        var settings=DisplaySettings.INSTANCE;
        if(context!=ItemDisplayContext.GUI)DisplayDemand.phone();
        boolean horizontal=held&&settings.landscape;
        pose.pushPose();
        if(first){float size=settings.handScale*(horizontal?1:.72f);float side=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;pose.translate(.5+side*settings.handX,.5+settings.handY+(horizontal?0:.14),0);pose.scale(size,size,size);pose.translate(-.5,-.5,0);}
        pose.pushPose();
        if(horizontal){pose.translate(.5,.5,0);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));pose.translate(-.5,-.5,0);}
        renderBody(stack,pose,buffers,light,overlay);
        pose.popPose();
        float[] s=PhoneGeometry.SCREEN;
        if(horizontal)ScreenQuad.draw(pose,buffers,1-s[3],s[0],1-s[1],s[2],s[4]+.0001f,IDLE);
        else ScreenQuad.draw(pose,buffers,s[0],s[1],s[2],s[3],s[4]+.0001f,IDLE);
        pose.popPose();
    }
    static void renderBody(ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(BODY),stack,light,overlay,pose,buffers.getBuffer(RenderType.cutout()));
    }
}
