package dev.browserlink.client;

import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;

/** Local preferences only; no browser content or text drafts are persisted. */
final class DisplaySettings {
    static final DisplaySettings INSTANCE=load();
    boolean adaptive=true,landscape=true,metrics=false;
    int browserGameFps=0;
    float handScale=1,handX=0,handY=0,focusScale=1;
    private static DisplaySettings load(){
        try{var v=new GsonBuilder().create().fromJson(Files.readString(path()),DisplaySettings.class);if(v!=null){v.clamp();return v;}}catch(Exception ignored){}
        return new DisplaySettings();
    }
    private static java.nio.file.Path path(){return FMLPaths.CONFIGDIR.get().resolve("browserlink-display.json");}
    private static float bound(float v,float low,float high,float fallback){return Float.isFinite(v)?Math.max(low,Math.min(high,v)):fallback;}
    void clamp(){handScale=bound(handScale,.7f,1.15f,1);handX=bound(handX,-.3f,.3f,0);handY=bound(handY,-.3f,.3f,0);focusScale=bound(focusScale,.7f,1,1);if(browserGameFps!=30&&browserGameFps!=40&&browserGameFps!=60)browserGameFps=0;}
    void save(){clamp();try{Files.createDirectories(path().getParent());Files.writeString(path(),new GsonBuilder().setPrettyPrinting().create().toJson(this));}catch(Exception e){ClientRuntime.status="Could not save display preferences";}}
}
