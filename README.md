<div align="center">

<img src="images/banner.png" alt="ZDiscord" width="100%">

# ZDiscord

Discord integration for Minecraft servers.

[Releases](https://github.com/DemonZ-Development/ZDiscord/releases) · [Wiki](https://github.com/DemonZ-Development/ZDiscord/wiki) · [Issues](https://github.com/DemonZ-Development/ZDiscord/issues)

</div>

---

## What it does

ZDiscord connects your Minecraft server to Discord. Chat flows both ways, players see server status without leaving Discord, and staff manage tickets from a dropdown panel.

## Why ZDiscord?

| | ZDiscord | DiscordSRV |
|---|---|---|
| **Slash commands** | Native JDA 5 slash commands + buttons | Bolted-on text commands |
| **Folia support** | Built-in regionized multithreading | No Folia support |
| **Modular design** | 15+ toggleable modules | Chat bridge only |
| **Account linking** | One-time codes + role grants | Clunky multi-step process |
| **Ticket system** | Built-in dropdown panel with categories | Requires separate plugin |
| **Config migration** | Automatic schema upgrades | Manual editing |
| **Webhook handling** | Rate-limited queue with retry | Rate limit issues under load |
| **Message delivery** | Async with guaranteed delivery | Can drop messages |

## Features

- **Chat bridge** — Two-way chat between Minecraft and Discord. Webhooks display player heads as avatars. Linked players show their Discord name and avatar.
- **Server status** — One Discord message that auto-updates with player count, TPS, and memory usage.
- **Console streaming** — Server log lines forwarded to a Discord channel.
- **Account linking** — One-time codes link Discord and Minecraft accounts. Enforce link-to-join if you want.
- **Staff chat** — `/sc` toggles a staff-only channel bridged to Discord.
- **Tickets** — Players open private support channels via a Discord button or `/ticket`.
- **Leaderboards** — Kills, deaths, and playtime ranked via `/leaderboard`.
- **Event messages** — Joins, quits, deaths, and advancements posted to Discord.
- **Performance monitor** — TPS and memory tracked over time with configurable alerts.
- **Anti-raid** — Mass-join detection with optional automatic lockdown.
- **Command logger** — Watched and critical commands posted to a staff channel.
- **Voice status** — Linked players get a tab-list indicator while in a tracked Discord voice channel.
- **Reaction roles** — Map message reactions to Discord roles and in-game permissions.
- **Player profiles** — `/profile [player]` renders a rich embed with avatar, NameMC link, stats, and a follow button.
- **Follow system** — Follow players to get DM notifications when they join. `/following` and `/unfollow` manage subscriptions.
- **Anonymous confessions** — `/confess` posts to a dedicated channel with rate limiting and configurable appearance.
- **Setup wizard** — `/setup` configures channels from Discord with dropdowns and buttons.

## Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled

## Installation

1. Download `ZDiscord-1.2.0.jar` from the [Releases](https://github.com/DemonZ-Development/ZDiscord/releases) page.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels.

## Configuration

All configuration lives in `plugins/ZDiscord/config.yml`. Summary of major sections:

| Section | Purpose |
|---|---|
| `storage` | YAML or MySQL backend for persistent data |
| `bot` | Bot token, activity, and target guild |
| `channels` | Discord channel IDs for each module |
| `chat` | Chat bridge formatting and webhook options |
| `events` | Join, quit, death, and advancement messages |
| `status` | Server status embed |
| `leaderboard` | Tracked stats and top count |
| `linking` | Account linking, optional enforcement, and reward commands |
| `anti-raid` | Mass-join thresholds and lockdown behaviour |
| `performance` | TPS and memory alert thresholds |
| `tickets` | Categories, panel appearance, support roles |
| `confessions` | Confession channel, cooldown, and color |
| `follow` | Enable/disable follow features |
| `command-logger` | Watched and critical commands |
| `staff-chat`, `voice-status` | Staff chat bridge and voice status indicator |
| `misc` | Update checks, invite link, console role, debug |

User-facing strings live in `messages.yml`. They accept `&` colour codes and the `%prefix%` placeholder.

## Commands

### In-game

| Command | Permission | Description |
|---|---|---|
| `/zdiscord reload` | `zdiscord.admin` | Reload configuration |
| `/zdiscord status` | `zdiscord.admin` | Show bot and platform status |
| `/zdiscord link` | `zdiscord.link` | Generate a link code |
| `/zdiscord embed <title> <description>` | `zdiscord.embed` | Send a custom embed |
| `/zdiscord ticket <subject>` | `zdiscord.ticket` | Open a support ticket |
| `/zdiscord panel` | `zdiscord.admin` | (Re)post the ticket panel |
| `/zdiscord lockdown` | `zdiscord.admin` | Toggle anti-raid lockdown |
| `/zdiscord update [check\|dismiss]` | `zdiscord.admin` | Manual update check / dismiss banner |
| `/zdiscord dump` | `zdiscord.admin` | Write a diagnostics file |
| `/discord` | `zdiscord.discord` | Show the Discord invite link |
| `/sc [message]` | `zdiscord.staffchat` | Send to staff chat (toggle with no message) |

### Discord slash commands

| Command | Description |
|---|---|
| `/status` | Server status |
| `/players` | Online players |
| `/tps` | Server performance |
| `/link <code>` | Link a Discord account to a Minecraft account |
| `/ticket <subject>` | Open a support ticket |
| `/panel` | (Re)post the ticket panel (admin) |
| `/leaderboard <stat>` | View kills, deaths, or playtime leaderboard |
| `/profile [player]` | Player profile card with stats and follow button |
| `/seen <player>` | Quick last-seen lookup |
| `/following` | List players you follow |
| `/unfollow <player>` | Stop following a player |
| `/confess <message>` | Post an anonymous confession |
| `/setup` | Open the setup wizard |

## Building

```bash
git clone https://github.com/DemonZ-Development/ZDiscord.git
cd ZDiscord
mvn clean package
```

The shaded JAR is written to `target/ZDiscord-1.2.0.jar`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
