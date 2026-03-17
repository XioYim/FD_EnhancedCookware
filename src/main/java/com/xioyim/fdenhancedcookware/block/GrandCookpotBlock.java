package com.xioyim.fdenhancedcookware.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;
import com.xioyim.fdenhancedcookware.init.ModBlockEntities;
import vectorwing.farmersdelight.common.registry.ModParticleTypes;

import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class GrandCookpotBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 形状：比农夫乐事的锅略高（含4×3网格）
    protected static final VoxelShape SHAPE =
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    public GrandCookpotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ===================== 方块状态 =====================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    // ===================== 交互 =====================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GrandCookpotBlockEntity cookpot) {
                // 空桶右键 → 清除锅内所有液体
                if (player.getItemInHand(hand).is(Items.BUCKET)) {
                    cookpot.clearFluid();
                    level.playSound(null, pos,
                            net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.fd_enhancedcookware.fluid_cleared")
                                    .withStyle(net.minecraft.ChatFormatting.AQUA),
                            true);
                    return InteractionResult.SUCCESS;
                }
                NetworkHooks.openScreen((ServerPlayer) player, cookpot, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    // ===================== Block Entity =====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrandCookpotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.GRAND_COOKPOT.get(),
                    GrandCookpotBlockEntity::animationTick);
        } else {
            return createTickerHelper(type, ModBlockEntities.GRAND_COOKPOT.get(),
                    GrandCookpotBlockEntity::serverTick);
        }
    }

    // ===================== 粒子动画（仿 FD） =====================

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof GrandCookpotBlockEntity cookpot)) return;
        if (!cookpot.isHeated(level, pos)) return;

        double cx = pos.getX() + 0.5D;
        double cy = pos.getY();
        double cz = pos.getZ() + 0.5D;

        // 沸腾声（与 FD 一致）
        if (random.nextInt(10) == 0) {
            level.playLocalSound(cx, cy, cz,
                    SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.2F + 0.9F, false);
        }

        // 蒸汽粒子：X/Z=0 垂直上升（FD 参数为正值单向漂移，会斜飞，此处修正为对称 0）
        // Y 速度取 FD 的约一半，确保粒子快速消散但不冲太高
        for (int i = 0; i < 1; i++) {
            level.addParticle(ModParticleTypes.STEAM.get(),
                    cx + (-0.5F + random.nextFloat()) * 0.25F,
                    pos.getY() + 0.6F,
                    cz + (-0.5F + random.nextFloat()) * 0.25F,
                    0D, 0.008D + random.nextDouble() * 0.001D, 0D);
        }

        // 气泡粒子：在锅口液面（Y+0.72）随机出现，不依赖 cookingState
        if (random.nextFloat() < 0.5F) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                    pos.getY() + 0.72D,
                    pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                    0, 0, 0);
        }
    }

    // ===================== 掉落（带 NBT） =====================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GrandCookpotBlockEntity cookpot) {
                // 清除悬浮展示实体，防止方块被破坏后残留
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    cookpot.killDisplayEntitiesPublic(sl);
                }
                // 丢出所有库存物品
                Containers.dropContents(level, pos, cookpot.getDroppableInventory());
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * Ctrl+皮克时保留库存 NBT。
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        GrandCookpotBlockEntity cookpot = (GrandCookpotBlockEntity) level.getBlockEntity(pos);
        if (cookpot != null) {
            CompoundTag tag = cookpot.saveInventoryToTag(new CompoundTag());
            if (!tag.isEmpty()) {
                stack.addTagElement("BlockEntityTag", tag);
            }
        }
        return stack;
    }

    // ===================== 红石 =====================

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GrandCookpotBlockEntity cookpot) {
            return cookpot.getRedstoneSignal();
        }
        return 0;
    }
}
