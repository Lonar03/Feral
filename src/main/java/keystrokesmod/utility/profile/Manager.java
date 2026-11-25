package keystrokesmod.utility.profile;

import java.awt.Desktop;
import java.io.IOException;
import keystrokesmod.Feral;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.Utils;

public class Manager extends Module {
    private final TextSetting createProfileName;
    private ButtonSetting loadProfiles;
    private ButtonSetting openFolder;
    private ButtonSetting createProfile;

    public Manager() {
        super("Manager", Module.category.profiles);
        this.registerSetting(
                this.createProfileName =
                        new TextSetting("Profile name", "", "Type a profile name...", 32, this::createProfile));
        this.registerSetting(this.createProfile = new ButtonSetting("Create profile", () -> this.createProfile()));
        this.registerSetting(
                this.loadProfiles = new ButtonSetting("Load profiles", () -> {
                    if (Utils.nullCheck() && Feral.profileManager != null) {
                        Feral.profileManager.loadProfiles(true);
                    }
                }));
        this.registerSetting(
                this.openFolder = new ButtonSetting("Open folder", () -> {
                    try {
                        Desktop.getDesktop().open(Feral.profileManager.directory);
                    } catch (IOException ex) {
                        Feral.profileManager.directory.mkdirs();
                        Utils.sendMessage("&cError locating folder, recreated.");
                    }
                }));
        this.registerSetting(new ButtonSetting("Remove default profile", () -> {
            if (Utils.nullCheck() && Feral.profileManager != null) {
                Utils.sendMessage("&7Removed default profile: &b" + Feral.profileManager.getDefaultProfileName());
                Feral.profileManager.removeDefaultProfile();
            }
        }));
        this.ignoreOnSave = true;
        this.canBeEnabled = false;
    }

    private void createProfile() {
        if (Utils.nullCheck() && Feral.profileManager != null) {
            String profileName = this.createProfileName.getText().trim();
            if (!profileName.isEmpty()) {
                Profile profile = Feral.profileManager.createProfile(profileName, 0);
                if (profile != null) {
                    this.createProfileName.setText("");
                    Utils.sendMessage("&7Created profile: &b" + profile.getName());
                }
            }
        }
    }
}
