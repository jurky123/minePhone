package dev.browserlink.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** An ordinary undecorated desktop window, so Chrome's visible helper can stay on top. */
final class BrowserFullscreen {
    private static boolean active;
    private static int x,y,w,h;
    private static int decorated;
    private static int pendingEnter,placement,tx,ty,tw,th;
    private BrowserFullscreen() {}
    static boolean active(){return active;}
    static void toggle(){
        var mc=Minecraft.getInstance();var window=mc.getWindow();long handle=window.getWindow();
        if(active){restore(handle);return;}
        // Leave Minecraft fullscreen through its own API before changing GLFW geometry.
        if(window.isFullscreen()){window.toggleFullScreen();mc.options.fullscreen().set(false);pendingEnter=3;return;}
        int[] px={0},py={0},pw={0},ph={0};
        GLFW.glfwGetWindowPos(handle,px,py);GLFW.glfwGetWindowSize(handle,pw,ph);
        x=px[0];y=py[0];w=pw[0];h=ph[0];decorated=GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_DECORATED);
        long monitor=GLFW.glfwGetPrimaryMonitor();long bestArea=-1;
        var monitors=GLFW.glfwGetMonitors();
        if(monitors!=null)for(int i=0;i<monitors.limit();i++){
            long m=monitors.get(i);var mode=GLFW.glfwGetVideoMode(m);if(mode==null)continue;
            GLFW.glfwGetMonitorPos(m,px,py);
            long area=(long)Math.max(0,Math.min(x+w,px[0]+mode.width())-Math.max(x,px[0]))
                *Math.max(0,Math.min(y+h,py[0]+mode.height())-Math.max(y,py[0]));
            if(area>bestArea){monitor=m;bestArea=area;}
        }
        var mode=GLFW.glfwGetVideoMode(monitor);if(mode==null)return;
        GLFW.glfwGetMonitorPos(monitor,px,py);GLFW.glfwRestoreWindow(handle);
        tx=px[0];ty=py[0];tw=mode.width();th=mode.height();placement=3;
        GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(handle,0,px[0],py[0],mode.width(),mode.height(),GLFW.GLFW_DONT_CARE);
        active=true;
    }
    private static void restore(long handle){
        GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,decorated);
        GLFW.glfwSetWindowMonitor(handle,0,x,y,Math.max(320,w),Math.max(240,h),GLFW.GLFW_DONT_CARE);
        active=false;
        placement=0;
    }
    static void tick(){
        if(pendingEnter>0&&--pendingEnter==0){toggle();return;}
        if(active&&placement>0&&--placement==0)GLFW.glfwSetWindowMonitor(Minecraft.getInstance().getWindow().getWindow(),0,tx,ty,tw,th,GLFW.GLFW_DONT_CARE);
        // F11 remains available: it exits compatibility mode and enters vanilla fullscreen.
        if(active&&Minecraft.getInstance().getWindow().isFullscreen()){
            GLFW.glfwSetWindowAttrib(Minecraft.getInstance().getWindow().getWindow(),GLFW.GLFW_DECORATED,decorated);
            active=false;
        }
    }
}
