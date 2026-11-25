package keystrokesmod.module.impl.player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import keystrokesmod.event.PostProfileLoadEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.InventoryItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.Utils;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.PlayerMenu;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.GoldenAppleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;

public class InvManager extends Module {
    private static final int HOTBAR_SIZE = PlayerInventory.getHotbarSize();
    private static final int MAX_INSTANT_CLICKS_PER_TICK = 90;
    private static final int AUTO_SORT_DISABLED = -1;
    private static final int AUTO_SORT_NORMAL = 0;
    private static final int AUTO_SORT_CUSTOM = 1;
    private static final double QUALITY_EPSILON = 1.0E-6;
    private static final Comparator<InvManager.PlannedAction> ACTION_COMPARATOR = (first, second) -> {
        int comparison = Integer.compare(first.clickCount, second.clickCount);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Boolean.compare(second.completesTarget, first.completesTarget);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(first.priorityIndex, second.priorityIndex);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(first.displacesCorrectHotbar, second.displacesCorrectHotbar);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(second.resultingSize, first.resultingSize);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(first.targetHotbarSlot, second.targetHotbarSlot);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(first.primarySourceIndex, second.primarySourceIndex);
        return comparison != 0 ? comparison : Integer.compare(first.type.ordinal(), second.type.ordinal());
    };
    private final SliderSetting autoSortMode;
    private final SliderSetting normalSortDelay;
    private final SliderSetting swordSlot;
    private final SliderSetting blocksSlot;
    private final SliderSetting goldenAppleSlot;
    private final SliderSetting projectileSlot;
    private final SliderSetting speedSlot;
    private final SliderSetting pearlSlot;
    private final SliderSetting targetCPS;
    private final ButtonSetting disableWhenComplete;
    private final ButtonSetting stackItems;
    private final InventoryItemListSetting items;
    private static SliderSetting chestStealer;
    private static SliderSetting autoArmor;
    private static SliderSetting inventoryCleaner;
    private final ButtonSetting closeChest;
    private final ButtonSetting closeInventory;
    private final ButtonSetting disableInLobby;
    private final ButtonSetting stealFromCustomChests;
    private final KeySetting cleanKey;
    private InvManager.PlannedAction currentAction;
    private int cursorRecoveryInventoryIndex = -1;
    private double windowClickBudget;
    private boolean sessionOpen;
    private InvManager.SessionState sessionState = InvManager.SessionState.ACTIVE;
    private int remainingDelay;
    private InventoryMenu activeContainer;
    private WeakReference<ChestMenu> populatedChest = new WeakReference<>(null);
    private ChestMenu chestPendingClose;
    private Short pendingSortClick;
    private final HashSet<Short> pendingTimedClicks = new HashSet<>();
    private final Map<Integer, ItemStack> instantClickSources = new HashMap<>();
    private boolean awaitingSlots;
    private boolean awaitingCursor;
    private boolean closeInventoryGui;
    private boolean inventoryActionPerformed;
    private boolean inventoryScreenReady;
    private boolean preUpdatePacketWindow;
    private boolean sendingInventoryClick;
    private boolean sendingTimedClick;
    private boolean lastTimedClickWasCleaner;
    private boolean instantClickChangedSlot;
    private boolean externalInventoryClickPending;
    private final InvManager.CurrentArmor[] armorArr = InvManager.CurrentArmor.values();
    private static final InvManager.StepHook NO_OP = new InvManager.StepHook() {
        @Override
        public void run(InvManager module) {}
    };

    public InvManager() {
        super("InvManager", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.closeChest = new ButtonSetting("Close chest", false));
        this.registerSetting(this.closeInventory = new ButtonSetting("Close inventory", false));
        this.registerSetting(this.disableInLobby = new ButtonSetting("Disable in lobby", true));
        this.registerSetting(autoArmor = new SliderSetting("Auto armor", " tick", true, 3.0, 0.0, 20.0, 1.0));
        this.registerSetting(chestStealer = new SliderSetting("Chest stealer", " tick", true, 3.0, 0.0, 20.0, 1.0));
        this.registerSetting(this.stealFromCustomChests = new ButtonSetting("Steal from custom chests", false));
        this.registerSetting(
                inventoryCleaner = new SliderSetting("Inventory cleaner", " tick", true, 5.0, 0.0, 20.0, 1.0));
        this.registerSetting(this.cleanKey = new KeySetting("Clean key", 1002));
        this.registerSetting(
                this.autoSortMode = new SliderSetting("Auto sort mode", true, 0, new String[] {"Normal", "Custom"}));
        this.registerSetting(
                this.normalSortDelay =
                        new SliderSetting("Auto sort", " tick", 3.0, 0.0, 20.0, 1.0, "Normal auto sort.Auto sort"));
        this.registerSetting(
                this.swordSlot =
                        new SliderSetting("Sword slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Sword slot"));
        this.registerSetting(
                this.blocksSlot =
                        new SliderSetting("Blocks slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Blocks slot"));
        this.registerSetting(
                this.goldenAppleSlot = new SliderSetting(
                        "Golden apple slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Golden apple slot"));
        this.registerSetting(
                this.projectileSlot = new SliderSetting(
                        "Projectile slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Projectile slot"));
        this.registerSetting(
                this.speedSlot = new SliderSetting(
                        "Speed potion slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Speed potion slot"));
        this.registerSetting(
                this.pearlSlot =
                        new SliderSetting("Pearl slot", true, -1.0, 1.0, 9.0, 1.0, "Normal auto sort.Pearl slot"));
        this.registerSetting(
                this.targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5, "Auto sort.Target CPS"));
        this.registerSetting(
                this.disableWhenComplete =
                        new ButtonSetting("Disable when complete", false, "Auto sort.Disable when complete"));
        this.registerSetting(this.stackItems = new ButtonSetting("Stack items", false, "Auto sort.Stack items"));
        this.registerSetting(this.items = new InventoryItemListSetting("Items", new String[] {"Auto sort.Items"}));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        boolean normalMode = this.isAutoSortMode(0);
        this.normalSortDelay.setVisible(normalMode, this);
        this.swordSlot.setVisible(normalMode, this);
        this.blocksSlot.setVisible(normalMode, this);
        this.goldenAppleSlot.setVisible(normalMode, this);
        this.projectileSlot.setVisible(normalMode, this);
        this.speedSlot.setVisible(normalMode, this);
        this.pearlSlot.setVisible(normalMode, this);
        boolean customMode = this.isAutoSortMode(1);
        this.targetCPS.setVisible(customMode, this);
        this.disableWhenComplete.setVisible(customMode, this);
        this.stackItems.setVisible(customMode, this);
        this.items.setVisible(customMode, this);
    }

    @Override
    public void onEnable() {
        this.migrateLegacyAutoSortSetting();
        this.remainingDelay = 0;
        this.chestPendingClose = null;
        this.closeInventoryGui = false;
        this.inventoryActionPerformed = false;
        this.inventoryScreenReady = false;
        this.preUpdatePacketWindow = false;
        this.sendingInventoryClick = false;
        this.externalInventoryClickPending = false;
        this.closeSession();
    }

    @Override
    public void onDisable() {
        this.remainingDelay = 0;
        this.chestPendingClose = null;
        this.closeInventoryGui = false;
        this.inventoryActionPerformed = false;
        this.inventoryScreenReady = false;
        this.preUpdatePacketWindow = false;
        this.sendingInventoryClick = false;
        this.externalInventoryClickPending = false;
        this.closeSession();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (this.isAutoSortMode(1) && buttonSetting == this.disableWhenComplete && this.isManagedInventoryOpen()) {
            if (!this.disableWhenComplete.isToggled()) {
                if (this.sessionState == InvManager.SessionState.COMPLETE_LATCHED) {
                    this.sessionState = InvManager.SessionState.ACTIVE;
                }
            } else {
                if (this.currentAction == null && this.cursorRecoveryInventoryIndex < 0) {
                    InvManager.InventorySnapshot snapshot = InvManager.InventorySnapshot.capture();
                    InvManager.SnapshotContext context =
                            InvManager.SnapshotContext.create(snapshot, this.resolveAssignments(snapshot));
                    if (this.findBestSortingAction(context) == null) {
                        this.sessionState = InvManager.SessionState.COMPLETE_LATCHED;
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSlotScroll(PreSlotScrollEvent event) {
        if (this.shouldCancelManualInventoryInput()) {
            event.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onProfileLoad(PostProfileLoadEvent event) {
        this.migrateLegacyAutoSortSetting();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        if (event.getPacket() instanceof InventoryMenuClickSlotC2SPacket) {
            if (this.sendingInventoryClick) {
                InventoryMenuClickSlotC2SPacket click = (InventoryMenuClickSlotC2SPacket) event.getPacket();
                if (this.sendingTimedClick) {
                    this.pendingTimedClicks.add(click.getActionId());
                } else if (this.currentAction != null) {
                    this.pendingSortClick = click.getActionId();
                }
            } else if (mc.screen instanceof SurvivalInventoryScreen || mc.screen instanceof ChestScreen) {
                this.externalInventoryClickPending = true;
                this.closeInventoryGui = false;
                this.chestPendingClose = null;
            }
        }
    }

    public void onMenuContents(int menuId, ItemStack[] items) {
        if (mc.player != null && mc.player.menu.networkId == menuId) {
            if (this.activeContainer == mc.player.menu) {
                this.awaitingSlots = false;
            }

            if (mc.player.menu instanceof ChestMenu) {
                this.populatedChest = new WeakReference<>((ChestMenu) mc.player.menu);
            } else if (mc.player.menu instanceof PlayerMenu) {
                if (!this.sessionOpen) {
                    return;
                }

                for (int index = 0; index < 36; index++) {
                    int slot = toContainerSlot(index);
                    if (slot < items.length && !ItemStack.matches(mc.player.inventory.getItem(index), items[slot])) {
                        this.invalidateSortPlan();
                        break;
                    }
                }
            }
        }
    }

    public void onMenuSlot(int menuId, int slot, ItemStack incoming) {
        if (menuId == -1 && mc.player != null && this.activeContainer == mc.player.menu) {
            this.awaitingCursor = false;
        }

        if (this.isManagedInventoryOpen()) {
            ItemStack current;
            if (menuId == -1) {
                current = mc.player.inventory.getCursorItem();
            } else {
                if (menuId != mc.player.menu.networkId || slot < 9 || slot >= 45) {
                    return;
                }

                current = mc.player.inventory.getItem(slot >= 36 ? slot - 36 : slot);
            }

            if (this.sessionOpen && !ItemStack.matches(current, incoming)) {
                this.invalidateSortPlan();
            }
        }
    }

    public void onMenuConfirm(int menuId, short actionId, boolean accepted) {
        if (this.activeContainer != null
                && mc.player != null
                && this.activeContainer == mc.player.menu
                && this.activeContainer.networkId == menuId) {
            if (this.pendingSortClick != null && this.pendingSortClick == actionId) {
                this.pendingSortClick = null;
            } else if (!this.pendingTimedClicks.remove(actionId)) {
                return;
            }

            if (!accepted) {
                this.pendingTimedClicks.clear();
                this.awaitingSlots = true;
                this.awaitingCursor = true;
                this.chestPendingClose = null;
                this.invalidateSortPlan();
            }
        }
    }

    private void invalidateSortPlan() {
        this.currentAction = null;
        this.sessionState = InvManager.SessionState.ACTIVE;
        this.closeInventoryGui = false;
    }

    private void migrateLegacyAutoSortSetting() {
        if (this.normalSortDelay.getInput() == -1.0) {
            this.autoSortMode.setValueRaw(-1.0);
            this.normalSortDelay.setValue(3.0);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        this.preUpdatePacketWindow = true;

        try {
            this.handlePreUpdate();
        } finally {
            this.preUpdatePacketWindow = false;
        }
    }

    private void handlePreUpdate() {
        if (this.remainingDelay > 0) {
            this.remainingDelay--;
        }

        this.instantClickSources.clear();
        if (!Utils.nullCheck()) {
            this.chestPendingClose = null;
            this.closeInventoryGui = false;
            this.inventoryActionPerformed = false;
            this.inventoryScreenReady = false;
            this.externalInventoryClickPending = false;
            this.closeSession();
        } else if (mc.screen == null) {
            this.chestPendingClose = null;
            this.closeInventoryGui = false;
            this.inventoryActionPerformed = false;
            this.inventoryScreenReady = false;
            this.externalInventoryClickPending = false;
            this.closeSession();
        } else {
            if (!(mc.screen instanceof SurvivalInventoryScreen)) {
                this.inventoryScreenReady = false;
            }

            if (this.shouldPauseForLobby()) {
                this.inventoryScreenReady = false;
                this.closeSession();
            } else {
                if (this.activeContainer != mc.player.menu) {
                    this.closeSession();
                    this.activeContainer = mc.player.menu;
                    this.externalInventoryClickPending = false;
                    this.closeInventoryGui = false;
                    this.inventoryActionPerformed = false;
                    this.inventoryScreenReady = false;
                }

                if (this.externalInventoryClickPending) {
                    if (this.pendingSortClick == null
                            && this.pendingTimedClicks.isEmpty()
                            && !this.awaitingSlots
                            && !this.awaitingCursor) {
                        this.externalInventoryClickPending = false;
                        this.resetCustomSorter();
                    }
                } else {
                    if (this.chestPendingClose != mc.player.menu || !this.closeChest.isToggled()) {
                        this.chestPendingClose = null;
                    }

                    if (this.closeInventory.isToggled()
                            && this.closeInventoryGui
                            && mc.screen instanceof SurvivalInventoryScreen) {
                        this.closeInventoryGui = false;
                        this.closeSession();
                        mc.player.closeMenu();
                    } else {
                        if (mc.screen instanceof SurvivalInventoryScreen) {
                            if (!this.inventoryScreenReady) {
                                this.inventoryScreenReady = true;
                                return;
                            }
                        } else if (!(mc.screen instanceof ChestScreen)) {
                            this.closeSession();
                            return;
                        }

                        this.runInventoryActions();
                    }
                }
            }
        }
    }

    private void runInventoryActions() {
        if (this.remainingDelay <= 0 && this.pendingSortClick == null && !this.awaitingSlots && !this.awaitingCursor) {
            for (int clicks = 0; clicks < 90; clicks++) {
                this.instantClickChangedSlot = false;
                if (mc.screen instanceof SurvivalInventoryScreen) {
                    this.handleInventoryScreen();
                } else if (mc.screen instanceof ChestScreen) {
                    this.handleChestScreen();
                }

                if (!this.instantClickChangedSlot || this.awaitingSlots || this.awaitingCursor) {
                    return;
                }
            }
        }
    }

    private boolean shouldPauseForLobby() {
        return this.disableInLobby.isToggled() && Utils.isLobby()
                || ModuleManager.skyWars != null
                        && ModuleManager.invmove != null
                        && ModuleManager.skyWars.isEnabled()
                        && ModuleManager.invmove.isEnabled()
                        && ModuleManager.invmove.isCloseInventoryMode()
                        && Utils.getSkyWarsStatus() != 2;
    }

    private void handleInventoryScreen() {
        if (!this.isManagedInventoryOpen()) {
            this.closeSession();
        } else if (!this.tryAutoArmorAction()) {
            if (this.isAutoSortMode(1)) {
                if (this.pendingTimedClicks.isEmpty()
                        ? this.runInventorySorterTick()
                        : !this.lastTimedClickWasCleaner) {
                    return;
                }
            } else {
                this.stopCustomSorter();
                if (this.isAutoSortMode(0) && this.tryNormalAutoSortAction()) {
                    return;
                }
            }

            if (!this.tryInventoryCleanerAction()) {
                if (this.closeInventory.isToggled()
                        && this.inventoryActionPerformed
                        && this.pendingTimedClicks.isEmpty()
                        && !this.ownsCarriedStack()) {
                    this.closeInventoryGui = true;
                }
            }
        }
    }

    private boolean isAutoSortMode(int mode) {
        return (int) this.autoSortMode.getInput() == mode;
    }

    private void stopCustomSorter() {
        if (this.sessionOpen || this.currentAction != null || this.cursorRecoveryInventoryIndex >= 0) {
            InvManager.InventorySnapshot snapshot = InvManager.InventorySnapshot.capture();
            if (this.ownsCarriedStack() && snapshot.carried != null) {
                this.recoverCarriedStackImmediately(snapshot, false);
            }

            this.resetCustomSorter();
        }
    }

    private boolean tryNormalAutoSortAction() {
        if (mc.player.inventory.getCursorItem() != null) {
            return false;
        }

        InvManager.InventoryData data = new InvManager.InventoryData(mc.player.inventory, true, false);
        if (this.fixNormalSwordSlot(data)) {
            return true;
        }

        for (int inventoryIndex = 0; inventoryIndex < data.size; inventoryIndex++) {
            ItemStack itemStack = data.inventory.getItem(inventoryIndex);
            if (itemStack != null && !this.isSword(itemStack) && !this.isArmor(itemStack)) {
                Item item = itemStack.getItem();
                if (isSortableBlock(itemStack)) {
                    int targetSlot = this.configuredHotbarSlot(this.blocksSlot);
                    if (targetSlot >= 0 && inventoryIndex != targetSlot) {
                        ItemStack currentBlocks = data.inventory.getItem(targetSlot);
                        if (!isSortableBlock(currentBlocks)) {
                            this.normalSortClick(inventoryIndex, targetSlot, 2);
                            return true;
                        }

                        if (currentBlocks.size < currentBlocks.getMaxSize()) {
                            if (canStacksMerge(itemStack, currentBlocks)) {
                                if (itemStack.size == itemStack.getMaxSize()) {
                                    this.normalSortClick(inventoryIndex, targetSlot, 2);
                                    return true;
                                }

                                if (data.emptyWithoutHotbar != 0 || inventoryIndex >= HOTBAR_SIZE) {
                                    this.normalSortClick(inventoryIndex, 0, 1);
                                    return true;
                                }
                            } else if (itemStack.size > currentBlocks.size) {
                                this.normalSortClick(inventoryIndex, targetSlot, 2);
                                return true;
                            }
                        }
                    }
                } else if (item instanceof GoldenAppleItem) {
                    int targetSlot = this.configuredHotbarSlot(this.goldenAppleSlot);
                    if (targetSlot >= 0 && inventoryIndex != targetSlot) {
                        ItemStack currentGoldenApple = data.inventory.getItem(targetSlot);
                        if (currentGoldenApple == null || !(currentGoldenApple.getItem() instanceof GoldenAppleItem)) {
                            this.normalSortClick(inventoryIndex, targetSlot, 2);
                            return true;
                        }
                    }
                } else if (item instanceof SnowballItem || item instanceof EggItem) {
                    int targetSlot = this.configuredHotbarSlot(this.projectileSlot);
                    if (targetSlot >= 0 && inventoryIndex != targetSlot) {
                        ItemStack currentProjectile = data.inventory.getItem(targetSlot);
                        if (currentProjectile == null
                                || !(currentProjectile.getItem() instanceof SnowballItem)
                                        && !(currentProjectile.getItem() instanceof EggItem)) {
                            this.normalSortClick(inventoryIndex, targetSlot, 2);
                            return true;
                        }

                        if (itemStack.size >= itemStack.getMaxSize()) {
                            if (itemStack.size > currentProjectile.size) {
                                this.normalSortClick(inventoryIndex, targetSlot, 2);
                                return true;
                            }
                        } else if (currentProjectile.size < currentProjectile.getMaxSize()) {
                            if (item == currentProjectile.getItem()) {
                                if (data.emptyWithoutHotbar != 0 || inventoryIndex >= HOTBAR_SIZE) {
                                    this.normalSortClick(inventoryIndex, 0, 1);
                                    return true;
                                }
                            } else if (itemStack.size > currentProjectile.size) {
                                this.normalSortClick(inventoryIndex, targetSlot, 2);
                                return true;
                            }
                        }
                    }
                } else if (item instanceof PotionItem) {
                    int targetSlot = this.configuredHotbarSlot(this.speedSlot);
                    if (targetSlot >= 0
                            && inventoryIndex != targetSlot
                            && this.isSpeedPotion(itemStack)
                            && !this.isSpeedPotion(data.inventory.getItem(targetSlot))) {
                        this.normalSortClick(inventoryIndex, targetSlot, 2);
                        return true;
                    }
                } else if (item instanceof EnderPearlItem) {
                    int targetSlot = this.configuredHotbarSlot(this.pearlSlot);
                    if (targetSlot >= 0 && inventoryIndex != targetSlot) {
                        ItemStack currentPearl = data.inventory.getItem(targetSlot);
                        if (currentPearl == null || !(currentPearl.getItem() instanceof EnderPearlItem)) {
                            this.normalSortClick(inventoryIndex, targetSlot, 2);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean fixNormalSwordSlot(InvManager.InventoryData data) {
        int targetSlot = this.configuredHotbarSlot(this.swordSlot);
        if (targetSlot < 0) {
            return false;
        }

        ItemStack targetStack = data.inventory.getItem(targetSlot);
        double bestDamage = this.isSword(targetStack) ? Utils.getDamageLevel(targetStack) : -1.0;
        int bestInventoryIndex = -1;

        for (int inventoryIndex = 0; inventoryIndex < data.size; inventoryIndex++) {
            if (inventoryIndex != targetSlot) {
                ItemStack candidate = data.inventory.getItem(inventoryIndex);
                if (this.isSword(candidate)) {
                    double damage = Utils.getDamageLevel(candidate);
                    if (damage > bestDamage) {
                        bestDamage = damage;
                        bestInventoryIndex = inventoryIndex;
                    }
                }
            }
        }

        if (bestInventoryIndex < 0) {
            return false;
        }

        this.normalSortClick(bestInventoryIndex, targetSlot, 2);
        return true;
    }

    private int configuredHotbarSlot(SliderSetting setting) {
        int slot = (int) setting.getInput() - 1;
        return setting != this.swordSlot && slot == (int) this.swordSlot.getInput() - 1 ? -1 : slot;
    }

    private void normalSortClick(int inventoryIndex, int button, int mode) {
        this.delayedClick(toContainerSlot(inventoryIndex), button, mode, this.normalSortDelay);
    }

    private boolean runInventorySorterTick() {
        InvManager.InventorySnapshot snapshot = InvManager.InventorySnapshot.capture();
        if (!this.sessionOpen) {
            this.openSession(snapshot);
        }

        if (this.sessionState == InvManager.SessionState.COMPLETE_LATCHED) {
            return false;
        }

        InvManager.SlotAssignment[] assignments = this.resolveAssignments(snapshot);
        InvManager.SnapshotContext context = InvManager.SnapshotContext.create(snapshot, assignments);
        if (this.currentAction == null) {
            if (snapshot.carried != null) {
                this.currentAction = this.buildRecoveryAction(context, InvManager.PlanType.RECOVER_CURSOR, false);
                if (this.currentAction == null) {
                    this.sessionState = InvManager.SessionState.ACTIVE;
                    return true;
                }

                this.sessionState = InvManager.SessionState.EXECUTING;
            } else {
                this.cursorRecoveryInventoryIndex = -1;
                this.currentAction = this.findBestSortingAction(context);
                if (this.currentAction == null) {
                    this.sessionState = this.disableWhenComplete.isToggled()
                            ? InvManager.SessionState.COMPLETE_LATCHED
                            : InvManager.SessionState.ACTIVE;
                    return false;
                }

                this.sessionState = InvManager.SessionState.EXECUTING;
            }
        }

        int clickBudget = this.consumeWindowClickBudget();
        if (clickBudget <= 0) {
            return true;
        } else {
            return this.executeCurrentStep(context)
                    ? true
                    : this.currentAction != null || context.snapshot.carried != null;
        }
    }

    private boolean tryAutoArmorAction() {
        if (autoArmor.getInput() != -1.0 && mc.player.inventory.getCursorItem() == null) {
            this.updateCurrentArmor();
            InvManager.InventoryData data = new InvManager.InventoryData(mc.player.inventory, true, true);

            for (int i = 0; i < data.armorData[0].length; i++) {
                if (data.armorData[1][i] != -1) {
                    InvManager.CurrentArmor currentArmor = this.armorArr[i];
                    if (data.armorData[1][i] > currentArmor.defenceLevel) {
                        if (currentArmor.getItemStack() != null) {
                            this.delayedClick(currentArmor.invSlot, 0, 4, autoArmor);
                            return true;
                        }

                        this.delayedClick(toContainerSlot(data.armorData[0][i]), 0, 1, autoArmor);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean tryInventoryCleanerAction() {
        if (inventoryCleaner.getInput() != -1.0 && mc.player.inventory.getCursorItem() == null) {
            if (this.cleanKey.getKey() != 0 && !this.cleanKey.isPressed()) {
                return false;
            }

            this.updateCurrentArmor();
            InvManager.InventoryData data = new InvManager.InventoryData(mc.player.inventory, true, false);
            int[] bestArmorLevels = new int[4];
            boolean[] keptBestArmor = new boolean[4];

            for (int idx = 0; idx < bestArmorLevels.length; idx++) {
                bestArmorLevels[idx] = Math.max(this.armorArr[idx].defenceLevel, data.armorData[1][idx]);
                if (this.armorArr[idx].defenceLevel == bestArmorLevels[idx] && bestArmorLevels[idx] > 0) {
                    keptBestArmor[idx] = true;
                }
            }

            List<Integer> duplicateItems = new ArrayList<>();
            int keptSwordIndex = (int) data.swordData[0];
            int preferredSwordSlot = this.configuredHotbarSlot(this.swordSlot);
            if (this.isAutoSortMode(0) && preferredSwordSlot >= 0) {
                ItemStack preferredSword = data.inventory.getItem(preferredSwordSlot);
                if (this.isSword(preferredSword) && Utils.getDamageLevel(preferredSword) == data.bestSwordDamage) {
                    keptSwordIndex = preferredSwordSlot;
                }
            }

            boolean keptBestPick = false;
            boolean keptBestAxe = false;
            boolean keptBestShovel = false;
            int j = 0;

            while (j < data.size) {
                int slotId;
                label123:
                {
                    ItemStack itemStack = data.inventory.getItem(j);
                    if (itemStack != null) {
                        Item item = itemStack.getItem();
                        slotId = toContainerSlot(j);
                        if (this.isSword(itemStack)) {
                            if (j != keptSwordIndex) {
                                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                return true;
                            }
                        } else if (this.isArmor(itemStack)) {
                            ArmorItem armor = (ArmorItem) item;
                            int armorSlot = 3 - armor.slot;
                            int defenceLevel = this.getDefenceLevel(itemStack);
                            int bestLevel = bestArmorLevels[armorSlot];
                            if (defenceLevel < bestLevel) {
                                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                return true;
                            }

                            if (defenceLevel == bestLevel) {
                                if (keptBestArmor[armorSlot]) {
                                    this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                    return true;
                                }

                                keptBestArmor[armorSlot] = true;
                            } else {
                                bestArmorLevels[armorSlot] = defenceLevel;
                                keptBestArmor[armorSlot] = true;
                            }
                        } else if (item instanceof PickaxeItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.STONE);
                            if (efficiency < data.bestPickaxe || keptBestPick && efficiency == data.bestPickaxe) {
                                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                return true;
                            }

                            keptBestPick = true;
                        } else if (item instanceof AxeItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.LOG);
                            if (efficiency < data.bestAxe || keptBestAxe && efficiency == data.bestAxe) {
                                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                return true;
                            }

                            keptBestAxe = true;
                        } else if (item instanceof ShovelItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.DIRT);
                            if (efficiency < data.bestShovel || keptBestShovel && efficiency == data.bestShovel) {
                                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                                return true;
                            }

                            keptBestShovel = true;
                        } else if (!this.isProtectedInventoryItem(itemStack) && item != Items.SPAWN_EGG) {
                            if (item instanceof PotionItem) {
                                if (this.isBadPotion(itemStack)) {
                                    break label123;
                                }
                            } else {
                                if (item instanceof MusicDiscItem || itemStack.getMaxSize() != 1) {
                                    break label123;
                                }

                                int id = Item.getId(item);
                                if (duplicateItems.contains(id)) {
                                    break label123;
                                }

                                duplicateItems.add(id);
                            }
                        }
                    }

                    j++;
                    continue;
                }

                this.delayedClick(slotId, 1, 4, inventoryCleaner);
                return true;
            }

            return false;
        } else {
            return false;
        }
    }

    private void handleChestScreen() {
        if (chestStealer.getInput() != -1.0
                && mc.player.menu instanceof ChestMenu
                && this.populatedChest.get() == mc.player.menu) {
            Inventory chestInventory = ((ChestMenu) mc.player.menu).getChest();
            if (this.stealFromCustomChests.isToggled()
                    || chestInventory.getName().contains("Chest")) {
                this.updateCurrentArmor();
                InvManager.InventoryData chestData = new InvManager.InventoryData(chestInventory, false, false);
                InvManager.InventoryData playerData = new InvManager.InventoryData(mc.player.inventory, true, false);
                chestData.compareAndRemove(playerData);
                if (!this.isAutoSortMode(1)) {
                    this.handleNormalChestStealer(chestData, playerData);
                } else {
                    if (playerData.size != playerData.filled) {
                        if (chestData.swordData[1] != -1.0) {
                            this.stealChestItem(
                                    (int) chestData.swordData[0],
                                    chestData.inventory.getItem((int) chestData.swordData[0]),
                                    playerData);
                            return;
                        }

                        for (int k = 0; k < chestData.armorData[0].length; k++) {
                            if (chestData.armorData[1][k] != -1) {
                                InvManager.CurrentArmor currentArmor = this.armorArr[k];
                                if (chestData.armorData[1][k] > currentArmor.defenceLevel) {
                                    int chestSlot = chestData.armorData[0][k];
                                    this.stealChestItem(chestSlot, chestData.inventory.getItem(chestSlot), playerData);
                                    return;
                                }
                            }
                        }

                        for (int k = 0; k < chestData.size; k++) {
                            ItemStack itemStack = chestData.inventory.getItem(k);
                            if (itemStack != null
                                    && !this.isSword(itemStack)
                                    && !this.isArmor(itemStack)
                                    && !this.shouldSkipChestItem(itemStack, playerData)) {
                                this.stealChestItem(k, itemStack, playerData);
                                return;
                            }
                        }
                    } else {
                        for (int k = 0; k < chestData.size; k++) {
                            ItemStack chestStack = chestData.inventory.getItem(k);
                            if (chestStack != null
                                    && !this.shouldSkipChestItem(chestStack, playerData)
                                    && this.canMergeChestStackIntoInventory(chestStack, playerData)) {
                                this.stealChestItem(k, chestStack, playerData);
                                return;
                            }
                        }
                    }

                    this.finishChestScan();
                }
            }
        }
    }

    private void handleNormalChestStealer(InvManager.InventoryData chestData, InvManager.InventoryData playerData) {
        if (playerData.size != playerData.filled) {
            if (this.stealBestSwordToNormalSlot(chestData, playerData)) {
                return;
            }

            for (int armorIndex = 0; armorIndex < chestData.armorData[0].length; armorIndex++) {
                if (chestData.armorData[1][armorIndex] != -1) {
                    InvManager.CurrentArmor currentArmor = this.armorArr[armorIndex];
                    if (chestData.armorData[1][armorIndex] > currentArmor.defenceLevel) {
                        this.normalChestClick(chestData.armorData[0][armorIndex], 0, 1);
                        return;
                    }
                }
            }

            for (int chestSlot = 0; chestSlot < chestData.size; chestSlot++) {
                ItemStack chestStack = chestData.inventory.getItem(chestSlot);
                if (chestStack != null
                        && !this.isSword(chestStack)
                        && !this.isArmor(chestStack)
                        && !this.shouldSkipChestItem(chestStack, playerData)) {
                    Item item = chestStack.getItem();
                    if (isSortableBlock(chestStack)) {
                        int targetSlot = this.configuredHotbarSlot(this.blocksSlot);
                        if (targetSlot < 0) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }

                        ItemStack currentBlocks = playerData.inventory.getItem(targetSlot);
                        if (!isSortableBlock(currentBlocks)
                                || ((BlockItem) item).getBlock() != ((BlockItem) currentBlocks.getItem()).getBlock()
                                        && chestStack.size > currentBlocks.size) {
                            this.normalChestClick(chestSlot, targetSlot, 2);
                            return;
                        }
                    } else if (item instanceof GoldenAppleItem) {
                        int targetSlot = this.configuredHotbarSlot(this.goldenAppleSlot);
                        if (targetSlot < 0) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }

                        ItemStack currentGoldenApple = playerData.inventory.getItem(targetSlot);
                        if (currentGoldenApple == null || !(currentGoldenApple.getItem() instanceof GoldenAppleItem)) {
                            this.normalChestClick(chestSlot, targetSlot, 2);
                            return;
                        }
                    } else if (item instanceof SnowballItem || item instanceof EggItem) {
                        int targetSlot = this.configuredHotbarSlot(this.projectileSlot);
                        if (targetSlot < 0) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }

                        ItemStack currentProjectile = playerData.inventory.getItem(targetSlot);
                        if (currentProjectile == null
                                || !(currentProjectile.getItem() instanceof SnowballItem)
                                        && !(currentProjectile.getItem() instanceof EggItem)) {
                            this.normalChestClick(chestSlot, targetSlot, 2);
                            return;
                        }

                        if (chestStack.size > currentProjectile.size) {
                            if (item == currentProjectile.getItem() && chestStack.size <= chestStack.getMaxSize()) {
                                this.normalChestClick(chestSlot, 0, 1);
                            } else {
                                this.normalChestClick(chestSlot, targetSlot, 2);
                            }

                            return;
                        }
                    } else if (item instanceof PotionItem && this.isSpeedPotion(chestStack)) {
                        int targetSlot = this.configuredHotbarSlot(this.speedSlot);
                        if (targetSlot < 0) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }

                        if (!this.isSpeedPotion(playerData.inventory.getItem(targetSlot))) {
                            this.normalChestClick(chestSlot, targetSlot, 2);
                            return;
                        }
                    } else if (item instanceof EnderPearlItem) {
                        int targetSlot = this.configuredHotbarSlot(this.pearlSlot);
                        if (targetSlot < 0) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }

                        ItemStack currentPearl = playerData.inventory.getItem(targetSlot);
                        if (currentPearl == null || !(currentPearl.getItem() instanceof EnderPearlItem)) {
                            this.normalChestClick(chestSlot, targetSlot, 2);
                            return;
                        }
                    }

                    this.normalChestClick(chestSlot, 0, 1);
                    return;
                }
            }
        } else {
            for (int chestSlot = 0; chestSlot < chestData.size; chestSlot++) {
                ItemStack chestStack = chestData.inventory.getItem(chestSlot);
                if (chestStack != null
                        && !this.shouldSkipChestItem(chestStack, playerData)
                        && this.isNormalMergeItem(chestStack)) {
                    for (int inventoryIndex = 0; inventoryIndex < playerData.size; inventoryIndex++) {
                        ItemStack inventoryStack = playerData.inventory.getItem(inventoryIndex);
                        if (inventoryStack != null
                                && canStacksMerge(chestStack, inventoryStack)
                                && inventoryStack.size < inventoryStack.getMaxSize()) {
                            this.normalChestClick(chestSlot, 0, 1);
                            return;
                        }
                    }
                }
            }
        }

        this.finishChestScan();
    }

    private void finishChestScan() {
        if (this.closeChest.isToggled() && this.pendingTimedClicks.isEmpty()) {
            if (this.chestPendingClose == mc.player.menu) {
                this.chestPendingClose = null;
                mc.player.closeMenu();
            } else {
                this.chestPendingClose = (ChestMenu) mc.player.menu;
            }
        }
    }

    private boolean stealBestSwordToNormalSlot(
            InvManager.InventoryData chestData, InvManager.InventoryData playerData) {
        int targetSlot = this.configuredHotbarSlot(this.swordSlot);
        if (chestData.swordData[1] == -1.0) {
            return false;
        }

        if (targetSlot < 0) {
            if (chestData.swordData[1] <= playerData.bestSwordDamage) {
                return false;
            }

            this.normalChestClick((int) chestData.swordData[0], 0, 1);
            return true;
        } else {
            ItemStack currentSword = playerData.inventory.getItem(targetSlot);
            double currentDamage = this.isSword(currentSword) ? Utils.getDamageLevel(currentSword) : -1.0;
            if (chestData.swordData[1] <= currentDamage) {
                return false;
            }

            this.normalChestClick((int) chestData.swordData[0], targetSlot, 2);
            return true;
        }
    }

    private static boolean isTnt(ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof BlockItem
                && ((BlockItem) stack.getItem()).getBlock() == Blocks.TNT;
    }

    private static boolean isSortableBlock(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem && !isTnt(stack);
    }

    private static boolean matchesSortItem(String storageId, ItemStack stack) {
        return !isTnt(stack) && ItemSearchIndex.matches(storageId, stack);
    }

    private boolean isNormalMergeItem(ItemStack stack) {
        Item item = stack.getItem();
        return isSortableBlock(stack)
                || item instanceof GoldenAppleItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderPearlItem;
    }

    private void normalChestClick(int chestSlot, int button, int mode) {
        this.delayedClick(chestSlot, button, mode, chestStealer);
    }

    private void stealChestItem(int chestSlot, ItemStack stack, InvManager.InventoryData playerData) {
        int targetHotbarSlot = this.getAssignedHotbarSlot(stack);
        if (targetHotbarSlot >= 0 && playerData.inventory.getItem(targetHotbarSlot) == null) {
            this.delayedClick(chestSlot, targetHotbarSlot, 2, chestStealer);
        } else {
            this.delayedClick(chestSlot, 0, 1, chestStealer);
        }
    }

    private int getAssignedHotbarSlot(ItemStack stack) {
        if (this.isAutoSortMode(1) && stack != null) {
            for (String storageId : this.items.getItems()) {
                if (matchesSortItem(storageId, stack)) {
                    Integer assignedSlot = this.items.getAssignedSlot(storageId);
                    if (assignedSlot == null) {
                        return -1;
                    }

                    int hotbarSlot = assignedSlot - 1;
                    return hotbarSlot >= 0 && hotbarSlot < HOTBAR_SIZE ? hotbarSlot : -1;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    private void delayedClick(int slotId, int button, int mode, SliderSetting delaySlider) {
        InventoryMenu container = mc.player.menu;
        ItemStack sourceBeforeClick = null;
        this.remainingDelay = (int) delaySlider.getInput();
        if (this.remainingDelay == 0) {
            ItemStack source = container.getSlot(slotId).getItem();
            sourceBeforeClick = source != null ? source.copy() : null;
            if (this.instantClickSources.containsKey(slotId)
                    && ItemStack.matches(this.instantClickSources.get(slotId), sourceBeforeClick)) {
                return;
            }

            this.instantClickSources.put(slotId, sourceBeforeClick);
        }

        this.chestPendingClose = null;
        this.resetCustomSorter();
        this.lastTimedClickWasCleaner = delaySlider == inventoryCleaner;
        this.sendingTimedClick = true;

        try {
            this.click(slotId, button, mode);
        } finally {
            this.sendingTimedClick = false;
        }

        this.instantClickChangedSlot = this.remainingDelay == 0
                && !ItemStack.matches(
                        sourceBeforeClick, container.getSlot(slotId).getItem());
    }

    private boolean isProtectedInventoryItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        Item item = itemStack.getItem();
        return this.isAutoSortMode(1) && this.isManagedStack(itemStack)
                || isSortableBlock(itemStack) && !(((BlockItem) item).getBlock() instanceof AbstractRailBlock)
                || item instanceof GoldenAppleItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderPearlItem
                || item == Items.ARROW;
    }

    private boolean canMergeChestStackIntoInventory(ItemStack chestStack, InvManager.InventoryData playerData) {
        Item chestItem = chestStack.getItem();
        if (this.isProtectedInventoryItem(chestStack)
                || chestItem instanceof PotionItem && !this.isBadPotion(chestStack)) {
            for (int inventoryIndex = 0; inventoryIndex < playerData.size; inventoryIndex++) {
                ItemStack playerStack = playerData.inventory.getItem(inventoryIndex);
                if (playerStack != null
                        && canStacksMerge(playerStack, chestStack)
                        && playerStack.size < playerStack.getMaxSize()) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isSword(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof SwordItem;
    }

    private boolean isArmor(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof ArmorItem;
    }

    private int getDefenceLevel(ItemStack itemStack) {
        return ((ArmorItem) itemStack.getItem()).protection
                + EnchantmentHelper.modifyProtection(new ItemStack[] {itemStack}, DamageSource.GENERIC);
    }

    private void updateCurrentArmor() {
        for (InvManager.CurrentArmor armor : this.armorArr) {
            ItemStack itemStack = armor.getItemStack();
            armor.defenceLevel = this.isArmor(itemStack) ? this.getDefenceLevel(itemStack) : 0;
        }
    }

    private boolean isSpeedPotion(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof PotionItem) {
            List<StatusEffectInstance> effects = ((PotionItem) itemStack.getItem()).getPotionEffects(itemStack);
            if (effects == null) {
                return false;
            }

            for (StatusEffectInstance effect : effects) {
                if (effect.toString().contains("moveSpeed")) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isBadPotion(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof PotionItem) {
            List<StatusEffectInstance> effects = ((PotionItem) itemStack.getItem()).getPotionEffects(itemStack);
            if (effects != null && !effects.isEmpty()) {
                for (StatusEffectInstance effect : effects) {
                    String desc = effect.toString();
                    if (desc.contains("poison")
                            || desc.contains("moveSlowdown")
                            || desc.contains("weakness")
                            || desc.contains("harm")
                            || desc.contains("digSlowDown")) {
                        return true;
                    }
                }

                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean shouldSkipChestItem(ItemStack stack, InvManager.InventoryData playerData) {
        if (isTnt(stack)) {
            return true;
        }

        Item item = stack.getItem();
        if (this.isAutoSortMode(1) && this.isManagedStack(stack)) {
            return false;
        }

        if (item instanceof PotionItem && this.isBadPotion(stack)) {
            return true;
        }

        if (item == Items.SPAWN_EGG || item instanceof MusicDiscItem) {
            return true;
        }

        if (item instanceof PickaxeItem) {
            double efficiency = Utils.getEfficiency(stack, Blocks.STONE);
            return playerData.bestPickaxe >= 0.0 && efficiency <= playerData.bestPickaxe;
        }

        if (item instanceof AxeItem) {
            double efficiency = Utils.getEfficiency(stack, Blocks.LOG);
            return playerData.bestAxe >= 0.0 && efficiency <= playerData.bestAxe;
        }

        if (item instanceof ShovelItem) {
            double efficiency = Utils.getEfficiency(stack, Blocks.DIRT);
            return playerData.bestShovel >= 0.0 && efficiency <= playerData.bestShovel;
        }

        if (!this.isProtectedInventoryItem(stack) && (!(item instanceof PotionItem) || this.isBadPotion(stack))) {
            if (stack.getMaxSize() == 1) {
                int id = Item.getId(item);
                return playerData.uniqueSingles.contains(id);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean shouldCancelManualInventoryInput() {
        return this.isEnabled()
                && this.isAutoSortMode(1)
                && this.isManagedInventoryOpen()
                && this.sessionState == InvManager.SessionState.EXECUTING
                && this.currentAction != null;
    }

    public boolean isSendingInventoryClick() {
        return this.sendingInventoryClick;
    }

    private void openSession(InvManager.InventorySnapshot snapshot) {
        this.sessionOpen = true;
        this.currentAction = null;
        this.cursorRecoveryInventoryIndex = -1;
        this.windowClickBudget = 0.0;
        this.sessionState = InvManager.SessionState.ACTIVE;
        if (this.disableWhenComplete.isToggled()) {
            InvManager.SnapshotContext context =
                    InvManager.SnapshotContext.create(snapshot, this.resolveAssignments(snapshot));
            if (this.findBestSortingAction(context) == null) {
                this.sessionState = InvManager.SessionState.COMPLETE_LATCHED;
            }
        }
    }

    private void closeSession() {
        this.activeContainer = null;
        this.pendingSortClick = null;
        this.pendingTimedClicks.clear();
        this.awaitingSlots = false;
        this.awaitingCursor = false;
        this.resetCustomSorter();
    }

    private void resetCustomSorter() {
        this.sessionOpen = false;
        this.currentAction = null;
        this.cursorRecoveryInventoryIndex = -1;
        this.windowClickBudget = 0.0;
        this.sessionState = InvManager.SessionState.ACTIVE;
    }

    private boolean ownsCarriedStack() {
        return this.currentAction != null || this.cursorRecoveryInventoryIndex >= 0;
    }

    private int consumeWindowClickBudget() {
        this.windowClickBudget = this.windowClickBudget + Math.max(0.0, this.targetCPS.getInput()) / 20.0;
        int clicks = Math.min(1, (int) this.windowClickBudget);
        if (clicks <= 0) {
            return 0;
        }

        this.windowClickBudget -= clicks;
        this.windowClickBudget = Math.min(this.windowClickBudget, 1.0);
        return clicks;
    }

    private boolean executeCurrentStep(InvManager.SnapshotContext context) {
        if (this.currentAction == null) {
            return false;
        }

        InvManager.PlannedClick step = this.currentAction.peek();
        if (step == null) {
            this.currentAction = null;
            this.sessionState = InvManager.SessionState.ACTIVE;
            return false;
        }

        if (!step.validator.isValid(context, this)) {
            this.currentAction = null;
            this.sessionState = InvManager.SessionState.ACTIVE;
            if (context.snapshot.carried != null && this.cursorRecoveryInventoryIndex >= 0) {
                InvManager.PlannedAction recoveryAction =
                        this.buildRecoveryAction(context, InvManager.PlanType.RECOVER_CURSOR, false);
                if (recoveryAction != null) {
                    this.currentAction = recoveryAction;
                    this.sessionState = InvManager.SessionState.EXECUTING;
                    return this.executeCurrentStep(context);
                }
            }

            if (context.snapshot.carried == null) {
                this.cursorRecoveryInventoryIndex = -1;
            }

            return false;
        } else {
            step.beforeExecute.run(this);
            this.click(step.slotId, step.button, step.mode);
            step.afterExecute.run(this);
            this.currentAction.advance();
            if (this.currentAction.isComplete()) {
                this.currentAction = null;
                if (this.sessionState != InvManager.SessionState.COMPLETE_LATCHED) {
                    this.sessionState = InvManager.SessionState.ACTIVE;
                }
            }

            return true;
        }
    }

    private InvManager.PlannedAction findBestSortingAction(InvManager.SnapshotContext context) {
        InvManager.PlannedAction bestAction = null;

        for (InvManager.SlotAssignment assignment : context.assignments) {
            if (assignment != null) {
                ItemStack targetStack = context.snapshot.getSlot(assignment.hotbarSlot);
                boolean targetMatchesRule = this.isAssignedTargetSatisfied(context, assignment, targetStack);
                if (targetMatchesRule && isPartialStack(targetStack)) {
                    InvManager.PlannedAction mergeAction = this.buildMergeAction(context, assignment, targetStack);
                    bestAction = pickBetterAction(bestAction, mergeAction);
                    InvManager.PlannedAction pickupAllAction =
                            this.buildPickupAllAction(context, assignment, targetStack);
                    bestAction = pickBetterAction(bestAction, pickupAllAction);
                } else if (!targetMatchesRule) {
                    InvManager.PlannedAction placementAction = this.buildPlacementAction(context, assignment);
                    InvManager.PlannedAction pickupAllPlacementAction =
                            this.buildPickupAllPlacementAction(context, assignment, targetStack);
                    if (pickupAllPlacementAction == null
                            || placementAction != null
                                    && pickupAllPlacementAction.resultingSize <= placementAction.resultingSize) {
                        if (placementAction != null) {
                            bestAction = pickBetterAction(bestAction, placementAction);
                        }
                    } else {
                        bestAction = pickBetterAction(bestAction, pickupAllPlacementAction);
                    }
                }
            }
        }

        return bestAction == null && this.stackItems.isToggled()
                ? this.buildUnconfiguredStackAction(context)
                : bestAction;
    }

    private InvManager.PlannedAction buildPlacementAction(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment) {
        InvManager.PlacementSource bestSource =
                this.findBestPlacementSource(context, assignment, assignment.hotbarSlot);
        if (bestSource == null) {
            return null;
        }

        final int sourceInventoryIndex = bestSource.inventoryIndex;
        final InvManager.SlotAssignment selectedAssignment = assignment;
        List<InvManager.PlannedClick> steps = new ArrayList<>(1);
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                selectedAssignment.hotbarSlot,
                2,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried == null
                                && InvManager.matchesSortItem(
                                        selectedAssignment.storageId, current.snapshot.getSlot(sourceInventoryIndex))
                                && (sourceInventoryIndex >= InvManager.HOTBAR_SIZE
                                        || !module.isCorrectHotbarSlot(current, sourceInventoryIndex));
                    }
                },
                NO_OP,
                NO_OP));
        return new InvManager.PlannedAction(
                InvManager.PlanType.PLACE_TO_HOTBAR,
                1,
                assignment.hotbarSlot,
                assignment.priorityIndex,
                bestSource.resultingSize,
                true,
                0,
                sourceInventoryIndex,
                steps);
    }

    private InvManager.PlannedAction buildMergeAction(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, ItemStack targetStack) {
        InvManager.MergePlanData mergePlan = this.buildMergePlanData(context, assignment, targetStack);
        if (mergePlan == null) {
            return null;
        }

        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        List<InvManager.PlannedClick> steps = new ArrayList<>(mergePlan.clickCount);

        for (InvManager.MergeUse mergeUse : mergePlan.uses) {
            final int sourceInventoryIndex = mergeUse.source.inventoryIndex;
            if (mergeUse.source.type == InvManager.MergeSourceType.MAIN_INVENTORY) {
                steps.add(new InvManager.PlannedClick(
                        toContainerSlot(sourceInventoryIndex),
                        0,
                        1,
                        new InvManager.StepValidator() {
                            @Override
                            public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                                ItemStack sourceStack = current.snapshot.getSlot(sourceInventoryIndex);
                                ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                                return current.snapshot.carried == null
                                        && InvManager.matchesSortItem(storageId, currentTarget)
                                        && InvManager.canStacksMerge(sourceStack, currentTarget)
                                        && InvManager.isPartialStack(currentTarget)
                                        && InvManager.isQuickMoveMergeSafe(
                                                current.snapshot, targetHotbarSlot, sourceStack, currentTarget);
                            }
                        },
                        NO_OP,
                        NO_OP));
            } else {
                steps.add(new InvManager.PlannedClick(
                        toContainerSlot(sourceInventoryIndex),
                        0,
                        0,
                        new InvManager.StepValidator() {
                            @Override
                            public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                                ItemStack sourceStack = current.snapshot.getSlot(sourceInventoryIndex);
                                ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                                return current.snapshot.carried == null
                                        && InvManager.matchesSortItem(storageId, currentTarget)
                                        && InvManager.canStacksMerge(sourceStack, currentTarget)
                                        && InvManager.isPartialStack(currentTarget)
                                        && !module.isCorrectHotbarSlot(current, sourceInventoryIndex);
                            }
                        },
                        new InvManager.StepHook() {
                            @Override
                            public void run(InvManager module) {
                                module.cursorRecoveryInventoryIndex = sourceInventoryIndex;
                            }
                        },
                        NO_OP));
                steps.add(new InvManager.PlannedClick(
                        toContainerSlot(targetHotbarSlot),
                        0,
                        0,
                        new InvManager.StepValidator() {
                            @Override
                            public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                                ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                                return current.snapshot.carried != null
                                        && InvManager.matchesSortItem(storageId, currentTarget)
                                        && InvManager.canStacksMerge(current.snapshot.carried, currentTarget)
                                        && InvManager.isPartialStack(currentTarget);
                            }
                        },
                        NO_OP,
                        mergeUse.returnsRemainder
                                ? NO_OP
                                : new InvManager.StepHook() {
                                    @Override
                                    public void run(InvManager module) {
                                        module.cursorRecoveryInventoryIndex = -1;
                                    }
                                }));
                if (mergeUse.returnsRemainder) {
                    steps.add(new InvManager.PlannedClick(
                            toContainerSlot(sourceInventoryIndex),
                            0,
                            0,
                            new InvManager.StepValidator() {
                                @Override
                                public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                                    return current.snapshot.carried != null
                                            && InvManager.canDepositToSlot(current.snapshot, sourceInventoryIndex);
                                }
                            },
                            NO_OP,
                            new InvManager.StepHook() {
                                @Override
                                public void run(InvManager module) {
                                    module.cursorRecoveryInventoryIndex = -1;
                                }
                            }));
                }
            }
        }

        return new InvManager.PlannedAction(
                InvManager.PlanType.MERGE_TO_TARGET,
                mergePlan.clickCount,
                assignment.hotbarSlot,
                assignment.priorityIndex,
                mergePlan.resultingSize,
                mergePlan.resultingSize >= targetStack.getMaxSize(),
                0,
                mergePlan.primarySourceIndex,
                steps);
    }

    private InvManager.PlannedAction buildPickupAllAction(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, ItemStack targetStack) {
        int resultingSize = this.getPickupAllResultingSize(context, assignment.hotbarSlot, targetStack);
        if (resultingSize <= targetStack.size) {
            return null;
        }

        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        List<InvManager.PlannedClick> steps = new ArrayList<>(3);
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(targetHotbarSlot),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                        return current.snapshot.carried == null
                                && InvManager.matchesSortItem(storageId, currentTarget)
                                && InvManager.isPartialStack(currentTarget)
                                && module.getPickupAllResultingSize(current, targetHotbarSlot, currentTarget)
                                        > currentTarget.size;
                    }
                },
                new InvManager.StepHook() {
                    @Override
                    public void run(InvManager module) {
                        module.cursorRecoveryInventoryIndex = targetHotbarSlot;
                    }
                },
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(targetHotbarSlot),
                0,
                6,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried != null
                                && current.snapshot.getSlot(targetHotbarSlot) == null
                                && module.getPickupAllResultingSize(current, targetHotbarSlot, current.snapshot.carried)
                                        > current.snapshot.carried.size;
                    }
                },
                NO_OP,
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(targetHotbarSlot),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried != null
                                && InvManager.canDepositToSlot(current.snapshot, targetHotbarSlot);
                    }
                },
                NO_OP,
                new InvManager.StepHook() {
                    @Override
                    public void run(InvManager module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                }));
        return new InvManager.PlannedAction(
                InvManager.PlanType.PICKUP_ALL_TO_TARGET,
                3,
                assignment.hotbarSlot,
                assignment.priorityIndex,
                resultingSize,
                resultingSize >= targetStack.getMaxSize(),
                0,
                assignment.hotbarSlot,
                steps);
    }

    private InvManager.PlannedAction buildPickupAllPlacementAction(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, ItemStack targetStack) {
        InvManager.PlacementSource bestSource = null;

        for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
            if (inventoryIndex != assignment.hotbarSlot) {
                ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
                if (matchesSortItem(assignment.storageId, sourceStack)
                        && (inventoryIndex >= HOTBAR_SIZE || !this.isCorrectHotbarSlot(context, inventoryIndex))) {
                    int resultingSize = this.getPickupAllResultingSize(context, inventoryIndex, sourceStack);
                    if (resultingSize > sourceStack.size) {
                        InvManager.PlacementSource candidate = new InvManager.PlacementSource(
                                inventoryIndex,
                                ItemSearchIndex.getMatchQuality(assignment.storageId, sourceStack),
                                inventoryIndex < HOTBAR_SIZE ? 1 : 0,
                                resultingSize);
                        if (bestSource == null || candidate.isBetterThan(bestSource)) {
                            bestSource = candidate;
                        }
                    }
                }
            }
        }

        if (bestSource == null) {
            return null;
        }

        final int sourceInventoryIndex = bestSource.inventoryIndex;
        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        boolean targetOccupiedByDifferentItem = targetStack != null && !matchesSortItem(storageId, targetStack);
        List<InvManager.PlannedClick> steps = new ArrayList<>(targetOccupiedByDifferentItem ? 4 : 3);
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried == null
                                && InvManager.matchesSortItem(storageId, current.snapshot.getSlot(sourceInventoryIndex))
                                && (sourceInventoryIndex >= InvManager.HOTBAR_SIZE
                                        || !module.isCorrectHotbarSlot(current, sourceInventoryIndex));
                    }
                },
                new InvManager.StepHook() {
                    @Override
                    public void run(InvManager module) {
                        module.cursorRecoveryInventoryIndex = sourceInventoryIndex;
                    }
                },
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                0,
                6,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried != null
                                && InvManager.matchesSortItem(storageId, current.snapshot.carried)
                                && current.snapshot.getSlot(sourceInventoryIndex) == null
                                && module.getPickupAllResultingSize(
                                                current, sourceInventoryIndex, current.snapshot.carried)
                                        > current.snapshot.carried.size;
                    }
                },
                NO_OP,
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(targetHotbarSlot),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        if (current.snapshot.carried != null
                                && InvManager.matchesSortItem(storageId, current.snapshot.carried)) {
                            ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                            if (currentTarget == null) {
                                return true;
                            } else {
                                return !InvManager.matchesSortItem(storageId, currentTarget)
                                        ? true
                                        : InvManager.canStacksMerge(current.snapshot.carried, currentTarget)
                                                && InvManager.isPartialStack(currentTarget);
                            }
                        } else {
                            return false;
                        }
                    }
                },
                NO_OP,
                targetOccupiedByDifferentItem
                        ? NO_OP
                        : new InvManager.StepHook() {
                            @Override
                            public void run(InvManager module) {
                                module.cursorRecoveryInventoryIndex = -1;
                            }
                        }));
        if (targetOccupiedByDifferentItem) {
            steps.add(new InvManager.PlannedClick(
                    toContainerSlot(sourceInventoryIndex),
                    0,
                    0,
                    new InvManager.StepValidator() {
                        @Override
                        public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                            return current.snapshot.carried != null
                                    && InvManager.canDepositToSlot(current.snapshot, sourceInventoryIndex);
                        }
                    },
                    NO_OP,
                    new InvManager.StepHook() {
                        @Override
                        public void run(InvManager module) {
                            module.cursorRecoveryInventoryIndex = -1;
                        }
                    }));
        }

        return new InvManager.PlannedAction(
                InvManager.PlanType.PICKUP_ALL_TO_TARGET,
                steps.size(),
                assignment.hotbarSlot,
                assignment.priorityIndex,
                bestSource.resultingSize,
                bestSource.resultingSize
                        >= context.snapshot.getSlot(sourceInventoryIndex).getMaxSize(),
                0,
                sourceInventoryIndex,
                steps);
    }

    private InvManager.PlannedAction buildUnconfiguredStackAction(InvManager.SnapshotContext context) {
        InvManager.PlannedAction bestAction = null;

        for (int targetInventoryIndex = 0; targetInventoryIndex < 36; targetInventoryIndex++) {
            ItemStack targetStack = context.snapshot.getSlot(targetInventoryIndex);
            if (isPartialStack(targetStack) && !this.isManagedStack(targetStack)) {
                InvManager.PlannedAction pickupAllAction =
                        this.buildUnconfiguredPickupAllAction(context, targetInventoryIndex, targetStack);
                bestAction = pickBetterAction(bestAction, pickupAllAction);
            }
        }

        return bestAction;
    }

    private InvManager.PlannedAction buildUnconfiguredPickupAllAction(
            InvManager.SnapshotContext context, int targetInventoryIndex, ItemStack targetStack) {
        int resultingSize = this.getUnconfiguredPickupAllResultingSize(context, targetInventoryIndex, targetStack);
        if (resultingSize <= targetStack.size) {
            return null;
        }

        final int selectedTargetInventoryIndex = targetInventoryIndex;
        List<InvManager.PlannedClick> steps = new ArrayList<>(3);
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(selectedTargetInventoryIndex),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        ItemStack currentTarget = current.snapshot.getSlot(selectedTargetInventoryIndex);
                        return current.snapshot.carried == null
                                && InvManager.isPartialStack(currentTarget)
                                && !module.isManagedStack(currentTarget)
                                && module.getUnconfiguredPickupAllResultingSize(
                                                current, selectedTargetInventoryIndex, currentTarget)
                                        > currentTarget.size;
                    }
                },
                new InvManager.StepHook() {
                    @Override
                    public void run(InvManager module) {
                        module.cursorRecoveryInventoryIndex = selectedTargetInventoryIndex;
                    }
                },
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(selectedTargetInventoryIndex),
                0,
                6,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried != null
                                && !module.isManagedStack(current.snapshot.carried)
                                && current.snapshot.getSlot(selectedTargetInventoryIndex) == null
                                && module.getUnconfiguredPickupAllResultingSize(
                                                current, selectedTargetInventoryIndex, current.snapshot.carried)
                                        > current.snapshot.carried.size;
                    }
                },
                NO_OP,
                NO_OP));
        steps.add(new InvManager.PlannedClick(
                toContainerSlot(selectedTargetInventoryIndex),
                0,
                0,
                new InvManager.StepValidator() {
                    @Override
                    public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                        return current.snapshot.carried != null
                                && InvManager.canDepositToSlot(current.snapshot, selectedTargetInventoryIndex);
                    }
                },
                NO_OP,
                new InvManager.StepHook() {
                    @Override
                    public void run(InvManager module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                }));
        return new InvManager.PlannedAction(
                InvManager.PlanType.STACK_UNCONFIGURED_ITEMS,
                3,
                selectedTargetInventoryIndex,
                Integer.MAX_VALUE,
                resultingSize,
                resultingSize >= targetStack.getMaxSize(),
                0,
                selectedTargetInventoryIndex,
                steps);
    }

    private InvManager.PlannedAction buildRecoveryAction(
            InvManager.SnapshotContext context, InvManager.PlanType type, boolean allowDrop) {
        InvManager.RecoveryPlanData recoveryPlan =
                this.simulateRecoveryPlan(context.snapshot, this.cursorRecoveryInventoryIndex, allowDrop);
        if (recoveryPlan == null) {
            return null;
        }

        List<InvManager.PlannedClick> steps =
                new ArrayList<>(recoveryPlan.depositIndices.size() + (recoveryPlan.willDrop ? 1 : 0));

        for (int i = 0; i < recoveryPlan.depositIndices.size(); i++) {
            final int depositInventoryIndex = recoveryPlan.depositIndices.get(i);
            boolean clearsCursor = !recoveryPlan.willDrop && i == recoveryPlan.depositIndices.size() - 1;
            steps.add(new InvManager.PlannedClick(
                    toContainerSlot(depositInventoryIndex),
                    0,
                    0,
                    new InvManager.StepValidator() {
                        @Override
                        public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                            return current.snapshot.carried != null
                                    && InvManager.canDepositToSlot(current.snapshot, depositInventoryIndex);
                        }
                    },
                    new InvManager.StepHook() {
                        @Override
                        public void run(InvManager module) {
                            module.cursorRecoveryInventoryIndex = depositInventoryIndex;
                        }
                    },
                    clearsCursor
                            ? new InvManager.StepHook() {
                                @Override
                                public void run(InvManager module) {
                                    module.cursorRecoveryInventoryIndex = -1;
                                }
                            }
                            : NO_OP));
        }

        if (recoveryPlan.willDrop) {
            steps.add(new InvManager.PlannedClick(
                    -999,
                    0,
                    0,
                    new InvManager.StepValidator() {
                        @Override
                        public boolean isValid(InvManager.SnapshotContext current, InvManager module) {
                            return current.snapshot.carried != null;
                        }
                    },
                    NO_OP,
                    new InvManager.StepHook() {
                        @Override
                        public void run(InvManager module) {
                            module.cursorRecoveryInventoryIndex = -1;
                        }
                    }));
        }

        return new InvManager.PlannedAction(
                type,
                steps.size(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                0,
                false,
                0,
                recoveryPlan.depositIndices.isEmpty() ? Integer.MAX_VALUE : recoveryPlan.depositIndices.get(0),
                steps);
    }

    private InvManager.MergePlanData buildMergePlanData(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, ItemStack targetStack) {
        int room = getRemainingRoom(targetStack);
        if (room <= 0) {
            return null;
        }

        List<InvManager.MergeSource> sources = this.collectMergeSources(context, assignment.hotbarSlot, targetStack);
        if (sources.isEmpty()) {
            return null;
        }

        InvManager.MergePath[] bestPaths = new InvManager.MergePath[room + 1];
        bestPaths[0] = new InvManager.MergePath(0, new ArrayList<>());

        for (InvManager.MergeSource source : sources) {
            InvManager.MergePath[] nextPaths = new InvManager.MergePath[room + 1];
            System.arraycopy(bestPaths, 0, nextPaths, 0, bestPaths.length);

            for (int added = 0; added <= room; added++) {
                InvManager.MergePath path = bestPaths[added];
                if (path != null && added < room) {
                    int effectiveAdded = Math.min(room - added, source.stackSize);
                    if (effectiveAdded > 0) {
                        boolean returnsRemainder =
                                source.type == InvManager.MergeSourceType.HOTBAR && source.stackSize > effectiveAdded;
                        int extraClicks = source.type == InvManager.MergeSourceType.MAIN_INVENTORY
                                ? 1
                                : (returnsRemainder ? 3 : 2);
                        InvManager.MergeUse mergeUse =
                                new InvManager.MergeUse(source, effectiveAdded, returnsRemainder);
                        InvManager.MergePath candidate = path.append(mergeUse, extraClicks);
                        int newAdded = added + effectiveAdded;
                        if (this.isBetterMergePath(candidate, nextPaths[newAdded])) {
                            nextPaths[newAdded] = candidate;
                        }
                    }
                }
            }

            bestPaths = nextPaths;
        }

        for (int added = room; added > 0; added--) {
            InvManager.MergePath bestPath = bestPaths[added];
            if (bestPath != null) {
                return new InvManager.MergePlanData(
                        targetStack.size + added,
                        bestPath.cost,
                        bestPath.uses,
                        bestPath.uses.isEmpty() ? Integer.MAX_VALUE : bestPath.uses.get(0).source.inventoryIndex);
            }
        }

        return null;
    }

    private boolean isBetterMergePath(InvManager.MergePath candidate, InvManager.MergePath existing) {
        if (candidate == null) {
            return false;
        }

        if (existing == null) {
            return true;
        }

        int comparison = Integer.compare(candidate.cost, existing.cost);
        if (comparison != 0) {
            return comparison < 0;
        }

        comparison = Integer.compare(candidate.uses.size(), existing.uses.size());
        if (comparison != 0) {
            return comparison < 0;
        }

        for (int index = 0; index < candidate.uses.size() && index < existing.uses.size(); index++) {
            InvManager.MergeUse candidateUse = candidate.uses.get(index);
            InvManager.MergeUse existingUse = existing.uses.get(index);
            comparison = Integer.compare(candidateUse.source.type.ordinal(), existingUse.source.type.ordinal());
            if (comparison != 0) {
                return comparison < 0;
            }

            comparison = Integer.compare(candidateUse.effectiveAdded, existingUse.effectiveAdded);
            if (comparison != 0) {
                return comparison > 0;
            }

            comparison = Integer.compare(candidateUse.source.inventoryIndex, existingUse.source.inventoryIndex);
            if (comparison != 0) {
                return comparison < 0;
            }
        }

        return false;
    }

    private List<InvManager.MergeSource> collectMergeSources(
            InvManager.SnapshotContext context, int targetHotbarSlot, ItemStack targetStack) {
        List<InvManager.MergeSource> sources = new ArrayList<>();

        for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
            if (inventoryIndex != targetHotbarSlot) {
                ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
                if (canStacksMerge(sourceStack, targetStack)) {
                    if (inventoryIndex < HOTBAR_SIZE) {
                        if (!this.isCorrectHotbarSlot(context, inventoryIndex)) {
                            sources.add(new InvManager.MergeSource(
                                    inventoryIndex, sourceStack.size, InvManager.MergeSourceType.HOTBAR));
                        }
                    } else if (isQuickMoveMergeSafe(context.snapshot, targetHotbarSlot, sourceStack, targetStack)) {
                        sources.add(new InvManager.MergeSource(
                                inventoryIndex, sourceStack.size, InvManager.MergeSourceType.MAIN_INVENTORY));
                    }
                }
            }
        }

        sources.sort(new Comparator<InvManager.MergeSource>() {
            public int compare(InvManager.MergeSource first, InvManager.MergeSource second) {
                int comparison = Integer.compare(first.type.ordinal(), second.type.ordinal());
                if (comparison != 0) {
                    return comparison;
                }

                comparison = Integer.compare(second.stackSize, first.stackSize);
                return comparison != 0 ? comparison : Integer.compare(first.inventoryIndex, second.inventoryIndex);
            }
        });
        return sources;
    }

    private int getPickupAllResultingSize(
            InvManager.SnapshotContext context, int targetHotbarSlot, ItemStack targetStack) {
        if (!isPartialStack(targetStack)) {
            return 0;
        }

        int mergedSize = targetStack.size;

        for (int inventoryIndex = 0; inventoryIndex < 36 && mergedSize < targetStack.getMaxSize(); inventoryIndex++) {
            if (inventoryIndex != targetHotbarSlot) {
                ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
                if (canStacksMerge(sourceStack, targetStack)) {
                    if (inventoryIndex < HOTBAR_SIZE && this.isCorrectHotbarSlot(context, inventoryIndex)) {
                        return 0;
                    }

                    mergedSize = Math.min(targetStack.getMaxSize(), mergedSize + sourceStack.size);
                }
            }
        }

        return mergedSize;
    }

    private int getUnconfiguredPickupAllResultingSize(
            InvManager.SnapshotContext context, int targetInventoryIndex, ItemStack targetStack) {
        if (isPartialStack(targetStack) && !this.isManagedStack(targetStack)) {
            int mergedSize = targetStack.size;

            for (int inventoryIndex = 0;
                    inventoryIndex < 36 && mergedSize < targetStack.getMaxSize();
                    inventoryIndex++) {
                if (inventoryIndex != targetInventoryIndex) {
                    ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
                    if (sourceStack != null
                            && !this.isManagedStack(sourceStack)
                            && isPartialStack(sourceStack)
                            && canStacksMerge(sourceStack, targetStack)) {
                        mergedSize = Math.min(targetStack.getMaxSize(), mergedSize + sourceStack.size);
                    }
                }
            }

            return mergedSize;
        } else {
            return 0;
        }
    }

    private void recoverCarriedStackImmediately(InvManager.InventorySnapshot snapshot, boolean allowDrop) {
        if (mc.player.menu instanceof PlayerMenu && snapshot.carried != null) {
            int preferredIndex = this.cursorRecoveryInventoryIndex;

            for (int remainingAttempts = 38;
                    snapshot.carried != null && remainingAttempts-- > 0;
                    snapshot = InvManager.InventorySnapshot.capture()) {
                int depositIndex = this.findRecoveryDepositIndex(snapshot, preferredIndex);
                if (depositIndex < 0) {
                    break;
                }

                this.cursorRecoveryInventoryIndex = depositIndex;
                this.click(toContainerSlot(depositIndex), 0, 0);
                preferredIndex = -1;
            }

            if (snapshot.carried != null && allowDrop) {
                this.click(-999, 0, 0);
                snapshot = InvManager.InventorySnapshot.capture();
            }

            this.currentAction = null;
            if (snapshot.carried == null || allowDrop) {
                this.cursorRecoveryInventoryIndex = -1;
            }

            if (this.sessionState != InvManager.SessionState.COMPLETE_LATCHED) {
                this.sessionState = InvManager.SessionState.ACTIVE;
            }
        } else {
            this.currentAction = null;
            if (allowDrop) {
                this.cursorRecoveryInventoryIndex = -1;
            }

            if (this.sessionState != InvManager.SessionState.COMPLETE_LATCHED) {
                this.sessionState = InvManager.SessionState.ACTIVE;
            }
        }
    }

    private InvManager.RecoveryPlanData simulateRecoveryPlan(
            InvManager.InventorySnapshot snapshot, int preferredIndex, boolean allowDrop) {
        if (snapshot.carried == null) {
            return null;
        }

        InvManager.SimulatedInventory simulated = new InvManager.SimulatedInventory(snapshot);
        List<Integer> depositIndices = new ArrayList<>();
        int remainingAttempts = 37;

        for (int preferredRecoveryIndex = preferredIndex;
                simulated.getCarried() != null && remainingAttempts-- > 0;
                preferredRecoveryIndex = -1) {
            int depositIndex = this.findRecoveryDepositIndex(simulated, preferredRecoveryIndex);
            if (depositIndex < 0) {
                break;
            }

            depositIndices.add(depositIndex);
            simulated.deposit(depositIndex);
        }

        if (simulated.getCarried() != null && !allowDrop) {
            return null;
        }

        boolean willDrop = simulated.getCarried() != null;
        return depositIndices.isEmpty() && !willDrop ? null : new InvManager.RecoveryPlanData(depositIndices, willDrop);
    }

    private int findRecoveryDepositIndex(InvManager.RecoveryView view, int preferredIndex) {
        if (preferredIndex >= 0 && canDepositToSlot(view, preferredIndex)) {
            return preferredIndex;
        }

        for (int inventoryIndex = HOTBAR_SIZE; inventoryIndex < 36; inventoryIndex++) {
            if (canMergeIntoSlot(view, inventoryIndex)) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = 0; inventoryIndex < HOTBAR_SIZE; inventoryIndex++) {
            if (canMergeIntoSlot(view, inventoryIndex)) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = HOTBAR_SIZE; inventoryIndex < 36; inventoryIndex++) {
            if (view.getSlot(inventoryIndex) == null) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = 0; inventoryIndex < HOTBAR_SIZE; inventoryIndex++) {
            if (view.getSlot(inventoryIndex) == null) {
                return inventoryIndex;
            }
        }

        return -1;
    }

    private InvManager.SlotAssignment[] resolveAssignments(InvManager.InventorySnapshot snapshot) {
        InvManager.SlotAssignment[] assignments = new InvManager.SlotAssignment[HOTBAR_SIZE];
        List<String> orderedItems = this.items.getItems();

        for (int priorityIndex = 0; priorityIndex < orderedItems.size(); priorityIndex++) {
            String storageId = orderedItems.get(priorityIndex);
            Integer assignedSlot = this.items.getAssignedSlot(storageId);
            if (assignedSlot != null) {
                int hotbarSlot = assignedSlot - 1;
                if (hotbarSlot >= 0
                        && hotbarSlot < HOTBAR_SIZE
                        && assignments[hotbarSlot] == null
                        && this.hasMatchingStack(snapshot, storageId)) {
                    assignments[hotbarSlot] = new InvManager.SlotAssignment(hotbarSlot, storageId, priorityIndex);
                }
            }
        }

        return assignments;
    }

    private boolean hasMatchingStack(InvManager.InventorySnapshot snapshot, String storageId) {
        for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
            if (matchesSortItem(storageId, snapshot.getSlot(inventoryIndex))) {
                return true;
            }
        }

        return false;
    }

    private boolean isCorrectHotbarSlot(InvManager.SnapshotContext context, int hotbarSlot) {
        if (hotbarSlot >= 0 && hotbarSlot < HOTBAR_SIZE) {
            InvManager.SlotAssignment assignment = context.assignments[hotbarSlot];
            return assignment != null && matchesSortItem(assignment.storageId, context.snapshot.getSlot(hotbarSlot));
        } else {
            return false;
        }
    }

    private boolean isManagedStack(ItemStack stack) {
        return stack != null && !isTnt(stack) && this.items.matches(stack);
    }

    private boolean isAssignedTargetSatisfied(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, ItemStack targetStack) {
        if (!matchesSortItem(assignment.storageId, targetStack)) {
            return false;
        }

        InvManager.PlacementSource betterSource =
                this.findBestPlacementSource(context, assignment, assignment.hotbarSlot);
        if (betterSource == null) {
            return true;
        }

        double targetQuality = ItemSearchIndex.getMatchQuality(assignment.storageId, targetStack);
        return betterSource.quality <= targetQuality + 1.0E-6;
    }

    private InvManager.PlacementSource findBestPlacementSource(
            InvManager.SnapshotContext context, InvManager.SlotAssignment assignment, int excludedInventoryIndex) {
        InvManager.PlacementSource bestSource = null;

        for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
            if (inventoryIndex != excludedInventoryIndex) {
                ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
                if (matchesSortItem(assignment.storageId, sourceStack)
                        && (inventoryIndex >= HOTBAR_SIZE || !this.isCorrectHotbarSlot(context, inventoryIndex))) {
                    InvManager.PlacementSource candidate = new InvManager.PlacementSource(
                            inventoryIndex,
                            ItemSearchIndex.getMatchQuality(assignment.storageId, sourceStack),
                            inventoryIndex < HOTBAR_SIZE ? 1 : 0,
                            sourceStack != null ? sourceStack.size : 0);
                    if (bestSource == null || candidate.isBetterThan(bestSource)) {
                        bestSource = candidate;
                    }
                }
            }
        }

        return bestSource;
    }

    private static InvManager.PlannedAction pickBetterAction(
            InvManager.PlannedAction currentBest, InvManager.PlannedAction candidate) {
        if (candidate == null) {
            return currentBest;
        } else {
            return currentBest != null && ACTION_COMPARATOR.compare(candidate, currentBest) >= 0
                    ? currentBest
                    : candidate;
        }
    }

    private void click(int slotId, int button, int mode) {
        if (this.preUpdatePacketWindow) {
            this.inventoryActionPerformed = true;
            this.sendingInventoryClick = true;

            try {
                mc.interactionManager.clickSlot(mc.player.menu.networkId, slotId, button, mode, mc.player);
            } finally {
                this.sendingInventoryClick = false;
            }
        }
    }

    private boolean isManagedInventoryOpen() {
        return Utils.nullCheck()
                && mc.screen instanceof SurvivalInventoryScreen
                && mc.player.menu instanceof PlayerMenu
                && Utils.inInventory();
    }

    private static boolean isQuickMoveMergeSafe(
            InvManager.InventorySnapshot snapshot, int targetHotbarSlot, ItemStack sourceStack, ItemStack targetStack) {
        if (canStacksMerge(sourceStack, targetStack) && isPartialStack(targetStack)) {
            for (int hotbarSlot = 0; hotbarSlot < HOTBAR_SIZE; hotbarSlot++) {
                if (hotbarSlot != targetHotbarSlot) {
                    ItemStack hotbarStack = snapshot.getSlot(hotbarSlot);
                    if (hotbarStack != null
                            && canStacksMerge(hotbarStack, sourceStack)
                            && isPartialStack(hotbarStack)
                            && hotbarSlot < targetHotbarSlot) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean canDepositToSlot(InvManager.RecoveryView view, int inventoryIndex) {
        ItemStack slotStack = view.getSlot(inventoryIndex);
        return slotStack == null || canMergeIntoSlot(view, inventoryIndex);
    }

    private static boolean canMergeIntoSlot(InvManager.RecoveryView view, int inventoryIndex) {
        ItemStack slotStack = view.getSlot(inventoryIndex);
        ItemStack carried = view.getCarried();
        return canStacksMerge(slotStack, carried) && isPartialStack(slotStack);
    }

    private static boolean canStacksMerge(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getItem() != second.getItem()) {
            return false;
        } else {
            return first.hasCustomData() && first.getMetadata() != second.getMetadata()
                    ? false
                    : ItemStack.matchesNbt(first, second);
        }
    }

    private static boolean isPartialStack(ItemStack stack) {
        return stack != null && stack.isStackable() && stack.size < stack.getMaxSize();
    }

    private static int getRemainingRoom(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.getMaxSize() - stack.size);
    }

    private static int toContainerSlot(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex < 36) {
            return inventoryIndex < HOTBAR_SIZE ? inventoryIndex + 36 : inventoryIndex;
        } else {
            return -1;
        }
    }

    private enum CurrentArmor {
        BOOTS(0, 8),
        LEGGINGS(1, 7),
        CHESTPLATE(2, 6),
        HELMET(3, 5);

        final int slot;
        final int invSlot;
        int defenceLevel;

        CurrentArmor(int slot, int invSlot) {
            this.slot = slot;
            this.invSlot = invSlot;
        }

        ItemStack getItemStack() {
            return InvManager.mc.player.inventory.getArmor(this.slot);
        }
    }

    private final class InventoryData {
        final Inventory inventory;
        final int size;
        int filled;
        int emptyWithoutHotbar;
        final double[] swordData = new double[] {-1.0, -1.0};
        final int[][] armorData = new int[][] {{-1, -1, -1, -1}, {-1, -1, -1, -1}};
        double bestSwordDamage = -1.0;
        double bestPickaxe = -1.0;
        double bestAxe = -1.0;
        double bestShovel = -1.0;
        final HashSet<Integer> uniqueSingles = new HashSet<>();

        InventoryData(Inventory inventory, boolean playerInventory, boolean armorOnly) {
            this.inventory = inventory;
            this.size = playerInventory ? inventory.getSize() - 4 : inventory.getSize();

            for (int i = 0; i < this.size; i++) {
                ItemStack itemStack = inventory.getItem(i);
                if (itemStack != null) {
                    this.filled++;
                } else if (i >= InvManager.HOTBAR_SIZE) {
                    this.emptyWithoutHotbar++;
                }

                if (itemStack != null) {
                    if (InvManager.this.isArmor(itemStack)) {
                        ArmorItem armor = (ArmorItem) itemStack.getItem();
                        int slot = 3 - armor.slot;
                        int defenceLevel = InvManager.this.getDefenceLevel(itemStack);
                        if (defenceLevel > this.armorData[1][slot]) {
                            this.armorData[1][slot] = defenceLevel;
                            this.armorData[0][slot] = i;
                        }
                    }

                    if (!armorOnly) {
                        Item item = itemStack.getItem();
                        if (InvManager.this.isSword(itemStack)) {
                            double damageLevel = Utils.getDamageLevel(itemStack);
                            if (damageLevel > this.bestSwordDamage) {
                                this.bestSwordDamage = damageLevel;
                            }

                            if (damageLevel > this.swordData[1]) {
                                this.swordData[1] = damageLevel;
                                this.swordData[0] = i;
                            }
                        } else if (item instanceof PickaxeItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.STONE);
                            if (efficiency > this.bestPickaxe) {
                                this.bestPickaxe = efficiency;
                            }
                        } else if (item instanceof AxeItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.LOG);
                            if (efficiency > this.bestAxe) {
                                this.bestAxe = efficiency;
                            }
                        } else if (item instanceof ShovelItem) {
                            double efficiency = Utils.getEfficiency(itemStack, Blocks.DIRT);
                            if (efficiency > this.bestShovel) {
                                this.bestShovel = efficiency;
                            }
                        } else if (!(item instanceof BlockItem) && itemStack.getMaxSize() == 1) {
                            this.uniqueSingles.add(Item.getId(item));
                        }
                    }
                }
            }
        }

        void compareAndRemove(InvManager.InventoryData data) {
            if (data.bestSwordDamage > this.swordData[1]) {
                this.swordData[1] = -1.0;
            }

            for (int i = 0; i < this.armorData[0].length; i++) {
                if (data.armorData[1][i] > this.armorData[1][i]) {
                    this.armorData[1][i] = -1;
                }
            }
        }
    }

    private static final class InventorySnapshot implements InvManager.RecoveryView {
        static final int INVENTORY_SIZE = 36;
        final ItemStack[] slots = new ItemStack[36];
        final ItemStack carried;

        private InventorySnapshot(ItemStack[] slots, ItemStack carried) {
            System.arraycopy(slots, 0, this.slots, 0, slots.length);
            this.carried = carried;
        }

        static InvManager.InventorySnapshot capture() {
            ItemStack[] slots = new ItemStack[36];

            for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
                ItemStack stack = InvManager.mc.player.inventory.getItem(inventoryIndex);
                slots[inventoryIndex] = stack != null ? stack.copy() : null;
            }

            ItemStack carried = InvManager.mc.player.inventory.getCursorItem();
            return new InvManager.InventorySnapshot(slots, carried != null ? carried.copy() : null);
        }

        @Override
        public ItemStack getSlot(int inventoryIndex) {
            return inventoryIndex >= 0 && inventoryIndex < 36 ? this.slots[inventoryIndex] : null;
        }

        @Override
        public ItemStack getCarried() {
            return this.carried;
        }
    }

    private static final class MergePath {
        final int cost;
        final List<InvManager.MergeUse> uses;

        MergePath(int cost, List<InvManager.MergeUse> uses) {
            this.cost = cost;
            this.uses = uses;
        }

        InvManager.MergePath append(InvManager.MergeUse mergeUse, int extraCost) {
            List<InvManager.MergeUse> nextUses = new ArrayList<>(this.uses.size() + 1);
            nextUses.addAll(this.uses);
            nextUses.add(mergeUse);
            return new InvManager.MergePath(this.cost + extraCost, nextUses);
        }
    }

    private static final class MergePlanData {
        final int resultingSize;
        final int clickCount;
        final List<InvManager.MergeUse> uses;
        final int primarySourceIndex;

        MergePlanData(int resultingSize, int clickCount, List<InvManager.MergeUse> uses, int primarySourceIndex) {
            this.resultingSize = resultingSize;
            this.clickCount = clickCount;
            this.uses = uses;
            this.primarySourceIndex = primarySourceIndex;
        }
    }

    private static final class MergeSource {
        final int inventoryIndex;
        final int stackSize;
        final InvManager.MergeSourceType type;

        MergeSource(int inventoryIndex, int stackSize, InvManager.MergeSourceType type) {
            this.inventoryIndex = inventoryIndex;
            this.stackSize = stackSize;
            this.type = type;
        }
    }

    private enum MergeSourceType {
        MAIN_INVENTORY,
        HOTBAR;
    }

    private static final class MergeUse {
        final InvManager.MergeSource source;
        final int effectiveAdded;
        final boolean returnsRemainder;

        MergeUse(InvManager.MergeSource source, int effectiveAdded, boolean returnsRemainder) {
            this.source = source;
            this.effectiveAdded = effectiveAdded;
            this.returnsRemainder = returnsRemainder;
        }
    }

    private static final class PlacementSource {
        final int inventoryIndex;
        final double quality;
        final int sourcePreference;
        final int resultingSize;

        PlacementSource(int inventoryIndex, double quality, int sourcePreference, int resultingSize) {
            this.inventoryIndex = inventoryIndex;
            this.quality = quality;
            this.sourcePreference = sourcePreference;
            this.resultingSize = resultingSize;
        }

        boolean isBetterThan(InvManager.PlacementSource other) {
            int comparison = Double.compare(this.quality, other.quality);
            if (Math.abs(this.quality - other.quality) > 1.0E-6) {
                return comparison > 0;
            }

            comparison = Integer.compare(this.sourcePreference, other.sourcePreference);
            if (comparison != 0) {
                return comparison < 0;
            }

            comparison = Integer.compare(this.resultingSize, other.resultingSize);
            return comparison != 0 ? comparison > 0 : this.inventoryIndex < other.inventoryIndex;
        }
    }

    private enum PlanType {
        PLACE_TO_HOTBAR,
        MERGE_TO_TARGET,
        PICKUP_ALL_TO_TARGET,
        STACK_UNCONFIGURED_ITEMS,
        RECOVER_CURSOR,
        PRE_CLOSE_RECOVERY;
    }

    private static final class PlannedAction {
        final InvManager.PlanType type;
        final int clickCount;
        final int targetHotbarSlot;
        final int priorityIndex;
        final int resultingSize;
        final boolean completesTarget;
        final int displacesCorrectHotbar;
        final int primarySourceIndex;
        final List<InvManager.PlannedClick> steps;
        int nextStepIndex;

        PlannedAction(
                InvManager.PlanType type,
                int clickCount,
                int targetHotbarSlot,
                int priorityIndex,
                int resultingSize,
                boolean completesTarget,
                int displacesCorrectHotbar,
                int primarySourceIndex,
                List<InvManager.PlannedClick> steps) {
            this.type = type;
            this.clickCount = clickCount;
            this.targetHotbarSlot = targetHotbarSlot;
            this.priorityIndex = priorityIndex;
            this.resultingSize = resultingSize;
            this.completesTarget = completesTarget;
            this.displacesCorrectHotbar = displacesCorrectHotbar;
            this.primarySourceIndex = primarySourceIndex;
            this.steps = steps;
        }

        InvManager.PlannedClick peek() {
            return this.nextStepIndex < this.steps.size() ? this.steps.get(this.nextStepIndex) : null;
        }

        void advance() {
            this.nextStepIndex++;
        }

        boolean isComplete() {
            return this.nextStepIndex >= this.steps.size();
        }
    }

    private static final class PlannedClick {
        final int slotId;
        final int button;
        final int mode;
        final InvManager.StepValidator validator;
        final InvManager.StepHook beforeExecute;
        final InvManager.StepHook afterExecute;

        PlannedClick(
                int slotId,
                int button,
                int mode,
                InvManager.StepValidator validator,
                InvManager.StepHook beforeExecute,
                InvManager.StepHook afterExecute) {
            this.slotId = slotId;
            this.button = button;
            this.mode = mode;
            this.validator = validator;
            this.beforeExecute = beforeExecute;
            this.afterExecute = afterExecute;
        }
    }

    private static final class RecoveryPlanData {
        final List<Integer> depositIndices;
        final boolean willDrop;

        RecoveryPlanData(List<Integer> depositIndices, boolean willDrop) {
            this.depositIndices = depositIndices;
            this.willDrop = willDrop;
        }
    }

    private interface RecoveryView {
        ItemStack getSlot(int var1);

        ItemStack getCarried();
    }

    private enum SessionState {
        ACTIVE,
        EXECUTING,
        COMPLETE_LATCHED;
    }

    private static final class SimulatedInventory implements InvManager.RecoveryView {
        final ItemStack[] slots = new ItemStack[36];
        ItemStack carried;

        SimulatedInventory(InvManager.InventorySnapshot snapshot) {
            for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
                ItemStack stack = snapshot.getSlot(inventoryIndex);
                this.slots[inventoryIndex] = stack != null ? stack.copy() : null;
            }

            this.carried = snapshot.carried != null ? snapshot.carried.copy() : null;
        }

        void deposit(int inventoryIndex) {
            if (this.carried != null) {
                ItemStack slotStack = this.slots[inventoryIndex];
                if (slotStack == null) {
                    this.slots[inventoryIndex] = this.carried.copy();
                    this.carried = null;
                } else if (InvManager.canStacksMerge(slotStack, this.carried)) {
                    int moved = Math.min(InvManager.getRemainingRoom(slotStack), this.carried.size);
                    slotStack.size += moved;
                    this.carried.size -= moved;
                    if (this.carried.size <= 0) {
                        this.carried = null;
                    }
                }
            }
        }

        @Override
        public ItemStack getSlot(int inventoryIndex) {
            return inventoryIndex >= 0 && inventoryIndex < this.slots.length ? this.slots[inventoryIndex] : null;
        }

        @Override
        public ItemStack getCarried() {
            return this.carried;
        }
    }

    private static final class SlotAssignment {
        final int hotbarSlot;
        final String storageId;
        final int priorityIndex;

        SlotAssignment(int hotbarSlot, String storageId, int priorityIndex) {
            this.hotbarSlot = hotbarSlot;
            this.storageId = storageId;
            this.priorityIndex = priorityIndex;
        }
    }

    private static final class SnapshotContext {
        final InvManager.InventorySnapshot snapshot;
        final InvManager.SlotAssignment[] assignments;

        private SnapshotContext(InvManager.InventorySnapshot snapshot, InvManager.SlotAssignment[] assignments) {
            this.snapshot = snapshot;
            this.assignments = assignments;
        }

        static InvManager.SnapshotContext create(
                InvManager.InventorySnapshot snapshot, InvManager.SlotAssignment[] assignments) {
            return new InvManager.SnapshotContext(snapshot, assignments);
        }
    }

    private interface StepHook {
        void run(InvManager var1);
    }

    private interface StepValidator {
        boolean isValid(InvManager.SnapshotContext var1, InvManager var2);
    }
}
