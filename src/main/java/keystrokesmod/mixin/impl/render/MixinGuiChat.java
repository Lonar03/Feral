package keystrokesmod.mixin.impl.render;

import keystrokesmod.Feral;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(ChatScreen.class)
public abstract class MixinGuiChat extends MixinGuiScreen {
    private static final int MAX_PREVIEW_ROWS = 6;
    private static final long PREVIEW_SCROLL_DURATION_MS = 200L;

    @Unique
    private String[] feral$commandCompletions = new String[0];

    @Unique
    private int feral$commandCompletionIndex = -1;

    @Unique
    private final ScrollOffsetAnimation feral$previewScrollAnim = new ScrollOffsetAnimation(200L);

    @Unique
    private String feral$previewScrollInput = "";

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    private boolean completed;

    @Shadow
    public abstract void setMessageHistory(String[] var1);

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void updateLength(CallbackInfo callbackInfo) {
        if (Feral.commandManager != null && Feral.commandManager.isCommand(this.chatField.getText())) {
            this.chatField.setMaxLength(256);
        } else {
            this.chatField.setMaxLength(100);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void clearCommandCompletionState(char typedChar, int keyCode, CallbackInfo callbackInfo) {
        if (keyCode != 15) {
            this.feral$resetCommandCompletions();
        }
    }

    @Inject(method = "handleMouse", at = @At("HEAD"), cancellable = true)
    private void handleSuggestionScroll(CallbackInfo callbackInfo) {
        if (Feral.commandManager != null) {
            int wheelInput = Mouse.getEventDWheel();
            if (wheelInput != 0) {
                String input = this.chatField.getText();
                if (!Feral.commandManager.isCommand(input)) {
                    this.feral$resetPreviewScroll();
                } else {
                    String[] suggestions = Feral.commandManager.getPreviewSuggestions(input);
                    int visibleRows = Math.min(6, suggestions.length);
                    if (visibleRows != 0 && suggestions.length > visibleRows) {
                        int mouseX = Mouse.getEventX() * this.width / this.minecraft.width;
                        int mouseY = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
                        if (this.feral$isMouseOverPreviewPanel(mouseX, mouseY, suggestions, visibleRows)) {
                            this.feral$syncPreviewScrollState(input, suggestions.length, visibleRows);
                            float scrollSpeed = Gui.scrollSpeed != null ? (float) Gui.scrollSpeed.getInput() : 20.0F;
                            float delta = scrollSpeed * (wheelInput / 120.0F);
                            if (delta != 0.0F) {
                                this.feral$previewScrollAnim.extend(-delta);
                                this.feral$clampPreviewScroll(suggestions.length, visibleRows);
                            }

                            callbackInfo.cancel();
                        }
                    } else {
                        this.feral$syncPreviewScrollState(input, suggestions.length, visibleRows);
                    }
                }
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void drawLiveCommandSuggestions(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (Feral.commandManager == null) {
            this.feral$resetPreviewScroll();
        } else {
            String input = this.chatField.getText();
            if (!Feral.commandManager.isCommand(input)) {
                this.feral$resetPreviewScroll();
            } else {
                String[] suggestions = Feral.commandManager.getPreviewSuggestions(input);
                if (suggestions.length == 0) {
                    this.feral$resetPreviewScroll();
                } else {
                    int rows = Math.min(6, suggestions.length);
                    this.feral$syncPreviewScrollState(input, suggestions.length, rows);
                    int fontHeight = this.minecraft.textRenderer.fontHeight;
                    int rowHeight = fontHeight + 2;
                    int widest = this.minecraft.textRenderer.getWidth("Suggestions");

                    for (String suggestion : suggestions) {
                        widest = Math.max(widest, this.minecraft.textRenderer.getWidth(suggestion));
                    }

                    int panelLeft = 4;
                    int panelRight = Math.min(this.width - 4, panelLeft + widest + 10);
                    int panelBottom = this.height - 16;
                    int panelTop = panelBottom - 6 - fontHeight - rows * (fontHeight + 2);
                    int contentTop = panelTop + 5 + fontHeight;
                    int contentHeight = rows * rowHeight;
                    GuiElement.fill(panelLeft, panelTop, panelRight, panelBottom, -1072689130);
                    GuiElement.fill(panelLeft, panelTop, panelRight, panelTop + 1, -3381505);
                    GuiElement.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, -2145378262);
                    this.minecraft.textRenderer.drawWithShadow("Suggestions", panelLeft + 4, panelTop + 3, -1517313);
                    RenderUtils.scissorPushGui(panelLeft, contentTop, panelRight - panelLeft, contentHeight);
                    float scrollOffset = this.feral$previewScrollAnim.getValue();
                    int activeIndex = this.feral$getActiveSuggestionIndex(input, suggestions);

                    for (int i = 0; i < suggestions.length; i++) {
                        int textY = Math.round(contentTop - scrollOffset + i * rowHeight);
                        if (textY + fontHeight >= contentTop && textY <= contentTop + contentHeight) {
                            int color = i == activeIndex ? -1 : -4209450;
                            this.minecraft.textRenderer.drawWithShadow(suggestions[i], panelLeft + 4, textY, color);
                        }
                    }

                    RenderUtils.scissorPop();
                }
            }
        }
    }

    @Inject(method = "goThroughHistory(Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void handleClientCommandCompletion(String full, String ignored, CallbackInfo callbackInfo) {
        if (Feral.commandManager != null && Feral.commandManager.isCommand(full)) {
            String[] suggestions = Feral.commandManager.getAutoComplete(full);
            if (suggestions.length != 0) {
                if (suggestions.length != 1 || !full.equalsIgnoreCase(suggestions[0])) {
                    this.completed = true;
                    this.setMessageHistory(suggestions);
                    callbackInfo.cancel();
                }
            }
        }
    }

    @Inject(method = "reset", at = @At("HEAD"), cancellable = true)
    private void handleCommandAutocomplete(CallbackInfo callbackInfo) {
        if (Feral.commandManager != null) {
            String input = this.chatField.getText();
            if (!Feral.commandManager.isCommand(input)) {
                this.feral$resetCommandCompletions();
            } else {
                String[] suggestions;
                if (this.feral$canCycleCurrentCompletion(input)) {
                    suggestions = this.feral$commandCompletions;
                    this.feral$commandCompletionIndex = (this.feral$commandCompletionIndex + 1) % suggestions.length;
                } else {
                    suggestions = Feral.commandManager.getAutoComplete(input);
                    if (suggestions.length == 0) {
                        this.feral$resetCommandCompletions();
                        callbackInfo.cancel();
                        return;
                    }

                    this.feral$commandCompletions = suggestions;
                    this.feral$commandCompletionIndex = 0;
                }

                this.chatField.setText(suggestions[this.feral$commandCompletionIndex]);
                this.chatField.setCursorToEnd();
                callbackInfo.cancel();
            }
        }
    }

    @Unique
    private boolean feral$canCycleCurrentCompletion(String input) {
        if (this.feral$commandCompletions.length != 0 && this.feral$commandCompletionIndex >= 0) {
            for (String suggestion : this.feral$commandCompletions) {
                if (suggestion.equals(input)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Unique
    private void feral$resetCommandCompletions() {
        this.feral$commandCompletions = new String[0];
        this.feral$commandCompletionIndex = -1;
    }

    @Unique
    private void feral$syncPreviewScrollState(String input, int suggestionCount, int visibleRows) {
        if (!input.equals(this.feral$previewScrollInput)) {
            this.feral$previewScrollInput = input;
            this.feral$previewScrollAnim.reset(0.0F);
        }

        this.feral$clampPreviewScroll(suggestionCount, visibleRows);
    }

    @Unique
    private void feral$clampPreviewScroll(int suggestionCount, int visibleRows) {
        int rowHeight = this.minecraft.textRenderer.fontHeight + 2;
        float maxScroll = Math.max(0.0F, (suggestionCount - visibleRows) * rowHeight);
        this.feral$previewScrollAnim.clampTarget(0.0F, maxScroll);
    }

    @Unique
    private boolean feral$isMouseOverPreviewPanel(int mouseX, int mouseY, String[] suggestions, int visibleRows) {
        int fontHeight = this.minecraft.textRenderer.fontHeight;
        int widest = this.minecraft.textRenderer.getWidth("Suggestions");

        for (String suggestion : suggestions) {
            widest = Math.max(widest, this.minecraft.textRenderer.getWidth(suggestion));
        }

        int panelLeft = 4;
        int panelRight = Math.min(this.width - 4, panelLeft + widest + 10);
        int panelBottom = this.height - 16;
        int panelTop = panelBottom - 6 - fontHeight - visibleRows * (fontHeight + 2);
        return mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom;
    }

    @Unique
    private int feral$getActiveSuggestionIndex(String input, String[] suggestions) {
        String loweredInput = input == null ? "" : input.toLowerCase();

        for (int i = 0; i < suggestions.length; i++) {
            if (loweredInput.endsWith(suggestions[i].toLowerCase())) {
                return i;
            }
        }

        return 0;
    }

    @Unique
    private void feral$resetPreviewScroll() {
        this.feral$previewScrollInput = "";
        this.feral$previewScrollAnim.reset(0.0F);
    }
}
