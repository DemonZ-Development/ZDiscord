ZDiscord connects Minecraft and Discord directly. Players chat across both platforms, check who is online, link accounts, open tickets, and view profiles or leaderboards. Staff use the same connection for console logs, alerts, and private staff chat.

### How it fits your server

Commands use native Discord slash commands, buttons, and selection menus. One JAR runs on Paper, Folia, and Spigot. You can toggle each module in the configuration.

### Features

- **Chat bridge**: Two-way chat with reply context and webhook avatars. Supports SkinsRestorer with Java and Bedrock fallbacks.
- **Server status**: An updating Discord message showing player count, TPS, and memory usage.
- **Live stats**: Combined panel showing online players, server performance, and current leaders.
- **Console streaming**: Forwards server console logs to a Discord channel.
- **Account linking**: One-time codes link Discord and Minecraft accounts, with optional link-to-join enforcement.
- **Staff chat**: `/sc` toggles a staff-only channel bridged to Discord.
- **Tickets**: Players open private support channels from Discord buttons or `/ticket`, with inactivity auto-close, staff claims, and full chronological transcripts.
- **Leaderboards**: Track kills, deaths, and playtime with paging, stat switching, and timed refreshes.
- **Event messages**: Posts joins, quits, deaths, and advancements to Discord.
- **Performance monitor**: Tracks TPS and memory over time with configurable alerts.
- **Anti-raid**: Detects mass joins and triggers automated lockdown when needed.
- **Command logger**: Logs watched and critical commands to a staff channel.
- **Voice status**: Shows a tab-list indicator when linked players join a Discord voice channel.
- **Reaction roles**: Maps message reactions to Discord roles and in-game permissions.
- **Player profiles**: `/profile [player]` shows an embed with the player avatar, NameMC link, stats, and a follow button.
- **Follow system**: Get DM notifications when followed players join. Manage subscriptions with `/following` and `/unfollow`.
- **Anonymous confessions**: `/confess` posts to a designated channel with cooldowns and customizable embeds.
- **Setup wizard**: `/setup` configures channels and roles directly from Discord.
- **Halloween event**: A configurable seasonal mob hunt with points, standings, milestones, and finale rewards. Check progress with `/zdiscord halloween` in-game or `/halloween` in Discord. Disable with `halloween.enabled: false`.
- **Plugin bridges**: Forwards Onlysleep and RedstoneReboot messages through your existing bot connection.

### Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord bot token with **Server Members** and **Message Content** intents enabled
- MySQL 8.0 or newer only when using the optional MySQL storage backend

### Installation

1. Download `ZDiscord-1.5.0.jar` from the releases page.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set your bot token and Discord server ID.
5. Restart the server.
6. Run `/setup` in Discord to choose channels and roles.

### Configuration

All settings live in `plugins/ZDiscord/config.yml`. User-facing Minecraft text is in `messages.yml`. YAML storage works without a database; MySQL is available when you need shared or larger-scale storage.
