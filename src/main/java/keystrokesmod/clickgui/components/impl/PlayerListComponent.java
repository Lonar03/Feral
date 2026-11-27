package keystrokesmod.clickgui.components.impl;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.PlayerListSetting;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.PlayerSkinCache;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;

public class PlayerListComponent extends AbstractTextInputComponent {
    private static final float SELECTED_LIST_GAP = 4.0F;
    private static final int MAX_VISIBLE_SELECTED = 7;
    private static final int CLOSE_SIZE = 6;
    private static final float CLOSE_PAD = 3.0F;
    private static final float HEAD_SIZE = 8.0F;
    private static final float LIST_ROW_TEXT_SCALE = 0.56F;
    private static final float LIST_ROW_TEXT_Y_OFFSET = 0.56F;
    private static final float LIST_ROW_VISUAL_HEIGHT = 11.0F;
    private final PlayerListSetting setting;
    private final ScrollOffsetAnimation selectedScrollAnim = new ScrollOffsetAnimation(200L);
    private float lastMouseX;
    private float lastMouseY;

    public PlayerListComponent(PlayerListSetting setting, ModuleComponent moduleComponent, float o) {
        super(moduleComponent, o, setting.getPlaceholder(), setting.getMaxLength());
        this.setting = setting;
    }

    @Override
    public void render() {
        AbstractTextInputComponent.Layout layout = this.layout(false);
        this.renderLabel(layout, this.setting.getName());
        this.renderTextField(layout);
        this.renderSelectedEntries(layout);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        super.drawScreen(mouseX, mouseY);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.clampSelectedScroll();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (this.moduleComponent.isOpened && this.moduleComponent.isVisible(this)) {
            AbstractTextInputComponent.Layout layout = this.layout(true);
            if (button == 0 && this.isTextFieldClicked(mouseX, mouseY, layout)) {
                this.setTextFieldFocused(true);
                return true;
            }

            if (button == 0 && this.handleSelectedEntryClick(mouseX, mouseY, layout)) {
                return true;
            }

            if (this.isTextFieldFocused()) {
                this.getTextField().setText("");
                this.setTextFieldFocused(false);
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (this.moduleComponent.isOpened && this.isTextFieldFocused()) {
            if (keyCode == 1) {
                this.getTextField().setText("");
                this.setTextFieldFocused(false);
            } else if (keyCode != 28 && keyCode != 156) {
                this.getTextField().textboxKeyTyped(typedChar, keyCode);
            } else {
                this.submitText();
                this.setTextFieldFocused(false);
            }
        }
    }

    @Override
    public void onScroll(int scroll) {
        if (this.moduleComponent.isOpened && this.moduleComponent.isVisible(this)) {
            if (this.capturesCategoryScroll(this.lastMouseX, this.lastMouseY)) {
                float scrollSpeed = (float) Gui.scrollSpeed.getInput();
                float delta = scrollSpeed * (scroll / 120.0F);
                if (delta != 0.0F) {
                    this.selectedScrollAnim.extend(-delta);
                }

                this.clampSelectedScroll();
            }
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        this.getTextField().setText("");
        this.selectedScrollAnim.reset(0.0F);
    }

    @Override
    public float getHeightF() {
        int count = this.setting.getEntries().size();
        float selectedHeight = count == 0 ? 0.0F : 4.0F + Math.min(7, count) * 12.0F;
        return 24.0F + selectedHeight;
    }

    @Override
    public boolean isBaseVisible() {
        return this.setting.visible;
    }

    @Override
    public String getGroupName() {
        return this.setting.group != null ? this.setting.group.getName() : "";
    }

    public boolean capturesCategoryScroll(float mouseX, float mouseY) {
        return this.setting.getEntries().size() > 7 && this.isMouseOverSelectedList(mouseX, mouseY);
    }

    @Override
    public boolean containsClick(int mouseX, int mouseY) {
        AbstractTextInputComponent.Layout layout = this.layout(true);
        return this.isTextFieldClicked(mouseX, mouseY, layout) || this.isMouseOverSelectedList(mouseX, mouseY);
    }

    public void onExternalDataChanged() {
        this.clampSelectedScroll();
    }

    private void submitText() {
        String typedName = this.getTextField().getText();
        if (typedName != null && !typedName.trim().isEmpty()) {
            if (this.setting.addPlayer(typedName)) {
                this.getTextField().setText("");
            }

            this.moduleComponent.updateSettingPositions();
            this.clampSelectedScroll();
        }
    }

    private void renderSelectedEntries(AbstractTextInputComponent.Layout layout) {
        List<PlayerRelationsManager.PlayerEntry> entries = this.setting.getEntries();
        if (!entries.isEmpty()) {
            float selectedTop = this.getSelectedTop(layout);
            float selectedHeight = this.getSelectedVisibleHeight(entries.size());
            float scrollOffset = this.moduleComponent.categoryComponent.getModuleY() - layout.cy;
            RenderUtils.scissorPushGui(
                    layout.left, selectedTop + scrollOffset, layout.right - layout.left, selectedHeight);
            float offsetPx = this.selectedScrollAnim.getValue();
            int firstRow = (int) (offsetPx / 12.0F);
            int end = Math.min(firstRow + 7 + 1, entries.size());
            Map<String, PlayerInfo> playerInfoMap = this.getPlayerInfoMap();

            for (int i = firstRow; i < end; i++) {
                PlayerRelationsManager.PlayerEntry entry = entries.get(i);
                float rowTop = selectedTop - offsetPx + i * 12.0F;
                int bg = i % 2 == 0 ? -15066582 : -14803410;
                this.renderEntryRow(entry, playerInfoMap.get(entry.getKey()), layout.left, layout.right, rowTop, bg);
            }

            RenderUtils.scissorPop();
        }
    }

    private void renderEntryRow(
            PlayerRelationsManager.PlayerEntry entry,
            PlayerInfo playerInfo,
            float left,
            float right,
            float rowTop,
            int bgColor) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + 12.0F - 1.0F, bgColor);
        this.renderPlayerHead(entry, playerInfo, left + 2.0F, rowTop + 1.5F);
        drawScaledText(
                entry.getDisplayName(),
                left + 13f,
                centeredScaledTextY(rowTop, 11f, 0.56f),
                -3355444,
                0.56f,
                right - 11f);
        this.renderCloseIcon(right, rowTop);
    }

    private boolean handleSelectedEntryClick(int mouseX, int mouseY, AbstractTextInputComponent.Layout layout) {
        List<PlayerRelationsManager.PlayerEntry> entries = this.setting.getEntries();
        float offsetPx = this.selectedScrollAnim.getValue();

        for (int i = 0; i < entries.size(); i++) {
            float rowTop = this.getSelectedTop(layout) - offsetPx + i * 12.0F;
            if (this.isOverClose(mouseX, mouseY, rowTop, layout.right)) {
                this.setting.removePlayer(entries.get(i).getKey());
                this.moduleComponent.updateSettingPositions();
                this.clampSelectedScroll();
                return true;
            }
        }

        return false;
    }

    private void renderPlayerHead(PlayerRelationsManager.PlayerEntry entry, PlayerInfo playerInfo, float x, float y) {
        Identifier skin = this.getSkin(entry, playerInfo);
        if (skin != null) {
            boolean depthEnabled = GL11.glIsEnabled(2929);
            boolean blendEnabled = GL11.glIsEnabled(3042);
            boolean depthMask = GL11.glGetBoolean(2930);

            try {
                RenderUtils.prepareGuiTextureRenderState();
                Minecraft.getInstance().getTextureManager().bind(skin);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GuiElement.drawTexture((int) x, (int) y, 8.0F, 8.0F, 8, 8, 8, 8, 64.0F, 64.0F);
                GuiElement.drawTexture((int) x, (int) y, 40.0F, 8.0F, 8, 8, 8, 8, 64.0F, 64.0F);
            } finally {
                RenderUtils.restoreGuiRenderState(depthEnabled, blendEnabled, depthMask);
            }
        }
    }

    private Identifier getSkin(PlayerRelationsManager.PlayerEntry entry, PlayerInfo playerInfo) {
        return PlayerSkinCache.getSkin(entry.getDisplayName(), playerInfo);
    }

    private Map<String, PlayerInfo> getPlayerInfoMap() {
        Map<String, PlayerInfo> playerInfoMap = new HashMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getNetworkHandler() == null) {
            return playerInfoMap;
        }

        for (PlayerInfo playerInfo : minecraft.getNetworkHandler().getOnlinePlayers()) {
            if (playerInfo != null) {
                GameProfile profile = playerInfo.getProfile();
                if (profile != null && profile.getName() != null) {
                    playerInfoMap.put(profile.getName().toLowerCase(), playerInfo);
                }
            }
        }

        return playerInfoMap;
    }

    private void clampSelectedScroll() {
        int count = this.setting.getEntries().size();
        float maxScrollPx = Math.max(0.0F, (count - 7) * 12.0F);
        this.selectedScrollAnim.clampTarget(0.0F, maxScrollPx);
        if (this.selectedScrollAnim.getValue() > maxScrollPx) {
            this.selectedScrollAnim.reset(maxScrollPx);
        }
    }

    private boolean isMouseOverSelectedList(float mouseX, float mouseY) {
        List<PlayerRelationsManager.PlayerEntry> entries = this.setting.getEntries();
        if (entries.isEmpty()) {
            return false;
        }

        AbstractTextInputComponent.Layout layout = this.layout(true);
        float selectedTop = this.getSelectedTop(layout);
        float selectedHeight = this.getSelectedVisibleHeight(entries.size());
        return mouseX >= layout.left
                && mouseX <= layout.right
                && mouseY >= selectedTop
                && mouseY < selectedTop + selectedHeight;
    }

    private float getSelectedTop(AbstractTextInputComponent.Layout layout) {
        return layout.contentTop + 4.0F;
    }

    private float getSelectedVisibleHeight(int count) {
        return Math.min(7, count) * 12.0F;
    }

    private void renderCloseIcon(float right, float rowTop) {
        drawCloseIcon(right, rowTop);
    }

    private boolean isOverClose(float mouseX, float mouseY, float rowTop, float right) {
        return isOverCloseIcon(mouseX, mouseY, right, rowTop);
    }
}
