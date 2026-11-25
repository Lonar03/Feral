package keystrokesmod.module.impl.movement;

import keystrokesmod.event.ClientLookEvent;
import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.world.HitResult.Type;

public class Jump45 extends Module {
    private final ButtonSetting flying;
    private final ButtonSetting holdingBlocks;
    private final ButtonSetting holdingBow;
    private final ButtonSetting holdingThrowable;
    private final ButtonSetting holdingRod;
    private final ButtonSetting inLiquid;
    private final ButtonSetting targeting;
    private boolean rotating;
    private float offset;
    private boolean sentInteraction;
    private boolean tracingPlayerRotations;
    private float mouseYaw;
    private float mousePitch;

    public Jump45() {
        super("45 Jump", Module.category.movement);
        this.registerSetting(new DescriptionSetting("Disable while..."));
        this.registerSetting(this.flying = new ButtonSetting("Flying", false));
        this.registerSetting(this.holdingBlocks = new ButtonSetting("Holding blocks", true));
        this.registerSetting(this.holdingBow = new ButtonSetting("Holding bow", true));
        this.registerSetting(this.holdingThrowable = new ButtonSetting("Holding throwable", true));
        this.registerSetting(this.holdingRod = new ButtonSetting("Holding rod", true));
        this.registerSetting(this.inLiquid = new ButtonSetting("In liquid", false));
        this.registerSetting(this.targeting = new ButtonSetting("Targeting", true));
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreUpdate(PreUpdateEvent event) {
        RotationHelper rotations = RotationHelper.get();
        if (!rotations.isActive() && this.shouldRotate()) {
            if (!this.rotating) {
                this.rotating = true;
                this.offset = 45.0F + (float) (-0.3F + 0.6F * Math.random());
            }

            rotations.setRotations(mc.player.yaw + this.offset, mc.player.pitch);
        } else {
            this.rotating = false;
        }
    }

    private boolean shouldRotate() {
        if (mc.player != null
                && mc.world != null
                && !mc.player.onGround
                && mc.player.isSprinting()
                && !this.sentInteraction) {
            float strafe = 0.0F;
            if (mc.options.leftKey.isPressed()) {
                strafe++;
            }

            if (mc.options.rightKey.isPressed()) {
                strafe--;
            }

            if (strafe == 0.0F
                    && (!this.flying.isToggled() || !mc.player.abilities.flying)
                    && (!this.inLiquid.isToggled() || !mc.player.isInWater() && !mc.player.isInLava())) {
                ItemStack stack = mc.player.getDisplayItemInHand();
                Item item = stack == null ? null : stack.getItem();
                return item == null
                                || (!this.holdingBlocks.isToggled() || !this.isScaffoldBlock(stack))
                                        && (!this.holdingBow.isToggled() || item != Items.BOW)
                                        && (!this.holdingThrowable.isToggled() || !this.isThrowable(stack))
                                        && (!this.holdingRod.isToggled() || item != Items.FISHING_ROD)
                        ? !this.targeting.isToggled() || !this.isTargeting()
                        : false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isScaffoldBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        return (block == Blocks.GLASS
                        || block.isOpaque()
                                && block != Blocks.GRAVEL
                                && block != Blocks.SAND
                                && block != Blocks.SOUL_SAND
                                && block != Blocks.TNT
                                && block != Blocks.CRAFTING_TABLE
                                && block != Blocks.FURNACE
                                && block != Blocks.DISPENSER
                                && block != Blocks.DROPPER
                                && block != Blocks.NOTEBLOCK
                                && block != Blocks.COMMAND_BLOCK)
                && block != Blocks.PUMPKIN;
    }

    private boolean isThrowable(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.FIRE_CHARGE
                || item == Items.ENDER_PEARL
                || item == Items.ENDER_EYE
                || item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.NETHER_STAR
                || item == Items.POTION && PotionItem.isSplashPotion(stack.getMetadata());
    }

    private boolean isTargeting() {
        return KillAura.target != null
                || ModuleManager.aimAssist != null
                        && ModuleManager.aimAssist.isEnabled()
                        && "Silent".equals(ModuleManager.aimAssist.getInfo())
                        && ModuleManager.aimAssist.getAimAssistTarget() != null
                || mc.crosshairTarget != null && mc.crosshairTarget.entity != null;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST, receiveCanceled = true)
    public void onSendPacket(SendPacketEvent event) {
        this.trackPacket(event.getPacket());
    }

    @ForgeEvents.SubscribeEvent
    public void onNoEventPacket(NoEventPacketEvent event) {
        this.trackPacket(event.getPacket());
    }

    private void trackPacket(Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket) {
            this.sentInteraction = false;
        } else if (packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerUseC2SPacket) {
            this.sentInteraction = true;
        } else if (packet instanceof PlayerHandActionC2SPacket) {
            Action action = ((PlayerHandActionC2SPacket) packet).getAction();
            if (action == Action.START_DESTROY_BLOCK
                    || action == Action.STOP_DESTROY_BLOCK
                    || action == Action.ABORT_DESTROY_BLOCK) {
                this.sentInteraction = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent event) {
        if (this.rotating
                && event.buttonstate
                && (event.button == 0 || event.button == 1)
                && mc.player != null
                && mc.gameRenderer != null) {
            float previousHeadYaw = mc.player.headYaw;
            this.mouseYaw = mc.player.yaw;
            this.mousePitch = mc.player.pitch;
            mc.player.headYaw = this.mouseYaw;
            this.tracingPlayerRotations = true;

            try {
                mc.gameRenderer.pick(1.0F);
            } finally {
                this.tracingPlayerRotations = false;
                if (mc.crosshairTarget == null || mc.crosshairTarget.type == Type.MISS) {
                    mc.player.headYaw = previousHeadYaw;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onClientLook(ClientLookEvent event) {
        if (this.tracingPlayerRotations) {
            event.yaw = this.mouseYaw;
            event.pitch = this.mousePitch;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoinWorld(ForgeEvents.EntityJoinWorldEvent event) {
        if (event.entity == mc.player) {
            this.reset();
        }
    }

    private void reset() {
        this.rotating = false;
        this.offset = 0.0F;
        this.sentInteraction = false;
        this.tracingPlayerRotations = false;
    }
}
