# Contributing

Thanks for your interest in ZDiscord. Issues, pull requests, and wiki
edits are all welcome.

## Workflow

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/my-change`
3. Make your change. Follow the existing code style and add an Apache 2.0
   license header to any new file.
4. Verify it builds: `mvn clean package`
5. Push to your fork and open a pull request against `main`.

## Building

```bash
git clone https://github.com/DemonZ-Development/ZDiscord.git
cd ZDiscord
mvn clean package
```

The shaded JAR is written to `target/ZDiscord-1.1.0.jar`. The build runs
tests automatically during the `test` phase.

## Running tests

```bash
mvn test
```

The test suite uses JUnit Jupiter 5.10.2. Tests cover:

- **ConfigManager** — schema migration, key merging, defaults
- **YamlStorage** — CRUD operations for links, stats, activity, follows
- **TicketModule** — category parsing from config
- **StatusEmbedBuilder** — embed structure and placeholder resolution
- **UpdateChecker** — semver comparison and pre-release handling
- **HeadUtil** — avatar URL resolution
- **ColorUtil** — Discord markdown conversion
- **PlaceholderUtil** — colour code stripping and placeholder expansion
- **TPSUtil** — server performance metrics

Tests use a `SyncPlatformAdapter` (runs tasks on the caller thread) and
`BukkitStub` (fake `ServerBridge` backend) so they don't need a running
server.

## Code style

- 4-space indentation, no tabs.
- Use the existing utility classes (`ColorUtil`, `HeadUtil`, `PlaceholderUtil`,
  `TPSUtil`, `EmbedUtil`, `StatusEmbedBuilder`) instead of duplicating logic.
- Run config/messages changes through `/setup` or a manual YAML check to
  make sure indentation is correct.
- Do not commit `target/`, `.jar` build outputs, secrets, tokens, or
  real Discord credentials.

## Project structure

```
src/main/java/dev/demonz/zdiscord/
  ZDiscord.java                # Plugin entry point
  platform/                    # Paper / Spigot / Folia scheduler adapters
  config/                      # ConfigManager, MessageManager
  storage/                     # StorageManager + YamlStorage / MySQLStorage
  discord/                     # JDA wiring (BotManager, slash/button listeners)
  discord/listeners/           # DiscordChatListener, ReconnectListener, etc.
  minecraft/                   # In-game bridge
    ChatBridge.java            # Shared MC→Discord forwarder
    commands/                  # /zdiscord, /discord, /sc
    listeners/                 # ChatListener, JoinQuitListener, ...
  modules/                     # Feature modules (status, ticket, ...)
  util/                        # ColorUtil, HeadUtil, ServerBridge, StatusEmbedBuilder, ...
```

## Adding a new module

1. Create a class in `modules/` implementing `init()`, `shutdown()`, and
   `reload()`.
2. Add a config toggle in `config.yml` (e.g. `my-module.enabled`).
3. Register the module in `ZDiscord.initModules()`.
4. Add a shutdown call in `ZDiscord.onDisable()`.
5. Add tests for any non-trivial logic.

## Reporting issues

- Use the **Bug report** issue template for broken behaviour.
- Use the **Feature request** template for ideas.
- For security vulnerabilities, see `SECURITY.md` — do not open a public
  issue.

## License

By contributing, you agree that your contributions will be licensed under
the Apache License 2.0.
