package dev.browserlink.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

final class ScreenQuad {
    static final ResourceLocation IDLE = new ResourceLocation("browserlink", "textures/screen_idle.png");
    static void draw(PoseStack pose, MultiBufferSource buffers, float left, float bottom, float right, float top, float z) {
        draw(pose,buffers,left,bottom,right,top,z,IDLE);
    }
    static void draw(PoseStack pose, MultiBufferSource buffers, float left, float bottom, float right, float top, float z, ResourceLocation idle) {
        BrowserTexture stream = ClientRuntime.TEXTURE;
        ResourceLocation image = stream.location();
        quad(pose, buffers, idle, left, bottom, right, top, z, 1, 1, 1);
        if (image == null) return;
        float w = right - left, h = top - bottom;
        float scale = Math.min(w / stream.width(), h / stream.height());
        float actualW = stream.width() * scale, actualH = stream.height() * scale;
        float x = left + (w - actualW) / 2, y = bottom + (h - actualH) / 2;
        // Black letterbox; no stretching of desktop websites into a portrait phone.
        quad(pose, buffers, new ResourceLocation("browserlink", "textures/white.png"), left, bottom, right, top, z + .0003f, 0, 0, 0);
        quad(pose, buffers, image, x, y, x + actualW, y + actualH, z + .0006f, 1, 1, 1);
    }
    static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation image,
                     float x1, float y1, float x2, float y2, float z, float r, float g, float b) {
        VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(image));
        var p = pose.last();
        v.vertex(p.pose(), x1, y1, z).color(r,g,b,1).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex();
        v.vertex(p.pose(), x2, y1, z).color(r,g,b,1).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex();
        v.vertex(p.pose(), x2, y2, z).color(r,g,b,1).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex();
        v.vertex(p.pose(), x1, y2, z).color(r,g,b,1).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex();
    }
}
