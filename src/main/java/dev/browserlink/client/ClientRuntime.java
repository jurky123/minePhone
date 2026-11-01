package dev.browserlink.client;

import com.google.gson.*;
import dev.browserlink.BrowserLink;
import dev.browserlink.bridge.BridgeServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientRuntime {
    public record Tab(int id,String title) {}
    public record Tabs(java.util.List<Tab> entries,int activeId,String url) {}
    public static volatile Tabs tabs = new Tabs(java.util.List.of(),-1,"");
    public static final BrowserTexture TEXTURE = new BrowserTexture();
    public static volatile BridgeServer bridge;
    public static volatile String status = "Starting local bridge...";
    public static final KeyMapping OPEN = new KeyMapping("key.browserlink.open", GLFW.GLFW_KEY_F8, "key.categories.browserlink");
    public static final KeyMapping ROTATE = new KeyMapping("key.browserlink.rotate", GLFW.GLFW_KEY_F7, "key.categories.browserlink");
    public static final KeyMapping METRICS = new KeyMapping("key.browserlink.metrics", GLFW.GLFW_KEY_F9, "key.categories.browserlink");
    public static final KeyMapping FULLSCREEN = new KeyMapping("key.browserlink.fullscreen", GLFW.GLFW_KEY_F6, "key.categories.browserlink");
    private ClientRuntime() {}
    public static void openScreen() { Minecraft.getInstance().setScreen(new BrowserScreen()); }
    public static void openHeldPhoneOrBrowser() {
        var player=Minecraft.getInstance().player;
        if(player==null)return;
        if(player.getMainHandItem().is(BrowserLink.PHONE.get()))openPhone(net.minecraft.world.InteractionHand.MAIN_HAND);
        else if(player.getOffhandItem().is(BrowserLink.PHONE.get()))openPhone(net.minecraft.world.InteractionHand.OFF_HAND);
        else openScreen();
    }
    public static void openPhone(net.minecraft.world.InteractionHand hand) { BrowserAudio.selectPhone(hand);Minecraft.getInstance().setScreen(new PhoneFocusScreen(hand)); }
    public static void openComputer(net.minecraft.core.BlockPos pos,net.minecraft.core.Direction facing) {
        BrowserAudio.selectBlock(pos);
        Minecraft.getInstance().setScreen(new ComputerFocusScreen(pos,facing));
    }
    public static void openTv(net.minecraft.core.BlockPos pos,net.minecraft.core.Direction facing){
        BrowserAudio.selectBlock(pos);
        Minecraft.getInstance().setScreen(new ComputerFocusScreen(pos,facing,dev.browserlink.TvGeometry.SCREEN,BrowserLink.TV.get()));
    }
    public static boolean connected() { return bridge != null && bridge.isConnected(); }
    public static void send(JsonObject message) { if (bridge != null) bridge.send(message); }
    public static void disconnect() { if (bridge != null) bridge.disconnect(); }
    private static void start() {
        Thread thread = new Thread(() -> {
            try {
                bridge = new BridgeServer(new BridgeServer.Listener() {
                    public void connected() { TEXTURE.reset(); tabs=new Tabs(java.util.List.of(),-1,""); status = "Connected - waiting for frames"; }
                    public void disconnected() { TEXTURE.reset(); BrowserAudio.reset();tabs=new Tabs(java.util.List.of(),-1,""); status = "Connection lost - waiting for extension to reconnect. Check Chrome if it persists."; }
                    public void audio(int tabId,byte[] pcm){BrowserAudio.receive(tabId,pcm);}
                    public void frame(byte[] png) { TEXTURE.receive(png); if (status.equals("Connected - waiting for frames")) status="Connected - Chrome tab is live"; }
                    public void clipboard(String text) { Minecraft.getInstance().execute(()->Minecraft.getInstance().keyboardHandler.setClipboard(text)); }
                    public void status(String text) { status = text; }
                    public void browserState(JsonObject state) {
                        var entries=new java.util.ArrayList<Tab>();
                        for(var element:state.getAsJsonArray("tabs")) {
                            if(entries.size()>=64) break;
                            var tab=element.getAsJsonObject(); String title=tab.get("title").getAsString();
                            entries.add(new Tab(tab.get("id").getAsInt(),title.substring(0,Math.min(80,title.length()))));
                        }
                        int active=state.get("activeId").getAsInt(); String url=state.get("url").getAsString();
                        if (active!=tabs.activeId()) {TEXTURE.reset();BrowserAudio.clearFrames();}
                        tabs=new Tabs(java.util.List.copyOf(entries),active,url.substring(0,Math.min(2048,url.length())));
                    }
                });
                bridge.setWorldActive(false);
                bridge.start(BridgeServer.DEFAULT_PORT);
                status = "Ready. Select a Chrome tab and click Connect. No pairing code.";
                Runtime.getRuntime().addShutdownHook(new Thread(() -> bridge.close(), "browserlink-shutdown"));
            } catch (Exception error) {
                status = "Bridge unavailable: " + error.getClass().getSimpleName() + ". Check port " + BridgeServer.DEFAULT_PORT;
                if (bridge != null) bridge.close();
            }
        }, "browserlink-start");
        thread.setDaemon(true); thread.start();
    }
    @Mod.EventBusSubscriber(modid = BrowserLink.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(ClientRuntime::start); }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(BrowserLink.COMPUTER_ENTITY.get(), ComputerRenderer::new);
            event.registerBlockEntityRenderer(BrowserLink.TV_ENTITY.get(),TvRenderer::new);
            event.registerEntityRenderer(BrowserLink.SEAT.get(),SeatRenderer::new);
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(ROTATE); event.register(METRICS); event.register(FULLSCREEN); }
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(new net.minecraft.client.resources.model.ModelResourceLocation("browserlink", "phone_body", "inventory")); }
    }
    @Mod.EventBusSubscriber(modid = BrowserLink.ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent public static void usePhone(InputEvent.InteractionKeyMappingTriggered event) {
            var mc=Minecraft.getInstance();
            if(!event.isUseItem()||mc.player==null||mc.level==null)return;
            // Forge fires use input for each hand. Cancel both so the main-hand item
            // cannot place, eat or activate after opening the offhand phone.
            if(mc.screen instanceof PhoneFocusScreen){event.setCanceled(true);event.setSwingHand(false);return;}
            if(mc.screen==null&&mc.player.isShiftKeyDown()&&mc.player.getOffhandItem().is(BrowserLink.PHONE.get())){
                event.setCanceled(true);event.setSwingHand(false);
                openPhone(net.minecraft.world.InteractionHand.OFF_HAND);
            }
        }
        @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
            if (Minecraft.getInstance().screen instanceof ComputerFocusScreen) event.setFOV(ComputerFocusScreen.FOV);
        }
        @SubscribeEvent public static void hand(RenderHandEvent event) {
            if (Minecraft.getInstance().screen instanceof ComputerFocusScreen || Minecraft.getInstance().screen instanceof PhoneFocusScreen) event.setCanceled(true);
        }
        @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
            if (Minecraft.getInstance().screen instanceof ComputerFocusScreen || Minecraft.getInstance().screen instanceof PhoneFocusScreen)
                event.setCanceled(true);
        }
        @SubscribeEvent public static void diagnostics(RenderGuiEvent.Post event){
            var mc=Minecraft.getInstance();
            if(!DisplaySettings.INSTANCE.metrics||mc.screen!=null)return;
            var g=event.getGuiGraphics();float scale=(float)(1.5/mc.getWindow().getGuiScale());
            g.pose().pushPose();g.pose().scale(scale,scale,1);
            int w=(int)(mc.getWindow().getGuiScaledWidth()/scale);
            g.fill(4,4,Math.min(w-4,760),34,0xdd10151f);
            g.drawString(mc.font,mc.font.plainSubstrByWidth(TEXTURE.stats(),w-20),8,8,0xffffff);
            g.drawString(mc.font,"Mode: "+DisplayDemand.mode+" | F9 hide",8,21,0x70d9b4);
            g.pose().popPose();
        }
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (OPEN.consumeClick()) openHeldPhoneOrBrowser();
            while(FULLSCREEN.consumeClick())BrowserFullscreen.toggle();
            BrowserFullscreen.tick();
            if (bridge!=null) bridge.setWorldActive(Minecraft.getInstance().level!=null);
            var mc=Minecraft.getInstance();
            while(ROTATE.consumeClick())if(mc.player!=null&&(mc.player.getMainHandItem().is(BrowserLink.PHONE.get())||mc.player.getOffhandItem().is(BrowserLink.PHONE.get()))){DisplaySettings.INSTANCE.landscape=!DisplaySettings.INSTANCE.landscape;DisplaySettings.INSTANCE.save();}
            while(METRICS.consumeClick()){DisplaySettings.INSTANCE.metrics=!DisplaySettings.INSTANCE.metrics;DisplaySettings.INSTANCE.save();}
            DisplayDemand.tick();
            BrowserAudio.tick();
            if(Boolean.getBoolean("browserlink.benchmark"))TEXTURE.stats();
        }
        @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) { if(event.phase==TickEvent.Phase.START)TEXTURE.upload(); }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BrowserAudio.reset();if (bridge!=null) bridge.setWorldActive(false); }
    }
}
