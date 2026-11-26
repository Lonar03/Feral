package keystrokesmod.module.impl.player;

import keystrokesmod.event.RightClickDelayTickEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockCountHud;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult.Type;

public class FastPlace extends Module {
    public SliderSetting tickDelay;
    public SliderSetting activationTime;
    public ButtonSetting blocksOnly;
    public ButtonSetting gunException;
    public ButtonSetting perkException;
    public ItemListSetting perkItems;
    public ButtonSetting pitchCheck;
    public ButtonSetting showBlockCount;
    public ButtonSetting editPosition;
    public ButtonSetting ignoredHeldItemsToggle;
    public ItemListSetting ignoredHeldItems;
    public ButtonSetting blockBlacklistToggle;
    public BlockListSetting blockBlacklist;
    private final BlockCountHud blockCountHud = new BlockCountHud(mc, this::getTotalBlocks);
    private long rightClickStartTime;
    private Timer fadeTimer;
    private Timer fadeInTimer;
    private float previousAlpha;

    public FastPlace() {
        super("Fast Place", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.tickDelay = new SliderSetting("Delay", " tick", 1.0, 0.0, 3.0, 1.0));
        this.registerSetting(this.activationTime = new SliderSetting("Minimum hold time", "ms", 0.0, 0.0, 100.0, 5.0));
        this.registerSetting(this.blocksOnly = new ButtonSetting("Blocks only", true));
        // Zombies guns and perk items
        this.registerSetting(this.gunException = new ButtonSetting("Gun exception", false));
        this.registerSetting(this.perkException = new ButtonSetting("Perk exception", false));
        this.perkItems = new ItemListSetting("Perk items");
        // Zombies perk/consumable items: redstone dust, blaze powder, gray dye, gold nugget, sugar.
        this.perkItems.addItem("minecraft:redstone");
        this.perkItems.addItem("minecraft:blaze_powder");
        this.perkItems.addItem("minecraft:dye:8");
        this.perkItems.addItem("minecraft:gold_nugget");
        this.perkItems.addItem("minecraft:sugar");
        this.registerSetting(this.perkItems);
        this.registerSetting(this.pitchCheck = new ButtonSetting("Pitch check", false));
        this.registerSetting(this.showBlockCount = new ButtonSetting("Show block count", false));
        this.registerSetting(
                this.editPosition =
                        new ButtonSetting("Edit position", () -> mc.openScreen(this.blockCountHud.editScreen())));
        this.registerSetting(
                this.ignoredHeldItemsToggle = new ButtonSetting(
                        "Held item blacklist",
                        false,
                        "Ignore held items",
                        "Restrict held items",
                        "Allow while holding"));
        this.registerSetting(this.ignoredHeldItems = new ItemListSetting("Held items", new String[] {"Items"}));
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
    public void onDisable() {
        this.rightClickStartTime = 0L;
    }

    @Override
    public void guiUpdate() {
        this.editPosition.setVisible(this.showBlockCount.isToggled(), this);
        this.gunException.setVisible(this.blocksOnly.isToggled(), this);
        boolean perkOptions = this.blocksOnly.isToggled() && this.gunException.isToggled();
        this.perkException.setVisible(perkOptions, this);
        this.perkItems.setVisible(perkOptions && this.perkException.isToggled(), this);
        this.ignoredHeldItems.setVisible(this.ignoredHeldItemsToggle.isToggled(), this);
        this.blockBlacklist.setVisible(this.blockBlacklistToggle.isToggled(), this);
    }

    @ForgeEvents.SubscribeEvent
    public void onRightClickDelayTick(RightClickDelayTickEvent e) {
        if (!Utils.nullCheck() || !mc.focused) {
            this.rightClickStartTime = 0L;
        } else if (!this.isRightClickActive()) {
            this.rightClickStartTime = 0L;
        } else {
            long now = System.currentTimeMillis();
            if (this.rightClickStartTime == 0L) {
                this.rightClickStartTime = now;
            }

            if (this.canFastPlace(now, true)) {
                int delay = (int) this.tickDelay.getInput();
                if (delay == 0) {
                    ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
                } else {
                    if (delay == 4) {
                        return;
                    }

                    if (((IAccessorMinecraft) mc).getRightClickDelayTimer() > delay) {
                        ((IAccessorMinecraft) mc).setRightClickDelayTimer(delay);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (Utils.nullCheck() && e.getPacket() instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket packet = (PlayerUseC2SPacket) e.getPacket();
            if (packet.getFace() == 255) {
                ItemStack packetStack = packet.getItemInHand();
                if (packetStack != null && packetStack.getItem() instanceof BlockItem) {
                    if (this.canFastPlace(System.currentTimeMillis(), true)) {
                        if (Math.random() < 0.7) {
                            e.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent ev) {
        if (ev.phase == ForgeEvents.TickEvent.Phase.END) {
            if (Utils.nullCheck()) {
                if (mc.screen != null) {
                    this.fadeTimer = null;
                    this.fadeInTimer = null;
                    this.previousAlpha = 0.0F;
                } else {
                    boolean fastPlaceEnabled = ModuleManager.fastPlace != null && ModuleManager.fastPlace.isEnabled();
                    boolean showBlockCountToggled =
                            fastPlaceEnabled && ModuleManager.fastPlace.showBlockCount.isToggled();
                    ItemStack heldItem = mc.player.getDisplayItemInHand();
                    boolean holdingPlaceableBlock = heldItem != null
                            && heldItem.getItem() instanceof BlockItem
                            && Utils.canBePlaced((BlockItem) heldItem.getItem());
                    boolean shouldShow = showBlockCountToggled
                            && mc.screen == null
                            && holdingPlaceableBlock
                            && this.canFastPlace(System.currentTimeMillis(), false);
                    if (shouldShow) {
                        if (this.fadeTimer != null) {
                            this.fadeTimer = null;
                            (this.fadeInTimer = new Timer(150.0F)).start();
                        } else if (this.fadeInTimer == null && this.previousAlpha < 255.0F) {
                            (this.fadeInTimer = new Timer(150.0F)).start();
                        }

                        float alpha;
                        if (this.fadeInTimer != null) {
                            alpha = this.fadeInTimer.getValueFloat(10.0F, 255.0F, 1);
                            if (alpha >= 255.0F) {
                                alpha = 255.0F;
                                this.fadeInTimer = null;
                            }
                        } else {
                            alpha = 255.0F;
                        }

                        this.previousAlpha = alpha;
                        this.blockCountHud.render((int) alpha);
                    } else {
                        if (this.previousAlpha <= 10.0F) {
                            this.fadeTimer = null;
                            this.fadeInTimer = null;
                            this.previousAlpha = 0.0F;
                            return;
                        }

                        if (this.fadeTimer == null) {
                            (this.fadeTimer = new Timer(150.0F)).start();
                            this.fadeInTimer = null;
                        }

                        float alpha = 255.0F - this.fadeTimer.getValueInt(0, 255, 1);
                        if (alpha < 0.0F) {
                            alpha = 0.0F;
                        }

                        this.previousAlpha = alpha;
                        if (alpha > 10.0F) {
                            this.blockCountHud.render((int) alpha);
                        }
                    }
                }
            }
        }
    }

    public float getPosX() {
        return this.blockCountHud.getPosX();
    }

    public float getPosY() {
        return this.blockCountHud.getPosY();
    }

    public float getRelativePosX() {
        return this.blockCountHud.getRelativePosX();
    }

    public float getRelativePosY() {
        return this.blockCountHud.getRelativePosY();
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.blockCountHud.setRelativePosition(normalizedX, normalizedY);
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        this.blockCountHud.setAbsolutePosition(absoluteX, absoluteY);
    }

    private boolean isBlockedHoverBlock() {
        if (this.blockBlacklistToggle.isToggled()
                && mc.crosshairTarget != null
                && mc.crosshairTarget.type == Type.BLOCK) {
            BlockPos hoveredPos = mc.crosshairTarget.getPos();
            if (hoveredPos == null) {
                return false;
            } else {
                BlockState state = mc.world.getBlockState(hoveredPos);
                Block hoveredBlock = state.getBlock();
                if (hoveredBlock != null && Block.REGISTRY.getKey(hoveredBlock) != null) {
                    String registryId = ((Identifier) Block.REGISTRY.getKey(hoveredBlock)).toString();
                    int meta = hoveredBlock.getMetadataFromState(state);
                    String storageId = meta != 0 ? registryId + ":" + meta : registryId;
                    return this.blockBlacklist.contains(storageId) || this.blockBlacklist.contains(registryId);
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    private boolean isRightClickActive() {
        return Utils.isBindDown(mc.options.useKey) || mc.player.hasItemInUse();
    }

    /** Zombies guns are non-sword tools: pickaxes, axes, shovels, hoes, shears, flint and steel, plus dyes. */
    private static boolean isGun(ItemStack stack) {
        net.minecraft.item.Item item = stack.getItem();
        return item instanceof net.minecraft.item.ToolItem
                || item instanceof net.minecraft.item.HoeItem
                || item instanceof net.minecraft.item.ShearsItem
                || item instanceof net.minecraft.item.FlintAndSteelItem
                || item instanceof net.minecraft.item.DyeItem;
    }

    private boolean canFastPlace(long now, boolean requireActivationDelay) {
        if (this.blocksOnly.isToggled()) {
            ItemStack item = mc.player.getDisplayItemInHand();
            boolean allowed = item != null
                    && (item.getItem() instanceof BlockItem
                            || this.gunException.isToggled()
                                    && (isGun(item) || this.perkException.isToggled() && this.perkItems.matches(item)));
            if (!allowed) {
                return false;
            }
        }

        if (this.pitchCheck.isToggled() && mc.player.pitch < 70.0F) {
            return false;
        } else if (this.ignoredHeldItemsToggle.isToggled()
                && this.ignoredHeldItems.matches(mc.player.getDisplayItemInHand())) {
            return false;
        } else {
            return this.isBlockedHoverBlock()
                    ? false
                    : !requireActivationDelay
                            || now - this.rightClickStartTime >= (long) this.activationTime.getInput();
        }
    }

    public int getTotalBlocks() {
        int totalBlocks = 0;
        if (mc.player != null && mc.player.inventory != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.inventory.items[i];
                if (stack != null
                        && stack.getItem() instanceof BlockItem
                        && Utils.canBePlaced((BlockItem) stack.getItem())
                        && stack.size > 0) {
                    totalBlocks += stack.size;
                }
            }

            return totalBlocks;
        } else {
            return 0;
        }
    }
}
