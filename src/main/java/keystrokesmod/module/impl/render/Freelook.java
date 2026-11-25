package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorMouseHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class Freelook extends Module {
    public static boolean perspectiveToggled;
    public static float cameraYaw;
    public static float cameraPitch;
    private ButtonSetting hold;
    private ButtonSetting invertPitch;
    private ButtonSetting lockPitch;
    private ButtonSetting customFov;
    private SliderSetting fov;
    private KeySetting freelookKey;
    private boolean prevKeyState;
    private int previousPerspective;
    private float lastFov;

    public Freelook() {
        super("Free Look", Module.category.render);
        this.registerSetting(this.freelookKey = new KeySetting("Key", 56));
        this.registerSetting(this.hold = new ButtonSetting("Hold", true));
        this.registerSetting(this.invertPitch = new ButtonSetting("Invert pitch", false));
        this.registerSetting(this.lockPitch = new ButtonSetting("Lock pitch", true));
        this.registerSetting(this.customFov = new ButtonSetting("Custom FOV", false));
        this.registerSetting(this.fov = new SliderSetting("FOV", 90.0, 10.0, 150.0, 1.0));
    }

    @Override
    public void guiUpdate() {
        this.fov.setVisible(this.customFov.isToggled(), this);
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && mc.screen == null && Utils.nullCheck()) {
            Module freecamMod = ModuleManager.getModule(Freecam.class);
            if (!(freecamMod instanceof Freecam) || Freecam.freeEntity == null) {
                boolean down = this.freelookKey.isPressed();
                if (down != this.prevKeyState) {
                    this.onPressed(down);
                    this.prevKeyState = down;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGuiOpen(ForgeEvents.GuiOpenEvent event) {
        if (event.gui != null && perspectiveToggled && this.hold.isToggled()) {
            this.resetPerspective();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldLoad(ForgeEvents.WorldEvent.Load e) {
        if (perspectiveToggled) {
            this.resetPerspective();
        }
    }

    private void onPressed(boolean state) {
        if (!this.isEnabled()) {
            if (perspectiveToggled) {
                this.resetPerspective();
            }
        } else {
            if (state) {
                cameraYaw = mc.player.yaw;
                cameraPitch = mc.player.pitch;
                if (perspectiveToggled) {
                    this.resetPerspective();
                } else {
                    this.enterPerspective();
                }
            } else if (this.hold.isToggled()) {
                this.resetPerspective();
            }
        }
    }

    private void enterPerspective() {
        perspectiveToggled = true;
        this.previousPerspective = mc.options.perspective;
        this.applyThirdPersonView(1);
        this.lastFov = mc.options.fov;
    }

    public void resetPerspective() {
        perspectiveToggled = false;
        this.applyThirdPersonView(this.previousPerspective);
        if (mc.screen == null && mc.focused && !Mouse.isGrabbed()) {
            mc.mouse.lock();
        }

        if (this.hold.isToggled() || mc.options.fov == this.lastFov || this.customFov.isToggled()) {
            mc.options.fov = this.lastFov;
        }
    }

    public static boolean overrideMouse(Minecraft mc) {
        if (!mc.focused) {
            return false;
        }

        if (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && perspectiveToggled) {
            if (Minecraft.IS_MAC && Display.isActive() && !Mouse.isInsideWindow()) {
                Mouse.setGrabbed(false);
                Mouse.setCursorPosition(mc.width / 2, mc.height / 2 - 20);
                Mouse.setGrabbed(true);
            }

            mc.mouse.tick();
            float sens = mc.options.mouseSensitivity * 0.6F + 0.2F;
            float mult = sens * sens * sens * 8.0F;
            Freelook fl = ModuleManager.freelook;
            if (fl != null) {
                int dx = ((IAccessorMouseHelper) mc.mouse).getDeltaX();
                int dy = ((IAccessorMouseHelper) mc.mouse).getDeltaY();
                float fdx = dx * mult;
                float fdy = dy * mult;
                cameraYaw += fdx * 0.15F;
                if (fl.invertPitch.isToggled()) {
                    fdy = -fdy;
                }

                cameraPitch += fdy * 0.15F;
                if (fl.lockPitch.isToggled()) {
                    cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
                }

                if (fl.customFov.isToggled()) {
                    mc.options.fov = (float) fl.fov.getInput();
                }
            }

            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onDisable() {
        if (perspectiveToggled) {
            perspectiveToggled = false;
            this.applyThirdPersonView(0);
            if (mc.screen == null && mc.focused && !Mouse.isGrabbed()) {
                mc.mouse.lock();
            }

            mc.options.fov = this.lastFov;
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
