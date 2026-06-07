# SpigotMC listing

## Banner / icon

Upload `images/banner.png` (1280×720) as the resource banner. For the square icon shown in the resource list, crop the centre of the banner to a square using `scripts/crop-banner.js` to produce `banner-square.png`.

## Title

ZDiscord — Discord integration for Paper / Folia / Spigot

## Tag line (max 64 chars)

Chat bridge, tickets, status, account linking, anti-raid, player profiles, confessions.

## Description

ZDiscord bridges your Minecraft server and Discord guild. Chat flows both ways, players see server status from Discord, and staff manage tickets from a dropdown panel.

### Features

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

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled

### Installation

1. Download `ZDiscord-1.1.0.jar` from the downloads section.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels.

### License

Apache License 2.0

## Categories

- Admin Tools
- Chat
- General

## Permissions

- `zdiscord.admin` (op) — reload, status, lockdown, dump
- `zdiscord.discord` (true) — view the Discord invite link
- `zdiscord.link` (true) — generate a link code
- `zdiscord.chat` (true) — have chat forwarded to Discord
- `zdiscord.ticket` (true) — open a support ticket
- `zdiscord.embed` (op) — send a custom embed
- `zdiscord.staffchat` (op) — use the staff chat
- `zdiscord.bypass.antiraid` (op) — bypass anti-raid checks
- `zdiscord.bypass.link` (op) — bypass link-to-join enforcement
- `zdiscord.bypass.console` (op) — reserved (currently unused)

[URL=https://bstats.org/plugin/bukkit/MyZDiscord/29652]bStats[/URL]

## External links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
