package keystrokesmod.module.impl.player;

import java.util.List;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.StringListSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.resource.Identifier;

public class HideWindow extends Module {
    private static final String ICON_PATH = "/assets/keystrokesmod/textures/gui/tv_off.png";
    private static final int ICON_BASE_SIZE = 16;
    private static final float DEFAULT_RELATIVE_X = 0.5F;
    private static final float DEFAULT_RELATIVE_Y = 0.05F;
    private static final int EDIT_OUTLINE_COLOR = -1;
    private final ColorSetting iconColor;
    private final SliderSetting iconScale;
    private final ButtonSetting onlyWhileCrouching;
    private final ButtonSetting whitelist;
    private final StringListSetting whitelistEntries;
    private InventoryMenuScreen hiddenGui;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public HideWindow() {
        super("Hide Window", Module.category.player);
        this.registerSetting(this.iconColor = new ColorSetting("Icon color", 255, 255, 255));
        this.registerSetting(this.iconScale = new SliderSetting("Icon scale", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new HideWindow.EditScreen())));
        this.registerSetting(this.onlyWhileCrouching = new ButtonSetting("Only while crouching", false));
        this.registerSetting(this.whitelist = new ButtonSetting("Whitelist", false));
        this.registerSetting(
                this.whitelistEntries = new StringListSetting("Whitelist names", "e.g. Upgrades & Traps", 128));
        this.whitelistEntries.visible = false;
    }

    @Override
    public String getInfo() {
        return this.hiddenGui != null ? "Hidden" : "";
    }

    @Override
    public void guiUpdate() {
        this.whitelistEntries.setVisible(this.whitelist.isToggled(), this);
    }

    @Override
    public void onDisable() {
        if (this.hiddenGui != null && mc.player != null) {
            mc.player.closeMenu();
        }

        this.hiddenGui = null;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onGuiOpen(ForgeEvents.GuiOpenEvent event) {
        if (event.gui != null && mc.player != null) {
            if (event.gui instanceof InventoryMenuScreen && !(event.gui instanceof SurvivalInventoryScreen)) {
                if (mc.screen instanceof InventoryMenuScreen) {
                    this.hiddenGui = null;
                } else {
                    InventoryMenuScreen gui = (InventoryMenuScreen) event.gui;
                    if (!this.onlyWhileCrouching.isToggled() || mc.player.isSneaking()) {
                        if (!this.whitelist.isToggled() || this.matchesWhitelist(gui)) {
                            this.hiddenGui = gui;
                            mc.player.menu = this.hiddenGui.menu;
                            event.setCanceled(true);
                        }
                    }
                }
            } else {
                if (event.gui instanceof SurvivalInventoryScreen && this.hiddenGui != null) {
                    event.gui = this.hiddenGui;
                    this.hiddenGui = null;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof CloseInventoryMenuS2CPacket) {
            this.hiddenGui = null;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldLoad(ForgeEvents.WorldEvent.Load event) {
        this.hiddenGui = null;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (this.hiddenGui != null && mc.screen == null && !mc.options.debugEnabled) {
                this.renderIcon(false);
            }
        }
    }

    public float getPosX() {
        this.syncPosition();
        return this.posX;
    }

    public float getPosY() {
        this.syncPosition();
        return this.posY;
    }

    public float getRelativePosX() {
        this.syncPosition();
        return this.relativePosX;
    }

    public float getRelativePosY() {
        this.syncPosition();
        return this.relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.relativePosX = normalizedX;
        this.relativePosY = normalizedY;
        this.syncPosition();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        this.setAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public void resetPosition() {
        this.setRelativePosition(0.5F, 0.05F);
    }

    private void renderIcon(boolean editing) {
        this.syncPosition();
        Identifier icon = RenderUtils.getIcon("/assets/keystrokesmod/textures/gui/tv_off.png");
        if (icon != null) {
            float scale = (float) this.iconScale.getInput();
            int size = Math.round(16.0F * scale);
            float drawX = this.posX - size / 2.0F;
            float drawY = this.posY - size / 2.0F;
            RenderUtils.drawIcon(icon, drawX, drawY, size, 0xFF000000 | this.iconColor.getRGB());
            if (editing) {
                float outLeft = drawX - 2.0F;
                float outTop = drawY - 2.0F;
                float outRight = drawX + size + 2.0F;
                float outBottom = drawY + size + 2.0F;
                RenderUtils.drawRect(outLeft, outTop, outRight, outTop + 1.0F, -1);
                RenderUtils.drawRect(outLeft, outBottom - 1.0F, outRight, outBottom, -1);
                RenderUtils.drawRect(outLeft, outTop, outLeft + 1.0F, outBottom, -1);
                RenderUtils.drawRect(outRight - 1.0F, outTop, outRight, outBottom, -1);
            }
        }
    }

    private void syncPosition() {
        this.syncPosition(new Window(mc));
    }

    private void syncPosition(Window resolution) {
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (!Float.isNaN(this.posX) && !Float.isNaN(this.posY)) {
                this.relativePosX = this.posX / w;
                this.relativePosY = this.posY / h;
            } else {
                this.relativePosX = 0.5F;
                this.relativePosY = 0.05F;
            }
        }

        this.posX = this.relativePosX * w;
        this.posY = this.relativePosY * h;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        this.relativePosX = absoluteX / w;
        this.relativePosY = absoluteY / h;
    }

    private boolean matchesWhitelist(InventoryMenuScreen gui) {
        List<String> entries = this.whitelistEntries.getEntries();
        if (entries.isEmpty()) {
            return false;
        }

        String title = getContainerTitle(gui);
        if (title.isEmpty()) {
            return false;
        }

        String lower = title.toLowerCase();

        for (String name : entries) {
            if (lower.contains(name.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private static String getContainerTitle(InventoryMenuScreen gui) {
        return gui.menu instanceof ChestMenu
                ? ((ChestMenu) gui.menu).getChest().getDisplayName().getString()
                : "";
    }

    private class EditScreen extends Screen {
        private ButtonWidget resetBtn;
        private boolean dragging;
        private float actualX;
        private float actualY;
        private float lastActualX;
        private float lastActualY;
        private int lastMouseX;
        private int lastMouseY;

        private EditScreen() {}

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetBtn = new ButtonWidget(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            HideWindow.this.syncPosition(new Window(this.minecraft));
            this.actualX = HideWindow.this.posX;
            this.actualY = HideWindow.this.posY;
        }

        public void render(int mouseX, int mouseY, float partialTicks) {
            Window resolution = new Window(this.minecraft);
            if (!this.dragging) {
                HideWindow.this.syncPosition(resolution);
                this.actualX = HideWindow.this.posX;
                this.actualY = HideWindow.this.posY;
            }

            fill(0, 0, this.width, this.height, -1308622848);
            HideWindow.this.setAbsolutePosition(this.actualX, this.actualY, resolution);
            HideWindow.this.renderIcon(true);
            this.actualX = HideWindow.this.posX;
            this.actualY = HideWindow.this.posY;
            String message = "Drag the icon to reposition it.";
            int textX = resolution.getWidth() / 2 - this.textRenderer.getWidth(message) / 2;
            int textY = resolution.getHeight() / 2 - 10;
            RenderUtils.drawColoredString(message, '-', textX, textY, 2L, 0L, true, this.minecraft.textRenderer);
            ClientPlatform.handleInput(this);
            super.render(mouseX, mouseY, partialTicks);
        }

        protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
            if (button == 0) {
                if (this.dragging) {
                    this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                    this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
                } else {
                    float scale = (float) HideWindow.this.iconScale.getInput();
                    int size = Math.round(16.0F * scale);
                    float minX = HideWindow.this.posX - size / 2.0F - 2.0F;
                    float minY = HideWindow.this.posY - size / 2.0F - 2.0F;
                    float maxX = HideWindow.this.posX + size / 2.0F + 2.0F;
                    float maxY = HideWindow.this.posY + size / 2.0F + 2.0F;
                    if (mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY) {
                        this.dragging = true;
                        this.lastMouseX = mouseX;
                        this.lastMouseY = mouseY;
                        this.lastActualX = this.actualX;
                        this.lastActualY = this.actualY;
                    }
                }
            }
        }

        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        public void buttonClicked(ButtonWidget button) {
            if (button == this.resetBtn) {
                HideWindow.this.resetPosition();
                this.actualX = HideWindow.this.posX;
                this.actualY = HideWindow.this.posY;
            }
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }
}
