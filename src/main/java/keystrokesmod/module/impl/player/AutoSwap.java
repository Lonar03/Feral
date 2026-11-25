package keystrokesmod.module.impl.player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.scaffold.ScaffoldSupport;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.util.math.BlockPos;

public class AutoSwap extends Module {
    private static final String[] ALLOWED_TYPES = new String[] {"Exact", "Variants", "Full", "Any"};
    private static final int EXACT = 0;
    private static final int VARIANTS = 1;
    private static final int FULL = 2;
    private static final int ANY = 3;
    private final SliderSetting allowedTypes;
    private final ButtonSetting swapOnRmb;
    private final ButtonSetting syncStackSize;
    private final ButtonSetting useBlockWhitelist;
    private final BlockListSetting blockWhitelist;
    private ItemStack trackedStack;
    private volatile int lastPlaceSlot = -1;
    private int lastSwapSlot = -1;
    private final ItemStack[] realStacks = new ItemStack[9];
    private final long[] lastBlockUse = new long[9];
    private final boolean[] sizeUpdateWhitelist = new boolean[9];
    private final Map<BlockPos, AutoSwap.PendingPlacement> pendingPlacements = new HashMap<>();
    private long lastSwapTime;

    public AutoSwap() {
        super("Auto Swap", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.allowedTypes = new SliderSetting("Allowed types", 0, ALLOWED_TYPES));
        this.registerSetting(this.swapOnRmb = new ButtonSetting("Swap on RMB", true));
        this.registerSetting(this.syncStackSize = new ButtonSetting("Sync stack size", true));
        this.registerSetting(this.useBlockWhitelist = new ButtonSetting("Use block whitelist", false));
        this.registerSetting(
                this.blockWhitelist = new BlockListSetting(
                        "Whitelisted blocks", "Blocks", "Blocks.Block whitelist", "Blocks.Whitelisted blocks"));
        this.blockWhitelist.visible = false;
        this.closetModule = true;
        this.alwaysOn = true;
        ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void guiUpdate() {
        this.blockWhitelist.setVisible(this.useBlockWhitelist.isToggled(), this);
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.syncStackSize) {
            this.resetStackSizes();
        }
    }

    public boolean shouldSyncStackSize() {
        return (this.isEnabled() || this.scaffoldSwapEnabled())
                && this.syncStackSize.isToggled()
                && Utils.nullCheck()
                && !mc.interactionManager.hasCreativeInventory();
    }

    public int getBlockCount(int slot, ItemStack stack) {
        if (!this.shouldSyncStackSize()) {
            this.resetStackSizes();
            return stack == null ? 0 : stack.size;
        } else if (stack == null) {
            return 0;
        } else {
            ItemStack real = this.realStacks[slot];
            if (!this.isSameStack(real, stack)) {
                return stack.size;
            } else if (real.size <= 0 && Math.abs(real.size - stack.size) > 8) {
                this.resetStackSizes();
                return stack.size;
            } else {
                return real.size;
            }
        }
    }

    public void onBlockUsed(ItemStack stack, int previousSize, BlockPos pos, BlockState state) {
        if (!this.shouldSyncStackSize()) {
            this.resetStackSizes();
        } else {
            int slot = mc.player.inventory.selectedSlot;
            long now = System.currentTimeMillis();
            long timeout = this.stackUpdateTimeout();
            ItemStack real = this.realStacks[slot];
            int size = this.isSameStack(real, stack) && now - this.lastBlockUse[slot] <= timeout
                    ? real.size
                    : previousSize;
            this.realStacks[slot] = stack.copy();
            this.realStacks[slot].size = Math.max(0, size - (previousSize - stack.size));
            this.lastBlockUse[slot] = now;
            this.pendingPlacements.values().removeIf(placement -> now - placement.time > timeout);
            this.pendingPlacements.put(pos, new AutoSwap.PendingPlacement(slot, state, now));
        }
    }

    public void processSetSlot(InventoryMenuSlotContentS2CPacket packet) {
        if (!this.shouldSyncStackSize()) {
            this.resetStackSizes();
        } else {
            int slot = packet.getSlotId() - 36;
            if (packet.getMenuId() == 0 && slot >= 0 && slot < 9) {
                ItemStack incoming = packet.getItem();
                ItemStack current = mc.player.inventory.getItem(slot);
                boolean whitelisted = this.sizeUpdateWhitelist[slot];
                this.sizeUpdateWhitelist[slot] = false;
                if (incoming != null && incoming.getItem() instanceof BlockItem) {
                    if (whitelisted
                            || !this.isSameStack(incoming, current)
                            || Math.abs(incoming.size - current.size) > 8) {
                        this.realStacks[slot] = incoming.copy();
                    }
                } else {
                    this.realStacks[slot] = null;
                    this.lastBlockUse[slot] = 0L;
                }
            }
        }
    }

    public void processWindowItems(InventoryMenuContentS2CPacket packet) {
        if (!this.shouldSyncStackSize()) {
            this.resetStackSizes();
        } else if (packet.getMenuId() == 0) {
            this.resetStackSizes();
            ItemStack[] stacks = packet.getItems();

            for (int slot = 0; slot < 9 && slot + 36 < stacks.length; slot++) {
                ItemStack stack = stacks[slot + 36];
                if (stack != null && stack.getItem() instanceof BlockItem) {
                    this.realStacks[slot] = stack.copy();
                }
            }
        }
    }

    public void processBlockChange(BlockPos pos, BlockState state) {
        if (!this.shouldSyncStackSize()) {
            this.resetStackSizes();
        } else {
            AutoSwap.PendingPlacement placement = this.pendingPlacements.remove(pos);
            if (placement != null
                    && System.currentTimeMillis() - placement.time <= this.stackUpdateTimeout()
                    && !placement.state.equals(state)) {
                this.sizeUpdateWhitelist[placement.slot] = true;
            }
        }
    }

    private long stackUpdateTimeout() {
        PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
        return 1000L + (info == null ? 0 : Math.max(0, info.getPing()));
    }

    private void resetStackSizes() {
        Arrays.fill(this.realStacks, null);
        Arrays.fill(this.lastBlockUse, 0L);
        Arrays.fill(this.sizeUpdateWhitelist, false);
        this.pendingPlacements.clear();
    }

    @Override
    public void onEnable() {
        if (!this.scaffoldSwapEnabled()) {
            this.resetState();
        }
    }

    @Override
    public void onDisable() {
        if (!this.scaffoldSwapEnabled()) {
            this.resetState();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if ((this.isEnabled() || this.scaffoldSwapEnabled())
                && Utils.nullCheck()
                && e.getPacket() instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket packet = (PlayerUseC2SPacket) e.getPacket();
            if (packet.getFace() != 255) {
                ItemStack stack = packet.getItemInHand();
                if (stack != null && stack.getItem() instanceof BlockItem) {
                    this.trackStack(stack, mc.player.inventory.selectedSlot);
                }
            }
        }
    }

    private void trackStack(ItemStack stack, int slot) {
        this.trackedStack = stack.copy();
        this.trackedStack.size = 1;
        this.lastPlaceSlot = slot;
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (this.isEnabled() && !this.scaffoldSwapEnabled()) {
            this.trySwap(false);
        } else if (!this.scaffoldSwapEnabled()) {
            this.resetState();
        }
    }

    public boolean swapForScaffold() {
        return this.scaffoldSwapEnabled() && this.trySwap(true);
    }

    private boolean scaffoldSwapEnabled() {
        return Scaffold.instance != null
                && Scaffold.instance.isEnabled()
                && Scaffold.autoSwap != null
                && Scaffold.autoSwap.isToggled();
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload e) {
        if (e.world == mc.world) {
            this.resetState();
        }
    }

    private boolean trySwap(boolean scaffold) {
        if (!Utils.nullCheck()) {
            this.resetState();
            return false;
        }

        if (mc.focused && mc.screen == null && (!this.swapOnRmb.isToggled() || Utils.isBindDown(mc.options.useKey))) {
            int currentSlot = mc.player.inventory.selectedSlot;
            ItemStack held = mc.player.getDisplayItemInHand();
            ItemStack reference;
            if (scaffold) {
                reference = held != null && held.getItem() instanceof BlockItem
                        ? held
                        : (held == null && currentSlot == this.lastPlaceSlot ? this.trackedStack : null);
                if (ScaffoldSupport.ItemUtils.isScaffoldBlock(held)) {
                    this.trackStack(held, currentSlot);
                }
            } else {
                if (this.trackedStack == null
                        || currentSlot != this.lastPlaceSlot
                        || held != null && !this.isSameStack(held, this.trackedStack)) {
                    return false;
                }

                reference = this.trackedStack;
            }

            boolean clientStackEmpty = held == null || held.size <= 0;
            boolean serverStackEmpty = this.syncStackSize.isToggled()
                    && !mc.interactionManager.hasCreativeInventory()
                    && held != null
                    && this.getBlockCount(currentSlot, held) <= 0;
            if (!clientStackEmpty
                    && !serverStackEmpty
                    && (!scaffold || ScaffoldSupport.ItemUtils.isScaffoldBlock(held))) {
                return false;
            }

            if (reference != null && !this.isWhitelistedBlock(reference)) {
                return false;
            }

            long now = System.currentTimeMillis();

            for (int slot = 8; slot >= 0; slot--) {
                if (slot != currentSlot && (slot != this.lastSwapSlot || now - this.lastSwapTime >= 300L)) {
                    ItemStack candidate = mc.player.inventory.getItem(slot);
                    if (this.matchesStack(candidate, reference)
                            && this.getBlockCount(slot, candidate) > 0
                            && (reference != null || this.isWhitelistedBlock(candidate))
                            && (!scaffold || ScaffoldSupport.ItemUtils.isScaffoldBlock(candidate))) {
                        this.swapToSlot(slot);
                        if (scaffold) {
                            this.trackStack(candidate, slot);
                        }

                        this.lastSwapSlot = slot;
                        this.lastSwapTime = now;
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isWhitelistedBlock(ItemStack stack) {
        if (!this.useBlockWhitelist.isToggled()) {
            return true;
        }

        if (stack != null && stack.getItem() instanceof BlockItem) {
            Block block = ((BlockItem) stack.getItem()).getBlock();
            Object registryName = Block.REGISTRY.getKey(block);
            if (block != null && registryName != null) {
                String registryId = registryName.toString();
                int meta = stack.getMetadata();
                String storageId = meta != 0 ? registryId + ":" + meta : registryId;
                return this.blockWhitelist.contains(storageId) || this.blockWhitelist.contains(registryId);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isSameStack(ItemStack first, ItemStack second) {
        return first != null
                && second != null
                && first.getItem() == second.getItem()
                && first.getMetadata() == second.getMetadata()
                && ItemStack.matchesNbt(first, second);
    }

    private boolean matchesStack(ItemStack stack, ItemStack reference) {
        if (stack != null && stack.size > 0 && stack.getItem() instanceof BlockItem) {
            int allowedType = (int) this.allowedTypes.getInput();
            if (allowedType != 0 && allowedType != 1) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block == null) {
                    return false;
                } else {
                    return allowedType == 2 ? block.isOpaque() : allowedType == 3 && !BlockUtils.isInteractable(block);
                }
            } else if (reference == null) {
                return true;
            } else if (stack.getItem() != reference.getItem()) {
                return false;
            } else {
                return allowedType == 0 && stack.getMetadata() != reference.getMetadata()
                        ? false
                        : ItemStack.matchesNbt(stack, reference);
            }
        } else {
            return false;
        }
    }

    private void swapToSlot(int slot) {
        if (slot != -1 && slot != mc.player.inventory.selectedSlot) {
            mc.player.inventory.selectedSlot = slot;
            ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
        }
    }

    private void resetState() {
        this.trackedStack = null;
        this.lastPlaceSlot = -1;
        this.lastSwapSlot = -1;
        this.resetStackSizes();
        this.lastSwapTime = 0L;
    }

    private static final class PendingPlacement {
        private final int slot;
        private final BlockState state;
        private final long time;

        private PendingPlacement(int slot, BlockState state, long time) {
            this.slot = slot;
            this.state = state;
            this.time = time;
        }
    }
}
