package keystrokesmod.module.impl.other;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.HoverEvent.Action;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class Debug extends Module {
    private ButtonSetting debugBlock;
    public ButtonSetting debugBPS;
    public ButtonSetting debugEntities;
    private ButtonSetting debugAttacked;
    private ButtonSetting debugHeld;
    private ButtonSetting alertPost;
    private ButtonSetting spawnDummy;
    private static final int DEBUG_MAX_LINE_WIDTH = 300;
    private static final int DEBUG_LINE_HEIGHT = 12;
    private static final int COPY_BUTTON_SPACE = 16;
    private final ArrayDeque<String> postQueue = new ArrayDeque<>();
    private final Debug.DebugPanel blockPanel = new Debug.DebugPanel();
    private final Debug.DebugPanel heldPanel = new Debug.DebugPanel();
    private Screen renderedScreen;
    private Debug.DebugPanel copiedPanel;
    private int copiedEntry = -1;
    private String copiedValue;
    private long copiedUntil;
    public RemoteClientPlayerEntity fakeEntity = null;
    private boolean sentFlying;

    public Debug() {
        super("Debug", Module.category.other);
        this.registerSetting(this.spawnDummy = new ButtonSetting("Spawn dummy", true));
        this.registerSetting(new DescriptionSetting("Visuals"));
        this.registerSetting(this.alertPost = new ButtonSetting("Alert post", false));
        this.registerSetting(this.debugBlock = new ButtonSetting("Debug block", true));
        this.registerSetting(this.debugHeld = new ButtonSetting("Debug held", false));
        this.registerSetting(this.debugBPS = new ButtonSetting("Show speed", true));
        this.registerSetting(this.debugEntities = new ButtonSetting("Render all entities", false));
        this.registerSetting(this.debugAttacked = new ButtonSetting("Debug attacked", true));
    }

    @Override
    public void onDisable() {
        this.resetState();
        this.resetDebugInteraction();
        if (this.fakeEntity != null) {
            mc.world.removeEntity(this.fakeEntity);
            this.fakeEntity = null;
        }
    }

    @Override
    public void onEnable() {
        if (this.spawnDummy.isToggled()) {
            this.fakeEntity = new RemoteClientPlayerEntity(mc.world, new GameProfile(mc.player.getUuid(), "Dummy"));
            this.fakeEntity.copyPositionAndRotationFrom(mc.player);
            mc.world.forceEntity(-8008, this.fakeEntity);
            this.fakeEntity.inventory.armor[0] = new ItemStack(Items.GOLDEN_HELMET);
            this.fakeEntity.setEquipment(0, new ItemStack(Blocks.WOOL));
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onSendPacket(SendPacketEvent e) {
        if (!this.alertPost.isToggled()) {
            this.resetState();
        } else if (!Utils.nullCheck()) {
            this.resetState();
        } else if (!e.isCanceled()) {
            Packet<?> packet = e.getPacket();
            if (packet != null) {
                if (isTickPacket(packet)) {
                    this.postQueue.clear();
                    this.sentFlying = true;
                } else if (isTransactionPacket(packet)) {
                    if (this.sentFlying && !this.postQueue.isEmpty()) {
                        Utils.sendMessage("&7Post packet: &b" + this.postQueue.peekFirst());
                    }

                    this.postQueue.clear();
                    this.sentFlying = false;
                } else {
                    if (this.sentFlying && isPostCheckPacket(packet)) {
                        this.postQueue.add(ClientPlatform.className(packet.getClass()));
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent ev) {
        if (ev.phase == ForgeEvents.TickEvent.Phase.END) {
            this.blockPanel.clear();
            this.heldPanel.clear();
            this.renderedScreen = null;
            if (!Utils.nullCheck()) {
                this.resetDebugInteraction();
            } else {
                this.renderedScreen = mc.screen;
                Window resolution = new Window(mc);
                boolean interactive = mc.screen instanceof ChatScreen;
                int mouseX = interactive ? Mouse.getX() * resolution.getWidth() / mc.width : -1;
                int mouseY = interactive
                        ? resolution.getHeight() - Mouse.getY() * resolution.getHeight() / mc.height - 1
                        : -1;
                boolean bothPanels = this.debugBlock.isToggled() && this.debugHeld.isToggled();
                int availableWidth = (resolution.getWidth() - 60 - (bothPanels ? 20 : 0)) / (bothPanels ? 2 : 1);
                int maxTextWidth = Math.min(300, Math.max(40, availableWidth - 16));
                if (this.debugBlock.isToggled()) {
                    this.blockPanel.render(
                            this.buildBlockDebugEntries(),
                            resolution,
                            false,
                            maxTextWidth,
                            mouseX,
                            mouseY,
                            interactive);
                }

                if (this.debugHeld.isToggled()) {
                    ItemStack stack = mc.player.getDisplayItemInHand();
                    if (stack != null) {
                        this.heldPanel.render(
                                this.buildHeldDebugEntries(stack),
                                resolution,
                                true,
                                maxTextWidth,
                                mouseX,
                                mouseY,
                                interactive);
                    }
                }

                if (interactive) {
                    Debug.DebugPanel hoveredPanel = this.getHoveredPanel(mouseX, mouseY);
                    if (hoveredPanel != null) {
                        Debug.DebugEntry entry = hoveredPanel.getEntry(mouseY);
                        if (entry != null) {
                            boolean copied = this.isCopied(hoveredPanel, entry);
                            String text = copied ? "Copied!" : "Copy " + entry.label;
                            int width = mc.textRenderer.getWidth(text);
                            int x = Math.max(4, Math.min(mouseX + 12, resolution.getWidth() - width - 8));
                            int y = mouseY + 14;
                            if (y + 15 > resolution.getHeight() - 16) {
                                y = mouseY - 16;
                            }

                            GuiElement.fill(x - 4, y - 3, x + width + 4, y + 11, -300607201);
                            mc.textRenderer.drawWithShadow(text, x, y, copied ? -8461656 : -1840398);
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGuiOpen(ForgeEvents.GuiOpenEvent event) {
        this.resetDebugInteraction();
    }

    @ForgeEvents.SubscribeEvent
    public void onDebugMouseInput(ForgeEvents.GuiScreenEvent.MouseInputEvent.Pre event) {
        if (event.gui instanceof ChatScreen && event.gui == this.renderedScreen && Utils.nullCheck()) {
            int mouseX = Mouse.getEventX() * event.gui.width / mc.width;
            int mouseY = event.gui.height - Mouse.getEventY() * event.gui.height / mc.height - 1;
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                Text component = mc.gui.getChat().getMessageAt(Mouse.getEventX(), Mouse.getEventY());
                if (component != null) {
                    Style style = component.getStyle();
                    HoverEvent hover = style.getHoverEvent();
                    if (style.getInsertion() != null
                            && hover != null
                            && "Click to copy full entity data"
                                    .equals(hover.getValue().getString())) {
                        Screen.setClipboard(style.getInsertion());
                        event.setCanceled(true);
                        return;
                    }
                }
            }

            Debug.DebugPanel panel = this.getHoveredPanel(mouseX, mouseY);
            if (panel != null) {
                int wheel = Mouse.getEventDWheel();
                if (wheel != 0 && panel.contentHeight > panel.height) {
                    panel.scroll = Math.max(
                            0,
                            Math.min(
                                    panel.contentHeight - panel.height, panel.scroll - Integer.signum(wheel) * 12 * 3));
                    event.setCanceled(true);
                } else {
                    if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                        Debug.DebugEntry entry = panel.getEntry(mouseY);
                        if (entry != null) {
                            Screen.setClipboard(entry.value);
                            this.copiedPanel = panel;
                            this.copiedEntry = panel.entries.indexOf(entry);
                            this.copiedValue = entry.value;
                            this.copiedUntil = System.currentTimeMillis() + 1200L;
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    private Debug.DebugPanel getHoveredPanel(int mouseX, int mouseY) {
        if (this.debugHeld.isToggled() && this.heldPanel.contains(mouseX, mouseY)) {
            return this.heldPanel;
        } else {
            return this.debugBlock.isToggled() && this.blockPanel.contains(mouseX, mouseY) ? this.blockPanel : null;
        }
    }

    private boolean isCopied(Debug.DebugPanel panel, Debug.DebugEntry entry) {
        return this.copiedPanel == panel
                && panel.entries.indexOf(entry) == this.copiedEntry
                && entry.value.equals(this.copiedValue)
                && System.currentTimeMillis() < this.copiedUntil;
    }

    private void resetDebugInteraction() {
        this.blockPanel.clear();
        this.heldPanel.clear();
        this.blockPanel.scroll = 0;
        this.heldPanel.scroll = 0;
        this.renderedScreen = null;
        this.copiedPanel = null;
        this.copiedEntry = -1;
        this.copiedValue = null;
        this.copiedUntil = 0L;
    }

    private List<Debug.DebugEntry> buildBlockDebugEntries() {
        List<Debug.DebugEntry> entries = new ArrayList<>();
        HitResult mouse =
                RotationUtils.rayCast(mc.interactionManager.getReach(), mc.player.yaw, mc.player.pitch, false);
        if (mouse != null && mouse.type == Type.BLOCK && mouse.getPos() != null) {
            BlockPos pos = mouse.getPos();
            Block block = BlockUtils.getBlock(pos);
            if (block != null && block != Blocks.AIR) {
                BlockState state = mc.world.getBlockState(pos);
                this.addDebugEntry(entries, "BlockPos", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                this.addDebugEntry(
                        entries,
                        "HitVec",
                        Utils.round(mouse.facePos.x, 3)
                                + ", "
                                + Utils.round(mouse.facePos.y, 3)
                                + ", "
                                + Utils.round(mouse.facePos.z, 3));
                this.addDebugEntry(entries, "Face", mouse.face.name());
                this.addDebugEntry(entries, "Unlocalized Name", block.getTranslationKey());
                this.addDebugEntry(entries, "Registry Name", ClientPlatform.registryName(block));

                for (Property<?> property : block.stateDefinition().properties()) {
                    Class<?> valueClass = property.getType();
                    String propName = property.getName();
                    Object currentValue = state.get(property);
                    Collection<?> allowedValues = property.values();
                    this.addDebugEntry(entries, "Property Name", propName);
                    this.addDebugEntry(entries, "Value Type", valueClass.getName());
                    if (currentValue != null) {
                        this.addDebugEntry(entries, "Current Value", currentValue);
                        if (valueClass.isEnum()) {
                            Enum<?> enumValue = (Enum<?>) currentValue;
                            this.addDebugEntry(entries, "Current Enum Name", enumValue.name());
                            this.addDebugEntry(entries, "Current Enum Ordinal", enumValue.ordinal());
                        }
                    }

                    this.addDebugEntry(entries, "Allowed Values", allowedValues);
                    if (valueClass.isEnum()) {
                        Object[] enumConstants = valueClass.getEnumConstants();
                        if (!Arrays.toString(enumConstants).equals(allowedValues.toString())) {
                            this.addDebugEntry(entries, "Enum Constants", Arrays.toString(enumConstants));
                        }
                    }
                }

                return entries;
            } else {
                return entries;
            }
        } else {
            return entries;
        }
    }

    private List<Debug.DebugEntry> buildHeldDebugEntries(ItemStack stack) {
        List<Debug.DebugEntry> entries = new ArrayList<>();
        Item item = stack.getItem();
        this.addDebugEntry(entries, "Display Name", Formatting.strip(stack.getHoverName()));
        this.addDebugEntry(entries, "Registry Name", Item.REGISTRY.getKey(item));
        this.addDebugEntry(entries, "Numeric ID", Item.getId(item));
        this.addDebugEntry(entries, "Metadata", stack.getMetadata());
        this.addDebugEntry(entries, "Unlocalized Name", stack.getTranslationKey());
        this.addDebugEntry(entries, "Class", item.getClass().getName());
        this.addDebugEntry(entries, "Held Slot", mc.player.inventory.selectedSlot + 1);
        this.addDebugEntry(entries, "Stack Size", stack.size + "/" + stack.getMaxSize());
        this.addDebugEntry(entries, "Rarity", stack.getRarity().name);
        this.addDebugEntry(entries, "Damage", stack.getDamage() + "/" + stack.getMaxDamage());
        this.addDebugEntry(entries, "Damageable", stack.isDamageable());
        if (stack.isDamageable()) {
            this.addDebugEntry(entries, "Durability Remaining", stack.getMaxDamage() - stack.getDamage());
        }

        this.addDebugEntry(entries, "Repair Cost", stack.getRepairCost());
        this.addDebugEntry(entries, "Enchanted", stack.hasEnchantments());
        this.addDebugEntry(entries, "Enchantability", ClientPlatform.enchantability(item, stack));
        this.addDebugEntry(entries, "Has Effect", stack.hasEnchantmentGlint());
        this.addDebugEntry(entries, "Use Action", stack.getUseAction());
        this.addDebugEntry(entries, "Max Use Duration", stack.getUseDuration());
        Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers();
        this.addDebugEntry(entries, "Attribute Modifiers", modifiers.size());
        if (modifiers.isEmpty()) {
            this.addDebugEntry(entries, "Attributes", "None");
        } else {
            for (Entry<String, AttributeModifier> entry : modifiers.entries()) {
                AttributeModifier modifier = entry.getValue();
                this.addDebugEntry(entries, "Attribute Name", entry.getKey());
                this.addDebugEntry(entries, "Modifier Name", modifier.getName());
                this.addDebugEntry(entries, "Amount", Utils.round(modifier.get(), 6));
                this.addDebugEntry(entries, "Operation", modifier.getOperation());
                this.addDebugEntry(entries, "UUID", modifier.getId());
            }
        }

        this.addDebugEntry(entries, "NBT", stack.hasNbt() ? stack.getNbt() : "None");
        return entries;
    }

    private void addDebugEntry(List<Debug.DebugEntry> entries, String label, Object value) {
        entries.add(new Debug.DebugEntry(label, String.valueOf(value)));
    }

    private void drawCopyIcon(int x, int y, boolean hovered, boolean copied) {
        int color = copied ? -8461656 : (hovered ? -8332305 : -8812910);
        if (copied) {
            GuiElement.fill(x + 1, y + 5, x + 3, y + 7, color);
            GuiElement.fill(x + 3, y + 7, x + 5, y + 9, color);
            GuiElement.fill(x + 5, y + 5, x + 7, y + 7, color);
            GuiElement.fill(x + 7, y + 3, x + 9, y + 5, color);
        } else {
            GuiElement.fill(x + 2, y + 1, x + 8, y + 2, color);
            GuiElement.fill(x + 1, y + 2, x + 2, y + 10, color);
            GuiElement.fill(x + 8, y + 2, x + 9, y + 10, color);
            GuiElement.fill(x + 2, y + 10, x + 8, y + 11, color);
            GuiElement.fill(x + 3, y, x + 7, y + 3, color);
            GuiElement.fill(x + 3, y + 5, x + 7, y + 6, color);
            GuiElement.fill(x + 3, y + 7, x + 7, y + 8, color);
        }
    }

    public void showEntityDebugReport(String report) {
        if (this.shouldDebugAttacked() && Utils.nullCheck()) {
            LiteralText copy = new LiteralText("§b[Copy full entity data]");
            copy.setStyle(new Style()
                    .setInsertion(report)
                    .setHoverEvent(
                            new HoverEvent(Action.SHOW_TEXT, new LiteralText("Click to copy full entity data"))));
            mc.player.sendMessage(copy);
        }
    }

    public boolean shouldDebugAttacked() {
        return this.isEnabled() && this.debugAttacked.isToggled();
    }

    private static boolean isPostCheckPacket(Packet<?> packet) {
        return packet instanceof PlayerAbilitiesC2SPacket
                || packet instanceof SelectSlotC2SPacket
                || packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof PlayerUseC2SPacket
                || packet instanceof PlayerHandActionC2SPacket
                || packet instanceof ArmSwingC2SPacket
                || packet instanceof InventoryMenuClickSlotC2SPacket
                || packet instanceof PlayerMovementActionC2SPacket;
    }

    private static boolean isTickPacket(Packet<?> packet) {
        return packet instanceof PlayerMoveC2SPacket;
    }

    private static boolean isTransactionPacket(Packet<?> packet) {
        return packet instanceof InventoryMenuConfirmC2SPacket;
    }

    private void resetState() {
        this.postQueue.clear();
        this.sentFlying = false;
    }

    private static class DebugEntry {
        private final String label;
        private final String value;
        private List<String> lines;
        private int y;
        private int height;

        private DebugEntry(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private class DebugPanel {
        private List<Debug.DebugEntry> entries = new ArrayList<>();
        private int x;
        private final int y = 20;
        private int width;
        private int height;
        private int contentHeight;
        private int scroll;

        private DebugPanel() {}

        private void clear() {
            this.entries.clear();
            this.width = 0;
            this.height = 0;
            this.contentHeight = 0;
        }

        private void render(
                List<Debug.DebugEntry> entries,
                Window resolution,
                boolean rightAligned,
                int maxTextWidth,
                int mouseX,
                int mouseY,
                boolean interactive) {
            if (entries.isEmpty()) {
                this.scroll = 0;
            } else {
                this.entries = entries;
                int textWidth = 0;

                for (Debug.DebugEntry entry : entries) {
                    entry.lines = Debug.mc.textRenderer.split("§7" + entry.label + ": §b" + entry.value, maxTextWidth);
                    entry.height = Math.max(1, entry.lines.size()) * 12;
                    this.contentHeight = this.contentHeight + entry.height;

                    for (String line : entry.lines) {
                        textWidth = Math.max(textWidth, Debug.mc.textRenderer.getWidth(line));
                    }
                }

                this.width = textWidth + (interactive ? 16 : 0);
                this.height =
                        Math.min(this.contentHeight, Math.max(0, resolution.getHeight() - 20 - (interactive ? 30 : 4)));
                this.x = rightAligned ? Math.max(4, resolution.getWidth() - this.width - 30) : 30;
                this.scroll = interactive ? Math.max(0, Math.min(this.scroll, this.contentHeight - this.height)) : 0;
                int entryY = 20 - this.scroll;
                RenderUtils.scissorPushGui(this.x - 2, 20.0, this.width + 4, this.height);

                try {
                    for (Debug.DebugEntry entry : entries) {
                        entry.y = entryY;
                        entryY += entry.height;
                        if (entry.y + entry.height > 20 && entry.y < 20 + this.height) {
                            boolean hovered = interactive
                                    && this.contains(mouseX, mouseY)
                                    && mouseY >= entry.y
                                    && mouseY < entry.y + entry.height;
                            boolean copied = interactive && Debug.this.isCopied(this, entry);
                            if (hovered) {
                                GuiElement.fill(
                                        this.x - 2,
                                        entry.y,
                                        this.x + this.width + 2,
                                        entry.y + entry.height,
                                        -2143268254);
                            }

                            int textY = entry.y + 2;

                            for (String line : entry.lines) {
                                if (textY + Debug.mc.textRenderer.fontHeight > 20 && textY < 20 + this.height) {
                                    float var10002 = this.x;
                                    Debug.mc.textRenderer.drawWithShadow(line, var10002, textY, -1);
                                }

                                textY += 12;
                            }

                            if (interactive) {
                                Debug.this.drawCopyIcon(this.x + textWidth + 5, entry.y, hovered, copied);
                            }
                        }
                    }
                } finally {
                    RenderUtils.scissorPop();
                }

                if (interactive && this.contentHeight > this.height && this.height > 0) {
                    int thumbHeight = Math.max(8, this.height * this.height / this.contentHeight);
                    int thumbY = 20 + (this.height - thumbHeight) * this.scroll / (this.contentHeight - this.height);
                    GuiElement.fill(this.x + this.width + 3, 20, this.x + this.width + 4, 20 + this.height, 1348364918);
                    GuiElement.fill(
                            this.x + this.width + 3, thumbY, this.x + this.width + 4, thumbY + thumbHeight, -8332305);
                }
            }
        }

        private boolean contains(int mouseX, int mouseY) {
            return this.width > 0
                    && this.height > 0
                    && mouseX >= this.x - 2
                    && mouseX < this.x + this.width + 2
                    && mouseY >= 20
                    && mouseY < 20 + this.height;
        }

        private Debug.DebugEntry getEntry(int mouseY) {
            for (Debug.DebugEntry entry : this.entries) {
                if (mouseY >= entry.y && mouseY < entry.y + entry.height) {
                    return entry;
                }
            }

            return null;
        }
    }
}
