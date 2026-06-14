# Pole Position UI — Design Spec

**Date:** 2026-05-14
**Author:** OWL + EfraMLG
**Status:** Approved

## Overview

Comando `/poleposition` para o mod WolfMOD (Minecraft Fabric) que exibe uma UI estilo meme do Verstappen (1:11.365 em Mônaco) quando um jogador faz a volta mais rápida.

## Comando

- **Comando:** `/poleposition`
- **Argumentos:** nenhum (pega o player que executou)
- **Ação:** dispara a UI completa com skin do player, nome, pista e tempo

## Layout da UI

Divisão horizontal da tela:

### Lado Esquerdo (40%)
- Render 3D do player usando `EntityRenderDispatcher`
- Player com braços cruzados (via `ModelPart` rotation)
- Olhando direto pra câmera
- Skin obtida via `GameProfile` → `MinecraftSessionService`

### Lado Direito (60%)
- **Em cima:** Nome da pista (recebido via packet do servidor)
- **Meio:** Nome do jogador em ciano com glow neon
- **Embaixo:** Timer da volta em dourado com glow effect

## Animações

- **Entrada:** Sweep light da esquerda pra direita (estilo F1), ~0.4s
- **Hold:** UI visível por 8 segundos (160 ticks)
- **Saída:** Fade out

## Especificações Técnicas

| Propriedade | Valor |
|---|---|
| Duração | 8 segundos (160 ticks) |
| Animação entrada | Sweep light left→right |
| Animação saída | Fade out |
| Player 3D | `EntityRenderDispatcher.render()` no `DrawContext` |
| Pose | Braços cruzados via `leftArm`/`rightArm` rotation |
| Skin | `GameProfile` → `MinecraftSessionService` |
| Pista/Timer | Payload packet do servidor |

## Arquivos

- `PolePositionCommand.java` — registra `/poleposition`
- `PolePositionHud.java` — HUD render com player 3D
- `WolfmodClient.java` — registrar comando + tick event

## Dependências

- Reutiliza sistema de packet existente (WolfConfigPayload)
- Reutiliza padrão de HUD existente (FastestLap.java)
- EntityRenderDispatcher do Minecraft
