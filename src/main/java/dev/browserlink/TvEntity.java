package dev.browserlink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
public final class TvEntity extends BlockEntity {
    public TvEntity(BlockPos pos,BlockState state){super(BrowserLink.TV_ENTITY.get(),pos,state);}
    @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(1,1,1);}
}
