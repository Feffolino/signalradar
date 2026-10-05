# CurseForge upload checklist

Files in this folder: `summary.txt` (project summary), `description.md` (project description, paste in the
Markdown editor).

## 1. Before uploading
- [ ] Icon in the mod: `src/main/resources/signalradar_logo.png` (or `logoFile` in `neoforge.mods.toml`).
- [ ] Rebuild: `./gradlew build` -> `build/libs/signalradar-1.0.0.jar`.
- [ ] Screenshots / GIF: test in game and capture screenshots (radar display, blips, targets, upgrades).

## 2. Create the project (Minecraft > Mods)
| Field | Value |
|---|---|
| Name | Signal Radar |
| Slug | `signal-radar` |
| Summary | contents of `summary.txt` |
| Description | contents of `description.md` (Markdown) |
| Avatar | `curseforge/icon/radar.png` |
| Main category | Technology |
| Other categories | Adventure and RPG, Map and Information, Utility & QoL |
| License | MIT |
| Source URL | https://github.com/Feffolino/signalradar |
| Issues URL | https://github.com/Feffolino/signalradar/issues |

## 3. Upload the file
| Field | Value |
|---|---|
| File | `build/libs/signalradar-1.0.0.jar` |
| Display name | Signal Radar 1.0.0 (1.21.1 NeoForge) |
| Release type | Release |
| Game version | 1.21.1 |
| Mod loader | NeoForge |
| Environment | Client and Server (required on both) |
| Java | Java 21 |
| Changelog | contents of `CHANGELOG.md` |

## 4. Relations (file upload page, "Related projects")
| Project | Type |
|---|---|
| KubeJS | Optional dependency |
| Manhole Travel | Optional dependency |
| Lootr | Optional dependency |
| FTB Teams | Optional dependency |
| Just Enough Items | Optional dependency |

Do not mark any as Required: only NeoForge is needed.

## 5. After approval
- [ ] Tag the release on GitHub: `git tag v1.0.0 && git push origin v1.0.0`.