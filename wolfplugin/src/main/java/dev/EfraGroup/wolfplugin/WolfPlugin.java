package dev.EfraGroup.wolfplugin;

import dev.EfraGroup.wolfplugin.utils.VarIntUtils;
import dev.EfraGroup.wolfplugin.vehicle.CarPhysics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

public class WolfPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private static final String CHANNEL = "wolfnetwork:settings";
    private static final String RADIO_CHANNEL = "formularacing:radio";
    private final Map<UUID, BukkitTask> pendingHandshakeTasks = new HashMap<>();
    private final Map<UUID, String> pendingTireChanges = new HashMap<>();
    private byte[] handshakePayload;
    private byte[] serverInfoPayload;
    private CarPhysics carPhysics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        handshakePayload = VarIntUtils.encodeString("1", "");
        serverInfoPayload = VarIntUtils.encodeString("server_info", getConfig().getString("server-info", "Desconhecido"));
        carPhysics = new CarPhysics(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, RADIO_CHANNEL, this);

        getServer().getPluginManager().registerEvents(this, this);
        carPhysics.start();

        CarPhysicsCommand carCommand = new CarPhysicsCommand();
        if (getCommand("carphysics") != null) {
            getCommand("carphysics").setExecutor(carCommand);
            getCommand("carphysics").setTabCompleter(carCommand);
            getLogger().info("Comando /carphysics registrado.");
        } else {
            getLogger().warning("Comando /carphysics NAO encontrado no plugin.yml!");
        }

        getLogger().info("WolfPlugin habilitado! Canal " + CHANNEL + " registrado.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : pendingHandshakeTasks.values()) {
            task.cancel();
        }
        pendingHandshakeTasks.clear();
        if (carPhysics != null) {
            carPhysics.stop();
        }

        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, RADIO_CHANNEL);
        getLogger().info("WolfPlugin desabilitado.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            pendingHandshakeTasks.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendPluginMessage(this, CHANNEL, handshakePayload);
            }
        }, 40L);

        pendingHandshakeTasks.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BukkitTask task = pendingHandshakeTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (carPhysics != null) {
            carPhysics.setEnabled(event.getPlayer().getUniqueId(), false);
        }
        pendingTireChanges.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (CHANNEL.equals(channel)) {
            handleSettingsMessage(player, message);
        } else if (RADIO_CHANNEL.equals(channel)) {
            handleRadioMessage(player, message);
        }
    }

    private void handleSettingsMessage(Player player, byte[] message) {
        try {
            VarIntUtils.DecodedStrings decoded = VarIntUtils.decodeStrings(message);
            String key = decoded.key();

            if ("version_reply".equals(key)) {
                player.sendPluginMessage(this, CHANNEL, serverInfoPayload);
                getLogger().info("Jogador " + player.getName() + " possui o WolfMOD. Server info enviado.");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao processar mensagem do canal " + CHANNEL + " de " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleRadioMessage(Player player, byte[] message) {
        try {
            String raw = new String(message, StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                return;
            }
            String key = parts[0];
            String value = parts[1];

            if ("SELECAO_PNEU".equals(key)) {
                pendingTireChanges.put(player.getUniqueId(), value);
                player.sendPluginMessage(this, CHANNEL, VarIntUtils.encodeString("tire_ack", value));
                player.sendMessage("§e[Wolf] §aPneu selecionado para o próximo pit: §e" + value);
                getLogger().info("Jogador " + player.getName() + " pediu pneus " + value + " para o próximo pit.");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao processar mensagem de rádio de " + player.getName() + ": " + e.getMessage());
        }
    }

    private final class CarPhysicsCommand implements CommandExecutor, TabCompleter {
        private static final List<String> STATES = Arrays.asList("on", "off");

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cUse: /carphysics <jogador> [on|off]");
                    return true;
                }
                if (!player.hasPermission("wolfplugin.carphysics")) {
                    player.sendMessage("§cSem permissão.");
                    return true;
                }
                applyToggle(player, player);
                return true;
            }

            Player target = getServer().getPlayerExact(args[0]);
            String stateArg = null;
            if (target == null && isState(args[0]) && sender instanceof Player self) {
                if (!self.hasPermission("wolfplugin.carphysics")) {
                    self.sendMessage("§cSem permissão.");
                    return true;
                }
                applyState(self, self, parseState(args[0]));
                return true;
            }

            if (target == null) {
                sender.sendMessage("§cJogador não encontrado: " + args[0]);
                return true;
            }

            if (args.length >= 2) {
                stateArg = args[1];
                if (!isState(stateArg)) {
                    sender.sendMessage("§cUse: /carphysics " + target.getName() + " <on|off>");
                    return true;
                }
            }

            boolean selfTarget = sender instanceof Player self
                    && self.getUniqueId().equals(target.getUniqueId());
            if (!selfTarget && !sender.hasPermission("wolfplugin.carphysics.others")) {
                sender.sendMessage("§cSem permissão para alterar outros jogadores.");
                return true;
            }
            if (selfTarget && !sender.hasPermission("wolfplugin.carphysics")) {
                sender.sendMessage("§cSem permissão.");
                return true;
            }

            if (stateArg != null) {
                applyState(sender, target, parseState(stateArg));
            } else {
                applyToggle(sender, target);
            }
            return true;
        }

        private void applyToggle(CommandSender sender, Player target) {
            boolean on = carPhysics.toggle(target.getUniqueId());
            announce(sender, target, on);
        }

        private void applyState(CommandSender sender, Player target, boolean on) {
            carPhysics.setEnabled(target.getUniqueId(), on);
            announce(sender, target, on);
        }

        private void announce(CommandSender sender, Player target, boolean on) {
            String mode = on ? "§aATIVADO" : "§cDESATIVADO";
            target.sendMessage("§e[Wolf] §fModo carro " + mode + " §fpara você.");
            if (!sender.equals(target)) {
                sender.sendMessage("§e[Wolf] §fModo carro " + mode + " §fpara §e" + target.getName() + "§f.");
            }
        }

        private boolean isState(String arg) {
            return "on".equalsIgnoreCase(arg) || "off".equalsIgnoreCase(arg);
        }

        private boolean parseState(String arg) {
            return "on".equalsIgnoreCase(arg);
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(STATES);
                if (sender.hasPermission("wolfplugin.carphysics.others")) {
                    for (Player online : getServer().getOnlinePlayers()) {
                        options.add(online.getName());
                    }
                }
                return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
            }
            if (args.length == 2 && getServer().getPlayerExact(args[0]) != null) {
                return StringUtil.copyPartialMatches(args[1], STATES, new ArrayList<>());
            }
            return Collections.emptyList();
        }
    }
}
