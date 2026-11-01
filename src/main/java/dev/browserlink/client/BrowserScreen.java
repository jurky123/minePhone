package dev.browserlink.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;

public class BrowserScreen extends Screen {
    protected int sx,sy,sw,sh;
    private boolean webFocus;
    private final Set<Integer> pressed=new HashSet<>();
    private EditBox text,address;
    private static String inputDraft="";
    protected float uiScale;
    private int uiWidth,uiHeight,tabOffset,lastActive=-1;
    private String lastUrl="";
    private double pointerX,pointerY;
    public BrowserScreen(){super(Component.translatable("screen.browserlink.title"));}
    protected int topMargin(){return (int)Math.ceil(48*uiScale);}
    protected int bottomMargin(){return (int)Math.ceil(22*uiScale);}
    protected int textStart(){return 184;}
    protected void extraWidgets(int uiWidth,int uiHeight) {}
    @Override protected void init(){
        // Keep browser chrome near 14 px text, independent of Minecraft's large GUI scale.
        uiScale=(float)(1.5/minecraft.getWindow().getGuiScale());
        uiWidth=(int)(width/uiScale);uiHeight=(int)(height/uiScale);
        button("<",6,25,22,()->command("back"));button(">",31,25,22,()->command("forward"));
        button("R",56,25,22,()->command("reload"));
        address=new EditBox(font,84,25,Math.max(50,uiWidth-266),18,Component.translatable("screen.browserlink.address"));
        address.setMaxLength(2048);address.setHint(Component.translatable("screen.browserlink.address"));
        address.setValue(ClientRuntime.tabs.url());lastUrl=ClientRuntime.tabs.url();addRenderableWidget(address);
        button("Go",uiWidth-176,25,28,()->navigate(false));button("+",uiWidth-144,25,22,this::newTab);
        button(Component.translatable("screen.browserlink.disconnect"),uiWidth-118,25,64,ClientRuntime::disconnect);
        button("Esc",uiWidth-50,25,44,this::onClose);
        text=new EditBox(font,textStart(),uiHeight-20,Math.max(40,uiWidth-textStart()-50),17,Component.translatable("screen.browserlink.text"));
        text.setHint(Component.translatable("screen.browserlink.text"));text.setMaxLength(4096);text.setValue(inputDraft);text.setResponder(value->inputDraft=value);addRenderableWidget(text);
        text.setTooltip(Tooltip.create(Component.literal("Ctrl+I：中文输入；Enter：发送并返回网页；Ctrl+Enter：发送并提交；Esc：保留草稿返回网页；Ctrl+,：显示设置")));
        button(Component.translatable("screen.browserlink.send"),uiWidth-46,uiHeight-20,40,this::submitText);
        button("设置",142,uiHeight-20,36,()->minecraft.setScreen(new DisplaySettingsScreen(this)));
        extraWidgets(uiWidth,uiHeight);
        webFocus=false;
        layout();
        JsonObject request=new JsonObject();request.addProperty("type","tabs-request");ClientRuntime.send(request);
    }
    private void button(String label,int x,int y,int w,Runnable action){button(Component.literal(label),x,y,w,action);}
    private void button(Component label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,w,18).build());}
    private void navigate(boolean newTab){
        JsonObject msg=new JsonObject();msg.addProperty("type","navigate");msg.addProperty("url",address.getValue());msg.addProperty("newTab",newTab);ClientRuntime.send(msg);
        focusWeb();
    }
    private void focusWeb(){webFocus=true;setFocused(null);address.setFocused(false);text.setFocused(false);}
    private void focusAddress(){webFocus=false;setFocused(address);address.setFocused(true);address.setCursorPosition(address.getValue().length());address.setHighlightPos(0);}
    private void newTab(){JsonObject msg=new JsonObject();msg.addProperty("type","navigate");msg.addProperty("url","");msg.addProperty("newTab",true);ClientRuntime.send(msg);address.setValue("");focusAddress();}
    private void tabAction(String action,int id){JsonObject msg=new JsonObject();msg.addProperty("type",action);msg.addProperty("tabId",id);ClientRuntime.send(msg);}
    private void cycleTab(int direction){var state=ClientRuntime.tabs;for(int i=0;i<state.entries().size();i++)if(state.entries().get(i).id()==state.activeId()){tabAction("switch-tab",state.entries().get(Math.floorMod(i+direction,state.entries().size())).id());break;}}
    @Override public void tick(){
        var state=ClientRuntime.tabs;
        if(state.activeId()!=lastActive){lastActive=state.activeId();for(int i=0;i<state.entries().size();i++)if(state.entries().get(i).id()==lastActive)tabOffset=i/visibleTabs()*visibleTabs();}
        if(!address.isFocused()&&!state.url().equals(lastUrl)){address.setValue(state.url());lastUrl=state.url();}
        tabOffset=Math.max(0,Math.min(tabOffset,Math.max(0,state.entries().size()-visibleTabs())));
    }
    protected boolean worldView(){return false;}
    protected void layout(){
        int availableW=Math.max(1,width-8),availableH=Math.max(1,height-topMargin()-bottomMargin()-4);
        float scale=Math.min((float)availableW/ClientRuntime.TEXTURE.width(),(float)availableH/ClientRuntime.TEXTURE.height());
        sw=Math.max(1,(int)(ClientRuntime.TEXTURE.width()*scale));sh=Math.max(1,(int)(ClientRuntime.TEXTURE.height()*scale));
        sx=(width-sw)/2;sy=topMargin()+2+(availableH-sh)/2;
    }
    private int visibleTabs(){return Math.max(1,(uiWidth-48)/150);}
    private int tabWidth(){return Math.max(1,(uiWidth-48)/visibleTabs());}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        layout();
        if(!worldView()){
            g.fill(0,0,width,height,0xff10151f);g.fill(sx,sy,sx+sw,sy+sh,0xff070b12);
            var image=ClientRuntime.TEXTURE.location();
            if(image!=null)g.blit(image,sx,sy,sw,sh,0,0,ClientRuntime.TEXTURE.width(),ClientRuntime.TEXTURE.height(),ClientRuntime.TEXTURE.width(),ClientRuntime.TEXTURE.height());
        }
        g.pose().pushPose();g.pose().translate(0,0,1000);g.pose().scale(uiScale,uiScale,1);
        g.fill(0,0,uiWidth,48,0xf21b2533);g.fill(0,uiHeight-22,uiWidth,uiHeight,0xf21b2533);
        g.drawString(font,"<",7,8,0xc3d3e6);g.drawString(font,">",uiWidth-14,8,0xc3d3e6);
        var state=ClientRuntime.tabs;
        if(state.entries().isEmpty())g.drawString(font,Component.translatable("screen.browserlink.waiting"),28,8,0xb6c7dc);
        for(int n=0;n<visibleTabs()&&tabOffset+n<state.entries().size();n++){
            var tab=state.entries().get(tabOffset+n);int x=24+n*tabWidth();
            boolean active=tab.id()==state.activeId();g.fill(x,2,x+tabWidth()-3,22,active?0xff354963:0xff253245);
            if(active)g.fill(x,20,x+tabWidth()-3,22,0xff70d9b4);
            g.drawString(font,font.plainSubstrByWidth(tab.title(),tabWidth()-34),x+6,8,active?0xffffff:0xb9c6d5);
            boolean closeHover=mx/uiScale>=x+tabWidth()-23&&mx/uiScale<x+tabWidth()-3&&my/uiScale>=2&&my/uiScale<22;
            if(closeHover)g.fill(x+tabWidth()-23,3,x+tabWidth()-4,21,0xff83505c);
            g.drawString(font,"×",x+tabWidth()-17,8,0xffffff);
        }
        String hint=text.isFocused()?"输入文字 · Enter 发送 / Esc 返回":address.isFocused()?"输入网址 · Enter 打开":ClientRuntime.status;
        g.drawString(font,font.plainSubstrByWidth(hint,130),6,uiHeight-14,ClientRuntime.connected()?0x70d9b4:0xffcc88);
        if(DisplaySettings.INSTANCE.metrics){
            g.fill(5,51,Math.min(uiWidth-5,640),81,0xdd10151f);
            g.drawString(font,font.plainSubstrByWidth(ClientRuntime.TEXTURE.stats(),uiWidth-20),10,55,0xffffff);
            g.drawString(font,"Mode: "+DisplayDemand.mode+" | Ctrl+, settings | F9 hide",10,68,0x70d9b4);
        }
        super.render(g,(int)(mx/uiScale),(int)(my/uiScale),partial);g.pose().popPose();
    }
    private boolean inside(double x,double y){return x>=sx&&x<sx+sw&&y>=sy&&y<sy+sh;}
    private void pointer(String action,double x,double y,int button,double delta){
        if(!ClientRuntime.connected())return;
        pointerX=x;pointerY=y;
        JsonObject msg=new JsonObject();msg.addProperty("type","pointer");msg.addProperty("action",action);
        msg.addProperty("x",Math.max(0,Math.min(.99999,(x-sx)/sw)));msg.addProperty("y",Math.max(0,Math.min(.99999,(y-sy)/sh)));
        msg.addProperty("button",button);msg.addProperty("delta",delta);msg.addProperty("modifiers",modifiers());ClientRuntime.send(msg);
    }
    private int modifiers() { return (hasAltDown()?1:0)|(hasControlDown()?2:0)|(hasShiftDown()?8:0); }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==3||button==4){command(button==3?"back":"forward");return true;}
        if (y/uiScale<22) {
            int ux=(int)(x/uiScale);
            if(ux<22) tabOffset=Math.max(0,tabOffset-visibleTabs());
            else if(ux>=uiWidth-22) tabOffset=Math.min(Math.max(0,ClientRuntime.tabs.entries().size()-1),tabOffset+visibleTabs());
            else if(ux>=24) { int index=tabOffset+(ux-24)/tabWidth();
                if(index>=0&&index<ClientRuntime.tabs.entries().size()) {
                    boolean close=button==2||(button==0&&(ux-24)%tabWidth()>=tabWidth()-23);
                    if(close||button==0)tabAction(close?"close-tab":"switch-tab",ClientRuntime.tabs.entries().get(index).id());
                }
            }
            return true;
        }
        if (inside(x,y) && y>=topMargin() && y<height-bottomMargin() && button<=2 && ClientRuntime.TEXTURE.location()!=null) {
            webFocus=true; setFocused(null); text.setFocused(false); address.setFocused(false);
            pressed.add(button); pointer("down",x,y,button,0); return true;
        }
        webFocus=false; return super.mouseClicked(x/uiScale,y/uiScale,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if (pressed.remove(button)) { pointer("up",x,y,button,0); return true; }
        return super.mouseReleased(x/uiScale,y/uiScale,button);
    }
    @Override public void mouseMoved(double x,double y) {
        if ((inside(x,y)&&y>=topMargin()&&y<height-bottomMargin())||!pressed.isEmpty()) pointer("move",x,y,pressed.isEmpty()?-1:pressed.iterator().next(),0);
        super.mouseMoved(x/uiScale,y/uiScale);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if (pressed.contains(button)) { pointer("move",x,y,button,0); return true; }
        return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(y/uiScale<22){tabOffset=Math.max(0,Math.min(Math.max(0,ClientRuntime.tabs.entries().size()-1),tabOffset+(delta>0?-1:1)));return true;}
        if (inside(x,y)&&y>=topMargin()&&y<height-bottomMargin()) {
            if(hasControlDown())command(delta>0?"zoom-in":"zoom-out");else pointer("wheel",x,y,-1,-delta*70); return true;
        }
        return super.mouseScrolled(x/uiScale,y/uiScale,delta);
    }
    private void insert(String value) {
        JsonObject msg=new JsonObject(); msg.addProperty("type","text"); msg.addProperty("text",value); ClientRuntime.send(msg);
    }
    private void submitText() { if (!text.getValue().isEmpty()) { insert(text.getValue()); text.setValue(""); } focusWeb(); }
    private void key(String name,int mods){JsonObject msg=new JsonObject();msg.addProperty("type","key");msg.addProperty("key",name);msg.addProperty("modifiers",mods);ClientRuntime.send(msg);}
    private void command(String action) { JsonObject msg=new JsonObject(); msg.addProperty("type","command"); msg.addProperty("action",action); ClientRuntime.send(msg); }
    @Override public boolean charTyped(char c,int modifiers) {
        if (webFocus) { if (!hasControlDown()&&!hasAltDown()&&c!=' '&&!Character.isISOControl(c)) insert(String.valueOf(c)); return true; }
        return super.charTyped(c,modifiers);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(ClientRuntime.FULLSCREEN.matches(key,scan)){BrowserFullscreen.toggle();return true;}
        if(key==GLFW.GLFW_KEY_F9){DisplaySettings.INSTANCE.metrics=!DisplaySettings.INSTANCE.metrics;DisplaySettings.INSTANCE.save();return true;}
        if(hasControlDown()&&key==GLFW.GLFW_KEY_COMMA){minecraft.setScreen(new DisplaySettingsScreen(this));return true;}
        if (key==GLFW.GLFW_KEY_ESCAPE) {
            if(text.isFocused()||address.isFocused()){focusWeb();return true;}
            return super.keyPressed(key,scan,mods);
        }
        // Native edit fields own their editing shortcuts; browser actions must not steal them.
        if(text.isFocused()||address.isFocused()){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){
                if(address.isFocused())navigate(false);else{submitText();if(hasControlDown())key("Enter",0);}return true;
            }
            if(!(hasControlDown()&&(key==GLFW.GLFW_KEY_L||key==GLFW.GLFW_KEY_I)))return super.keyPressed(key,scan,mods);
        }
        if(hasControlDown()){
            if(key==GLFW.GLFW_KEY_L){focusAddress();return true;}
            if(key==GLFW.GLFW_KEY_I){webFocus=false;setFocused(text);text.setFocused(true);return true;}
            if(key==GLFW.GLFW_KEY_W){tabAction("close-tab",ClientRuntime.tabs.activeId());return true;}
            if(key==GLFW.GLFW_KEY_T){newTab();return true;}
            if(key==GLFW.GLFW_KEY_TAB){cycleTab(hasShiftDown()?-1:1);return true;}
            if(key==GLFW.GLFW_KEY_R){command("reload");return true;}
            if(key==GLFW.GLFW_KEY_EQUAL||key==GLFW.GLFW_KEY_MINUS||key==GLFW.GLFW_KEY_0){command(key==GLFW.GLFW_KEY_EQUAL?"zoom-in":key==GLFW.GLFW_KEY_MINUS?"zoom-out":"zoom-reset");return true;}
        }
        if(key==GLFW.GLFW_KEY_F5){command("reload");return true;}
        if(hasAltDown()&&(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT)){command(key==GLFW.GLFW_KEY_LEFT?"back":"forward");return true;}
        if(address.isFocused() && key==GLFW.GLFW_KEY_ENTER){navigate(false);return true;}
        if (text.isFocused() && key==GLFW.GLFW_KEY_ENTER) { submitText();if(hasControlDown())key("Enter",0); return true; }
        if (webFocus) {
            if (hasControlDown() && key==GLFW.GLFW_KEY_V) { insert(minecraft.keyboardHandler.getClipboard()); return true; }
            if(hasControlDown()&&(key==GLFW.GLFW_KEY_C||key==GLFW.GLFW_KEY_X)){JsonObject msg=new JsonObject();msg.addProperty("type","clipboard");msg.addProperty("cut",key==GLFW.GLFW_KEY_X);ClientRuntime.send(msg);return true;}
            String name = switch(key) {
                case GLFW.GLFW_KEY_ENTER -> "Enter"; case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
                case GLFW.GLFW_KEY_DELETE -> "Delete"; case GLFW.GLFW_KEY_TAB -> "Tab";
                case GLFW.GLFW_KEY_LEFT -> "ArrowLeft"; case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
                case GLFW.GLFW_KEY_UP -> "ArrowUp"; case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
                case GLFW.GLFW_KEY_HOME -> "Home"; case GLFW.GLFW_KEY_END -> "End";
                case GLFW.GLFW_KEY_PAGE_UP -> "PageUp"; case GLFW.GLFW_KEY_PAGE_DOWN -> "PageDown";
                case GLFW.GLFW_KEY_SPACE -> "Space";
                default -> "";
            };
            if (hasControlDown() && key==GLFW.GLFW_KEY_A) name="a";
            if (hasControlDown() && key==GLFW.GLFW_KEY_Z) name="z";
            if (hasControlDown() && key==GLFW.GLFW_KEY_Y) name="y";
            if (!name.isEmpty()) { JsonObject msg=new JsonObject(); msg.addProperty("type","key"); msg.addProperty("key",name); msg.addProperty("modifiers",modifiers()); ClientRuntime.send(msg); }
            return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed() {
        for(int button:pressed) pointer("up",pointerX,pointerY,button,0);
        pressed.clear();
    }
    @Override public boolean isPauseScreen() { return false; }
}
