package keystrokesmod.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.input.Mouse;

public class AimAssist extends Module {
    private SliderSetting mode;
    private SliderSetting aimAxis;
    private SliderSetting speed;
    private SliderSetting multipointHorizontal;
    private SliderSetting multipointVertical;
    private SliderSetting randomization;
    private SliderSetting fov;
    private SliderSetting range;
    private SliderSetting sortMode;
    private ButtonSetting aimInvis;
    private ButtonSetting ignoreManualAim;
    private ButtonSetting clickAim;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting ignoreBehindWalls;
    private ButtonSetting ignoreBehindEntities;
    private ButtonSetting stopWhenBreaking;
    private SliderSetting hoverDelay;
    private ButtonSetting weaponOnly;
    private long miningStartTime = -1L;
    private boolean normalAimRunning;
    private boolean cameraInitialized;
    private LocalClientPlayerEntity cameraPlayer;
    private float cameraYaw;
    private float cameraPitch;
    private float prevCameraYaw;
    private float prevCameraPitch;
    private String[] AIM_MODES = new String[] {"Normal", "Silent"};
    private String[] AIM_AXIS_MODES = new String[] {"Both", "Vertical", "Horizontal"};
    private String[] SORT_MODES = new String[] {"Health", "Angle", "Hurt time", "Distance"};

    public AimAssist() {
        super("Aim Assist", Module.category.combat);
        this.liteModule = true;
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, this.AIM_MODES));
        this.registerSetting(this.aimAxis = new SliderSetting("Axis", 0, this.AIM_AXIS_MODES));
        this.registerSetting(this.speed = new SliderSetting("Speed", 10.0, 1.0, 30.0, 1.0));
        this.registerSetting(
                this.multipointHorizontal = new SliderSetting("Multipoint horizontal", "%", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(
                this.multipointVertical = new SliderSetting("Multipoint vertical", "%", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.randomization = new SliderSetting("Randomization", "%", 50.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.fov = new SliderSetting("FOV", 90.0, 15.0, 360.0, 1.0));
        this.registerSetting(this.range = new SliderSetting("Range", 4.5, 0.0, 5.0, 0.1));
        this.registerSetting(this.hoverDelay = new SliderSetting("Hover delay", " ms", 100.0, 0.0, 500.0, 10.0));
        this.registerSetting(this.sortMode = new SliderSetting("Sort", 1, this.SORT_MODES));
        this.registerSetting(this.ignoreManualAim = new ButtonSetting("Ignore manual aim", false));
        this.registerSetting(this.ignoreBehindWalls = new ButtonSetting("Ignore behind walls", false));
        this.registerSetting(this.ignoreBehindEntities = new ButtonSetting("Ignore behind entities", false));
        this.registerSetting(this.aimInvis = new ButtonSetting("Aim invis", false));
        this.registerSetting(this.clickAim = new ButtonSetting("Require mouse", true));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(this.stopWhenBreaking = new ButtonSetting("Stop when breaking", false));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        return this.AIM_MODES[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean normalMode = this.mode.getInput() == 0.0;
        this.ignoreManualAim.setVisible(normalMode, this);
        if (!normalMode) {
            this.stopNormalAim();
        }

        this.hoverDelay.setVisible(this.stopWhenBreaking.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.stopNormalAim();
        this.miningStartTime = -1L;
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.ignoreManualAim && !this.ignoreManualAim.isToggled()) {
            this.releaseDetachedCamera();
        }
    }

    public boolean handleMouseInput(LocalClientPlayerEntity player, float yaw, float pitch) {
        if (!this.shouldIgnoreManualAim()) {
            this.releaseDetachedCamera();
            return false;
        }

        if (!this.cameraInitialized || this.cameraPlayer != player) {
            this.cameraPlayer = player;
            this.cameraYaw = player.yaw;
            this.cameraPitch = player.pitch;
            this.prevCameraYaw = player.lastYaw;
            this.prevCameraPitch = player.lastPitch;
            this.cameraInitialized = true;
        }

        float aimYaw = player.yaw;
        float aimPitch = player.pitch;
        float prevAimYaw = player.lastYaw;
        float prevAimPitch = player.lastPitch;
        player.yaw = this.cameraYaw;
        player.pitch = this.cameraPitch;
        player.lastYaw = this.prevCameraYaw;
        player.lastPitch = this.prevCameraPitch;
        player.updateLocalPlayerCamera(yaw, pitch);
        this.cameraYaw = player.yaw;
        this.cameraPitch = player.pitch;
        this.prevCameraYaw = player.lastYaw;
        this.prevCameraPitch = player.lastPitch;
        player.yaw = aimYaw;
        player.pitch = aimPitch;
        player.lastYaw = prevAimYaw;
        player.lastPitch = prevAimPitch;
        return true;
    }

    public boolean isDetachedCameraActive() {
        return this.shouldIgnoreManualAim()
                && this.cameraInitialized
                && this.cameraPlayer == mc.player
                && mc.getCamera() == this.cameraPlayer;
    }

    public float getCameraYaw() {
        return this.cameraYaw;
    }

    public float getCameraPitch() {
        return this.cameraPitch;
    }

    public float getPrevCameraYaw() {
        return this.prevCameraYaw;
    }

    public float getPrevCameraPitch() {
        return this.prevCameraPitch;
    }

    private boolean shouldIgnoreManualAim() {
        return this.isEnabled()
                && this.normalAimRunning
                && this.mode.getInput() == 0.0
                && this.ignoreManualAim.isToggled();
    }

    private void beginNormalAim() {
        this.normalAimRunning = true;
        if (this.ignoreManualAim.isToggled()
                && mc.player != null
                && (!this.cameraInitialized || this.cameraPlayer != mc.player)) {
            this.cameraPlayer = mc.player;
            this.cameraYaw = this.cameraPlayer.yaw;
            this.cameraPitch = this.cameraPlayer.pitch;
            this.prevCameraYaw = this.cameraPlayer.lastYaw;
            this.prevCameraPitch = this.cameraPlayer.lastPitch;
            this.cameraInitialized = true;
        }
    }

    private void stopNormalAim() {
        this.normalAimRunning = false;
        this.releaseDetachedCamera();
    }

    private void releaseDetachedCamera() {
        this.cameraInitialized = false;
        this.cameraPlayer = null;
    }

    public Entity getAimAssistTarget() {
        if (Utils.nullCheck() && (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver())) {
            if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
                return null;
            } else {
                return !this.conditionsMet() ? null : this.getEnemy(this.mode.getInput() == 1.0);
            }
        } else {
            return null;
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
            if (ModuleManager.killAura == null || !ModuleManager.killAura.isEnabled() || KillAura.target == null) {
                if (this.mode.getInput() != 0.0 && this.conditionsMet()) {
                    Entity en = this.getEnemy(true);
                    if (en != null) {
                        int speedVal = (int) this.speed.getInput();
                        double multipointH = this.multipointHorizontal.getInput();
                        double multipointV = this.multipointVertical.getInput();
                        float randomizationPercent = (float) this.randomization.getInput();
                        boolean useBackup = this.ignoreBehindWalls.isToggled() || this.ignoreBehindEntities.isToggled();
                        float[] rot = RotationHelper.get()
                                .getRotationsToTarget(
                                        en,
                                        e,
                                        speedVal,
                                        multipointH,
                                        multipointV,
                                        randomizationPercent,
                                        useBackup,
                                        this.range.getInput(),
                                        !this.ignoreBehindWalls.isToggled(),
                                        !this.ignoreBehindEntities.isToggled());
                        if (rot != null) {
                            int aimAxisMode = (int) this.aimAxis.getInput();
                            if (aimAxisMode != 1) {
                                RotationHelper.get().forceMovementFix = true;
                                e.yaw = rot[0];
                            }

                            if (aimAxisMode != 2) {
                                e.pitch = rot[1];
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            this.stopNormalAim();
        } else if (this.mode.getInput() != 1.0 && this.conditionsMet()) {
            Entity en = this.getEnemy(false);
            if (en == null) {
                this.stopNormalAim();
            } else {
                int speedVal = (int) this.speed.getInput();
                double multipointH = this.multipointHorizontal.getInput();
                double multipointV = this.multipointVertical.getInput();
                float randomizationPercent = (float) this.randomization.getInput();
                boolean useBackup = this.ignoreBehindWalls.isToggled() || this.ignoreBehindEntities.isToggled();
                float[] rot = RotationHelper.get()
                        .getRotationsToTarget(
                                en,
                                speedVal,
                                multipointH,
                                multipointV,
                                randomizationPercent,
                                useBackup,
                                this.range.getInput(),
                                !this.ignoreBehindWalls.isToggled(),
                                !this.ignoreBehindEntities.isToggled());
                if (rot == null) {
                    this.stopNormalAim();
                } else {
                    this.beginNormalAim();
                    int aimAxisMode = (int) this.aimAxis.getInput();
                    if (aimAxisMode != 1) {
                        mc.player.yaw = rot[0];
                    }

                    if (aimAxisMode != 2) {
                        mc.player.pitch = rot[1];
                    }
                }
            }
        } else {
            this.stopNormalAim();
        }
    }

    private Entity getEnemy(boolean silentMode) {
        int fovVal = (int) this.fov.getInput();
        float viewYaw = mc.player.yaw;
        if (silentMode) {
            Float serverYaw = RotationHelper.get().getServerYaw();
            if (serverYaw != null) {
                viewYaw = serverYaw;
            }
        }

        List<PlayerEntity> candidates = new ArrayList<>();

        for (PlayerEntity entityPlayer : mc.world.players) {
            if (entityPlayer != mc.player
                    && entityPlayer.deathTicks == 0
                    && !Utils.isFriended(entityPlayer)
                    && (!this.ignoreTeammates.isToggled() || !Utils.isTeammate(entityPlayer))
                    && (this.aimInvis.isToggled() || !entityPlayer.isInvisible())
                    && !(RotationUtils.distanceSqFromEyeToClosestOnAABB(entityPlayer)
                            > this.range.getInput() * this.range.getInput())
                    && !AntiBot.isBot(entityPlayer)) {
                if (fovVal != 360) {
                    float angleToEntity = RotationUtils.angle(entityPlayer.x, entityPlayer.z);
                    if (!Utils.inFov(viewYaw, fovVal, angleToEntity)) {
                        continue;
                    }
                }

                candidates.add(entityPlayer);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<PlayerEntity> primary;
        switch ((int) this.sortMode.getInput()) {
            case 0:
                primary = Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorption());
                break;
            case 1:
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
                break;
            case 2:
                primary = Comparator.comparingInt(p -> p.damagedTimer);
                break;
            case 3:
                primary = Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p));
                break;
            default:
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
        }

        candidates.sort(primary.thenComparingDouble(p -> mc.player.squaredDistanceTo(p)));
        if (!this.ignoreBehindWalls.isToggled() && !this.ignoreBehindEntities.isToggled()) {
            return (Entity) candidates.get(0);
        }

        double multipointH = this.multipointHorizontal.getInput();
        double multipointV = this.multipointVertical.getInput();
        double rangeVal = this.range.getInput();
        boolean allowThroughBlocks = !this.ignoreBehindWalls.isToggled();
        boolean allowThroughEntities = !this.ignoreBehindEntities.isToggled();

        for (PlayerEntity candidate : candidates) {
            if (RotationUtils.hasValidAimPoint(
                    candidate, multipointH, multipointV, rangeVal, allowThroughBlocks, allowThroughEntities)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean conditionsMet() {
        if (mc.screen != null || !mc.focused) {
            return false;
        }

        if (this.weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }

        if (this.clickAim.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }

        if (this.stopWhenBreaking.isToggled() && Utils.isMining()) {
            if (this.miningStartTime == -1L) {
                this.miningStartTime = System.currentTimeMillis();
            }

            long elapsed = System.currentTimeMillis() - this.miningStartTime;
            if (elapsed >= this.hoverDelay.getInput()) {
                return false;
            }
        } else {
            this.miningStartTime = -1L;
        }

        return true;
    }
}
