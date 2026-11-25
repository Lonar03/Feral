package keystrokesmod.module.impl.player;

import com.google.common.base.Predicates;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Mouse;

public class GhostHand extends Module {
    private final SliderSetting transparency;
    private GroupSetting interactGroup;
    private ButtonSetting throughNonPlayer;
    private ButtonSetting throughBots;
    private ButtonSetting throughFriendlies;
    private ButtonSetting throughEnemies;
    private GroupSetting priorityGroup;
    private ButtonSetting priorityEverything;
    private ButtonSetting priorityBed;
    private ButtonSetting priorityBedAdjacent;
    private GroupSetting useGroup;
    private GroupSetting conditionsGroup;
    private ButtonSetting useSword;
    private ButtonSetting useTool;
    private ButtonSetting useFists;
    private ButtonSetting useBucket;
    private ButtonSetting useFlintSteel;
    private ButtonSetting useCobweb;
    private ButtonSetting useOther;
    private ButtonSetting requireLmb;
    private ButtonSetting requireRmb;
    private ButtonSetting notSword;

    public GhostHand() {
        super("Ghost Hand", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.transparency = new SliderSetting("Transparency", "%", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.interactGroup = new GroupSetting("Interact through"));
        this.registerSetting(
                this.throughNonPlayer = new ButtonSetting(this.interactGroup, "Non-player entities", true));
        this.registerSetting(this.throughBots = new ButtonSetting(this.interactGroup, "Bots", false));
        this.registerSetting(this.throughFriendlies = new ButtonSetting(this.interactGroup, "Friendlies", false));
        this.registerSetting(this.throughEnemies = new ButtonSetting(this.interactGroup, "Enemies", true));
        this.registerSetting(this.priorityGroup = new GroupSetting("Preferred targets"));
        this.registerSetting(this.priorityEverything = new ButtonSetting(this.priorityGroup, "Everything", true));
        this.registerSetting(this.priorityBed = new ButtonSetting(this.priorityGroup, "Bed", false));
        this.registerSetting(this.priorityBedAdjacent = new ButtonSetting(this.priorityGroup, "Next to bed", false));
        this.registerSetting(this.useGroup = new GroupSetting("Allow while using"));
        this.registerSetting(this.useSword = new ButtonSetting(this.useGroup, "Sword", false));
        this.registerSetting(this.useTool = new ButtonSetting(this.useGroup, "Tool", true));
        this.registerSetting(this.useFists = new ButtonSetting(this.useGroup, "Fists", true));
        this.registerSetting(this.useBucket = new ButtonSetting(this.useGroup, "Bucket", true));
        this.registerSetting(this.useFlintSteel = new ButtonSetting(this.useGroup, "Flint and steel", true));
        this.registerSetting(this.useCobweb = new ButtonSetting(this.useGroup, "Cobweb", true));
        this.registerSetting(this.useOther = new ButtonSetting(this.useGroup, "Other", true));
        this.registerSetting(this.conditionsGroup = new GroupSetting("Conditions"));
        this.registerSetting(this.requireLmb = new ButtonSetting(this.conditionsGroup, "Require Left mouse", false));
        this.registerSetting(this.requireRmb = new ButtonSetting(this.conditionsGroup, "Require right mouse", false));
        this.registerSetting(this.notSword = new ButtonSetting(this.conditionsGroup, "Not holding a sword", false));
    }

    public boolean shouldOverrideMouseOver() {
        if (!this.isEnabled()) {
            return false;
        } else if (mc == null || mc.world == null || mc.player == null) {
            return false;
        } else if (mc.getCamera() == null) {
            return false;
        } else if (this.notSword.isToggled() && Utils.holdingSword()) {
            return false;
        } else if (this.requireLmb.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        } else {
            return this.requireRmb.isToggled() && !Mouse.isButtonDown(1) ? false : this.heldItemAllowed();
        }
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (this.shouldOverrideMouseOver()) {
            Entity viewEntity = mc.getCamera();
            double reach = mc.interactionManager.getReach();
            if (mc.interactionManager.hasExtendedReach()) {
                reach = 6.0;
            }

            HitResult blockHit = this.findPrioritizedBlock(viewEntity, reach, partialTicks);
            if (blockHit != null) {
                boolean priorityOverride = this.isPriorityTarget(blockHit);
                if (this.priorityEverything.isToggled() || priorityOverride) {
                    if (!priorityOverride) {
                        Vec3d eyes = viewEntity.getEyePosition(partialTicks);
                        Vec3d blockHitVec = blockHit.facePos;
                        double blockDist = eyes.distanceTo(blockHitVec);
                        Box scanBox = new Box(
                                Math.min(eyes.x, blockHitVec.x) - 1.0,
                                Math.min(eyes.y, blockHitVec.y) - 1.0,
                                Math.min(eyes.z, blockHitVec.z) - 1.0,
                                Math.max(eyes.x, blockHitVec.x) + 1.0,
                                Math.max(eyes.y, blockHitVec.y) + 1.0,
                                Math.max(eyes.z, blockHitVec.z) + 1.0);
                        List<Entity> candidates = mc.world.getEntities(
                                viewEntity, scanBox, Predicates.and(EntityFilter.NOT_SPECTATOR, Entity::hasCollision));
                        Entity closest = null;
                        double closestDist = Double.MAX_VALUE;

                        for (Entity e : candidates) {
                            if (e != viewEntity) {
                                float cb = e.getPickRadius();
                                Box bb = e.getShape().grown(cb, cb, cb);
                                HitResult intercept = bb.clip(eyes, blockHitVec);
                                boolean inside = bb.contains(eyes);
                                if (inside || intercept != null) {
                                    double dist = inside ? 0.0 : eyes.distanceTo(intercept.facePos);
                                    if (!(dist >= blockDist) && e != viewEntity.vehicle && dist < closestDist) {
                                        closestDist = dist;
                                        closest = e;
                                    }
                                }
                            }
                        }

                        if (closest == null || !this.obstructionAllowed(closest)) {
                            return;
                        }
                    }

                    mc.crosshairTarget = blockHit;
                    mc.targetEntity = null;
                    GameRenderer renderer = mc.gameRenderer;
                    if (renderer instanceof IAccessorEntityRenderer) {
                        ((IAccessorEntityRenderer) renderer).setPointedEntity(null);
                    }
                }
            }
        }
    }

    public float getRenderOpacity(Entity entity, float partialTicks) {
        double amount = this.transparency.getInput();
        if (!(amount <= 0.0) && entity instanceof PlayerEntity && this.shouldOverrideMouseOver()) {
            Entity viewEntity = mc.getCamera();
            if (entity == mc.player
                    || entity == viewEntity
                    || ((PlayerEntity) entity).isSpectator()
                    || !entity.hasCollision()) {
                return 1.0F;
            }

            if (entity == viewEntity.vehicle) {
                return 1.0F;
            }

            if (!this.priorityEverything.isToggled() || !this.obstructionAllowed(entity)) {
                if (!this.priorityBed.isToggled() && !this.priorityBedAdjacent.isToggled()) {
                    return 1.0F;
                }

                double reach = mc.interactionManager.hasExtendedReach() ? 6.0 : mc.interactionManager.getReach();
                if (!this.isPriorityTarget(this.findPrioritizedBlock(viewEntity, reach, partialTicks))) {
                    return 1.0F;
                }
            }

            return (float) (1.0 - Math.min(100.0, amount) / 100.0);
        } else {
            return 1.0F;
        }
    }

    private boolean isPriorityTarget(HitResult blockHit) {
        if (blockHit == null) {
            return false;
        }

        BlockPos hitPos = blockHit.getPos();
        Block hitBlock = BlockUtils.getBlock(hitPos);
        boolean isBed = hitBlock instanceof BedBlock;
        return this.priorityBed.isToggled() && isBed
                || this.priorityBedAdjacent.isToggled() && !isBed && BlockUtils.isAdjacentToBed(hitPos);
    }

    private HitResult findPrioritizedBlock(Entity viewEntity, double reach, float partialTicks) {
        Vec3d eyes = viewEntity.getEyePosition(partialTicks);
        Vec3d look = viewEntity.getRotationVector(partialTicks);
        Vec3d rayEnd = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        return BlockUtils.traverseBlocksAlongRay(
                eyes, rayEnd, this.priorityBed.isToggled(), this.priorityBedAdjacent.isToggled());
    }

    private boolean heldItemAllowed() {
        ItemStack held = mc.player.getDisplayItemInHand();
        switch (classify(held)) {
            case SWORD:
                return this.useSword.isToggled();
            case TOOL:
                return this.useTool.isToggled();
            case FISTS:
                return this.useFists.isToggled();
            case BUCKET:
                return this.useBucket.isToggled();
            case FLINT_STEEL:
                return this.useFlintSteel.isToggled();
            case COBWEB:
                return this.useCobweb.isToggled();
            default:
                return this.useOther.isToggled();
        }
    }

    private boolean obstructionAllowed(Entity e) {
        if (!(e instanceof PlayerEntity)) {
            return this.throughNonPlayer.isToggled();
        } else {
            PlayerEntity player = (PlayerEntity) e;
            if (AntiBot.isBot(player)) {
                return this.throughBots.isToggled();
            } else {
                return !Utils.isFriended(player) && !Utils.isTeammate(player)
                        ? this.throughEnemies.isToggled()
                        : this.throughFriendlies.isToggled();
            }
        }
    }

    private static GhostHand.HeldCategory classify(ItemStack held) {
        if (held == null) {
            return GhostHand.HeldCategory.FISTS;
        } else {
            Item item = held.getItem();
            if (item instanceof SwordItem) {
                return GhostHand.HeldCategory.SWORD;
            } else if (item instanceof ToolItem || item instanceof HoeItem || item instanceof ShearsItem) {
                return GhostHand.HeldCategory.TOOL;
            } else if (item instanceof BucketItem) {
                return GhostHand.HeldCategory.BUCKET;
            } else if (item instanceof FlintAndSteelItem) {
                return GhostHand.HeldCategory.FLINT_STEEL;
            } else {
                return item instanceof BlockItem && ((BlockItem) item).getBlock() == Blocks.WEB
                        ? GhostHand.HeldCategory.COBWEB
                        : GhostHand.HeldCategory.OTHER;
            }
        }
    }

    private enum HeldCategory {
        SWORD,
        TOOL,
        FISTS,
        BUCKET,
        FLINT_STEEL,
        COBWEB,
        OTHER;
    }
}
