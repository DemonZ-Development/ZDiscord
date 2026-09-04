# ZDiscord

ZDiscord keeps your Minecraft server and Discord community in the same conversation. Chat works in both directions, status panels update themselves, players can link their accounts, and staff can handle tickets without adding another support bot.

## Highlights

- Two-way chat with webhook avatars and reply context
- Player heads from SkinsRestorer when it is available, with Java and Bedrock fallbacks
- Self-updating status and live-stat panels for players, TPS, memory, and top players
- Interactive leaderboards for kills, deaths, and playtime, including paging and timed refreshes
- One-time account linking with optional role grants and join enforcement
- Private ticket channels with configurable categories and support roles
- Staff chat, console streaming, and join, quit, death, and advancement messages
- Player profiles, last-seen lookups, and join notifications for followed players
- Anti-raid controls, performance alerts, and watched-command logging
- Voice status, reaction roles, and anonymous confessions
- Optional Onlysleep and RedstoneReboot messages through the same Discord connection
- A Discord setup wizard for choosing channels and roles
- YAML storage by default, with MySQL available for larger setups
- Automatic configuration migration between versions

## Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Spigot 1.20.4 or newer, or Folia
- A Discord bot token with the **Server Members** and **Message Content** intents enabled
- MySQL 8.0 or newer only if you choose the MySQL storage backend

## Installation

1. Download the latest ZDiscord JAR from [GitHub Releases](https://github.com/DemonZ-Development/ZDiscord/releases).
2. Place it in your server's `plugins/` folder.
3. Start the server to generate `config.yml` and `messages.yml`.
4. Set `bot.token` and `bot.guild-id` in `plugins/ZDiscord/config.yml`.
5. Restart the server, then run `/setup` in Discord to choose channels and roles.

The setup wizard saves its choices to the same configuration file. You can still edit the file directly and use `/zdiscord reload` when you need finer control.

## Configuration

All settings live in `config.yml`. Each module can be enabled separately, and channels for chat, events, status, tickets, console output, staff chat, live stats, and integrations can be kept together or split apart. User-facing Minecraft text is in `messages.yml`, which accepts `&` colour codes and the `%prefix%` placeholder.

## Commands

In Minecraft, `/zdiscord` covers configuration, diagnostics, linking, tickets, embeds, lockdown, update checks, and support dumps. `/discord`, `/sc`, and `/confess` provide the player-facing shortcuts.

On Discord, members can use `/status`, `/players`, `/tps`, `/link`, `/ticket`, `/leaderboard`, `/profile`, `/seen`, `/following`, `/unfollow`, and `/confess`. Administrators use `/setup` and `/panel`.

## Links

- [Source code](https://github.com/DemonZ-Development/ZDiscord)
- [Issues](https://github.com/DemonZ-Development/ZDiscord/issues)
- [Wiki](https://github.com/DemonZ-Development/ZDiscord/wiki)
- [Discord support](https://discord.com/invite/GYsTt96ypf)

## License

Apache License 2.0. See the repository's [LICENSE](https://github.com/DemonZ-Development/ZDiscord/blob/main/LICENSE) file.
