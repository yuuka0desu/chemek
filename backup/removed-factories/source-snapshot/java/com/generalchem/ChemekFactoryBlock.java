package com.generalchem;

import mekanism.api.Upgrade;
import mekanism.api.security.ISecurityUtils;
import mekanism.api.text.ILangEntry;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.block.attribute.AttributeGui;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.interfaces.IHasTileEntity;
import mekanism.common.block.interfaces.ITypeBlock;
import mekanism.common.content.blocktype.BlockType;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * 化工厂方块：通过实现 {@link ITypeBlock} 暴露 Mekanism 原生方块属性，
 * 使安全/红石/升级/侧配置/物流颜色等全部由 Mekanism 原版组件直接驱动。
 */
public final class ChemekFactoryBlock extends Block implements EntityBlock, ITypeBlock, IHasTileEntity<ChemekFactoryBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final ILangEntry BLOCK_TYPE_DESCRIPTION = () -> "block_type.generalchem.factory";

    private final ChemekFactoryType type;
    private final ChemekFactoryTier tier;
    private final BlockType blockType;

    public ChemekFactoryBlock(Properties properties, ChemekFactoryType type, ChemekFactoryTier tier) {
        super(properties);
        this.type = type;
        this.tier = tier;
        this.blockType = buildBlockType();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private BlockType buildBlockType() {
        BlockType result = new BlockType(BLOCK_TYPE_DESCRIPTION);
        result.add(
                new AttributeGui(() -> GeneralChem.FACTORY_MENU_TYPE, null),
                Attributes.SECURITY,
                Attributes.REDSTONE,
                new AttributeStateFacing(FACING),
                new AttributeUpgradeSupport(Set.of(Upgrade.SPEED, Upgrade.ENERGY)),
                new AttributeEnergy(
                        () -> ChemekFactoryBlockEntity.feToJoules(type.energyPerTick()),
                        () -> ChemekFactoryBlockEntity.feToJoules(tier.getEnergyCapacity())));
        return result;
    }

    @Override
    public BlockType getType() {
        return blockType;
    }

    @Override
    public TileEntityTypeRegistryObject<ChemekFactoryBlockEntity> getTileType() {
        return GeneralChem.FACTORY_TILE_TYPE;
    }

    public ChemekFactoryType getFactoryType() {
        return type;
    }

    public ChemekFactoryTier getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemekFactoryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof ChemekFactoryBlockEntity factory) {
                TileEntityMekanism.tickServer(lvl, pos, st, factory);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ChemekFactoryBlockEntity factory) {
            CompoundTag saved = stack.getTagElement("BlockEntityTag");
            if (saved != null) {
                factory.load(saved.copy());
            }
            if (placer instanceof Player player && factory.getSecurity().getOwnerUUID() == null) {
                factory.getSecurity().setOwnerUUID(player.getUUID());
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChemekFactoryBlockEntity factory) {
            factory.onNeighborChange(neighborBlock, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()
                && !(newState.getBlock() instanceof ChemekFactoryBlock)
                && level.getBlockEntity(pos) instanceof ChemekFactoryBlockEntity factory) {
            factory.blockRemoved();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new java.util.ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ChemekFactoryBlockEntity factory) {
            ItemStack machine = drops.stream().filter(stack -> stack.is(asItem())).findFirst().orElse(null);
            if (machine == null) {
                machine = new ItemStack(asItem());
                drops.add(machine);
            }
            CompoundTag blockEntityTag = factory.saveWithoutMetadata();
            machine.getOrCreateTag().put("BlockEntityTag", blockEntityTag);
        }
        return drops;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ChemekFactoryBlockEntity factory) {
            if (factory.getSecurity() != null && factory.getSecurity().getOwnerUUID() == null) {
                factory.getSecurity().setOwnerUUID(player.getUUID());
            }
            return factory.openGui(player);
        }
        return InteractionResult.CONSUME;
    }
}
