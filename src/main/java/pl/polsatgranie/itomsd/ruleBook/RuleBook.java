package pl.polsatgranie.itomsd.ruleBook;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuleBook extends JavaPlugin implements Listener {

    private final Set<Player> playersPendingAcceptance = new HashSet<>();
    private File playersFile;
    private FileConfiguration playersConfig;

    @Override
    public void onEnable() {
        Metrics metrics = new Metrics(this, 22937);
        this.getLogger().info("""
                
                ------------------------------------------------------------
                |                                                          |
                |      _  _______        __     __    _____   ____         |
                |     | ||___ ___|      |  \\   /  |  / ____| |  _ \\        |
                |     | |   | |   ___   | |\\\\ //| | | (___   | | \\ \\       |
                |     | |   | |  / _ \\  | | \\_/ | |  \\___ \\  | |  ) )      |
                |     | |   | | | (_) | | |     | |  ____) | | |_/ /       |
                |     |_|   |_|  \\___/  |_|     |_| |_____/  |____/        |
                |                                                          |
                |                                                          |
                ------------------------------------------------------------
                |                 +==================+                     |
                |                 |     RuleBook     |                     |
                |                 |------------------|                     |
                |                 |        1.0       |                     |
                |                 |------------------|                     |
                |                 |  PolsatGraniePL  |                     |
                |                 +==================+                     |
                ------------------------------------------------------------
                """);
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("rbreload").setExecutor(this);
        getCommand("rbreload").setAliases(List.of("rulebookreload"));
        getCommand("acceptRules").setExecutor(this);
        getCommand("denyRules").setExecutor(this);

        playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                playersFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playersConfig.contains(player.getUniqueId().toString())) {
            playersPendingAcceptance.add(player);
            openRuleBook(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (playersPendingAcceptance.contains(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                openRuleBook(player);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playersPendingAcceptance.contains(player)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                openRuleBook(player);
            }, 1L);
        }
    }

    private void openRuleBook(Player player) {
        FileConfiguration config = getConfig();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(ChatColor.RED + config.getString("title"));
        meta.setAuthor("Admin");

        List<String> rulesPages = config.getStringList("rules");
        String acceptText = config.getString("accept-text");
        String denyText = config.getString("deny-text");

        for (String pageContent : rulesPages) {
            String formattedPage = pageContent.replace("%nl%", "\n");
            meta.addPage(ChatColor.translateAlternateColorCodes('&', formattedPage));
        }

        TextComponent acceptButton = new TextComponent(ChatColor.GREEN + acceptText);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acceptrules"));

        TextComponent denyButton = new TextComponent(ChatColor.RED + denyText);
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/denyrules"));

        meta.spigot().addPage(new ComponentBuilder("\n\n").append(acceptButton).append("\n\n").append(denyButton).create());
        book.setItemMeta(meta);
        forceOpenBook(player, book);
    }

    private void forceOpenBook(Player player, ItemStack book) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack oldItem = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, book);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.openBook(book);
            player.getInventory().setItem(slot, oldItem);
        }, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rbreload")) {
            if (sender.hasPermission("itomsd.rulebook.reload") || sender instanceof ConsoleCommandSender) {
                reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("plugin-reloaded")));
                return true;
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("no-permission-message")));
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("acceptrules")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                playersPendingAcceptance.remove(player);
                playersConfig.set(player.getUniqueId().toString(), true);
                savePlayersConfig();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("accept-message")));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("denyrules")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.kickPlayer(ChatColor.translateAlternateColorCodes('&', getConfig().getString("deny-message")));
            }
            return true;
        }
        return false;
    }

    private void savePlayersConfig() {
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
