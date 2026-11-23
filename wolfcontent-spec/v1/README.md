# WolfContent spec v1 (referencia congelada — Fase 1)

Schemas de referencia do formato de pacote de dados. Nao entram no build do mod
(pasta fora dos modulos gradle). Motor v1.0.0 le este formato.

- `pack.example.json` — formato do `pack.json` (format=1).
- `definitions/*.json` — 1 por bloco/item piloto (slots 12,13,14,20).
- `behaviors/*.json` — verbos por bloco (lista fechada v1: impulse, bounce, friction,
  climb, emit_particles, emit_sound, interact_action, tick_effect, state_cycle,
  light, redstone_source).
- `mappings/pilot.json` — minecraft:26.x <-> wolfnetwork:* <-> fallback Via*.
- `manifest.example.json` — manifest v2 (engine + packs data).

Protocolo correspondente: `wc_v1_hello` / `wc_v1_status` (protocol=1).
Comparacao de `contentVersion`: numerica ("9" < "12").
