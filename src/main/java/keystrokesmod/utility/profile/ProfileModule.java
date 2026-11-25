package keystrokesmod.utility.profile;

import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.Utils;

public class ProfileModule extends Module {
    private final Profile profile;
    private final TextSetting profileNameSetting;
    final ButtonSetting autoSave;
    private String displayName;
    public boolean saved = true;

    public ProfileModule(Profile profile, String name, int bind) {
        super(name, Module.category.profiles, bind);
        this.profile = profile;
        this.displayName = name;
        this.registerSetting(
                this.profileNameSetting =
                        new TextSetting("Profile name", name, "Type a new profile name...", 32, this::renameProfile));
        this.registerSetting(new ButtonSetting("Save profile", () -> {
            if (Feral.profileManager.saveProfile(this.profile)) {
                Utils.sendMessage("&7Saved profile: &b" + this.getName());
            }
        }));
        this.registerSetting(new ButtonSetting("Set default", () -> {
            if (Feral.profileManager != null) {
                Feral.profileManager.setDefaultProfile(this.profile);
                Utils.sendMessage("&7Set &b" + this.getName() + " &7as default profile.");
            }
        }));
        this.registerSetting(new ButtonSetting("Remove profile", () -> {
            String profileName = this.getName();
            if (Feral.profileManager.deleteProfile(profileName)) {
                Utils.sendMessage("&7Removed profile: &b" + profileName);
            }
        }));
        this.registerSetting(this.autoSave = new ButtonSetting("Auto save", false));
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.autoSave && Feral.profileManager != null) {
            Feral.profileManager.setAutoSave(this.profile, setting.isToggled());
        }
    }

    @Override
    public void toggle() {
        if ((mc.screen instanceof ClickGui || mc.screen == null)
                && Feral.profileManager.loadProfile(this.getName())
                && Settings.sendMessage.isToggled()) {
            Utils.sendMessage("&7Enabled profile: &b" + this.getName());
        }
    }

    @Override
    public boolean isEnabled() {
        return Feral.currentProfile == null ? false : Feral.currentProfile.getModule() == this;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public void setProfileName(String profileName) {
        this.displayName = profileName;
        this.profileNameSetting.setText(profileName);
    }

    private void renameProfile() {
        if (Feral.profileManager != null) {
            String oldName = this.getName();
            if (Feral.profileManager.renameProfile(this.profile, this.profileNameSetting.getText())) {
                this.profileNameSetting.setText(this.profile.getName());
                if (!oldName.equals(this.profile.getName())) {
                    Utils.sendMessage("&7Renamed profile: &b" + oldName + " &7to &b" + this.profile.getName());
                }
            }
        }
    }
}
