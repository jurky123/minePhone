package dev.browserlink.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class DisplaySettingsScreen extends Screen {
    private final Screen parent;
    private final DisplaySettings s=DisplaySettings.INSTANCE;
    DisplaySettingsScreen(Screen parent){super(Component.literal("BrowserLink · 显示设置"));this.parent=parent;}
    private void row(int y,String label,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->{action.run();s.save();rebuildWidgets();}).bounds(width/2-140,y,280,20).build());}
    private void slider(int y,String name,float current,float min,float max,java.util.function.DoubleConsumer setter){
        addRenderableWidget(new AbstractSliderButton(width/2-140,y,280,20,Component.literal(name+"："+Math.round(current*100)),(current-min)/(max-min)){
            @Override protected void updateMessage(){setMessage(Component.literal(name+"："+Math.round((min+value*(max-min))*100)));}
            @Override protected void applyValue(){setter.accept(min+value*(max-min));s.clamp();}
        });
    }
    @Override protected void init(){
        int y=Math.max(26,height/2-124);
        row(y,"自适应画质 / 按需刷新："+(s.adaptive?"开":"关"),()->s.adaptive=!s.adaptive);
        row(y+24,"手机方向："+(s.landscape?"横屏":"竖屏")+"（手持时 F7 切换）",()->s.landscape=!s.landscape);
        slider(y+48,"手持大小 (%)",s.handScale,.7f,1.15f,v->s.handScale=(float)v);
        slider(y+72,"手持左右",s.handX,-.3f,.3f,v->s.handX=(float)v);
        slider(y+96,"手持上下",s.handY,-.3f,.3f,v->s.handY=(float)v);
        slider(y+120,"手机近屏大小 (%)",s.focusScale,.7f,1,v->s.focusScale=(float)v);
        row(y+144,"性能面板："+(s.metrics?"开":"关")+"（F9）",()->s.metrics=!s.metrics);
        row(y+168,"浏览器兼容无边框全屏："+(BrowserFullscreen.active()?"开":"关")+"（F6）",BrowserFullscreen::toggle);
        row(y+192,"网页流畅优先："+(s.browserGameFps==0?"关":"游戏上限 "+s.browserGameFps+" FPS"),()->s.browserGameFps=s.browserGameFps==0?60:s.browserGameFps==60?40:s.browserGameFps==40?30:0);
        addRenderableWidget(Button.builder(Component.literal("完成"),b->onClose()).bounds(width/2-140,y+224,280,20).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,10,0xffffff);super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){s.save();}
    @Override public boolean isPauseScreen(){return false;}
}
