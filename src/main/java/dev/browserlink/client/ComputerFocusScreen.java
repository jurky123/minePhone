package dev.browserlink.client;

import dev.browserlink.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Transparent input surface aligned with the real world monitor, using a local camera only. */
public final class ComputerFocusScreen extends BrowserScreen {
    public static final double FOV=55;
    private final BlockPos pos;
    private final Direction facing;
    private final Level sourceLevel;
    private final Entity previousCamera;
    private final CameraType previousType;
    private final ArmorStand camera;
    private double distance;
    private final float[] screen;
    private final net.minecraft.world.level.block.Block device;
    public ComputerFocusScreen(BlockPos pos,Direction facing) {
        this(pos,facing,ComputerGeometry.SCREEN,BrowserLink.COMPUTER.get());
    }
    public ComputerFocusScreen(BlockPos pos,Direction facing,float[] screen,net.minecraft.world.level.block.Block device) {
        Minecraft mc=Minecraft.getInstance();
        this.screen=screen;this.device=device;
        this.pos=pos.immutable(); this.facing=facing; sourceLevel=mc.level;
        previousCamera=mc.getCameraEntity(); previousType=mc.options.getCameraType();
        camera=new ArmorStand(mc.level,0,0,0); camera.setInvisible(true); camera.setNoGravity(true);
    }
    @Override protected void init() {
        super.init();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        updateCamera(); minecraft.setCameraEntity(camera);
    }
    @Override protected boolean worldView() { return true; }
    @Override protected void layout() {
        float[] s=screen;
        double screenW=s[2]-s[0],screenH=s[3]-s[1];
        double aspect=(double)minecraft.getWindow().getWidth()/minecraft.getWindow().getHeight();
        double tangent=Math.tan(Math.toRadians(FOV/2));
        double fractionH=Math.max(.15,Math.min(.84,(height-2.0*topMargin()-8*uiScale)/height));
        distance=Math.max(screenH/(2*tangent*fractionH),screenW/(2*tangent*aspect*.92));
        double planePixelsPerBlock=height/(2*distance*tangent);
        double scale=Math.min(screenW/ClientRuntime.TEXTURE.width(),screenH/ClientRuntime.TEXTURE.height());
        sw=Math.max(1,(int)Math.round(ClientRuntime.TEXTURE.width()*scale*planePixelsPerBlock));
        sh=Math.max(1,(int)Math.round(ClientRuntime.TEXTURE.height()*scale*planePixelsPerBlock));
        sx=(width-sw)/2; sy=(height-sh)/2;
        if (camera!=null) updateCamera();
    }
    private void updateCamera() {
        float[] s=screen;
        double x=(s[0]+s[2])/2-.5,z=s[4]-.5;
        double angle=Math.toRadians(-facing.toYRot());
        Vec3 center=new Vec3(pos.getX()+.5+Math.cos(angle)*x+Math.sin(angle)*z,
            pos.getY()+(s[1]+s[3])/2,pos.getZ()+.5-Math.sin(angle)*x+Math.cos(angle)*z);
        Vec3 eye=center.add(facing.getStepX()*distance,0,facing.getStepZ()*distance);
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);
        camera.xo=camera.getX(); camera.yo=camera.getY(); camera.zo=camera.getZ();
        camera.xOld=camera.getX(); camera.yOld=camera.getY(); camera.zOld=camera.getZ();
        camera.setYRot(facing.toYRot()+180); camera.yRotO=camera.getYRot();
        // LivingEntity cameras interpolate HEAD yaw, not Entity.yRot. The un-ticked
        // proxy otherwise repeats the constructor's random head-yaw interpolation every tick.
        camera.yHeadRot=camera.getYRot(); camera.yHeadRotO=camera.getYRot();
        camera.yBodyRot=camera.getYRot(); camera.yBodyRotO=camera.getYRot();
        camera.setXRot(0); camera.xRotO=0;
    }
    @Override public void tick() {
        super.tick();
        if (minecraft.level!=sourceLevel || minecraft.player==null || !minecraft.player.isAlive()
            || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos))>36
            || !sourceLevel.getBlockState(pos).is(device)
            || sourceLevel.getBlockState(pos).getValue(ComputerBlock.FACING)!=facing) { onClose(); return; }
        layout();
    }
    @Override public void removed() {
        super.removed();
        if (minecraft.getCameraEntity()==camera) minecraft.setCameraEntity(previousCamera!=null && !previousCamera.isRemoved()?previousCamera:minecraft.player);
        minecraft.options.setCameraType(previousType);
    }
}
