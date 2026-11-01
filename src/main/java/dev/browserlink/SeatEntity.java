package dev.browserlink;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** A server-authoritative, temporary mount. The chair block is the visible model. */
public final class SeatEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(SeatEntity.class,EntityDataSerializers.BLOCK_POS);
    public SeatEntity(EntityType<? extends SeatEntity> type,Level level) { super(type,level); noPhysics=true; setNoGravity(true); }
    @Override protected void defineSynchedData() { entityData.define(ANCHOR,BlockPos.ZERO); }
    public BlockPos anchor() { return entityData.get(ANCHOR); }
    public void place(BlockPos pos,Direction facing) {
        entityData.set(ANCHOR,pos.immutable()); setPos(pos.getX()+.5+facing.getStepX()/32.0,pos.getY()+ChairGeometry.SEAT_HEIGHT,pos.getZ()+.5+facing.getStepZ()/32.0); setYRot(facing.toYRot());
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (getPassengers().isEmpty() || !level().getBlockState(anchor()).is(BrowserLink.CHAIR.get()))) {
            ejectPassengers(); discard();
        }
    }
    @Override public double getPassengersRidingOffset() { return -.4; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos base=anchor();
        for (Direction d:Direction.Plane.HORIZONTAL) for(int dy:new int[]{0,1,-1}) {
            BlockPos p=base.relative(d).offset(0,dy,0);
            Vec3 candidate=new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5);
            if (level().getBlockState(p.below()).isFaceSturdy(level(),p.below(),Direction.UP)
                && level().noCollision(passenger,passenger.getDimensions(Pose.STANDING).makeBoundingBox(candidate))) return candidate;
        }
        return new Vec3(base.getX()+.5,base.getY()+1.5,base.getZ()+.5);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(ANCHOR,BlockPos.of(tag.getLong("Chair"))); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("Chair",anchor().asLong()); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
