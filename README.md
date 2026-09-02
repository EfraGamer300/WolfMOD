<div align="center">

# 🐺 WolfMOD

**Mod de corrida para Minecraft Bedrock e Java - Fabric**

[![Fabric](https://img.shields.io/badge/Fabric-1.21+-green.svg)](https://fabricmc.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.3-blue.svg)](https://minecraft.net)
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
| Minecraft | 1.21.3+ | ✅ Sim |
| Fabric Loader | 0.16.9+ | ✅ Sim |
| Fabric API | 0.106.1+ | ✅ Sim |
| Java | 21+ | ✅ Sim |
| Paper (servidor) | 1.21+ | ✅ (WolfPlugin) |

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

## Desenvolvimento

### Estrutura

```
WolfMOD/
├── src/                        # Mod Fabric (cliente)
│   ├── client/
│   │   ├── java/dev/EfraGroup/wolfmod/client/
│   │   │   ├── commands/      # Comandos do cliente
│   │   │   ├── ghost/         # Sistema de ghost
│   │   │   ├── hud/           # Interface HUD
│   │   │   ├── radio/         # Sistema de radio
│   │   │   ├── vehicle/       # Controle de veículo
│   │   │   └── WolfmodClient.java
│   │   └── resources/         # Mixins e assets
│   └── main/
│       └── java/dev/EfraGroup/wolfmod/
│           ├── network/       # Pacotes de rede
│           └── Wolfmod.java
├── wolfplugin/                 # Plugin Paper (servidor)
│   └── src/main/java/dev/EfraGroup/wolfplugin/
│       ├── WolfPlugin.java    # Handshake e comunicação
│       ├── vehicle/
│       │   └── CarPhysics.java # Física de carro realista
│       └── utils/
│           └── VarIntUtils.java # Codificação de mensagens
└── Wolfmod.java
```

### Build

**Mod Fabric:**
```bash
# Build completo
./gradlew build

# Apenas client
./gradlew buildClient

# Gerar fontes
./gradlew genSources

# Executar client de teste
./gradlew runClient
```

**WolfPlugin (Paper):**
```bash
cd wolfplugin
./gradlew build
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
