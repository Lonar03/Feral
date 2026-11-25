package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;

public class Holdlook extends Module {
    private final KeySetting rearCamKey;
    private final KeySetting frontCamKey;
    private boolean rearActive;
    private boolean frontActive;
    private int savedPerspective;

    public Holdlook() {
        super("Hold Look", Module.category.render);
        this.registerSetting(this.rearCamKey = new KeySetting("Rear cam", 0));
        this.registerSetting(this.frontCamKey = new KeySetting("Front cam", 0));
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.START && Utils.nullCheck()) {
            if (mc.screen != null) {
                if (this.rearActive || this.frontActive) {
                    this.applyThirdPersonView(0);
                    this.rearActive = false;
                    this.frontActive = false;
                }
            } else {
                boolean rearDown = this.rearCamKey.isPressed();
                boolean frontDown = this.frontCamKey.isPressed();
                if (rearDown && !this.rearActive) {
                    this.savedPerspective = mc.options.perspective;
                    this.applyThirdPersonView(1);
                    this.rearActive = true;
                } else if (!rearDown && this.rearActive) {
                    this.applyThirdPersonView(this.frontActive ? 2 : 0);
                    this.rearActive = false;
                }

                if (frontDown && !this.frontActive) {
                    if (!this.rearActive) {
                        this.savedPerspective = mc.options.perspective;
                    }

                    this.applyThirdPersonView(2);
                    this.frontActive = true;
                } else if (!frontDown && this.frontActive) {
                    this.applyThirdPersonView(this.rearActive ? 1 : 0);
                    this.frontActive = false;
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.rearActive || this.frontActive) {
            this.applyThirdPersonView(0);
            this.rearActive = false;
            this.frontActive = false;
        }
    }

    private void applyThirdPersonView(int view) {
        if (view < 0) {
            view = 0;
        } else if (view > 2) {
            view = 2;
        }

        mc.options.perspective = view;
        if (mc.gameRenderer != null) {
            if (view == 0) {
                mc.gameRenderer.updateShader(mc.getCamera());
            } else if (view == 1) {
                mc.gameRenderer.updateShader((Entity) null);
            }
        }

        if (mc.worldRenderer != null) {
            mc.worldRenderer.onViewChanged();
        }
    }
}
