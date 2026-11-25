package keystrokesmod.utility;

import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithBlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ColoredBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.BlockItem;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class BlockUtils implements IMinecraftInstance {
    public static boolean isSamePos(BlockPos blockPos, BlockPos blockPos2) {
        return blockPos == blockPos2
                || blockPos.getX() == blockPos2.getX()
                        && blockPos.getY() == blockPos2.getY()
                        && blockPos.getZ() == blockPos2.getZ();
    }

    public static boolean notFull(Block block) {
        return block instanceof FenceGateBlock
                || block instanceof LadderBlock
                || block instanceof FlowerPotBlock
                || block instanceof AbstractPressurePlateBlock
                || isFluid(block)
                || block instanceof FenceBlock
                || block instanceof AnvilBlock
                || block instanceof EnchantingTableBlock
                || block instanceof ChestBlock;
    }

    public static boolean isNormalBlock(Block block) {
        return block == Blocks.GLASS
                || block.isOpaque()
                        && block != Blocks.GRAVEL
                        && block != Blocks.SAND
                        && block != Blocks.SOUL_SAND
                        && block != Blocks.TNT
                        && block != Blocks.CRAFTING_TABLE
                        && block != Blocks.FURNACE
                        && block != Blocks.DISPENSER
                        && block != Blocks.DROPPER
                        && block != Blocks.NOTEBLOCK
                        && block != Blocks.COMMAND_BLOCK;
    }

    public static BlockPos pos(double x, double y, double z) {
        return new BlockPos(x, y, z);
    }

    public static boolean isBlockPosEqual(BlockPos pos1, BlockPos pos2) {
        return pos1 == pos2 || pos1.getX() == pos2.getX() && pos1.getY() == pos2.getY() && pos1.getZ() == pos2.getZ();
    }

    public static BlockPos offsetPos(HitResult mop) {
        return mop.getPos().offset(mop.face);
    }

    public static boolean isFluid(Block block) {
        return block.getMaterial() == Material.LAVA || block.getMaterial() == Material.WATER;
    }

    public static boolean isInteractable(Block block) {
        return block instanceof TrapdoorBlock
                || block instanceof DoorBlock
                || block instanceof BlockWithBlockEntity
                || block instanceof JukeboxBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof EnchantingTableBlock
                || block instanceof BrewingStandBlock
                || block instanceof BedBlock
                || block instanceof DropperBlock
                || block instanceof DispenserBlock
                || block instanceof HopperBlock
                || block instanceof AnvilBlock
                || block instanceof NoteBlock
                || block instanceof CraftingTableBlock;
    }

    public static boolean isInteractable(HitResult mv) {
        if (mv == null || mv.type != Type.BLOCK || mv.getPos() == null) {
            return false;
        } else {
            return mc.player.isSneaking() && mc.player.getDisplayItemInHand() != null
                    ? false
                    : isInteractable(getBlock(mv.getPos()));
        }
    }

    public static float getBlockHardness(Block block, ItemStack itemStack, boolean ignoreSlow, boolean ignoreGround) {
        float getBlockHardness = block.getMiningTime(mc.world, null);
        if (getBlockHardness < 0.0F) {
            return 0.0F;
        } else {
            return !block.getMaterial().isToolNotRequired() && (itemStack == null || !itemStack.canMineBlock(block))
                    ? getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 100.0F
                    : getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 30.0F;
        }
    }

    public static float maxDigRateAcrossSlots(Block block, int slotCount) {
        if (mc.player != null && slotCount > 0) {
            int n = Math.min(slotCount, mc.player.inventory.getSize());
            float best = 0.0F;

            for (int i = 0; i < n; i++) {
                float h = getBlockHardness(block, mc.player.inventory.getItem(i), false, false);
                if (h > best) {
                    best = h;
                }
            }

            return best;
        } else {
            return 0.0F;
        }
    }

    public static float getToolDigEfficiency(
            ItemStack itemStack, Block block, boolean ignoreSlow, boolean ignoreGround) {
        float n = itemStack == null ? 1.0F : itemStack.getItem().getMiningSpeed(itemStack, block);
        if (n > 1.0F) {
            int getEnchantmentLevel = EnchantmentHelper.getLevel(Enchantment.EFFICIENCY.id, itemStack);
            if (getEnchantmentLevel > 0 && itemStack != null) {
                n += getEnchantmentLevel * getEnchantmentLevel + 1;
            }
        }

        if (mc.player.hasStatusEffect(StatusEffect.HASTE)) {
            n *= 1.0F + (mc.player.getEffectInstance(StatusEffect.HASTE).getAmplifier() + 1) * 0.2F;
        }

        if (!ignoreSlow) {
            if (mc.player.hasStatusEffect(StatusEffect.MINING_FATIGUE)) {
                float n2;
                switch (mc.player.getEffectInstance(StatusEffect.MINING_FATIGUE).getAmplifier()) {
                    case 0:
                        n2 = 0.3F;
                        break;
                    case 1:
                        n2 = 0.09F;
                        break;
                    case 2:
                        n2 = 0.0027F;
                        break;
                    default:
                        n2 = 8.1E-4F;
                }

                n *= n2;
            }

            if (mc.player.isSubmergedIn(Material.WATER) && !EnchantmentHelper.getAquaAffinityLevel(mc.player)) {
                n /= 5.0F;
            }

            if (!mc.player.onGround && !ignoreGround) {
                n /= 5.0F;
            }
        }

        return n;
    }

    public static Block getBlock(BlockPos blockPos) {
        return getBlockState(blockPos).getBlock();
    }

    public static Block getBlock(double x, double y, double z) {
        return getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static Block getBlock(Vec3d position) {
        return getBlockState(new BlockPos(position.x, position.y, position.z)).getBlock();
    }

    public static BlockState getBlockState(BlockPos blockPos) {
        return mc.world != null && blockPos != null ? mc.world.getBlockState(blockPos) : Blocks.AIR.defaultState();
    }

    public static Box getBlockSelectionBox(BlockPos pos) {
        if (mc.world != null && pos != null) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            block.updateShape(mc.world, pos);
            Box box = block.getOutlineShape(mc.world, pos);
            if (box == null) {
                box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
            }

            return box;
        } else {
            return null;
        }
    }

    public static Box getCollisionOrSelectionBox(BlockPos pos) {
        if (mc.world != null && pos != null) {
            BlockState st = mc.world.getBlockState(pos);
            Block block = st.getBlock();
            Box bb = block.getCollisionShape(mc.world, pos, st);
            if (bb == null) {
                bb = block.getOutlineShape(mc.world, pos);
            }

            if (bb == null) {
                bb = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
            }

            return bb;
        } else {
            return null;
        }
    }

    public static Box getCollisionOrSelectedOnly(BlockPos pos) {
        if (mc.world != null && pos != null) {
            BlockState st = mc.world.getBlockState(pos);
            Block block = st.getBlock();
            Box bb = block.getCollisionShape(mc.world, pos, st);
            if (bb == null) {
                bb = block.getOutlineShape(mc.world, pos);
            }

            return bb;
        } else {
            return null;
        }
    }

    public static Box unionBlockBounds(BlockPos a, BlockPos b) {
        Box ua = getCollisionOrSelectionBox(a);
        Box ub = getCollisionOrSelectionBox(b);
        return ua.union(ub);
    }

    public static Direction facingFromBlockCenterToPoint(BlockPos pos, Vec3d hit) {
        double px = hit.x - (pos.getX() + 0.5);
        double py = hit.y - (pos.getY() + 0.5);
        double pz = hit.z - (pos.getZ() + 0.5);
        double ax = Math.abs(px);
        double ay = Math.abs(py);
        double az = Math.abs(pz);
        if (ax > ay && ax > az) {
            return px > 0.0 ? Direction.EAST : Direction.WEST;
        } else if (ay > az) {
            return py > 0.0 ? Direction.UP : Direction.DOWN;
        } else {
            return pz > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static boolean check(BlockPos blockPos, Block block) {
        return getBlock(blockPos) == block;
    }

    public static boolean replaceable(BlockPos blockPos) {
        return !Utils.nullCheck() ? true : getBlock(blockPos).canBeReplaced(mc.world, blockPos);
    }

    public static boolean canSeeVecBlock(BlockPos pos, Vec3d vecPlayer, Vec3d vecBlockPoint) {
        HitResult mop = mc.world.rayTrace(vecPlayer, vecBlockPoint, false, false, false);
        if (mop == null) {
            return true;
        }

        if (mop.type == Type.BLOCK) {
            BlockPos mopPos = mop.getPos();
            if (mopPos.getX() == pos.getX() && mopPos.getY() == pos.getY() && mopPos.getZ() == pos.getZ()) {
                return true;
            }
        }

        return false;
    }

    public static boolean canBlockBeSeen(BlockPos pos) {
        Vec3d vecPlayer = new Vec3d(mc.player.x, mc.player.y + mc.player.getEyeHeight(), mc.player.z);

        for (double offsetY = 0.0; offsetY <= 0.5; offsetY += 0.5) {
            double y = pos.getY() + offsetY;
            Vec3d vecBlockPoint = new Vec3d(pos.getX() + 1, y, pos.getZ() + 0.5);
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }

            vecBlockPoint = new Vec3d(pos.getX(), y, pos.getZ() + 0.5);
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }

            vecBlockPoint = new Vec3d(pos.getX() + 0.5, y, pos.getZ() + 1);
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }

            vecBlockPoint = new Vec3d(pos.getX() + 0.5, y, pos.getZ());
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }
        }

        return false;
    }

    public static DyeColor getWoolColor(BlockState state) {
        return (DyeColor) state.values().get(ColoredBlock.COLOR);
    }

    public static Direction[] getVisibleFaces(Vec3d eye, BlockPos block) {
        Direction yFace =
                Math.abs(eye.y - (block.getY() + 1)) < Math.abs(eye.y - block.getY()) ? Direction.UP : Direction.DOWN;
        Direction zFace = Math.abs(eye.z - (block.getZ() + 1)) < Math.abs(eye.z - block.getZ())
                ? Direction.SOUTH
                : Direction.NORTH;
        Direction xFace =
                Math.abs(eye.x - (block.getX() + 1)) < Math.abs(eye.x - block.getX()) ? Direction.EAST : Direction.WEST;
        return new Direction[] {yFace, zFace, xFace};
    }

    public static boolean containsFace(Direction[] faces, Direction face) {
        for (Direction f : faces) {
            if (f == face) {
                return true;
            }
        }

        return false;
    }

    public static Vec3d getFaceCenter(BlockPos block, Direction face) {
        double eps = 0.001;
        double cx = block.getX() + 0.5;
        double cy = block.getY() + 0.5;
        double cz = block.getZ() + 0.5;
        switch (face) {
            case UP:
                return new Vec3d(cx, block.getY() + 1 - eps, cz);
            case DOWN:
                return new Vec3d(cx, block.getY() + eps, cz);
            case NORTH:
                return new Vec3d(cx, cy, block.getZ() + eps);
            case SOUTH:
                return new Vec3d(cx, cy, block.getZ() + 1 - eps);
            case EAST:
                return new Vec3d(block.getX() + 1 - eps, cy, cz);
            case WEST:
                return new Vec3d(block.getX() + eps, cy, cz);
            default:
                return new Vec3d(cx, cy, cz);
        }
    }

    public static double dist2PointAABB(Vec3d p, BlockPos b) {
        double cx = Math.max(b.getX(), Math.min(b.getX() + 1, p.x));
        double cy = Math.max(b.getY(), Math.min(b.getY() + 1, p.y));
        double cz = Math.max(b.getZ(), Math.min(b.getZ() + 1, p.z));
        double dx = p.x - cx;
        double dy = p.y - cy;
        double dz = p.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean canPlaceBlockOnSide(ItemStack stack, BlockPos pos, Direction side) {
        return stack != null && stack.getItem() instanceof BlockItem
                ? ((BlockItem) stack.getItem()).onPlace(mc.world, pos, side, mc.player, stack)
                : false;
    }

    public static float getFistBreakTicks(Block block) {
        float hardness = block.getMiningTime(mc.world, null);
        if (hardness < 0.0F) {
            return Float.MAX_VALUE;
        } else {
            return hardness == 0.0F ? 0.0F : hardness * (block.getMaterial().isToolNotRequired() ? 30.0F : 100.0F);
        }
    }

    public static boolean hasAirNeighbor(BlockPos pos, BlockPos... exclude) {
        for (Direction f : Direction.values()) {
            BlockPos n = pos.offset(f);
            if (mc.world.getBlockState(n).getBlock() == Blocks.AIR) {
                boolean excluded = false;

                for (BlockPos ex : exclude) {
                    if (n.equals(ex)) {
                        excluded = true;
                        break;
                    }
                }

                if (!excluded) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isAdjacentToBed(BlockPos pos) {
        for (Direction face : Direction.values()) {
            if (getBlock(pos.offset(face)) instanceof BedBlock) {
                return true;
            }
        }

        return false;
    }

    public static HitResult traverseBlocksAlongRay(Vec3d start, Vec3d end, boolean wantBed, boolean wantAdjacent) {
        if (mc.world == null) {
            return null;
        }

        if (Double.isNaN(start.x) || Double.isNaN(start.y) || Double.isNaN(start.z)) {
            return null;
        }

        if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
            int destX = MathHelper.floor(end.x);
            int destY = MathHelper.floor(end.y);
            int destZ = MathHelper.floor(end.z);
            int curX = MathHelper.floor(start.x);
            int curY = MathHelper.floor(start.y);
            int curZ = MathHelper.floor(start.z);
            HitResult firstHit = null;
            HitResult candidate = getBlockCollisionHit(curX, curY, curZ, start, end);
            if (candidate != null) {
                if (isBedOrAdjacentMatch(candidate.getPos(), wantBed, wantAdjacent)) {
                    return candidate;
                }

                firstHit = candidate;
            }

            Vec3d tracePos = start;
            int remaining = 200;

            while (remaining-- >= 0) {
                if (Double.isNaN(tracePos.x) || Double.isNaN(tracePos.y) || Double.isNaN(tracePos.z)) {
                    return firstHit;
                }

                if (curX == destX && curY == destY && curZ == destZ) {
                    return firstHit;
                }

                boolean crossX = true;
                boolean crossY = true;
                boolean crossZ = true;
                double boundX = 999.0;
                double boundY = 999.0;
                double boundZ = 999.0;
                if (destX > curX) {
                    boundX = curX + 1.0;
                } else if (destX < curX) {
                    boundX = curX;
                } else {
                    crossX = false;
                }

                if (destY > curY) {
                    boundY = curY + 1.0;
                } else if (destY < curY) {
                    boundY = curY;
                } else {
                    crossY = false;
                }

                if (destZ > curZ) {
                    boundZ = curZ + 1.0;
                } else if (destZ < curZ) {
                    boundZ = curZ;
                } else {
                    crossZ = false;
                }

                double dx = end.x - tracePos.x;
                double dy = end.y - tracePos.y;
                double dz = end.z - tracePos.z;
                double tX = 999.0;
                double tY = 999.0;
                double tZ = 999.0;
                if (crossX) {
                    tX = (boundX - tracePos.x) / dx;
                }

                if (crossY) {
                    tY = (boundY - tracePos.y) / dy;
                }

                if (crossZ) {
                    tZ = (boundZ - tracePos.z) / dz;
                }

                if (tX == -0.0) {
                    tX = -1.0E-4;
                }

                if (tY == -0.0) {
                    tY = -1.0E-4;
                }

                if (tZ == -0.0) {
                    tZ = -1.0E-4;
                }

                Direction face;
                if (tX < tY && tX < tZ) {
                    face = destX > curX ? Direction.WEST : Direction.EAST;
                    tracePos = new Vec3d(boundX, tracePos.y + dy * tX, tracePos.z + dz * tX);
                } else if (tY < tZ) {
                    face = destY > curY ? Direction.DOWN : Direction.UP;
                    tracePos = new Vec3d(tracePos.x + dx * tY, boundY, tracePos.z + dz * tY);
                } else {
                    face = destZ > curZ ? Direction.NORTH : Direction.SOUTH;
                    tracePos = new Vec3d(tracePos.x + dx * tZ, tracePos.y + dy * tZ, boundZ);
                }

                curX = MathHelper.floor(tracePos.x) - (face == Direction.EAST ? 1 : 0);
                curY = MathHelper.floor(tracePos.y) - (face == Direction.UP ? 1 : 0);
                curZ = MathHelper.floor(tracePos.z) - (face == Direction.SOUTH ? 1 : 0);
                candidate = getBlockCollisionHit(curX, curY, curZ, start, end);
                if (candidate != null) {
                    if (isBedOrAdjacentMatch(candidate.getPos(), wantBed, wantAdjacent)) {
                        return candidate;
                    }

                    if (firstHit == null) {
                        firstHit = candidate;
                    }
                }
            }

            return firstHit;
        } else {
            return null;
        }
    }

    private static HitResult getBlockCollisionHit(int x, int y, int z, Vec3d start, Vec3d end) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        return !block.canRayTrace(state, false) ? null : block.rayTrace(mc.world, pos, start, end);
    }

    private static boolean isBedOrAdjacentMatch(BlockPos pos, boolean wantBed, boolean wantAdjacent) {
        Block block = getBlock(pos);
        boolean isBed = block instanceof BedBlock;
        return wantBed && isBed ? true : wantAdjacent && !isBed && isAdjacentToBed(pos);
    }
}
