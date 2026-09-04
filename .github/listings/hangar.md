# Paper Hangar listing

## Banner / icon

Upload `images/banner.png` (1280×720) as the project banner. For the project avatar / icon, crop the centre of the banner to a square using `scripts/crop-banner.js` to produce `banner-square.png`.

## Project name

ZDiscord

## Project slug

`zdiscord`

## Tagline

Discord chat, status, tickets, account linking, and player tools for Paper, Folia, and Spigot.

## Description

ZDiscord brings day-to-day Minecraft server activity into Discord. Players can chat across both platforms, check who is online, link their accounts, open tickets, and view profiles or leaderboards. Staff get the same bridge for console output, alerts, and private staff chat.

It uses JDA 6 slash commands, buttons, and menus. The same JAR runs on Paper 1.20.4+, Folia, and Spigot 1.20.4+, and each module can be switched on or off in the configuration.

### Features

- **Chat bridge** — Two-way chat with reply context and webhook avatars. SkinsRestorer is used when available, with Java and Bedrock fallbacks.
- **Server status** — A Discord message that updates itself with player count, TPS, and memory usage.
- **Live stats** — One updating panel for online players, performance, and current leaders.
- **Console streaming** — Server log lines forwarded to a Discord channel.
- **Account linking** — One-time codes link Discord and Minecraft accounts. Enforce link-to-join if you want.
- **Staff chat** — `/sc` toggles a staff-only channel bridged to Discord.
- **Tickets** — Players open private support channels via a Discord button or `/ticket`.
- **Leaderboards** — Kills, deaths, and playtime with paging, stat switching, and timed refreshes.
- **Event messages** — Joins, quits, deaths, and advancements posted to Discord.
- **Performance monitor** — TPS and memory tracked over time with configurable alerts.
- **Anti-raid** — Mass-join detection with optional automatic lockdown.
- **Command logger** — Watched and critical commands posted to a staff channel.
- **Voice status** — Linked players get a tab-list indicator while in a tracked Discord voice channel.
- **Reaction roles** — Map message reactions to Discord roles and in-game permissions.
- **Player profiles** — `/profile [player]` renders a rich embed with avatar, NameMC link, stats, and a follow button.
- **Follow system** — Follow players to get DM notifications when they join. `/following` and `/unfollow` manage subscriptions.
- **Anonymous confessions** — `/confess` posts to a dedicated channel with rate limiting and configurable appearance.
- **Setup wizard** — `/setup` configures channels and roles from Discord.
- **Plugin bridges** — Optional Onlysleep and RedstoneReboot messages use ZDiscord's existing connection.

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled
- MySQL 8.0 or newer only when using the optional MySQL storage backend

### Installation

1. Download `ZDiscord-1.3.1.jar` from the versions tab.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set your bot token and Discord server ID.
5. Restart the server.
6. Run `/setup` in Discord to choose channels and roles.

### License

Apache License 2.0

## Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki

## Tags

`discord` `chat` `utility` `admin` `linking` `tickets` `leaderboard` `anti-raid` `webhooks` `status` `staff`
