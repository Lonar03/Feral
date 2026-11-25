package keystrokesmod.module.impl.network;

import java.awt.Color;
import java.util.Set;
import keystrokesmod.Feral;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.util.math.Vec3d;

public class Blink extends Module {
    private static final String[] MODE_LABELS = new String[] {"Inbound", "Outbound", "Both"};
    private SliderSetting mode;
    private SliderSetting disableAfterMs;
    private SliderSetting releasePacketEvery;
    private ButtonSetting maxDuration;
    private ButtonSetting disableOnAttack;
    private ButtonSetting initialPosition;
    private Vec3d pos;
    private int color = new Color(0, 255, 0, 120).getRGB();
    private int blinkTicks;
    private long enableTime;

    public Blink() {
        super("Blink", Module.category.network);
        this.registerSetting(this.mode = new SliderSetting("Mode", 1, MODE_LABELS));
        this.registerSetting(this.maxDuration = new ButtonSetting("Max duration", false));
        this.registerSetting(
                this.disableAfterMs = new SliderSetting("Disable after", "ms", 500.0, 50.0, 20000.0, 50.0));
        this.registerSetting(
                this.releasePacketEvery =
                        new SliderSetting("Release packet every", " tick", true, -1.0, 1.0, 20.0, 1.0));
        this.registerSetting(new DescriptionSetting("Disable on"));
        this.registerSetting(this.disableOnAttack = new ButtonSetting("Attack", false));
        this.registerSetting(this.initialPosition = new ButtonSetting("Show initial position", true));
    }

    @Override
    public void guiUpdate() {
        this.disableAfterMs.setVisible(this.maxDuration.isToggled(), this);
    }

    @Override
    public void onEnable() {
        this.pos = new Vec3d(mc.player.x, mc.player.y, mc.player.z);
        this.blinkTicks = 0;
        this.enableTime = System.currentTimeMillis();
        Feral.lagHandler.requestLag(new LagRequest(this.lagDirectionsForMode(), new ModuleBackedTimeout(this)));
    }

    private Set<EnumLagDirection> lagDirectionsForMode() {
        switch ((int) this.mode.getInput()) {
            case 0:
                return EnumLagDirection.ONLY_INBOUND;
            case 1:
            default:
                return EnumLagDirection.ONLY_OUTBOUND;
            case 2:
                return EnumLagDirection.BIDIRECTIONAL;
        }
    }

    public boolean delaysInboundPackets() {
        int m = (int) this.mode.getInput();
        return m == 0 || m == 2;
    }

    @Override
    public String getInfo() {
        return String.valueOf(this.blinkTicks);
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        this.blinkTicks++;
        if (this.maxDuration.isToggled()) {
            long elapsed = System.currentTimeMillis() - this.enableTime;
            if (elapsed >= (int) this.disableAfterMs.getInput()) {
                this.disable();
                return;
            }
        }

        int releaseInterval = (int) this.releasePacketEvery.getInput();
        if (releaseInterval > 0 && this.blinkTicks % releaseInterval == 0) {
            Set<EnumLagDirection> directions = this.lagDirectionsForMode();
            if (directions.contains(EnumLagDirection.INBOUND)) {
                Feral.lagHandler.releaseNextPacket(EnumLagDirection.INBOUND);
            }

            if (directions.contains(EnumLagDirection.OUTBOUND)) {
                Feral.lagHandler.releaseNextPacket(EnumLagDirection.OUTBOUND);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onAttackEntity(ForgeEvents.AttackEntityEvent event) {
        if (this.isEnabled() && this.disableOnAttack.isToggled() && Utils.nullCheck()) {
            if (event.entityPlayer == mc.player) {
                this.disable();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.pos != null && this.initialPosition.isToggled()) {
            RenderUtils.drawPlayerBoundingBox(this.pos, this.color);
        }
    }
}
