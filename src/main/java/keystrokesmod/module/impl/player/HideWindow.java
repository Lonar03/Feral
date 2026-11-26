package keystrokesmod.module.impl.player;

import java.util.List;
import keystrokesmod.clickgui.HudPositionEditor;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.StringListSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
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

    private class EditScreen extends HudPositionEditor {
        private EditScreen() {
            super("Drag the icon to reposition it.", 10);
        }

        @Override
        protected void syncAnchor(Window window) {
            HideWindow.this.syncPosition(window);
        }

        @Override
        protected float anchorX() {
            return HideWindow.this.posX;
        }

        @Override
        protected float anchorY() {
            return HideWindow.this.posY;
        }

        @Override
        protected void drawPreview(float x, float y, Window window) {
            HideWindow.this.setAbsolutePosition(x, y, window);
            HideWindow.this.renderIcon(true);
        }

        @Override
        protected boolean hitsPreview(int mouseX, int mouseY) {
            float scale = (float) HideWindow.this.iconScale.getInput();
            int size = Math.round(16.0F * scale);
            float minX = HideWindow.this.posX - size / 2.0F - 2.0F;
            float minY = HideWindow.this.posY - size / 2.0F - 2.0F;
            float maxX = HideWindow.this.posX + size / 2.0F + 2.0F;
            float maxY = HideWindow.this.posY + size / 2.0F + 2.0F;
            return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
        }

        @Override
        protected void resetAnchor() {
            HideWindow.this.resetPosition();
        }
    }
}
