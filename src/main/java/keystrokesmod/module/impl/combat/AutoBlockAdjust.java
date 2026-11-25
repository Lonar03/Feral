// Ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: module/impl/combat/autoblock/* (AutoBlockModule, FullAutoBlockMode, PredictiveAutoBlockMode).
// The outbound "Lag" part is left out (patched on Hypixel, flags/bans).
package keystrokesmod.module.impl.combat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.monster.MonsterEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Mouse;

public class AutoBlockAdjust extends Module {
    private static final String[] MODES = new String[] {"Full", "Predictive"};
    private static final String[] PING_MODES = new String[] {"Automatic", "Static"};

    private final SliderSetting mode;
    private final SliderSetting range;
    private final ButtonSetting targetPlayers;
    private final ButtonSetting targetHostile;
    private final ButtonSetting targetTeammates;
    private final ButtonSetting targetInvisibles;
    private final ButtonSetting projFireballs;
    private final ButtonSetting projArrows;
    private final ButtonSetting projPearls;
    private final SliderSetting pingMode;
    private final SliderSetting staticPing;
    private final SliderSetting maxHoldTime;
    private final ButtonSetting fakeBlockVisual;
    private final ButtonSetting blockAgainImmediately;
    private final ButtonSetting holdAttack;
    private final ButtonSetting holdBlock;
    private final ButtonSetting onlyWhenHurt;

    // entity id -> last hurt (S19 opcode 2) / last time we attacked it, in ms. Written from the netty thread.
    private final Map<Integer, Long> lastHurt = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastAttack = new ConcurrentHashMap<>();

    // Predictive
    private boolean stateDefinition;
    private long blockStart;
    private int nextBlockTick;
    // Full
    private Integer fullHoldTicks;
    // true while this module owns the use key (and the physical right mouse button is ignored)
    private boolean controlling;
    private boolean pressedUse;
    private boolean fakeBlocking;

    public AutoBlockAdjust() {
        super("Auto Block (Adjust)", Module.category.combat);
        this.registerSetting(new DescriptionSetting("Adjust's autoblock, no lag (patched on Hypixel)"));
        this.registerSetting(this.mode = new SliderSetting("Mode", 1, MODES));
        this.registerSetting(this.range = new SliderSetting("Range", " blocks", 6.0, 3.0, 12.0, 0.1));
        GroupSetting targets = new GroupSetting("Targets");
        this.registerSetting(targets);
        this.registerSetting(this.targetPlayers = new ButtonSetting(targets, "Players", true));
        this.registerSetting(this.targetHostile = new ButtonSetting(targets, "Hostile", false));
        this.registerSetting(this.targetTeammates = new ButtonSetting(targets, "Teammates", false));
        this.registerSetting(this.targetInvisibles = new ButtonSetting(targets, "Invisibles", true));
        GroupSetting projectiles = new GroupSetting("Projectiles");
        this.registerSetting(projectiles);
        this.registerSetting(this.projFireballs = new ButtonSetting(projectiles, "Fireballs", true));
        this.registerSetting(this.projArrows = new ButtonSetting(projectiles, "Arrows", true));
        this.registerSetting(this.projPearls = new ButtonSetting(projectiles, "Ender pearls", true));
        GroupSetting ping = new GroupSetting("Ping");
        this.registerSetting(ping);
        this.registerSetting(this.pingMode = new SliderSetting(ping, "Mode", 0, PING_MODES));
        this.registerSetting(this.staticPing = new SliderSetting(ping, "Ping", "ms", 50.0, 0.0, 1000.0, 5.0));
        this.registerSetting(this.maxHoldTime = new SliderSetting("Max hold time", "ms", 50.0, 50.0, 500.0, 1.0));
        this.registerSetting(this.fakeBlockVisual = new ButtonSetting("Fake block visual", false));
        this.registerSetting(this.blockAgainImmediately = new ButtonSetting("Block again immediately", false));
        this.registerSetting(this.holdAttack = new ButtonSetting("Hold attack", true));
        this.registerSetting(this.holdBlock = new ButtonSetting("Hold block", true));
        this.registerSetting(this.onlyWhenHurt = new ButtonSetting("Only when hurt", false));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return MODES[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean predictive = this.isPredictive();
        this.maxHoldTime.setVisible(predictive, this);
        this.fakeBlockVisual.setVisible(predictive, this);
        this.blockAgainImmediately.setVisible(predictive, this);
        this.holdAttack.setVisible(predictive, this);
        this.holdBlock.setVisible(predictive, this);
        this.onlyWhenHurt.setVisible(predictive, this);
        this.staticPing.setVisible(this.pingMode.getInput() == 1.0, this);
    }

    @Override
    public void onEnable() {
        this.reset();
        this.lastHurt.clear();
        this.lastAttack.clear();
    }

    @Override
    public void onDisable() {
        this.reset();
        this.fakeBlocking = false;
        if (Utils.nullCheck()) {
            ReflectionUtils.setItemInUse(false);
        }
    }

    private boolean isPredictive() {
        return this.mode.getInput() == 1.0;
    }

    private void reset() {
        this.stateDefinition = false;
        this.blockStart = 0L;
        this.nextBlockTick = 0;
        this.fullHoldTicks = null;
        this.releaseControl();
    }

    // ---------------------------------------------------------------- packets

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof EntityEventS2CPacket) {
            EntityEventS2CPacket packet = (EntityEventS2CPacket) e.getPacket();
            if (packet.getEvent() == 2) {
                Entity entity = mc.world == null ? null : packet.getEntity(mc.world);
                if (entity != null) {
                    this.lastHurt.put(entity.getNetworkId(), System.currentTimeMillis());
                }
            }
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) e.getPacket();
            if (packet.getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK && mc.world != null) {
                Entity target = packet.getInteractTarget(mc.world);
                if (target != null && this.isHurtWindowReady(target.getNetworkId(), 0, this.getPing())) {
                    this.lastAttack.put(target.getNetworkId(), System.currentTimeMillis());
                }
            }
        }
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreInteract(PrePlayerInteractEvent e) {
        this.fakeBlocking = false;
        if (!Utils.nullCheck() || mc.player.removed || mc.screen != null) {
            this.reset();
            return;
        }
        if (this.isPredictive()) {
            this.tickPredictive();
        } else {
            this.tickFull();
        }
    }

    private void tickPredictive() {
        if (!this.hasRequiredButtons() || !this.hasSword()) {
            this.stateDefinition = false;
            this.releaseControl();
            return;
        }
        if (!this.shouldBlock()) {
            this.stateDefinition = false;
            this.releaseControl();
            return;
        }
        // From here on the module decides; the physical right mouse button is ignored.
        this.fakeBlocking = this.fakeBlockVisual.isToggled();
        boolean wantUse = false;
        int ping = this.getPing();
        int self = mc.player.getNetworkId();
        long now = System.currentTimeMillis();
        if (now - this.getLastHurt(self) < 500L && !this.isHurtWindowReady(self, 0, ping)) {
            // We just got hit and are still invulnerable: don't block, swing.
            this.nextBlockTick = 0;
            this.stateDefinition = false;
            this.applyUse(false);
            return;
        }
        if (this.shouldStartBlock(self, ping)) {
            if (!this.stateDefinition
                    && (this.blockAgainImmediately.isToggled() || mc.player.ticks >= this.nextBlockTick)) {
                this.stateDefinition = true;
                this.nextBlockTick = mc.player.ticks + 10;
                this.blockStart = now;
            }
            if (this.stateDefinition) {
                if (now - this.blockStart < (long) this.maxHoldTime.getInput()) {
                    wantUse = true;
                } else {
                    this.stateDefinition = false;
                    // Adjust's "Lag" (holding packets to block longer) is left out: patched on Hypixel
                }
            }
        }
        this.applyUse(wantUse);
    }

    private void tickFull() {
        if (!this.shouldBlock()) {
            if (this.fullHoldTicks != null && this.controlling) {
                // keep the block for a few random ticks after the threat is gone
                if (--this.fullHoldTicks <= 0) {
                    this.fullHoldTicks = null;
                    this.releaseControl();
                } else {
                    this.applyUse(this.hasSword());
                }
            } else {
                this.fullHoldTicks = null;
                this.releaseControl();
            }
            return;
        }
        this.fullHoldTicks = ThreadLocalRandom.current().nextInt(2, 4);
        this.applyUse(true);
    }

    private boolean shouldStartBlock(int self, int ping) {
        long sinceHurt = System.currentTimeMillis() - this.getLastHurt(self);
        if (!this.onlyWhenHurt.isToggled()) {
            return sinceHurt >= 500L || this.isHurtWindowReady(self, 0, ping);
        }
        return sinceHurt < 500L && this.isHurtWindowReady(self, 0, ping) && this.hasThreat(true);
    }

    private boolean isHurtWindowReady(int entityId, int ticks, int pingMs) {
        int latency = (int) (pingMs * 1.5F);
        long now = System.currentTimeMillis();
        long hurtDelay = 50L * (9 - ticks) - latency;
        if (entityId == (mc.player == null ? -1 : mc.player.getNetworkId())) {
            return hurtDelay <= now - this.getLastHurt(entityId);
        }
        Long attack = this.lastAttack.get(entityId);
        return 50L * (10 - ticks) <= now - (attack == null ? 0L : attack)
                && hurtDelay <= now - this.getLastHurt(entityId);
    }

    private long getLastHurt(int entityId) {
        Long hurt = this.lastHurt.get(entityId);
        return hurt == null ? 0L : hurt;
    }

    // ---------------------------------------------------------------- use key

    private void applyUse(boolean down) {
        KeyBinding use = mc.options.useKey;
        int keyCode = use.getKeyCode();
        this.controlling = true;
        if (down) {
            KeyBinding.set(keyCode, true);
            if (!this.pressedUse && !mc.player.hasItemInUse()) {
                KeyBinding.click(keyCode);
            }
            this.pressedUse = true;
        } else {
            KeyBinding.set(keyCode, false);
            // swallow queued physical clicks so vanilla doesn't start blocking on its own
            while (use.consumeClick()) {}
            this.pressedUse = false;
        }
    }

    private void releaseControl() {
        if (!this.controlling) {
            return;
        }
        this.controlling = false;
        this.pressedUse = false;
        int keyCode = mc.options.useKey.getKeyCode();
        boolean physical = mc.screen == null && Utils.isBindDown(mc.options.useKey);
        KeyBinding.set(keyCode, physical);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !Utils.nullCheck() || mc.screen != null) {
            return;
        }
        if (this.fakeBlocking && this.hasSword() && !mc.player.hasItemInUse()) {
            ReflectionUtils.setItemInUse(true);
        }
    }

    // ---------------------------------------------------------------- checks

    private boolean hasRequiredButtons() {
        if (this.holdAttack.isToggled() && !(Mouse.isCreated() && Mouse.isButtonDown(0))) {
            return false;
        }
        return !this.holdBlock.isToggled() || Mouse.isCreated() && Mouse.isButtonDown(1);
    }

    private boolean hasSword() {
        ItemStack stack = mc.player.getDisplayItemInHand();
        return stack != null && stack.getItem() != null && stack.getItem().getClass() == SwordItem.class;
    }

    private boolean shouldBlock() {
        HitResult hit = mc.crosshairTarget;
        if (hit != null) {
            if (mc.interactionManager.isMiningBlock()) {
                return false;
            }
            if (hit.type == HitResult.Type.ENTITY
                    && (hit.entity instanceof VillagerEntity || hit.entity instanceof ArmorStandEntity)) {
                return false;
            }
            if (hit.type == HitResult.Type.BLOCK && !mc.player.isSneaking() && BlockUtils.isInteractable(hit)) {
                return false;
            }
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            return false;
        }
        // Adjust: WorldUtil.isLookingAtBlock, holding attack on a block means you want to break it
        if (mc.options.attackKey.isPressed() && hit != null && hit.type == HitResult.Type.BLOCK) {
            return false;
        }
        return this.hasSword() && this.hasThreat(false);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || entity.removed || entity.deathTicks > 0 || entity.getHealth() <= 0.0F) {
            return false;
        }
        if (entity.ticks < 3 || entity instanceof ArmorStandEntity) {
            return false;
        }
        if (entity.isInvisible() && !this.targetInvisibles.isToggled()) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            if (!this.targetPlayers.isToggled() || AntiBot.isBot(player) || Utils.isFriended(player)) {
                return false;
            }
            return this.targetTeammates.isToggled() || !Utils.isTeammate(player);
        }
        boolean hostile =
                entity instanceof MonsterEntity || entity instanceof IronGolemEntity || entity instanceof SlimeEntity;
        return hostile && this.targetHostile.isToggled();
    }

    private static boolean isFacingPlayer(Entity target) {
        double dx = mc.player.x - target.x;
        double dz = mc.player.z - target.z;
        float yawToPlayer = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(MathHelper.wrapDegrees(target.getHeadYaw() - yawToPlayer)) <= 90.0F;
    }

    private static Vec3d closestPoint(Vec3d point, Box box) {
        return new Vec3d(
                MathHelper.clamp(point.x, box.minX, box.maxX),
                MathHelper.clamp(point.y, box.minY, box.maxY),
                MathHelper.clamp(point.z, box.minZ, box.maxZ));
    }

    private boolean hasThreat(boolean requireFacing) {
        Vec3d feet = mc.player.getCommandSourcePos();
        double rangeSq = this.range.getInput() * this.range.getInput();
        for (Entity entity : mc.world.entities) {
            if (!(entity instanceof LivingEntity) || entity.ticks < 3 || !this.isValidTarget((LivingEntity) entity)) {
                continue;
            }
            if (requireFacing && !isFacingPlayer(entity)) {
                continue;
            }
            double distSq = feet.squaredDistanceTo(closestPoint(feet, entity.getShape()));
            double allowed = mc.player.canSee(entity) ? rangeSq : rangeSq * 0.5;
            if (distSq <= allowed) {
                return true;
            }
        }
        if (requireFacing) {
            return false;
        }
        for (Entity entity : mc.world.entities) {
            if (entity instanceof ProjectileEntity && this.projFireballs.isToggled()) {
                if (this.isFireballDangerous((ProjectileEntity) entity)) {
                    return true;
                }
            } else if (entity instanceof ArrowEntity && this.projArrows.isToggled()) {
                if (this.isArrowDangerous((ArrowEntity) entity)) {
                    return true;
                }
            } else if (entity instanceof EnderPearlEntity && this.projPearls.isToggled()) {
                if (this.isPearlDangerous((EnderPearlEntity) entity, feet, rangeSq)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Adjust: dangerous + impact distance factor <= 0.8. Here: heading at us and its path passes within ~3 blocks.
    private boolean isFireballDangerous(ProjectileEntity fireball) {
        if (fireball.shooter == mc.player) {
            return false;
        }
        double mx = fireball.x - fireball.lastX;
        double my = fireball.y - fireball.lastY;
        double mz = fireball.z - fireball.lastZ;
        double speed = Math.sqrt(mx * mx + my * my + mz * mz);
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        double tx = eyes.x - fireball.x;
        double ty = eyes.y - fireball.y;
        double tz = eyes.z - fireball.z;
        double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (dist > this.range.getInput() * 2.0) {
            return false;
        }
        if (speed < 1.0E-3) {
            return dist <= 3.0;
        }
        double along = (tx * mx + ty * my + tz * mz) / speed;
        if (along <= 0.0) {
            return dist <= 2.0;
        }
        double missSq = dist * dist - along * along;
        return missSq <= 9.0 && along <= this.range.getInput() * 0.8 * 2.0;
    }

    private boolean isArrowDangerous(ArrowEntity arrow) {
        if (arrow.shooter == mc.player || arrow.ticks < 1) {
            return false;
        }
        double mx = arrow.x - arrow.lastX;
        double my = arrow.y - arrow.lastY;
        double mz = arrow.z - arrow.lastZ;
        if (Math.abs(mx) + Math.abs(my) + Math.abs(mz) < 1.0E-3) {
            return false; // in ground
        }
        double motionYaw = Math.toDegrees(Math.atan2(-mx, mz));
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        double towardArrow = Math.toDegrees(Math.atan2(-(arrow.x - eyes.x), arrow.z - eyes.z));
        // the arrow flies toward us when its heading is opposite to the direction from us to it
        double delta = Math.abs(MathHelper.wrapDegrees(motionYaw - towardArrow - 180.0));
        if (Math.hypot(mx, mz) > 0.2 || my >= 0.0) {
            boolean still = Math.hypot(mc.player.velocityX, mc.player.velocityZ) <= 0.08;
            if (delta > (still ? 25.0 : 55.0)) {
                return false;
            }
        } else if (Math.hypot(arrow.x - mc.player.x, arrow.z - mc.player.z) > 2.0) {
            return false;
        }
        double distance = this.getPing() >= 131 ? 20.5 : 18.5;
        return mc.player.squaredDistanceTo(arrow) <= distance * distance;
    }

    // Simulates the pearl until it hits a block and checks if it lands within range.
    private boolean isPearlDangerous(EnderPearlEntity pearl, Vec3d feet, double rangeSq) {
        if (pearl.getThrower() == mc.player) {
            return false;
        }
        double x = pearl.x;
        double y = pearl.y;
        double z = pearl.z;
        double mx = pearl.velocityX;
        double my = pearl.velocityY;
        double mz = pearl.velocityZ;
        for (int i = 0; i < 100; i++) {
            Vec3d from = new Vec3d(x, y, z);
            Vec3d to = new Vec3d(x + mx, y + my, z + mz);
            HitResult hit = mc.world.rayTrace(from, to);
            if (hit != null && hit.facePos != null) {
                return feet.squaredDistanceTo(hit.facePos) <= rangeSq;
            }
            x += mx;
            y += my;
            z += mz;
            mx *= 0.99;
            my *= 0.99;
            mz *= 0.99;
            my -= 0.03;
            if (y < 0.0) {
                return false;
            }
        }
        return false;
    }

    private int getPing() {
        if (this.pingMode.getInput() == 1.0) {
            return (int) this.staticPing.getInput();
        }
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return 0;
        }
        PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
        return info == null ? 0 : Math.max(0, info.getPing());
    }
}
