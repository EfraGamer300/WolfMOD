package dev.EfraGroup.wolfmod.client;

import net.minecraft.client.MinecraftClient;

public class ServerInfoManager {
    private static boolean isOnWolfNetwork = false;
    private static String currentSubServer = "Unknown";
    private static String displayServerName = "WolfNetwork";

    // Domínios aceitos como WolfNetwork
    private static final String[] WOLF_DOMAINS = {
        "wolfnetwork.com.br",
    };

    public static void updateFromServer(String subServer) {
        if (!isOnWolfNetwork) return;
        currentSubServer = subServer;
    }

    public static void checkCurrentServer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() == null) {
            isOnWolfNetwork = false;
            currentSubServer = "Unknown";
            return;
        }

        String address = client.getCurrentServerEntry().address.toLowerCase();
        boolean isWolf = false;
        for (String domain : WOLF_DOMAINS) {
            if (address.contains(domain)) {
                isWolf = true;
                break;
            }
        }
        isOnWolfNetwork = isWolf;
    }

    public static void require() {
        if (!isOnWolfNetwork) {
            throw new WolfNetworkException();
        }
    }

    public static class WolfNetworkException extends RuntimeException {
    }

    public static boolean isOnWolfNetwork() {
        return isOnWolfNetwork;
    }

    public static String getCurrentSubServer() {
        return currentSubServer;
    }

    public static void setDisplayServerName(String name) {
        displayServerName = name;
    }

    public static String getDisplayServerName() {
        return displayServerName;
    }

    public static void reset() {
        isOnWolfNetwork = false;
        currentSubServer = "Unknown";
        displayServerName = "WolfNetwork";
    }
}

