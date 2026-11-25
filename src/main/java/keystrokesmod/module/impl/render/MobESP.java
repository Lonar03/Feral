package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.GlowShader;
import keystrokesmod.utility.shader.OutlineShader;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class MobESP extends Module {
    public static boolean renderingOutlinePass = false;
    private static final ThreadLocal<Boolean> MOB_CHAMS_ACTIVE = ThreadLocal.withInitial(() -> false);
    public GroupSetting espTypes;
    public ButtonSetting twoD;
    public ButtonSetting box;
    public ButtonSetting outline;
    public ButtonSetting chams;
    public ButtonSetting ring;
    public ButtonSetting shaded;
    public ButtonSetting healthBar;
    public ButtonSetting redOnDamage;
    public ButtonSetting showInvis;
    private final SliderSetting maxDistance;
    /** Colors Hypixel Zombies bosses, powerup mobs and bad-headshot mobs (from ZombieCat). */
    public ButtonSetting zombiesColors;
    /** With Zombies colors: plain mobs are white. Off: plain mobs keep their configured color. */
    public ButtonSetting whiteNormalMobs;

    private SliderSetting boxThickness;
    private SliderSetting outlineThickness;

    private final List<MobESP.MobEntry> mobEntries = new ArrayList<>();
    private final Map<LivingEntity, Integer> renderAsTwoD = new HashMap<>();
    private RenderTarget outlineFramebuffer;
    private final OutlineShader outlineShader = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();

    public MobESP() {
        super("MobESP", Module.category.render);
        this.registerSetting(this.espTypes = new GroupSetting("Types"));
        this.registerSetting(this.twoD = new ButtonSetting(this.espTypes, "2D", false));
        this.registerSetting(this.box = new ButtonSetting(this.espTypes, "Box", false));
        this.registerSetting(this.outline = new ButtonSetting(this.espTypes, "Outline", false));
        this.registerSetting(this.chams = new ButtonSetting(this.espTypes, "Chams", false));
        this.registerSetting(this.ring = new ButtonSetting(this.espTypes, "Ring", false));
        this.registerSetting(this.shaded = new ButtonSetting(this.espTypes, "Shaded", false));
        this.registerSetting(this.healthBar = new ButtonSetting(this.espTypes, "Health bar", false));
        this.registerSetting(this.redOnDamage = new ButtonSetting("Red on damage", true));
        this.registerSetting(this.showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(this.zombiesColors = new ButtonSetting("Zombies colors", false));
        this.registerSetting(this.whiteNormalMobs = new ButtonSetting("White for normal mobs", true));
        this.registerSetting(this.boxThickness = new SliderSetting("Box thickness", 2.0, 0.5, 6.0, 0.5));
        this.registerSetting(this.outlineThickness = new SliderSetting("Outline thickness", 2.0, 1.0, 5.0, 1.0));
        this.registerSetting(this.maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));
        this.registerMobGroups();
    }

    @Override
    public void guiUpdate() {
        this.whiteNormalMobs.setVisible(this.zombiesColors.isToggled(), this);
        this.boxThickness.setVisible(this.box.isToggled(), this);
        this.outlineThickness.setVisible(this.outline.isToggled(), this);
    }

    private void registerMobGroups() {
        this.registerMob("Armor Stand", ArmorStandEntity.class, false, 200, 200, 200);
        this.registerMob(
                "Baby Zombie",
                ZombieEntity.class,
                e -> !((ZombieEntity) e).isVillager() && ((ZombieEntity) e).isBaby(),
                true,
                0,
                120,
                255);
        this.registerMob("Bat", BatEntity.class, false, 80, 80, 80);
        this.registerMob("Blaze", BlazeEntity.class, true, 255, 165, 0);
        this.registerMob("Cave Spider", CaveSpiderEntity.class, true, 64, 64, 64);
        this.registerMob("Chicken", ChickenEntity.class, false, 255, 255, 255);
        this.registerMob("Cow", CowEntity.class, false, 120, 80, 60);
        this.registerMob("Creeper", CreeperEntity.class, true, 0, 255, 0);
        this.registerMob(
                "Donkey", HorseBaseEntity.class, e -> ((HorseBaseEntity) e).getType() == 1, false, 120, 120, 120);
        this.registerMob(
                "Elder Guardian", GuardianEntity.class, e -> ((GuardianEntity) e).isElder(), false, 0, 90, 140);
        this.registerMob("Ender Dragon", EnderDragonEntity.class, false, 200, 0, 200);
        this.registerMob("Enderman", EndermanEntity.class, true, 0, 0, 0);
        this.registerMob("Endermite", EndermiteEntity.class, true, 80, 0, 120);
        this.registerMob("Ghast", GhastEntity.class, true, 255, 255, 255);
        this.registerMob("Giant", GiantEntity.class, true, 0, 0, 139);
        this.registerMob("Guardian", GuardianEntity.class, g -> !((GuardianEntity) g).isElder(), false, 0, 150, 180);
        this.registerMob(
                "Horse", HorseBaseEntity.class, e -> ((HorseBaseEntity) e).getType() == 0, false, 150, 100, 60);
        this.registerMob("Iron Golem", IronGolemEntity.class, true, 200, 200, 200);
        this.registerMob("Magma Cube", MagmaCubeEntity.class, true, 200, 60, 0);
        this.registerMob("Mooshroom", MooshroomEntity.class, false, 200, 0, 0);
        this.registerMob("Mule", HorseBaseEntity.class, e -> ((HorseBaseEntity) e).getType() == 2, false, 130, 110, 80);
        this.registerMob("Ocelot", OcelotEntity.class, false, 200, 150, 100);
        this.registerMob("Pig", PigEntity.class, false, 255, 150, 200);
        this.registerMob("Rabbit", RabbitEntity.class, false, 180, 140, 100);
        this.registerMob("Sheep", SheepEntity.class, false, 255, 255, 255);
        this.registerMob("Silverfish", SilverfishEntity.class, true, 128, 128, 128);
        this.registerMob(
                "Skeleton", SkeletonEntity.class, e -> ((SkeletonEntity) e).getType() == 0, true, 255, 255, 255);
        this.registerMob(
                "Skeleton Horse",
                HorseBaseEntity.class,
                e -> ((HorseBaseEntity) e).getType() == 4,
                false,
                220,
                220,
                220);
        this.registerMob("Slime", SlimeEntity.class, true, 0, 255, 0);
        this.registerMob("Snow Golem", SnowGolemEntity.class, false, 255, 255, 255);
        this.registerMob("Spider", SpiderEntity.class, true, 0, 0, 0);
        this.registerMob("Squid", SquidEntity.class, true, 30, 60, 180);
        this.registerMob("Villager", VillagerEntity.class, false, 180, 150, 120);
        this.registerMob("Witch", WitchEntity.class, true, 90, 50, 120);
        // Invisible withers are the fake entity Hypixel draws boss bars with, not a mob (MeowerClient).
        this.registerMob("Wither", WitherEntity.class, e -> !e.isInvisible(), true, 64, 64, 64);
        this.registerMob(
                "Wither Skeleton", SkeletonEntity.class, e -> ((SkeletonEntity) e).getType() == 1, true, 55, 55, 55);
        this.registerMob("Wolf", WolfEntity.class, true, 200, 200, 200);
        this.registerMob(
                "Zombie",
                ZombieEntity.class,
                e -> !((ZombieEntity) e).isVillager() && !((ZombieEntity) e).isBaby(),
                true,
                0,
                0,
                255);
        this.registerMob(
                "Zombie Horse", HorseBaseEntity.class, e -> ((HorseBaseEntity) e).getType() == 3, false, 80, 100, 70);
        this.registerMob("Zombie Pigman", ZombiePigmanEntity.class, true, 255, 192, 203);
        this.registerMob(
                "Zombie Villager", ZombieEntity.class, e -> ((ZombieEntity) e).isVillager(), true, 100, 140, 90);
    }

    private void registerMob(String name, Class<? extends LivingEntity> clazz, boolean defaultOn, int r, int g, int b) {
        this.registerMob(name, clazz, null, defaultOn, r, g, b);
    }

    private void registerMob(
            String name,
            Class<? extends LivingEntity> clazz,
            Predicate<LivingEntity> refine,
            boolean defaultOn,
            int r,
            int g,
            int b) {
        GroupSetting group = new GroupSetting(name);
        this.registerSetting(group);
        ButtonSetting enable = new ButtonSetting(group, "Enable", defaultOn);
        this.registerSetting(enable);
        ColorSetting color = new ColorSetting(group, "Color", r, g, b);
        this.registerSetting(color);
        this.mobEntries.add(new MobESP.MobEntry(clazz, refine, enable, color));
    }

    private MobESP.MobEntry resolveEntry(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return null;
        }

        MobESP.MobEntry best = null;

        for (MobESP.MobEntry entry : this.mobEntries) {
            if (entry.enable.isToggled()
                    && entry.matches(entity)
                    && (best == null || best.type.isAssignableFrom(entry.type))) {
                best = entry;
            }
        }

        return best;
    }

    private int rgbFor(LivingEntity ent, MobESP.MobEntry entry) {
        int rgb = Utils.mergeAlpha(entry.color.getRGB(), 255);
        if (this.zombiesColors.isToggled()) {
            java.awt.Color zombiesColor =
                    keystrokesmod.utility.zombies.ZombiesMobs.colorFor(ent, this.whiteNormalMobs.isToggled());
            if (zombiesColor != null) {
                rgb = zombiesColor.getRGB();
            }
        }
        if (this.redOnDamage.isToggled() && ent.damagedTimer != 0) {
            rgb = -65536;
        }

        return rgb;
    }

    public static void onRenderMobPre(LivingEntity entity) {
        MobESP mod = getMobEspModule();
        if (mod != null && mod.shouldApplyChamsTo(entity)) {
            GL11.glEnable(32823);
            GL11.glPolygonOffset(1.0F, -1100000.0F);
            MOB_CHAMS_ACTIVE.set(true);
        }
    }

    public static void onRenderMobPost() {
        if (Boolean.TRUE.equals(MOB_CHAMS_ACTIVE.get())) {
            MOB_CHAMS_ACTIVE.set(false);
            GL11.glPolygonOffset(1.0F, 1100000.0F);
            GL11.glDisable(32823);
        }
    }

    private static MobESP getMobEspModule() {
        Module module = ModuleManager.getModule(MobESP.class);
        return module instanceof MobESP && module.isEnabled() ? (MobESP) module : null;
    }

    private boolean shouldApplyChamsTo(LivingEntity entity) {
        if (!this.chams.isToggled() || !Utils.nullCheck() || entity == null || entity == mc.player) {
            return false;
        }

        if (entity.deathTicks != 0) {
            return false;
        }

        if (!this.showInvis.isToggled() && entity.isInvisible()) {
            return false;
        }

        double maxDistSq = this.maxDistance.getInput() * this.maxDistance.getInput();
        return !RenderUtils.isWithinDistanceSqToRenderView(entity, maxDistSq)
                ? false
                : this.resolveEntry(entity) != null;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        this.renderAsTwoD.clear();
        if (Utils.nullCheck() && this.isEnabled()) {
            double maxDistSq = this.maxDistance.getInput() * this.maxDistance.getInput();

            for (Entity entity : mc.world.entities) {
                if (entity instanceof LivingEntity && entity != mc.player) {
                    LivingEntity living = (LivingEntity) entity;
                    if (living.deathTicks == 0
                            && (this.showInvis.isToggled() || !living.isInvisible())
                            && RenderUtils.isWithinDistanceSqToRenderView(living, maxDistSq)) {
                        MobESP.MobEntry entry = this.resolveEntry(living);
                        if (entry != null) {
                            int rgb = this.rgbFor(living, entry);
                            this.renderAsTwoD.put(living, rgb);
                            this.render(living, rgb);
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderWorldLast2D(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.isEnabled()) {
            if (this.outline.isToggled()) {
                this.runOutlinePass(e.partialTicks);
            }

            if (this.twoD.isToggled()) {
                for (Entry<LivingEntity, Integer> entry : this.renderAsTwoD.entrySet()) {
                    int col = this.redOnDamage.isToggled() && entry.getKey().damagedTimer != 0
                            ? -65536
                            : entry.getValue();
                    this.renderTwoD(entry.getKey(), col, 0.0, e.partialTicks);
                }
            }
        }
    }

    private void runOutlinePass(float partialTicks) {
        if (this.outlineShader.isValid() && this.glowShader.isValid() && !this.renderAsTwoD.isEmpty()) {
            boolean anyVisible = false;

            for (LivingEntity ent : this.renderAsTwoD.keySet()) {
                if (RenderUtils.isInViewFrustum(ent)) {
                    anyVisible = true;
                    break;
                }
            }

            if (anyVisible) {
                this.outlineFramebuffer = RenderUtils.createFrameBuffer(this.outlineFramebuffer, false);
                if (this.outlineFramebuffer != null) {
                    GlStateManager.pushMatrix();
                    GlStateManager.pushLightingAttributes();
                    this.outlineFramebuffer.bindWrite(false);
                    ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
                    boolean shadows = mc.options.entityShadows;
                    mc.options.entityShadows = false;
                    renderingOutlinePass = true;
                    this.glowShader.use();

                    for (Entry<LivingEntity, Integer> e : this.renderAsTwoD.entrySet()) {
                        LivingEntity ent = e.getKey();
                        if (RenderUtils.isInViewFrustum(ent)) {
                            int col = this.redOnDamage.isToggled() && ent.damagedTimer != 0 ? -65536 : e.getValue();
                            this.glowShader.setColor(col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, col >> 24 & 0xFF);
                            boolean invis = ent.isInvisible();
                            if (this.showInvis.isToggled()) {
                                ent.setInvisible(false);
                            }

                            mc.getEntityRenderDispatcher().render(ent, partialTicks, true);
                            ent.setInvisible(invis);
                        }
                    }

                    this.glowShader.stop();
                    renderingOutlinePass = false;
                    mc.options.entityShadows = shadows;
                    mc.gameRenderer.disableLightMap();
                    mc.gameRenderer.setupGuiState();
                    mc.getRenderTarget().bindWrite(false);
                    this.outlineShader.setKernel((float) this.outlineThickness.getInput());
                    this.outlineShader.use();
                    RenderUtils.drawFramebufferFullscreen(this.outlineFramebuffer);
                    this.outlineShader.stop();
                    this.outlineFramebuffer.clear();
                    mc.getRenderTarget().bindWrite(false);
                    GlStateManager.popAttributes();
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private void render(Entity en, int rgb) {
        if (this.box.isToggled() || this.shaded.isToggled() || this.healthBar.isToggled() || this.ring.isToggled()) {
            if (RenderUtils.isInViewFrustum(en)) {
                if (this.box.isToggled()) {
                    RenderUtils.renderEntity(en, 1, 0.0, 0.0, rgb, this.redOnDamage.isToggled(), 0.25F, (float)
                            this.boxThickness.getInput());
                }

                if (this.shaded.isToggled()
                        && (ModuleManager.murderMystery == null
                                || !ModuleManager.murderMystery.isEnabled()
                                || ModuleManager.murderMystery.isEmpty())) {
                    RenderUtils.renderEntity(en, 2, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
                }

                if (this.healthBar.isToggled()) {
                    RenderUtils.renderEntity(en, 4, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
                }

                if (this.ring.isToggled()) {
                    RenderUtils.renderEntity(en, 6, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
                }

                // The box colour stayed set afterwards and tinted the hand (visible with Zombies colors' green)
                // RenderUtils sets it with raw GL11 calls, so GlStateManager.color4f would be skipped by its cache
                org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private void renderTwoD(LivingEntity en, int rgb, double expand, float partialTicks) {
        if (RenderUtils.isInViewFrustum(en)) {
            ((IAccessorEntityRenderer) mc.gameRenderer)
                    .callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().partialTick, 0);
            double playerX = en.prevX + (en.x - en.prevX) * partialTicks - mc.getEntityRenderDispatcher().cameraX;
            double playerY = en.prevY + (en.y - en.prevY) * partialTicks - mc.getEntityRenderDispatcher().cameraY;
            double playerZ = en.prevZ + (en.z - en.prevZ) * partialTicks - mc.getEntityRenderDispatcher().cameraZ;
            Box bbox = en.getShape().grown(0.1 + expand, 0.1 + expand, 0.1 + expand);
            Box axis = new Box(
                    bbox.minX - en.x + playerX,
                    bbox.minY - en.y + playerY,
                    bbox.minZ - en.z + playerZ,
                    bbox.maxX - en.x + playerX,
                    bbox.maxY - en.y + playerY,
                    bbox.maxZ - en.z + playerZ);
            Vec3d[] corners = new Vec3d[] {
                new Vec3d(axis.minX, axis.minY, axis.minZ),
                new Vec3d(axis.minX, axis.minY, axis.maxZ),
                new Vec3d(axis.minX, axis.maxY, axis.minZ),
                new Vec3d(axis.minX, axis.maxY, axis.maxZ),
                new Vec3d(axis.maxX, axis.minY, axis.minZ),
                new Vec3d(axis.maxX, axis.minY, axis.maxZ),
                new Vec3d(axis.maxX, axis.maxY, axis.minZ),
                new Vec3d(axis.maxX, axis.maxY, axis.maxZ)
            };
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double maxY = Double.MIN_VALUE;
            boolean isInView = false;
            Window scaledResolution = new Window(mc);

            for (Vec3d corner : corners) {
                Vec3d screenVec = RenderUtils.convertTo2D(scaledResolution.getScale(), corner.x, corner.y, corner.z);
                if (screenVec != null && !(screenVec.z >= 1.0003684) && !(screenVec.z <= 0.0)) {
                    isInView = true;
                    double screenX = screenVec.x;
                    double screenY = screenVec.y;
                    if (screenX < minX) {
                        minX = screenX;
                    }

                    if (screenY < minY) {
                        minY = screenY;
                    }

                    if (screenX > maxX) {
                        maxX = screenX;
                    }

                    if (screenY > maxY) {
                        maxY = screenY;
                    }
                }
            }

            if (isInView) {
                mc.gameRenderer.setupGuiState();
                Window res = new Window(mc);
                int screenWidth = res.getWidth();
                int screenHeight = res.getHeight();
                minX = Math.max(0.0, minX);
                minY = Math.max(0.0, minY);
                maxX = Math.min(screenWidth, maxX);
                maxY = Math.min(screenHeight, maxY);
                float red = (rgb >> 16 & 0xFF) / 255.0F;
                float green = (rgb >> 8 & 0xFF) / 255.0F;
                float blue = (rgb & 0xFF) / 255.0F;
                GL11.glPushMatrix();
                GL11.glDisable(3553);
                GL11.glDisable(2929);
                GL11.glEnable(2848);
                GL11.glLineWidth(1.0F);
                GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX, minY);
                GL11.glVertex2d(maxX, minY);
                GL11.glVertex2d(maxX, maxY);
                GL11.glVertex2d(minX, maxY);
                GL11.glEnd();
                GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX + 1.0, minY + 1.0);
                GL11.glVertex2d(maxX - 1.0, minY + 1.0);
                GL11.glVertex2d(maxX - 1.0, maxY - 1.0);
                GL11.glVertex2d(minX + 1.0, maxY - 1.0);
                GL11.glEnd();
                GL11.glColor4f(red, green, blue, 1.0F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX + 0.5, minY + 0.5);
                GL11.glVertex2d(maxX - 0.5, minY + 0.5);
                GL11.glVertex2d(maxX - 0.5, maxY - 0.5);
                GL11.glVertex2d(minX + 0.5, maxY - 0.5);
                GL11.glEnd();
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glEnable(3553);
                GL11.glEnable(2929);
                GL11.glDisable(2848);
                GL11.glPopMatrix();
            }
        }
    }

    private static final class MobEntry {
        final Class<? extends LivingEntity> type;
        final Predicate<LivingEntity> refine;
        final ButtonSetting enable;
        final ColorSetting color;

        MobEntry(
                Class<? extends LivingEntity> type,
                Predicate<LivingEntity> refine,
                ButtonSetting enable,
                ColorSetting color) {
            this.type = type;
            this.refine = refine;
            this.enable = enable;
            this.color = color;
        }

        boolean matches(LivingEntity entity) {
            return !this.type.isInstance(entity) ? false : this.refine == null || this.refine.test(entity);
        }
    }
}
