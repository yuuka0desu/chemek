package com.generalchem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

@Mod(GeneralChem.MOD_ID)
public final class GeneralChem {
    public static final String MOD_ID = "generalchem";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TILES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), MOD_ID);

    private static final Map<ChemekFactoryType, Map<ChemekFactoryTier, RegistryObject<Block>>> FACTORY_BLOCKS =
            new EnumMap<>(ChemekFactoryType.class);
    private static final Map<ChemekFactoryType, Map<ChemekFactoryTier, RegistryObject<Item>>> FACTORY_ITEMS =
            new EnumMap<>(ChemekFactoryType.class);

    public static final RegistryObject<BlockEntityType<ChemekFactoryBlockEntity>> FACTORY_TILE;

    public static final mekanism.common.registration.impl.TileEntityTypeRegistryObject<ChemekFactoryBlockEntity> FACTORY_TILE_TYPE;

    public static final RegistryObject<MenuType<ChemekFactoryMenu>> FACTORY_MENU =
            MENUS.register("factory",
                    () -> mekanism.common.inventory.container.type.MekanismContainerType.tile(
                            ChemekFactoryBlockEntity.class, ChemekFactoryMenu::new));

    public static final mekanism.common.registration.impl.ContainerTypeRegistryObject<ChemekFactoryMenu> FACTORY_MENU_TYPE =
            new mekanism.common.registration.impl.ContainerTypeRegistryObject<>(FACTORY_MENU);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.generalchem"))
                    .icon(() -> new ItemStack(FACTORY_ITEMS
                            .get(ChemekFactoryType.COMBINER).get(ChemekFactoryTier.ULTIMATE).get()))
                    .displayItems((params, output) -> {
                        for (ChemekFactoryType type : ChemekFactoryType.values()) {
                            for (ChemekFactoryTier tier : ChemekFactoryTier.values()) {
                                output.accept(FACTORY_ITEMS.get(type).get(tier).get());
                            }
                        }
                    })
                    .build());

    static {
        for (ChemekFactoryType type : ChemekFactoryType.values()) {
            Map<ChemekFactoryTier, RegistryObject<Block>> tierBlocks = new EnumMap<>(ChemekFactoryTier.class);
            Map<ChemekFactoryTier, RegistryObject<Item>> tierItems = new EnumMap<>(ChemekFactoryTier.class);
            for (ChemekFactoryTier tier : ChemekFactoryTier.values()) {
                String name = type.getName() + "_factory_" + tier.getName();
                RegistryObject<Block> block = BLOCKS.register(name,
                        () -> new ChemekFactoryBlock(
                                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                                        .requiresCorrectToolForDrops(),
                                type, tier));
                tierBlocks.put(tier, block);
                tierItems.put(tier, ITEMS.register(name,
                        () -> new BlockItem(block.get(), new Item.Properties())));
            }
            FACTORY_BLOCKS.put(type, tierBlocks);
            FACTORY_ITEMS.put(type, tierItems);
        }

        FACTORY_TILE = TILES.register("factory",
                () -> BlockEntityType.Builder.of(ChemekFactoryBlockEntity::new,
                                FACTORY_BLOCKS.values().stream()
                                        .flatMap(m -> m.values().stream())
                                        .map(RegistryObject::get)
                                        .toArray(Block[]::new))
                        .build(null));

        FACTORY_TILE_TYPE = new mekanism.common.registration.impl.TileEntityTypeRegistryObject<>(FACTORY_TILE);
    }

    public GeneralChem() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TILES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GeneralChemConfig.SPEC,
                "general-chemistry-common.toml");

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static Block factoryBlock(ChemekFactoryType type, ChemekFactoryTier tier) {
        return FACTORY_BLOCKS.get(type).get(tier).get();
    }

    /** 供 Mekanism 原版安装器升级调用（经 TierInstallerChemekMixin）。 */
    public static void upgradeFactory(net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos,
                                      ChemekFactoryBlockEntity factory) {
        ChemekFactoryTier current = factory.getTier();
        ChemekFactoryTier next = current.next();
        if (next == null) {
            return;
        }
        Block newBlock = factoryBlock(factory.getFactoryType(), next);
        level.setBlock(pos, newBlock.defaultBlockState()
                        .setValue(ChemekFactoryBlock.FACING,
                                level.getBlockState(pos).getValue(ChemekFactoryBlock.FACING)),
                Block.UPDATE_ALL);

        net.minecraft.world.level.block.entity.BlockEntity newEntity = level.getBlockEntity(pos);
        if (newEntity instanceof ChemekFactoryBlockEntity newFactory) {
            factory.transferTo(newFactory);
        }
    }

    public static Item speedUpgradeItem() {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("mekanism", "upgrade_speed"));
    }

    public static Item energyUpgradeItem() {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("mekanism", "upgrade_energy"));
    }

    public static boolean isSpeedUpgrade(ItemStack stack) {
        return stack.is(speedUpgradeItem());
    }

    public static boolean isEnergyUpgrade(ItemStack stack) {
        return stack.is(energyUpgradeItem());
    }
}
