# ZDiscord

Discord integration for Minecraft servers.

## Project slug (suggested)

`zdiscord`

## Category

`utility` (also reasonable: `admin-tools`)

## License

Apache License 2.0

## Minecraft versions

1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4

## Platform

- Paper
- Folia
- Spigot (and Spigot forks such as Purpur)

## Tags (space- or comma-separated)

`discord` `chat` `bridge` `utility` `admin-tools` `tickets` `voice` `staff-chat`

## Repository / Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Releases: https://github.com/DemonZ-Development/ZDiscord/releases

## Short description (max ~150 chars — used for the project card)

A self-contained Paper/Folia/Spigot plugin that bridges Minecraft with Discord. Chat, tickets, status, account linking, anti-raid, leaderboards. JDA is shaded in.

## Long description (Markdown — paste into Hangar project body)

ZDiscord is a self-contained Minecraft plugin that bridges your server and a Discord guild. The Discord library (JDA) is shaded into the JAR, so you do not need to install any extra dependencies on the server.

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

1. Download `ZDiscord-1.1.0.jar` from the **Versions** tab.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set `bot.token`, `bot.guild-id`, and at least `channels.chat`.
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels interactively.

### Support

Issues go on the GitHub issue tracker. Documentation is in the project wiki.
