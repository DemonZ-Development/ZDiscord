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

## Code style

- 4-space indentation, no tabs.
- Use the existing utility classes (`ColorUtil`, `HeadUtil`, `PlaceholderUtil`,
  `TPSUtil`, `EmbedUtil`, `StatusEmbedBuilder`) instead of duplicating logic.
- Run config/messages changes through `/setup` or a manual YAML check to
  make sure indentation is correct.
- Do not commit `target/`, `.jar` build outputs, secrets, tokens, or
  real Discord credentials.

## Reporting issues

- Use the **Bug report** issue template for broken behaviour.
- Use the **Feature request** template for ideas.
- For security vulnerabilities, see `SECURITY.md` — do not open a public
  issue.

## License

By contributing, you agree that your contributions will be licensed under
the Apache License 2.0.
