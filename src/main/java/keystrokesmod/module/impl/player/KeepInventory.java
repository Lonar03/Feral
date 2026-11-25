package keystrokesmod.module.impl.player;

import keystrokesmod.event.KeyEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorCloseInventoryMenuC2S;
import keystrokesmod.mixin.impl.accessor.IAccessorKeyBinding;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents.GuiOpenEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.WorldEvent.Unload;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.text.LiteralText;
import org.lwjgl.input.Keyboard;

public class KeepInventory extends Module {
    private InventoryMenuScreen cachedScreen;
    private int cachedWindowId = -1;
    private String cachedTitle;
    private boolean reopeningGui = false;
    private int lastAnnouncedWindowId = -1;
    private static int lastOpenWindowId = -1;
    private static String lastOpenWindowTitle = null;
    private static int allowCloseWindowId = -1;
    /**
     * Feral's KeyEvent fires before Minecraft hands the key to the open screen (Forge's KeyInputEvent fired after),
     * so reopening right away let the same E press close the reopened chest again. Reopen at the end of the tick.
     */
    private boolean pendingReopen;

    private final KeySetting forgetKey;

    public KeepInventory() {
        super("Keep Inventory", Module.category.player);
        this.registerSetting(this.forgetKey = new KeySetting("Forget key", 0));
    }

    /** Forget key: closes the saved container for real (server-side too) and drops it. */
    private void forgetSaved() {
        if (!this.hasSavedGUI()) {
            return;
        }

        int id = this.cachedWindowId;
        allowCloseWindowId = id; // lets closeMenu() through our own hook and its close packet through onSendPacket
        if (mc.player.menu != null && mc.player.menu.networkId == id) {
            mc.player.closeMenu();
        } else {
            this.forceCloseIfNeeded();
        }

        this.clearCache();
        Utils.sendMessage("&7[KeepInventory] Forgot the saved inventory.");
    }

    @Override
    public void onDisable() {
        this.forceCloseIfNeeded();
        this.clearCache();
    }

    private void clearCache() {
        this.resetCache(true, true);
    }

    private void resetCache(boolean resetAllowFlag, boolean resetOpenPacketCache) {
        this.cachedScreen = null;
        this.cachedWindowId = -1;
        this.cachedTitle = null;
        this.reopeningGui = false;
        this.lastAnnouncedWindowId = -1;
        if (resetAllowFlag) {
            allowCloseWindowId = -1;
        }

        if (resetOpenPacketCache) {
            lastOpenWindowId = -1;
            lastOpenWindowTitle = null;
        }
    }

    /** Shown next to the module name in the HUD, like ZombieCat's status. */
    @Override
    public String getInfo() {
        return this.hasSavedGUI() ? "Saved" : "None";
    }

    public boolean hasSavedGUI() {
        return this.cachedScreen != null && this.cachedWindowId > 0;
    }

    private int currentWindowId() {
        return mc != null && mc.player != null && mc.player.menu != null ? mc.player.menu.networkId : -1;
    }

    private boolean isContainerAlive() {
        if (this.cachedWindowId <= 0 || mc == null || mc.player == null) {
            return false;
        } else if (mc.player.menu == null) {
            return false;
        } else {
            return mc.player.menu == mc.player.playerMenu ? false : mc.player.menu.networkId == this.cachedWindowId;
        }
    }

    private void forceCloseIfNeeded() {
        if (mc != null && mc.getNetworkHandler() != null) {
            if (this.cachedWindowId > 0) {
                allowCloseWindowId = this.cachedWindowId;

                try {
                    mc.getNetworkHandler().sendPacket(new CloseInventoryMenuC2SPacket(this.cachedWindowId));
                } catch (Throwable var2) {
                }
            }
        }
    }

    public static void onOpenWindowPacket(int id, String title) {
        lastOpenWindowId = id;
        lastOpenWindowTitle = title;
        KeepInventory inst = ModuleManager.keepInventory;
        if (inst != null) {
            inst.onServerWindowOpened(id, title);
        }
    }

    private void onServerWindowOpened(int id, String title) {
        if (this.isEnabled()) {
            this.resetCache(true, false);
        }
    }

    public static void onServerForcedClose(int id) {
        KeepInventory inst = ModuleManager.keepInventory;
        if (inst != null) {
            if (id < 0 || id == inst.cachedWindowId) {
                inst.clearCache();
            }
        }
    }

    public static boolean shouldPreserveFor(int id) {
        KeepInventory inst = ModuleManager.keepInventory;
        return inst != null && inst.isEnabled() && inst.cachedWindowId == id && id > 0 && allowCloseWindowId != id;
    }

    public boolean handleCloseKey(InventoryMenuScreen gui, int keyCode) {
        if (this.isEnabled() && Utils.nullCheck()) {
            int id = this.currentWindowId();
            if (id <= 0) {
                return false;
            }

            if (!Keyboard.isKeyDown(42) && !Keyboard.isKeyDown(54)) {
                int inventoryKey = mc.options.inventoryKey.getKeyCode();
                if (keyCode != 1 && keyCode != inventoryKey) {
                    return false;
                }

                this.cacheGui(gui, id);
                this.hideGui();
                return true;
            } else {
                allowCloseWindowId = id;
                this.resetCache(false, false);
                return false;
            }
        } else {
            return false;
        }
    }

    private void cacheGui(InventoryMenuScreen gui, int id) {
        this.cachedScreen = gui;
        this.cachedWindowId = id;
        if (this.cachedTitle == null) {
            if (lastOpenWindowId == id && lastOpenWindowTitle != null) {
                this.cachedTitle = lastOpenWindowTitle;
            } else {
                this.cachedTitle = ClientPlatform.className(gui.getClass());
            }
        }
    }

    private void hideGui() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.screen = null;
            minecraft.lockMouse();
        }

        KeyBinding inv = minecraft.options.inventoryKey;
        ((IAccessorKeyBinding) inv).setPressTime(0);
    }

    private void reopenCached() {
        if (this.cachedScreen != null) {
            if (!this.isContainerAlive()) {
                this.clearCache();
            } else {
                this.reopeningGui = true;
                Minecraft minecraft = Minecraft.getInstance();
                ((IAccessorKeyBinding) minecraft.options.inventoryKey).setPressTime(0);
                minecraft.openScreen(this.cachedScreen);
                this.reopeningGui = false;
            }
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (this.isEnabled() && Utils.nullCheck()) {
            Screen next = e.gui;
            if (next instanceof InventoryMenuScreen && !(next instanceof SurvivalInventoryScreen)) {
                int id = this.currentWindowId();
                if (id > 0) {
                    this.cachedScreen = (InventoryMenuScreen) next;
                    this.cachedWindowId = id;
                    if (lastOpenWindowId == id && lastOpenWindowTitle != null) {
                        this.cachedTitle = lastOpenWindowTitle;
                    }

                    boolean shouldAnnounce = !this.reopeningGui && this.lastAnnouncedWindowId != id;
                    if (shouldAnnounce) {
                        if (this.cachedTitle == null) {
                            this.cachedTitle = ClientPlatform.className(next.getClass());
                        }

                        this.sendSavedMessage();
                        this.lastAnnouncedWindowId = id;
                    }

                    if (this.cachedTitle == null) {
                        this.cachedTitle = ClientPlatform.className(next.getClass());
                    }

                    lastOpenWindowId = -1;
                    lastOpenWindowTitle = null;
                }
            }
        }
    }

    private void sendSavedMessage() {
        if (mc != null && mc.player != null) {
            String title = this.cachedTitle != null ? this.cachedTitle : "Window " + this.cachedWindowId;
            mc.player.sendMessage(new LiteralText("[KeepInventory] Saved " + title));
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyEvent e) {
        if (this.isEnabled() && Keyboard.getEventKeyState()) {
            if (Utils.nullCheck()) {
                if (mc.screen == null
                        && this.forgetKey.getKey() > 0
                        && Keyboard.getEventKey() == this.forgetKey.getKey()) {
                    this.forgetSaved();
                    return;
                }

                if (mc.screen == null) {
                    if (this.cachedScreen != null && this.cachedWindowId > 0) {
                        int inventoryKey = mc.options.inventoryKey.getKeyCode();
                        if (Keyboard.getEventKey() == inventoryKey) {
                            // Drop the click so vanilla doesn't open the player inventory later this tick
                            ((IAccessorKeyBinding) mc.options.inventoryKey).setPressTime(0);
                            this.pendingReopen = true;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(Unload e) {
        if (this.isEnabled()) {
            this.forceCloseIfNeeded();
            this.clearCache();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent e) {
        if (this.isEnabled()) {
            if (e.phase == Phase.END) {
                if (!Utils.nullCheck()) {
                    this.forceCloseIfNeeded();
                    this.clearCache();
                } else {
                    if (this.cachedScreen != null && !this.isContainerAlive()) {
                        this.clearCache();
                    }

                    if (this.pendingReopen) {
                        this.pendingReopen = false;
                        if (mc.screen == null) {
                            this.reopenCached();
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!(e.getPacket() instanceof CloseInventoryMenuC2SPacket)) {
            return;
        }

        int id = ((IAccessorCloseInventoryMenuC2S) e.getPacket()).getWindowId();

        if (id == allowCloseWindowId) {
            allowCloseWindowId = -1;
        } else if (id == this.cachedWindowId && this.cachedWindowId > 0) {
            e.setCanceled(true);
        }
    }
}
