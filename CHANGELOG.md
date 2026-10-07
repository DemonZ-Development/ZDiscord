# Changelog

All notable changes to ZDiscord are documented here.

## [1.4.3] - 2026-10-07

### Added
- **Halloween event** — A configurable seasonal mob hunt, promoted from the old secret Oct 31 easter egg. The date window is set with `halloween.window-start` / `halloween.window-end` (`MM-DD`; an end that sorts before the start wraps into the next year, so `10-28` to `11-02` is a five-day hunt). Mob kills during the window are tallied under a separate `halloween_kills` stat, ranked with a winner crowned in a finale embed once it closes. PvP kills do not count.
- **Countdown announcements** — Enabled by default. Posts once a day in Discord and broadcasts in-game during the final `halloween.countdown.days` days before the window opens, with the top milestone shown so there is something to chase. Set `halloween.countdown.days: 0` to silence it.
- **Live standings panel** — A self-refreshing embed of the current rankings, shown for the duration of the window so the channel reacts to kills on its own. Configure with `halloween.live-panel`.
- **Milestone announcements** — The channel reacts the first time a player crosses each kill count in `halloween.milestones` (25/50/100/250/500 by default), including how far the player is from the next one.
- **Boss bonus** — The ender dragon, wither and elder guardian count for `halloween.boss-multiplier` kills (5 by default) instead of 1.
- **Rewards** — `halloween.rewards.winner` and `halloween.rewards.participant` dispatch console commands when the window closes, with `%player%` and `%uuid%` placeholders. Only players with at least one kill are paid.
- New Discord slash command `/halloween` shows the current event state, standings, and countdown.
- The countdown, opening announcement, closing winner, and join-time greeting are all sent to regular players, not just operators.

### Changed
- Configuration schema version bumped to 15.
- The startup banner now counts 17 modules, including the Halloween event.

### Fixed
- The event tally is keyed to the window it belongs to, so restarting the server mid-event no longer wipes the standings.

## [1.4.2] - 2026-10-05

### Added
- Added standalone `/link` in-game command as a direct shortcut for `/zdiscord link`.
- Added `chat.enabled` boolean option in `config.yml` to allow toggling the chat bridge without clearing the configured channel ID.

### Changed
- Configuration schema version bumped to 13.
- In-game `/zdiscord` help and tab completion now filter subcommands based on player permissions.

### Fixed
- Fixed `/zdiscord link` permission check blocking players without `zdiscord.admin` from generating account linking codes.
- Fixed playtime leaderboard stats not updating while players remain connected online. Active session playtime is now flushed periodically and on server reload/shutdown.
- Fixed stale leaderboard cached rankings when clicking the Discord refresh button or when stats are updated.

## [1.4.1] - 2026-10-03

### Added
- A secret Halloween easter egg was added.

## [1.4.0] - 2026-09-22

### Added
- Optional CraftyAI integration via `/crafty` slash command for asking questions from Discord with private replies, rate limiting, and role/channel allowlists.

### Changed
- Configuration schema version bumped to 12.
- Updated official website links to demonz.org.

### Fixed
- Fixed an infinite loop in `LeaderboardModule` that could freeze the server thread when recording stats for a player without prior entries.

## [1.3.1] - 2026-08-30

### Fixed
- The startup banner now derives its module total from all 16 current modules, including Live Stats, Confession, and the Onlysleep/RedstoneReboot integration bridge.

### Changed
- Completed a maintainability pass across configuration, storage, Discord bridge, module, and utility code while preserving public API documentation and runtime behavior.
- Added regression coverage for startup module accounting.

## [1.3.0] - 2026-08-30

### Added
- Real profile pictures. If SkinsRestorer is installed, `/profile` cards and leaderboard thumbnails use the player's actual skin instead of the default Steve/Alex head. Set `profile.skin-restorer` to `false` to disable.
- Discord replies are now quoted in Minecraft chat. Replying to a bridged message shows a short "Replying to X: ..." line above it, so it's clear what the message is answering. See `chat.show-replies`, `chat.reply-format`, `chat.reply-preview-length`.
- More console logging: account links/unlinks, ticket open/close, and a shutdown summary with storage flush confirmation.
- `SkinsRestorer` listed as a soft dependency in `plugin.yml`.
- `/zdiscord diagnostics` — a read-only health check in-game: storage backend and unflushed writes, Discord ping, active webhooks, SkinsRestorer/Geyser detection with Bedrock player count, and module states.
- **Live stats panel** — one combined embed that refreshes itself in a channel: players online (with names), TPS, memory, top kills, most playtime, and most followed. Enable it with `live-stats.channel` in config.yml; refresh rate via `live-stats.update-interval`.
- **Self-refreshing `/leaderboard` replies** — command responses now re-edit themselves with fresh data every `leaderboard.auto-refresh-seconds` (default 60s, 0 disables) and follow you when you page or switch stats. They stop after 30 minutes or if the message is deleted.
- `logging.debug: false` in config.yml — one switch for troubleshooting. Turns every log category to DEBUG, unsuppresses JDA/Hikari connection logs, and applies live via `/zdiscord reload` (previously logging changes needed a restart). `/zdiscord diagnostics` shows whether it's on.
- Geyser/Floodgate awareness for avatars. Bedrock players get name-based mc-heads heads when SkinsRestorer has no skin for them instead of a guaranteed-broken UUID URL. `chat.avatar-url` now also accepts `"auto"` (the new default) which picks the best source per player; custom `%uuid%`/`%name%` formats still work everywhere.
- The "Most Followed Players" leaderboard panel now reads follower counts from storage, so players who haven't joined since the last restart no longer disappear from it.
- Ticket channels store their owner in the channel topic, so closing a ticket always frees the right person's ticket slot.
- Optional Onlysleep and RedstoneReboot bridges forward sleep/night and restart messages through ZDiscord's existing bot connection. Each can use a dedicated channel or fall back to `channels.events`.
- The public API can queue plain messages and `EmbedData` objects to configured Discord channels.

### Changed
- `config-version` bumped to 11 so existing configs pick up all avatar, logging, live-stats, reply, and integration keys. Untouched `chat.avatar-url` defaults are migrated to `"auto"` automatically.
- JDA updated from 5.2.1 to 6.5.0 for current Discord API behavior and 2026 permission changes; SLF4J updated/aligned to 2.0.18 and relocated inside the plugin JAR.
- MySQL connections encrypt by default (`use-ssl: true`) but no longer demand a CA-signed certificate; set `storage.mysql.ssl-verify-certificate: true` if yours has one. The official Connector/J driver is now bundled and relocated, so MySQL 8+ storage works on a clean server without another plugin supplying a driver.
- Stat-update events (`ZDiscordStatUpdateEvent`) fire before the value lands and cancelling one no longer rolls back increments that raced with it.
- `/zdiscord embed` reports "bot not connected" instead of throwing when startup failed.

### Fixed
- Emoji shortcodes (`:green_circle:` etc.) showed as raw text in every embed - Discord only renders shortcodes in plain messages, not embed titles/fields/footers. All ~80 usages across panels, cards, tickets, and status embeds now use real Unicode emoji, which render everywhere. (config-version 10)
- YAML storage could silently drop writes: a save landing between the flusher's file snapshot and its dirty-flag reset was lost until restart. Dirty flags now live under the same lock as the save itself.
- Pending MySQL writes queued during shutdown ran anyway instead of being cancelled with the scheduler teardown.
- `/ticket` from Discord no longer blocks JDA's gateway thread during channel creation (chat relay froze while tickets were being made).
- `/players` and the status embed no longer risk a `ConcurrentModificationException` when someone joins or leaves mid-refresh; the status panel just skips the player list that tick.
- Two simultaneous first messages in a channel can't create duplicate "ZChat" webhooks anymore (orphans count against Discord's 10-webhook channel cap).
- Unlinking no longer clears the reverse lookup when legacy data maps several players to one Discord ID - the other linked player keeps working.
- Startup gives up on a stuck Discord connection after 15 seconds instead of hanging the server enable phase forever.
- Transcript button on a fresh empty ticket replies with a friendly message instead of an exception.
- Numbers formatted into URLs and embeds (TPS, memory, K/D, avatar URLs) now use locale-independent formatting, so servers running under digit-substituting locales don't produce broken image links.
- Skin cache is bounded (~2048 entries) so very large servers don't accumulate entries forever; confession/follow cooldown maps evict expired entries too.
- SkinsRestorer's overloaded texture helper is resolved by its `SkinProperty` parameter, avoiding a runtime mismatch with the `String` overload.
- Release automation publishes only the shaded runtime JAR and now runs from immutable `v*` tags instead of every push to `main`.
- Discord console commands keep a hard denylist even when a custom allowlist is configured; shutdown, permission escalation, arbitrary command execution, and plugin reload commands cannot be enabled remotely.
- Minecraft chat cannot trigger Discord `@everyone`, `@here`, or role/user mentions, and console/webhook buffers now have hard memory bounds under message floods.

- Ticket numbering no longer resets to #1 after a restart (the counter was written as a string and read back as 0).
- ZDiscord no longer crashes at startup if the server console can't display unicode; the startup banner and console messages are ASCII-safe.
- `status.embed.color` is honored again; it was silently ignored during the rewrite.
- TPS reading falls back to a flat 20.0 on old Spigot builds instead of breaking the embed.

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
- Ticket panel dropdown values now submit category IDs instead of display labels.
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
