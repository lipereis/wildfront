# Modrinth listing for Wildfront

Everything needed to create the Modrinth project page by copy and paste. The page text is in
[description.md](description.md); pictures are in [gallery/](gallery) and [icon-512.png](icon-512.png).

## Project

| Field | Value |
|---|---|
| Project type | Mod |
| Name | `Wildfront` |
| URL (slug) | `wildfront` — free when checked on 2026-10-10 |
| Visibility | Public (it stays a draft until you submit it for review) |
| Summary | `Rare forgotten caches that appear while you explore, and abandoned camps in new terrain. Adds to vanilla survival without replacing it. Configurable and server-side.` |
| Icon | `icon-512.png` |
| Description | paste the content of `description.md` |

The summary is 165 characters. Modrinth limits its length; shorten it if the form refuses it.

## Tags

| Field | Value |
|---|---|
| Categories | `adventure`, `worldgen` |
| Additional category | `game-mechanics` |
| Client side | Optional |
| Server side | Required |

In newer versions of the form the two "side" fields are a single "environment" choice; pick the
one that says the mod works on the server alone and is optional on the client.

## License and links

| Field | Value |
|---|---|
| License | MIT |
| Source | `https://github.com/lipereis/wildfront` |
| Issues | `https://github.com/lipereis/wildfront/issues` |
| Wiki | leave empty |
| Discord | leave empty |

## Gallery

Upload in this order; make the first one the featured image.

| File | Title | Description |
|---|---|---|
| `gallery/1-forgotten-cache.png` | Forgotten cache | A forgotten cache marked by its column of particles. |
| `gallery/2-abandoned-camp-forest.png` | Abandoned camp in a birch forest | A naturally generated camp: lean-to, bedroll and what the traveller left behind. |
| `gallery/3-abandoned-camp-plains.png` | Abandoned camp on the plains | Fire pit, log seats, crafting table and the supply chest. |

## Version

| Field | Value |
|---|---|
| File | `wildfront-0.1.1+mc26.3.jar` from https://github.com/lipereis/wildfront/releases/latest (not the `-sources` jar) |
| Version number | `0.1.1+mc26.3` |
| Version title | `Wildfront 0.1.1` |
| Release channel | Release (or Beta, if you would rather signal that it is new) |
| Loaders | Fabric |
| Game versions | 26.3 |
| Dependencies | Fabric API — required |
| Changelog | paste the `0.1.1` and `0.1.0` sections of `CHANGELOG.md`, since this is the first version on Modrinth |

Modrinth usually fills in loader, game version and version number from the jar; check them
against this table.

## Steps

1. Sign in at https://modrinth.com and choose **Create a project** from the dashboard.
2. Enter name, URL and summary from the first table.
3. Fill in description, icon, tags, license and links.
4. Upload the three gallery pictures.
5. Create the version and upload the jar.
6. Modrinth lists what is still missing next to the submit button; when nothing is, **submit for
   review**. A moderator has to approve the project before it becomes visible, which can take
   from hours to a few days.

## After it is approved

- Add the Modrinth link to `README.md` and to `contact.homepage` in `fabric.mod.json`.
- Uploading future versions can be automated in the release workflow with a Modrinth API token
  stored as a repository secret. That is not set up yet because it cannot be tested before the
  project exists.
