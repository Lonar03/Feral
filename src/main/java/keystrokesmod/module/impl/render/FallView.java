package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.player.NoFall;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.Window;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class FallView extends Module {
    private ButtonSetting disableWhileFlying;
    private ButtonSetting onlyWhileSneaking;
    private ButtonSetting overrideHealthFormat;
    private ButtonSetting showDamage;
    private ButtonSetting showDistance;
    private ButtonSetting showRemainingHealth;
    private SliderSetting damageThreshold;
    private double fallStartY = -1.0;
    private double groundY = -1.0;
    private float cachedFallDistance = 0.0F;
    private volatile double lastReportedY = Double.NaN;
    private volatile double lastGroundPacketY = Double.NaN;
    private int cachedEnchantmentModifier = -1;
    private ItemStack[] cachedArmorInventory = new ItemStack[4];
    private boolean armorCacheValid = false;
    private final Map<FallView.DamageCacheKey, Integer> damageCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private String damageText;
    private boolean showDamageText;
    private String distanceText;
    private int distanceTextColor = Color.WHITE.getRGB();
    private boolean showDistanceText;
    private boolean recalculateAfterTeleport;

    public FallView() {
        super("Fall View", Module.category.render);
        this.registerSetting(new DescriptionSetting("Shows fall distance damage."));
        this.registerSetting(this.damageThreshold = new SliderSetting("Damage threshold", "%", 0.0, 0.0, 100.0, 5.0));
        this.registerSetting(this.disableWhileFlying = new ButtonSetting("Disable while flying", true));
        this.registerSetting(this.onlyWhileSneaking = new ButtonSetting("Only while sneaking", false));
        this.registerSetting(this.overrideHealthFormat = new ButtonSetting("Override health format", true));
        this.registerSetting(this.showDamage = new ButtonSetting("Show damage", true));
        this.registerSetting(this.showDistance = new ButtonSetting("Show distance", false));
        this.registerSetting(this.showRemainingHealth = new ButtonSetting("Show remaining health", false));
    }

    @Override
    public void onDisable() {
        this.resetFallTracking();
        this.cachedEnchantmentModifier = -1;
        this.cachedArmorInventory = new ItemStack[4];
        this.armorCacheValid = false;
        this.damageCache.clear();
        this.recalculateAfterTeleport = false;
        this.clearOverlayState();
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent ev) {
        if (ev.phase == ForgeEvents.TickEvent.Phase.END) {
            this.clearOverlayState();
            if (mc.screen == null) {
                if (Utils.nullCheck() && !mc.player.abilities.creativeMode) {
                    if (this.recalculateAfterTeleport) {
                        this.recalculateAfterTeleport = false;
                        this.recalculateFallTracking();
                    }

                    if (!this.disableWhileFlying.isToggled() || !mc.player.abilities.canFly) {
                        if (!this.onlyWhileSneaking.isToggled() || mc.player.isSneaking()) {
                            boolean onGround = mc.player.onGround;
                            if (onGround) {
                                this.fallStartY = -1.0;
                                this.groundY = -1.0;
                                this.cachedFallDistance = 0.0F;
                            } else if (this.fallStartY == -1.0) {
                                this.fallStartY = mc.player.y;
                                this.groundY = this.findGroundY(mc.player.x, mc.player.z);
                            } else {
                                double newGroundY = this.findGroundY(mc.player.x, mc.player.z);
                                if (newGroundY != this.groundY) {
                                    this.groundY = newGroundY;
                                    this.cachedFallDistance = 0.0F;
                                }
                            }

                            float fallDistance = this.calculateFallDistance();
                            if (!(fallDistance <= 2.5F)) {
                                if (this.showDistance.isToggled()) {
                                    this.distanceText = Utils.asWholeNum(Utils.round(fallDistance, 2)) + "m";
                                    this.distanceTextColor =
                                            this.getDistanceColor(fallDistance).getRGB();
                                    this.showDistanceText = true;
                                }

                                NoFall noFall = ModuleManager.noFall;
                                if (this.showDamage.isToggled()
                                        && (noFall == null || !noFall.isEnabled() || noFall.mode.getInput() != 0.0)) {
                                    float damageFallDistance = fallDistance;
                                    double lastGroundY = this.lastGroundPacketY;
                                    if (!Double.isNaN(lastGroundY) && this.groundY != -1.0) {
                                        damageFallDistance = (float)
                                                Math.min(fallDistance, Math.max(0.0, lastGroundY - this.groundY));
                                    }

                                    StatusEffectInstance jumpEffect =
                                            mc.player.getEffectInstance(StatusEffect.JUMP_BOOST);
                                    float jumpAmplifier = jumpEffect != null ? jumpEffect.getAmplifier() + 1 : 0.0F;
                                    int jumpBoostLevel = jumpEffect != null ? jumpEffect.getAmplifier() + 1 : 0;
                                    StatusEffectInstance resistanceEffect =
                                            mc.player.getEffectInstance(StatusEffect.RESISTANCE);
                                    boolean hasResistance = resistanceEffect != null;
                                    int resistanceLevel = hasResistance ? resistanceEffect.getAmplifier() + 1 : 0;
                                    ItemStack[] armorInventory = new ItemStack[4];
                                    boolean armorChanged = false;
                                    int armorHash = 0;

                                    for (int i = 0; i < 4; i++) {
                                        ItemStack currentArmor = mc.player.inventory.getArmor(i);
                                        armorInventory[i] = currentArmor;
                                        if (this.cachedArmorInventory[i] != currentArmor) {
                                            armorChanged = true;
                                        }

                                        if (currentArmor != null) {
                                            armorHash = armorHash * 31
                                                    + (currentArmor.getItem() != null
                                                            ? currentArmor
                                                                    .getItem()
                                                                    .hashCode()
                                                            : 0);
                                            armorHash = armorHash * 31 + currentArmor.getDamage();
                                            armorHash = armorHash * 31
                                                    + EnchantmentHelper.getLevel(
                                                            Enchantment.FEATHER_FALLING.id, currentArmor);
                                        }
                                    }

                                    int enchantmentModifier = this.cachedEnchantmentModifier;
                                    if (armorChanged || !this.armorCacheValid) {
                                        long totalModifier = 0L;

                                        for (int i = 0; i < 100; i++) {
                                            int mod = EnchantmentHelper.modifyProtection(
                                                    armorInventory, DamageSource.FALL);
                                            if (mod > 20) {
                                                mod = 20;
                                            }

                                            totalModifier += mod;
                                        }

                                        enchantmentModifier = (int) Math.round(totalModifier / 100.0);
                                        this.cachedEnchantmentModifier = enchantmentModifier;
                                        System.arraycopy(armorInventory, 0, this.cachedArmorInventory, 0, 4);
                                        this.armorCacheValid = true;
                                        if (this.damageCache.size() > 500) {
                                            this.damageCache.clear();
                                        }
                                    }

                                    FallView.DamageCacheKey cacheKey = new FallView.DamageCacheKey(
                                            armorHash, damageFallDistance, jumpBoostLevel, resistanceLevel);
                                    Integer cachedFinalDamage = this.damageCache.get(cacheKey);
                                    int finalDamage;
                                    if (cachedFinalDamage != null) {
                                        finalDamage = cachedFinalDamage;
                                    } else {
                                        float damagePoints = damageFallDistance - 3.0F - jumpAmplifier;
                                        double damage = Math.max(0, MathHelper.ceil(damagePoints));
                                        if (hasResistance && damage > 0.0) {
                                            int resistanceReduction = resistanceLevel * 5;
                                            int damageMultiplier = 25 - resistanceReduction;
                                            damage = damageMultiplier * damage / 25.0;
                                        }

                                        if (damage > 0.0 && enchantmentModifier > 0) {
                                            damage = (25 - enchantmentModifier) * damage / 25.0;
                                        }

                                        finalDamage = MathHelper.ceil(damage);
                                        if (this.damageCache.size() >= 1000) {
                                            this.damageCache.clear();
                                        }

                                        this.damageCache.put(cacheKey, finalDamage);
                                    }

                                    double currentHealth = mc.player.getHealth();
                                    double damagePercent = finalDamage / currentHealth * 100.0;
                                    if (finalDamage > 0 && damagePercent > this.damageThreshold.getInput()) {
                                        double displayVal = this.showRemainingHealth.isToggled()
                                                ? Math.max(0.0, currentHealth - finalDamage)
                                                : finalDamage;
                                        float hearts = (float) displayVal;
                                        if (Settings.showHealthAsHearts.isToggled()
                                                || this.overrideHealthFormat.isToggled()) {
                                            hearts = (float) (displayVal / 2.0);
                                            hearts = (float) Utils.round(hearts, 1);
                                        }

                                        double percent = finalDamage / currentHealth;
                                        String healthStr = finalDamage >= currentHealth
                                                ? "§4"
                                                : (percent >= 0.7
                                                        ? "§c"
                                                        : (percent >= 0.5 ? "§6" : (percent >= 0.3 ? "§e" : "§a")));
                                        this.damageText = healthStr + Utils.asWholeNum(hearts);
                                        if (Settings.showHeartSymbol.isToggled()) {
                                            this.damageText = this.damageText + "§c❤§r";
                                        }

                                        this.showDamageText = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        this.recalculateAfterTeleport = true;
        this.resetFallTracking();
        this.clearOverlayState();
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.resetFallTracking();
            this.recalculateAfterTeleport = false;
            this.clearOverlayState();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onDispatchPacket(DispatchPacketEvent e) {
        if (Utils.nullCheck()
                && e.getPacket() instanceof PlayerMoveC2SPacket
                && e.getNetworkManager() == mc.player.networkHandler.getConnection()) {
            PlayerMoveC2SPacket packet = (PlayerMoveC2SPacket) e.getPacket();
            if (packet.hasPos()) {
                this.lastReportedY = packet.getMinY();
            } else if (Double.isNaN(this.lastReportedY)) {
                this.lastReportedY = mc.player.y;
            }

            if (packet.getOnGround()) {
                this.lastGroundPacketY = this.lastReportedY;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent ev) {
        if (ev.phase == ForgeEvents.TickEvent.Phase.END && mc.screen == null && Utils.nullCheck()) {
            if (this.showDamageText || this.showDistanceText) {
                Window scaledResolution = new Window(mc);
                if (this.showDamageText && this.damageText != null) {
                    mc.textRenderer.drawWithShadow(
                            this.damageText,
                            scaledResolution.getWidth() / 2 - mc.textRenderer.getWidth(this.damageText) / 2,
                            scaledResolution.getHeight() / 2 - 15,
                            Color.WHITE.getRGB());
                }

                if (this.showDistanceText && this.distanceText != null) {
                    mc.textRenderer.drawWithShadow(
                            this.distanceText,
                            scaledResolution.getWidth() / 2 - mc.textRenderer.getWidth(this.distanceText) / 2,
                            scaledResolution.getHeight() / 2 + 6,
                            this.distanceTextColor);
                }
            }
        }
    }

    private float calculateFallDistance() {
        if (this.fallStartY != -1.0 && this.groundY != -1.0) {
            if (this.cachedFallDistance == 0.0F) {
                this.cachedFallDistance = (float) Math.max(0.0, this.fallStartY - this.groundY);
            }

            return this.cachedFallDistance;
        } else {
            double currentY = mc.player.y;
            double ground = this.findGroundY(mc.player.x, mc.player.z);
            return ground == -1.0 ? 0.0F : (float) Math.max(0.0, currentY - ground);
        }
    }

    private double findGroundY(double x, double z) {
        int startY = (int) Math.floor(mc.player.y);

        for (int y = startY; y > -1; y--) {
            BlockPos pos = new BlockPos(Math.floor(x), y, Math.floor(z));
            if (mc.world.getBlockState(pos).getBlock().getMaterial() == Material.WATER) {
                return -1.0;
            }

            if (!Utils.isPlaceable(pos)) {
                return y + 1;
            }
        }

        return -1.0;
    }

    private Color getDistanceColor(float distance) {
        float minDistance = 2.5F;
        float maxDistance = 20.0F;
        float normalized = MathHelper.clamp((distance - minDistance) / (maxDistance - minDistance), 0.0F, 1.0F);
        int red = 255;
        int green = (int) (255.0F * (1.0F - normalized));
        int blue = 0;
        return new Color(red, green, blue);
    }

    private void clearOverlayState() {
        this.damageText = null;
        this.showDamageText = false;
        this.distanceText = null;
        this.distanceTextColor = Color.WHITE.getRGB();
        this.showDistanceText = false;
    }

    private void resetFallTracking() {
        this.fallStartY = -1.0;
        this.groundY = -1.0;
        this.cachedFallDistance = 0.0F;
        this.lastReportedY = Double.NaN;
        this.lastGroundPacketY = Double.NaN;
    }

    private void recalculateFallTracking() {
        this.cachedFallDistance = 0.0F;
        this.damageCache.clear();
        this.clearOverlayState();
        if (mc.player.onGround) {
            this.fallStartY = -1.0;
            this.groundY = -1.0;
        } else {
            this.fallStartY = mc.player.y;
            this.groundY = this.findGroundY(mc.player.x, mc.player.z);
        }
    }

    private static class DamageCacheKey {
        final int armorHash;
        final int fallDistanceInt;
        final int jumpBoostLevel;
        final int resistanceLevel;

        DamageCacheKey(int armorHash, float fallDistance, int jumpBoost, int resistance) {
            this.armorHash = armorHash;
            this.fallDistanceInt = Math.round(fallDistance * 100.0F);
            this.jumpBoostLevel = jumpBoost;
            this.resistanceLevel = resistance;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                FallView.DamageCacheKey that = (FallView.DamageCacheKey) o;
                return this.armorHash == that.armorHash
                        && this.fallDistanceInt == that.fallDistanceInt
                        && this.jumpBoostLevel == that.jumpBoostLevel
                        && this.resistanceLevel == that.resistanceLevel;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.armorHash * 31 * 31 * 31
                    + this.fallDistanceInt * 31 * 31
                    + this.jumpBoostLevel * 31
                    + this.resistanceLevel;
        }
    }
}
