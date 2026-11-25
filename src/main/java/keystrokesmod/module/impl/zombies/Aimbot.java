package keystrokesmod.module.impl.zombies;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import keystrokesmod.Feral;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.helper.PlayerLeadTracker;
import keystrokesmod.helper.zombies.ZombiesSpawnHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.MinecraftForge;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.SmoothAim;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.MobType;
import keystrokesmod.utility.zombies.ZombiesMap;
import keystrokesmod.utility.zombies.ZombiesMobs;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

public class Aimbot extends Module {
    public static ButtonSetting onlyFire;
    public static ButtonSetting pup;
    public static ButtonSetting smartPup;
    public static ButtonSetting insta;
    public static ButtonSetting autoInsta;
    public static ButtonSetting bbr1;
    public static ButtonSetting bossesFirst;
    /** PvP mode: aim only at real players (AntiBot-filtered) with packet-based movement prediction. */
    public static ButtonSetting pvp;

    public static SliderSetting pvpLead;
    public static ButtonSetting attemptHeadshots;
    /** Shoot through walls when the shot line first enters a pass-through block (slab, bars, sign...). */
    public static ButtonSetting wallshoot;

    public static SliderSetting predict;
    public static SliderSetting yPredict;
    public static SliderSetting crits;
    public static SliderSetting witherHeadOffset;
    private static final double HEAD_FRAC_BASE = 0.9;
    private static final double HEAD_FRAC_SLOPE = 0.2;
    private static final double CRITS_MIN = -0.2;
    private static final double CRITS_MAX = 0.5;
    private static final double CRITS_EXT_MIN = -2.0;
    private static final double CRITS_EXT_MAX = 2.0;
    private static final double HEAD_FRAC_MIN = 0.05;
    private static final double Y_EPS = 0.01;
    /** Bigger than every Priority bonus, so a visible boss always wins with "Bosses first" on. */
    private static final double BOSS_PRIORITY_BONUS = 10000.0;
    /** Between the biggest Priority bonus (3000) and BOSS_PRIORITY_BONUS. */
    private static final double POWERUP_PRIORITY_BONUS = 5000.0;
    /** Bigger than every bonus combined, so a powerup mob loses to any other visible mob. */
    private static final double POWERUP_LAST_PENALTY = 100000.0;
    /** Half the size of the wither's 8px middle head, in blocks. */
    private static final double WITHER_HEAD_HALF = 0.25;

    private static final double CHEST_PRIORITY_FRAC = 0.76;
    private static final double ABOVE_PLAYER_EPS = 0.25;
    /**
     * Aim height (% of the mob's height) for mobs that are close and above you, where the normal
     * head aim misses most. Also skips the bad-headshot "raise" step for them.
     */
    public static SliderSetting closeAimHeight;
    /** Upper limit for the head fraction (Crits can't push the aim point past this). */
    private static final double HEAD_FRAC_MAX = 2.0;
    /** "Close and above": within this many blocks on both X and Z... */
    private static final double CLOSE_XZ = 3.0;
    /** ...and standing at least this many blocks higher than you. */
    private static final double CLOSE_MIN_DY = 1.0;

    private static final long CHAT_INSTA_AUTO_DISABLE_MS = 10000L;
    private static final long BBR1_DELAY_MS = 18000L;
    private static final long BBR1_WINDOW_MS = 10000L;
    private final PlayerLeadTracker playerLeads = new PlayerLeadTracker();
    private boolean playerLeadsActive;
    private Integer currentTargetId = null;
    private Integer lastRenderedTargetId = null;
    /** Insta flick: mobs we fired at, presumed dead (Insta Kill) for this many more ticks. */
    private final Map<Integer, Integer> presumedDead = new HashMap<>();

    private static final int PRESUMED_DEAD_TICKS = 10;
    private boolean shotFired = false;
    /** Last seen gun state per hotbar slot, for shot detection. */
    private final Item[] slotItem = new Item[9];

    private final int[] slotAmmo = new int[9];
    private final boolean[] slotReloading = new boolean[9];
    private static final Map<Integer, Double> stableYBase = new HashMap<>();
    /** Guns are hitscan; anything a shot line passes through within this range counts. */
    private static final double SHOT_RANGE = 64.0;

    private final List<Aimbot.BadHsBox> badHsBoxes = new ArrayList<>();
    private int scanTargetId = -1;
    private boolean avoidBadHs = false;
    private static volatile long instaAutoDisableAtMs = -1L;
    private static volatile long bbr1EnableAtMs = -1L;
    private static volatile long bbr1DisableAtMs = -1L;
    private static boolean CHAT_HOOK_REGISTERED = false;
    private static volatile boolean independentInstaState = false;
    private static volatile long independentInstaDisableTime = -1L;
    private static volatile boolean critsOverrideActive = false;
    private static volatile double critsOverrideValue = 0.0;
    private static volatile boolean predictOverrideActive = false;
    private static volatile double predictOverrideValue = 0.0;
    private static volatile boolean yPredictOverrideActive = false;
    private static volatile double yPredictOverrideValue = 0.0;
    private static double lastGuiCritsValue = 0.0;
    private static double lastGuiPredictValue = 0.0;
    private static double lastGuiYPredictValue = 0.0;
    public static SliderSetting priority;
    /** Where powerup mobs rank: Normal (no change), First or Last. */
    public static SliderSetting powerupPriority;

    public static SliderSetting aimSpeed;
    private final SmoothAim smoothAim = new SmoothAim();
    private static int guiCritsLockTicks = 0;
    private static int guiPredictLockTicks = 0;
    private static int guiYPredictLockTicks = 0;
    private static final ThreadLocal<DecimalFormat> PCT_FMT3 = new ThreadLocal<DecimalFormat>() {
        protected DecimalFormat initialValue() {
            return new DecimalFormat("0.###");
        }
    };

    private static double getStableBaseY(LivingEntity e) {
        if (e instanceof SlimeEntity) {
            double current = e.y;
            int id = e.getNetworkId();
            Double prev = stableYBase.get(id);
            if (prev == null) {
                stableYBase.put(id, current);
                return current;
            } else {
                double verticalThreshold = 1.5;
                if (Math.abs(current - prev) > verticalThreshold) {
                    stableYBase.put(id, current);
                    return current;
                } else {
                    return prev;
                }
            }
        } else {
            double current = e.y;
            int id = e.getNetworkId();
            Double prev = stableYBase.get(id);
            if (prev == null || current < prev - 0.3) {
                stableYBase.put(id, current);
                return current;
            } else if (current > prev + 0.5) {
                stableYBase.put(id, current);
                return current;
            } else {
                return prev;
            }
        }
    }

    public Aimbot() {
        super("Aimbot", Module.category.zombies);
        this.registerSetting(onlyFire = new ButtonSetting("Hold RMB", true, "OnlyFire"));
        this.registerSetting(pup = new ButtonSetting("Baby wolf", false, "Pup"));
        this.registerSetting(smartPup = new ButtonSetting("Wolf BB only", false, "SmartPup"));
        // Internal state set by Insta kill flick / BBR1, not shown in the GUI or saved.
        insta = new ButtonSetting("Insta", false);
        this.registerSetting(autoInsta = new ButtonSetting("Insta kill flick", true, "AutoInsta"));
        this.registerSetting(bbr1 = new ButtonSetting("BBR1", false));
        this.registerSetting(bossesFirst = new ButtonSetting("Bosses first", false));
        this.registerSetting(pvp = new ButtonSetting("PvP", false, "Target players"));
        this.registerSetting(pvpLead = new SliderSetting("PvP extra lead", " tick", 0.0, -2.0, 3.0, 0.1));
        this.registerSetting(attemptHeadshots = new ButtonSetting("Attempt headshots", false));
        this.registerSetting(wallshoot = new ButtonSetting("Wallshoot", true));
        this.registerSetting(
                priority = new SliderSetting(
                        "Priority", Aimbot.PriorityMode.NONE.ordinal(), Utils.enumNames(Aimbot.PriorityMode.class)));
        this.registerSetting(
                powerupPriority = new SliderSetting(
                        "Powerup mobs",
                        Aimbot.PowerupMode.NORMAL.ordinal(),
                        Utils.enumNames(Aimbot.PowerupMode.class)));
        this.registerSetting(aimSpeed = new SliderSetting("Aim speed", 10.0, 1.0, 20.0, 1.0));
        this.registerSetting(predict = new SliderSetting("Predict", " tick", 4.0, 0.0, 10.0, 0.1));
        this.registerSetting(yPredict = new SliderSetting("YPredict", " tick", 4.0, 0.0, 10.0, 0.1));
        this.registerSetting(crits = new SliderSetting("Crits height", "", 0.1, -0.2, 0.5, 0.01, "Crits"));
        this.registerSetting(closeAimHeight = new SliderSetting("Close aim height", "%", 75.0, 40.0, 100.0, 1.0));
        this.registerSetting(witherHeadOffset = new SliderSetting("Wither head Y", 0.55, -0.5, 1.0, 0.05));
        lastGuiCritsValue = crits.getInput();
        lastGuiPredictValue = predict.getInput();
        lastGuiYPredictValue = yPredict.getInput();
        if (!CHAT_HOOK_REGISTERED) {
            MinecraftForge.EVENT_BUS.register(new Aimbot.GlobalChatHookIncoming());
            CHAT_HOOK_REGISTERED = true;
        }
    }

    @Override
    public void guiUpdate() {
        boolean mobs = !pvp.isToggled();
        pup.setVisible(mobs, this);
        smartPup.setVisible(mobs, this);
        autoInsta.setVisible(mobs, this);
        bbr1.setVisible(mobs, this);
        bossesFirst.setVisible(mobs, this);
        priority.setVisible(mobs, this);
        powerupPriority.setVisible(mobs, this);
        predict.setVisible(mobs, this);
        yPredict.setVisible(mobs, this);
        witherHeadOffset.setVisible(mobs, this);
        closeAimHeight.setVisible(mobs, this);
        pvpLead.setVisible(!mobs, this);
        attemptHeadshots.setVisible(!mobs, this);
        crits.setVisible(mobs || attemptHeadshots.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.playerLeads.reset();
        this.playerLeadsActive = false;
        this.badHsBoxes.clear();
        ZombiesMobs.setBadHsLine(null);
    }

    private static String fmt3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String fmtPct3(double pct) {
        return PCT_FMT3.get().format(pct);
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (pvp.isToggled() && !e.isCanceled()) {
            this.playerLeads.onPacket(e.getPacket());
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent e) {
        if (e.phase == Phase.END) {
            if (pvp.isToggled()) {
                this.playerLeads.tick();
                this.playerLeadsActive = true;
            } else if (this.playerLeadsActive) {
                this.playerLeads.reset();
                this.playerLeadsActive = false;
            }
            if (mc != null && mc.world != null) {
                if (crits != null) {
                    double gv = crits.getInput();
                    if (guiCritsLockTicks > 0) {
                        guiCritsLockTicks--;
                        lastGuiCritsValue = gv;
                    } else if (Math.abs(gv - lastGuiCritsValue) > 1.0E-12) {
                        critsOverrideActive = false;
                        critsOverrideValue = 0.0;
                        lastGuiCritsValue = gv;
                    }
                }

                if (predict != null) {
                    double gv = predict.getInput();
                    if (guiPredictLockTicks > 0) {
                        guiPredictLockTicks--;
                        lastGuiPredictValue = gv;
                    } else if (Math.abs(gv - lastGuiPredictValue) > 1.0E-12) {
                        predictOverrideActive = false;
                        predictOverrideValue = 0.0;
                        lastGuiPredictValue = gv;
                    }
                }

                if (yPredict != null) {
                    double gv = yPredict.getInput();
                    if (guiYPredictLockTicks > 0) {
                        guiYPredictLockTicks--;
                        lastGuiYPredictValue = gv;
                    } else if (Math.abs(gv - lastGuiYPredictValue) > 1.0E-12) {
                        yPredictOverrideActive = false;
                        yPredictOverrideValue = 0.0;
                        lastGuiYPredictValue = gv;
                    }
                }

                if (!this.presumedDead.isEmpty()) {
                    Iterator<Entry<Integer, Integer>> it =
                            this.presumedDead.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<Integer, Integer> en = it.next();
                        int left = en.getValue() - 1;
                        if (left <= 0) {
                            it.remove();
                        } else {
                            en.setValue(left);
                        }
                    }
                }

                if (!this.isShotFlickActive()) {
                    this.currentTargetId = null;
                    this.presumedDead.clear();
                }
            }
        }
    }

    private static void setGuiCritsSlider(double value) {
        if (trySetSlider(crits, value)) {
            guiCritsLockTicks = 2;
            lastGuiCritsValue = value;
        }
    }

    /**
     * Insta mode: one shot kills, so stay on a mob until the gun fires, then treat it as dead and
     * snap to the next one instead of waiting for it to be removed from the world.
     */
    private boolean isShotFlickActive() {
        return insta != null && insta.isToggled();
    }

    /**
     * Watches every gun's clip on the hotbar. In Zombies the stack size is the ammo left in the clip,
     * so a shot is the count dropping. The last bullet can't show as 0, so it shows as the durability
     * bar emptying to start the reload. All slots are watched, not just the held one, because with
     * Quick Switch the ammo update for a shot often arrives after we've already swapped away.
     */
    private void pollGunShot() {
        ItemStack[] inv = mc.player.inventory.items;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv[i];
            if (st == null || !st.isDamageable()) {
                this.slotItem[i] = null;
                continue;
            }
            int ammo = st.size;
            boolean reloading = st.isDamaged();
            if (st.getItem() == this.slotItem[i]
                    && (ammo < this.slotAmmo[i] || (reloading && !this.slotReloading[i]))) {
                this.shotFired = true;
            }
            this.slotItem[i] = st.getItem();
            this.slotAmmo[i] = ammo;
            this.slotReloading[i] = reloading;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!onlyFire.isToggled() || mc.options.useKey.isPressed()) {
            if (mc.player != null && mc.world != null) {
                this.pollGunShot();
                Aimbot.CandidateList cands = this.collectCandidates(e.partialTicks);
                if (cands.isEmpty()) {
                    this.currentTargetId = null;
                    this.smoothAim.reset();
                } else {
                    Integer chosenId = this.chooseTargetWithFlick(cands);
                    Vec3d bestAim = cands.aimFor(chosenId);
                    if (bestAim == null) {
                        this.smoothAim.reset();
                    } else if (!isInstantAim()) {
                        if (!Objects.equals(chosenId, this.lastRenderedTargetId)) {
                            this.smoothAim.newTarget();
                        }
                        Vec3d eyes = aimEyes();
                        float[] ang = calculateYawPitch(eyes, bestAim);
                        this.applySmoothAim(ang);
                        this.lastRenderedTargetId = chosenId;
                    } else {
                        this.smoothAim.reset();
                        Vec3d eyes = aimEyes();
                        float[] ang = calculateYawPitch(eyes, bestAim);
                        if (!Objects.equals(chosenId, this.lastRenderedTargetId)) {
                            mc.player.lastYaw = ang[0];
                            mc.player.lastPitch = ang[1];
                            mc.player.lastHeadYaw = ang[0];
                            this.lastRenderedTargetId = chosenId;
                        }

                        mc.player.yaw = ang[0];
                        mc.player.pitch = ang[1];
                    }
                }
            }
        } else {
            this.smoothAim.reset();
            ZombiesMobs.setBadHsLine(null);
            if (mc.player != null) {
                // Keep the durability baseline current so the next aim doesn't see an old shot.
                this.pollGunShot();
                this.shotFired = false;
            }
        }
    }

    /** Insta Kill always snaps (flicking between mobs as the gun fires); otherwise Aim speed applies. */
    private static boolean isInstantAim() {
        return aimSpeed == null || aimSpeed.getInput() >= 20.0 || (insta != null && insta.isToggled());
    }

    /**
     * Where our eyes will be when the rotation set now reaches the server: the next movement packet
     * carries it together with the position after this tick's move. Aiming from the current position
     * makes shots trail behind while walking.
     */
    private static Vec3d aimEyes() {
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        double dy = mc.player.onGround ? 0.0 : mc.player.y - mc.player.lastY;
        return eyes.add(mc.player.x - mc.player.lastX, dy, mc.player.z - mc.player.lastZ);
    }

    /** Move toward {yaw, pitch} like mouse input: shift current and previous rotation together. */
    private void applySmoothAim(float[] ang) {
        if (this.smoothAim.isIdle()) {
            this.smoothAim.randomize(0.3);
        }
        float dt = this.smoothAim.frameDelta();
        // Slider 1..20 covers the old 75..100 range (old scale: speed = (v - 1) / 99).
        double old = 75.0 + (aimSpeed.getInput() - 1.0) * 25.0 / 19.0;
        double speed = (old - 1.0) / 99.0;
        float[] d = this.smoothAim.step(mc.player.yaw, mc.player.pitch, ang[0], ang[1], speed, dt);
        float oldPitch = mc.player.pitch;
        float newPitch = MathHelper.clamp(oldPitch + d[1], -90.0F, 90.0F);
        mc.player.yaw += d[0];
        mc.player.lastYaw += d[0];
        mc.player.pitch = newPitch;
        mc.player.lastPitch += newPitch - oldPitch;
    }

    private Integer chooseTargetWithFlick(Aimbot.CandidateList cands) {
        if (this.isShotFlickActive()) {
            return this.chooseTargetWithShotFlick(cands);
        }
        this.shotFired = false;
        this.currentTargetId = cands.bestId();
        return this.currentTargetId;
    }

    /** Stay on a mob until the gun fires, then presume it dead and snap to the next one. */
    private Integer chooseTargetWithShotFlick(Aimbot.CandidateList cands) {
        if (this.shotFired) {
            this.shotFired = false;
            if (this.currentTargetId != null) {
                this.presumedDead.put(this.currentTargetId, PRESUMED_DEAD_TICKS);
                this.currentTargetId = null;
            }
        }
        if (this.currentTargetId != null
                && cands.contains(this.currentTargetId)
                && !this.presumedDead.containsKey(this.currentTargetId)) {
            return this.currentTargetId;
        }
        Integer next = cands.bestIdExcluding(this.presumedDead.keySet(), null);
        if (next == null) {
            // Everything left was already shot; one of them survived, so aim again.
            next = cands.bestId();
        }
        this.currentTargetId = next;
        return next;
    }

    private Aimbot.CandidateList collectCandidates(float partialTicks) {
        Aimbot.CandidateList out = new Aimbot.CandidateList();
        List<LivingEntity> candidates = new ArrayList<>();
        this.badHsBoxes.clear();
        boolean pvpMode = pvp.isToggled();
        for (Entity entity : mc.world.entities) {
            if (this.isCandidate(entity)) {
                candidates.add((LivingEntity) entity);
            }
            if (!pvpMode
                    && entity instanceof LivingEntity
                    && entity.isAlive()
                    && ZombiesSpawnHelper.getMobType(entity) == MobType.BAD_HEADSHOT) {
                LivingEntity bad = (LivingEntity) entity;
                Vec3d LEAD = getMotionVec(bad, (float) getPredictActive(), (float) getYPredictActive());
                Aimbot.Dim dim = getDims(bad);
                double cx = bad.x + LEAD.x;
                double cy = bad.y + LEAD.y;
                double cz = bad.z + LEAD.z;
                double critFrac = MathHelper.clamp(
                        HEAD_FRAC_BASE + HEAD_FRAC_SLOPE * getCritsActive(), HEAD_FRAC_MIN, getHeadFracMax());
                double critY = MathHelper.clamp(cy + dim.h * critFrac, cy + Y_EPS, cy + dim.h - Y_EPS);
                this.badHsBoxes.add(new Aimbot.BadHsBox(
                        bad.getNetworkId(),
                        new Box(cx - dim.w / 2.0, cy, cz - dim.d / 2.0, cx + dim.w / 2.0, cy + dim.h, cz + dim.d / 2.0),
                        critY));
            }
        }

        // A lone bad-headshot-aware mob is treated as bad headshot (see findHighestVisibleAimPoint).
        boolean singleCandidate = candidates.size() <= 1;
        boolean instaActive = insta != null && insta.isToggled();
        Aimbot.PriorityMode priorityMode = Utils.enumOption(Aimbot.PriorityMode.class, priority);
        Aimbot.PowerupMode powerupMode = Utils.enumOption(Aimbot.PowerupMode.class, powerupPriority);
        Vec3d myEyes = aimEyes();

        for (LivingEntity living : candidates) {
            Vec3d LEAD = this.leadFor(living);
            this.scanTargetId = living.getNetworkId();
            this.avoidBadHs = false;
            Vec3d aim = this.scanAimPoint(living, LEAD, myEyes, singleCandidate, false);
            if (aim != null
                    && !instaActive
                    && !this.badHsBoxes.isEmpty()
                    && ZombiesSpawnHelper.getMobType(living) != MobType.BAD_HEADSHOT
                    && this.lineHitsBadHs(myEyes, aim)) {
                // A bad headshot mob is in the shot line. Shoot through ITS head: it crits and the
                // mobs in front still count as headshots.
                Vec3d through = this.aimThroughBadHsHead(living, LEAD, myEyes, aim);
                if (through != null) {
                    aim = through;
                    out.badHsLine.add(living.getNetworkId());
                } else {
                    // Its head can't be lined up with this mob: look for a line that misses it.
                    this.avoidBadHs = true;
                    Vec3d clean = this.scanAimPoint(living, LEAD, myEyes, singleCandidate, false);
                    this.avoidBadHs = false;
                    if (clean != null) {
                        aim = clean;
                    } else {
                        Vec3d crit = this.scanAimPoint(living, LEAD, myEyes, singleCandidate, true);
                        if (crit != null) {
                            aim = crit;
                            out.badHsLine.add(living.getNetworkId());
                        }
                    }
                }
            }
            if (aim != null) {
                double sc = fovDistance(aim);
                sc -= this.priorityBonus(priorityMode, living);
                if (bossesFirst.isToggled() && ZombiesMobs.isSpecialRedEntity(living)) {
                    sc -= BOSS_PRIORITY_BONUS;
                }
                sc += powerupScoreShift(powerupMode, living);
                out.add(living.getNetworkId(), aim, sc);
            }
        }

        if (out.isEmpty()) {
            for (LivingEntity living : candidates) {
                Vec3d LEAD = this.leadFor(living);
                double y = living.y + 0.05;
                Vec3d alt = new Vec3d(living.x, y, living.z).add(LEAD);
                if (canWallShot(myEyes, alt)) {
                    double sc = fovDistance(alt);
                    out.add(living.getNetworkId(), alt, sc);
                }
            }
        }

        ZombiesMobs.setBadHsLine(out.badHsLine);
        return out;
    }

    /**
     * Mobs: the Predict/YPredict tick lead. PvP: where the server will have the player when our shot
     * arrives (round-trip ping + 1 tick, since the click is sent before this tick's rotation), from
     * AutoAim's packet-based predictor, plus the "PvP extra lead" tweak.
     */
    private Vec3d leadFor(LivingEntity living) {
        if (pvp.isToggled() && living instanceof PlayerEntity) {
            double ticks = Math.max(0.0, this.playerLeads.getPingTicks() + 1.0 + pvpLead.getInput());
            Vec3d LEAD = this.playerLeads.lead((PlayerEntity) living, ticks);
            return LEAD != null ? LEAD : new Vec3d(0.0, 0.0, 0.0);
        }
        return getMotionVec(living, (float) getPredictActive(), (float) getYPredictActive());
    }

    private Vec3d scanAimPoint(
            LivingEntity living, Vec3d LEAD, Vec3d eyes, boolean singleCandidate, boolean forceBadHs) {
        Vec3d aim = living instanceof WitherEntity ? this.findWitherHeadAimPoint(living, LEAD, eyes) : null;
        return aim != null ? aim : this.findHighestVisibleAimPoint(living, LEAD, eyes, singleCandidate, forceBadHs);
    }

    /** A visible point whose shot line (when avoiding) doesn't run through the bad headshot mob. */
    private boolean canShoot(Vec3d eyes, Vec3d point) {
        return canWallShot(eyes, point) && !(this.avoidBadHs && this.lineHitsBadHs(eyes, point));
    }

    /**
     * Whether a shot through {@code point} also hits the bad headshot mob, in front of the target or
     * behind it (bullets pierce mobs but stop at walls).
     */
    private boolean lineHitsBadHs(Vec3d eyes, Vec3d point) {
        Vec3d dir = point.subtract(eyes).normalize();
        Vec3d far = eyes.add(dir.x * SHOT_RANGE, dir.y * SHOT_RANGE, dir.z * SHOT_RANGE);
        double pointDistSq = eyes.squaredDistanceTo(point);
        for (Aimbot.BadHsBox bad : this.badHsBoxes) {
            if (bad.id == this.scanTargetId) {
                continue;
            }
            HitResult hit = bad.box.clip(eyes, far);
            if (hit == null) {
                continue;
            }
            if (eyes.squaredDistanceTo(hit.facePos) <= pointDistSq || canWallShot(eyes, hit.facePos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A point on {@code target} whose shot line continues through the crit height of a bad headshot
     * mob that the plain line {@code plain} runs into. The bad headshot mob's head doesn't have to be
     * visible, only the point on the target. {@code null} if no such line exists.
     */
    private Vec3d aimThroughBadHsHead(LivingEntity target, Vec3d LEAD, Vec3d eyes, Vec3d plain) {
        Vec3d plainDir = plain.subtract(eyes).normalize();
        Vec3d plainFar = eyes.add(plainDir.x * SHOT_RANGE, plainDir.y * SHOT_RANGE, plainDir.z * SHOT_RANGE);
        Aimbot.Dim td = getDims(target);
        double tx = target.x + LEAD.x;
        double ty = target.y + LEAD.y;
        double tz = target.z + LEAD.z;
        Box tBox = new Box(tx - td.w / 2.0, ty, tz - td.d / 2.0, tx + td.w / 2.0, ty + td.h, tz + td.d / 2.0);
        double plainDistSq = eyes.squaredDistanceTo(plain);

        for (Aimbot.BadHsBox bad : this.badHsBoxes) {
            if (bad.id == this.scanTargetId) {
                continue;
            }
            HitResult onLine = bad.box.clip(eyes, plainFar);
            if (onLine == null
                    || (eyes.squaredDistanceTo(onLine.facePos) > plainDistSq && !canWallShot(eyes, onLine.facePos))) {
                continue;
            }
            double cx = (bad.box.minX + bad.box.maxX) / 2.0;
            double cz = (bad.box.minZ + bad.box.maxZ) / 2.0;
            double hx = (bad.box.maxX - bad.box.minX) / 2.0;
            double hz = (bad.box.maxZ - bad.box.minZ) / 2.0;
            for (double[] col : this.generateXZColumns(cx, cz, hx, hz)) {
                double y = raiseForEntry(eyes, bad.box, col[0], col[1], bad.critY);
                Vec3d dir = new Vec3d(col[0], y, col[1]).subtract(eyes).normalize();
                Vec3d far = eyes.add(dir.x * SHOT_RANGE, dir.y * SHOT_RANGE, dir.z * SHOT_RANGE);
                HitResult hit = tBox.clip(eyes, far);
                if (hit != null && canWallShot(eyes, hit.facePos)) {
                    // Nudge just inside the hitbox so the aim point is on the mob, not its surface.
                    return hit.facePos.add(dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
                }
            }
        }
        return null;
    }

    /**
     * Aim height on column (x, z) so the shot enters {@code box} at {@code wantY} or higher. A shot
     * hits the near face of the hitbox before the column, so shooting upward lands lower than the aim
     * point (and downward higher). Only ever raises; capped below the top of the box.
     */
    private static double raiseForEntry(Vec3d eyes, Box box, double x, double z, double wantY) {
        double aimY = wantY;
        for (int i = 0; i < 4; i++) {
            Vec3d dir = new Vec3d(x, aimY, z).subtract(eyes);
            HitResult hit = box.clip(eyes, eyes.add(dir.x * 2.0, dir.y * 2.0, dir.z * 2.0));
            if (hit == null) {
                break;
            }
            double diff = wantY - hit.facePos.y;
            if (diff <= 0.005) {
                break;
            }
            aimY = Math.min(aimY + diff, box.maxY - Y_EPS);
        }
        return aimY;
    }

    private static Aimbot.Dim getDims(LivingEntity e) {
        Aimbot.Dim mapped = mappedDimsOrNull(e);
        if (mapped != null) {
            return mapped;
        } else if (e instanceof SlimeEntity) {
            SlimeEntity slime = (SlimeEntity) e;
            int size = slime.getSize();
            double baseSize = 0.51;
            double scale = baseSize * size;
            return new Aimbot.Dim(scale, scale, scale);
        } else {
            double w = aabbW(e);
            double d = aabbD(e);
            double h = aabbH(e);
            return new Aimbot.Dim(w, h, d);
        }
    }

    private static double aabbH(LivingEntity e) {
        if (e.getShape() != null) {
            double height = e.getShape().maxY - e.getShape().minY;
            return Math.max(0.1, height);
        } else {
            return e.getEyeHeight();
        }
    }

    private static double aabbW(LivingEntity e) {
        return e.getShape() != null ? Math.max(0.4, e.getShape().maxX - e.getShape().minX) : 0.8;
    }

    private static double aabbD(LivingEntity e) {
        return e.getShape() != null ? Math.max(0.4, e.getShape().maxZ - e.getShape().minZ) : 0.8;
    }

    private static double getHeadFracMax() {
        return HEAD_FRAC_MAX;
    }

    private static double getCritsActive() {
        if (critsOverrideActive) {
            return critsOverrideValue;
        } else {
            return crits == null ? 0.0 : crits.getInput();
        }
    }

    private static double getPredictActive() {
        return predictOverrideActive ? predictOverrideValue : (predict == null ? 0.0 : predict.getInput());
    }

    private static double getYPredictActive() {
        return yPredictOverrideActive ? yPredictOverrideValue : (yPredict == null ? 0.0 : yPredict.getInput());
    }

    /**
     * Visible point on a wither's middle head, or {@code null} if the head is fully blocked (the
     * caller then falls back to the normal body scan). Scans outward from the head centre so the
     * shot lands as close to the middle of the head as visibility allows.
     */
    private Vec3d findWitherHeadAimPoint(LivingEntity WITHER, Vec3d LEAD, Vec3d eyes) {
        double centerY = WITHER.y + WITHER.getEyeHeight() + witherHeadOffset.getInput();
        for (double[] col : this.generateXZColumns(WITHER.x, WITHER.z, WITHER_HEAD_HALF, WITHER_HEAD_HALF)) {
            for (double off = 0.0; off <= WITHER_HEAD_HALF; off += 0.05) {
                Vec3d below = new Vec3d(col[0], centerY - off, col[1]).add(LEAD);
                if (this.canShoot(eyes, below)) {
                    return below;
                }
                Vec3d above = new Vec3d(col[0], centerY + off, col[1]).add(LEAD);
                if (off > 0.0 && this.canShoot(eyes, above)) {
                    return above;
                }
            }
        }
        return null;
    }

    private Vec3d findHighestVisibleAimPoint(
            LivingEntity e, Vec3d LEAD, Vec3d eyes, boolean singleCandidate, boolean forceBadHs) {
        if (pvp.isToggled() && e instanceof PlayerEntity) {
            return this.findPlayerAimPoint(e, LEAD, eyes);
        }
        Aimbot.Dim dim = getDims(e);
        double halfX = dim.w * 0.5;
        double halfZ = dim.d * 0.5;
        double baseY = e.y + Y_EPS;
        double topYHitbox = e.y + dim.h - Y_EPS;
        MobType type = ZombiesSpawnHelper.getMobType(e);
        boolean isBadHS = forceBadHs || type == MobType.BAD_HEADSHOT;
        if (!isBadHS && singleCandidate && this.isBadHsAwareMob(e)) {
            isBadHS = true;
        }

        double c = getCritsActive();
        boolean instaActive = insta != null && insta.isToggled();
        double usualHeadFrac = instaActive ? 0.5 : HEAD_FRAC_BASE + HEAD_FRAC_SLOPE * c;
        boolean closeAbove = !instaActive && isCloseAbove(e);
        boolean forceLowAim = false;
        if (!instaActive) {
            double playerY = mc.player.y;
            boolean isAboveOrSame = e.y > playerY - 1.0;
            boolean canReachMinPitch = e.y + dim.h > eyes.y;
            if (isAboveOrSame && canReachMinPitch && this.isBadHsAwareMob(e) && !isBadHS) {
                forceLowAim = true;
            }

            usualHeadFrac = this.applyBadHsAwareAimPreference(e, usualHeadFrac);
            if (closeAbove) {
                usualHeadFrac = Math.min(usualHeadFrac, closeAimHeight.getInput() / 100.0);
            }
        }

        usualHeadFrac = MathHelper.clamp(usualHeadFrac, HEAD_FRAC_MIN, getHeadFracMax());
        double headYCandidate = e.y + dim.h * usualHeadFrac;
        double startY = MathHelper.clamp(headYCandidate, baseY, topYHitbox);
        List<double[]> xzCols = this.generateXZColumns(e.x, e.z, halfX, halfZ);
        if (isBadHS && !instaActive) {
            double targetY = e.y + dim.h * usualHeadFrac;
            double critY = MathHelper.clamp(targetY, baseY, topYHitbox);
            Box critBox = new Box(
                    e.x - halfX + LEAD.x,
                    e.y + LEAD.y,
                    e.z - halfZ + LEAD.z,
                    e.x + halfX + LEAD.x,
                    e.y + dim.h + LEAD.y,
                    e.z + halfZ + LEAD.z);

            for (double[] col : xzCols) {
                double colX = col[0];
                double colZ = col[1];
                double entryY = raiseForEntry(eyes, critBox, colX + LEAD.x, colZ + LEAD.z, critY + LEAD.y) - LEAD.y;
                // Close and above: skip the raise, it pushes the aim to the top edge of the hitbox.
                if (!closeAbove && entryY > critY && this.canShoot(eyes, new Vec3d(colX, entryY, colZ).add(LEAD))) {
                    return new Vec3d(colX, entryY, colZ).add(LEAD);
                }
                if (this.canShoot(eyes, new Vec3d(colX, critY, colZ).add(LEAD))) {
                    return new Vec3d(colX, critY, colZ).add(LEAD);
                }

                for (double y = critY - 0.05; y >= baseY; y -= 0.05) {
                    if (this.canShoot(eyes, new Vec3d(colX, y, colZ).add(LEAD))) {
                        return new Vec3d(colX, y, colZ).add(LEAD);
                    }
                }

                for (double y = critY + 0.05; y <= topYHitbox; y += 0.05) {
                    if (this.canShoot(eyes, new Vec3d(colX, y, colZ).add(LEAD))) {
                        return new Vec3d(colX, y, colZ).add(LEAD);
                    }
                }
            }
        } else if (forceLowAim) {
            boolean isStrictlyAbove = e.y > mc.player.y + ABOVE_PLAYER_EPS;
            double tanMin = Math.tan(Math.toRadians(0.1));
            if (!isStrictlyAbove) {
                double bestY = Double.POSITIVE_INFINITY;
                double bestX = 0.0;
                double bestZ = 0.0;
                boolean found = false;

                for (double[] col : xzCols) {
                    double colX = col[0];
                    double colZ = col[1];
                    double dx = colX - eyes.x;
                    double dz = colZ - eyes.z;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    double limitY = eyes.y + distXZ * tanMin;
                    double startScanY = Math.max(baseY, limitY);

                    for (double y = startScanY; y <= topYHitbox && (!found || !(y > bestY)); y += 0.05) {
                        Vec3d test = new Vec3d(colX, y, colZ).add(LEAD);
                        if (this.canShoot(eyes, test)) {
                            if (y < bestY - 0.15 || !found) {
                                bestY = y;
                                bestX = colX;
                                bestZ = colZ;
                                found = true;
                            }
                            break;
                        }
                    }
                }

                if (found) {
                    return new Vec3d(bestX, bestY, bestZ).add(LEAD);
                }
            } else {
                for (double[] col : xzCols) {
                    double colX = col[0];
                    double colZ = col[1];
                    double dx = colX - eyes.x;
                    double dz = colZ - eyes.z;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    double limitY = eyes.y + distXZ * tanMin;
                    double centerY = e.y + dim.h / 2.0;
                    double centerCheckY = MathHelper.clamp(centerY, baseY, topYHitbox);
                    if (this.canShoot(eyes, new Vec3d(colX, centerCheckY, colZ).add(LEAD))) {
                        return new Vec3d(colX, centerCheckY, colZ).add(LEAD);
                    }

                    if (centerY > limitY) {
                        for (double y = centerCheckY - 0.05; y >= limitY && !(y < baseY); y -= 0.05) {
                            if (this.canShoot(eyes, new Vec3d(colX, y, colZ).add(LEAD))) {
                                return new Vec3d(colX, y, colZ).add(LEAD);
                            }
                        }
                    }

                    for (double y = centerCheckY + 0.05; y <= topYHitbox; y += 0.05) {
                        if (this.canShoot(eyes, new Vec3d(colX, y, colZ).add(LEAD))) {
                            return new Vec3d(colX, y, colZ).add(LEAD);
                        }
                    }

                    double scanStart = Math.min(topYHitbox, Math.max(baseY, limitY - 0.05));

                    for (double y = scanStart; y >= baseY; y -= 0.05) {
                        if (this.canShoot(eyes, new Vec3d(colX, y, colZ).add(LEAD))) {
                            return new Vec3d(colX, y, colZ).add(LEAD);
                        }
                    }
                }
            }
        } else {
            double bestY = Double.NEGATIVE_INFINITY;
            double bestX = 0.0;
            double bestZ = 0.0;

            for (double[] col : xzCols) {
                double colX = col[0];
                double colZ = col[1];

                for (double y = startY; y >= baseY; y -= 0.05) {
                    Vec3d test = new Vec3d(colX, y, colZ).add(LEAD);
                    if (this.canShoot(eyes, test)) {
                        if (y > bestY) {
                            bestY = y;
                            bestX = colX;
                            bestZ = colZ;
                        }
                        break;
                    }
                }
            }

            if (bestY != Double.NEGATIVE_INFINITY) {
                return new Vec3d(bestX, bestY, bestZ).add(LEAD);
            }
        }

        Vec3d base = new Vec3d(e.x, baseY, e.z).add(LEAD);
        return this.canShoot(eyes, base) ? base : null;
    }

    /** The mob is within CLOSE_XZ blocks on X and Z and at least CLOSE_MIN_DY blocks above you. */
    /**
     * PvP aim point: the visible point closest to the wanted height, body centre by default or the
     * head (Crits height) with "Attempt headshots". Scans outward from that height so a partly covered
     * player is still hit as close to it as possible.
     */
    private Vec3d findPlayerAimPoint(LivingEntity e, Vec3d LEAD, Vec3d eyes) {
        Aimbot.Dim dim = getDims(e);
        double baseY = e.y + Y_EPS;
        double topY = e.y + dim.h - Y_EPS;
        double frac = attemptHeadshots.isToggled() ? HEAD_FRAC_BASE + HEAD_FRAC_SLOPE * getCritsActive() : 0.5;
        double wantY = MathHelper.clamp(e.y + dim.h * frac, baseY, topY);
        Vec3d best = null;
        double bestOff = Double.POSITIVE_INFINITY;
        for (double[] col : this.generateXZColumns(e.x, e.z, dim.w * 0.5, dim.d * 0.5)) {
            for (double off = 0.0; off < bestOff && off <= dim.h; off += 0.05) {
                Vec3d below = new Vec3d(col[0], wantY - off, col[1]).add(LEAD);
                if (wantY - off >= baseY && this.canShoot(eyes, below)) {
                    best = below;
                    bestOff = off;
                    break;
                }
                Vec3d above = new Vec3d(col[0], wantY + off, col[1]).add(LEAD);
                if (off > 0.0 && wantY + off <= topY && this.canShoot(eyes, above)) {
                    best = above;
                    bestOff = off;
                    break;
                }
            }
            if (bestOff == 0.0) {
                break;
            }
        }
        return best;
    }

    private static boolean isCloseAbove(LivingEntity e) {
        return Math.abs(e.x - mc.player.x) <= CLOSE_XZ
                && Math.abs(e.z - mc.player.z) <= CLOSE_XZ
                && e.y - mc.player.y >= CLOSE_MIN_DY;
    }

    private double applyBadHsAwareAimPreference(LivingEntity target, double defaultHeadFrac) {
        if (mc == null || mc.player == null) {
            return defaultHeadFrac;
        }

        if (!this.isBadHsAwareMob(target)) {
            return defaultHeadFrac;
        }

        double playerBaseY = mc.player.y;
        double targetBaseY = target.y;
        if (targetBaseY <= playerBaseY + ABOVE_PLAYER_EPS) {
            return defaultHeadFrac;
        }

        MobType mobType = ZombiesSpawnHelper.getMobType(target);
        return mobType == MobType.BAD_HEADSHOT
                ? defaultHeadFrac
                : MathHelper.clamp(CHEST_PRIORITY_FRAC, HEAD_FRAC_MIN, getHeadFracMax());
    }

    private boolean isBadHsAwareMob(LivingEntity target) {
        return target instanceof SkeletonEntity
                || target instanceof ZombiePigmanEntity
                || target instanceof ZombieEntity
                || target instanceof BlazeEntity
                || target instanceof WitchEntity;
    }

    private List<double[]> generateXZColumns(double cx, double cz, double halfX, double halfZ) {
        List<double[]> out = new ArrayList<>(25);
        out.add(new double[] {cx, cz});
        this.addRingXZ(out, cx, cz, halfX * 0.35, halfZ * 0.35);
        this.addRingXZ(out, cx, cz, halfX * 0.7, halfZ * 0.7);
        this.addRingXZ(out, cx, cz, halfX * 0.95, halfZ * 0.95);
        return out;
    }

    private void addRingXZ(List<double[]> out, double cx, double cz, double rx, double rz) {
        out.add(new double[] {cx + rx, cz});
        out.add(new double[] {cx - rx, cz});
        out.add(new double[] {cx, cz + rz});
        out.add(new double[] {cx, cz - rz});
        out.add(new double[] {cx + rx, cz + rz});
        out.add(new double[] {cx - rx, cz + rz});
        out.add(new double[] {cx + rx, cz - rz});
        out.add(new double[] {cx - rx, cz - rz});
    }

    private static Aimbot.Dim mappedDimsOrNull(LivingEntity e) {
        if (e instanceof SkeletonEntity) {
            SkeletonEntity sk = (SkeletonEntity) e;
            return sk.getType() == 1 ? new Aimbot.Dim(1.0, 2.6, 1.0) : new Aimbot.Dim(0.9, 2.0, 0.9);
        } else if (e instanceof ZombieEntity) {
            ZombieEntity z = (ZombieEntity) e;
            return z.isBaby() ? new Aimbot.Dim(1.2, 0.8, 1.2) : new Aimbot.Dim(0.9, 2.0, 0.9);
        } else if (e instanceof WolfEntity) {
            WolfEntity w = (WolfEntity) e;
            return w.isBaby() ? new Aimbot.Dim(0.5, 0.3, 0.5) : new Aimbot.Dim(1.5, 0.7, 1.5);
        } else if (e instanceof ZombiePigmanEntity) {
            return new Aimbot.Dim(0.9, 2.0, 0.9);
        } else if (e instanceof BlazeEntity) {
            return new Aimbot.Dim(0.9, 2.0, 0.9);
        } else if (e instanceof SilverfishEntity) {
            return new Aimbot.Dim(0.2, 0.4, 0.2);
        } else if (e instanceof EndermiteEntity) {
            return new Aimbot.Dim(0.2, 0.4, 0.2);
        } else if (e instanceof WitchEntity) {
            return new Aimbot.Dim(0.9, 1.75, 0.9);
        } else if (e instanceof CreeperEntity) {
            return new Aimbot.Dim(0.9, 1.1, 0.9);
        } else if (e instanceof CaveSpiderEntity) {
            return new Aimbot.Dim(0.5, 0.7, 0.5);
        } else if (e instanceof GiantEntity) {
            return new Aimbot.Dim(3.9, 12.0, 3.9);
        } else if (e instanceof GhastEntity) {
            return new Aimbot.Dim(4.4, 4.4, 4.4);
        } else {
            return e instanceof IronGolemEntity ? new Aimbot.Dim(1.8, 2.7, 1.8) : null;
        }
    }

    private boolean isCandidate(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        if (!entity.isAlive()) {
            return false;
        }

        if (entity instanceof ArmorStandEntity) {
            return false;
        }

        // PvP: real players only. AntiBot (when enabled) drops anticheat bots / NPCs.
        if (pvp.isToggled()) {
            if (!(entity instanceof PlayerEntity) || entity == mc.player) {
                return false;
            }
            PlayerEntity player = (PlayerEntity) entity;
            return !player.isSpectator() && !AntiBot.isBot(player) && !Utils.isFriended(player);
        }

        if (entity instanceof VillagerEntity) {
            return false;
        }

        // Skips the invisible wither Hypixel uses to draw boss bars.
        if (entity instanceof WitherEntity && !ZombiesMobs.isWitherBoss(entity)) {
            return false;
        }

        if (entity instanceof PlayerEntity) {
            return false;
        }

        if (entity instanceof SquidEntity) {
            return false;
        }

        if (entity instanceof ChickenEntity) {
            return false;
        }

        if (entity instanceof PigEntity) {
            return false;
        }

        if (entity instanceof CowEntity) {
            return false;
        }

        if (entity instanceof WolfEntity && ((WolfEntity) entity).isBaby()) {
            boolean aimPups = smartPup.isToggled() ? ZombiesMap.current() == ZombiesMap.BAD_BLOOD : pup.isToggled();
            if (!aimPups) {
                return false;
            }
        }

        return true;
    }

    /**
     * Score reduction for the selected priority mob type. Scores are FOV angles (lower wins), so a
     * bonus this large makes a visible priority mob win over anything else regardless of angle.
     */
    private double priorityBonus(Aimbot.PriorityMode mode, Entity entity) {
        switch (mode) {
            case GHAST:
                return this.isGhastPriority(entity) ? 3000.0 : 0.0;
            case SENTINEL:
                return this.isSentinelPriority(entity) ? 2500.0 : 0.0;
            case R5_BOSS:
                return this.isR5BossZombie(entity) ? 2000.0 : 0.0;
            case SLIME:
                return this.isSlimePriority(entity) ? 1500.0 : 0.0;
            case SKELETON:
                return this.isArmoredSkele(entity) ? 1000.0 : 0.0;
            case ZOMBIE:
                return this.isZombiePriority(entity) ? 500.0 : 0.0;
            default:
                return 0.0;
        }
    }

    /**
     * First: beats the Priority list but not "Bosses first". Last: only picked when nothing else is
     * shootable. Normal: no change, so the Priority list and cursor distance decide.
     */
    private static double powerupScoreShift(Aimbot.PowerupMode mode, Entity entity) {
        if (mode == Aimbot.PowerupMode.NORMAL) {
            return 0.0;
        }
        MobType type = ZombiesSpawnHelper.getMobType(entity);
        if (type != MobType.POWERUP_PREDICT && type != MobType.POWERUP_ENSURED) {
            return 0.0;
        }
        return mode == Aimbot.PowerupMode.FIRST ? -POWERUP_PRIORITY_BONUS : POWERUP_LAST_PENALTY;
    }

    private boolean isZombiePriority(Entity entity) {
        if (!(entity instanceof ZombieEntity)) {
            return false;
        }

        ZombieEntity zombie = (ZombieEntity) entity;
        return !this.isR5BossZombie(entity) && !this.isSentinelPriority(entity) ? !zombie.isBaby() : false;
    }

    private boolean isArmoredSkele(Entity entity) {
        if (!(entity instanceof SkeletonEntity)) {
            return false;
        }

        SkeletonEntity s = (SkeletonEntity) entity;
        ItemStack boots = s.getEquipment(1);
        ItemStack legs = s.getEquipment(2);
        ItemStack CHEST = s.getEquipment(3);
        ItemStack held = s.getDisplayItemInHand();
        return boots != null
                && boots.getItem() == Items.IRON_BOOTS
                && legs != null
                && legs.getItem() == Items.IRON_LEGGINGS
                && CHEST != null
                && CHEST.getItem() == Items.IRON_CHESTPLATE
                && held != null
                && held.getItem() == Items.STONE_SWORD;
    }

    /** Round 5 boss style mob: chainmail-armored zombie with a stone/iron sword. */
    private boolean isR5BossZombie(Entity entity) {
        return ZombiesMobs.isGearedZombie(entity);
    }

    private boolean isGhastPriority(Entity entity) {
        if (!(entity instanceof GhastEntity)) {
            return false;
        }

        Vec3d eyes = mc.player.getEyePosition(1.0F);
        Vec3d target = new Vec3d(entity.x, entity.y + entity.height * 0.5, entity.z);
        return canWallShot(eyes, target);
    }

    private boolean isSentinelPriority(Entity entity) {
        if (!(entity instanceof ZombieEntity)) {
            return false;
        }

        ZombieEntity zombie = (ZombieEntity) entity;
        return zombie.hasStatusEffect(StatusEffect.INVISIBILITY);
    }

    private boolean isSlimePriority(Entity entity) {
        return entity instanceof SlimeEntity;
    }

    public static double fovDistance(Vec3d vec3) {
        float[] a = calculateYawPitch(mc.player.getEyePosition(1.0F), vec3);
        return angleBetween(a[0], mc.player.yaw)
                + Math.abs(Math.max(a[1], mc.player.pitch) - Math.min(a[1], mc.player.pitch));
    }

    public static double angleBetween(double first, double second) {
        return Math.abs(subtractAngles(first, second));
    }

    public static double subtractAngles(double start, double end) {
        return MathHelper.wrapDegrees(end - start);
    }

    public static float[] calculateYawPitch(Vec3d start, Vec3d vec) {
        float[] rot = RotationUtils.getRotationsFromEye(start, vec.x, vec.y, vec.z);
        return new float[] {MathHelper.wrapDegrees(rot[0]), MathHelper.wrapDegrees(rot[1])};
    }

    public static boolean canWallShot(Vec3d start, Vec3d end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double eps = 1.0E-7;
        double sx = start.x + (len > 0.0 ? dx / len * eps : 0.0);
        double sy = start.y + (len > 0.0 ? dy / len * eps : 0.0);
        double sz = start.z + (len > 0.0 ? dz / len * eps : 0.0);
        double ex = end.x;
        double ey = end.y;
        double ez = end.z;
        int x = MathHelper.floor(sx);
        int y = MathHelper.floor(sy);
        int z = MathHelper.floor(sz);
        int endX = MathHelper.floor(ex);
        int endY = MathHelper.floor(ey);
        int endZ = MathHelper.floor(ez);
        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);
        double tDeltaX = stepX != 0 ? Math.min(Math.abs(1.0 / dx), 1000000.0) : 1000000.0;
        double tDeltaY = stepY != 0 ? Math.min(Math.abs(1.0 / dy), 1000000.0) : 1000000.0;
        double tDeltaZ = stepZ != 0 ? Math.min(Math.abs(1.0 / dz), 1000000.0) : 1000000.0;
        double xBorder = stepX > 0 ? x + 1 : x;
        double yBorder = stepY > 0 ? y + 1 : y;
        double zBorder = stepZ > 0 ? z + 1 : z;
        double tMaxX = stepX != 0 ? (xBorder - sx) / dx : 1000000.0;
        double tMaxY = stepY != 0 ? (yBorder - sy) / dy : 1000000.0;
        double tMaxZ = stepZ != 0 ? (zBorder - sz) / dz : 1000000.0;
        int maxIter = 4096;

        while (maxIter-- > 0) {
            Block b = BlockUtils.getBlock(new BlockPos(x, y, z));
            if (!isAirLike(b)) {
                return (wallshoot == null || wallshoot.isToggled()) && isAllowedFirstSolid(b);
            }

            if (x == endX && y == endY && z == endZ) {
                return true;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return false;
    }

    private static boolean isAirLike(Block b) {
        if (b != Blocks.AIR
                && b != Blocks.TALLGRASS
                && b != Blocks.VINE
                && b != Blocks.CARPET
                && b != Blocks.SNOW_LAYER
                && b != Blocks.TRIPWIRE_HOOK
                && b != Blocks.STONE_BUTTON
                && b != Blocks.WOODEN_BUTTON
                && b != Blocks.LEVER
                && b != Blocks.RED_FLOWER
                && b != Blocks.YELLOW_FLOWER
                && !(b instanceof FlowerBlock)) {
            try {
                return b.getMaterial().isLiquid() || b.getMaterial().isReplaceable();
            } catch (Throwable t) {
                return false;
            }
        } else {
            return true;
        }
    }

    private static boolean isAllowedFirstSolid(Block b) {
        if (b instanceof SlabBlock) {
            return !((SlabBlock) b).isDouble();
        }

        if (b instanceof StairsBlock) {
            if (b == Blocks.JUNGLE_STAIRS) {
                return false;
            } else if (b == Blocks.DARK_OAK_STAIRS) {
                return false;
            } else if (b == Blocks.SPRUCE_STAIRS) {
                return false;
            } else if (b == Blocks.SANDSTONE_STAIRS) {
                return false;
            } else {
                return b == Blocks.NETHER_BRICK_STAIRS ? false : b != Blocks.RED_SANDSTONE_STAIRS;
            }
        } else if (b == Blocks.IRON_DOOR) {
            return true;
        } else if (b == Blocks.IRON_BARS) {
            return true;
        } else if (b instanceof SignBlock) {
            return true;
        } else {
            return b instanceof BarrierBlock ? true : b == Blocks.FENCE;
        }
    }

    /** The Aimbot's chat commands (.crits, .predict, .ypredict), registered by CommandManager. */
    public static List<Command> createCommands() {
        List<Command> list = new ArrayList<>();
        list.add(new Aimbot.CritsCommand());
        list.add(new Aimbot.PredictCommand());
        list.add(new Aimbot.YPredictCommand());
        return list;
    }

    public static Vec3d getMotionVec(Entity entity, float ticks, float yTicks) {
        double dX = entity.x - entity.lastX;
        double dY = entity.y - entity.lastY;
        double dZ = entity.z - entity.lastZ;
        float verticalPredict = yTicks;
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.damagedTimer > 0 || !living.onGround) {
                double distSq = dX * dX + dZ * dZ;
                if (distSq > 0.001) {
                    double moveYaw = Math.toDegrees(Math.atan2(-dX, dZ));
                    double diff = Math.abs(MathHelper.wrapDegrees(moveYaw - living.yaw));
                    if (diff > 90.0) {
                        verticalPredict *= 0.25F;
                    }
                }
            }
        }

        double outX = 0.0;
        double outY = 0.0;
        double outZ = 0.0;
        if (dY > 0.0 && dY < 0.85) {
            dY = 0.0;
        }

        Aimbot.Dim dim = getDims((LivingEntity) entity);
        Box customBB = new Box(
                entity.x - dim.w / 2.0,
                entity.y,
                entity.z - dim.d / 2.0,
                entity.x + dim.w / 2.0,
                entity.y + dim.h,
                entity.z + dim.d / 2.0);

        for (double i = 1.0; i <= ticks; i += 0.3) {
            for (double i2 = 1.0;
                    i2 <= verticalPredict && !mc.world.containsNonAir(customBB.moved(dX * i, dY * i2, dZ * i));
                    i2 += 0.3) {
                outX = dX * i;
                outY = dY * i2;
                outZ = dZ * i;
            }
        }

        return new Vec3d(outX, outY, outZ);
    }

    private static String normalizeChat(String s) {
        if (s == null) {
            return "";
        }

        String out = s.toLowerCase();
        out = out.replaceAll("[\\[\\](){}|:;*~_^<>§]", " ");
        return out.replaceAll("\\s+", " ").trim();
    }

    private static boolean trySetBoolean(ButtonSetting setting, boolean value) {
        if (setting == null) {
            return false;
        }
        setting.setEnabled(value);
        return true;
    }

    private static boolean trySetSlider(SliderSetting setting, double value) {
        if (setting == null) {
            return false;
        }
        setting.setValue(value);
        return true;
    }

    private static class BadHsBox {
        final int id;
        final Box box;
        /** Height of its crit (head) point, prediction included. */
        final double critY;

        BadHsBox(int id, Box box, double critY) {
            this.id = id;
            this.box = box;
            this.critY = critY;
        }
    }

    private static class CandidateList {
        /** Mobs whose every visible shot line also crosses the bad headshot mob. */
        final Set<Integer> badHsLine = new HashSet<>();

        private final Map<Integer, Vec3d> aimVec = new HashMap<>();
        private final Map<Integer, Double> score = new HashMap<>();
        private Integer bestId;

        private CandidateList() {}

        boolean isEmpty() {
            return this.score.isEmpty();
        }

        boolean contains(Integer id) {
            return id != null && this.score.containsKey(id);
        }

        Vec3d aimFor(Integer id) {
            return id == null ? null : this.aimVec.get(id);
        }

        Integer bestId() {
            return this.bestId;
        }

        Integer bestIdExcluding(Set<Integer> exclude, Integer alsoExclude) {
            Double bestScore = null;
            Integer best = null;

            for (Entry<Integer, Double> en : this.score.entrySet()) {
                Integer id = en.getKey();
                if ((alsoExclude == null || !id.equals(alsoExclude)) && (exclude == null || !exclude.contains(id))) {
                    double sc = en.getValue();
                    if (bestScore == null || sc < bestScore) {
                        bestScore = sc;
                        best = id;
                    }
                }
            }

            return best;
        }

        void add(Integer id, Vec3d aim, double sc) {
            if (id != null && aim != null) {
                Double prev = this.score.get(id);
                if (prev == null || sc < prev) {
                    this.score.put(id, sc);
                    this.aimVec.put(id, aim);
                }

                if (this.bestId == null || this.score.get(id) < this.score.get(this.bestId)) {
                    this.bestId = id;
                }
            }
        }
    }

    private static class CritsCommand extends Command {
        private CritsCommand() {
            super("crits");
        }

        private String usage() {
            return Feral.commandManager.getPrefix() + "crits <value -2.000..+2.000>";
        }

        @Override
        public void execute(CommandInput input) {
            String[] args = input.getArguments();
            if (args.length == 0) {
                double cur = Aimbot.getCritsActive();
                Utils.sendRawMessage("§a[Aimbot] Crits is §e" + Aimbot.fmt3(cur));
            } else {
                try {
                    double v = MathHelper.clamp(Double.parseDouble(args[0]), CRITS_EXT_MIN, CRITS_EXT_MAX);
                    if (v >= CRITS_MIN && v <= CRITS_MAX) {
                        Aimbot.setGuiCritsSlider(v);
                        Aimbot.critsOverrideActive = false;
                        Aimbot.critsOverrideValue = 0.0;
                    } else {
                        double nearestGui = v < CRITS_MIN ? CRITS_MIN : CRITS_MAX;
                        Aimbot.setGuiCritsSlider(nearestGui);
                        Aimbot.critsOverrideActive = true;
                        Aimbot.critsOverrideValue = v;
                    }

                    Utils.sendRawMessage("§a[Aimbot] Crits set to §e" + Aimbot.fmt3(v));
                } catch (NumberFormatException ex) {
                    Utils.sendRawMessage("§c[Aimbot] Invalid number. Usage: §f" + this.usage());
                }
            }
        }
    }

    private static class Dim {
        final double w;
        final double h;
        final double d;

        Dim(double w, double h, double d) {
            this.w = w;
            this.h = h;
            this.d = d;
        }
    }

    private class GlobalChatHookIncoming {
        private GlobalChatHookIncoming() {}

        @SubscribeEvent
        public void onChat(ClientChatReceivedEvent e) {
            if (e != null && e.message != null) {
                String norm = Aimbot.normalizeChat(e.message.getString());
                boolean isRed = this.isRedColored(e.message);
                if (Aimbot.autoInsta != null
                        && Aimbot.autoInsta.isToggled()
                        && (norm.contains("activated insta kill") || norm.contains("insta kill activated"))
                        && isRed) {
                    Aimbot.independentInstaState = true;
                    Aimbot.independentInstaDisableTime = System.currentTimeMillis() + CHAT_INSTA_AUTO_DISABLE_MS;
                    if (Aimbot.this.isEnabled()) {
                        Aimbot.trySetBoolean(Aimbot.insta, true);
                        Utils.sendRawMessage("§a[Aimbot] Insta Enabled");
                    }
                }

                if (Aimbot.bbr1 != null
                        && Aimbot.bbr1.isToggled()
                        && norm.contains("fight with your teammates")
                        && isRed) {
                    long now = System.currentTimeMillis();
                    Aimbot.bbr1EnableAtMs = now + BBR1_DELAY_MS;
                    Aimbot.bbr1DisableAtMs = Aimbot.bbr1EnableAtMs + BBR1_WINDOW_MS;
                }
            }
        }

        private boolean isRedColored(Text component) {
            if (component == null) {
                return false;
            }

            Style style = component.getStyle();
            if (style == null
                    || style.getColor() == null
                    || style.getColor().getId() != 12
                            && style.getColor().getId() != 14
                            && !"red".equalsIgnoreCase(style.getColor().getName())
                            && !"dark_red".equalsIgnoreCase(style.getColor().getName())) {
                String formatted = component.getFormattedString();
                if (formatted == null || !formatted.contains("§c") && !formatted.contains("§4")) {
                    if (component.getSiblings() != null) {
                        for (Text sibling : component.getSiblings()) {
                            if (this.isRedColored(sibling)) {
                                return true;
                            }
                        }
                    }

                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }

        @SubscribeEvent
        public void onClientTick(ClientTickEvent e) {
            long now = System.currentTimeMillis();
            if (Aimbot.independentInstaState
                    && Aimbot.independentInstaDisableTime >= 0L
                    && now >= Aimbot.independentInstaDisableTime) {
                Aimbot.independentInstaState = false;
                Aimbot.independentInstaDisableTime = -1L;
                if (Aimbot.this.isEnabled()) {
                    Aimbot.trySetBoolean(Aimbot.insta, false);
                    Utils.sendRawMessage("§c[Aimbot] Insta Disabled");
                }
            }

            if (Aimbot.bbr1EnableAtMs > 0L && now >= Aimbot.bbr1EnableAtMs) {
                Aimbot.independentInstaState = true;
                Aimbot.bbr1EnableAtMs = -1L;
                if (Aimbot.this.isEnabled()) {
                    Aimbot.trySetBoolean(Aimbot.insta, true);
                    Utils.sendRawMessage("§a[Aimbot] Insta Enabled");
                }
            }

            if (Aimbot.bbr1DisableAtMs > 0L && now >= Aimbot.bbr1DisableAtMs) {
                Aimbot.independentInstaState = false;
                Aimbot.bbr1DisableAtMs = -1L;
                if (Aimbot.this.isEnabled()) {
                    Aimbot.trySetBoolean(Aimbot.insta, false);
                    Utils.sendRawMessage("§c[Aimbot] Insta Disabled");
                }
            }

            if (Aimbot.this.isEnabled()) {
                if (Aimbot.independentInstaState && Aimbot.insta != null && !Aimbot.insta.isToggled()) {
                    Aimbot.trySetBoolean(Aimbot.insta, true);
                } else if (!Aimbot.independentInstaState && Aimbot.insta != null && Aimbot.insta.isToggled()) {
                    Aimbot.trySetBoolean(Aimbot.insta, false);
                }
            }
        }
    }

    private static class PredictCommand extends Command {
        private PredictCommand() {
            super("predict");
        }

        private String usage() {
            return Feral.commandManager.getPrefix() + "predict <value 0.000..10.000>";
        }

        @Override
        public void execute(CommandInput input) {
            String[] args = input.getArguments();
            if (Aimbot.predict != null) {
                if (args.length == 0) {
                    Utils.sendRawMessage("§a[Aimbot] Predict is §e" + Aimbot.fmt3(Aimbot.getPredictActive()));
                } else {
                    try {
                        double v = MathHelper.clamp(Double.parseDouble(args[0]), 0.0, 10.0);
                        Aimbot.trySetSlider(Aimbot.predict, v);
                        Aimbot.guiPredictLockTicks = 2;
                        Aimbot.lastGuiPredictValue = Aimbot.predict.getInput();
                        Aimbot.predictOverrideActive = true;
                        Aimbot.predictOverrideValue = v;
                        Utils.sendRawMessage("§a[Aimbot] Predict set to §e" + Aimbot.fmt3(v));
                    } catch (NumberFormatException ex) {
                        Utils.sendRawMessage("§c[Aimbot] Invalid number. Usage: §f" + this.usage());
                    }
                }
            }
        }
    }

    public enum PowerupMode {
        NORMAL("Normal"),
        FIRST("First"),
        LAST("Last");

        private final String label;

        PowerupMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    public enum PriorityMode {
        NONE("None"),
        ZOMBIE("Zombie"),
        SKELETON("Skeleton"),
        R5_BOSS("R5Boss"),
        GHAST("Ghast"),
        SENTINEL("Sentinel"),
        SLIME("Slime");

        private final String name;

        PriorityMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private static class YPredictCommand extends Command {
        private YPredictCommand() {
            super("ypredict");
        }

        private String usage() {
            return Feral.commandManager.getPrefix() + "ypredict <value 0.000..10.000>";
        }

        @Override
        public void execute(CommandInput input) {
            String[] args = input.getArguments();
            if (Aimbot.yPredict != null) {
                if (args.length == 0) {
                    Utils.sendRawMessage("§a[Aimbot] YPredict is §e" + Aimbot.fmt3(Aimbot.getYPredictActive()));
                } else {
                    try {
                        double v = MathHelper.clamp(Double.parseDouble(args[0]), 0.0, 10.0);
                        Aimbot.trySetSlider(Aimbot.yPredict, v);
                        Aimbot.guiYPredictLockTicks = 2;
                        Aimbot.lastGuiYPredictValue = Aimbot.yPredict.getInput();
                        Aimbot.yPredictOverrideActive = true;
                        Aimbot.yPredictOverrideValue = v;
                        Utils.sendRawMessage("§a[Aimbot] YPredict set to §e" + Aimbot.fmt3(v));
                    } catch (NumberFormatException ex) {
                        Utils.sendRawMessage("§c[Aimbot] Invalid number. Usage: §f" + this.usage());
                    }
                }
            }
        }
    }
}
