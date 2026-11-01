package dev.browserlink;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(BrowserLink.ID)
public final class BrowserLink {
    public static final String ID = "browserlink";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<Block> CHAIR = BLOCKS.register("computer_chair", () -> new ChairBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion()));
    public static final RegistryObject<Item> CHAIR_ITEM = ITEMS.register("computer_chair", () -> new BlockItem(CHAIR.get(),new Item.Properties()));
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat", () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new,MobCategory.MISC).sized(.01f,.01f).clientTrackingRange(8).updateInterval(20).noSummon().build("browserlink:seat"));
    public static final RegistryObject<Block> COMPUTER = BLOCKS.register("computer", () -> new ComputerBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion()));
    public static final RegistryObject<Item> COMPUTER_ITEM = ITEMS.register("computer", () -> new BlockItem(COMPUTER.get(), new Item.Properties()));
    public static final RegistryObject<Block> TV = BLOCKS.register("television", () -> new TvBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion()));
    public static final RegistryObject<Item> TV_ITEM = ITEMS.register("television", () -> new BlockItem(TV.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<TvEntity>> TV_ENTITY = ENTITIES.register("television", () -> BlockEntityType.Builder.of(TvEntity::new,TV.get()).build(null));
    public static final RegistryObject<Item> PHONE = ITEMS.register("phone", () -> new PhoneItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<ComputerEntity>> COMPUTER_ENTITY = ENTITIES.register("computer", () -> BlockEntityType.Builder.of(ComputerEntity::new, COMPUTER.get()).build(null));
    public BrowserLink() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        ENTITY_TYPES.register(bus);
        bus.addListener(this::creative);
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(COMPUTER_ITEM);
            event.accept(TV_ITEM);
            event.accept(PHONE);
            event.accept(CHAIR_ITEM);
        }
    }
}
