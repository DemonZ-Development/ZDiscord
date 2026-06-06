# Marketplace listings

These files are ready-to-paste drafts for the four plugin distribution platforms ZDiscord is published to.

| File | Platform | URL |
|---|---|---|
| `modrinth.md` | Modrinth | https://modrinth.com/plugin/create |
| `curseforge.md` | CurseForge | https://www.curseforge.com/minecraft/bukkit-plugins/zdiscord |
| `spigot.md` | SpigotMC (Premium) | https://www.spigotmc.org/resources/zdiscord |
| `hangar.md` | PaperMC Hangar | https://hangar.papermc.io/ |

## How to use

1. Open the relevant file.
2. Copy the **Long description** (the big block at the bottom) into the platform's description editor. Each file is already in Markdown — both Modrinth and Hangar render Markdown; CurseForge and SpigotMC accept it too.
3. Copy the **Short description** / **Tag line** into the dedicated short-description field.
4. Use the **Categories / Tags** list when filling in the form's checkbox grid.
5. Use the **Versions** and **License** blocks when creating the first version of the project.
6. The `Links` / `Source` block provides the URLs to put in the platform's "homepage", "source", and "wiki" fields.

## What to update per release

When a new version ships, edit only the following lines in each file:

- `## Versions` and `## Game versions` — append the new Minecraft versions covered
- The JAR filename in the installation step (`ZDiscord-X.Y.Z.jar`)
- The short description, if the headline changes

The platform's version page (after the initial creation) only needs the new file, version number, and supported game versions — the long description stays the same.

## Why these are in the repo

Keeping the listings in version control means:

- Changes are reviewable in PRs
- Anyone with push access can update them without needing an account on the specific platform
- The description stays in sync with the README and CHANGELOG
- The history of marketing changes is auditable
