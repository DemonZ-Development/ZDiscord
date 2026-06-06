# Paper Hangar listing

## Banner / icon

Upload `images/banner.png` (1280×720) as the project banner. For the project avatar / icon, crop the centre of the banner to a square using `scripts/crop-banner.js` to produce `banner-square.png`.

## Project name

ZDiscord

## Project slug

`zdiscord`

## Tagline

Discord integration for Paper servers. Chat bridge, status, tickets, account linking, anti-raid.

## Description

ZDiscord is a Minecraft plugin that bridges your Minecraft server and Discord guild. It provides chat synchronisation, server status, account linking, tickets, leaderboards, anti-raid, and a small set of utility modules.

It supports Paper 1.20.4 or newer, Folia, and Spigot 1.20.4 or newer.

### Features

- **Chat bridge** — Two-way synchronisation between Minecraft and Discord. Webhooks are used to display player heads as avatars.
- **Server status** — A single message in a Discord channel that auto-edits with player count, TPS, and memory.
- **Console streaming** — Server log lines forwarded to a Discord channel.
- **Account linking** — One-time codes link Discord and Minecraft accounts. Optionally enforced as link-to-join.
- **Staff chat** — `/sc` toggles a staff-only chat that is also bridged to a Discord channel.
- **Tickets** — Players open private support channels via a Discord button or `/ticket`.
- **Leaderboards** — Kills, deaths, and playtime ranked via `/leaderboard`.
- **Event messages** — Joins, quits, deaths, and advancements posted to a Discord channel.
- **Performance monitor** — TPS and memory usage tracked over time with configurable alerts.
- **Anti-raid** — Mass-join detection with optional automatic lockdown.
- **Command logger** — Watched and critical commands posted to a staff channel.
- **Voice status** — Linked players get a tab-list indicator while in a tracked Discord voice channel.
- **Reaction roles** — Map message reactions to Discord roles and in-game permissions.
- **Setup wizard** — `/setup` configures channels from Discord with dropdowns and buttons.

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer
- A Discord application with a bot token. Enable the **Server Members** and **Message Content** intents on the bot.

### Installation

1. Download `ZDiscord-1.1.0.jar` from the versions tab.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels interactively.

### License

Apache License 2.0

## Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki

## Tags

`discord` `chat` `utility` `admin` `linking` `tickets` `leaderboard` `anti-raid` `webhooks` `status` `staff`
