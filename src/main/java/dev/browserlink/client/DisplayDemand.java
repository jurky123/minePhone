package dev.browserlink.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Conservative visibility hints, with a grace period to avoid toggling while turning. */
final class DisplayDemand {
    private static long smallSeen,largeSeen,lastSent;
    static String mode="Waiting";
    static void phone(){smallSeen=System.currentTimeMillis();}
    static void block(BlockPos pos,boolean large){
        var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera();
        Vec3 delta=Vec3.atCenterOf(pos).subtract(camera.getPosition());
        double distance=delta.length();var look=camera.getLookVector();
        if(distance>48||distance>3&&delta.normalize().dot(new Vec3(look.x(),look.y(),look.z()))<.25)return;
        if(large&&distance<12)largeSeen=System.currentTimeMillis();else smallSeen=System.currentTimeMillis();
    }
    static void tick(){
        long now=System.currentTimeMillis();if(now-lastSent<1000)return;lastSent=now;
        var mc=Minecraft.getInstance();boolean active=mc.level!=null&&!mc.isPaused();
        boolean focus=mc.screen instanceof BrowserScreen;
        boolean visible=active&&(focus||now-smallSeen<2500||now-largeSeen<2500);
        boolean adaptive=DisplaySettings.INSTANCE.adaptive;
        int maxHeight=focus?1440:now-largeSeen<2500?1080:720;
        mode=!active||adaptive&&!visible?"Paused":!adaptive?"Manual":focus?"Focus":maxHeight==1080?"TV":"Small screen";
        JsonObject m=new JsonObject();m.addProperty("type","stream-policy");m.addProperty("enabled",active&&(!adaptive||visible));
        m.addProperty("maxHeight",adaptive?maxHeight:1440);m.addProperty("maxFps",30);
        ClientRuntime.send(m);
    }
}
