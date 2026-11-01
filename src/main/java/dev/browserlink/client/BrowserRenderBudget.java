package dev.browserlink.client;

import net.minecraft.client.Minecraft;

/** Temporary GPU headroom while a connected browser screen is visible. */
final class BrowserRenderBudget {
    private static final FrameLimitBudget state=new FrameLimitBudget();
    static void tick(){
        var mc=Minecraft.getInstance();var window=mc.getWindow();
        int current=window.getFramerateLimit(),budget=DisplaySettings.INSTANCE.browserGameFps;
        int target=state.update(current,budget,ClientRuntime.connected()&&DisplayDemand.wantsFrames);
        if(current!=target)window.setFramerateLimit(target);
    }
}
