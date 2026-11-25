package keystrokesmod.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.FutureTask;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class Velocity extends Module {
    private static final Logger logger = LogManager.getLogger(Velocity.class);
    public static SliderSetting horizontal;
    public static SliderSetting vertical;
    private SliderSetting chance;
    private ButtonSetting attackAfterKnockback;
    private ButtonSetting onlyWhileTargeting;
    private ButtonSetting disableS;
    private final Queue<Runnable> postAttackActions = new ArrayDeque<>();
    private Connection postAttackConnection;
    private ClientWorld postAttackWorld;
    private LocalClientPlayerEntity postAttackPlayer;
    private double postAttackMultiplierX = 1.0;
    private double postAttackMultiplierZ = 1.0;
    private boolean inClientTick;
    private boolean finishingPostAttacks;
    private boolean replayingPostAttacks;
    public boolean disable;

    public Velocity() {
        super("Velocity", Module.category.combat, 0);
        this.registerSetting(horizontal = new SliderSetting("Horizontal", "%", 90.0, 0.0, 100.0, 1.0));
        this.registerSetting(vertical = new SliderSetting("Vertical", "%", 100.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.chance = new SliderSetting("Chance", "%", 100.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.attackAfterKnockback = new ButtonSetting("Attack after knockback", true));
        this.registerSetting(this.onlyWhileTargeting = new ButtonSetting("Only while targeting", false));
        this.registerSetting(this.disableS = new ButtonSetting("Disable while holding S", false));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return (int) horizontal.getInput() + "% " + (int) vertical.getInput() + "%";
    }

    public void beginClientTick() {
        if (this.inClientTick) {
            this.finishClientTick();
        }

        this.inClientTick = true;
    }

    public boolean deferAttackPacket(Connection connection, Packet<?> packet, Runnable dispatch) {
        if (!this.replayingPostAttacks
                && mc.isOnSameThread()
                && mc.getNetworkHandler() != null
                && connection == mc.getNetworkHandler().getConnection()) {
            if (!this.postAttackActions.isEmpty()) {
                if (this.isPostAttackContextValid() && this.postAttackConnection == connection) {
                    this.postAttackActions.add(dispatch);
                    return true;
                }

                this.clearPostAttacks();
            }

            if (this.inClientTick
                    && !this.finishingPostAttacks
                    && this.isEnabled()
                    && this.attackAfterKnockback.isToggled()
                    && packet instanceof PlayerInteractEntityC2SPacket
                    && ((PlayerInteractEntityC2SPacket) packet).getAction() == Action.ATTACK
                    && this.canModifyVelocity()
                    && !mc.player.removed) {
                this.postAttackConnection = connection;
                this.postAttackWorld = mc.world;
                this.postAttackPlayer = mc.player;
                this.postAttackActions.add(dispatch);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static void applyAttackSlowdown(PlayerEntity player, double multiplierX, double multiplierZ) {
        recordAttackSlowdown(player, multiplierX, multiplierZ);
        player.velocityX *= multiplierX;
        player.velocityZ *= multiplierZ;
    }

    public static void recordAttackSlowdown(PlayerEntity player, double multiplierX, double multiplierZ) {
        Velocity velocity = ModuleManager.velocity;
        if (velocity != null) {
            velocity.recordPostAttackSlowdown(player, multiplierX, multiplierZ);
        }
    }

    private void recordPostAttackSlowdown(PlayerEntity player, double multiplierX, double multiplierZ) {
        if (mc.isOnSameThread()
                && !this.replayingPostAttacks
                && !this.postAttackActions.isEmpty()
                && player == this.postAttackPlayer
                && this.isPostAttackContextValid()) {
            this.postAttackMultiplierX *= multiplierX;
            this.postAttackMultiplierZ *= multiplierZ;
        }
    }

    public void onEntityVelocityApplied(EntityVelocityS2CPacket packet) {
        if (mc.isOnSameThread()
                && !this.replayingPostAttacks
                && !this.postAttackActions.isEmpty()
                && this.isPostAttackContextValid()
                && packet.getId() == this.postAttackPlayer.getNetworkId()) {
            this.postAttackPlayer.velocityX = this.postAttackPlayer.velocityX * this.postAttackMultiplierX;
            this.postAttackPlayer.velocityZ = this.postAttackPlayer.velocityZ * this.postAttackMultiplierZ;
        }
    }

    public void finishClientTick() {
        this.inClientTick = false;
        if (!this.finishingPostAttacks && !this.postAttackActions.isEmpty()) {
            this.finishingPostAttacks = true;

            try {
                Queue<FutureTask<?>> scheduledTasks = ((IAccessorMinecraft) mc).getScheduledTasks();
                FutureTask<?> task;
                synchronized (scheduledTasks) {
                    while ((task = scheduledTasks.poll()) != null) {
                        net.minecraft.util.Utils.run(task, logger);
                    }
                }

                this.replayingPostAttacks = true;

                Runnable dispatch;
                while (this.isPostAttackContextValid() && (dispatch = this.postAttackActions.poll()) != null) {
                    dispatch.run();
                }
            } finally {
                this.clearPostAttacks();
                this.replayingPostAttacks = false;
                this.finishingPostAttacks = false;
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.isOnSameThread()) {
            this.finishClientTick();
        } else {
            mc.executeTask(this::finishClientTick);
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.attackAfterKnockback && !this.attackAfterKnockback.isToggled()) {
            this.finishClientTick();
        }
    }

    private boolean isPostAttackContextValid() {
        return this.postAttackPlayer != null
                && mc.world == this.postAttackWorld
                && mc.player == this.postAttackPlayer
                && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getConnection() == this.postAttackConnection
                && this.postAttackConnection.isConnected()
                && !this.postAttackPlayer.removed;
    }

    private void clearPostAttacks() {
        this.postAttackActions.clear();
        this.postAttackConnection = null;
        this.postAttackWorld = null;
        this.postAttackPlayer = null;
        this.postAttackMultiplierX = 1.0;
        this.postAttackMultiplierZ = 1.0;
    }

    private boolean canModifyVelocity() {
        return Utils.nullCheck()
                && !LongJump.stopVelocity
                && !this.disable
                && (ModuleManager.antiKnockback == null || !ModuleManager.antiKnockback.isEnabled())
                && (!this.onlyWhileTargeting.isToggled()
                        || mc.crosshairTarget != null && mc.crosshairTarget.entity != null)
                && (!this.disableS.isToggled() || !Keyboard.isKeyDown(mc.options.backKey.getKeyCode()));
    }

    @ForgeEvents.SubscribeEvent
    public void onLivingUpdate(ForgeEvents.LivingEvent.LivingUpdateEvent ev) {
        if (this.canModifyVelocity()) {
            if (ev.entity != mc.player) {
                return;
            }

            if (mc.player.damagedTime <= 0 || mc.player.damagedTimer != mc.player.damagedTime) {
                return;
            }

            if (this.chance.getInput() == 0.0) {
                return;
            }

            if (this.chance.getInput() != 100.0) {
                double ch = Math.random();
                if (ch >= this.chance.getInput() / 100.0) {
                    return;
                }
            }

            if (horizontal.getInput() != 100.0) {
                mc.player.velocityX = mc.player.velocityX * (horizontal.getInput() / 100.0);
                mc.player.velocityZ = mc.player.velocityZ * (horizontal.getInput() / 100.0);
            }

            if (vertical.getInput() != 100.0) {
                mc.player.velocityY = mc.player.velocityY * (vertical.getInput() / 100.0);
            }
        }
    }
}
