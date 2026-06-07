# Changelog

All notable changes to ZDiscord are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MockBukkit test framework with JUnit 5. The build workflow now runs
  the unit test suite in CI; new tests cover config loading, ticket
  category parsing, status embed structure, update-checker version
  comparison, head URL resolution, and the YAML storage backend.
- Configurable ticket categories with a `categories` list in
  `config.yml` (id, label, description, emoji, color). The panel
  posted to Discord is rendered from this config so it can be
  re-themed without rebuilding the plugin.
- `tickets.panel.*` config block for title, description, color,
  thumbnail, image, and footer of the ticket panel embed.
- `/panel` Discord slash command and `/zdiscord panel` in-game
  command to (re)post the ticket panel on demand.
- `tickets.panel-channel` config option to pin the panel channel
  for `/zdiscord panel`.
- `/zdiscord update [check|dismiss]` admin subcommand. `check`
  triggers a synchronous Modrinth lookup; `dismiss` suppresses the
  join-banner for the current session.
- Update notifications are now clickable: the URL is a real
  ClickEvent (openUrl) with a HoverEvent, plus a clickable Dismiss
  shortcut. Versions are compared semver-style so `1.2.3-beta` is
  correctly treated as older than `1.2.3`.
- UpdateChecker re-runs every 6 hours so long-running servers pick
  up new releases without a restart.
- Chat bridge now listens for Paper's modern `AsyncChatEvent` (via
  the new `PaperChatListener`) and the legacy
  `AsyncPlayerChatEvent`. On Paper, the modern listener consumes
  the event and cancels the legacy one to avoid double-sends.

### Changed
- Ticket panel is now a beautiful, branded embed with a
  StringSelectMenu (dropdown) of categories plus a "Quick Open"
  button. The in-ticket message was redesigned to include
  category, opener, and the close/claim/transcript actions.
- Ticket button IDs were consolidated: panel actions now share the
  `zdiscord_create_ticket:<action>` format. The button handler is
  isolated in `TicketButtonListener` and handles close, claim,
  transcript, quick-open, and the category dropdown.
- Status embed layout was rebuilt: full-width memory row with a
  block-character progress bar, color-coded health indicator
  (green/amber/red), and a guild icon thumbnail.
- Performance embed layout was rebuilt: Unicode block-character
  sparklines replace the cramped ASCII art graphs; per-row
  "TPS / Memory / Players+Threads" fields; healthy/warning/critical
  title; alert fields for low TPS and high memory.
- Join/quit embeds were rebuilt with title, thumbnail, fields
  (player, online count, status), and a footer line driven by the
  configured `events.join.message` / `events.quit.message` template.
- `StatusModule` and `PerformanceModule` now persist their edited
  message IDs to dedicated `status_data.yml` and
  `performance_data.yml` files instead of writing them into
  `config.yml`.
- `ConfigManager` exposes a `getConfig()` accessor for tests and
  other callers that need the raw Bukkit `FileConfiguration`.
- `TicketModule` exposes a static `loadCategories(root)` helper so
  the category parser can be unit-tested without JDA.

### Fixed
- `WebhookManager` no longer caches a `null` client when webhook
  creation fails — subsequent messages will retry instead of
  silently dropping.
- Ticket creator count is decremented when the channel is closed
  from Discord (the close handler resolves the requester from the
  channel's member permission overrides).
- `PlayerQuitEvent` no longer races the online-count update.
- `PlaceholderUtil` strips both `&` and `\u00A7` colour codes.
- `ColorUtil` correctly handles null/empty/malformed hex.
- `HeadUtil` handles null names without NPE.

## [1.1.0] - 2026-06-06

### Changed
- Bumped version to 1.1.0 across `pom.xml` and `plugin.yml`.
- Switched `api-version` to `1.20` to match the `paper-api 1.20.4` dependency.
- Rewrote `config.yml` and `messages.yml` to remove marketing language, emoji,
  and AI-slop boilerplate.
- Centralised status-embed construction in `util/StatusEmbedBuilder.java` so
  the status module, the `/status` slash command, and the `/setup` wizard
  share a single source of truth.
- Centralised avatar URL resolution in `util/HeadUtil.java` and message
  template resolution in `util/PlaceholderUtil.java`.

### Fixed
- `ConfigManager` no longer overwrites user config on version bump; missing
  keys are merged in, existing values are preserved, and the version stamp is
  updated.
- `ConsoleModule` no longer double-logs each line; it now hooks only the
  server root logger and skips ZDiscord's own logger.
- `WebhookManager` replaces forbidden words (`discord`, `clyde`) with
  `Player` instead of the asterisk-wrapped form (asterisks are also rejected
  by Discord). The rate limiter now uses a `ScheduledExecutorService`
  instead of `Thread.sleep` on the caller's thread.
- `BotManager.connect()` no longer requests the non-existent
  `CacheFlag.MEMBER_OVERRIDES` (would have thrown at startup).
- `StaffChatModule` toggled-player set is now `ConcurrentHashMap.newKeySet()`.
- `TPSUtil` uses a `volatile` lazy-init flag for cross-thread visibility.
- `ZDiscordCommand.handleDump` uses try-with-resources for the writer.
- `JoinQuitListener` no longer reports a `-1` player count on quit (defers
  the activity update by two ticks).
- `DiscordChatListener.handleConsoleCommand` now requires the configured
  `misc.console-role` (or Administrator) and rejects `op`, `deop`, `stop`,
  `restart`, `reload confirm`, and `whitelist remove`.

### Added
- Apache 2.0 license headers on every Java source file.
- ReconnectListener, TicketButtonListener, SetupCommand, and SlashCommandManager
  are now actually registered as JDA event listeners (previously declared
  but never wired up).
- `/setup` slash command: interactive module/channel/role wizard.
- Discord role grant on link (configurable via `linking.linked-role`).
- Link reward commands run via `PlaceholderUtil` (use `%player%`,
  `%displayname%`, etc.).
- Ticket creator tracked via channel permission overrides so the close
  button can find them.
- `UpdateChecker` has connect/read timeouts and an explicit UTF-8 charset.
- MySQL storage has leak detection and a validation timeout.
- `ReactionRoleModule` can grant LuckPerms permissions via `lp user <uuid> permission set <node>`.
- `/zdiscord dump` writes a diagnostics file with version, platform, and
  module state.

### Removed
- Pre-built `ZDiscord-1.0.0-beta.jar` from the repo.
- `images/feature.png`, `images/logo.png`, `images/banner.png`.
- `.github/workflows/deployer-pipeline.yml` (a previous CI step allowed
  anyone pushing `workspace.zip` to overwrite the repo and self-commit).
- Fake SpotBugs / quality / benchmark steps from `.github/workflows/build.yml`.
- ASCII-art banner in `ZDiscord.onEnable`.
- "Premium" marketing language and the `Premium Only: false` claim from
  `plugin.yml`.
- False "1.21+ Paper/Folia/Spigot" claim from `README.md` (the actual
  paper-api dependency is 1.20.4).
- The dead bStats badge that pointed to a different plugin.

## [1.0.0] - Initial release

Initial public release.
