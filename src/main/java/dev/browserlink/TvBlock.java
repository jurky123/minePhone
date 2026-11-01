package dev.browserlink;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Six occupied cells keep the full large screen selectable and avoid clipping into walls. */
public final class TvBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,5);
    public TvBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(PART,1));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,PART);}
    public static BlockPos cell(BlockPos anchor,Direction facing,int part){return anchor.relative(facing.getCounterClockWise(),part%3-1).above(part/3);}
    public static BlockPos anchor(BlockPos pos,BlockState state){int p=state.getValue(PART);return pos.relative(state.getValue(FACING).getCounterClockWise(),1-p%3).below(p/3);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction facing=c.getHorizontalDirection().getOpposite();
        for(int p=0;p<6;p++){BlockPos at=cell(c.getClickedPos(),facing,p);if(c.getLevel().isOutsideBuildHeight(at)||!c.getLevel().getWorldBorder().isWithinBounds(at)||!c.getLevel().getBlockState(at).canBeReplaced(c))return null;}
        return defaultBlockState().setValue(FACING,facing);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(!level.isClientSide)for(int p=0;p<6;p++)if(p!=1)level.setBlock(cell(pos,state.getValue(FACING),p),state.setValue(PART,p),3);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(PART)==1?new TvEntity(pos,state):null;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext c){return TvGeometry.shape(s.getValue(FACING),s.getValue(PART));}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide&&hand==InteractionHand.MAIN_HAND)DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.browserlink.client.ClientRuntime.openTv(anchor(pos,state),state.getValue(FACING)));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide&&player.isCreative()&&state.getValue(PART)!=1){BlockPos a=anchor(pos,state);if(level.getBlockState(a).is(this))level.destroyBlock(a,false);}
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!next.is(this)&&!level.isClientSide){
            BlockPos a=anchor(pos,state);Direction facing=state.getValue(FACING);
            if(state.getValue(PART)!=1){if(level.getBlockState(a).is(this))level.destroyBlock(a,true);}
            else for(int p=0;p<6;p++)if(p!=1){BlockPos at=cell(a,facing,p);BlockState s=level.getBlockState(at);if(s.is(this)&&s.getValue(FACING)==facing&&s.getValue(PART)==p)level.destroyBlock(at,false);}
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
