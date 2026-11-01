package dev.browserlink.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.browserlink.BrowserLink;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Raise the actual phone model into a stable close-up, with the world still visible. */
public final class PhoneFocusScreen extends BrowserScreen {

    private final InteractionHand hand;
    private final Level sourceLevel;
    private final CameraType previousType;
    private final ItemStack displayStack;
    private PhoneViewLayout.View view;
    public PhoneFocusScreen(InteractionHand hand) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        this.hand=hand;sourceLevel=mc.level;previousType=mc.options.getCameraType();
        displayStack=mc.player.getItemInHand(hand).copy();
    }
    @Override protected void init() {
        super.init();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
    }
    @Override protected boolean worldView(){return true;}
    @Override protected int textStart(){return 244;}
    @Override protected void extraWidgets(int uiWidth,int uiHeight) {
        addRenderableWidget(Button.builder(Component.translatable("screen.browserlink.rotate"),b->{DisplaySettings.INSTANCE.landscape=!DisplaySettings.INSTANCE.landscape;DisplaySettings.INSTANCE.save();layout();})
            .bounds(184,uiHeight-20,54,18).build());
    }
    @Override protected void layout() {
        boolean live=ClientRuntime.TEXTURE.location()!=null;
        view=PhoneViewLayout.calculate(width,height,topMargin(),bottomMargin(),DisplaySettings.INSTANCE.landscape,
            live?ClientRuntime.TEXTURE.width():128,live?ClientRuntime.TEXTURE.height():256,DisplaySettings.INSTANCE.focusScale);
        sx=view.imageX();sy=view.imageY();sw=view.imageWidth();sh=view.imageHeight();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        layout();g.flush();
        var pose=g.pose();pose.pushPose();
        pose.translate(view.centerX(),view.centerY(),200);
        pose.scale(view.scale(),-view.scale(),view.scale());
        if(DisplaySettings.INSTANCE.landscape)pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.translate(-.5,-.5,-.5);
        Lighting.setupForFlatItems();
        PhoneRenderer.renderBody(displayStack,pose,g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        g.flush();pose.popPose();Lighting.setupFor3DItems();
        pose.pushPose();pose.translate(0,0,500);
        g.fill(view.left(),view.top(),view.right(),view.bottom(),0xff000000);
        var image=ClientRuntime.TEXTURE.location();
        int iw=image==null?128:ClientRuntime.TEXTURE.width(),ih=image==null?256:ClientRuntime.TEXTURE.height();
        g.blit(image==null?PhoneRenderer.IDLE:image,sx,sy,sw,sh,0,0,iw,ih,iw,ih);
        g.flush();pose.popPose();
        super.render(g,mx,my,partial);
    }
    @Override public void tick() {
        super.tick();
        if(minecraft.level!=sourceLevel||minecraft.player==null||!minecraft.player.isAlive()
            ||!minecraft.player.getItemInHand(hand).is(BrowserLink.PHONE.get()))onClose();
    }
    @Override public void removed() {
        super.removed();minecraft.options.setCameraType(previousType);
    }
}
