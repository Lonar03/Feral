package keystrokesmod.module.impl.other;

import java.util.List;
import java.util.Locale;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

final class AnticheatContext implements IMinecraftInstance {
    private static final String HOST = "hypixel.net";
    private static final String LOBBY_MARK = "  §8L";
    private static final char BED_MARK = '✓';

    private AnticheatContext() {}

    public static boolean onHypixel() {
        return isHypixelServer();
    }

    public static boolean sameTeam(PlayerEntity player) {
        if (onHypixel() && detect() != AnticheatContext.State.PIT) {
            Team team = (Team) player.getScoreboardTeam();
            PlayerInfo info = mc.getNetworkHandler() == null
                    ? null
                    : mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
            Team own = info == null ? (Team) mc.player.getScoreboardTeam() : info.getTeam();
            return team != null && own != null && team.getPrefix().equals(own.getPrefix());
        } else {
            return false;
        }
    }

    public static boolean skywars() {
        AnticheatContext.State state = detect();
        return state == AnticheatContext.State.SKYWARS_QUEUE
                || state == AnticheatContext.State.SKYWARS_GAME_SOLO
                || state == AnticheatContext.State.SKYWARS_GAME_TEAM;
    }

    public static int buildBattle() {
        List<String> list;
        if (onHypixel()
                && (list = sidebar()) != null
                && list.size() > 6
                && Utils.stripColor(list.get(0)).contains("BUILD BATTLE")) {
            boolean startsIn = false;
            boolean timeLeft = false;
            boolean theme = false;
            boolean judging = false;

            for (String s : list) {
                if (s.contains("Mode:") && s.contains("Speed Bu")) {
                    return 1;
                }

                if (s.contains("Starts In:")) {
                    startsIn = true;
                } else if (s.contains("Time Left:")) {
                    timeLeft = true;
                } else if (s.contains("Judging:")) {
                    judging = true;
                } else if (s.contains("Theme:")) {
                    theme = true;
                } else if (s.contains("Perfect Builds:")) {
                    if (!theme) {
                        return 2;
                    }

                    if (startsIn) {
                        boolean items = false;

                        for (int i = 0; i < 9; i++) {
                            if (Minecraft.getInstance().player.inventory.getItem(i) != null) {
                                items = true;
                                break;
                            }
                        }

                        return items ? 4 : 3;
                    }

                    if (timeLeft) {
                        return 4;
                    }

                    if (judging) {
                        return 5;
                    }
                }
            }
        }

        return 0;
    }

    public static String tabHeader() {
        try {
            Object o = ClientPlatform.ReflectionHelper.getPrivateValue(
                    PlayerTabOverlay.class, mc.gui.getPlayerTabOverlay(), "header", "field_175256_i");
            if (o != null) {
                return ((Text) o).getString();
            }
        } catch (Exception var1) {
        }

        return "";
    }

    public static boolean isHypixelServer() {
        if (!mc.isSingleplayer() && mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().ip.toLowerCase(Locale.ROOT).endsWith("hypixel.net")
                    ? true
                    : tabHeader().toLowerCase(Locale.ROOT).contains("hypixel.net");
        } else {
            return false;
        }
    }

    public static List<String> sidebar() {
        return Utils.getSidebarLinesWithTitle();
    }

    public static AnticheatContext.State detect() {
        if (!isHypixelServer()) {
            return AnticheatContext.State.NOT_HYPIXEL;
        }

        List<String> lines = sidebar();
        if (lines == null) {
            return mc.world.dimension.getId() == 1 ? AnticheatContext.State.LIMBO : AnticheatContext.State.UNKNOWN;
        }

        boolean bedwars = false;
        boolean skywars = false;
        boolean skyPlayers = false;
        boolean duels = false;
        boolean murder = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0) {
                String t = Utils.stripColor(line);
                if (t.equals("REPLAY")) {
                    return AnticheatContext.State.REPLAY;
                }

                if (t.equals("BED WARS PRACTICE")) {
                    return AnticheatContext.State.BRIDGE_PRACTICE;
                }

                if (t.equals("THE HYPIXEL PIT")) {
                    return AnticheatContext.State.PIT;
                }

                if (t.equals("BED WARS")) {
                    bedwars = true;
                } else if (t.equals("SKYWARS")) {
                    skywars = true;
                } else if (t.equals("DUELS")) {
                    duels = true;
                } else if (t.equals("MURDER MYSTERY")) {
                    murder = true;
                }
            } else {
                if (i == 1 && line.contains("  §8L")) {
                    return AnticheatContext.State.LOBBY;
                }

                if (bedwars) {
                    if (i >= 5) {
                        if (i == 8 && line.startsWith("Mode")) {
                            return AnticheatContext.State.BEDWARS_PREGAME;
                        }

                        if (line.endsWith(" YOU")) {
                            return line.indexOf(10003) != -1
                                    ? AnticheatContext.State.BEDWARS_GAME_BED
                                    : AnticheatContext.State.BEDWARS_GAME_NO_BED;
                        }
                    }
                } else if (skywars) {
                    if (i == 3) {
                        if (line.startsWith("Player")) {
                            return AnticheatContext.State.SKYWARS_QUEUE;
                        }
                    } else if (i == 6) {
                        if (line.startsWith("Player")) {
                            skyPlayers = true;
                        }
                    } else if (i == 7 && skyPlayers) {
                        return !line.startsWith("Team")
                                ? AnticheatContext.State.SKYWARS_GAME_SOLO
                                : AnticheatContext.State.SKYWARS_GAME_TEAM;
                    }
                } else if (murder) {
                    if (i == 3 && line.startsWith("Role")) {
                        return AnticheatContext.State.MURDER_MYSTERY_GAME;
                    }
                } else if (duels) {
                    if (i == 3) {
                        if (line.startsWith("Time")) {
                            return AnticheatContext.State.DUELS_GAME;
                        }
                    } else if (i == 6) {
                        if (line.startsWith("Waiting.")) {
                            return AnticheatContext.State.DUELS_QUEUE;
                        }

                        if (line.startsWith("Starting ")) {
                            return AnticheatContext.State.DUELS_QUEUE;
                        }
                    }
                }
            }
        }

        return AnticheatContext.State.UNKNOWN;
    }

    public static boolean screenOpen() {
        return mc.screen != null
                && !(mc.screen instanceof ClickGui)
                && (Settings.setChatAsInventory.isToggled() || !(mc.screen instanceof ChatScreen));
    }

    public static String distance(double distance) {
        char color = (char)
                (distance <= 10.0
                        ? 99
                        : (distance <= 25.0 ? 54 : (distance <= 50.0 ? 101 : (distance <= 100.0 ? 97 : 50))));
        String text = "&" + color + (int) distance + "m";
        return Settings.addBracketsToDistance.isToggled() ? "&7(" + text + "&7)" : text;
    }

    public static boolean isBlock(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem;
    }

    public static boolean isItem(ItemStack stack, Item item) {
        return stack != null && stack.getItem() == item;
    }

    public static BlockPos offset(int n, BlockPos pos) {
        if (n == 0) {
            return pos.up();
        } else if (n == 1) {
            return pos.down();
        } else if (n == 2) {
            return pos.north();
        } else if (n == 3) {
            return pos.south();
        } else {
            return n == 4 ? pos.east() : pos.west();
        }
    }

    public static boolean exposed(BlockPos pos) {
        boolean bed = pos != null && BlockUtils.getBlock(pos) == Blocks.BED;
        int i = 0;

        while (i < 6) {
            Block b = BlockUtils.getBlock(offset(i, pos));
            if (b != Blocks.AIR
                    && !(b instanceof LiquidBlock)
                    && b != Blocks.FIRE
                    && b != Blocks.LADDER
                    && b != Blocks.IRON_BARS
                    && b != Blocks.ENDER_CHEST
                    && b != Blocks.ENCHANTING_TABLE
                    && b != Blocks.SKULL
                    && b != Blocks.ANVIL
                    && b != Blocks.TRIPWIRE
                    && b != Blocks.VINE
                    && b != Blocks.FLOWER_POT
                    && !(b instanceof ChestBlock)
                    && !(b instanceof DoorBlock)
                    && !(b instanceof TrapdoorBlock)
                    && !(b instanceof FenceBlock)
                    && !(b instanceof FenceGateBlock)
                    && !(b instanceof WallBlock)
                    && !(b instanceof StairsBlock)) {
                if (i != 1 && b instanceof SlabBlock) {
                    return true;
                }

                if (i < 2 || (bed || b != Blocks.BED) && b != Blocks.END_PORTAL_FRAME) {
                    i++;
                    continue;
                }

                return true;
            }

            return true;
        }

        return false;
    }

    public enum State {
        NOT_HYPIXEL,
        UNKNOWN,
        LIMBO,
        LOBBY,
        REPLAY,
        BRIDGE_PRACTICE,
        PIT,
        MURDER_MYSTERY_GAME,
        BEDWARS_PREGAME,
        BEDWARS_GAME_BED,
        BEDWARS_GAME_NO_BED,
        SKYWARS_QUEUE,
        SKYWARS_GAME_SOLO,
        SKYWARS_GAME_TEAM,
        DUELS_QUEUE,
        DUELS_GAME;
    }
}
