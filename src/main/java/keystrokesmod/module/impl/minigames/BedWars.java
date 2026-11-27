package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import keystrokesmod.Feral;
import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.DragonDivePrediction;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.ObsidianBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.text.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class BedWars extends Module {
    private static final long MAGIC_MILK_DURATION_MS = 30000L;
    private static final long TIME_WARP_DURATION_MS = 5000L;
    private static final long TIME_WARP_TIMEOUT_MS = 30000L;
    private static final int TIME_WARP_COLOR = new Color(210, 0, 255, 64).getRGB();
    private static final long CLOSEST_ENEMY_ANIMATION_DURATION_MS = 100L;
    private static final int DRAGON_COLOR = new Color(214, 91, 191).getRGB();
    private static final int[] DRAGON_BLOCK_FACES =
            new int[] {0, 1, 3, 2, 4, 6, 7, 5, 0, 4, 5, 1, 2, 3, 7, 6, 0, 2, 6, 4, 1, 5, 7, 3};
    private static final int[] DRAGON_BLOCK_EDGES =
            new int[] {0, 1, 0, 2, 0, 4, 1, 3, 1, 5, 2, 3, 2, 6, 3, 7, 4, 5, 4, 6, 5, 7, 6, 7};
    private static final Set<String> SHOPS = new HashSet<>(Arrays.asList(
            "Quick Buy",
            "Blocks",
            "Melee",
            "Armor",
            "Tools",
            "Ranged",
            "Potions",
            "Utility",
            "Rotating Items",
            "Purchase Enemy Tracker"));
    private static final Pattern SCOREBOARD_DATE = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}");
    private static final double OWN_BED_PROTECTION_RADIUS_SQ = 800.0;
    private static final int OWN_BED_SEARCH_HORIZONTAL_RADIUS = 24;
    private static final int OWN_BED_SEARCH_VERTICAL_RADIUS = 6;
    private final SliderSetting closestEnemy;
    private final SliderSetting alertInterval;
    private final ButtonSetting instantShop;
    private final ButtonSetting whitelistOwnBed;
    private final ButtonSetting magicMilkTimer;
    private final ButtonSetting dragon;
    private final ButtonSetting removeGameId;
    private final ButtonSetting targetUnicorn;
    private final ButtonSetting renderTimeWarp;
    private final ButtonSetting armorReminder;
    private ButtonSetting bow;
    private ButtonSetting diamondArmor;
    private ButtonSetting dreamDefender;
    private ButtonSetting fireball;
    private ButtonSetting enderPearl;
    private ButtonSetting knockbackStick;
    private ButtonSetting magicMilk;
    private ButtonSetting obsidian;
    private ButtonSetting potions;
    private ButtonSetting rotationItems;
    private ButtonSetting waterBucket;
    private ButtonSetting ignoreTeammate;
    private ButtonSetting shouldPing;
    private List<String> armoredPlayer = new ArrayList<>();
    private Map<String, String> lastHeldMap = new ConcurrentHashMap<>();
    private Map<String, Long> lastAlertMap = new ConcurrentHashMap<>();
    private Map<BlockPos, Long> obsidianPos = new HashMap<>();
    public List<SkyWars.SpawnEggInfo> entitySpawnQueue = new ArrayList<>();
    public List<Integer> spawnedMobs = new ArrayList<>();
    private BlockPos spawnAnchor;
    private Vec3d ownBedCenter;
    private boolean ownBedDestroyed;
    private boolean pendingSpawnAnchorCapture;
    private boolean waitingForRespawn;
    private int armorCheckTicks = -1;
    private long respawnMessageTime;
    private long magicMilkExpiresAt;
    private final Map<Vec3d, Long> timeWarpPositions = new LinkedHashMap<>();
    private Vec3d pendingTimeWarpPosition;
    private long pendingTimeWarpThrownAt;
    private boolean pendingTimeWarpTeleported;
    private double dragonDistance = -1.0;
    private final Map<EnderDragonEntity, DragonDivePrediction> dragonDivePredictions = new HashMap<>();
    private final Map<EnderDragonEntity, BedWars.DragonConeVisual> dragonConeVisuals = new HashMap<>();
    private float closestEnemyPosX = Float.NaN;
    private float closestEnemyPosY = Float.NaN;
    private float closestEnemyRelativePosX = Float.NaN;
    private float closestEnemyRelativePosY = Float.NaN;
    private final BedWars.EnemyHudLayout closestEnemyLayout = new BedWars.EnemyHudLayout();
    private final BedWars.EnemyHudLayout dragonHudLayout = new BedWars.EnemyHudLayout();
    private float dragonPosX = Float.NaN;
    private float dragonPosY = Float.NaN;
    private float dragonRelativePosX = Float.NaN;
    private float dragonRelativePosY = Float.NaN;
    private float magicMilkPosX = Float.NaN;
    private float magicMilkPosY = Float.NaN;
    private float magicMilkRelativePosX = Float.NaN;
    private float magicMilkRelativePosY = Float.NaN;
    private int OBSIDIAN_COLOR = new Color(106, 13, 173).getRGB();

    public BedWars() {
        super("Bed Wars", Module.category.minigames);
        this.registerSetting(this.instantShop = new ButtonSetting("Instant shop", true));
        this.registerSetting(this.removeGameId = new ButtonSetting("Remove game id", false));
        this.registerSetting(this.targetUnicorn = new ButtonSetting("Target unicorn", false));
        this.registerSetting(this.whitelistOwnBed = new ButtonSetting("Whitelist own bed", true));
        this.registerSetting(new DescriptionSetting("Reminders"));
        this.registerSetting(this.armorReminder = new ButtonSetting("Armor reminder", true));
        this.registerSetting(new DescriptionSetting("Game alerts"));
        this.registerSetting(this.alertInterval = new SliderSetting("Alert interval", " second", 5.0, 0.0, 30.0, 1.0));
        this.registerSetting(this.bow = new ButtonSetting("Bow", true));
        this.registerSetting(this.diamondArmor = new ButtonSetting("Diamond armor", true));
        this.registerSetting(this.dreamDefender = new ButtonSetting("Dream defender", true));
        this.registerSetting(this.fireball = new ButtonSetting("Fireball", false));
        this.registerSetting(this.knockbackStick = new ButtonSetting("Knockback stick", true));
        this.registerSetting(this.magicMilk = new ButtonSetting("Magic milk", true));
        this.registerSetting(this.obsidian = new ButtonSetting("Obsidian", true));
        this.registerSetting(this.enderPearl = new ButtonSetting("Ender pearl", true));
        this.registerSetting(this.potions = new ButtonSetting("Potions", true));
        this.registerSetting(this.rotationItems = new ButtonSetting("Rotation items", true));
        this.registerSetting(this.waterBucket = new ButtonSetting("Water bucket", true));
        this.registerSetting(this.ignoreTeammate = new ButtonSetting("Ignore teammates", false, "Ignore teammate"));
        this.registerSetting(this.shouldPing = new ButtonSetting("Should ping", true));
        this.registerSetting(new DescriptionSetting("HUD"));
        this.registerSetting(this.closestEnemy = new SliderSetting("Closest enemy", true, 1.0, 1.0, 10.0, 1.0));
        this.registerSetting(this.dragon = new ButtonSetting("Dragon", true));
        this.registerSetting(this.magicMilkTimer = new ButtonSetting("Magic milk timer", true));
        this.registerSetting(this.renderTimeWarp = new ButtonSetting("Render time warp", true));
        this.registerSetting(
                new ButtonSetting("Edit positions", () -> mc.openScreen(new BedWars.EditPositionsScreen())));
        this.closetModule = true;
        this.liteModule = true;
    }

    public static boolean shouldTargetUnicorn() {
        return ModuleManager.bedwars != null
                && ModuleManager.bedwars.isEnabled()
                && ModuleManager.bedwars.targetUnicorn.isToggled()
                && Utils.getBedwarsStatus() == 2;
    }

    public String filterScoreboardLine(String line) {
        if (!this.removeGameId.isToggled()) {
            return line;
        }

        String visibleLine = StringUtils.stripFormatting(line);
        Matcher date = SCOREBOARD_DATE.matcher(visibleLine);
        if (date.find() && !visibleLine.substring(date.end()).trim().isEmpty()) {
            int bedWarsStatus = Utils.getBedwarsStatus();
            if (bedWarsStatus != 1 && bedWarsStatus != 2) {
                return line;
            }

            int rawIndex = 0;
            int visibleIndex = 0;

            while (rawIndex < line.length() && visibleIndex < date.end()) {
                if (line.charAt(rawIndex) == 167 && rawIndex + 1 < line.length()) {
                    rawIndex += 2;
                } else {
                    rawIndex++;
                    visibleIndex++;
                }
            }

            return line.substring(0, rawIndex);
        } else {
            return line;
        }
    }

    @Override
    public void onDisable() {
        this.entitySpawnQueue.clear();
        this.spawnedMobs.clear();
        this.lastAlertMap.clear();
        this.resetSpawnTracking();
        this.magicMilkExpiresAt = 0L;
        this.resetTimeWarpTracking();
        this.resetDragonHud();
        this.resetClosestEnemyHud();
        this.armorCheckTicks = -1;
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.dragon && !this.dragon.isToggled()) {
            this.resetDragonHud();
        }

        if (setting == this.renderTimeWarp && !this.renderTimeWarp.isToggled()) {
            this.resetTimeWarpTracking();
        }
    }

    private boolean dragonFeaturesEnabled() {
        return this.dragon.isToggled() && (Feral.DEBUG || Utils.getBedwarsStatus() == 2);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        this.renderTimeWarpMarkers();
        this.dragonDistance = -1.0;
        if (Utils.nullCheck() && this.dragonFeaturesEnabled()) {
            boolean bedWarsRules = Utils.getBedwarsStatus() == 2;
            Set<EnderDragonEntity> activeDragons = new HashSet<>();

            for (Entity entity : mc.world.entities) {
                if (entity instanceof EnderDragonEntity) {
                    EnderDragonEntity dragonEntity = (EnderDragonEntity) entity;
                    activeDragons.add(dragonEntity);
                    this.renderDragon(dragonEntity, e.partialTicks);
                    DragonDivePrediction prediction =
                            this.dragonDivePredictions.computeIfAbsent(dragonEntity, DragonDivePrediction::new);
                    this.renderDragonDive(dragonEntity, prediction.update(bedWarsRules), e.partialTicks);
                    double distance = Math.sqrt(mc.player.squaredDistanceTo(entity));
                    if (this.dragonDistance < 0.0 || distance < this.dragonDistance) {
                        this.dragonDistance = distance;
                    }
                }
            }

            this.dragonDivePredictions.keySet().retainAll(activeDragons);
            this.dragonConeVisuals.keySet().retainAll(activeDragons);
        } else {
            this.dragonDivePredictions.clear();
            this.dragonConeVisuals.clear();
        }

        if (Utils.nullCheck() && this.obsidian.isToggled()) {
            if (this.obsidianPos.isEmpty()) {
                return;
            }

            try {
                List<BlockPos> blocksToRender = new ArrayList<>();
                Iterator<Entry<BlockPos, Long>> iterator =
                        this.obsidianPos.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<BlockPos, Long> entry = iterator.next();
                    BlockPos blockPos = entry.getKey();
                    Long receivedMs = entry.getValue();
                    if (!(mc.world.getBlockState(blockPos).getBlock() instanceof ObsidianBlock)
                            && Utils.timeBetween(System.currentTimeMillis(), receivedMs) >= 500L) {
                        iterator.remove();
                    } else {
                        blocksToRender.add(blockPos);
                    }
                }

                this.renderObsidianOverlays(this.mergeObsidianBounds(blocksToRender));
            } catch (Exception var10) {
            }
        }
    }

    private void renderTimeWarpMarkers() {
        if (Utils.nullCheck() && this.renderTimeWarp.isToggled() && Utils.getBedwarsStatus() == 2) {
            long now = System.currentTimeMillis();
            Iterator<Entry<Vec3d, Long>> iterator =
                    this.timeWarpPositions.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Vec3d, Long> entry = iterator.next();
                if (now >= entry.getValue()) {
                    iterator.remove();
                } else {
                    RenderUtils.drawPlayerBoundingBox(entry.getKey(), TIME_WARP_COLOR);
                }
            }

            if (this.pendingTimeWarpPosition != null
                    && !this.timeWarpPositions.containsKey(this.pendingTimeWarpPosition)) {
                this.pendingTimeWarpPosition = null;
                this.pendingTimeWarpThrownAt = 0L;
                this.pendingTimeWarpTeleported = false;
            }
        } else {
            this.resetTimeWarpTracking();
        }
    }

    private void startTimeWarpCountdown(boolean confirmedByChat) {
        if (Utils.nullCheck()
                && this.renderTimeWarp.isToggled()
                && Utils.getBedwarsStatus() == 2
                && this.pendingTimeWarpPosition != null) {
            long now = System.currentTimeMillis();
            Long expiresAt = this.timeWarpPositions.get(this.pendingTimeWarpPosition);
            if (expiresAt != null && now < expiresAt) {
                this.timeWarpPositions.put(
                        this.pendingTimeWarpPosition, Math.min(this.pendingTimeWarpThrownAt + 30000L, now + 5000L));
                if (confirmedByChat) {
                    this.pendingTimeWarpPosition = null;
                    this.pendingTimeWarpThrownAt = 0L;
                    this.pendingTimeWarpTeleported = false;
                } else {
                    this.pendingTimeWarpTeleported = true;
                }
            }
        }
    }

    private void resetTimeWarpTracking() {
        this.timeWarpPositions.clear();
        this.pendingTimeWarpPosition = null;
        this.pendingTimeWarpThrownAt = 0L;
        this.pendingTimeWarpTeleported = false;
    }

    private void renderDragon(EnderDragonEntity entity, float partialTicks) {
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        double x = entity.prevX + (entity.x - entity.prevX) * partialTicks - renderManager.cameraX;
        double y = entity.prevY + (entity.y - entity.prevY) * partialTicks - renderManager.cameraY;
        double z = entity.prevZ + (entity.z - entity.prevZ) * partialTicks - renderManager.cameraZ;
        Box bounds = entity.getShape().grown(2.0, 0.0, 2.0);
        Box box = new Box(
                bounds.minX - entity.x + x,
                bounds.minY - entity.y + y,
                bounds.minZ - entity.z + z,
                bounds.maxX - entity.x + x,
                bounds.maxY - entity.y + y - 3.0,
                bounds.maxZ - entity.z + z);
        GL11.glPushAttrib(1048575);

        try {
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glColor4f(
                    (DRAGON_COLOR >> 16 & 0xFF) / 255.0F,
                    (DRAGON_COLOR >> 8 & 0xFF) / 255.0F,
                    (DRAGON_COLOR & 0xFF) / 255.0F,
                    1.0F);
            GL11.glEnable(2848);
            GL11.glLineWidth(2.0F);
            WorldRenderer.renderOutlineShape(box);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void renderDragonDive(
            EnderDragonEntity entity, DragonDivePrediction.Result prediction, float partialTicks) {
        BedWars.DragonConeVisual visual =
                this.dragonConeVisuals.computeIfAbsent(entity, ignored -> new BedWars.DragonConeVisual());
        Vec3d markerImpact = visual.update(prediction);
        if (markerImpact != null || !prediction.blocks.isEmpty()) {
            EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
            GL11.glPushAttrib(1048575);

            try {
                GL11.glEnable(3042);
                GL11.glBlendFunc(770, 771);
                GL11.glDisable(3553);
                GL11.glDisable(2896);
                GL11.glDisable(2912);
                GL11.glDisable(2884);
                GL11.glDisable(2929);
                GL11.glDepthMask(false);
                GL11.glEnable(2848);
                GL11.glLineWidth(1.5F);
                GL11.glColor4f(0.62F, 0.25F, 1.0F, 0.14F);
                GL11.glBegin(7);

                for (BlockPos pos : prediction.blocks) {
                    if (!mc.world.isAir(pos)) {
                        this.emitDragonBlockVertices(pos, DRAGON_BLOCK_FACES, renderManager);
                    }
                }

                GL11.glEnd();
                GL11.glColor4f(0.72F, 0.36F, 1.0F, 0.65F);
                GL11.glBegin(1);

                for (BlockPos pos : prediction.blocks) {
                    if (!mc.world.isAir(pos)) {
                        this.emitDragonBlockVertices(pos, DRAGON_BLOCK_EDGES, renderManager);
                    }
                }

                GL11.glEnd();
                if (markerImpact != null) {
                    Box head = entity.head.getShape();
                    Vec3d headOrigin = new Vec3d(
                            (head.minX + head.maxX) * 0.5 + (entity.prevX - entity.x) * (1.0 - partialTicks),
                            (head.minY + head.maxY) * 0.5 + (entity.prevY - entity.y) * (1.0 - partialTicks),
                            (head.minZ + head.maxZ) * 0.5 + (entity.prevZ - entity.z) * (1.0 - partialTicks));
                    GL11.glColor4f(0.68F, 0.3F, 1.0F, 0.65F);
                    GL11.glBegin(3);

                    for (int i = 0; i < prediction.path.size(); i++) {
                        Vec3d point = i == prediction.path.size() - 1
                                ? markerImpact
                                : (i == 0 ? headOrigin : prediction.path.get(i));
                        GL11.glVertex3d(
                                point.x - renderManager.cameraX,
                                point.y - renderManager.cameraY,
                                point.z - renderManager.cameraZ);
                    }

                    GL11.glEnd();
                    double x = markerImpact.x - renderManager.cameraX;
                    double y = markerImpact.y - renderManager.cameraY;
                    double z = markerImpact.z - renderManager.cameraZ;
                    double rotation = (entity.ticks + partialTicks) * 0.1;
                    double directionX = markerImpact.x - headOrigin.x;
                    double directionY = markerImpact.y - headOrigin.y;
                    double directionZ = markerImpact.z - headOrigin.z;
                    double yaw = Math.toDegrees(Math.atan2(directionX, -directionZ));
                    double pitch = Math.toDegrees(
                            Math.atan2(directionY, Math.sqrt(directionX * directionX + directionZ * directionZ)));
                    double radius = 0.85;
                    double length = 1.8;
                    int sides = 16;
                    GL11.glPushMatrix();

                    try {
                        GL11.glTranslated(x, y, z);
                        GL11.glRotated(-yaw, 0.0, 1.0, 0.0);
                        GL11.glRotated(pitch, 1.0, 0.0, 0.0);
                        GL11.glRotated(Math.toDegrees(rotation), 0.0, 0.0, 1.0);
                        GL11.glBegin(4);

                        for (int i = 0; i < sides; i++) {
                            double angle = i * Math.PI * 2.0 / sides;
                            double nextAngle = (i + 1) * Math.PI * 2.0 / sides;
                            float shade = i % 2 == 0 ? 1.0F : 0.72F;
                            GL11.glColor4f(0.62F * shade, 0.25F * shade, shade, 0.42F);
                            GL11.glVertex3d(0.0, 0.0, 0.0);
                            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, length);
                            GL11.glVertex3d(Math.cos(nextAngle) * radius, Math.sin(nextAngle) * radius, length);
                        }

                        GL11.glEnd();
                        GL11.glColor4f(0.82F, 0.5F, 1.0F, 0.95F);
                        GL11.glLineWidth(2.0F);
                        GL11.glBegin(2);

                        for (int i = 0; i < sides; i++) {
                            double angle = i * Math.PI * 2.0 / sides;
                            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, length);
                        }

                        GL11.glEnd();
                        GL11.glBegin(1);

                        for (int i = 0; i < sides; i += 4) {
                            double angle = i * Math.PI * 2.0 / sides;
                            GL11.glVertex3d(0.0, 0.0, 0.0);
                            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, length);
                        }

                        GL11.glEnd();
                        return;
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    private void emitDragonBlockVertices(BlockPos pos, int[] vertices, EntityRenderDispatcher renderManager) {
        double x = pos.getX() - renderManager.cameraX;
        double y = pos.getY() - renderManager.cameraY;
        double z = pos.getZ() - renderManager.cameraZ;

        for (int vertex : vertices) {
            GL11.glVertex3d(
                    x + ((vertex & 1) == 0 ? -0.002 : 1.002),
                    y + ((vertex & 2) == 0 ? -0.002 : 1.002),
                    z + ((vertex & 4) == 0 ? -0.002 : 1.002));
        }
    }

    private List<Box> mergeObsidianBounds(List<BlockPos> blocks) {
        List<Box> bounds = new ArrayList<>();

        for (BlockPos pos : blocks) {
            bounds.add(
                    new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
        }

        boolean merged;
        label32:
        do {
            merged = false;

            for (int i = 0; i < bounds.size(); i++) {
                for (int j = i + 1; j < bounds.size(); j++) {
                    Box combined = this.mergeAdjacentBounds(bounds.get(i), bounds.get(j));
                    if (combined != null) {
                        bounds.set(i, combined);
                        bounds.remove(j);
                        merged = true;
                        continue label32;
                    }
                }
            }
        } while (merged);

        return bounds;
    }

    private Box mergeAdjacentBounds(Box first, Box second) {
        boolean sameX = first.minX == second.minX && first.maxX == second.maxX;
        boolean sameY = first.minY == second.minY && first.maxY == second.maxY;
        boolean sameZ = first.minZ == second.minZ && first.maxZ == second.maxZ;
        if (!sameY || !sameZ || first.maxX != second.minX && second.maxX != first.minX) {
            if (!sameX || !sameZ || first.maxY != second.minY && second.maxY != first.minY) {
                return !sameX || !sameY || first.maxZ != second.minZ && second.maxZ != first.minZ
                        ? null
                        : new Box(
                                first.minX,
                                first.minY,
                                Math.min(first.minZ, second.minZ),
                                first.maxX,
                                first.maxY,
                                Math.max(first.maxZ, second.maxZ));
            } else {
                return new Box(
                        first.minX,
                        Math.min(first.minY, second.minY),
                        first.minZ,
                        first.maxX,
                        Math.max(first.maxY, second.maxY),
                        first.maxZ);
            }
        } else {
            return new Box(
                    Math.min(first.minX, second.minX),
                    first.minY,
                    first.minZ,
                    Math.max(first.maxX, second.maxX),
                    first.maxY,
                    first.maxZ);
        }
    }

    private void renderObsidianOverlays(List<Box> obsidianBounds) {
        if (!obsidianBounds.isEmpty()) {
            float red = (this.OBSIDIAN_COLOR >> 16 & 0xFF) / 255.0F;
            float green = (this.OBSIDIAN_COLOR >> 8 & 0xFF) / 255.0F;
            float blue = (this.OBSIDIAN_COLOR & 0xFF) / 255.0F;
            EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
            GL11.glPushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);

            try {
                for (Box bounds : obsidianBounds) {
                    Box renderedBounds =
                            bounds.moved(-renderManager.cameraX, -renderManager.cameraY, -renderManager.cameraZ);
                    RenderUtils.drawBoundingBox(renderedBounds, red, green, blue, 0.25F);
                }
            } finally {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glEnable(3553);
                GL11.glEnable(2929);
                GL11.glDepthMask(true);
                GL11.glDisable(3042);
                GL11.glPopMatrix();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END
                && Utils.nullCheck()
                && mc.screen == null
                && (!mc.options.debugEnabled || Feral.DEBUG)) {
            boolean playingBedWars = Utils.getBedwarsStatus() == 2;
            long remainingMilkTime = this.magicMilkExpiresAt - System.currentTimeMillis();
            boolean showMagicMilkTimer = !mc.options.debugEnabled
                    && playingBedWars
                    && this.magicMilkTimer.isToggled()
                    && this.magicMilkExpiresAt > 0L
                    && remainingMilkTime > -2000L;
            boolean showClosestEnemy =
                    !mc.options.debugEnabled && playingBedWars && this.closestEnemy.getInput() != -1.0;
            boolean showDragon = this.dragonFeaturesEnabled() && this.dragonDistance >= 0.0;
            if (showClosestEnemy || showMagicMilkTimer || showDragon) {
                Window resolution = new Window(mc);
                this.syncHudPositions(resolution);
                if (showDragon) {
                    this.drawDragonHudBox(this.dragonDistance, this.dragonPosX, this.dragonPosY);
                }

                if (showClosestEnemy) {
                    Vec3d bedReference = !this.ownBedDestroyed ? this.getOwnBedReference() : null;
                    List<PlayerEntity> enemies =
                            this.findClosestEnemies(bedReference, (int) this.closestEnemy.getInput());
                    List<BedWars.EnemyHudEntry> entries = new ArrayList<>();

                    for (PlayerEntity enemy : enemies) {
                        double distance = this.distanceToReference(enemy, bedReference);
                        String name = enemy.getDisplayName().getString();
                        String distanceText = this.formatDistance(distance);
                        entries.add(new BedWars.EnemyHudEntry(name, distanceText, enemy.getDisplayItemInHand()));
                    }

                    this.drawEnemyHudBox(entries, this.closestEnemyPosX, this.closestEnemyPosY);
                }

                if (showMagicMilkTimer) {
                    String text = remainingMilkTime > 0L
                            ? String.format(Locale.ROOT, "§7Magic Milk: §a%.1fs", remainingMilkTime / 1000.0)
                            : "§7Magic milk: §cFinished";
                    this.drawHudBox(text, this.magicMilkPosX, this.magicMilkPosY);
                }
            }
        }
    }

    private BedWars.HudBoxBounds drawHudBox(String text, float x, float textY) {
        float horizontalPadding = 3.0F;
        float verticalPadding = 4.0F;
        float textX = x + horizontalPadding;
        float textWidth = mc.textRenderer.getWidth(text);
        float left = x - 10.0F;
        float top = textY - verticalPadding;
        float right = textX + textWidth + horizontalPadding + 1.0F;
        float bottom = textY + mc.textRenderer.fontHeight + verticalPadding - 1.0F;
        RenderUtils.drawRoundedRectangle(left, top, right, bottom, 7.0F, 2013265920);
        this.enableClosestEnemyAlphaBlending();

        try {
            mc.textRenderer.draw(text, textX, textY, Color.WHITE.getRGB(), true);
        } finally {
            GlStateManager.disableBlend();
        }

        return new BedWars.HudBoxBounds(left, top, right, bottom);
    }

    private BedWars.HudBoxBounds drawDragonHudBox(double distance, float x, float textY) {
        char color = (char)
                (distance <= 25.0
                        ? 99
                        : (distance <= 50.0 ? 54 : (distance <= 75.0 ? 101 : (distance <= 125.0 ? 97 : 50))));
        String text = "§7Dragon: §" + color + (int) distance + "m";
        this.dragonHudLayout.update(mc.textRenderer.getWidth(text), 0.0F, 0.0F);
        Window resolution = new Window(mc);
        float left = x - 5.0F;
        float right = x + this.dragonHudLayout.width + 4.0F;
        float top = textY - 5.0F;
        float bottom = textY + mc.textRenderer.fontHeight + 3.5F;
        if (left <= 0.0F) {
            left = -3.0F;
        }

        if (right > resolution.getWidth()) {
            right = resolution.getWidth() + 3.0F;
        }

        right -= 0.5F;
        GL11.glPushAttrib(548865);

        try {
            this.enableClosestEnemyAlphaBlending();
            RenderUtils.drawRoundedRectangle(left, top, right, bottom, 8.0F, 2097152000);
            this.scissorEnemyHud(left, top, right, bottom, resolution);
            mc.textRenderer.draw(text, x, textY, -1, true);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.disableBlend();
        }

        return new BedWars.HudBoxBounds(left, top, right, bottom);
    }

    private void resetDragonHud() {
        this.dragonDistance = -1.0;
        this.dragonHudLayout.initialized = false;
        this.dragonDivePredictions.clear();
        this.dragonConeVisuals.clear();
    }

    private void renderHeldItem(ItemStack heldItem, float x, float y) {
        if (heldItem.getItem() != null) {
            ItemRenderer renderItem = mc.getItemRenderer();
            float previousZLevel = renderItem.zOffset;
            boolean previousDepthEnabled = GL11.glIsEnabled(2929);
            boolean previousDepthMask = GL11.glGetBoolean(2930);
            int previousDepthFunc = GL11.glGetInteger(2932);
            double previousClearDepth = GL11.glGetDouble(2931);
            boolean previousAlphaEnabled = GL11.glIsEnabled(3008);
            int previousAlphaFunc = GL11.glGetInteger(3009);
            float previousAlphaRef = GL11.glGetFloat(3010);
            Lighting.turnOnGui();
            renderItem.zOffset = -100.0F;
            BakedModel model = renderItem.getModelShaper().getModel(heldItem);
            GlStateManager.pushMatrix();
            mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            mc.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);

            try {
                GlStateManager.enableAlphaTest();
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.enableDepthTest();
                GlStateManager.depthMask(true);
                GlStateManager.depthFunc(515);
                GlStateManager.clearDepth(1.0);
                GlStateManager.clear(256);
                GlStateManager.enableRescaleNormal();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(770, 771);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                RenderUtils.drawGuiItemModel(renderItem, heldItem, model, x, y);
            } finally {
                GlStateManager.disableBlend();
                GlStateManager.disableRescaleNormal();
                GlStateManager.disableLighting();
                GlStateManager.popMatrix();
                mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
                mc.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).popFilter();
                renderItem.zOffset = previousZLevel;
                Lighting.turnOff();
                GlStateManager.depthFunc(previousDepthFunc);
                GlStateManager.depthMask(previousDepthMask);
                GlStateManager.clearDepth(previousClearDepth);
                if (previousDepthEnabled) {
                    GlStateManager.enableDepthTest();
                } else {
                    GlStateManager.disableDepthTest();
                }

                GlStateManager.alphaFunc(previousAlphaFunc, previousAlphaRef);
                if (previousAlphaEnabled) {
                    GlStateManager.enableAlphaTest();
                } else {
                    GlStateManager.disableAlphaTest();
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onShopMouseInput(ForgeEvents.GuiScreenEvent.MouseInputEvent.Pre event) {
        if (this.instantShop.isToggled()
                && Utils.nullCheck()
                && Utils.getBedwarsStatus() == 2
                && event.gui instanceof ChestScreen
                && mc.player.menu instanceof ChestMenu
                && Mouse.getEventButton() == 0
                && Mouse.getEventButtonState()
                && !Screen.isShiftDown()
                && (ModuleManager.inventory == null || !ModuleManager.inventory.shouldCancelManualInventoryInput())) {
            ChestMenu container = (ChestMenu) mc.player.menu;
            Inventory chest = container.getChest();
            if (SHOPS.contains(chest.getName())) {
                ChestScreen gui = (ChestScreen) event.gui;
                int guiLeft = (gui.width - 176) / 2;
                int guiTop = (gui.height - (114 + chest.getSize() / 9 * 18)) / 2;
                int mouseX = Mouse.getEventX() * gui.width / mc.width - guiLeft;
                int mouseY = gui.height - Mouse.getEventY() * gui.height / mc.height - 1 - guiTop;

                for (InventorySlot slot : container.slots) {
                    if (mouseX >= slot.x - 1 && mouseX < slot.x + 17 && mouseY >= slot.y - 1 && mouseY < slot.y + 17) {
                        mc.interactionManager.clickSlot(container.networkId, slot.index, 2, 3, mc.player);
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onItemUseFinish(ForgeEvents.PlayerUseItemEvent.Finish event) {
        if (this.magicMilkTimer.isToggled()
                && Utils.nullCheck()
                && Utils.getBedwarsStatus() == 2
                && event.entityPlayer == mc.player
                && event.item != null
                && event.item.getItem() == Items.MILK_BUCKET) {
            this.magicMilkExpiresAt = System.currentTimeMillis() + 30000L;
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onUseItem(UseItemEvent event) {
        if (this.renderTimeWarp.isToggled()
                && Utils.nullCheck()
                && Utils.getBedwarsStatus() == 2
                && event.usedItemStack != null
                && event.usedItemStack.getItem() instanceof EnderPearlItem
                && Utils.stripString(event.usedItemStack.getHoverName()).equals("Time Warp Pearl")) {
            this.pendingTimeWarpPosition = new Vec3d(mc.player.x, mc.player.y, mc.player.z);
            this.pendingTimeWarpThrownAt = System.currentTimeMillis();
            this.pendingTimeWarpTeleported = false;
            this.timeWarpPositions.put(this.pendingTimeWarpPosition, this.pendingTimeWarpThrownAt + 30000L);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (mc.isOnSameThread() && !this.pendingTimeWarpTeleported) {
            this.startTimeWarpCountdown(false);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.armoredPlayer.clear();
            this.lastHeldMap.clear();
            this.lastAlertMap.clear();
            this.obsidianPos.clear();
            this.entitySpawnQueue.clear();
            this.spawnedMobs.clear();
            this.resetSpawnTracking();
            this.magicMilkExpiresAt = 0L;
            this.resetTimeWarpTracking();
            this.resetDragonHud();
            this.resetClosestEnemyHud();
            this.armorCheckTicks = -1;
        } else if (e.entity instanceof IronGolemEntity) {
            if (Utils.getBedwarsStatus() != 2) {
                return;
            }

            Vec3d spawnPosition = new Vec3d(e.entity.x, e.entity.y, e.entity.z);

            for (SkyWars.SpawnEggInfo eggInfo : this.entitySpawnQueue) {
                if (eggInfo.spawnPos.distanceTo(spawnPosition) > 3.0
                        || Utils.timeBetween(mc.player.ticks, eggInfo.tickSpawned) > 60L) {
                    return;
                }

                if (!this.entitySpawnQueue.remove(eggInfo)) {
                    return;
                }

                this.spawnedMobs.add(e.entity.getNetworkId());
            }
        }
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck()) {
            if (this.pendingSpawnAnchorCapture && Utils.getBedwarsStatus() == 2) {
                this.spawnAnchor = mc.player.getCommandSourceBlockPos();
                this.ownBedCenter = this.findOwnBedCenter();
                this.pendingSpawnAnchorCapture = false;
            }

            this.updateArmorReminder();
            if (Utils.getBedwarsStatus() == 2
                    && (this.bow.isToggled()
                            || this.diamondArmor.isToggled()
                            || this.dreamDefender.isToggled()
                            || this.enderPearl.isToggled()
                            || this.fireball.isToggled()
                            || this.knockbackStick.isToggled()
                            || this.magicMilk.isToggled()
                            || this.obsidian.isToggled()
                            || this.potions.isToggled()
                            || this.rotationItems.isToggled()
                            || this.waterBucket.isToggled())) {
                for (PlayerEntity p : mc.world.players) {
                    if (p != null
                            && p != mc.player
                            && !AntiBot.isBot(p)
                            && (!this.ignoreTeammate.isToggled() || !Utils.isTeammate(p))) {
                        String name = p.getName();
                        ItemStack item = p.getDisplayItemInHand();
                        if (this.diamondArmor.isToggled()) {
                            ItemStack leggings = p.inventory.armor[1];
                            if (!this.armoredPlayer.contains(name)
                                    && leggings != null
                                    && leggings.getItem() != null
                                    && leggings.getItem() == Items.DIAMOND_LEGGINGS) {
                                this.armoredPlayer.add(name);
                                double distance = mc.player.distanceTo(p);
                                Utils.sendMessage(p.getDisplayName().getString() + " &7has purchased &bDiamond Armor "
                                        + this.formatDistance(distance));
                                this.ping();
                            }
                        }

                        if (item != null && !this.lastHeldMap.containsKey(name)) {
                            String itemType = this.getItemType(item);
                            if (itemType != null) {
                                this.lastHeldMap.put(name, itemType);
                                double distance = mc.player.distanceTo(p);
                                this.handleAlert(p, itemType, distance);
                            }
                        } else if (this.lastHeldMap.containsKey(name)) {
                            String itemType = this.lastHeldMap.get(name);
                            if (!itemType.equals(this.getItemType(item))) {
                                this.lastHeldMap.remove(name);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateArmorReminder() {
        if (this.armorCheckTicks < 0 || this.armorCheckTicks-- > 0) {
            return;
        }

        if (this.armorReminder.isToggled() && Utils.getBedwarsStatus() == 2 && !this.hasIronArmorOrBetter()) {
            Utils.sendMessage("&bbuy armor?");
            ping();
        }
    }

    // Bed Wars armor upgrades swap the leggings and boots; the helmet and chestplate stay leather.
    private boolean hasIronArmorOrBetter() {
        ItemStack leggings = mc.player.inventory.armor[1];
        return leggings != null
                && (leggings.getItem() == Items.IRON_LEGGINGS || leggings.getItem() == Items.DIAMOND_LEGGINGS);
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent event) {
        if (Utils.nullCheck()) {
            String strippedMessage = Utils.stripColor(event.message.getString());
            if (event.type != 2 && strippedMessage.trim().equals("You will be warped back in 5 seconds!")) {
                this.startTimeWarpCountdown(true);
            }

            if (strippedMessage.startsWith(" ")
                    && strippedMessage.contains("Protect your bed and destroy the enemy beds.")) {
                this.ownBedDestroyed = false;
                this.ownBedCenter = null;
                this.pendingSpawnAnchorCapture = true;
                this.waitingForRespawn = false;
                this.magicMilkExpiresAt = 0L;
                this.resetTimeWarpTracking();
            } else if (strippedMessage.equals("You will respawn because you still have a bed!")) {
                this.waitingForRespawn = true;
                this.respawnMessageTime = System.currentTimeMillis();
            } else if (strippedMessage.equals("You have respawned!")
                    && Utils.getBedwarsStatus() == 2
                    && this.armorReminder.isToggled()) {
                // Give the server a moment to put the permanent armor back on before checking.
                this.armorCheckTicks = 10;
            }

            if (strippedMessage.equals("You have respawned!")
                    && this.waitingForRespawn
                    && Utils.timeBetween(System.currentTimeMillis(), this.respawnMessageTime) <= 12000L) {
                this.pendingSpawnAnchorCapture = true;
                this.waitingForRespawn = false;
            }

            if (strippedMessage.trim().startsWith("BED DESTRUCTION > Your Bed")) {
                this.ownBedDestroyed = true;
                this.waitingForRespawn = false;
            }
        }
    }

    public boolean isOwnBed(BlockPos[] pair) {
        return this.ownBedCenter != null
                && Utils.getBedwarsStatus() == 2
                && pair != null
                && pair.length >= 2
                && this.bedCenter(pair).squaredDistanceTo(this.ownBedCenter) <= 0.25;
    }

    public void removeOwnBedPair(List<BlockPos[]> bedPairs) {
        if (this.shouldWhitelistOwnBed() && !bedPairs.isEmpty()) {
            BlockPos[] ownBedPair = null;
            double closestDistance = Double.POSITIVE_INFINITY;
            Vec3d spawnCenter = this.spawnAnchorCenter();

            for (BlockPos[] pair : bedPairs) {
                double distance = spawnCenter.squaredDistanceTo(this.bedCenter(pair));
                if (distance < closestDistance) {
                    closestDistance = distance;
                    ownBedPair = pair;
                }
            }

            if (ownBedPair != null) {
                this.ownBedCenter = this.bedCenter(ownBedPair);
                bedPairs.remove(ownBedPair);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket p = (PlayerUseC2SPacket) e.getPacket();
            if (p.getFace() != 255
                    && p.getItemInHand() != null
                    && p.getItemInHand().getItem() != null
                    && p.getItemInHand().getItem() instanceof SpawnEggItem) {
                Class<? extends Entity> oclass = (Class<? extends Entity>)
                        Entities.KEY_TO_TYPE.get(ClientPlatform.spawnEggEntityName(p.getItemInHand()));
                if (oclass == null) {
                    return;
                }

                if (oclass == IronGolemEntity.class) {
                    this.entitySpawnQueue.add(new SkyWars.SpawnEggInfo(p.getPos(), mc.player.ticks));
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket p = (BlockUpdateS2CPacket) e.getPacket();
            if (p.getBlockState() != null
                    && p.getBlockState().getBlock() instanceof ObsidianBlock
                    && this.isNextToBed(p.getPos())) {
                this.obsidianPos.put(p.getPos(), System.currentTimeMillis());
            }
        }
    }

    private boolean isNextToBed(BlockPos blockPos) {
        for (Direction enumFacing : Direction.values()) {
            BlockPos offset = blockPos.offset(enumFacing);
            if (BlockUtils.getBlock(offset) instanceof BedBlock) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldWhitelistOwnBed() {
        return this.whitelistOwnBed.isToggled()
                && this.spawnAnchor != null
                && Utils.getBedwarsStatus() == 2
                && mc.player.getSquaredDistanceTo(this.spawnAnchor) <= 800.0;
    }

    private Vec3d spawnAnchorCenter() {
        return new Vec3d(this.spawnAnchor.getX() + 0.5, this.spawnAnchor.getY() + 0.5, this.spawnAnchor.getZ() + 0.5);
    }

    private Vec3d getOwnBedReference() {
        return this.ownBedCenter != null
                ? this.ownBedCenter
                : (this.spawnAnchor == null ? null : this.spawnAnchorCenter());
    }

    private Vec3d findOwnBedCenter() {
        if (this.spawnAnchor == null) {
            return null;
        }

        Vec3d spawnCenter = this.spawnAnchorCenter();
        Vec3d closestBedCenter = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (int x = -24; x <= 24; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos position = this.spawnAnchor.add(x, y, z);
                    if (BlockUtils.getBlock(position) instanceof BedBlock) {
                        Vec3d center = new Vec3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
                        double distance = spawnCenter.squaredDistanceTo(center);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestBedCenter = center;
                        }
                    }
                }
            }
        }

        return closestBedCenter;
    }

    private List<PlayerEntity> findClosestEnemies(Vec3d bedReference, int players) {
        Map<UUID, PlayerEntity> enemiesById = new LinkedHashMap<>();

        for (PlayerEntity player : mc.world.players) {
            if (player != null
                    && player != mc.player
                    && player.isAlive()
                    && !player.isSpectator()
                    && !AntiBot.isBot(player)
                    && !Utils.isTeammate(player)
                    && !Utils.isFriended(player)) {
                UUID playerId = player.getUuid();
                PlayerEntity existing = enemiesById.get(playerId);
                if (existing == null
                        || player.ticks < existing.ticks
                        || player.ticks == existing.ticks && player.getNetworkId() > existing.getNetworkId()) {
                    enemiesById.put(playerId, player);
                }
            }
        }

        List<PlayerEntity> enemies = new ArrayList<>(enemiesById.values());
        enemies.sort(Comparator.comparingDouble(player -> this.distanceToReference(player, bedReference)));
        int amount = Math.min(Math.max(players, 0), enemies.size());
        return new ArrayList<>(enemies.subList(0, amount));
    }

    private double distanceToReference(PlayerEntity player, Vec3d bedReference) {
        return bedReference == null
                ? mc.player.distanceTo(player)
                : player.distanceTo(bedReference.x, bedReference.y, bedReference.z);
    }

    private Vec3d bedCenter(BlockPos[] pair) {
        return new Vec3d(
                (pair[0].getX() + pair[1].getX() + 1.0) * 0.5,
                (pair[0].getY() + pair[1].getY() + 1.0) * 0.5,
                (pair[0].getZ() + pair[1].getZ() + 1.0) * 0.5);
    }

    private void resetSpawnTracking() {
        this.spawnAnchor = null;
        this.ownBedCenter = null;
        this.ownBedDestroyed = false;
        this.pendingSpawnAnchorCapture = false;
        this.waitingForRespawn = false;
        this.respawnMessageTime = 0L;
    }

    private void resetClosestEnemyHud() {
        this.closestEnemyLayout.initialized = false;
    }

    private String getItemType(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return null;
        }

        if (item.getItem() instanceof EnderPearlItem && this.enderPearl.isToggled()) {
            return "&3Ender Pearl";
        }

        if (!(item.getItem() instanceof BowItem) || !this.bow.isToggled()) {
            if (item.getItem() == Items.MILK_BUCKET && this.magicMilk.isToggled()) {
                return "&fMagic Milk";
            }

            if (item.getItem() == Items.STICK && item.hasEnchantments() && this.knockbackStick.isToggled()) {
                return "&6Knockback Stick";
            }

            if (item.getItem() instanceof SpawnEggItem
                    && item.getHoverName().contains("Dream Defender")
                    && this.dreamDefender.isToggled()) {
                return "&fDream Defender";
            }

            if (item.getItem() instanceof PotionItem && this.potions.isToggled()) {
                String potionType = this.getPotionType(item);
                if (potionType != null) {
                    String color = potionType.equals("Invis") ? "&d" : (potionType.equals("Jump") ? "&a" : "&b");
                    return color + potionType + " &7Potion";
                }
            } else {
                if (item.getItem() == Items.WATER_BUCKET && this.waterBucket.isToggled()) {
                    return "&bWater Bucket";
                }

                if (item.getItem() == Item.byBlock(Blocks.OBSIDIAN) && this.obsidian.isToggled()) {
                    return "&dObsidian";
                }

                if (item.getItem() instanceof FireChargeItem && this.fireball.isToggled()) {
                    return "&6Fireball";
                }

                if (this.rotationItems.isToggled()) {
                    if (item.getItem() == Items.PRISMARINE_SHARD) {
                        return "&3Block Zapper";
                    }

                    if (item.getItem() == Items.GOLDEN_HORSE_ARMOR) {
                        return "&eCharlie Unicorn";
                    }

                    if (item.getItem() == Item.byBlock(Blocks.ICE)) {
                        return "&bIce Bridge";
                    }

                    if (item.getItem() == Items.SNOWBALL && item.getHoverName().contains("Sleeping Dust")) {
                        return "&fSleeping Dust";
                    }
                }
            }

            return null;
        } else if (EnchantmentHelper.getLevel(Enchantment.PUNCH.id, item) > 0) {
            return "&6Punch Bow";
        } else {
            return EnchantmentHelper.getLevel(Enchantment.POWER.id, item) > 0 ? "&6Power Bow" : "&6Bow";
        }
    }

    private String getPotionType(ItemStack item) {
        List<StatusEffectInstance> effects = ((PotionItem) item.getItem()).getPotionEffects(item);
        if (effects == null) {
            return null;
        }

        for (StatusEffectInstance effect : effects) {
            if (effect.getId() == StatusEffect.SPEED.id) {
                return "Speed";
            }

            if (effect.getId() == StatusEffect.INVISIBILITY.id) {
                return "Invis";
            }

            if (effect.getId() == StatusEffect.JUMP_BOOST.id) {
                return "Jump";
            }
        }

        return null;
    }

    private void handleAlert(PlayerEntity player, String itemType, double distance) {
        long currentTime = System.currentTimeMillis();
        long interval = Math.round(this.alertInterval.getInput() * 1000.0);
        String alertKey = player.getUuid() + ":" + itemType;
        Long lastAlert = this.lastAlertMap.get(alertKey);
        if (lastAlert == null || currentTime - lastAlert >= interval) {
            this.lastAlertMap.put(alertKey, currentTime);
            String alert = player.getDisplayName().getString() + " &7is holding " + itemType + " "
                    + this.formatDistance(distance);
            Utils.sendMessage(alert);
            this.ping();
        }
    }

    private String formatDistance(double distance) {
        char color = (char)
                (distance <= 10.0
                        ? 99
                        : (distance <= 25.0 ? 54 : (distance <= 50.0 ? 101 : (distance <= 100.0 ? 97 : 50))));
        return "§" + color + (int) distance + "m";
    }

    public float getClosestEnemyPosX() {
        this.syncHudPositions();
        return this.closestEnemyPosX;
    }

    public float getClosestEnemyPosY() {
        this.syncHudPositions();
        return this.closestEnemyPosY;
    }

    public float getClosestEnemyRelativePosX() {
        this.syncHudPositions();
        return this.closestEnemyRelativePosX;
    }

    public float getClosestEnemyRelativePosY() {
        this.syncHudPositions();
        return this.closestEnemyRelativePosY;
    }

    public void setClosestEnemyRelativePosition(float normalizedX, float normalizedY) {
        this.closestEnemyRelativePosX = normalizedX;
        this.closestEnemyRelativePosY = normalizedY;
        this.syncHudPositions();
    }

    public void setClosestEnemyAbsolutePosition(float absoluteX, float absoluteY) {
        this.setClosestEnemyAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public float getDragonPosX() {
        this.syncHudPositions();
        return this.dragonPosX;
    }

    public float getDragonPosY() {
        this.syncHudPositions();
        return this.dragonPosY;
    }

    public float getDragonRelativePosX() {
        this.syncHudPositions();
        return this.dragonRelativePosX;
    }

    public float getDragonRelativePosY() {
        this.syncHudPositions();
        return this.dragonRelativePosY;
    }

    public void setDragonRelativePosition(float normalizedX, float normalizedY) {
        this.dragonRelativePosX = normalizedX;
        this.dragonRelativePosY = normalizedY;
        this.syncHudPositions();
    }

    public void setDragonAbsolutePosition(float absoluteX, float absoluteY) {
        this.setDragonAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public float getMagicMilkPosX() {
        this.syncHudPositions();
        return this.magicMilkPosX;
    }

    public float getMagicMilkPosY() {
        this.syncHudPositions();
        return this.magicMilkPosY;
    }

    public float getMagicMilkRelativePosX() {
        this.syncHudPositions();
        return this.magicMilkRelativePosX;
    }

    public float getMagicMilkRelativePosY() {
        this.syncHudPositions();
        return this.magicMilkRelativePosY;
    }

    public void setMagicMilkRelativePosition(float normalizedX, float normalizedY) {
        this.magicMilkRelativePosX = normalizedX;
        this.magicMilkRelativePosY = normalizedY;
        this.syncHudPositions();
    }

    public void setMagicMilkAbsolutePosition(float absoluteX, float absoluteY) {
        this.setMagicMilkAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    private void syncHudPositions() {
        this.syncHudPositions(new Window(mc));
    }

    private void syncHudPositions(Window resolution) {
        int scaledWidth = Math.max(1, resolution.getWidth());
        int scaledHeight = Math.max(1, resolution.getHeight());
        float defaultClosestEnemyY = scaledHeight / 4.0F - mc.textRenderer.fontHeight / 2.0F;
        if (Float.isNaN(this.closestEnemyRelativePosX) || Float.isNaN(this.closestEnemyRelativePosY)) {
            if (Float.isNaN(this.closestEnemyPosX) || Float.isNaN(this.closestEnemyPosY)) {
                this.closestEnemyPosX = 0.0F;
                this.closestEnemyPosY = defaultClosestEnemyY;
            }

            this.closestEnemyRelativePosX = this.closestEnemyPosX / scaledWidth;
            this.closestEnemyRelativePosY = this.closestEnemyPosY / scaledHeight;
        }

        this.closestEnemyPosX = this.closestEnemyRelativePosX * scaledWidth;
        this.closestEnemyPosY = this.closestEnemyRelativePosY * scaledHeight;
        if (Float.isNaN(this.dragonRelativePosX) || Float.isNaN(this.dragonRelativePosY)) {
            if (Float.isNaN(this.dragonPosX) || Float.isNaN(this.dragonPosY)) {
                this.dragonPosX = this.closestEnemyPosX;
                this.dragonPosY = Math.max(5.0F, this.closestEnemyPosY - mc.textRenderer.fontHeight - 9.0F);
            }

            this.dragonRelativePosX = this.dragonPosX / scaledWidth;
            this.dragonRelativePosY = this.dragonPosY / scaledHeight;
        }

        this.dragonPosX = this.dragonRelativePosX * scaledWidth;
        this.dragonPosY = this.dragonRelativePosY * scaledHeight;
        if (Float.isNaN(this.magicMilkRelativePosX) || Float.isNaN(this.magicMilkRelativePosY)) {
            if (Float.isNaN(this.magicMilkPosX) || Float.isNaN(this.magicMilkPosY)) {
                this.magicMilkPosX = 0.0F;
                this.magicMilkPosY = defaultClosestEnemyY + mc.textRenderer.fontHeight + 9.0F;
            }

            this.magicMilkRelativePosX = this.magicMilkPosX / scaledWidth;
            this.magicMilkRelativePosY = this.magicMilkPosY / scaledHeight;
        }

        this.magicMilkPosX = this.magicMilkRelativePosX * scaledWidth;
        this.magicMilkPosY = this.magicMilkRelativePosY * scaledHeight;
    }

    private void setClosestEnemyAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.closestEnemyPosX = absoluteX;
        this.closestEnemyPosY = absoluteY;
        this.closestEnemyRelativePosX = absoluteX / Math.max(1, resolution.getWidth());
        this.closestEnemyRelativePosY = absoluteY / Math.max(1, resolution.getHeight());
    }

    private void setDragonAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.dragonPosX = absoluteX;
        this.dragonPosY = absoluteY;
        this.dragonRelativePosX = absoluteX / Math.max(1, resolution.getWidth());
        this.dragonRelativePosY = absoluteY / Math.max(1, resolution.getHeight());
    }

    private void setMagicMilkAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.magicMilkPosX = absoluteX;
        this.magicMilkPosY = absoluteY;
        this.magicMilkRelativePosX = absoluteX / Math.max(1, resolution.getWidth());
        this.magicMilkRelativePosY = absoluteY / Math.max(1, resolution.getHeight());
    }

    private void resetHudPositions(Window resolution) {
        float closestEnemyY = resolution.getHeight() / 4.0F - mc.textRenderer.fontHeight / 2.0F;
        this.setClosestEnemyAbsolutePosition(0.0F, closestEnemyY, resolution);
        this.setDragonAbsolutePosition(
                0.0F, Math.max(5.0F, closestEnemyY - mc.textRenderer.fontHeight - 9.0F), resolution);
        this.setMagicMilkAbsolutePosition(0.0F, closestEnemyY + mc.textRenderer.fontHeight + 9.0F, resolution);
    }

    private void ping() {
        if (this.shouldPing.isToggled()) {
            mc.player.playSound("note.pling", 1.0F, 1.0F);
        }
    }

    private BedWars.HudBoxBounds drawEnemyHudBox(List<BedWars.EnemyHudEntry> entries, float x, float textY) {
        if (entries.isEmpty()) {
            return null;
        }

        float gap = mc.textRenderer.getWidth(' ') * 2.0F;
        float maxNameWidth = 0.0F;
        float maxDistanceWidth = 0.0F;
        boolean hasItem = false;

        for (BedWars.EnemyHudEntry entry : entries) {
            maxNameWidth = Math.max(maxNameWidth, mc.textRenderer.getWidth(entry.name));
            maxDistanceWidth = Math.max(maxDistanceWidth, mc.textRenderer.getWidth(entry.distance));
            hasItem |= entry.heldItem != null;
        }

        float width = maxNameWidth + gap + maxDistanceWidth + (hasItem ? gap + 10.0F : 0.0F);
        this.closestEnemyLayout.update(width, maxNameWidth, maxDistanceWidth);
        float animatedWidth = this.closestEnemyLayout.width;
        float nameWidth = this.closestEnemyLayout.nameWidth;
        float distanceWidth = this.closestEnemyLayout.distanceWidth;
        Window resolution = new Window(mc);
        float left = x - 5.0F;
        float right = x + animatedWidth + 4.0F;
        float top = textY - 5.0F;
        float bottom = textY + (entries.size() - 1) * 12.0F + mc.textRenderer.fontHeight + 3.5F;
        if (left <= 0.0F) {
            left = -3.0F;
        }

        if (right > resolution.getWidth()) {
            right = resolution.getWidth() + 3.0F;
        }

        right -= 0.5F;
        GL11.glPushAttrib(549121);

        try {
            this.enableClosestEnemyAlphaBlending();
            RenderUtils.drawRoundedRectangle(left, top, right, bottom, 8.0F, 2097152000);
            this.scissorEnemyHud(x, top, x + nameWidth + 1.0F, bottom, resolution);

            for (int i = 0; i < entries.size(); i++) {
                mc.textRenderer.draw(entries.get(i).name, x, textY + i * 12.0F, -1, true);
            }

            this.scissorEnemyHud(left, top, right, bottom, resolution);
            float distanceX = x + nameWidth + gap;

            for (int i = 0; i < entries.size(); i++) {
                BedWars.EnemyHudEntry entry = entries.get(i);
                mc.textRenderer.draw(
                        entry.distance,
                        distanceX + distanceWidth - mc.textRenderer.getWidth(entry.distance),
                        textY + i * 12.0F,
                        -1,
                        true);
            }

            if (hasItem) {
                float itemX = (distanceX + distanceWidth + gap) * 1.6F;
                GlStateManager.pushMatrix();

                try {
                    GlStateManager.scalef(0.625F, 0.625F, 0.625F);

                    for (int i = 0; i < entries.size(); i++) {
                        BedWars.EnemyHudEntry entry = entries.get(i);
                        if (entry.heldItem != null) {
                            this.renderHeldItem(entry.heldItem, itemX, (textY + i * 12.0F - 0.5F) * 1.6F);
                        }
                    }
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.disableBlend();
        }

        return new BedWars.HudBoxBounds(left, top, right, bottom);
    }

    private void scissorEnemyHud(float left, float top, float right, float bottom, Window resolution) {
        int scale = resolution.getScale();
        int x1 = Math.max(0, (int) Math.floor(left * scale));
        int x2 = Math.min(mc.width, (int) Math.ceil(right * scale));
        int y1 = Math.max(0, (int) Math.floor(mc.height - bottom * scale));
        int y2 = Math.min(mc.height, (int) Math.ceil(mc.height - top * scale));
        GL11.glEnable(3089);
        GL11.glScissor(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    private void enableClosestEnemyAlphaBlending() {
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }

    private static final class DragonConeVisual {
        private Vec3d position;
        private long lastRenderNanos;

        private DragonConeVisual() {}

        private Vec3d update(DragonDivePrediction.Result prediction) {
            if (prediction.impact == null) {
                this.position = null;
                this.lastRenderNanos = 0L;
                return null;
            }

            long now = System.nanoTime();
            Vec3d target = prediction.impact;
            if (this.position != null && !(this.position.squaredDistanceTo(target) > 16.0)) {
                double elapsed = Math.min(0.1, Math.max(0.0, (now - this.lastRenderNanos) / 1.0E9));
                double progress = 1.0 - Math.exp(-elapsed / 0.045);
                this.position = new Vec3d(
                        this.position.x + (target.x - this.position.x) * progress,
                        this.position.y + (target.y - this.position.y) * progress,
                        this.position.z + (target.z - this.position.z) * progress);
            } else {
                this.position = target;
            }

            this.lastRenderNanos = now;
            Box surface = prediction.impactSurface;
            double x = Math.max(surface.minX, Math.min(surface.maxX, this.position.x));
            double y = Math.max(surface.minY, Math.min(surface.maxY, this.position.y));
            double z = Math.max(surface.minZ, Math.min(surface.maxZ, this.position.z));
            switch (prediction.impactFace) {
                case WEST:
                    x = surface.minX - 0.04;
                    break;
                case EAST:
                    x = surface.maxX + 0.04;
                    break;
                case DOWN:
                    y = surface.minY - 0.04;
                    break;
                case UP:
                    y = surface.maxY + 0.04;
                    break;
                case NORTH:
                    z = surface.minZ - 0.04;
                    break;
                case SOUTH:
                    z = surface.maxZ + 0.04;
            }

            this.position = new Vec3d(x, y, z);
            return this.position;
        }
    }

    private class EditPositionsScreen extends Screen {
        private static final int DRAG_NONE = 0;
        private static final int DRAG_CLOSEST_ENEMY = 1;
        private static final int DRAG_MAGIC_MILK = 2;
        private static final int DRAG_DRAGON = 3;
        private static final long ARROW_HOLD_DELAY_MS = 300L;
        private static final long ARROW_INITIAL_REPEAT_MS = 120L;
        private static final long ARROW_MIN_REPEAT_MS = 40L;
        private static final double ARROW_ACCELERATION_HALF_LIFE_MS = 1500.0;
        private ButtonWidget resetPositions;
        private BedWars.HudBoxBounds closestEnemyBounds;
        private BedWars.HudBoxBounds magicMilkBounds;
        private BedWars.HudBoxBounds dragonBounds;
        private int dragging = 0;
        private float closestEnemyX;
        private float closestEnemyY;
        private float magicMilkX;
        private float magicMilkY;
        private float dragonX;
        private float dragonY;
        private float dragStartX;
        private float dragStartY;
        private int lastMouseX;
        private int lastMouseY;
        private int heldArrowBox = 0;
        private int heldArrowKey;
        private long arrowHoldStartedAt;
        private long lastArrowNudgeAt;

        private EditPositionsScreen() {}

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetPositions =
                            new ButtonWidget(1, this.width - 95, this.height - 25, 90, 20, "Reset positions"));
            BedWars.this.syncHudPositions(new Window(this.minecraft));
            this.syncEditorPositions();
        }

        public void render(int mouseX, int mouseY, float partialTicks) {
            Window resolution = new Window(this.minecraft);
            if (this.dragging == 0) {
                BedWars.this.syncHudPositions(resolution);
                this.syncEditorPositions();
            }

            this.updateHeldArrowMovement();
            fill(0, 0, this.width, this.height, -1308622848);
            BedWars.this.setClosestEnemyAbsolutePosition(this.closestEnemyX, this.closestEnemyY, resolution);
            BedWars.this.setMagicMilkAbsolutePosition(this.magicMilkX, this.magicMilkY, resolution);
            BedWars.this.setDragonAbsolutePosition(this.dragonX, this.dragonY, resolution);
            List<BedWars.EnemyHudEntry> previewEntries = new ArrayList<>();
            int previewPlayers = Math.max(1, (int) BedWars.this.closestEnemy.getInput());

            for (int i = 0; i < previewPlayers; i++) {
                String name = this.minecraft.player.getDisplayName().getString();
                String distance = BedWars.this.formatDistance(16 + i * 3);
                previewEntries.add(
                        new BedWars.EnemyHudEntry(name, distance, this.minecraft.player.getDisplayItemInHand()));
            }

            this.closestEnemyBounds =
                    BedWars.this.drawEnemyHudBox(previewEntries, this.closestEnemyX, this.closestEnemyY);
            this.magicMilkBounds = BedWars.this.drawHudBox("§7Magic Milk: §a30.0s", this.magicMilkX, this.magicMilkY);
            this.dragonBounds = BedWars.this.drawDragonHudBox(50.0, this.dragonX, this.dragonY);
            String message = "Drag a box to reposition it.";
            int messageX = resolution.getWidth() / 2 - this.textRenderer.getWidth(message) / 2;
            int messageY = resolution.getHeight() / 2 - 10;
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.1F);
            BedWars.this.enableClosestEnemyAlphaBlending();

            try {
                RenderUtils.drawColoredString(message, '-', messageX, messageY, 2L, 0L, true, this.textRenderer);
            } finally {
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.disableBlend();
            }

            ClientPlatform.handleInput(this);
            super.render(mouseX, mouseY, partialTicks);
        }

        protected void keyPressed(char typedChar, int keyCode) {
            if (keyCode != 203 && keyCode != 205 && keyCode != 200 && keyCode != 208) {
                super.keyPressed(typedChar, keyCode);
            } else {
                if (!Keyboard.isRepeatEvent() && this.dragging == 0) {
                    int mouseX = Mouse.getX() * this.width / this.minecraft.width;
                    int mouseY = this.height - Mouse.getY() * this.height / this.minecraft.height - 1;
                    int hoveredBox = this.hoveredBox(mouseX, mouseY);
                    this.clearHeldArrowMovement();
                    if (hoveredBox != 0) {
                        this.heldArrowBox = hoveredBox;
                        this.heldArrowKey = keyCode;
                        this.arrowHoldStartedAt = this.lastArrowNudgeAt = System.currentTimeMillis();
                        this.moveBoxWithArrow(hoveredBox, keyCode);
                    }
                }
            }
        }

        private int hoveredBox(int mouseX, int mouseY) {
            if (this.closestEnemyBounds != null && this.closestEnemyBounds.contains(mouseX, mouseY)) {
                return 1;
            } else if (this.magicMilkBounds != null && this.magicMilkBounds.contains(mouseX, mouseY)) {
                return 2;
            } else {
                return this.dragonBounds != null && this.dragonBounds.contains(mouseX, mouseY) ? 3 : 0;
            }
        }

        private void updateHeldArrowMovement() {
            if (this.heldArrowBox != 0) {
                if (this.dragging == 0 && Keyboard.isKeyDown(this.heldArrowKey)) {
                    long now = System.currentTimeMillis();
                    long acceleratingDuration = now - this.arrowHoldStartedAt - 300L;
                    if (acceleratingDuration >= 0L) {
                        long repeatDelay =
                                Math.max(40L, Math.round(120.0 * Math.pow(0.5, acceleratingDuration / 1500.0)));
                        if (now - this.lastArrowNudgeAt >= repeatDelay) {
                            this.moveBoxWithArrow(this.heldArrowBox, this.heldArrowKey);
                            this.lastArrowNudgeAt = now;
                        }
                    }
                } else {
                    this.clearHeldArrowMovement();
                }
            }
        }

        private void moveBoxWithArrow(int box, int keyCode) {
            int offsetX = keyCode == 203 ? -1 : (keyCode == 205 ? 1 : 0);
            int offsetY = keyCode == 200 ? -1 : (keyCode == 208 ? 1 : 0);
            Window resolution = new Window(this.minecraft);
            if (box == 1) {
                this.closestEnemyX += offsetX;
                this.closestEnemyY += offsetY;
                BedWars.this.setClosestEnemyAbsolutePosition(this.closestEnemyX, this.closestEnemyY, resolution);
            } else if (box == 2) {
                this.magicMilkX += offsetX;
                this.magicMilkY += offsetY;
                BedWars.this.setMagicMilkAbsolutePosition(this.magicMilkX, this.magicMilkY, resolution);
            } else if (box == 3) {
                this.dragonX += offsetX;
                this.dragonY += offsetY;
                BedWars.this.setDragonAbsolutePosition(this.dragonX, this.dragonY, resolution);
            }
        }

        private void clearHeldArrowMovement() {
            this.heldArrowBox = 0;
            this.heldArrowKey = 0;
            this.arrowHoldStartedAt = 0L;
            this.lastArrowNudgeAt = 0L;
        }

        protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
            if (button == 0) {
                if (this.dragging == 1) {
                    this.closestEnemyX = this.dragStartX + mouseX - this.lastMouseX;
                    this.closestEnemyY = this.dragStartY + mouseY - this.lastMouseY;
                } else if (this.dragging == 2) {
                    this.magicMilkX = this.dragStartX + mouseX - this.lastMouseX;
                    this.magicMilkY = this.dragStartY + mouseY - this.lastMouseY;
                } else if (this.dragging == 3) {
                    this.dragonX = this.dragStartX + mouseX - this.lastMouseX;
                    this.dragonY = this.dragStartY + mouseY - this.lastMouseY;
                } else if (this.closestEnemyBounds != null && this.closestEnemyBounds.contains(mouseX, mouseY)) {
                    this.beginDragging(1, this.closestEnemyX, this.closestEnemyY, mouseX, mouseY);
                } else if (this.magicMilkBounds != null && this.magicMilkBounds.contains(mouseX, mouseY)) {
                    this.beginDragging(2, this.magicMilkX, this.magicMilkY, mouseX, mouseY);
                } else if (this.dragonBounds != null && this.dragonBounds.contains(mouseX, mouseY)) {
                    this.beginDragging(3, this.dragonX, this.dragonY, mouseX, mouseY);
                }
            }
        }

        private void beginDragging(int element, float elementX, float elementY, int mouseX, int mouseY) {
            this.clearHeldArrowMovement();
            this.dragging = element;
            this.dragStartX = elementX;
            this.dragStartY = elementY;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
        }

        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = 0;
            }
        }

        public void buttonClicked(ButtonWidget button) {
            if (button == this.resetPositions) {
                this.clearHeldArrowMovement();
                this.dragging = 0;
                BedWars.this.resetHudPositions(new Window(this.minecraft));
                this.syncEditorPositions();
            }
        }

        private void syncEditorPositions() {
            this.closestEnemyX = BedWars.this.closestEnemyPosX;
            this.closestEnemyY = BedWars.this.closestEnemyPosY;
            this.magicMilkX = BedWars.this.magicMilkPosX;
            this.magicMilkY = BedWars.this.magicMilkPosY;
            this.dragonX = BedWars.this.dragonPosX;
            this.dragonY = BedWars.this.dragonPosY;
        }

        public void removed() {
            this.clearHeldArrowMovement();
            super.removed();
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }

    private static final class EnemyHudEntry {
        private final String name;
        private final String distance;
        private final ItemStack heldItem;

        private EnemyHudEntry(String name, String distance, ItemStack heldItem) {
            this.name = name;
            this.distance = distance;
            this.heldItem = heldItem;
        }
    }

    private static final class EnemyHudLayout {
        private boolean initialized;
        private float width;
        private float nameWidth;
        private float distanceWidth;
        private float fromWidth;
        private float fromNameWidth;
        private float fromDistanceWidth;
        private float targetWidth;
        private float targetNameWidth;
        private float targetDistanceWidth;
        private long startedAt;

        private EnemyHudLayout() {}

        private void update(float newWidth, float newNameWidth, float newDistanceWidth) {
            long now = System.currentTimeMillis();
            if (!this.initialized) {
                this.width = this.fromWidth = this.targetWidth = newWidth;
                this.nameWidth = this.fromNameWidth = this.targetNameWidth = newNameWidth;
                this.distanceWidth = this.fromDistanceWidth = this.targetDistanceWidth = newDistanceWidth;
                this.startedAt = now;
                this.initialized = true;
            } else {
                float progress = Math.min(1.0F, (float) (now - this.startedAt) / 100.0F);
                float eased = progress * progress * (3.0F - 2.0F * progress);
                this.width = this.fromWidth + (this.targetWidth - this.fromWidth) * eased;
                this.nameWidth = this.fromNameWidth + (this.targetNameWidth - this.fromNameWidth) * eased;
                this.distanceWidth =
                        this.fromDistanceWidth + (this.targetDistanceWidth - this.fromDistanceWidth) * eased;
                if (Float.compare(this.targetWidth, newWidth) != 0
                        || Float.compare(this.targetNameWidth, newNameWidth) != 0
                        || Float.compare(this.targetDistanceWidth, newDistanceWidth) != 0) {
                    this.fromWidth = this.width;
                    this.fromNameWidth = this.nameWidth;
                    this.fromDistanceWidth = this.distanceWidth;
                    this.targetWidth = newWidth;
                    this.targetNameWidth = newNameWidth;
                    this.targetDistanceWidth = newDistanceWidth;
                    this.startedAt = now;
                }
            }
        }
    }

    private static final class HudBoxBounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private HudBoxBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.left && mouseX <= this.right && mouseY >= this.top && mouseY <= this.bottom;
        }
    }
}
