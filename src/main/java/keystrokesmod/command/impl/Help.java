package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;

public class Help extends Command {
    public Help() {
        super("help", "?");
    }

    @Override
    public void execute(CommandInput input) {
        String prefix = this.prefixed("");
        this.replyTagged(input, "&7Commands - &dGeneral");
        this.reply(" &b" + prefix + "help/? &7List chat commands.");
        this.reply(" &b" + prefix + "clear &7Clears the chat.");
        this.reply(" &b" + prefix + "ign/name &7Copy your username.");
        this.reply(" &b" + prefix + "enemy/e (name/clear) &7Manage enemies.");
        this.reply(" &b" + prefix + "fakechat (text) &7Print fake messages.");
        this.reply(" &b" + prefix + "fps &7Check your framerate.");
        this.reply(" &b" + prefix + "friend/f (name/clear) &7Manage friends.");
        this.reply(" &b" + prefix + "ping &7Estimate your ping.");
        this.reply(" &b" + prefix + "time [time] &7Set world time.");
        this.reply(" &b" + prefix + "track [name] &7Highlight a player.");
        this.reply(" &b" + prefix + "rotate [yaw] [pitch] &7Set rotations.");
        this.reply(" &b" + prefix + "prefix [char] &7Change the chat command prefix.");
        this.replyTagged(input, "&7Commands - &dModules");
        this.reply(" &b" + prefix + "namehider [name] &7Set name hider name.");
        this.reply(" &b" + prefix + "binds (key) &7List module binds.");
        this.reply(" &b" + prefix + "bind/b [module] [key] &7Bind a module.");
        this.reply(" &b" + prefix + "find [module] &7Find a module in GUI.");
        this.reply(" &b" + prefix + "limbo &7Sends you to Hypixel limbo.");
        this.reply(" &b" + prefix + "panic &7Disable all modules.");
        this.reply(" &b" + prefix + "resetgui &7Reset GUI positions.");
        this.reply(" &b" + prefix + "toggle/t [module] &7Toggle a module.");
        this.reply(" &b" + prefix + "unbind/ub [module] &7Remove module bind.");
        this.reply(" &b" + prefix + "show all/showall &7Show all modules in HUD.");
        this.reply(" &b" + prefix + "hide all/hideall &7Hide all modules from HUD.");
        this.reply(" &b" + prefix + "timer [set/reset/value] (value) &7Manage timer speed.");
        this.reply(" &b" + prefix + "script load &7Reload scripts.");
        this.reply(" &b" + prefix + "debug (mixin/background) &7Toggle debug output.");
        this.replyTagged(input, "&7Commands - &dProfiles");
        this.reply(" &b" + prefix + "profiles/profile/p &7List loaded profiles.");
        this.reply(" &b" + prefix + "profiles save (name) &7Save current settings as a profile.");
        this.reply(" &b" + prefix + "profiles load [name] &7Load a profile.");
        this.reply(" &b" + prefix + "profiles delete [name] &7Delete a profile.");
        this.reply(" &b" + prefix + "profiles rename [oldname] [newname] &7Rename a profile.");
    }
}
