package keystrokesmod.clickgui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.clickgui.components.impl.BindComponent;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.clickgui.components.impl.SliderComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.CommandLine;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.CommandHandler;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.shader.BlurUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ClickGui extends Screen {
    private Timer logoSmoothWidth;
    private Timer smoothEntity;
    private Timer backgroundFade;
    private long openedAt;
    private Window sr;
    private ButtonWidget commandLineSend;
    private TextFieldWidget commandLineInput;
    public static ArrayList<CategoryComponent> categories;
    private int actualScreenWidth;
    private int actualScreenHeight;
    private double previousScale;
    private static boolean isNotFirstOpen;
    private boolean pendingScaleRefresh;
    private SliderSetting heldArrowSlider;
    private int heldArrowDirection;
    private long arrowHoldStartedAt;
    private long lastArrowAdjustmentAt;
    private static final long ARROW_HOLD_DELAY_MS = 300L;
    private static final double ARROW_INITIAL_REPEAT_MS = 180.0;
    private static final double ARROW_ACCELERATION_HALF_LIFE_MS = 750.0;
    private static final long ARROW_MIN_REPEAT_MS = 20L;

    public ClickGui() {
        categories = new ArrayList<>();
        int y = 5;
        Module.category[] values;
        int length = (values = Module.category.values()).length;

        for (int i = 0; i < length; i++) {
            Module.category c = values[i];
            CategoryComponent categoryComponent = new CategoryComponent(c);
            categoryComponent.setY(y, false);
            categories.add(categoryComponent);
            y += 20;
        }
    }

    public void initMain() {
        this.openedAt = System.currentTimeMillis();
        (this.logoSmoothWidth = this.smoothEntity = this.backgroundFade = new Timer(500.0F)).start();
    }

    public void resetPositions() {
        int y = 5;

        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.applySavedState(5.0F, y, false, false);
            y += 20;
        }
    }

    public void init() {
        super.init();
        if (this.backgroundFade == null) {
            this.initMain();
        }

        double configuredScale = this.getConfiguredGuiScale();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = configuredScale;
        }

        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
        }

        if (Double.compare(this.previousScale, configuredScale) != 0) {
            for (CategoryComponent categoryComponent : categories) {
                categoryComponent.limitPositions();
            }
        }

        this.reloadModulesForCurrentMode();
        (this.commandLineInput = new TextFieldWidget(1, this.minecraft.textRenderer, 22, this.height - 100, 150, 20))
                .setMaxLength(256);
        this.buttons.add(this.commandLineSend = new ButtonWidget(2, 22, this.height - 70, 150, 20, "Send"));
        this.commandLineSend.visible = CommandLine.opened;
        this.previousScale = configuredScale;
    }

    public void reloadModulesForCurrentMode() {
        for (CategoryComponent categoryComponent : categories) {
            if (categoryComponent.category == Module.category.profiles) {
                categoryComponent.reloadModules(true);
            } else if (categoryComponent.category == Module.category.scripts) {
                categoryComponent.reloadModules(false);
            } else {
                categoryComponent.reloadModules();
            }
        }
    }

    private List<CategoryComponent> getCategoriesInRenderOrder() {
        List<CategoryComponent> renderOrder = new ArrayList<>(categories);
        renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
        return renderOrder;
    }

    private CategoryComponent getTopmostUnderCursor(List<CategoryComponent> renderOrder, int x, int y) {
        for (int i = renderOrder.size() - 1; i >= 0; i--) {
            if (renderOrder.get(i).overRect(x, y)) {
                return renderOrder.get(i);
            }
        }

        return null;
    }

    public void render(int x, int y, float p) {
        if (this.pendingScaleRefresh) {
            this.pendingScaleRefresh = false;
            this.refreshLayoutForConfiguredScale();
        }

        int logicalMouseX = this.toLogicalCoordinate(x);
        int logicalMouseY = this.toLogicalCoordinate(y);
        int backgroundAlpha = (int) (this.backgroundFade.getValueFloat(0.0F, 0.75F, 2) * 255.0F);
        if (Gui.darkBackground.isToggled()) {
            fill(0, 0, this.actualScreenWidth, this.actualScreenHeight, 1052688 | backgroundAlpha << 24);
        }

        if (Gui.backgroundBlur.getInput() > 0.0) {
            BlurUtils.prepareBlur();
            fill(0, 0, this.actualScreenWidth, this.actualScreenHeight, -1);
            float strength = (float) (3.4 * Gui.backgroundBlur.getInput() / 100.0);
            BlurUtils.blurEnd(2, strength * backgroundAlpha / 191.0F);
        }

        GlStateManager.pushMatrix();
        GlStateManager.scaled(this.getRenderScale(), this.getRenderScale(), 1.0);
        if (!Gui.hideWatermark.isToggled()) {
            this.drawTitle(this.width / 2.0F, this.height / 4.0F);
        }

        this.renderClientInfo(this.height + 8 - this.logoSmoothWidth.getValueInt(0, 30, 2), this.textRenderer);
        List<CategoryComponent> renderOrder = this.getCategoriesInRenderOrder();
        CategoryComponent topmostUnderCursor = this.getTopmostUnderCursor(renderOrder, logicalMouseX, logicalMouseY);

        for (CategoryComponent c : renderOrder) {
            c.render(this.textRenderer);
            c.mousePosition(logicalMouseX, logicalMouseY, c == topmostUnderCursor);

            for (Component m : c.getModules()) {
                m.drawScreen(logicalMouseX, logicalMouseY);
            }
        }

        this.updateHeldSliderAdjustment(logicalMouseX, logicalMouseY);
        GL11.glColor3f(1.0F, 1.0F, 1.0F);
        if (!Gui.hidePlayerModel.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableBlend();
            SurvivalInventoryScreen.renderEntity(
                    this.width + 15 - this.smoothEntity.getValueInt(0, 40, 2),
                    this.height - 10,
                    40,
                    this.width - 25 - logicalMouseX,
                    this.height - 50 - logicalMouseY,
                    this.minecraft.player);
            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }

        if (CommandLine.opened) {
            if (!this.commandLineSend.visible) {
                this.commandLineSend.visible = true;
            }

            int r = CommandLine.animate.isToggled() ? CommandLine.animation.getValueInt(0, 200, 2) : 200;
            if (CommandLine.closed) {
                r = 200 - r;
                if (r == 0) {
                    CommandLine.closed = false;
                    CommandLine.opened = false;
                    this.commandLineSend.visible = false;
                }
            }

            fill(0, 0, r, this.height, -1089466352);
            this.drawHorizontalLine(0, r - 1, this.height - 345, -1);
            this.drawHorizontalLine(0, r - 1, this.height - 115, -1);
            fill(r - 1, 0, r, this.height, -1);
            CommandHandler.renderCommandOutput(this.textRenderer, this.height, r, this.sr.getScale());
            int x2 = r - 178;
            this.commandLineInput.x = x2;
            this.commandLineSend.x = x2;
            this.commandLineInput.render();
            super.render(logicalMouseX, logicalMouseY, p);
        } else if (CommandLine.closed) {
            CommandLine.closed = false;
        }

        GlStateManager.popMatrix();
    }

    public void drawTitle(float cx, float cy) {
        long now = System.currentTimeMillis();
        float since = (float) (now - this.openedAt);
        String title = "feral";
        String tag = "";
        float intro = this.clamp(since / 950.0F);
        float base = 2.5F;
        float pulse = 1.0F + (float) Math.sin(now * 0.0017) * 0.012F;
        float scale = base * pulse;
        float spacing = 0.8F;
        float width = 0.0F;

        for (int i = 0; i < title.length(); i++) {
            width += (this.textRenderer.getWidth(title.charAt(i)) + spacing) * scale;
        }

        float var31;
        float startX = cx - (var31 = width - spacing * scale) / 2.0F;
        float bob = (float) Math.sin(now * 0.0021) * 1.2F;
        GL11.glEnable(3042);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        this.drawSparkles(cx, cy + 3.0F, var31 * 0.58F, 20.0F, intro, now);
        float px = startX;

        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            float cw = this.textRenderer.getWidth(c);
            float t = this.clamp((since - i * 70.0F) / 760.0F);
            if (t <= 0.0F) {
                px += (cw + spacing) * scale;
            } else {
                float e = this.easeOutBack(t);
                float ang = -1.3F + i * 0.48F;
                float dist = (1.0F - e) * (36.0F + i * 3.5F);
                float bx = px + cw * scale / 2.0F;
                float by = cy + bob;
                float lx = bx + (float) Math.cos(ang) * dist;
                float ly = by + (float) Math.sin(ang) * dist * 0.55F + (1.0F - e) * 14.0F;
                float rot = (1.0F - e) * ((i & 1) == 0 ? -86.0F : 86.0F);
                float s = scale * (0.72F + 0.28F * e);
                int color = RenderUtils.rainbow(2L, -i * 320L);
                this.drawGlowText(String.valueOf(c), lx, ly, s, rot, color, (int) (255.0F * t));
                px += (cw + spacing) * scale;
            }
        }

        float tagT = this.clamp((since - 420.0F) / 850.0F);
        if (tagT > 0.0F && !tag.isEmpty()) {
            float e = this.easeOutCubic(tagT);
            float s = 1.05F * (0.82F + 0.18F * e);
            float tw = this.textRenderer.getWidth(tag) * s;
            float sway = (float) Math.sin(now * 0.00115) * 2.2F;
            float tbob = (float) Math.sin(now * 0.0021) * 0.9F;
            float phase = (float) (now % 4200L) / 4200.0F;
            float rock = (float) Math.sin(phase * Math.PI * 2.0) * 7.0F;
            float tx = cx + var31 * 0.28F + sway + 6.0F;
            float ty = cy + 17.0F + (1.0F - e) * 10.0F + tbob;
            float rot = (1.0F - e) * 8.0F + rock;
            this.drawGlowText(tag, tx + tw / 2.0F, ty, s, rot, RenderUtils.rainbow(2L, -1700L), (int) (235.0F * tagT));
        }

        RenderUtils.resetColor();
        GL11.glDisable(3042);
    }

    public void drawSparkles(float cx, float cy, float rx, float ry, float intro, long now) {
        char[] chars = new char[] {'.', '+', 'x', '*', '.', '+'};

        for (int i = 0; i < chars.length; i++) {
            boolean even = (i & 1) == 0;
            double ang = i * ((Math.PI * 2) / chars.length) + (even ? 0.0 : Math.PI / chars.length);
            double speed = even ? 4.2E-4 : -3.1E-4;
            double a = ang + now * speed;
            float ex = rx * (even ? 1.02F : 0.78F);
            float ey = ry * (even ? 1.0F : 0.72F);
            float sx = cx + (float) Math.cos(a) * ex;
            float sy = cy + (float) Math.sin(a) * ey;
            float twinkle = 0.65F + 0.35F * (float) Math.sin(now * 0.0042 + i * 1.37);
            int alpha = (int) (120.0F * intro * twinkle);
            if (alpha > 3) {
                alpha = RenderUtils.clampAlpha(alpha);
                int color = RenderUtils.withAlpha(RenderUtils.rainbow(2L, i * 430L), alpha);
                char c = chars[i];
                float rot = 0.0F;
                float scale;
                switch (c) {
                    case '+':
                        scale = 0.72F;
                        rot = (float) ((now * 0.045 + i * 37) % 360.0);
                        break;
                    case '.':
                        scale = 0.85F;
                        break;
                    case 'x':
                        scale = 0.72F;
                        rot = (float) ((now * 0.04 + i * 53) % 360.0);
                        break;
                    default:
                        scale = 0.78F;
                        rot = (float) ((now * 0.03 + i * 29) % 360.0);
                }

                GlStateManager.pushMatrix();
                GlStateManager.translatef(sx, sy, 0.0F);
                if (rot != 0.0F) {
                    GlStateManager.rotatef(rot, 0.0F, 0.0F, 1.0F);
                }

                GlStateManager.scalef(scale, scale, 1.0F);
                String s = String.valueOf(c);
                this.textRenderer.draw(
                        s, -this.textRenderer.getWidth(s) / 2.0F, -this.textRenderer.fontHeight / 2.0F, color, true);
                GlStateManager.popMatrix();
            }
        }
    }

    public void drawGlowText(String s, float x, float y, float scale, float rot, int color, int alpha) {
        if (alpha > 3) {
            alpha = RenderUtils.clampAlpha(alpha);
            int w = this.textRenderer.getWidth(s);
            int h = this.textRenderer.fontHeight;
            float ox = -w / 2.0F;
            float oy = -h / 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translatef(x, y, 0.0F);
            GlStateManager.rotatef(rot, 0.0F, 0.0F, 1.0F);
            GlStateManager.scalef(scale, scale, 1.0F);

            for (int i = 2; i >= 1; i--) {
                float gs = 1.0F + i * 0.055F;
                int ga = (int) (alpha * (0.1F + i * 0.035F));
                if (ga >= 4) {
                    ga = RenderUtils.clampAlpha(ga);
                    GlStateManager.pushMatrix();
                    GlStateManager.scalef(gs, gs, 1.0F);
                    this.textRenderer.draw(s, ox, oy, RenderUtils.withAlpha(color, ga), false);
                    GlStateManager.popMatrix();
                }
            }

            this.textRenderer.draw(s, ox, oy, RenderUtils.withAlpha(color, alpha), true);
            GlStateManager.popMatrix();
        }
    }

    public float easeOutCubic(float f) {
        float t = 1.0F - f;
        return 1.0F - t * t * t;
    }

    public float clamp(float f) {
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    public float easeOutBack(float f) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float t = f - 1.0F;
        return 1.0F + c3 * t * t * t + c1 * t * t;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));
        CategoryComponent topmostCategory = null;

        for (CategoryComponent category : inputOrder) {
            if (category.overRect(mouseX, mouseY)) {
                topmostCategory = category;
                break;
            }
        }

        if (topmostCategory != null) {
            topmostCategory.markInteracted();
        }

        if (mouseButton == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
            }

            if (topmostCategory != null && topmostCategory.draggable(mouseX, mouseY)) {
                topmostCategory.overTitle(true);
                topmostCategory.xx = mouseX - topmostCategory.getX();
                topmostCategory.yy = mouseY - topmostCategory.getY();
                topmostCategory.dragging = true;
            }
        }

        if (mouseButton == 1 && topmostCategory != null && topmostCategory.overTitle(mouseX, mouseY)) {
            topmostCategory.mouseClicked(!topmostCategory.isOpened());
        }

        if (topmostCategory != null
                && topmostCategory.isOpened()
                && !topmostCategory.getModules().isEmpty()
                && !topmostCategory.overTitle(mouseX, mouseY)) {
            for (ModuleComponent component : topmostCategory.getModules()) {
                if (component.onClick(mouseX, mouseY, mouseButton)) {
                    break;
                }
            }
        }

        if (CommandLine.opened) {
            this.commandLineInput.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0 || mouseButton == 1) {
            FocusableTextComponent focusedComponent = this.findFocusedTextComponentAt(mouseX, mouseY);
            this.enforceSingleFocusedTextInput(focusedComponent);
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (button == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.mouseReleased(x, y, button);
                    }
                }
            }
        }

        if (this.pendingScaleRefresh) {
            this.pendingScaleRefresh = false;
            this.refreshLayoutForConfiguredScale();
        }
    }

    public void handleMouse() {
        super.handleMouse();
        int wheelInput = Mouse.getDWheel();
        if (wheelInput != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.minecraft.width;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;

            for (CategoryComponent category : categories) {
                category.onScroll(wheelInput, mouseX, mouseY);
            }
        }
    }

    public void refreshAfterProfileLoad() {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
        }

        this.reloadModulesForCurrentMode();
        this.refreshLayoutForConfiguredScale();
    }

    public void init(
            Minecraft p_setWorldAndResolution_1_, int p_setWorldAndResolution_2_, int p_setWorldAndResolution_3_) {
        this.minecraft = p_setWorldAndResolution_1_;
        this.itemRenderer = p_setWorldAndResolution_1_.getItemRenderer();
        this.textRenderer = p_setWorldAndResolution_1_.textRenderer;
        this.refreshScaledResolution();
        if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(
                new ForgeEvents.GuiScreenEvent.InitGuiEvent.Pre(this, this.buttons))) {
            this.buttons.clear();
            this.init();
        }

        ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.GuiScreenEvent.InitGuiEvent.Post(this, this.buttons));
    }

    public void keyPressed(char t, int k) {
        FocusableTextComponent activeTextInput = this.getActiveFocusedTextInput();
        if (k == 1) {
            if (activeTextInput != null) {
                activeTextInput.unfocusTextInput();
                return;
            }

            if (!this.binding()) {
                this.minecraft.openScreen(null);
                return;
            }
        }

        if (activeTextInput != null) {
            for (CategoryComponent category : categories) {
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.keyTyped(t, k);
                    }
                }
            }
        } else if (!this.binding() && this.adjustHoveredSlider(k)) {
            if (this.pendingScaleRefresh) {
                this.pendingScaleRefresh = false;
                this.refreshLayoutForConfiguredScale();
            }
        } else {
            for (CategoryComponent category : categories) {
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.keyTyped(t, k);
                    }
                }
            }

            if (CommandLine.opened) {
                String cm = this.commandLineInput.getText();
                if (k == 28 && !cm.isEmpty()) {
                    CommandHandler.runCommand(this.commandLineInput.getText());
                    this.commandLineInput.setText("");
                    return;
                }

                this.commandLineInput.keyPressed(t, k);
            }
        }
    }

    private boolean adjustHoveredSlider(int keyCode) {
        if (keyCode != 203 && keyCode != 205) {
            return false;
        }

        if (CommandLine.opened && this.commandLineInput.isFocused()) {
            return false;
        }

        int mouseX = Mouse.getX() * this.width / this.minecraft.width;
        int mouseY = this.height - Mouse.getY() * this.height / this.minecraft.height - 1;
        SliderComponent slider = this.getHoveredSlider(mouseX, mouseY);
        if (slider == null) {
            return false;
        }

        int direction = keyCode == 205 ? 1 : -1;
        if (this.heldArrowSlider != slider.sliderSetting || this.heldArrowDirection != direction) {
            long now = System.currentTimeMillis();
            this.heldArrowSlider = slider.sliderSetting;
            this.heldArrowDirection = direction;
            this.arrowHoldStartedAt = now;
            this.lastArrowAdjustmentAt = now;
            slider.adjustValue(direction);
        }

        return true;
    }

    private void renderClientInfo(int y, TextRenderer fontRenderer) {
        fontRenderer.drawWithShadow("feral " + Feral.VERSION, 4.0F, y + 10.0F, Gui.INFO_COLOR);
    }

    private SliderComponent getHoveredSlider(int mouseX, int mouseY) {
        CategoryComponent category = this.getTopmostUnderCursor(this.getCategoriesInRenderOrder(), mouseX, mouseY);
        if (category != null && category.isOpened() && !category.overTitle(mouseX, mouseY)) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof SliderComponent) {
                        SliderComponent slider = (SliderComponent) component;
                        if (slider.isHovered(mouseX, mouseY)) {
                            return slider;
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private void updateHeldSliderAdjustment(int mouseX, int mouseY) {
        if (this.heldArrowSlider != null) {
            int heldKey = this.heldArrowDirection > 0 ? 205 : 203;
            SliderComponent hoveredSlider = this.getHoveredSlider(mouseX, mouseY);
            if (Keyboard.isKeyDown(heldKey)
                    && !this.binding()
                    && (!CommandLine.opened || !this.commandLineInput.isFocused())
                    && hoveredSlider != null
                    && hoveredSlider.sliderSetting == this.heldArrowSlider) {
                long now = System.currentTimeMillis();
                long heldDuration = now - this.arrowHoldStartedAt;
                if (heldDuration >= 300L) {
                    long acceleratingDuration = heldDuration - 300L;
                    long repeatDelay = Math.max(20L, Math.round(180.0 * Math.pow(0.5, acceleratingDuration / 750.0)));
                    if (now - this.lastArrowAdjustmentAt >= repeatDelay) {
                        hoveredSlider.adjustValue(this.heldArrowDirection);
                        this.lastArrowAdjustmentAt = now;
                    }
                }
            } else {
                this.clearHeldSliderAdjustment();
            }
        }
    }

    private void clearHeldSliderAdjustment() {
        this.heldArrowSlider = null;
        this.heldArrowDirection = 0;
        this.arrowHoldStartedAt = 0L;
        this.lastArrowAdjustmentAt = 0L;
    }

    public void buttonClicked(ButtonWidget b) {
        if (b == this.commandLineSend) {
            CommandHandler.runCommand(this.commandLineInput.getText());
            this.commandLineInput.setText("");
        }
    }

    public void removed() {
        for (CategoryComponent c : categories) {
            c.dragging = false;
            c.onGuiClosed();

            for (Component m : c.getModules()) {
                m.onGuiClosed();
            }
        }

        this.clearHeldSliderAdjustment();
    }

    public boolean shouldPauseGame() {
        return false;
    }

    private boolean binding() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                for (Component component : m.settings) {
                    if (component instanceof BindComponent && ((BindComponent) component).isBinding) {
                        return true;
                    }

                    if (component instanceof FocusableTextComponent
                            && ((FocusableTextComponent) component).isTextInputFocused()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean unfocusFocusedTextInput() {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private FocusableTextComponent getActiveFocusedTextInput() {
        FocusableTextComponent activeComponent = null;

        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            if (activeComponent == null) {
                                activeComponent = textComponent;
                            } else {
                                textComponent.unfocusTextInput();
                            }
                        }
                    }
                }
            }
        }

        return activeComponent;
    }

    private FocusableTextComponent findFocusedTextComponentAt(int mouseX, int mouseY) {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));

        for (CategoryComponent category : inputOrder) {
            if (category.isOpened() && category.overRect(mouseX, mouseY)) {
                for (ModuleComponent module : category.getModules()) {
                    for (Component component : module.settings) {
                        if (component instanceof FocusableTextComponent) {
                            FocusableTextComponent textComponent = (FocusableTextComponent) component;
                            if (textComponent.isTextInputFocused() && textComponent.containsClick(mouseX, mouseY)) {
                                return textComponent;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private void enforceSingleFocusedTextInput(FocusableTextComponent focusedComponentToKeep) {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent != focusedComponentToKeep && textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                        }
                    }
                }
            }
        }
    }

    public void onSliderChange() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                m.onSliderChange();
            }
        }
    }

    public void requestScaleRefresh() {
        this.pendingScaleRefresh = true;
    }

    private void refreshLayoutForConfiguredScale() {
        this.refreshScaledResolution();

        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
            categoryComponent.limitPositions();
        }

        this.buttons.clear();
        this.init();
    }

    private void refreshScaledResolution() {
        this.sr = new Window(this.minecraft);
        this.actualScreenWidth = this.sr.getWidth();
        this.actualScreenHeight = this.sr.getHeight();
        double targetScaleFactor = this.getTargetGuiScaleFactor();
        this.width = Math.max(1, MathHelper.ceil(this.minecraft.width / targetScaleFactor));
        this.height = Math.max(1, MathHelper.ceil(this.minecraft.height / targetScaleFactor));
    }

    private int getMaximumGuiScaleFactor() {
        int scaleFactor = 1;

        while (this.minecraft.width / (scaleFactor + 1) >= 320 && this.minecraft.height / (scaleFactor + 1) >= 240) {
            scaleFactor++;
        }

        if (this.minecraft.isUnicode() && scaleFactor % 2 != 0 && scaleFactor != 1) {
            scaleFactor--;
        }

        return scaleFactor;
    }

    private double getTargetGuiScaleFactor() {
        return Math.max(1.0, Math.min(this.getMaximumGuiScaleFactor(), this.getConfiguredGuiScale() * 2.0));
    }

    private int toLogicalCoordinate(int coordinate) {
        return (int) Math.floor(coordinate / this.getRenderScale());
    }

    private double getRenderScale() {
        return this.actualScreenWidth > 0 && this.width > 0 ? (double) this.actualScreenWidth / this.width : 1.0;
    }

    public static double getActiveRenderScale() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen instanceof ClickGui ? ((ClickGui) minecraft.screen).getRenderScale() : 1.0;
    }

    private double getConfiguredGuiScale() {
        return Gui.getClickGuiScale();
    }
}
