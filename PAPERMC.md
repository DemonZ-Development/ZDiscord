# ZDiscord

ZDiscord connects your Minecraft server to Discord: two-way chat, live server status, account linking, and a built-in ticket system, all powered by native JDA 5 slash commands and buttons.

## Highlights

- Two-way chat bridge with webhook avatars (player heads)
- Live server status message that auto-updates with players, TPS, and memory
- Account linking with one-time codes and role grants
- Dropdown ticket panel with categories and support roles
- Staff chat, console streaming, and event messages (joins, deaths, advancements)
- Leaderboards for kills, deaths, and playtime
- Player profiles and a follow system with DM notifications
- Anti-raid detection, performance alerts, and a command logger
- Voice status, reaction roles, and anonymous confessions
- 15+ toggleable modules; turn off whatever you do not need
- Runs on Paper, Spigot, and Folia without extra setup
- Automatic config migration between versions

## Requirements

- Java 17 or newer
- Paper 1.20.4+, Spigot 1.20.4+, or Folia
- A Discord bot token with the Server Members and Message Content intents enabled

## Installation

1. Download the ZDiscord JAR from the releases page.
2. Place it in your server's `plugins/` folder.
3. Start the server to generate `config.yml` and `messages.yml`.
4. Edit `plugins/ZDiscord/config.yml` and set `bot.token`, `bot.guild-id`, and `channels.chat`.
5. Restart the server and run `/setup` in Discord to configure the remaining channels.

## Configuration

All settings live in `config.yml`: storage (YAML or MySQL), bot, channels, chat formatting, events, status embed, linking, anti-raid, tickets, confessions, follow, and more. User-facing text is in `messages.yml`, which accepts `&` color codes and the `%prefix%` placeholder.

## Commands

In-game: `/zdiscord reload`, `/zdiscord link`, `/zdiscord ticket`, `/sc` for staff chat, `/confess`, and more.
Discord: `/status`, `/players`, `/tps`, `/link`, `/ticket`, `/leaderboard`, `/profile`, `/seen`, `/setup`.

## Links

- Source: https://github.com/DemonZ-Development/ZDiscord
- Issues: https://github.com/DemonZ-Development/ZDiscord/issues
- Wiki: https://github.com/DemonZ-Development/ZDiscord/wiki
- Discord: https://discord.com/invite/GYsTt96ypf

## License

Apache License 2.0