package dev.EfraGroup.wolfplugin;

import dev.EfraGroup.wolfplugin.utils.VarIntUtils;
import dev.EfraGroup.wolfplugin.vehicle.CarPhysics;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

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

        // Registrar canal outgoing (servidor -> cliente)
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        // Registrar canal incoming (cliente -> servidor)
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, RADIO_CHANNEL, this);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(this, this);
        carPhysics.start();

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
            carPhysics.clearInput(event.getPlayer().getUniqueId());
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
                return;
            }

            if ("boat_input".equals(key) && carPhysics != null) {
                String[] parts = decoded.value().split(",", -1);
                if (parts.length == 4) {
                    carPhysics.updateInput(
                            player,
                            "1".equals(parts[0]),
                            "1".equals(parts[1]),
                            "1".equals(parts[2]),
                            "1".equals(parts[3])
                    );
                }
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
}

