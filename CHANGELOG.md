# Changelog

All notable changes to ZDiscord are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `/profile [player]` — renders a player card embed with avatar, NameMC link, first/last seen (Discord timestamps), sessions, playtime, advancement count, link status, follower count, and online indicator. Follow/Unfollow button on the embed subscribes the requester to DM notifications.
- `/seen <player>` — quick last-seen lookup. Returns online/offline status, last-seen timestamp, playtime, and session count.
- `/following` — lists the Minecraft players the requester follows.
- `/unfollow <player>` — stops following a player (previously only the button on `/profile`).
- `/confess <message>` — posts an anonymous confession to `channels.confessions`. Rate-limited (configurable via `confessions.cooldown`, default 300s). Stable monotonic handle per confessor. `&` colour codes convert to Discord markdown.
- Silent update check every 5 hours (was 6). One quiet embed to `misc.update-channel`, no pings. `misc.update-silent` suppresses both the in-game banner and Discord notice.
- Achievement rarity display — "First of the day" badge when the player is the first to unlock that advancement in 24 hours, and "Rare — only N% of players have this" badge below a configurable threshold (default 25%).
- Player activity storage: per-player `last_seen`, `first_join`, `sessions`, advancement unlocks, follow relationships. YAML uses `player_activity.yml`, `advancement_unlocks.yml`, `player_follows.yml`; MySQL uses three new tables.
- `ColorUtil.toDiscordMarkdown(text)` converts `&l` bold, `&o` italic, `&n` underline, `&m` strikethrough to Discord markdown. Colour codes are dropped. Applied to all player-visible embed text.
- `FollowModule` — in-memory player-to-follower cache with non-blocking DM dispatch via `User.openPrivateChannel()`. Cache populates lazily on first join, refreshes on add/remove.
- `LeaderboardModule.getStat(uuid, stat)` for per-player stat lookups.
- `PlayerProfileBuilder` — single source of truth for the profile-card embed. Resolves linked Discord usernames.
- `confessions.color` (default `#9B59B6`) and `confessions.cooldown` (seconds).
- `events.advancement.show-rarity` (default true) and `events.advancement.rarity-threshold` (default 0.25).
- `follow.enabled` config toggle.
- MySQL `isFollowing` uses `SELECT COUNT(*) > 0` instead of fetching all followers.

### Changed
- `YamlStorage` and `MySQLStorage` gained three new files / three new tables each, plus 12 new `StorageManager` methods.
- `AdvancementListener` persists the unlock to storage, reads rarity stats asynchronously, and guards against duplicate events (e.g. from `/reload`). Duplicate events suppress the generic "Unlocked by" field.
- `UpdateChecker` interval changed from 6 to 5 hours. `postSilentDiscordNotice()` fires once per detected release via `AtomicBoolean` guard and respects `misc.update-silent`.
- `JoinQuitListener` writes `last_seen` and increments sessions on every join/quit, keeping timestamps fresh after abnormal disconnects.
- First-join detection uses storage (`getFirstJoin(uuid) == 0`) instead of `player.hasPlayedBefore()`, which breaks after `/reload`.
- `ColorUtil.stripColor` used only internally for pre-processing before `toDiscordMarkdown`.

### Fixed
- `FollowModule.onPlayerJoin` no longer blocks the scheduler thread — `retrieveUserById().complete()` replaced with `.queue()`.
- `MySQLStorage.isFollowing` uses `SELECT COUNT(*)` instead of fetching all followers.
- `/seen` shows "This player has never joined the server" instead of "No activity recorded yet."
- Follow DM notification includes a Discord timestamp of when the player joined.
- Profile card Discord section shows the actual username instead of the raw numeric ID.
- Confession handles use a monotonic `AtomicInteger` counter instead of `hash % 10000`.
- `/panel` no longer crashes when used in a thread or forum channel.
- `UpdateChecker` Discord announcement retries on failure instead of being permanently lost.
- Confession cooldown applies only after successful send.

## [1.1.0] - 2026-06-06

### Changed
- Version bumped to 1.1.0 across `pom.xml` and `plugin.yml`.
- `api-version` set to `1.20` to match the `paper-api 1.20.4` dependency.
- `config.yml` and `messages.yml` rewritten without marketing language, emoji, or boilerplate.
- Status embed centralised in `util/StatusEmbedBuilder.java` — shared by the status module, `/status` slash command, and `/setup` wizard.
- Avatar URL resolution centralised in `util/HeadUtil.java`, message templates in `util/PlaceholderUtil.java`.
- Ticket panel redesigned: `StringSelectMenu` dropdown of categories plus a "Quick Open" button. In-ticket message shows category, opener, and close/claim/transcript actions.
- Ticket button IDs consolidated to `zdiscord_create_ticket:<action>`. Button handler in `TicketButtonListener` handles close, claim, transcript, quick-open, and the category dropdown.
- Status embed rebuilt: full-width memory row with block-character progress bar, color-coded health indicator (green/amber/red), guild icon thumbnail.
- Performance embed rebuilt: Unicode block-character sparklines, per-row "TPS / Memory / Players+Threads" fields, healthy/warning/critical title, alert fields for low TPS and high memory.
- Join/quit embeds rebuilt with title, thumbnail, fields (player, online count, status), and footer from configured templates.
- `StatusModule` and `PerformanceModule` persist edited message IDs to `status_data.yml` / `performance_data.yml` instead of `config.yml`.
- `ConfigManager` exposes `getConfig()` for tests and callers needing raw Bukkit `FileConfiguration`.
- `TicketModule` exposes `loadCategories(root)` for unit-testing without JDA.
- JUnit 5 test suite in CI: config loading, ticket category parsing, status embed structure, update-checker version comparison, head URL resolution, YAML storage backend.
- Configurable ticket categories: `categories` list in `config.yml` (id, label, description, emoji, color). Panel renders from config, re-themeable without rebuilding.
- `tickets.panel.*` config block for panel embed title, description, color, thumbnail, image, and footer.
- `/panel` Discord slash command and `/zdiscord panel` in-game command.
- `tickets.panel-channel` config for pinning the panel channel.
- `/zdiscord update [check|dismiss]` admin subcommand. `check` triggers a Modrinth lookup; `dismiss` suppresses the join-banner.
- Clickable update notifications: `ClickEvent` (openUrl) with `HoverEvent` and dismiss shortcut. Semver comparison treats `1.2.3-beta` as older than `1.2.3`.
- UpdateChecker re-runs every 5 hours for long-running servers.
- Chat bridge listens for Paper's `AsyncChatEvent` (via `PaperChatListener`) and the legacy `AsyncPlayerChatEvent`. On Paper, the modern listener consumes the event to avoid double-sends.

### Fixed
- `ConfigManager` no longer overwrites user config on version bump — missing keys merge in, existing values stay, version stamp updates.
- `ConsoleModule` hooks only the server root logger and skips ZDiscord's own logger, fixing double-logging.
- `WebhookManager` replaces forbidden words (`discord`, `clyde`) with `Player` instead of asterisks (also rejected by Discord). Rate limiter uses `ScheduledExecutorService` instead of `Thread.sleep`.
- `BotManager.connect()` no longer requests the non-existent `CacheFlag.MEMBER_OVERRIDES`.
- `StaffChatModule` toggled-player set uses `ConcurrentHashMap.newKeySet()`.
- `TPSUtil` uses `volatile` lazy-init flag for cross-thread visibility.
- `ZDiscordCommand.handleDump` uses try-with-resources for the writer.
- `JoinQuitListener` no longer reports `-1` player count on quit (defers activity update by two ticks).
- `DiscordChatListener.handleConsoleCommand` requires `misc.console-role` (or Administrator) and rejects dangerous commands (`op`, `deop`, `stop`, `restart`, `reload confirm`, `whitelist remove`).
- `WebhookManager` no longer caches `null` on webhook creation failure — subsequent messages retry.
- Ticket creator count decrements when channel is closed from Discord (close handler resolves requester from permission overrides).
- `PlayerQuitEvent` no longer races the online-count update.
- `PlaceholderUtil` strips both `&` and `\u00A7` colour codes.
- `ColorUtil` handles null/empty/malformed hex.
- `HeadUtil` handles null names without NPE.

### Added
- Apache 2.0 license headers on every Java source file.
- `ReconnectListener`, `TicketButtonListener`, `SetupCommand`, and `SlashCommandManager` registered as JDA event listeners (previously declared but never wired).
- `/setup` slash command: interactive module/channel/role wizard.
- Discord role grant on link (`linking.linked-role`).
- Link reward commands via `PlaceholderUtil` (`%player%`, `%displayname%`, etc.).
- Ticket creator tracked via channel permission overrides.
- `UpdateChecker` connect/read timeouts and explicit UTF-8 charset.
- MySQL leak detection and validation timeout.
- `ReactionRoleModule` grants LuckPerms permissions via `lp user <uuid> permission set <node>`.
- `/zdiscord dump` writes a diagnostics file with version, platform, and module state.

### Removed
- Pre-built `ZDiscord-1.0.0-beta.jar` from the repo.
- `images/feature.png`, `images/logo.png`, `images/banner.png`.
- `.github/workflows/deployer-pipeline.yml` (allowed anyone pushing `workspace.zip` to overwrite the repo and self-commit).
- Fake SpotBugs / quality / benchmark steps from `.github/workflows/build.yml`.
- ASCII-art banner in `ZDiscord.onEnable`.
- "Premium" marketing language and `Premium Only: false` from `plugin.yml`.
- False "1.21+ Paper/Folia/Spigot" claim (actual dependency is 1.20.4).
- Dead bStats badge pointing to a different plugin.

## [1.0.0] - Initial release

Initial public release.
