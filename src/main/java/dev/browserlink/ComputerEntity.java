package dev.browserlink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ComputerEntity extends BlockEntity {
    public ComputerEntity(BlockPos pos, BlockState state) { super(BrowserLink.COMPUTER_ENTITY.get(), pos, state); }
}
