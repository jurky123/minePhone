package dev.browserlink;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ChairBlock extends HorizontalDirectionalBlock {
    public ChairBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState s,Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s,Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return ChairGeometry.shape(s.getValue(FACING)); }
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!l.isClientSide && !player.isPassenger()) {
            if (!l.getEntitiesOfClass(SeatEntity.class,new AABB(p).inflate(.1),e -> e.anchor().equals(p)).isEmpty()) return InteractionResult.CONSUME;
            SeatEntity seat = new SeatEntity(BrowserLink.SEAT.get(),l);
            seat.place(p,s.getValue(FACING));
            l.addFreshEntity(seat);
            if (!player.startRiding(seat)) seat.discard();
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
