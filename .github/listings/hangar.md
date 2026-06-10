# Paper Hangar listing

## Banner / icon

Upload `images/banner.png` (1280×720) as the project banner. For the project avatar / icon, crop the centre of the banner to a square using `scripts/crop-banner.js` to produce `banner-square.png`.

## Project name

ZDiscord

## Project slug

`zdiscord`

## Tagline

Discord integration for Paper servers. Chat bridge, status, tickets, account linking, anti-raid, player profiles, confessions.

## Description

ZDiscord bridges your Minecraft server and Discord guild. Chat flows both ways, players see server status from Discord, and staff manage tickets from a dropdown panel.

Uses JDA 5 with real slash commands and button interactions. Works on Paper 1.20.4+, Folia, and Spigot 1.20.4+ with no per-platform config. Pick from 15+ modules — turn off the ones you're not using.

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

1. Download `ZDiscord-1.2.0.jar` from the versions tab.
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

## Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki

## Tags

`discord` `chat` `utility` `admin` `linking` `tickets` `leaderboard` `anti-raid` `webhooks` `status` `staff`
