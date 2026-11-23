package dev.EfraGroup.wolfmod.content;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * WolfContent Fase 1 — aviso de pacote desatualizado (só aviso, sem download).
 *
 * <p>Fase 4 troca este aviso pelo fluxo completo (prompt -&gt; download -&gt; hash -&gt; restart).</p>
 */
public final class ContentPromptToast {
    private ContentPromptToast() {
    }

    public static void show(String requiredVersion, String installedVersion) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("§e[WolfContent] §fPacote de conteúdo §ev" + requiredVersion
                    + " §fdisponível (instalado: v" + installedVersion + "). "
                    + "§7Download automático chega na próxima fase."),
                false);
        }
    }
}
