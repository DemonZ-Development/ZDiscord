<div align="center">

<img src="images/banner.png" alt="ZDiscord Banner" width="100%">

<br/>

# ⚡ ZDiscord

### Premium Discord ↔ Minecraft Integration

[![Version](https://img.shields.io/badge/version-1.0.0--beta-blue?style=for-the-badge)](https://github.com/DemonZDevelopment/ZDiscord/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green?style=for-the-badge&logo=minecraft)](https://papermc.io)
[![Platform](https://img.shields.io/badge/Paper%20%7C%20Folia%20%7C%20Spigot-Compatible-orange?style=for-the-badge)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17+-red?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![bStats](https://img.shields.io/bstats/servers/29652?style=for-the-badge&label=Servers&color=cyan)](https://bstats.org/plugin/bukkit/MyZDiscord/29652)

**The most feature-rich Discord bridge for Minecraft servers.**  
One plugin. Zero compromises. Built by [DemonZ Development](https://github.com/DemonZDevelopment).

[📥 Download](#installation) · [📖 Wiki](#configuration) · [🐛 Issues](https://github.com/DemonZDevelopment/ZDiscord/issues) · [💬 Discord](https://discord.gg/CJfEH3qKF)

</div>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 💬 Bidirectional Chat Bridge
Messages sync between Minecraft and Discord in real-time using webhooks with player head avatars.

### 📊 Live Server Status
Auto-updating status embed with players, TPS, memory, and server IP — refreshes every 30 seconds.

### 🔗 Account Linking
Link Discord accounts to Minecraft with one-time codes. Optional **Link-to-Join** enforcement.

</td>
<td width="50%">

### 🔒 Command Logger
Watches dangerous commands (`/op`, `/stop`, `/ban`) and logs them to Discord with severity levels.

### 💬 Staff Chat
Bidirectional `/sc` command bridges in-game staff chat with Discord. Toggle mode included.

### 🎙️ Voice Status
Shows a 🎙️ indicator on linked players who are active in Discord voice — real-time, no polling.

</td>
</tr>
</table>

### And much more...

| Feature | Description |
|---------|-------------|
| 🎫 **Ticket System** | Create support tickets from Discord with a button panel + auto-categories |
| 🏆 **Leaderboards** | Track kills, deaths, and playtime with Discord `/leaderboard` command |
| 🛡️ **Anti-Raid** | Auto-lockdown on mass join detection with configurable thresholds |
| 📈 **Performance Monitor** | TPS/memory alerts posted to Discord when thresholds are breached |
| 🎭 **Reaction Roles** | Assign Discord roles via reactions — one click setup |
| 📢 **Event Messages** | Join, quit, death, and advancement events forwarded to Discord |
| ⚙️ **Setup Wizard** | `/setup` command configures everything from Discord — no file editing |
| 📋 **Support Dump** | `/zdiscord dump` generates a diagnostics file for troubleshooting |
| 📊 **bStats** | Server metrics tracked via [bStats](https://bstats.org/plugin/bukkit/MyZDiscord/29652) |

---

## 🚀 Installation

### Requirements
- **Java 17+**
- **Paper 1.21+**, Folia, or Spigot
- A [Discord Bot](https://discord.com/developers/applications) with:
  - `GUILD_MESSAGES`, `GUILD_MEMBERS`, `MESSAGE_CONTENT`, `GUILD_VOICE_STATES` intents enabled

### Quick Start

1. **Download** `ZDiscord.jar` and drop it in your `plugins/` folder
2. **Start** your server — config files will be generated
3. **Edit** `plugins/ZDiscord/config.yml` — paste your bot token
4. **Restart** your server
5. **Run** `/setup` in Discord to see the setup wizard

```yaml
# config.yml — Just set these two values to get started:
bot:
  token: "YOUR_BOT_TOKEN_HERE"
  guild-id: "YOUR_GUILD_ID"
```

---

## ⚙️ Configuration

All configuration is in `plugins/ZDiscord/config.yml`. The plugin also supports **live setup from Discord**:

| Command | What It Does |
|---------|-------------|
| `/setup` | Opens the interactive setup wizard |
| `/setup module:chat channel:#channel` | Sets the chat bridge channel |
| `/setup module:status channel:#channel` | Configures live server status |
| `/setup module:events channel:#channel` | Sets the events channel |
| `/setup module:console channel:#channel` | Sets the console channel |
| `/setup module:tickets channel:#channel` | Creates a ticket panel with button |
| `/setup module:staffchat channel:#channel` | Sets the staff chat channel |
| `/setup module:cmdlog channel:#channel` | Sets the command logger channel |

---

## 📋 Commands

### In-Game Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/zdiscord reload` | `zdiscord.admin` | Reload configuration |
| `/zdiscord status` | `zdiscord.admin` | View bot connection info |
| `/zdiscord link` | `zdiscord.link` | Generate account link code |
| `/zdiscord dump` | `zdiscord.admin` | Generate support diagnostics |
| `/zdiscord embed <title> <desc>` | `zdiscord.embed` | Send custom embed |
| `/zdiscord lockdown` | `zdiscord.admin` | Toggle anti-raid lockdown |
| `/discord` | `zdiscord.discord` | Show Discord invite link |
| `/sc [message]` | `zdiscord.staffchat` | Staff chat (toggle or message) |

### Discord Slash Commands
| Command | Description |
|---------|-------------|
| `/status` | View server status |
| `/players` | View online players |
| `/tps` | View server performance |
| `/link <code>` | Link your Discord account |
| `/ticket <subject>` | Create a support ticket |
| `/leaderboard <stat>` | View leaderboards |
| `/setup` | Setup wizard + channel configuration |

---

## 🔌 Developer API

Use ZDiscord as a dependency in your plugins via [JitPack](https://jitpack.io/#DemonZDevelopment/ZDiscord):

### Maven
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.DemonZDevelopment</groupId>
    <artifactId>ZDiscord</artifactId>
    <version>1.0.0-beta</version>
    <scope>provided</scope>
</dependency>
```

### Gradle
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.DemonZDevelopment:ZDiscord:1.0.0-beta'
}
```

---

## 🏗️ Building from Source

```bash
git clone https://github.com/DemonZDevelopment/ZDiscord.git
cd ZDiscord
mvn clean package
```

The compiled JAR will be in `target/ZDiscord-1.0.0-beta.jar`.

---

## 🤝 Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📜 License

This project is licensed under the Apache License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ by [DemonZ Development](https://github.com/DemonZDevelopment)**

⭐ Star this repo if you find it useful!

</div>
