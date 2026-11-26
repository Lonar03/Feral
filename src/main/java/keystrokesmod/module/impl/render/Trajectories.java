package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.ProjectileTrajectoryRenderer;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;

public class Trajectories extends Module {
    private static final int HIT_NONE = 0;
    private static final int HIT_ENTITY = 1;
    private static final int HIT_WALL = 2;
    private static final int HIT_GROUND = 3;
    private ButtonSetting disableUnchargedBow;
    private ButtonSetting highlightEntities;
    private ButtonSetting shortenLine;
    private SliderSetting lineThickness;
    private SliderSetting target;
    private ButtonSetting autoScale;
    private ButtonSetting addPlayerVelocity;
    private ColorSetting defaultColor;
    private ColorSetting enemyColor;
    private ColorSetting wallColor;
    private ColorSetting groundColor;
    private SliderSetting maxTicks;
    private ButtonSetting showLanding;
    private ButtonSetting onlyWhenHolding;
    private ButtonSetting fireball;
    private ButtonSetting fireballThrown;
    private ButtonSetting fireballEnemy;
    private ButtonSetting pearlThrown;
    private ButtonSetting pearlEnemy;
    private ButtonSetting arrowThrown;
    private ButtonSetting arrowEnemy;
    private ButtonSetting enableBowDepth;
    private final ProjectileTrajectoryRenderer projectileRenderer = new ProjectileTrajectoryRenderer();
    private static final double ARROW_WATER_DRAG = 0.6;
    private static final double THROWABLE_WATER_DRAG = 0.8;
    private static final double FISH_HOOK_DRAG = 0.92;
    private static final double WATER_FLOW_ACCELERATION = 0.014;
    private static final double WATER_CHECK_EXPAND_Y = -0.4F;
    private static final double WATER_CHECK_CONTRACT = 0.001;
    private static final double LAVA_CHECK_EXPAND_XZ = -0.1F;
    private static final double LAVA_CHECK_EXPAND_Y = -0.4F;
    private static final int FISH_HOOK_WATER_SLICE_COUNT = 5;
    private static final double FISH_HOOK_BUOYANCY_ACCELERATION = 0.04F;
    private static final double FISH_HOOK_WATER_VERTICAL_DAMPING = 0.8;
    private static final double FISH_HOOK_WATER_DRAG_MULTIPLIER = 0.9;
    private static final double FIRST_PERSON_RENDER_CLIP_DISTANCE = 0.5;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7;
    private static final double ENTITY_HIT_EXPANSION = 0.3;
    private static final float BEDBUG_SNOWBALL_VELOCITY = 1.75F;

    public Trajectories() {
        super("Trajectories", Module.category.render);
        this.registerSetting(this.lineThickness = new SliderSetting("Line thickness", 2.0, 1.0, 5.0, 0.1));
        this.registerSetting(this.maxTicks = new SliderSetting("Max ticks", 100.0, 30.0, 200.0, 10.0));
        this.registerSetting(this.target = new SliderSetting("Target", 0, new String[] {"Cross", "Box"}));
        this.registerSetting(this.defaultColor = new ColorSetting("Default", 170, 0, 255));
        this.registerSetting(this.enemyColor = new ColorSetting("Enemy Hit", 255, 50, 50));
        this.registerSetting(this.wallColor = new ColorSetting("Wall Hit", 50, 255, 50));
        this.registerSetting(this.groundColor = new ColorSetting("Ground Hit", 85, 255, 255));
        this.registerSetting(this.autoScale = new ButtonSetting("Auto-scale", true));
        this.registerSetting(this.disableUnchargedBow = new ButtonSetting("Disable uncharged bow", true));
        this.registerSetting(
                this.enableBowDepth = new ButtonSetting("Enable depth (bow)", false, "Items.Enable depth (bow)"));
        this.registerSetting(this.highlightEntities = new ButtonSetting("Highlight on entity", true));
        this.registerSetting(this.shortenLine = new ButtonSetting("Shorten line", false));
        this.registerSetting(this.showLanding = new ButtonSetting("Show landing", true));
        this.registerSetting(this.onlyWhenHolding = new ButtonSetting("Only when holding", true));
        this.registerSetting(this.addPlayerVelocity = new ButtonSetting("Add player velocity", false));
        GroupSetting items = new GroupSetting("Items");
        this.registerSetting(items);
        this.registerSetting(this.fireball = new ButtonSetting(items, "Fireball", true));
        this.registerSetting(this.fireballThrown = new ButtonSetting(items, "Fireball (Thrown)", true));
        this.registerSetting(this.fireballEnemy = new ButtonSetting(items, "Fireball (Enemy)", false));
        this.registerSetting(this.pearlThrown = new ButtonSetting(items, "Pearl (Thrown)", true));
        this.registerSetting(this.pearlEnemy = new ButtonSetting(items, "Pearl (Enemy)", false));
        this.registerSetting(this.arrowThrown = new ButtonSetting(items, "Arrow (Thrown)", true));
        this.registerSetting(this.arrowEnemy = new ButtonSetting(items, "Arrow (Enemy)", false));
        this.liteModule = true;
    }

    @Override
    public void guiUpdate() {
        this.enableBowDepth.setVisible(this.arrowEnemy.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.projectileRenderer.clear();
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityS2CPacket) {
            this.projectileRenderer.onVelocityPacket((EntityVelocityS2CPacket) event.getPacket());
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            this.projectileRenderer.updateWorld();
        }
    }

    private void renderItemTrajectories(float partialTicks) {
        this.projectileRenderer.updateWorld();
        this.projectileRenderer.configureTarget(
                this.target.getInput() == 0.0,
                this.autoScale.isToggled(),
                (float) this.lineThickness.getInput(),
                this.enableBowDepth.isToggled());
        ItemStack localHeldStack = mc.player.getDisplayItemInHand();
        if (this.fireball.isToggled() && localHeldStack != null && localHeldStack.getItem() == Items.FIRE_CHARGE) {
            this.projectileRenderer.renderHeld(mc.player, partialTicks);
        }

        if (this.fireballThrown.isToggled() || this.pearlThrown.isToggled() || this.arrowThrown.isToggled()) {
            for (Entity entity : mc.world.entities) {
                if (entity instanceof FireballEntity && this.fireballThrown.isToggled()
                        || entity instanceof EnderPearlEntity && this.pearlThrown.isToggled()
                        || entity instanceof ArrowEntity && this.arrowThrown.isToggled()) {
                    this.projectileRenderer.renderThrown(entity, partialTicks);
                }
            }
        }

        if (this.fireballEnemy.isToggled() || this.pearlEnemy.isToggled() || this.arrowEnemy.isToggled()) {
            for (PlayerEntity player : mc.world.players) {
                if (player != mc.player
                        && !player.removed
                        && player.deathTicks == 0
                        && !player.isSpectator()
                        && !AntiBot.isBot(player)
                        && !Utils.isFriended(player)
                        && !Utils.isTeammate(player)) {
                    ItemStack heldStack = player.getDisplayItemInHand();
                    if (heldStack != null) {
                        Item item = heldStack.getItem();
                        if (item == Items.FIRE_CHARGE && this.fireballEnemy.isToggled()
                                || item == Items.ENDER_PEARL && this.pearlEnemy.isToggled()
                                || item == Items.BOW && this.arrowEnemy.isToggled()) {
                            this.projectileRenderer.renderHeld(player, partialTicks);
                        }
                    }
                }
            }
        }
    }

    private float getBowVelocity(float partialTicks) {
        int timeLeft = mc.player.getItemUseTimer();
        float drawTicks = 72000 - timeLeft + partialTicks;
        float f = drawTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        return f * 2.0F * 1.5F;
    }

    private Trajectories.TrajectoryProps getProjectileProperties(Item item, PlayerEntity player, float partialTicks) {
        if (item == Items.BOW) {
            float vel = this.getBowVelocity(partialTicks);
            return new Trajectories.TrajectoryProps(
                    Trajectories.PhysicsModel.ARROW, 0.05, 0.99, 0.6, 0.5, 0.5, 0.5, vel, false, true);
        } else if (item == Items.ENDER_PEARL) {
            return new Trajectories.TrajectoryProps(
                    Trajectories.PhysicsModel.THROWABLE, 0.03, 0.99, 0.8, 0.25, 0.25, 0.25, 1.5, true, false);
        } else if (item == Items.SNOWBALL || item == Items.EGG) {
            return new Trajectories.TrajectoryProps(
                    Trajectories.PhysicsModel.THROWABLE, 0.03, 0.99, 0.8, 0.25, 0.25, 0.25, 1.5, true, false);
        } else if (item == Items.EXPERIENCE_BOTTLE) {
            return new Trajectories.TrajectoryProps(
                    Trajectories.PhysicsModel.THROWABLE, 0.07, 0.99, 0.8, 0.25, 0.25, 0.25, 0.7, true, false);
        } else if (item == Items.POTION) {
            return new Trajectories.TrajectoryProps(
                    Trajectories.PhysicsModel.THROWABLE, 0.05, 0.99, 0.8, 0.25, 0.25, 0.25, 0.5, true, false);
        } else {
            return item == Items.FISHING_ROD
                    ? new Trajectories.TrajectoryProps(
                            Trajectories.PhysicsModel.FISH_HOOK, 0.04, 0.92, 0.92, 0.25, 0.25, 0.25, 1.5, false, false)
                    : null;
        }
    }

    private boolean isHypixelBedwars() {
        return Utils.isHypixel() && Utils.sidebarTitleContainsBedWars();
    }

    private boolean isBedbugSnowball(ItemStack stack) {
        if (stack != null && stack.getItem() == Items.SNOWBALL) {
            String name = stack.getHoverName();
            return name == null ? false : Utils.stripString(name).toLowerCase().contains("bedbug");
        } else {
            return false;
        }
    }

    private Trajectories.FluidState sampleFluidState(
            double posX, double posY, double posZ, Trajectories.TrajectoryProps props) {
        Box entityBox = this.getEntityBox(posX, posY, posZ, props);
        Box waterCheckBox = entityBox.grown(0.0, -0.4F, 0.0).contract(0.001, 0.001, 0.001);
        int minX = MathHelper.floor(waterCheckBox.minX);
        int maxX = MathHelper.floor(waterCheckBox.maxX + 1.0);
        int minY = MathHelper.floor(waterCheckBox.minY);
        int maxY = MathHelper.floor(waterCheckBox.maxY + 1.0);
        int minZ = MathHelper.floor(waterCheckBox.minZ);
        int maxZ = MathHelper.floor(waterCheckBox.maxZ + 1.0);
        if (!mc.world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new Trajectories.FluidState(false, false, new Vec3d(0.0, 0.0, 0.0));
        }

        boolean inWater = false;
        Vec3d flowDirection = new Vec3d(0.0, 0.0, 0.0);
        Mutable mutablePos = new Mutable();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = mc.world.getBlockState(mutablePos);
                    if (blockState.getBlock().getMaterial() == Material.WATER) {
                        double liquidSurfaceY =
                                y + 1 - LiquidBlock.getHeightLoss((Integer) blockState.get(LiquidBlock.LEVEL));
                        if (maxY >= liquidSurfaceY) {
                            inWater = true;
                            flowDirection = blockState
                                    .getBlock()
                                    .applyMaterialDrag(mc.world, mutablePos, mc.player, flowDirection);
                        }
                    }
                }
            }
        }

        if (flowDirection.length() > 0.0) {
            flowDirection = flowDirection.normalize();
        }

        boolean inLava = mc.world.containsMaterial(entityBox.grown(-0.1F, -0.4F, -0.1F), Material.LAVA);
        return new Trajectories.FluidState(inWater, inLava, flowDirection);
    }

    private Box getEntityBox(double posX, double posY, double posZ, Trajectories.TrajectoryProps props) {
        return new Box(
                posX - props.width * 0.5,
                posY,
                posZ - props.width * 0.5,
                posX + props.width * 0.5,
                posY + props.height,
                posZ + props.width * 0.5);
    }

    private double sampleWaterSubmersion(double posX, double posY, double posZ, Trajectories.TrajectoryProps props) {
        Box entityBox = this.getEntityBox(posX, posY, posZ, props);
        double submersion = 0.0;

        for (int slice = 0; slice < 5; slice++) {
            double boxHeight = entityBox.maxY - entityBox.minY;
            double minSliceY = entityBox.minY + boxHeight * slice / 5.0;
            double maxSliceY = entityBox.minY + boxHeight * (slice + 1) / 5.0;
            Box sliceBox =
                    new Box(entityBox.minX, minSliceY, entityBox.minZ, entityBox.maxX, maxSliceY, entityBox.maxZ);
            if (mc.world.containsLiquid(sliceBox, Material.WATER)) {
                submersion += 0.2;
            }
        }

        return submersion;
    }

    private void applyWaterFlowAcceleration(Trajectories.FluidState fluidState, double[] mot) {
        if (fluidState.inWater && !(fluidState.flowDirection.length() <= 0.0)) {
            mot[0] += fluidState.flowDirection.x * 0.014;
            mot[1] += fluidState.flowDirection.y * 0.014;
            mot[2] += fluidState.flowDirection.z * 0.014;
        }
    }

    private void tickVelocity(Trajectories.TrajectoryProps props, Trajectories.FluidState fluidState, double[] mot) {
        double drag = fluidState.inWater ? props.dragInWater : props.drag;
        mot[0] *= drag;
        mot[1] *= drag;
        mot[2] *= drag;
        mot[1] -= props.gravity;
    }

    private void tickFishingHookVelocity(
            double posX, double posY, double posZ, Trajectories.TrajectoryProps props, double[] mot) {
        double drag = props.drag;
        double waterSubmersion = this.sampleWaterSubmersion(posX, posY, posZ, props);
        double buoyancy = waterSubmersion * 2.0 - 1.0;
        mot[1] += 0.04F * buoyancy;
        if (waterSubmersion > 0.0) {
            drag *= 0.9;
            mot[1] *= 0.8;
        }

        mot[0] *= drag;
        mot[1] *= drag;
        mot[2] *= drag;
    }

    private void tickPostMoveVelocity(
            double posX,
            double posY,
            double posZ,
            Trajectories.TrajectoryProps props,
            Trajectories.FluidState fluidState,
            double[] mot) {
        if (props.physicsModel == Trajectories.PhysicsModel.FISH_HOOK) {
            this.tickFishingHookVelocity(posX, posY, posZ, props, mot);
        } else {
            if (fluidState.inLava && !fluidState.inWater) {}

            this.tickVelocity(props, fluidState, mot);
        }
    }

    private Box expandEntityCollisionBox(Box box) {
        return box.grown(0.3, 0.3, 0.3);
    }

    private Box getBlockSweepBounds(Vec3d start, Vec3d end) {
        return new Box(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z),
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z));
    }

    private Box getVanillaProjectileBounds(Block block, BlockPos pos) {
        block.updateShape(mc.world, pos);
        return block.getOutlineShape(mc.world, pos);
    }

    private Box intersectBoxes(Box first, Box second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);
        return !(maxX - minX <= 1.0E-7) && !(maxY - minY <= 1.0E-7) && !(maxZ - minZ <= 1.0E-7)
                ? new Box(minX, minY, minZ, maxX, maxY, maxZ)
                : null;
    }

    private Trajectories.BlockCollisionResult rayTraceBlockCollisionBoxes(
            Vec3d start, Vec3d end, Trajectories.TrajectoryProps props) {
        Box sweepBounds = this.getBlockSweepBounds(start, end);
        int minX = MathHelper.floor(sweepBounds.minX);
        int maxX = MathHelper.floor(sweepBounds.maxX + 1.0);
        int minY = MathHelper.floor(sweepBounds.minY);
        int maxY = MathHelper.floor(sweepBounds.maxY + 1.0);
        int minZ = MathHelper.floor(sweepBounds.minZ);
        int maxZ = MathHelper.floor(sweepBounds.maxZ + 1.0);
        if (!mc.world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new Trajectories.BlockCollisionResult(null, Double.MAX_VALUE);
        }

        List<Box> collisionBoxes = new ArrayList<>();
        Mutable mutablePos = new Mutable();
        HitResult bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = mc.world.getBlockState(mutablePos);
                    Block block = blockState.getBlock();
                    if ((!props.ignoreBlockWithoutBoundingBox
                                    || block.getCollisionShape(mc.world, mutablePos, blockState) != null)
                            && block.canRayTrace(blockState, false)) {
                        collisionBoxes.clear();
                        Box vanillaProjectileBounds = this.getVanillaProjectileBounds(block, mutablePos);
                        block.addCollisions(mc.world, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                        for (Box collisionBox : collisionBoxes) {
                            Box projectileCollisionBox = this.intersectBoxes(collisionBox, vanillaProjectileBounds);
                            if (projectileCollisionBox != null) {
                                HitResult hit = projectileCollisionBox.clip(start, end);
                                if (hit != null) {
                                    double distanceSq = start.squaredDistanceTo(hit.facePos);
                                    if (distanceSq + 1.0E-7 < bestDistanceSq) {
                                        bestDistanceSq = distanceSq;
                                        bestHit = new HitResult(hit.facePos, hit.face, new BlockPos(mutablePos));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new Trajectories.BlockCollisionResult(bestHit, bestDistanceSq);
    }

    private Trajectories.BlockCollisionResult getNearestBlockCollision(
            Vec3d start, Vec3d end, Trajectories.TrajectoryProps props) {
        HitResult vanillaHit = mc.world.rayTrace(start, end, false, props.ignoreBlockWithoutBoundingBox, false);
        double vanillaDistanceSq = vanillaHit != null ? start.squaredDistanceTo(vanillaHit.facePos) : Double.MAX_VALUE;
        Trajectories.BlockCollisionResult collisionBoxHit = this.rayTraceBlockCollisionBoxes(start, end, props);
        return collisionBoxHit.hit != null && collisionBoxHit.distanceSq + 1.0E-7 < vanillaDistanceSq
                ? collisionBoxHit
                : new Trajectories.BlockCollisionResult(vanillaHit, vanillaDistanceSq);
    }

    private List<double[]> clipRenderPoints(List<double[]> renderPoints, double hiddenDistance) {
        if (!(hiddenDistance <= 0.0) && renderPoints.size() >= 2) {
            List<double[]> clippedPoints = new ArrayList<>();
            double remainingDistance = hiddenDistance;
            double[] previousPoint = renderPoints.get(0);

            for (int i = 1; i < renderPoints.size(); i++) {
                double[] currentPoint = renderPoints.get(i);
                double deltaX = currentPoint[0] - previousPoint[0];
                double deltaY = currentPoint[1] - previousPoint[1];
                double deltaZ = currentPoint[2] - previousPoint[2];
                double segmentLength = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                if (remainingDistance > 0.0) {
                    if (segmentLength <= 1.0E-7) {
                        previousPoint = currentPoint;
                        continue;
                    }

                    if (segmentLength <= remainingDistance) {
                        remainingDistance -= segmentLength;
                        previousPoint = currentPoint;
                        continue;
                    }

                    double t = remainingDistance / segmentLength;
                    clippedPoints.add(new double[] {
                        previousPoint[0] + deltaX * t, previousPoint[1] + deltaY * t, previousPoint[2] + deltaZ * t
                    });
                    clippedPoints.add(currentPoint);
                    remainingDistance = 0.0;
                } else {
                    clippedPoints.add(currentPoint);
                }

                previousPoint = currentPoint;
            }

            return clippedPoints;
        } else {
            return renderPoints;
        }
    }

    private ItemStack getHeldProjectile(PlayerEntity player) {
        ItemStack held = player.getDisplayItemInHand();
        if (held == null) {
            return null;
        } else {
            Item item = held.getItem();
            if (item == Items.ENDER_PEARL
                    || item == Items.SNOWBALL
                    || item == Items.EGG
                    || item == Items.EXPERIENCE_BOTTLE) {
                return held;
            } else if (item == Items.POTION) {
                return PotionItem.isSplashPotion(held.getMetadata()) ? held : null;
            } else if (item instanceof BowItem) {
                return held;
            } else {
                return item == Items.FISHING_ROD ? held : null;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && mc.world != null) {
            this.renderItemTrajectories(e.partialTicks);
            PlayerEntity player = mc.player;
            float partialTicks = e.partialTicks;
            ItemStack heldStack = this.getHeldProjectile(player);
            if (!this.onlyWhenHolding.isToggled() || heldStack != null) {
                if (heldStack != null) {
                    Item item = heldStack.getItem();
                    Trajectories.TrajectoryProps props = this.getProjectileProperties(item, player, partialTicks);
                    if (props != null) {
                        double velocity = props.initialVelocity;
                        if (item == Items.SNOWBALL && this.isHypixelBedwars() && this.isBedbugSnowball(heldStack)) {
                            velocity = 1.75;
                        }

                        if (item != Items.BOW
                                || !this.disableUnchargedBow.isToggled()
                                || player.hasItemInUse() && !(velocity < 0.1)) {
                            float yaw = (float) Math.toRadians(player.yaw);
                            float pitch = (float) Math.toRadians(player.pitch);
                            double posX = player.prevX
                                    + (player.x - player.prevX) * partialTicks
                                    - MathHelper.cos(yaw) * 0.16F;
                            double posY = player.prevY
                                    + (player.y - player.prevY) * partialTicks
                                    + player.getEyeHeight()
                                    - 0.1;
                            double posZ = player.prevZ
                                    + (player.z - player.prevZ) * partialTicks
                                    - MathHelper.sin(yaw) * 0.16F;
                            double hiddenRenderDistance = mc.options.perspective == 0 ? 0.5 : 0.0;
                            double motX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
                            double motY = -MathHelper.sin(pitch);
                            double motZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
                            double len = Math.sqrt(motX * motX + motY * motY + motZ * motZ);
                            motX /= len;
                            motY /= len;
                            motZ /= len;
                            motX *= velocity;
                            motY *= velocity;
                            motZ *= velocity;
                            if (this.addPlayerVelocity.isToggled()) {
                                motX += player.velocityX;
                                motY += player.onGround ? 0.0 : player.velocityY;
                                motZ += player.velocityZ;
                            }

                            double[] mot = new double[] {motX, motY, motZ};
                            if (props.requiresInitialTickCorrection) {
                                Trajectories.FluidState fluidState = this.sampleFluidState(posX, posY, posZ, props);
                                this.applyWaterFlowAcceleration(fluidState, mot);
                                this.tickVelocity(props, fluidState, mot);
                                motX = mot[0];
                                motY = mot[1];
                                motZ = mot[2];
                            }

                            List<double[]> renderPoints = new ArrayList<>();
                            HitResult hitBlock = null;
                            Entity hitEntity = null;
                            Box hitEntityBox = null;
                            int hitType = 0;
                            Vec3d hitPos = null;
                            Direction hitFace = Direction.UP;
                            Vec3d terminalPos = null;
                            EntityRenderDispatcher rm = mc.getEntityRenderDispatcher();
                            int maxSteps = (int) this.maxTicks.getInput();
                            int SUB = 4;
                            double hw = props.hitboxRadius;

                            for (int i = 0; i < maxSteps; i++) {
                                Trajectories.FluidState fluidState = this.sampleFluidState(posX, posY, posZ, props);
                                mot[0] = motX;
                                mot[1] = motY;
                                mot[2] = motZ;
                                this.applyWaterFlowAcceleration(fluidState, mot);
                                motX = mot[0];
                                motY = mot[1];
                                motZ = mot[2];
                                double nextX = posX + motX;
                                double nextY = posY + motY;
                                double nextZ = posZ + motZ;
                                Vec3d start = new Vec3d(posX, posY, posZ);
                                Vec3d end = new Vec3d(nextX, nextY, nextZ);
                                Trajectories.BlockCollisionResult blockCollision =
                                        this.getNearestBlockCollision(start, end, props);
                                HitResult blockMop = blockCollision.hit;
                                double blockDistSq = blockCollision.distanceSq;
                                Vec3d clampedEnd = blockMop != null
                                        ? new Vec3d(blockMop.facePos.x, blockMop.facePos.y, blockMop.facePos.z)
                                        : end;
                                Box broadBox = new Box(posX - hw, posY - hw, posZ - hw, posX + hw, posY + hw, posZ + hw)
                                        .expanded(motX, motY, motZ)
                                        .grown(1.0, 1.0, 1.0);
                                List<Entity> candidates = mc.world.getEntities(mc.getCamera(), broadBox);
                                Entity bestEntity = null;
                                Vec3d bestHitVec = null;
                                Direction bestHitFace = null;
                                Box bestBox = null;
                                double bestDistSq = blockDistSq;

                                for (Entity en : candidates) {
                                    if (en instanceof LivingEntity
                                            && !(en instanceof ArmorStandEntity)
                                            && en.hasCollision()
                                            && ((LivingEntity) en).deathTicks == 0
                                            && (!(en instanceof PlayerEntity) || !AntiBot.isBot(en))) {
                                        Box entityBox = en.getShape();
                                        Box expandedEntityBox = this.expandEntityCollisionBox(entityBox);
                                        HitResult mop = expandedEntityBox.clip(start, clampedEnd);
                                        if (mop != null) {
                                            double dSq = start.squaredDistanceTo(mop.facePos);
                                            if (dSq + 1.0E-7 < bestDistSq) {
                                                bestDistSq = dSq;
                                                bestEntity = en;
                                                bestHitVec = mop.facePos;
                                                bestHitFace = mop.face;
                                                bestBox = expandedEntityBox;
                                            }
                                        }
                                    }
                                }

                                if (bestEntity != null) {
                                    double hitT =
                                            Math.sqrt(bestDistSq) / Math.sqrt(motX * motX + motY * motY + motZ * motZ);
                                    hitT = Math.max(0.0, Math.min(1.0, hitT));
                                    int subCount = (int) Math.ceil(hitT * 4.0);

                                    for (int s = 0; s < subCount; s++) {
                                        double t = s / 4.0;
                                        renderPoints.add(new double[] {
                                            posX + motX * t - rm.cameraX,
                                            posY + motY * t - rm.cameraY,
                                            posZ + motZ * t - rm.cameraZ
                                        });
                                    }

                                    renderPoints.add(new double[] {
                                        bestHitVec.x - rm.cameraX, bestHitVec.y - rm.cameraY, bestHitVec.z - rm.cameraZ
                                    });
                                    hitEntity = bestEntity;
                                    hitEntityBox = bestBox;
                                    hitType = 1;
                                    hitPos = bestHitVec;
                                    hitFace = bestHitFace;
                                    break;
                                }

                                if (blockMop != null) {
                                    Vec3d hitVec = blockMop.facePos;
                                    int side = blockMop.face.getId();
                                    hitType = side != 0 && side != 1 ? 2 : 3;
                                    hitPos = hitVec;
                                    hitFace = blockMop.face;
                                    double segLenSq = motX * motX + motY * motY + motZ * motZ;
                                    double hitDx = hitVec.x - posX;
                                    double hitDy = hitVec.y - posY;
                                    double hitDz = hitVec.z - posZ;
                                    double hitT = segLenSq > 0.0
                                            ? Math.sqrt((hitDx * hitDx + hitDy * hitDy + hitDz * hitDz) / segLenSq)
                                            : 0.0;
                                    hitT = Math.max(0.0, Math.min(1.0, hitT));
                                    int subCount = (int) Math.ceil(hitT * 4.0);

                                    for (int s = 0; s < subCount; s++) {
                                        double t = s / 4.0;
                                        renderPoints.add(new double[] {
                                            posX + motX * t - rm.cameraX,
                                            posY + motY * t - rm.cameraY,
                                            posZ + motZ * t - rm.cameraZ
                                        });
                                    }

                                    renderPoints.add(new double[] {
                                        hitVec.x - rm.cameraX, hitVec.y - rm.cameraY, hitVec.z - rm.cameraZ
                                    });
                                    hitBlock = blockMop;
                                    break;
                                }

                                for (int s = 0; s < 4; s++) {
                                    double t = s / 4.0;
                                    renderPoints.add(new double[] {
                                        posX + motX * t - rm.cameraX,
                                        posY + motY * t - rm.cameraY,
                                        posZ + motZ * t - rm.cameraZ
                                    });
                                }

                                posX = nextX;
                                posY = nextY;
                                posZ = nextZ;
                                terminalPos = new Vec3d(posX, posY, posZ);
                                mot[0] = motX;
                                mot[1] = motY;
                                mot[2] = motZ;
                                this.tickPostMoveVelocity(posX, posY, posZ, props, fluidState, mot);
                                motX = mot[0];
                                motY = mot[1];
                                motZ = mot[2];
                                if (posY < -64.0) {
                                    break;
                                }
                            }

                            if (hitPos == null
                                    && props.physicsModel == Trajectories.PhysicsModel.FISH_HOOK
                                    && terminalPos != null) {
                                hitPos = terminalPos;
                            }

                            int color;
                            switch (hitType) {
                                case 1:
                                    color = this.enemyColor.getColor();
                                    break;
                                case 2:
                                    color = this.wallColor.getColor();
                                    break;
                                case 3:
                                    color = this.groundColor.getColor();
                                    break;
                                default:
                                    color = this.defaultColor.getColor();
                            }

                            float r = (color >> 16 & 0xFF) / 255.0F;
                            float g = (color >> 8 & 0xFF) / 255.0F;
                            float b = (color & 0xFF) / 255.0F;
                            GL11.glPushMatrix();
                            GL11.glEnable(2848);
                            GL11.glBlendFunc(770, 771);
                            GL11.glEnable(3042);
                            GL11.glDisable(3553);
                            if (item == Items.BOW && this.enableBowDepth.isToggled()) {
                                GL11.glEnable(2929);
                            } else {
                                GL11.glDisable(2929);
                            }

                            GL11.glDepthMask(false);
                            float lineW = (float) this.lineThickness.getInput();
                            GL11.glColor4f(r, g, b, 1.0F);
                            GL11.glLineWidth(lineW);
                            GL11.glBegin(3);
                            boolean first = true;

                            for (double[] pt : this.clipRenderPoints(renderPoints, hiddenRenderDistance)) {
                                if (first && this.shortenLine.isToggled()) {
                                    first = false;
                                } else {
                                    first = false;
                                    GL11.glVertex3d(pt[0], pt[1], pt[2]);
                                }
                            }

                            GL11.glEnd();
                            if (hitEntity != null && this.highlightEntities.isToggled() && hitEntityBox != null) {
                                double ex = hitEntity.prevX + (hitEntity.x - hitEntity.prevX) * partialTicks;
                                double ey = hitEntity.prevY + (hitEntity.y - hitEntity.prevY) * partialTicks;
                                double ez = hitEntity.prevZ + (hitEntity.z - hitEntity.prevZ) * partialTicks;
                                Box renderBox = new Box(
                                        hitEntityBox.minX - hitEntity.x + ex,
                                        hitEntityBox.minY - hitEntity.y + ey,
                                        hitEntityBox.minZ - hitEntity.z + ez,
                                        hitEntityBox.maxX - hitEntity.x + ex,
                                        hitEntityBox.maxY - hitEntity.y + ey,
                                        hitEntityBox.maxZ - hitEntity.z + ez);
                                GL11.glColor4f(r, g, b, 1.0F);
                                RenderUtils.drawOutlinedBox(renderBox, rm.cameraX, rm.cameraY, rm.cameraZ);
                            } else if (hitBlock != null && !this.showLanding.isToggled()) {
                                BlockPos bpos = hitBlock.getPos();
                                Box selBox = BlockUtils.getBlockSelectionBox(bpos);
                                if (selBox != null) {
                                    GL11.glColor4f(r, g, b, 1.0F);
                                    RenderUtils.drawOutlinedBox(selBox, rm.cameraX, rm.cameraY, rm.cameraZ);
                                }
                            }

                            if (this.showLanding.isToggled() && hitPos != null) {
                                this.renderLandingIndicator(
                                        hitPos, hitFace, rm.cameraX, rm.cameraY, rm.cameraZ, r, g, b, hitType);
                            }

                            GL11.glDisable(3042);
                            GL11.glEnable(3553);
                            GL11.glEnable(2929);
                            GL11.glDepthMask(true);
                            GL11.glDisable(2848);
                            GL11.glPopMatrix();
                        }
                    }
                }
            }
        }
    }

    private void renderLandingIndicator(
            Vec3d hitPos,
            Direction hitFace,
            double camX,
            double camY,
            double camZ,
            float r,
            float g,
            float b,
            int hitType) {
        if (this.target.getInput() == 0.0) {
            ProjectileTrajectoryRenderer.renderCrossIndicator(
                    hitPos, hitFace, camX, camY, camZ, r, g, b, this.autoScale.isToggled(), (float)
                            this.lineThickness.getInput());
        } else {
            double boxSize = hitType == 1 ? 0.4 : 0.2;
            Box worldBox = new Box(
                    hitPos.x - boxSize,
                    hitPos.y - boxSize,
                    hitPos.z - boxSize,
                    hitPos.x + boxSize,
                    hitPos.y + boxSize,
                    hitPos.z + boxSize);
            GL11.glLineWidth(2.0F);
            GL11.glColor4f(r, g, b, 1.0F);
            RenderUtils.drawOutlinedBox(worldBox, camX, camY, camZ);
            Box renderBox = worldBox.moved(-camX, -camY, -camZ);
            GL11.glColor4f(r, g, b, 0.3F);
            RenderUtils.drawBoundingBox(renderBox, r, g, b, 0.3F);
            double x = hitPos.x - camX;
            double y = hitPos.y - camY;
            double z = hitPos.z - camZ;
            double crossSize = hitType == 1 ? 0.5 : 0.3;
            GL11.glLineWidth(1.5F);
            GL11.glColor4f(r, g, b, 1.0F);
            GL11.glBegin(1);
            GL11.glVertex3d(x - crossSize, y, z);
            GL11.glVertex3d(x + crossSize, y, z);
            GL11.glVertex3d(x, y - crossSize, z);
            GL11.glVertex3d(x, y + crossSize, z);
            GL11.glVertex3d(x, y, z - crossSize);
            GL11.glVertex3d(x, y, z + crossSize);
            GL11.glEnd();
        }
    }

    private static final class BlockCollisionResult {
        final HitResult hit;
        final double distanceSq;

        BlockCollisionResult(HitResult hit, double distanceSq) {
            this.hit = hit;
            this.distanceSq = distanceSq;
        }
    }

    private static final class FluidState {
        final boolean inWater;
        final boolean inLava;
        final Vec3d flowDirection;

        FluidState(boolean inWater, boolean inLava, Vec3d flowDirection) {
            this.inWater = inWater;
            this.inLava = inLava;
            this.flowDirection = flowDirection;
        }
    }

    private enum PhysicsModel {
        ARROW,
        THROWABLE,
        FISH_HOOK;
    }

    private static final class TrajectoryProps {
        final Trajectories.PhysicsModel physicsModel;
        final double gravity;
        final double drag;
        final double dragInWater;
        final double hitboxRadius;
        final double width;
        final double height;
        final double initialVelocity;
        final boolean requiresInitialTickCorrection;
        final boolean ignoreBlockWithoutBoundingBox;

        TrajectoryProps(
                Trajectories.PhysicsModel physicsModel,
                double gravity,
                double drag,
                double dragInWater,
                double hitboxRadius,
                double width,
                double height,
                double initialVelocity,
                boolean requiresInitialTickCorrection,
                boolean ignoreBlockWithoutBoundingBox) {
            this.physicsModel = physicsModel;
            this.gravity = gravity;
            this.drag = drag;
            this.dragInWater = dragInWater;
            this.hitboxRadius = hitboxRadius;
            this.width = width;
            this.height = height;
            this.initialVelocity = initialVelocity;
            this.requiresInitialTickCorrection = requiresInitialTickCorrection;
            this.ignoreBlockWithoutBoundingBox = ignoreBlockWithoutBoundingBox;
        }
    }
}
