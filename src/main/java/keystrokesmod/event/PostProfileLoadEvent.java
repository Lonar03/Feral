package keystrokesmod.event;

import keystrokesmod.platform.ornithe.ForgeEvents;

public class PostProfileLoadEvent extends ForgeEvents.Event {
    public String profileName;

    public PostProfileLoadEvent(String profileName) {
        this.profileName = profileName;
    }
}
