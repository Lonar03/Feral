package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class MurderMystery extends Module {
    private final ItemListSetting murderWeapons;
    private final ButtonSetting alert;
    private final ButtonSetting highlightMurderer;
    private final ButtonSetting highlightBow;
    private final ButtonSetting highlightInnocent;
    private final ButtonSetting highlightDead;
    private final ButtonSetting goldEsp;
    private final List<PlayerEntity> murderers = new ArrayList<>();
    private final List<PlayerEntity> hasBow = new ArrayList<>();
    private boolean override;

    public MurderMystery() {
        super("Murder Mystery", Module.category.minigames);
        this.registerSetting(this.murderWeapons = new ItemListSetting("Murder weapons"));
        this.addDefaultMurderWeapon(Items.IRON_SWORD);
        this.addDefaultMurderWeapon(Items.STONE_SWORD);
        this.addDefaultMurderWeapon(Items.IRON_SHOVEL);
        this.addDefaultMurderWeapon(Items.STICK);
        this.addDefaultMurderWeapon(Items.WOODEN_AXE);
        this.addDefaultMurderWeapon(Items.WOODEN_SWORD);
        this.addDefaultMurderWeapon(Items.STONE_SHOVEL);
        this.addDefaultMurderWeapon(Items.BLAZE_ROD);
        this.addDefaultMurderWeapon(Items.DIAMOND_SHOVEL);
        this.addDefaultMurderWeapon(Items.QUARTZ);
        this.addDefaultMurderWeapon(Items.PUMPKIN_PIE);
        this.addDefaultMurderWeapon(Items.GOLDEN_PICKAXE);
        this.addDefaultMurderWeapon(Items.APPLE);
        this.addDefaultMurderWeapon(Items.NAME_TAG);
        this.addDefaultMurderWeapon(Items.CARROT_ON_A_STICK);
        this.addDefaultMurderWeapon(Items.BONE);
        this.addDefaultMurderWeapon(Items.CARROT);
        this.addDefaultMurderWeapon(Items.GOLDEN_CARROT);
        this.addDefaultMurderWeapon(Items.COOKIE);
        this.addDefaultMurderWeapon(Items.DIAMOND_AXE);
        this.addDefaultMurderWeapon(Items.PRISMARINE_SHARD);
        this.addDefaultMurderWeapon(Items.COOKED_BEEF);
        this.addDefaultMurderWeapon(Items.NETHERBRICK);
        this.addDefaultMurderWeapon(Items.COOKED_CHICKEN);
        this.addDefaultMurderWeapon(Items.GOLDEN_SWORD);
        this.addDefaultMurderWeapon(Items.DIAMOND_SWORD);
        this.addDefaultMurderWeapon(Items.DIAMOND_HOE);
        this.addDefaultMurderWeapon(Items.SHEARS);
        this.addDefaultMurderWeapon(Items.FISH);
        this.addDefaultMurderWeapon(Items.DYE);
        this.addDefaultMurderWeapon(Items.BOAT);
        this.addDefaultMurderWeapon(Items.SPECKLED_MELON);
        this.addDefaultMurderWeapon(Items.BOOK);
        this.addDefaultMurderWeapon(Item.byBlock(Blocks.DOUBLE_PLANT));
        this.addDefaultMurderWeapon(Item.byBlock(Blocks.SPONGE));
        this.addDefaultMurderWeapon(Item.byBlock(Blocks.DEADBUSH));
        this.registerSetting(this.alert = new ButtonSetting("Alert murderer", true));
        this.registerSetting(this.highlightBow = new ButtonSetting("Highlight bow", true));
        this.registerSetting(this.highlightDead = new ButtonSetting("Highlight dead", true));
        this.registerSetting(this.goldEsp = new ButtonSetting("Gold ingots", true));
        this.registerSetting(this.highlightInnocent = new ButtonSetting("Highlight innocent", true));
        this.registerSetting(this.highlightMurderer = new ButtonSetting("Highlight murderer", true));
    }

    @Override
    public void onDisable() {
        this.clear();
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWordLast(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (!this.isMurderMystery()) {
                this.clear();
            } else {
                this.override = false;

                for (PlayerEntity en : mc.world.players) {
                    if (en != mc.player
                            && !en.isInvisible()
                            && (!AntiBot.isBot(en) || this.highlightDead.isToggled())) {
                        if (en.getDisplayItemInHand() != null) {
                            ItemStack heldStack = en.getDisplayItemInHand();
                            Item heldItem = heldStack.getItem();
                            if (this.murderWeapons.matches(heldStack)) {
                                if (!this.murderers.contains(en)) {
                                    this.murderers.add(en);
                                    if (this.alert.isToggled()) {
                                        mc.player.playSound("note.pling", 1.0F, 1.0F);
                                        Utils.sendMessage("&b"
                                                + en.getName()
                                                + " &7is the &cmurderer&7! "
                                                + Utils.getDistanceColor(mc.player.distanceTo(en))
                                                + (int) mc.player.distanceTo(en)
                                                + "m&r");
                                    }
                                }
                            } else if (heldItem instanceof BowItem
                                    && this.highlightBow.isToggled()
                                    && !this.hasBow.contains(en)) {
                                this.hasBow.add(en);
                            }
                        }

                        this.override = true;
                        int rgb = Color.green.getRGB();
                        if (this.murderers.contains(en) && this.highlightMurderer.isToggled()) {
                            rgb = Color.red.getRGB();
                        } else if (this.hasBow.contains(en) && this.highlightBow.isToggled()) {
                            rgb = Color.orange.getRGB();
                        } else if (!this.highlightInnocent.isToggled()) {
                            continue;
                        }

                        if (this.highlightDead.isToggled() || !(this.getBoundingBoxVolume(en) <= 0.009)) {
                            RenderUtils.renderEntity(en, 2, 0.0, 0.0, rgb, false);
                        }
                    }
                }

                if (!this.goldEsp.isToggled()) {
                    return;
                }

                float renderPartialTicks = ((IAccessorMinecraft) mc).getTimer().partialTick;
                int n4 = -331703;

                for (Entity entity : mc.world.entities) {
                    if (entity instanceof ItemEntity && entity.ticks >= 3) {
                        ItemEntity entityItem = (ItemEntity) entity;
                        if (entityItem.getItem().size != 0) {
                            Item getItem = entityItem.getItem().getItem();
                            if (getItem != null && getItem == Items.GOLD_INGOT) {
                                double n5 = entity.prevX + (entity.x - entity.prevX) * renderPartialTicks;
                                double n6 = entity.prevY + (entity.y - entity.prevY) * renderPartialTicks;
                                double n7 = entity.prevZ + (entity.z - entity.prevZ) * renderPartialTicks;
                                double n8 = mc.player.prevX + (mc.player.x - mc.player.prevX) * renderPartialTicks - n5;
                                double n9 = mc.player.prevY + (mc.player.y - mc.player.prevY) * renderPartialTicks - n6;
                                double n10 =
                                        mc.player.prevZ + (mc.player.z - mc.player.prevZ) * renderPartialTicks - n7;
                                GlStateManager.pushMatrix();
                                this.drawBox(n4, n5, n6, n7, MathHelper.sqrt(n8 * n8 + n9 * n9 + n10 * n10));
                                GlStateManager.popMatrix();
                            }
                        }
                    }
                }
            }
        }
    }

    public void drawBox(int n, double n4, double n5, double n6, double n7) {
        n4 -= mc.getEntityRenderDispatcher().cameraX;
        n5 -= mc.getEntityRenderDispatcher().cameraY;
        n6 -= mc.getEntityRenderDispatcher().cameraZ;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0F);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        float n8 = (n >> 16 & 0xFF) / 255.0F;
        float n9 = (n >> 8 & 0xFF) / 255.0F;
        float n10 = (n & 0xFF) / 255.0F;
        float min = Math.min(Math.max(0.2F, (float) (0.01F * n7)), 0.4F);
        RenderUtils.drawBoundingBox(
                new Box(n4 - min, n5, n6 - min, n4 + min, n5 + min * 2.0F, n6 + min), n8, n9, n10, 0.35F);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    private boolean isMurderMystery() {
        if (Utils.isHypixel()) {
            if (mc.player.getScoreboard() == null || mc.player.getScoreboard().getDisplayObjective(1) == null) {
                return false;
            }

            String d = mc.player.getScoreboard().getDisplayObjective(1).getDisplayName();
            if (!d.contains("MURDER") && !d.contains("MYSTERY")) {
                return false;
            }

            for (String l : Utils.getScoreBoardOld()) {
                String s = Utils.stripColor(l);
                if (s.contains("Role:") || s.contains("Innocents Left:")) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isEmpty() {
        return this.murderers.isEmpty() && this.hasBow.isEmpty() && !this.override;
    }

    private void clear() {
        this.override = false;
        this.murderers.clear();
        this.hasBow.clear();
    }

    private double getBoundingBoxVolume(Entity entity) {
        Box boundingBox = entity.getShape();
        if (boundingBox == null) {
            return 0.0;
        }

        double length = boundingBox.maxX - boundingBox.minX;
        double width = boundingBox.maxZ - boundingBox.minZ;
        double height = boundingBox.maxY - boundingBox.minY;
        return length * width * height;
    }

    private void addDefaultMurderWeapon(Item item) {
        if (item != null) {
            String registryId = ItemSearchIndex.getRegistryId(item);
            if (registryId != null) {
                this.murderWeapons.addItem(ItemSearchIndex.hasMultipleVariants(item) ? registryId + ":*" : registryId);
            }
        }
    }
}
