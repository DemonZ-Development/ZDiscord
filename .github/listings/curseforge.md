# ZDiscord

Discord integration for Minecraft servers (Paper, Folia, Spigot).

## Tags

`Paper` `Spigot` `Folia` `Purpur` `Bukkit` `Chat` `Admin Tools` `Utility` `Discord`

## Game versions

- 1.20.4
- 1.20.5
- 1.20.6
- 1.21
- 1.21.1
- 1.21.2
- 1.21.3
- 1.21.4

## Mod loader

`Paper` `Spigot` `Folia` `Purpur` (server-side only)

## License

Apache License 2.0

## Visibility

Public / Open Source

## Donations / Patreon

Not configured.

## Source / Website

- Source: https://github.com/DemonZ-Development/ZDiscord
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues

## Short description (max 250 chars)

A self-contained Minecraft plugin that bridges Paper, Folia, and Spigot servers with Discord. Chat, tickets, status, account linking, anti-raid, leaderboards — all in one JAR with JDA shaded in.

## Long description (Markdown — paste into CurseForge body)

ZDiscord is a self-contained Minecraft plugin that bridges your server and a Discord guild. The Discord library (JDA) is shaded into the JAR, so there is no separate dependency to install. It runs on Paper, Folia, Spigot, and Purpur.

### Features

- Two-way chat bridge with player head avatars via Discord webhooks
- Auto-updating server status message (players, TPS, memory)
- Console log streaming to a Discord channel
- Account linking with one-time codes and optional link-to-join enforcement
- Staff chat bridge (`/sc`) with Discord channel sync
- Ticket system with Discord button and `/ticket` command
- Leaderboards for kills, deaths, and playtime
- Event messages for joins, quits, deaths, and advancements
- TPS and memory performance monitor with configurable alerts
- Anti-raid with mass-join detection and optional lockdown
- Command logger for watched and critical commands
- Voice status indicator on the tab list
- Reaction role mapping between Discord reactions and in-game permissions
- Interactive `/setup` wizard for channel configuration

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled

### Installation

1. Download `ZDiscord-1.1.0.jar` from the **Files** tab.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels interactively.

### Support

Bug reports and feature requests go on the [issue tracker](https://github.com/DemonZ-Development/ZDiscord/issues). The full documentation is in the [wiki](https://github.com/DemonZ-Development/ZDiscord/wiki).
