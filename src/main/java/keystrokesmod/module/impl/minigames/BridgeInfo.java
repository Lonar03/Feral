package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class BridgeInfo extends Module {
    private final int textRGB = new Color(0, 200, 200).getRGB();
    private final String bridge = "the brid";
    private final String start = "Defend!";
    private final String start2 = "Jump in to score!";
    private final String qt = "First player to score 5 goals wins";
    private final String enemyText = "Enemy: ";
    private final String distance = "Distance to goal: ";
    private final String enemyDistance = "Enemy distance to goal: ";
    private final String blocks = "Blocks: ";
    private static int hudX = 5;
    private static int hudY = 70;
    private String enemyName = "";
    private BlockPos g1p = null;
    private BlockPos g2p = null;
    private boolean q = false;
    private double d1 = 0.0;
    private double d2 = 0.0;
    private int blc = 0;

    public BridgeInfo() {
        super("Bridge Info", Module.category.minigames, 0);
        this.registerSetting(new DescriptionSetting(new String("Only for solos.")));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new BridgeInfo.EditScreen())));
    }

    @Override
    public void onDisable() {
        this.reset();
    }

    @Override
    public void onUpdate() {
        if (!this.enemyName.isEmpty() && this.isBridge()) {
            PlayerEntity enem = null;

            for (Entity e : mc.world.entities) {
                if (e instanceof PlayerEntity) {
                    if (e.getName().equals(this.enemyName)) {
                        enem = (PlayerEntity) e;
                    }
                } else if (e instanceof ArmorStandEntity) {
                    if (e.getName().contains("Defend!")) {
                        this.g1p = e.getCommandSourceBlockPos();
                    } else if (e.getName().contains("Jump in to score!")) {
                        this.g2p = e.getCommandSourceBlockPos();
                    }
                }
            }

            if (this.g1p != null && this.g2p != null) {
                this.d1 = Utils.round(mc.player.distanceTo(this.g2p.getX(), this.g2p.getY(), this.g2p.getZ()) - 1.4, 1);
                if (this.d1 < 0.0) {
                    this.d1 = 0.0;
                }

                this.d2 = enem == null
                        ? 0.0
                        : Utils.round(enem.distanceTo(this.g1p.getX(), this.g1p.getY(), this.g1p.getZ()) - 1.4, 1);
                if (this.d2 < 0.0) {
                    this.d2 = 0.0;
                }
            }

            int blc2 = 0;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.inventory.getItem(i);
                if (stack != null
                        && stack.getItem() instanceof BlockItem
                        && ((BlockItem) stack.getItem()).block.equals(Blocks.STAINED_HARDENED_CLAY)) {
                    blc2 += stack.size;
                }
            }

            this.blc = blc2;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent ev) {
        if (ev.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck() && this.isBridge()) {
            if (mc.screen != null || mc.options.debugEnabled) {
                return;
            }

            mc.textRenderer.draw("Enemy: " + this.enemyName, hudX, hudY, this.textRGB, true);
            mc.textRenderer.draw("Distance to goal: " + this.d1, hudX, hudY + 11, this.textRGB, true);
            mc.textRenderer.draw("Enemy distance to goal: " + this.d2, hudX, hudY + 22, this.textRGB, true);
            mc.textRenderer.draw("Blocks: " + this.blc, hudX, hudY + 33, this.textRGB, true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent c) {
        if (Utils.nullCheck()) {
            String s = Utils.stripColor(c.message.getString());
            if (s.startsWith(" ")) {
                if (s.contains("First player to score 5 goals wins")) {
                    this.q = true;
                } else if (this.q && s.contains("Opponent:")) {
                    String n = s.split(":")[1].trim();
                    if (n.contains("[")) {
                        n = n.split("] ")[1];
                    }

                    this.enemyName = n;
                    this.q = false;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.reset();
        }
    }

    private boolean isBridge() {
        if (Utils.isHypixel()) {
            for (String s : Utils.getScoreBoardOld()) {
                String s2 = s.toLowerCase();
                if (s2.contains("mode") && s2.contains("the brid")) {
                    return true;
                }
            }
        }

        return false;
    }

    private void reset() {
        this.enemyName = "";
        this.q = false;
        this.g1p = null;
        this.g2p = null;
        this.d1 = 0.0;
        this.d2 = 0.0;
        this.blc = 0;
    }

    private class EditScreen extends Screen {
        String example = new String("Enemy: Player123-Distance to goal: 17.2-Enemy distance to goal: 16.3-Blocks: 98");
        ButtonWidget resetPosition;
        boolean d = false;
        int miX = 0;
        int miY = 0;
        int maX = 0;
        int maY = 0;
        int aX = 5;
        int aY = 70;
        int laX = 0;
        int laY = 0;
        int lmX = 0;
        int lmY = 0;

        private EditScreen() {}

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetPosition = new ButtonWidget(1, this.width - 90, 5, 85, 20, new String("Reset position")));
            this.aX = BridgeInfo.hudX;
            this.aY = BridgeInfo.hudY;
        }

        public void render(int mX, int mY, float pt) {
            fill(0, 0, this.width, this.height, -1308622848);
            int miX = this.aX;
            int miY = this.aY;
            int maX = miX + 140;
            int maY = miY + 41;
            this.d(this.minecraft.textRenderer, this.example);
            this.miX = miX;
            this.miY = miY;
            this.maX = maX;
            this.maY = maY;
            BridgeInfo.hudX = miX;
            BridgeInfo.hudY = miY;
            Window res = new Window(this.minecraft);
            int x = res.getWidth() / 2 - 84;
            int y = res.getHeight() / 2 - 20;
            RenderUtils.drawColoredString(
                    "Edit the HUD position by dragging.", '-', x, y, 2L, 0L, true, this.minecraft.textRenderer);
            ClientPlatform.handleInput(this);
            super.render(mX, mY, pt);
        }

        private void d(TextRenderer fr, String t) {
            int x = this.miX;
            int y = this.miY;

            for (String s : t.split("-")) {
                fr.draw(s, x, y, BridgeInfo.this.textRGB, true);
                y += fr.fontHeight + 2;
            }
        }

        protected void mouseDragged(int mX, int mY, int b, long t) {
            super.mouseDragged(mX, mY, b, t);
            if (b == 0) {
                if (this.d) {
                    this.aX = this.laX + (mX - this.lmX);
                    this.aY = this.laY + (mY - this.lmY);
                } else if (mX > this.miX && mX < this.maX && mY > this.miY && mY < this.maY) {
                    this.d = true;
                    this.lmX = mX;
                    this.lmY = mY;
                    this.laX = this.aX;
                    this.laY = this.aY;
                }
            }
        }

        protected void mouseReleased(int mX, int mY, int s) {
            super.mouseReleased(mX, mY, s);
            if (s == 0) {
                this.d = false;
            }
        }

        public void buttonClicked(ButtonWidget b) {
            if (b == this.resetPosition) {
                this.aX = BridgeInfo.hudX = 5;
                this.aY = BridgeInfo.hudY = 70;
            }
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }
}
