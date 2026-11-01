package dev.browserlink;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
public final class TvGeometry {
    public static final float[] SCREEN={-0.810043668f,0.327510917f,1.810043668f,1.801310044f,0.579070493f};
    private static final VoxelShape[][] SHAPES=new VoxelShape[4][6];
    static {
        for(Direction d:Direction.Plane.HORIZONTAL)for(int p=0;p<6;p++){
            // A thin 3 x 2 furniture footprint. Every occupied cell is clickable.
            VoxelShape full=(d.getAxis()==Direction.Axis.Z)?Shapes.box(-1,0,.17,2,1.942,.83):Shapes.box(.17,0,-1,.83,1.942,2);
            var side=d.getCounterClockWise();int offset=p%3-1;
            SHAPES[d.get2DDataValue()][p]=Shapes.join(full.move(-side.getStepX()*offset,-p/3,-side.getStepZ()*offset),Shapes.block(),BooleanOp.AND);
        }
    }
    public static VoxelShape shape(Direction d,int part){return SHAPES[d.get2DDataValue()][part];}
}
