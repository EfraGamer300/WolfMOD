<div align="center">

# 🐺 WolfMOD

**Mod de corrida para Minecraft Java - Fabric, Quilt, NeoForge e Forge**

[![Fabric](https://img.shields.io/badge/Fabric-1.21.4-green.svg)](https://fabricmc.net)
[![Quilt](https://img.shields.io/badge/Quilt-1.21.4-blueviolet.svg)](https://quiltmc.org)
[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.4-orange.svg)](https://neoforged.net)
[![Forge](https://img.shields.io/badge/Forge-1.21.4-8A8A8A.svg)](https://minecraftforge.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-Private-red.svg)](LICENSE)

*[O mod que acompanha sua corrida]*

</div>

---

## Sobre o Projeto

WolfMOD é um mod Fabric que complementa a experiência de corrida no Minecraft. Desenvolvido para funcionar em conjunto com o plugin FormulaRacing no servidor, o mod adiciona funcionalidades client-side que melhoram a imersão e gameplay nas corridas.

Criado para a **Wolf Network**, funciona tanto no cliente quanto integrado ao servidor via rede.

---

## Funcionalidades

### 🏎️ Veículo
- **Boat Visual State** - Controle visual aprimorado dos barcos
- **Animações Customizadas** - Movimentos realistas ao dirigir
- **Renderização Otimizada** - Sem impacto no FPS

### 👻 Sistema de Ghost
- **Gravação de Voltas** - Grave suas melhores voltas automaticamente
- **Reprodução Suave** - Assista e corra contra fantasmas
- **Ghost Player** - Fantasmas de outros jogadores em tempo real
- **Ghost Renderer** - Renderização otimizada dos fantasmas

### 📻 Radio In-Game
- **Comunicação por Voz** - Radio entre jogadores durante corridas
- **Transcrição Local** - Vosk para reconhecimento de voz
- **Supressão de Ruído** - Noise suppressor integrado
- **Configuração Ajustável** - Volume, frequência e efeitos

### 🖥️ HUD
- **Timer de Volta** - Cronômetro em tempo real
- **Fastest Lap** - Exibição da melhor volta
- **Pause HUD** - Interface durante pausas
- **Customização** - Posições e elementos configuráveis

### 🤖 Comandos
- `/debug` - Informações de debug
- `/fastestlap` - Ver melhor volta
- `/ghosttoggle` - Ativar/desativar ghost

---

## WolfPlugin - Counterpart Server-Side

O WolfMOD inclui o **WolfPlugin**, um plugin Paper que recebe input do mod cliente e aplica **física de carro realista** nos barcos.

### Como Funciona

```
[WolfMOD Client]                    [WolfPlugin Server]
       │                                    │
       ├── Envia input (WASD) ──────────────►│
       │    via Plugin Messages              │
       │                                    │
       │◄── Recebe server info ─────────────┤
       │                                    │
       │                         Aplica física realista:
       │                         • Massa: 1520kg
       │                         • Força do motor: 6400N
       │                         • Freios: 8600N
       │                         • Arrasto aerodinâmico
       │                         • Tração lateral
       │                         • Rotação travada
       ▼                                    ▼
```

### CarPhysics - Física Realista

O `CarPhysics` simula comportamento real de veículo:

| Parâmetro | Valor | Efeito |
|-----------|-------|--------|
| Massa | 1520 kg | Peso realista |
| Força Motor | 6400 N | Aceleração progressiva |
| Freios | 8600 N | Parada responsiva |
| Arrasto | 0.020 | Desaceleração natural |
| Resistência Rolagem | 0.060 | Perda de velocidade |
| Velocidade Máx | 1.0 bloco/tick | Limite realista |
| Tração | 0.96 | Aderência nas curvas |

### Comunicação Cliente-Servidor

**Canal:** `wolfnetwork:settings`

**Mensagens:**
- `version_reply` - Cliente confirma que tem o mod
- `boat_input` - Input do jogador (forward, backward, left, right)
- `server_info` - Servidor envia informações

### Instalação do WolfPlugin

```bash
# O WolfPlugin está em wolfplugin/
cd wolfplugin
../gradlew build

# Copie o JAR para plugins/
cp build/libs/wolfplugin-*.jar ../plugins/
```

---

## Requisitos

| Componente | Versão | Obrigatório |
|------------|--------|-------------|
| Minecraft | 1.21.4 | ✅ Sim |
| Java | 21+ | ✅ Sim |
| Fabric Loader + Fabric API | 0.16.14+ / 0.119.4+ | para o jar do Fabric |
| Quilt Loader + QFAPI | 0.28.1+ | para o jar do Quilt |
| NeoForge | 21.4+ | para o jar do NeoForge |
| Forge | 54+ | para o jar do Forge |
| Paper (servidor) | 1.21.4+ | ✅ (WolfPlugin) |

---

## Instalação

### Jogador

```bash
# 1. Instale Fabric Loader em https://fabricmc.net/use/
# 2. Baixe o WolfMOD.jar
# 3. Coloque na pasta mods/
cp WolfMOD.jar ~/.minecraft/mods/

# 4. Inicie o jogo com Fabric
```

### Desenvolvedor

```bash
git clone https://github.com/EfraGamer300/WolfMOD.git
cd WolfMOD

# Setup
./gradlew genSources

# Build
./gradlew build

# Testar
./gradlew runClient
```

---

## Configuração

O mod pode ser configurado via arquivo `wolfmod.json`:

```json
{
  "hud": {
    "timer": {
      "enabled": true,
      "position": "top_right",
      "color": "#FFFFFF"
    },
    "fastestLap": {
      "enabled": true,
      "position": "top_center"
    }
  },
  "ghost": {
    "enabled": true,
    "maxGhosts": 5,
    "renderDistance": 50
  },
  "radio": {
    "enabled": true,
    "volume": 0.8,
    "noiseSuppression": true
  }
}
```

---

## Integração com FormulaRacing

O WolfMOD funciona em conjunto com o plugin FormulaRacing no servidor:

| Funcionalidade | Plugin (Servidor) | Mod (Cliente) |
|----------------|-------------------|---------------|
| Cronômetro | ✅ | ✅ (HUD) |
| Ghost System | ✅ (Dados) | ✅ (Render) |
| Física | ✅ | - |
| Radio | - | ✅ |
| Comandos | ✅ | ✅ |

### Como Conectar

1. Instale o FormulaRacing no servidor Paper
2. Instale o WolfMOD no cliente Fabric
3. Conecte ao servidor - o mod detecta automaticamente
4. Use `/debug` para verificar a conexão

---

## Loaders

O mod é multi-loader. O código compartilhado fica em `common/` e cada loader tem
um módulo que só registra os eventos que ele oferece.

| Loader | Módulo | Biblioteca de eventos |
|--------|--------|----------------------|
| Fabric | `fabric/` | Fabric API |
| Quilt | `quilt/` | QFAPI (fork do Fabric API) + mixin |
| NeoForge | `neoforge/` | NeoForge events |
| Forge | `forge/` | Forge events |

O `common/` é compilado em **mappings Yarn** (igual era antes). O
`architectury-plugin` remapeia o jar do `common` para Mojang/SRG ao gerar o jar de
Forge e NeoForge — é por isso que o mesmo código-fonte roda nos quatro sem
tradução. Nenhum arquivo do `common/` importa `net.fabricmc`, `net.neoforged`,
`net.minecraftforge` ou `org.quiltmc`: o que precisa de API de loader vira facade
em `common/src/main/java/dev/EfraGroup/wolfmod/platform/`.

### Status de verificação

Esta modularização foi feita sem conseguir compilar (a máquina tem 3 GB de RAM e
4 GB de disco; Loom e o ForgeGradle precisam de muito mais). O que **foi**
verificado:

- `common/` não tem nenhuma dependência de API de loader (checado por grep);
- todos os imports `dev.EfraGroup.*` resolvem para arquivos que existem;
- todos os 51 arquivos Java passam no parser do `javac` sem erro de sintaxe
  (rodando sem classpath, então sobram só "cannot find symbol").

O que **não** foi verificado: se cada módulo compila de fato, e em especial:

- **`forge/`** é o ponto de maior risco. Não existe `architectury-forge` para
  1.21.4 (o último release é `11.1.17`, do 1.20.6/1.21), então esse módulo usa
  o transformer do plugin sem a API do Architectury. Se o `architectury { forge() }`
  não estiver pronto para o Forge 54, esse é o primeiro lugar a olhar.
- `quilt/`: a versão do `quilted-fabric-api` para 1.21.4 não aparece no mirror do
  maven do Quilt — está isolada em `gradle.properties`.
- `quilt/`: o QFAPI não tem `HudRenderCallback` nem `WorldRenderEvents`. Ambos
  foram cobertos por mixin (`DrawContextMixin`, `WorldRendererMixin`), e a
  assinatura de `WorldRenderer.render` é o ponto mais frágil do porte.
- `neoforge/` e `forge/`: os eventos de HUD, render de mundo e tela de pausa foram
  escritos de memória contra a API de cada loader.

Nenhuma funcionalidade foi perdida na migração: a lista de recursos do Fabric
antes da mudança é a mesma depois.

## Desenvolvimento

### Estrutura

```
WolfMOD/
├── common/                        # Código compartilhado (mappings Yarn)
│   └── src/main/
│       ├── java/dev/EfraGroup/wolfmod/
│       │   ├── Wolfmod.java
│       │   ├── client/
│       │   │   ├── WolfClient.java   # Orquestra o client (tick, HUD, rede)
│       │   │   ├── ServerInfoManager.java
│       │   │   ├── TimeTrial/       # Timer, geometria e sweeping de região
│       │   │   ├── ghost/           # Ghost: dados, playback, render
│       │   │   ├── hud/             # HUD, fastest lap, tela de pausa
│       │   │   ├── radio/           # Rádio, Vosk, NIM API
│       │   │   └── vehicle/         # CarPhysics e estado visual do barco
│       │   ├── network/             # WolfConfig, GhostData e Radio payloads
│       │   ├── platform/            # Facades (rede, comandos, mod list)
│       │   └── mixin/client/        # Mixin do renderer de barco
│       └── resources/
│           ├── assets/wolfmod/      # Ícones, lang, texturas
│           ├── wolfmod.accesswidener
│           ├── wolfmod.mixins.json
│           └── wolfmod.client.mixins.json
├── fabric/src/main/                # Entry points + wiring do Fabric API
├── neoforge/src/main/              # Entry points + wiring do NeoForge
├── quilt/src/main/                 # Entry points + mixins do HUD/render
├── forge/src/main/                 # Entry points + wiring do Forge
└── wolfplugin/                     # Plugin Paper (servidor) — build separado
    └── src/main/java/dev/EfraGroup/wolfplugin/
```

### Build

```bash
# Um loader por vez (o build completo tenta os quatro)
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :quilt:build
./gradlew :forge:build

# O Forge precisa de 6-8 GB livres para decompilar o Minecraft.
# Em máquina apertada, pule o módulo:
./gradlew build -PskipForge

# Rodar o client de teste
./gradlew :fabric:runClient
```

**WolfPlugin (Paper)** — build separado, com o próprio `settings.gradle`:
```bash
cd wolfplugin
../gradlew build
# JAR estará em wolfplugin/build/libs/
```

### Dependências

```groovy
dependencies {
    minecraft "com.mojang:minecraft:1.21.3"
    mappings "net.fabricmc:yarn:1.21.3+build.1:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.9"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.106.1+1.21.3"
    
    // Speech recognition
    implementation 'com.alphacephei:vosk:0.3.45'
}
```

---

## Tecnologias Utilizadas

- **Fabric API** - Framework de modding
- **Mixins** - Modificação de classes do jogo
- **Vosk** - Reconhecimento de voz offline
- **NIM API** - Processamento de áudio
- **Yarn Mappings** - Mappings de código aberto
- **Architectury Plugin / Loom** - Multi-loader e remapeamento do `common`

---

## Créditos

**Desenvolvimento:**
- [EfraGamer300](https://github.com/EfraGamer300)
- EfraGroup

**Contribuidores:**
- Wolf Network Team

**Agradecimentos:**
- FabricMC pelo framework
- Comunidade Vosk pelo modelo de reconhecimento

---

## Licença

Uso privado da Wolf Network. Todos os direitos reservados.

---

<div align="center">

**[Wolf Network](https://wolfnetwork.com.br)** • **[GitHub](https://github.com/EfraGamer300/WolfMOD)**

© 2025 Wolf Network. Todos os direitos reservados.

</div>
