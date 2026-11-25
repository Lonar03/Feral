package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.SlotUpdateEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.ItemSortScoring;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class AutoTool extends Module {
    private final GroupSetting timingGroup;
    private final SliderSetting activationTime;
    private final SliderSetting hoverDelay;
    private final SliderSetting nextHoverDelay;
    private final ButtonSetting ignoredHeldItemsToggle;
    private final ItemListSetting ignoredHeldItems;
    private final GroupSetting conditionsGroup;
    private final ButtonSetting onlyWhileCrouching;
    private final ButtonSetting requireLeftMouse;
    private final ButtonSetting disableInCreative;
    private final GroupSetting swapGroup;
    private final ButtonSetting switchBackWhenDone;
    private final ButtonSetting overrideSwapBack;
    public final ButtonSetting spoofItem;
    private final ButtonSetting blockWhitelistToggle;
    private final BlockListSetting blockWhitelist;
    private final ButtonSetting blockBlacklistToggle;
    private final BlockListSetting blockBlacklist;
    private boolean hasSwapped;
    public int previousSlot = -1;
    private int tickCounter;
    private int leftMouseDownSinceTick = -1;
    private int hoverStartTick = -1;
    private int nextHoverStartTick = -1;
    private int nextHoverSlot = -1;

    public AutoTool() {
        super("Auto Tool", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.timingGroup = new GroupSetting("Timing"));
        this.registerSetting(
                this.activationTime =
                        new SliderSetting(this.timingGroup, "Activation time", "ms", 0.0, 0.0, 1000.0, 25.0));
        this.registerSetting(
                this.hoverDelay = new SliderSetting(this.timingGroup, "Hover delay", "ms", 0.0, 0.0, 1000.0, 25.0));
        this.registerSetting(
                this.nextHoverDelay =
                        new SliderSetting(this.timingGroup, "Next hover delay", "ms", 0.0, 0.0, 1000.0, 25.0));
        this.registerSetting(this.conditionsGroup = new GroupSetting("Conditions"));
        this.registerSetting(
                this.onlyWhileCrouching = new ButtonSetting(this.conditionsGroup, "Only while crouching", false));
        this.registerSetting(
                this.requireLeftMouse =
                        new ButtonSetting(this.conditionsGroup, "Require Left mouse", true, "Require mouse down"));
        this.registerSetting(
                this.disableInCreative = new ButtonSetting(this.conditionsGroup, "Disable in creative", true));
        this.registerSetting(this.swapGroup = new GroupSetting("Swap"));
        this.registerSetting(
                this.switchBackWhenDone =
                        new ButtonSetting(this.swapGroup, "Switch back when done", true, "Swap to previous slot"));
        this.registerSetting(this.overrideSwapBack = new ButtonSetting(this.swapGroup, "Override swap back", true));
        this.registerSetting(this.spoofItem = new ButtonSetting(this.swapGroup, "Spoof item", false));
        this.registerSetting(
                this.ignoredHeldItemsToggle = new ButtonSetting(
                        "Held item blacklist",
                        false,
                        "Ignore held items",
                        "Restrict held items",
                        "Allow while holding"));
        this.registerSetting(this.ignoredHeldItems = new ItemListSetting("Held items", new String[] {"Items"}));
        this.registerSetting(
                this.blockWhitelistToggle = new ButtonSetting(
                        "Block whitelist", false, "Restrict allowed blocks", "Blocks.Block whitelist"));
        this.registerSetting(
                this.blockWhitelist =
                        new BlockListSetting("Whitelisted blocks", "Blocks", "Blocks.Whitelisted blocks"));
        this.registerSetting(
                this.blockBlacklistToggle = new ButtonSetting("Block blacklist", false, "Blocks.Block blacklist"));
        this.registerSetting(
                this.blockBlacklist = new BlockListSetting(
                        "Blacklisted blocks",
                        "Block blacklist",
                        "Blocks.Block blacklist",
                        "Blocks.Blacklisted blocks"));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        this.activationTime.setVisible(this.requireLeftMouse.isToggled(), this);
        this.ignoredHeldItems.setVisible(this.ignoredHeldItemsToggle.isToggled(), this);
        this.blockWhitelist.setVisible(this.blockWhitelistToggle.isToggled(), this);
        this.blockBlacklist.setVisible(this.blockBlacklistToggle.isToggled(), this);
    }

    @Override
    public void onEnable() {
        this.resetState(true);
    }

    @Override
    public void onDisable() {
        this.resetState(true);
    }

    @ForgeEvents.SubscribeEvent
    public void onScrollSlot(PreSlotScrollEvent e) {
        if (this.hasSwapped) {
            if (this.overrideSwapBack.isToggled()) {
                int slot = Integer.compare(e.slot, 0);
                this.previousSlot = Math.floorMod(mc.player.inventory.selectedSlot - slot, 9);
            }

            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (this.hasSwapped) {
            if (this.overrideSwapBack.isToggled()) {
                this.previousSlot = e.slot;
            }

            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) {
            this.resetState(true);
        } else {
            if (this.spoofItem.isToggled()
                    && this.previousSlot != mc.player.inventory.selectedSlot
                    && this.previousSlot != -1) {
                ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelUpdate(true);
                ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelReset(true);
            }

            int currentTick = ++this.tickCounter;
            boolean leftMouseDown = Mouse.isButtonDown(0);
            this.updateLeftMouseState(leftMouseDown, currentTick);
            if (!mc.focused || mc.screen != null || mc.player.removed || !mc.player.abilities.canModifyWorld) {
                this.resetState(true);
            } else if (this.disableInCreative.isToggled() && mc.player.abilities.creativeMode) {
                this.resetState(true);
            } else {
                HitResult hoverResult = RotationUtils.rayTraceBlockIfNoEntityInFront(
                        mc.interactionManager.getReach(), mc.player.yaw, mc.player.pitch);
                BlockPos hoverPos = hoverResult != null && hoverResult.type == Type.BLOCK ? hoverResult.getPos() : null;
                this.updateHoverState(hoverPos, currentTick);
                if (hoverPos == null || this.isUnsupportedBlock(hoverPos)) {
                    this.resetSlot();
                } else if (this.onlyWhileCrouching.isToggled() && !mc.player.isSneaking()) {
                    this.resetSlot();
                } else {
                    if (this.requireLeftMouse.isToggled()) {
                        if (!leftMouseDown) {
                            this.resetSlot();
                            return;
                        }

                        if (!this.hasElapsed(
                                this.leftMouseDownSinceTick, this.activationTime.getInput(), currentTick)) {
                            this.resetSlot();
                            return;
                        }
                    }

                    if (!this.hasElapsed(this.hoverStartTick, this.hoverDelay.getInput(), currentTick)) {
                        this.resetSlot();
                    } else if (this.isUseBlocked()) {
                        this.resetSlot();
                    } else if (this.isBlockedBlock(hoverPos)) {
                        this.resetSlot();
                    } else if (this.blockWhitelistToggle.isToggled() && !this.isWhitelistedBlock(hoverPos)) {
                        this.resetSlot();
                    } else {
                        HitResult swapResult = mc.crosshairTarget;
                        BlockPos swapPos =
                                swapResult != null && swapResult.type == Type.BLOCK ? swapResult.getPos() : null;
                        if (swapPos != null && !this.isUnsupportedBlock(swapPos)) {
                            Block block = BlockUtils.getBlock(swapPos);
                            if (block.getMiningSpeed(mc.player, mc.world, swapPos) >= 1.0F) {
                                this.resetNextHover();
                            } else {
                                int slot = this.getTool(block);
                                if (slot == -1) {
                                    this.resetNextHover();
                                } else {
                                    if (this.previousSlot == -1 && slot != mc.player.inventory.selectedSlot) {
                                        this.previousSlot = mc.player.inventory.selectedSlot;
                                    }

                                    if (!this.hasSwapped) {
                                        this.setSlot(slot);
                                        this.resetNextHover();
                                    } else if (slot == mc.player.inventory.selectedSlot) {
                                        this.resetNextHover();
                                    } else {
                                        if (this.nextHoverSlot != slot) {
                                            this.nextHoverSlot = slot;
                                            this.nextHoverStartTick = currentTick;
                                        }

                                        if (this.hasElapsed(
                                                this.nextHoverStartTick, this.nextHoverDelay.getInput(), currentTick)) {
                                            this.setSlot(slot);
                                            this.resetNextHover();
                                        }
                                    }
                                }
                            }
                        } else {
                            this.resetSlot();
                        }
                    }
                }
            }
        }
    }

    private int getTool(Block block) {
        int currentSlot = mc.player.inventory.selectedSlot;
        ItemStack heldStack = mc.player.inventory.getItem(currentSlot);
        double bestSpeed = ItemSortScoring.getBlockBreakingSpeed(heldStack, block);
        int bestSlot = bestSpeed > 1.0 ? currentSlot : -1;
        bestSpeed = Math.max(1.0, bestSpeed);
        double bestDurability = this.getRemainingDurability(heldStack);

        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
            ItemStack stack = mc.player.inventory.getItem(i);
            double speed = ItemSortScoring.getBlockBreakingSpeed(stack, block);
            double durability = this.getRemainingDurability(stack);
            if (speed > bestSpeed
                    || bestSlot != -1 && speed == bestSpeed && bestDurability <= 0.05 && durability > bestDurability) {
                bestSpeed = speed;
                bestSlot = i;
                bestDurability = durability;
            }
        }

        return bestSlot;
    }

    private double getRemainingDurability(ItemStack stack) {
        return stack != null && stack.isDamageable() && stack.getMaxDamage() > 0
                ? (double) (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage()
                : 1.0;
    }

    private void updateLeftMouseState(boolean leftMouseDown, int currentTick) {
        if (leftMouseDown) {
            if (this.leftMouseDownSinceTick == -1) {
                this.leftMouseDownSinceTick = currentTick;
            }
        } else {
            this.leftMouseDownSinceTick = -1;
        }
    }

    private void updateHoverState(BlockPos hoverPos, int currentTick) {
        if (hoverPos == null) {
            this.hoverStartTick = -1;
        } else {
            if (this.hoverStartTick == -1) {
                this.hoverStartTick = currentTick;
            }
        }
    }

    private boolean isUseBlocked() {
        boolean useActive = Utils.isBindDown(mc.options.useKey) || mc.player.hasItemInUse();
        int heldItemSlot =
                this.hasSwapped && this.previousSlot != -1 ? this.previousSlot : mc.player.inventory.selectedSlot;
        return this.ignoredHeldItemsToggle.isToggled()
                        && this.ignoredHeldItems.matches(mc.player.inventory.getItem(heldItemSlot))
                ? true
                : useActive;
    }

    private boolean isBlockedBlock(BlockPos blockPos) {
        return !this.blockBlacklistToggle.isToggled() ? false : this.matchesBlockList(blockPos, this.blockBlacklist);
    }

    private boolean isUnsupportedBlock(BlockPos blockPos) {
        Block block = BlockUtils.getBlock(blockPos);
        return block == Blocks.BEDROCK || block == Blocks.BARRIER;
    }

    private boolean isWhitelistedBlock(BlockPos blockPos) {
        return this.blockWhitelist.getBlocks().isEmpty() ? false : this.matchesBlockList(blockPos, this.blockWhitelist);
    }

    private boolean matchesBlockList(BlockPos blockPos, BlockListSetting blockList) {
        BlockState state = BlockUtils.getBlockState(blockPos);
        Block hoveredBlock = state.getBlock();
        if (hoveredBlock != null && Block.REGISTRY.getKey(hoveredBlock) != null) {
            String registryId = ((Identifier) Block.REGISTRY.getKey(hoveredBlock)).toString();
            int meta = hoveredBlock.getMetadataFromState(state);
            String storageId = meta != 0 ? registryId + ":" + meta : registryId;
            return blockList.contains(storageId) || blockList.contains(registryId);
        } else {
            return false;
        }
    }

    private boolean hasElapsed(int startTick, double requiredMs, int currentTick) {
        int requiredTicks = this.getRequiredTicks(requiredMs);
        return requiredTicks <= 0 ? true : startTick != -1 && currentTick - startTick >= requiredTicks;
    }

    private int getRequiredTicks(double requiredMs) {
        return requiredMs <= 0.0 ? 0 : (int) Math.ceil(requiredMs / 50.0);
    }

    private void resetState(boolean resetTimers) {
        if (resetTimers) {
            this.tickCounter = 0;
            this.leftMouseDownSinceTick = -1;
            this.hoverStartTick = -1;
        }

        this.resetSlot();
    }

    private void resetSlot() {
        if (this.previousSlot != -1 && this.switchBackWhenDone.isToggled()) {
            this.setSlot(this.previousSlot);
        }

        this.previousSlot = -1;
        this.hasSwapped = false;
        this.resetNextHover();
    }

    private void resetNextHover() {
        this.nextHoverStartTick = -1;
        this.nextHoverSlot = -1;
    }

    private void setSlot(int currentItem) {
        if (currentItem != -1 && currentItem != mc.player.inventory.selectedSlot) {
            mc.player.inventory.selectedSlot = currentItem;
            this.hasSwapped = true;
            ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
        }
    }
}
