package keystrokesmod.module.impl.network;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.network.DifferentThreadException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import org.lwjgl.opengl.GL11;

public class Freeze extends Module {
    private final SliderSetting autoDisable;
    public final ButtonSetting activateOnHit;
    public final ButtonSetting disableOnBedBreak;
    public final ButtonSetting disableOnInteract;
    public final ButtonSetting showFreezeTicks;
    public final ButtonSetting showKnockbackDirection;
    public final ButtonSetting updateChatMessages;
    public final ButtonSetting updateBlockChanges;
    public final ButtonSetting updatePlayerHealth;
    public final ButtonSetting updatePlayerPositions;
    public final ButtonSetting updateScoreboard;
    public final ButtonSetting updateSoundEffects;
    private final Object packetLock = new Object();
    private final List<Packet<?>> heldPackets = new ArrayList<>();
    private volatile boolean frozen;
    private volatile boolean releasing;
    private volatile boolean disableNext;
    private volatile int ticks;
    private volatile ClientPlayNetworkHandler packetHandler;
    private volatile ClientWorld packetWorld;
    private boolean shown;
    private long shownAt;
    private long hiddenAt;
    private String text;
    private volatile boolean knockback;
    private volatile double knockbackX;
    private volatile double knockbackZ;

    public Freeze() {
        super("Freeze", Module.category.network);
        this.registerSetting(new DescriptionSetting("Freeze inbound packets."));
        this.registerSetting(
                this.autoDisable = new SliderSetting("Auto-disable", " second", "§cNever", 0.0, 0.0, 15.0, 0.2));
        this.registerSetting(this.activateOnHit = new ButtonSetting("Activate on hit", false));
        this.registerSetting(this.disableOnBedBreak = new ButtonSetting("Disable on bed break", false));
        this.registerSetting(this.disableOnInteract = new ButtonSetting("Disable on interact", false));
        this.registerSetting(this.showFreezeTicks = new ButtonSetting("Show freeze ticks", true));
        this.registerSetting(this.showKnockbackDirection = new ButtonSetting("Show knockback direction", true));
        this.registerSetting(this.updateChatMessages = new ButtonSetting("Update chat messages", true));
        this.registerSetting(this.updateBlockChanges = new ButtonSetting("Update block changes", true));
        this.registerSetting(this.updatePlayerHealth = new ButtonSetting("Update player health", true));
        this.registerSetting(this.updatePlayerPositions = new ButtonSetting("Update player positions", true));
        this.registerSetting(this.updateScoreboard = new ButtonSetting("Update scoreboard", true));
        this.registerSetting(this.updateSoundEffects = new ButtonSetting("Update sound effects", true));
        this.alwaysOn = true;
        ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onEnable() {
        if (this.frozen || this.releasing || !Utils.nullCheck() || mc.getNetworkHandler() == null) {
            this.disable();
        } else if (mc.isSingleplayer()) {
            Utils.sendMessage("&cCan't enable Freeze in singleplayer.");
            this.disable();
        } else if (blinksInbound()) {
            Utils.sendMessage("&cConflicted modules, disable blink or use outbound-only.");
            this.disable();
        } else {
            synchronized (this.packetLock) {
                this.heldPackets.clear();
                this.packetHandler = mc.getNetworkHandler();
                this.packetWorld = mc.world;
                this.ticks = 0;
                this.disableNext = false;
            }

            this.clearKnockback();
            this.shown = true;
            this.shownAt = System.currentTimeMillis();
            this.text = "idle";
            if (!this.activateOnHit.isToggled()) {
                this.startFreeze();
                this.updateText();
            }
        }
    }

    @Override
    public void onDisable() {
        this.shown = false;
        this.hiddenAt = System.currentTimeMillis();
        this.clearKnockback();
        this.releasePackets();
        this.disableNext = false;
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onReceivePacketHigh(ReceivePacketEvent event) {
        if (this.isEnabled() && !event.isCanceled() && event.getPacket() != null && this.packetHandler != null) {
            Packet<?> packet = event.getPacket();
            if (Utils.nullCheck()
                    && !mc.isSingleplayer()
                    && mc.getNetworkHandler() == this.packetHandler
                    && mc.world == this.packetWorld
                    && !(packet instanceof DisconnectS2CPacket)
                    && !(packet instanceof LoginS2CPacket)
                    && !(packet instanceof PlayerRespawnS2CPacket)) {
                if (!this.frozen) {
                    if (!this.activateOnHit.isToggled() || !this.isHitPacket(packet)) {
                        return;
                    }

                    this.startFreeze();
                    if (!this.frozen) {
                        return;
                    }
                }

                if (packet instanceof EntityVelocityS2CPacket) {
                    EntityVelocityS2CPacket velocity = (EntityVelocityS2CPacket) packet;
                    if (velocity.getId() == mc.player.getNetworkId()) {
                        if (Math.abs(velocity.getVelocityX()) < 400 && Math.abs(velocity.getVelocityZ()) < 400) {
                            this.clearKnockback();
                        } else {
                            this.knockbackX = velocity.getVelocityX() / 8000.0;
                            this.knockbackZ = velocity.getVelocityZ() / 8000.0;
                            this.knockback = true;
                        }
                    }
                }

                if (!this.shouldUpdateImmediately(packet)) {
                    synchronized (this.packetLock) {
                        if (this.isEnabled()
                                && this.frozen
                                && mc.getNetworkHandler() == this.packetHandler
                                && mc.world == this.packetWorld) {
                            this.heldPackets.add(packet);
                            event.setCanceled(true);
                        }
                    }
                }
            } else {
                this.discardPackets();
                this.disableNext = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onSendPacketHigh(SendPacketEvent event) {
        if (this.isEnabled() && Utils.nullCheck() && !event.isCanceled() && event.getPacket() != null) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof PlayerInteractEntityC2SPacket) {
                if (this.frozen && this.disableOnInteract.isToggled()) {
                    this.disableNext = true;
                }
            } else if (packet instanceof PlayerHandActionC2SPacket && this.disableOnBedBreak.isToggled()) {
                PlayerHandActionC2SPacket digging = (PlayerHandActionC2SPacket) packet;
                if (digging.getAction() == Action.STOP_DESTROY_BLOCK
                        && mc.world.getBlockState(digging.getPos()).getBlock() == Blocks.BED) {
                    this.disableNext = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onGameTick(GameTickEvent event) {
        if (this.isEnabled()) {
            if (Utils.nullCheck()
                    && !mc.isSingleplayer()
                    && !mc.player.removed
                    && mc.getNetworkHandler() == this.packetHandler
                    && mc.world == this.packetWorld) {
                if (this.frozen) {
                    if (this.disableNext
                            || this.autoDisable.getInput() > 0.0 && this.ticks >= this.autoDisable.getInput() * 20.0) {
                        this.disable();
                        return;
                    }

                    this.ticks++;
                }

                this.updateText();
            } else {
                this.discardPackets();
                this.disable();
            }
        }
    }

    private void updateText() {
        this.text = this.frozen
                ? String.valueOf(
                        this.autoDisable.getInput() > 0.0
                                ? (int) (this.autoDisable.getInput() * 20.0) - this.ticks + 1
                                : this.ticks)
                : "idle";
    }

    private void startFreeze() {
        synchronized (this.packetLock) {
            if (!this.isEnabled()
                    || this.frozen
                    || mc.getNetworkHandler() != this.packetHandler
                    || mc.world != this.packetWorld) {
                return;
            }

            this.frozen = true;
            this.ticks = 0;
        }

        if (ModuleManager.knockbackDelay != null) {
            ModuleManager.knockbackDelay.flush();
        }

        if (ModuleManager.backtrack != null) {
            ModuleManager.backtrack.flush();
        }
    }

    private boolean isHitPacket(Packet<?> packet) {
        if (packet instanceof EntityVelocityS2CPacket) {
            EntityVelocityS2CPacket velocity = (EntityVelocityS2CPacket) packet;
            return velocity.getId() == mc.player.getNetworkId() && velocity.getVelocityY() != -627;
        }

        if (!(packet instanceof EntityStatusEffectS2CPacket)) {
            return false;
        }

        EntityStatusEffectS2CPacket effect = (EntityStatusEffectS2CPacket) packet;
        return effect.getId() == mc.player.getNetworkId()
                && effect.getEffect() == StatusEffect.SLOWNESS.id
                && effect.getDuration() == 20
                && Utils.getBedwarsStatus() == 2;
    }

    private boolean shouldUpdateImmediately(Packet<?> packet) {
        return this.updateChatMessages.isToggled() && packet instanceof ChatMessageS2CPacket
                || this.updateBlockChanges.isToggled()
                        && (packet instanceof BlocksUpdateS2CPacket || packet instanceof BlockUpdateS2CPacket)
                || this.updatePlayerHealth.isToggled() && packet instanceof PlayerHealthS2CPacket
                || this.updatePlayerPositions.isToggled() && packet instanceof EntityMoveS2CPacket
                || this.updateScoreboard.isToggled()
                        && (packet instanceof ScoreboardObjectiveS2CPacket
                                || packet instanceof ScoreboardScoreS2CPacket
                                || packet instanceof ScoreboardDisplayS2CPacket
                                || packet instanceof TeamS2CPacket)
                || this.updateSoundEffects.isToggled() && packet instanceof SoundEventS2CPacket;
    }

    private void discardPackets() {
        synchronized (this.packetLock) {
            this.heldPackets.clear();
            this.frozen = false;
            this.packetWorld = null;
        }

        this.clearKnockback();
    }

    private void releasePackets() {
        List<Packet<?>> packets;
        ClientPlayNetworkHandler handler;
        ClientWorld world;
        synchronized (this.packetLock) {
            if (this.releasing) {
                return;
            }

            this.releasing = true;
            this.frozen = false;
            packets = new ArrayList<>(this.heldPackets);
            this.heldPackets.clear();
            handler = this.packetHandler;
            world = this.packetWorld;
            this.packetHandler = null;
            this.packetWorld = null;
        }

        try {
            for (Packet<?> packet : packets) {
                if (!Utils.nullCheck() || handler == null || mc.getNetworkHandler() != handler || mc.world != world) {
                    break;
                }

                try {
                    ReceivePacketEvent event = new ReceivePacketEvent(packet);
                    if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(event)) {
                        ((Packet) packet).handle(handler);
                    }
                } catch (DifferentThreadException var12) {
                } catch (Exception exception) {
                    Utils.sendDebugMessage("Freeze failed to release " + ClientPlatform.className(packet.getClass()));
                }
            }
        } finally {
            this.releasing = false;
        }
    }

    private void clearKnockback() {
        this.knockback = false;
        this.knockbackX = 0.0;
        this.knockbackZ = 0.0;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck() && mc.screen == null) {
            long age = System.currentTimeMillis() - (this.shown ? this.shownAt : this.hiddenAt);
            int alpha = (int) (240.0 * (this.shown ? Math.min(1.0, age / 125.0) : Math.max(0.0, 1.0 - age / 125.0)));
            if (!this.shown && alpha <= 0) {
                this.text = null;
            }

            boolean drawArrow = this.showKnockbackDirection.isToggled() && this.knockback;
            boolean drawText = this.showFreezeTicks.isToggled()
                    && this.text != null
                    && alpha > 0
                    && (ModuleManager.blink == null || !ModuleManager.blink.isEnabled());
            if (drawArrow || drawText) {
                Window resolution = new Window(mc);
                int themeColor = Theme.getGradient(Theme.DEFAULT_THEME_INDEX, 0.0);
                boolean blend = GL11.glIsEnabled(3042);
                boolean texture = GL11.glIsEnabled(3553);
                boolean alphaTest = GL11.glIsEnabled(3008);
                boolean lineSmooth = GL11.glIsEnabled(2848);
                float lineWidth = GL11.glGetFloat(2849);
                int sourceRGB = GL11.glGetInteger(32969);
                int destinationRGB = GL11.glGetInteger(32968);
                int sourceAlpha = GL11.glGetInteger(32971);
                int destinationAlpha = GL11.glGetInteger(32970);
                GlStateManager.pushMatrix();

                try {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                    if (drawArrow) {
                        this.renderArrow(resolution, themeColor);
                    }

                    if (drawText) {
                        GlStateManager.enableTexture();
                        GlStateManager.enableAlphaTest();
                        float halfWidth = mc.textRenderer.getWidth(this.text) / 2.0F - 0.5F;
                        mc.textRenderer.draw(
                                this.text,
                                resolution.getWidth() / 2.0F - halfWidth,
                                resolution.getHeight() / 2.0F + 9.0F,
                                Utils.mergeAlpha(themeColor, Math.max(4, alpha)),
                                true);
                    }
                } finally {
                    GL11.glLineWidth(lineWidth);
                    if (lineSmooth) {
                        GL11.glEnable(2848);
                    } else {
                        GL11.glDisable(2848);
                    }

                    if (texture) {
                        GlStateManager.enableTexture();
                    } else {
                        GlStateManager.disableTexture();
                    }

                    if (alphaTest) {
                        GlStateManager.enableAlphaTest();
                    } else {
                        GlStateManager.disableAlphaTest();
                    }

                    GlStateManager.blendFuncSeparate(sourceRGB, destinationRGB, sourceAlpha, destinationAlpha);
                    if (blend) {
                        GlStateManager.enableBlend();
                    } else {
                        GlStateManager.disableBlend();
                    }

                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private void renderArrow(Window resolution, int themeColor) {
        double direction = Math.atan2(this.knockbackZ, this.knockbackX) - Math.toRadians(mc.player.yaw + 90.0F);
        float sin = (float) Math.sin(direction);
        float cos = (float) (-Math.cos(direction));
        float centerX = resolution.getWidth() / 2.0F;
        float centerY = resolution.getHeight() / 2.0F - 14.0F;
        float x1 = centerX - sin * 2.5F;
        float y1 = centerY - cos * 2.5F;
        float x2 = centerX + sin * 7.0F;
        float y2 = centerY + cos * 7.0F;
        float x3 = x2 - sin * 3.0F - cos * 1.95F;
        float y3 = y2 - cos * 3.0F + sin * 1.95F;
        float x4 = x2 - sin * 3.0F + cos * 1.95F;
        float y4 = y2 - cos * 3.0F - sin * 1.95F;
        GlStateManager.disableTexture();
        GlStateManager.disableAlphaTest();
        GL11.glEnable(2848);
        GL11.glLineWidth(3.5F);
        GlStateManager.color4f(0.0F, 0.0F, 0.0F, 0.6901961F);
        this.drawArrowLines(x1, y1, x2, y2, x3, y3, x4, y4);
        GL11.glLineWidth(1.75F);
        GlStateManager.color4f(
                (themeColor >> 16 & 0xFF) / 255.0F,
                (themeColor >> 8 & 0xFF) / 255.0F,
                (themeColor & 0xFF) / 255.0F,
                1.0F);
        this.drawArrowLines(x1, y1, x2, y2, x3, y3, x4, y4);
        GL11.glBegin(7);
        GL11.glVertex2f(centerX - 1.75F, centerY - 1.75F);
        GL11.glVertex2f(centerX + 1.75F, centerY - 1.75F);
        GL11.glVertex2f(centerX + 1.75F, centerY + 1.75F);
        GL11.glVertex2f(centerX - 1.75F, centerY + 1.75F);
        GL11.glEnd();
    }

    private void drawArrowLines(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        GL11.glBegin(1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x4, y4);
        GL11.glEnd();
    }

    private static boolean blinksInbound() {
        Blink blink = ModuleManager.blink;
        return blink != null && blink.isEnabled() && blink.delaysInboundPackets();
    }

    @Override
    public String getInfo() {
        return this.ticks > 0 ? String.valueOf(this.ticks) : "Idle";
    }
}
