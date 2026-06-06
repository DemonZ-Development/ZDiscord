# ZDiscord

## Resource title

ZDiscord

## Tag line (max 64 chars)

Discord bridge for Paper, Folia, and Spigot servers.

## Categories

- Chat
- Admin Tools
- General

## Minecraft versions

1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4

## Server software

Paper, Spigot, Folia, Purpur

## License

Apache License 2.0

## Source / Links

- GitHub: https://github.com/DemonZ-Development/ZDiscord
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues

## Donation link

(Not configured)

## Description (Markdown — paste into Spigot resource body)

ZDiscord is a self-contained Minecraft plugin that bridges your server with Discord. The Discord library (JDA) is shaded into the JAR, so you do not need to install any extra dependencies on the server.

### Features

- Two-way chat bridge with player head avatars via Discord webhooks
- Auto-updating server status message (player count, TPS, memory)
- Console log streaming to a Discord channel
- Account linking with one-time codes and optional link-to-join enforcement
- Staff chat bridge (`/sc`) with Discord channel sync
- Ticket system with Discord button and `/ticket` command
- Leaderboards for kills, deaths, and playtime
- Event messages for joins, quits, deaths, and advancements
- TPS and memory performance monitor with configurable alerts
- Anti-raid with mass-join detection and optional automatic lockdown
- Command logger for watched and critical commands
- Voice status indicator on the tab list
- Reaction role mapping between Discord reactions and in-game permissions
- Interactive `/setup` wizard for channel configuration

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled

### Installation

1. Download `ZDiscord-1.1.0.jar` from the **Download** button above.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels interactively.

### Support

Bug reports and feature requests belong on the GitHub issue tracker. The full documentation is in the project wiki.
