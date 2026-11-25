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
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
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
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;
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
        // Zombies guns and perk items (ported from MeowerClient)
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
                        new ButtonSetting("Edit position", () -> mc.openScreen(new FastPlace.EditScreen())));
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
                        this.renderBlockCount((int) alpha);
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
                            this.renderBlockCount((int) alpha);
                        }
                    }
                }
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

    public void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        this.relativePosX = absoluteX / w;
        this.relativePosY = absoluteY / h;
    }

    public void resetPosition() {
        this.relativePosX = Float.NaN;
        this.relativePosY = Float.NaN;
        this.posX = Float.NaN;
        this.posY = Float.NaN;
        this.syncPosition();
    }

    private void syncPosition() {
        this.syncPosition(new Window(mc));
    }

    private void syncPosition(Window resolution) {
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (Float.isNaN(this.posX) || Float.isNaN(this.posY)) {
                this.posX = w / 2.0F + 9.0F;
                this.posY = h / 2.0F - mc.textRenderer.fontHeight / 2.0F + 1.0F;
            }

            this.relativePosX = this.posX / w;
            this.relativePosY = this.posY / h;
        }

        this.posX = this.relativePosX * w;
        this.posY = this.relativePosY * h;
    }

    private void renderBlockCount(int alpha) {
        this.renderBlockCount(alpha, false);
    }

    private void renderBlockCount(int alpha, boolean editing) {
        int blocks = this.getTotalBlocks();
        if (editing && blocks == 0) {
            blocks = 64;
        }

        String color = "§";
        if (blocks <= 5) {
            color = color + "c";
        } else if (blocks <= 15) {
            color = color + "6";
        } else if (blocks <= 25) {
            color = color + "e";
        } else {
            color = "";
        }

        int colorAlpha = Utils.mergeAlpha(-1, alpha);
        Window scaledResolution = new Window(mc);
        this.syncPosition(scaledResolution);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        mc.textRenderer.drawWithShadow(
                color + blocks + " §rblock" + (blocks == 1 ? "" : "s"), this.posX, this.posY, colorAlpha);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
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
            FastPlace.this.syncPosition(new Window(this.minecraft));
            this.actualX = FastPlace.this.posX;
            this.actualY = FastPlace.this.posY;
        }

        public void render(int mouseX, int mouseY, float partialTicks) {
            Window resolution = new Window(this.minecraft);
            if (!this.dragging) {
                FastPlace.this.syncPosition(resolution);
                this.actualX = FastPlace.this.posX;
                this.actualY = FastPlace.this.posY;
            }

            fill(0, 0, this.width, this.height, -1308622848);
            FastPlace.this.setAbsolutePosition(this.actualX, this.actualY, resolution);
            FastPlace.this.renderBlockCount(255, true);
            this.actualX = FastPlace.this.posX;
            this.actualY = FastPlace.this.posY;
            String message = "Edit the position by dragging.";
            int textX = resolution.getWidth() / 2 - this.textRenderer.getWidth(message) / 2;
            int textY = resolution.getHeight() / 2 - 20;
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
                    int blocks = FastPlace.this.getTotalBlocks();
                    if (blocks == 0) {
                        blocks = 64;
                    }

                    String text = blocks + " block" + (blocks == 1 ? "" : "s");
                    int textWidth = this.textRenderer.getWidth(text);
                    int textHeight = this.textRenderer.fontHeight;
                    float minX = FastPlace.this.posX - 2.0F;
                    float minY = FastPlace.this.posY - 2.0F;
                    float maxX = FastPlace.this.posX + textWidth + 2.0F;
                    float maxY = FastPlace.this.posY + textHeight + 2.0F;
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
                FastPlace.this.resetPosition();
                this.actualX = FastPlace.this.posX;
                this.actualY = FastPlace.this.posY;
            }
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }
}
