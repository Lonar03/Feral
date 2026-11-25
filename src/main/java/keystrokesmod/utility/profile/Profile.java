package keystrokesmod.utility.profile;

import keystrokesmod.Feral;

public class Profile {
    private final ProfileModule module;
    private final int bind;
    private String profileName;

    public Profile(String profileName, int bind) {
        this.profileName = profileName;
        this.bind = bind;
        this.module = new ProfileModule(this, profileName, bind);
        this.module.ignoreOnSave = true;
    }

    public ProfileModule getModule() {
        return this.module;
    }

    public int getBind() {
        return this.bind;
    }

    public String getName() {
        return this.profileName;
    }

    public void setName(String profileName) {
        this.profileName = profileName;
        this.module.setProfileName(profileName);
    }

    public boolean isDefault() {
        return Feral.profileManager != null && Feral.profileManager.isDefaultProfile(this);
    }

    public void setDefault(boolean defaultProfile) {
        if (Feral.profileManager != null) {
            if (defaultProfile) {
                Feral.profileManager.setDefaultProfile(this);
            } else if (this.isDefault()) {
                Feral.profileManager.removeDefaultProfile();
            }
        }
    }
}
