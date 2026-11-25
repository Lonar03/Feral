package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;

public class NullMove extends Module {
    private boolean prevW;
    private boolean prevS;
    private boolean prevA;
    private boolean prevD;
    private int lastForwardSign;
    private int lastStrafeSign;

    public NullMove() {
        super("Null Move", Module.category.movement, 0);
    }

    @Override
    public void onDisable() {
        this.prevW = false;
        this.prevS = false;
        this.prevA = false;
        this.prevD = false;
        this.lastForwardSign = 0;
        this.lastStrafeSign = 0;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (this.isEnabled() && Utils.nullCheck()) {
            if (mc.screen == null) {
                boolean w = mc.options.forwardKey.isPressed();
                boolean s = mc.options.backKey.isPressed();
                boolean a = mc.options.leftKey.isPressed();
                boolean d = mc.options.rightKey.isPressed();
                if (w && !this.prevW) {
                    this.lastForwardSign = 1;
                }

                if (s && !this.prevS) {
                    this.lastForwardSign = -1;
                }

                if (a && !this.prevA) {
                    this.lastStrafeSign = 1;
                }

                if (d && !this.prevD) {
                    this.lastStrafeSign = -1;
                }

                if (w && s) {
                    e.setForward(this.lastForwardSign >= 0 ? 1.0F : -1.0F);
                }

                if (a && d) {
                    e.setStrafe(this.lastStrafeSign >= 0 ? 1.0F : -1.0F);
                }

                this.prevW = w;
                this.prevS = s;
                this.prevA = a;
                this.prevD = d;
            }
        }
    }
}
