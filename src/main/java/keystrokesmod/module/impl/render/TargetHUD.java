package keystrokesmod.module.impl.render;

import java.awt.Color;
import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.ClickMouseEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.combat.AimAssist;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.effects.Animation;
import keystrokesmod.utility.shader.effects.Render3D;
import keystrokesmod.utility.shader.effects.TargetHudRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;

public class TargetHUD extends Module {
    private SliderSetting mode;
    private SliderSetting theme;
    private ButtonSetting renderEsp;
    private ButtonSetting showDifference;
    private ButtonSetting showHealth;
    private ButtonSetting showStatus;
    private ButtonSetting healthColor;
    private ButtonSetting manual;
    private ButtonSetting aimAssist;
    private ButtonSetting killAura;
    private Animation fadeTimer;
    private LivingEntity target;
    private LivingEntity manualClickTarget;
    private long lastAliveMS;
    private String hudName;
    private float hudHealth;
    private double hudRatio;
    public int posX = 70;
    public int posY = 40;
    private String[] modes = new String[] {"Modern", "Legacy"};

    public TargetHUD() {
        super("TargetHUD", Module.category.render);
        this.liteModule = true;
        this.registerSetting(this.mode = new SliderSetting("Mode", true, 0, this.modes));
        this.registerSetting(this.theme = new SliderSetting("Theme", 0, Theme.THEMES_SETTING));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new TargetHUD.EditScreen())));
        this.registerSetting(this.renderEsp = new ButtonSetting("Render ESP", true));
        this.registerSetting(this.showDifference = new ButtonSetting("Show difference", true));
        this.registerSetting(this.showHealth = new ButtonSetting("Show health", true));
        this.registerSetting(this.showStatus = new ButtonSetting("Show win or loss", true));
        this.registerSetting(this.healthColor = new ButtonSetting("Traditional health color", false));
        this.registerSetting(new DescriptionSetting("Targets"));
        this.registerSetting(this.manual = new ButtonSetting("Manual", false));
        this.registerSetting(this.aimAssist = new ButtonSetting("AimAssist", false));
        this.registerSetting(this.killAura = new ButtonSetting("KillAura", true));
    }

    @Override
    public void onDisable() {
        this.reset();
        TargetHudRenderer.reset();
    }

    @ForgeEvents.SubscribeEvent
    public void onClickMouse(ClickMouseEvent event) {
        this.manualClickTarget = null;
        if (this.manual.isToggled() && Utils.nullCheck() && mc.crosshairTarget != null) {
            Entity clickedEntity = mc.crosshairTarget.entity;
            if (clickedEntity instanceof LivingEntity) {
                if (ModuleManager.killAura == null
                        || !ModuleManager.killAura.isEnabled()
                        || KillAura.attackingEntity != clickedEntity) {
                    this.manualClickTarget = (LivingEntity) clickedEntity;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onAttack(AttackEvent event) {
        if (this.manualClickTarget != null) {
            if (event.attacker == mc.player && event.target == this.manualClickTarget) {
                this.activateTarget(this.manualClickTarget);
            }

            this.manualClickTarget = null;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (!Utils.nullCheck()) {
                this.reset();
            } else {
                LivingEntity activeTarget = this.getActiveTarget();
                if (activeTarget != null) {
                    this.activateTarget(activeTarget);
                }

                if (this.target != null) {
                    long age = System.currentTimeMillis() - this.lastAliveMS;
                    if (age >= 650L) {
                        this.fadeTimer = null;
                        this.target = null;
                    } else {
                        if (age >= 250L && this.fadeTimer == null) {
                            this.fadeTimer = new Animation(400.0F);
                            this.fadeTimer.start();
                        }

                        if (!mc.options.debugEnabled
                                && (mc.screen == null
                                        || !Settings.setChatAsInventory.isToggled()
                                                && mc.screen instanceof ChatScreen)) {
                            LivingEntity dataTarget = this.killAura.isToggled()
                                            && ModuleManager.killAura.isEnabled()
                                            && KillAura.target != null
                                    ? KillAura.target
                                    : activeTarget;
                            if (dataTarget != null) {
                                this.hudName = dataTarget.getDisplayName().getString();
                                this.hudHealth = mc.world.getEntity(dataTarget.getNetworkId()) != null
                                        ? Utils.getTotalHealth(dataTarget)
                                        : 0.0F;
                                this.hudRatio = Math.min(this.hudHealth / dataTarget.getMaxHealth(), 1.0F);
                            }

                            this.drawTargetHUD(this.fadeTimer, this.hudName, this.hudHealth, this.hudRatio);
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent renderWorldLastEvent) {
        if (this.renderEsp.isToggled() && Utils.nullCheck()) {
            LivingEntity hudTarget = this.getEspTarget();
            if (hudTarget != null && !hudTarget.removed) {
                if (!ModuleManager.backtrack.isRenderingServerPositionFor(hudTarget)) {
                    this.drawTargetBox(
                            hudTarget,
                            renderWorldLastEvent.partialTicks,
                            Theme.getGradient((int) this.theme.getInput(), 0.0));
                }
            }
        }
    }

    private LivingEntity getEspTarget() {
        if (this.killAura.isToggled() && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            return KillAura.target;
        }

        LivingEntity activeTarget = this.getActiveTarget();
        return activeTarget != null ? activeTarget : (this.manual.isToggled() ? this.target : null);
    }

    private void drawTargetBox(LivingEntity entity, float partialTicks, int color) {
        Render3D.entityBox(entity, 2, color, partialTicks);
    }

    private LivingEntity getActiveTarget() {
        if (this.killAura.isToggled() && ModuleManager.killAura != null) {
            LivingEntity auraTarget = ModuleManager.killAura.getRenderTarget();
            if (auraTarget != null) {
                return auraTarget;
            }
        }

        if (this.aimAssist.isToggled()) {
            Module aimAssistModule = ModuleManager.getModule(AimAssist.class);
            if (aimAssistModule instanceof AimAssist && aimAssistModule.isEnabled()) {
                Entity aimAssistTarget = ((AimAssist) aimAssistModule).getAimAssistTarget();
                if (aimAssistTarget instanceof LivingEntity) {
                    return (LivingEntity) aimAssistTarget;
                }
            }
        }

        return null;
    }

    private void activateTarget(LivingEntity activeTarget) {
        this.target = activeTarget;
        this.lastAliveMS = System.currentTimeMillis();
        this.fadeTimer = null;
        this.hudName = activeTarget.getDisplayName().getString();
        this.hudHealth = Utils.getTotalHealth(activeTarget);
        this.hudRatio = Math.min(this.hudHealth / activeTarget.getMaxHealth(), 1.0F);
    }

    private void bindRenderer() {
        RenderUtils.updateResolution();
        TargetHudRenderer.mode =
                this.mode.getInput() == 1.0 ? TargetHudRenderer.Mode.Legacy : TargetHudRenderer.Mode.Modern;
        TargetHudRenderer.theme = Theme.values()[(int) this.theme.getInput()];
        TargetHudRenderer.offsetX = this.posX;
        TargetHudRenderer.offsetY = this.posY;
        TargetHudRenderer.showHealth = this.showHealth;
        TargetHudRenderer.showDifference = this.showDifference;
        TargetHudRenderer.showWinLoss = this.showStatus;
        TargetHudRenderer.traditionalHealthColor = this.healthColor;
    }

    private String getTargetText(String name, float health, double ratio) {
        this.bindRenderer();
        return TargetHudRenderer.text(name, health, ratio);
    }

    private void drawTargetHUD(Animation fade, String name, float health, double ratio) {
        if (this.mode.getInput() != -1.0) {
            this.bindRenderer();
            TargetHudRenderer.render(fade, name, health, ratio);
        }
    }

    private void reset() {
        this.fadeTimer = null;
        this.target = null;
        this.manualClickTarget = null;
        this.hudName = null;
        this.hudHealth = 0.0F;
        this.hudRatio = 0.0;
    }

    public boolean isEspActiveFor(LivingEntity entity) {
        return this.isEnabled() && this.renderEsp.isToggled() && entity != null && this.getEspTarget() == entity;
    }

    public Color getCurrentEspColor(int alpha) {
        int color = Theme.getGradient((int) this.theme.getInput(), 0.0);
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return new Color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, safeAlpha);
    }

    class EditScreen extends Screen {
        ButtonWidget resetPosition;
        boolean d = false;
        int miX = 0;
        int miY = 0;
        int maX = 0;
        int maY = 0;
        int aX = 70;
        int aY = 40;
        int laX = 0;
        int laY = 0;
        int lmX = 0;
        int lmY = 0;
        int clickMinX = 0;

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetPosition =
                            new ButtonWidget(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            this.aX = TargetHUD.this.posX;
            this.aY = TargetHUD.this.posY;
        }

        public void render(int mX, int mY, float pt) {
            Window res = new Window(this.minecraft);
            fill(0, 0, this.width, this.height, -1308622848);
            int miX = this.aX;
            int miY = this.aY;
            String playerInfo = this.minecraft.player.getDisplayName().getString();
            double health = Math.min(this.minecraft.player.getHealth() / this.minecraft.player.getMaxHealth(), 1.0F);
            TargetHUD.this.posX = miX;
            TargetHUD.this.posY = miY;
            TargetHUD.this.drawTargetHUD(null, playerInfo, this.minecraft.player.getHealth(), health);
            int stringWidth = this.minecraft.textRenderer.getWidth(
                            TargetHUD.this.getTargetText(playerInfo, this.minecraft.player.getHealth(), health))
                    + 8;
            int maX = res.getWidth() / 2 - stringWidth / 2 + miX + stringWidth;
            int maY = res.getHeight() / 2 + miY + this.minecraft.textRenderer.fontHeight - 1 + 8 + 13;
            int minX = res.getWidth() / 2 - stringWidth / 2 + miX - 8;
            int minY = res.getHeight() / 2 + miY - 8;
            this.miX = minX;
            this.miY = minY;
            this.maX = maX;
            this.maY = maY;
            this.clickMinX = minX;
            TargetHUD.this.posX = miX;
            TargetHUD.this.posY = miY;
            String edit = "Edit the HUD position by dragging.";
            int x = res.getWidth() / 2 - this.textRenderer.getWidth(edit) / 2;
            int y = res.getHeight() / 2 - 20;
            RenderUtils.drawColoredString(edit, '-', x, y, 2L, 0L, true, this.minecraft.textRenderer);
            ClientPlatform.handleInput(this);
            super.render(mX, mY, pt);
        }

        protected void mouseDragged(int mX, int mY, int b, long t) {
            super.mouseDragged(mX, mY, b, t);
            if (b == 0) {
                if (this.d) {
                    this.aX = this.laX + (mX - this.lmX);
                    this.aY = this.laY + (mY - this.lmY);
                } else if (mX > this.clickMinX && mX < this.maX && mY > this.miY && mY < this.maY) {
                    this.d = true;
                    this.lmX = mX;
                    this.lmY = mY;
                    this.laX = this.aX;
                    this.laY = this.aY;
                }
            }
        }

        protected void mouseReleased(int mX, int mY, int s) {
            super.mouseReleased(mX, mY, s);
            if (s == 0) {
                this.d = false;
            }
        }

        public void buttonClicked(ButtonWidget b) {
            if (b == this.resetPosition) {
                this.aX = TargetHUD.this.posX = 70;
                this.aY = TargetHUD.this.posY = 40;
            }
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }
}
