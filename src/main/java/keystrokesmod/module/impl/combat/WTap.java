package keystrokesmod.module.impl.combat;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class WTap extends Module {
    private SliderSetting delayBetweenReset;
    private SliderSetting delayUntilReset;
    private SliderSetting chance;
    private ButtonSetting playersOnly;
    private long pendingResetAtMs;
    private long lastResetStartMs;
    private boolean waitingForSprintRestart;
    private boolean wasSprinting;
    public static boolean stopSprint = false;

    public WTap() {
        super("WTap", Module.category.combat);
        this.registerSetting(this.chance = new SliderSetting("Chance", "%", 100.0, 0.0, 100.0, 1.0));
        this.registerSetting(
                this.delayBetweenReset = new SliderSetting("Delay between reset", "ms", 300.0, 0.0, 1000.0, 10.0));
        this.registerSetting(
                this.delayUntilReset = new SliderSetting("Delay until reset", "ms", 150.0, 0.0, 1000.0, 10.0));
        this.registerSetting(this.playersOnly = new ButtonSetting("Players only", true));
        this.liteModule = true;
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        this.pendingResetAtMs = 0L;
        this.lastResetStartMs = 0L;
        this.waitingForSprintRestart = false;
        this.wasSprinting = false;
        stopSprint = false;
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck() && !mc.player.removed) {
            long now = System.currentTimeMillis();
            boolean sprintingNow = mc.player.isSprinting();
            if (this.waitingForSprintRestart && sprintingNow && !this.wasSprinting) {
                this.lastResetStartMs = now;
                this.waitingForSprintRestart = false;
            }

            if (this.pendingResetAtMs > 0L && now >= this.pendingResetAtMs) {
                stopSprint = true;
                this.pendingResetAtMs = 0L;
                this.waitingForSprintRestart = true;
            }

            this.wasSprinting = sprintingNow;
        } else {
            this.pendingResetAtMs = 0L;
            this.waitingForSprintRestart = false;
            this.wasSprinting = false;
            stopSprint = false;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onAttack(ForgeEvents.AttackEntityEvent event) {
        if (Utils.nullCheck() && event.entityPlayer == mc.player && mc.player.isSprinting()) {
            if (this.chance.getInput() != 0.0) {
                if (this.playersOnly.isToggled()) {
                    if (!(event.target instanceof PlayerEntity)) {
                        return;
                    }

                    if (AntiBot.isBot(event.target)) {
                        return;
                    }
                } else if (!(event.target instanceof LivingEntity)) {
                    return;
                }

                if (((LivingEntity) event.target).deathTicks == 0) {
                    if (this.pendingResetAtMs <= 0L) {
                        long currentMs = System.currentTimeMillis();
                        long betweenResetDelay = (long) this.delayBetweenReset.getInput();
                        if (this.lastResetStartMs <= 0L || currentMs - this.lastResetStartMs >= betweenResetDelay) {
                            if (this.chance.getInput() != 100.0) {
                                double ch = Math.random();
                                if (ch >= this.chance.getInput() / 100.0) {
                                    return;
                                }
                            }

                            this.pendingResetAtMs = currentMs + (long) this.delayUntilReset.getInput();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.pendingResetAtMs = 0L;
        this.lastResetStartMs = 0L;
        this.waitingForSprintRestart = false;
        this.wasSprinting = false;
        stopSprint = false;
    }

    public boolean isResetPending() {
        return this.isEnabled() && (this.pendingResetAtMs > 0L || stopSprint);
    }
}
