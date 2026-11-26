package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.OverlayEntityScans;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.WorldEntityScan.Kind;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class ItemESP extends Module {
    private final ButtonSetting disableDiamond;
    private final ButtonSetting disableEmerald;
    private final ButtonSetting disableGold;
    private final ButtonSetting disableIron;

    public ItemESP() {
        super("ItemESP", Module.category.render);
        this.liteModule = true;
        this.registerSetting(this.disableDiamond = new ButtonSetting("Disable diamond", false));
        this.registerSetting(this.disableEmerald = new ButtonSetting("Disable emerald", false));
        this.registerSetting(this.disableGold = new ButtonSetting("Disable gold", false));
        this.registerSetting(this.disableIron = new ButtonSetting("Disable iron", false));
    }

    public boolean hasResourceOverlay() {
        return !this.disableIron.isToggled()
                || !this.disableGold.isToggled()
                || !this.disableDiamond.isToggled()
                || !this.disableEmerald.isToggled();
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (!this.hasResourceOverlay()) {
            return;
        }
        HashMap<Item, ArrayList<ItemEntity>> itemsMap = new HashMap<>();
        HashMap<Double, Integer> colorMap = new HashMap<>();

        for (Entity entity : OverlayEntityScans.render(e, mc, Kind.ITEMS)) {
            if (entity instanceof ItemEntity && entity.ticks >= 3) {
                ItemEntity entityItem = (ItemEntity) entity;
                ItemStack stack = entityItem.getItem();
                if (stack.size != 0) {
                    Item currentItem = stack.getItem();
                    if (currentItem != null) {
                        int stackSize = stack.size;
                        double colorDouble = this.getColorForItem(currentItem, entity.x, entity.y, entity.z);
                        Integer existingStackCount = colorMap.get(colorDouble);
                        int newStackCount;
                        if (existingStackCount == null) {
                            newStackCount = stackSize;
                            ArrayList<ItemEntity> itemList = itemsMap.get(currentItem);
                            if (itemList == null) {
                                itemList = new ArrayList<>(1);
                                itemsMap.put(currentItem, itemList);
                            }

                            itemList.add(entityItem);
                        } else {
                            newStackCount = existingStackCount + stackSize;
                        }

                        colorMap.put(colorDouble, newStackCount);
                    }
                }
            }
        }

        if (!itemsMap.isEmpty()) {
            float renderPartialTicks = ((IAccessorMinecraft) mc).getTimer().partialTick;
            Iterator var30 = itemsMap.entrySet().iterator();

            while (true) {
                Entry<Item, ArrayList<ItemEntity>> entry;
                Item item;
                int boxColor;
                int textColor;
                while (true) {
                    if (!var30.hasNext()) {
                        return;
                    }

                    entry = (Entry<Item, ArrayList<ItemEntity>>) var30.next();
                    item = entry.getKey();
                    if (item == Items.IRON_INGOT && !this.disableIron.isToggled()) {
                        boxColor = -1;
                        textColor = -1;
                        break;
                    }

                    if (item == Items.GOLD_INGOT && !this.disableGold.isToggled()) {
                        boxColor = -331703;
                        textColor = -152;
                        break;
                    }

                    if (item == Items.DIAMOND && !this.disableDiamond.isToggled()) {
                        boxColor = -10362113;
                        textColor = -7667713;
                        break;
                    }

                    if (item == Items.EMERALD && !this.disableEmerald.isToggled()) {
                        boxColor = -15216030;
                        textColor = -14614644;
                        break;
                    }
                }

                for (ItemEntity entityItem2 : entry.getValue()) {
                    double itemColor = this.getColorForItem(item, entityItem2.x, entityItem2.y, entityItem2.z);
                    double interpolatedX = entityItem2.prevX + (entityItem2.x - entityItem2.prevX) * renderPartialTicks;
                    double interpolatedY = entityItem2.prevY + (entityItem2.y - entityItem2.prevY) * renderPartialTicks;
                    double interpolatedZ = entityItem2.prevZ + (entityItem2.z - entityItem2.prevZ) * renderPartialTicks;
                    PlayerEntity self = (PlayerEntity) (Freecam.freeEntity == null ? mc.player : Freecam.freeEntity);
                    double diffX = self.prevX + (self.x - self.prevX) * renderPartialTicks - interpolatedX;
                    double diffY = self.prevY + (self.y - self.prevY) * renderPartialTicks - interpolatedY;
                    double diffZ = self.prevZ + (self.z - self.prevZ) * renderPartialTicks - interpolatedZ;
                    double dist = MathHelper.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ);
                    GlStateManager.pushMatrix();
                    this.drawBox(
                            boxColor,
                            textColor,
                            colorMap.get(itemColor),
                            interpolatedX,
                            interpolatedY,
                            interpolatedZ,
                            dist);
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    public double getColor(double x, double y, double z) {
        if (x == 0.0) {
            x = 1.0;
        }

        if (y == 0.0) {
            y = 1.0;
        }

        if (z == 0.0) {
            z = 1.0;
        }

        return Math.round((x + 1.0) * Math.floor(y) * (z + 2.0));
    }

    private double getColorForItem(Item item, double x, double y, double z) {
        double color = this.getColor(x, y, z);
        if (item == Items.IRON_INGOT) {
            color += 0.155;
        } else if (item == Items.GOLD_INGOT) {
            color += 0.255;
        } else if (item == Items.DIAMOND) {
            color += 0.355;
        } else if (item == Items.EMERALD) {
            color += 0.455;
        }

        return color;
    }

    public void drawBox(int boxColor, int textColor, int size, double posY, double posX, double posZ, double dist) {
        posY -= mc.getEntityRenderDispatcher().cameraX;
        posX -= mc.getEntityRenderDispatcher().cameraY;
        posZ -= mc.getEntityRenderDispatcher().cameraZ;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0F);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        float r = (boxColor >> 16 & 0xFF) / 255.0F;
        float g = (boxColor >> 8 & 0xFF) / 255.0F;
        float b = (boxColor & 0xFF) / 255.0F;
        float radius = Math.min(Math.max(0.2F, (float) (0.01F * dist)), 0.4F);
        RenderUtils.drawBoundingBox(
                new Box(posY - radius, posX, posZ - radius, posY + radius, posX + radius * 2.0F, posZ + radius),
                r,
                g,
                b,
                0.35F);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.translated((float) posY, (float) posX + 0.3, (float) posZ);
        GlStateManager.rotatef(-mc.getEntityRenderDispatcher().cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(
                (mc.options.perspective == 2 ? -1 : 1) * mc.getEntityRenderDispatcher().cameraPitch, 1.0F, 0.0F, 0.0F);
        float scale = Math.min(Math.max(0.02266667F, (float) (0.0015F * dist)), 0.07F);
        GlStateManager.scalef(-scale, -scale, -scale);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepthTest();
        String value = String.valueOf(size);
        mc.textRenderer.draw(
                value,
                -(mc.textRenderer.getWidth(value) / 2) + scale * 3.5F,
                -(123.805F * scale - 2.47494F),
                textColor,
                true);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
