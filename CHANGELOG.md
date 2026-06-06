# Changelog

All notable changes to ZDiscord are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
