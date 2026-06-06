# ZDiscord

Discord integration for Minecraft servers.

## Short description (max 100 chars)

Discord integration for Paper, Folia, and Spigot servers. Chat bridge, tickets, status, account linking.

## Categories

`paper` `spigot` `folia` `utility` `chat` `admin-tools` `discord`

## Client/Server side

Server-side only.

## License

Apache 2.0

## Versions

- Game versions: 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4
- Loader: Paper / Folia / Spigot
- Software: `paper` `spigot` `purpur` `folia`
- Environment: Server

## Dependencies (server-side only)

- JDA 5.2.1 (Discord API for Java — shaded into the JAR)
- discord-webhooks 0.8.4 (shaded)
- Java 17+

## Optional integrations

- PlaceholderAPI
- LuckPerms
- Vault
- Essentials

## Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Releases: https://github.com/DemonZ-Development/ZDiscord/releases

## Long description (Markdown — paste into Modrinth body)

ZDiscord is a self-contained Minecraft plugin that bridges your server and a Discord guild. It ships with the Discord library (JDA) shaded into the JAR, so there is no separate dependency to install.

### Features

- **Chat bridge** — two-way synchronisation between Minecraft and Discord. Player heads are shown as avatars through Discord webhooks.
- **Server status** — a single message in a chosen channel that auto-edits with player count, TPS, and memory.
- **Console streaming** — server log lines forwarded to a Discord channel.
- **Account linking** — one-time codes link Discord and Minecraft accounts. Optionally enforced as link-to-join.
- **Staff chat** — `/sc` toggles a staff-only chat that also bridges to a Discord channel.
- **Tickets** — players open private support channels via a Discord button or `/ticket`.
- **Leaderboards** — kills, deaths, and playtime ranked via `/leaderboard`.
- **Event messages** — joins, quits, deaths, and advancements posted to a Discord channel.
- **Performance monitor** — TPS and memory tracked over time with configurable alerts.
- **Anti-raid** — mass-join detection with optional automatic lockdown.
- **Command logger** — watched and critical commands posted to a staff channel.
- **Voice status** — linked players get a tab-list indicator while they are in a tracked Discord voice channel.
- **Reaction roles** — map message reactions to Discord roles and in-game permissions.
- **Setup wizard** — `/setup` configures channels from Discord with dropdowns and buttons.

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord application with a bot token. Enable the **Server Members** and **Message Content** intents on the bot.

### Quick start

1. Drop `ZDiscord-1.1.0.jar` into your server's `plugins/` directory.
2. Start the server to generate `config.yml` and `messages.yml`.
3. Open `plugins/ZDiscord/config.yml` and set `bot.token`, `bot.guild-id`, and at least `channels.chat`.
4. Restart the server.
5. Run `/setup` in Discord to configure the remaining channels interactively.

Full documentation is in the [wiki](https://github.com/DemonZ-Development/ZDiscord/wiki).
