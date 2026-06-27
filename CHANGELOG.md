# Changelog

All notable changes to ZDiscord are documented here.

## [1.2.0] - 2026-06-16

### Added
- Developer API (`ZDiscordAPI`, `ZDiscordProvider`, Bukkit events) for third-party plugins.
- Interactive leaderboards with medals, head thumbnails, pagination, and stat-switcher dropdown.
- Auto-updating leaderboard panel in a configurable channel.
- Centralized logger (`ZLogger`) with 9 categories, 6 levels, compact format.
- `/profile [player]` — player card with avatar, NameMC link, stats, and follow button.
- `/seen <player]` — last-seen lookup with online status, playtime, sessions.
- `/following` and `/unfollow <player>` — manage follow subscriptions from Discord.
- `/confess <message>` — anonymous confessions with cooldown and configurable color.
- Achievement rarity badges ("First of the day", rare advancement threshold).
- Player activity storage (last_seen, first_join, sessions, advancement unlocks, follows).
- `ColorUtil.toDiscordMarkdown()` for `&l`, `&o`, `&n`, `&m` conversion.
- `FollowModule` — in-memory cache with non-blocking DM dispatch.
- `PlayerProfileBuilder` for profile card embeds with Discord username resolution.
- In-game `/confess <message>` command for anonymous confessions.

### Changed
- `config-version` bumped to 4. Default avatar changed to mc-heads.net.
- `HeadUtil` rewritten for mc-heads.net with `avatar()`, `body()`, `combo()`.
- Storage backends gained 12 new methods for activity, advancements, and follows.
- `AdvancementListener` persists unlocks, reads rarity stats async, guards against duplicates.
- `UpdateChecker` interval 6h → 5h. Silent Discord notice fires once per release.
- `JoinQuitListener` writes last_seen and increments sessions on every join/quit.
- First-join detection uses storage instead of `player.hasPlayedBefore()`.

### Fixed
- `SetupCommand` "Loading options failed" bug and NPE on removed ticket categories.
- `JoinQuitListener` null-safe bot connection checks.
- `/seen` avatar resolution and "never joined" message.
- `FollowModule.onPlayerJoin` no longer blocks scheduler thread.
- `MySQLStorage.isFollowing` uses `SELECT COUNT(*)` instead of fetching all followers.
- Profile card shows actual Discord username instead of raw ID.
- Confession handles use monotonic counter instead of `hash % 10000`.
- Confession embeds use a real love-letter emoji instead of a Discord shortcode.
- Stat update events match the calling thread, fixing Paper quit-event crashes.
- JDA SLF4J provider packaging fixed so startup does not use the fallback logger.
- Ticket setup now uses the guided `/setup` wizard only and posts a ticket-specific setup flow.
- Ticket creation ignores placeholder support-role IDs instead of aborting channel creation.
- Setup and follow buttons use real emoji instead of Discord shortcode text.
- `/panel` no longer crashes in thread or forum channels.
- `UpdateChecker` Discord announcement retries on failure.

## [1.1.0] - 2026-06-06

### Changed
- Version bumped to 1.1.0. `api-version` set to `1.20`.
- `config.yml` and `messages.yml` rewritten without marketing language.
- Status embed centralised in `StatusEmbedBuilder`.
- Avatar URLs centralised in `HeadUtil`, message templates in `PlaceholderUtil`.
- Ticket panel redesigned: `StringSelectMenu` dropdown + "Quick Open" button.
- Status embed: memory progress bar, color-coded health indicator, guild icon thumbnail.
- Performance embed: Unicode sparklines, per-row TPS/Memory fields, alert fields.
- Join/quit embeds rebuilt with title, thumbnail, fields, and footer.
- `StatusModule` and `PerformanceModule` persist message IDs to dedicated YAML files.
- `ConfigManager` exposes `getConfig()` for tests.
- JUnit 5 test suite: config, tickets, status, update-checker, heads, YAML storage.
- Configurable ticket categories (`tickets.categories` list).
- `/panel` slash command and `/zdiscord panel` in-game command.
- `/zdiscord update [check|dismiss]` with clickable notification and dismiss shortcut.
- UpdateChecker re-runs every 5 hours.
- Chat bridge listens for Paper's `AsyncChatEvent` and legacy `AsyncPlayerChatEvent`.

### Fixed
- `ConfigManager` no longer overwrites user config on version bump.
- `ConsoleModule` hooks only the server root logger (fixes double-logging).
- `WebhookManager` replaces forbidden words with `Player` (Discord rejects asterisks).
- `BotManager.connect()` no longer requests non-existent `CacheFlag.MEMBER_OVERRIDES`.
- `StaffChatModule` uses `ConcurrentHashMap.newKeySet()`.
- `TPSUtil` uses `volatile` lazy-init flag.
- `ZDiscordCommand.handleDump` uses try-with-resources.
- `JoinQuitListener` no longer reports -1 player count on quit.
- `DiscordChatListener` requires `misc.console-role` and rejects dangerous commands.
- `WebhookManager` retries on webhook creation failure.
- Ticket creator count decrements on Discord-initiated close.
- `PlaceholderUtil` strips both `&` and `\u00A7` colour codes.
- `ColorUtil` handles null/empty/malformed hex.
- `HeadUtil` handles null names without NPE.

### Added
- Apache 2.0 license headers on all Java source files.
- `ReconnectListener`, `TicketButtonListener`, `SetupCommand`, `SlashCommandManager` wired as JDA listeners.
- `/setup` slash command with interactive module/channel/role wizard.
- Discord role grant on link (`linking.linked-role`).
- Link reward commands via `PlaceholderUtil`.
- `UpdateChecker` connect/read timeouts and explicit UTF-8 charset.
- MySQL leak detection and validation timeout.
- `ReactionRoleModule` grants LuckPerms permissions via `lp user <uuid> permission set <node>`.
- `/zdiscord dump` writes diagnostics file with version, platform, and module state.

### Removed
- Pre-built `ZDiscord-1.0.0-beta.jar` from the repo.
- `.github/workflows/deployer-pipeline.yml` (allowed anyone pushing `workspace.zip` to overwrite the repo).
- Fake SpotBugs / quality / benchmark steps from build workflow.
- ASCII-art banner in `ZDiscord.onEnable`.
- "Premium" marketing language from `plugin.yml`.

## [1.0.0] - Initial release

Initial public release.
