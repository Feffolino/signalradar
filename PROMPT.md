# Port di "Signal Radar" (signalradar) a Forge 1.20.1

## Obiettivo
Portare la mod `signalradar` (Signal Radar **1.0.0**) da **NeoForge 1.21.1** a **Forge 1.20.1** con le stesse funzionalità, in un progetto nuovo dentro **questa cartella**:
`C:\Users\stefy_zgbvz6k\Desktop\signalradar-1.20.1`

Serve per il modpack "Rat Lab", in fase di port a Forge 1.20.1 (istanza CurseForge `RatLab (1)`, Forge **47.4.23**). Su 1.20.1 si parte da **mondi nuovi**: nessun salvataggio 1.21.1 da migrare.

## Sorgente di riferimento (SOLA LETTURA)
`C:\Users\stefy_zgbvz6k\Desktop\signalradar` (git, HEAD `8db7f0b`). **Non modificarlo**: niente commit e niente push.

- 110 file Java in `src/main/java/it/ratlab/signalradar/`, divisi in:
  - `addon/` (con `detect/` e `menu/`), `api/`, `client/`, `command/`
  - `compat/` (ftbteams, jei, kubejs, lootr, manholes)
  - `data/`, `display/`, `icon/`, `item/`, `net/`, `progress/`, `recipe/`, `registry/`, `scan/`, `target/`, `test/`
- `src/main/resources/META-INF/accesstransformer.cfg`: rende pubblici `ModelPart.cubes` e `ModelPart.children`, che servono a disegnare le facce dei mob.
- **`SUMMARY.md` è la documentazione completa**:
  - item, tier, addon, target JSON e tag;
  - config, API ed eventi, KubeJS, JEI e test.
  
  Leggilo per primo. Poi leggi `README.md`, `CHANGELOG.md`, `CURSEFORGE.md` e `LICENSE` (MIT, "Stefano Manca").
- Test esistenti:
  - 32 JUnit (`./gradlew test`);
  - 77 GameTest in tre configurazioni (`runGameTestServer`, `runGameTestServerKubeJS`, `runGameTestServerCompat`, con gli script `tools/prepare-*.sh`);
  - datapack e template KubeJS in `src/test_datapack/`.
- La documentazione utente è nella **wiki GitHub** del repo `Feffolino/signalradar`. Non va modificata.

### Texture disegnate a mano dall'autore: non rigenerarle
- `textures/item/addon_*.png`, compresi `addon_custom` e `addon_custom_light`.
- **Non eseguire** `tools/make_placeholders.py` né altri generatori in `art/` o `tools/`.
- Copia gli asset così come sono da `src/main/resources`, **non** da `build/`.

## Dipendenze opzionali, già in `libs/` (versioni Forge 1.20.1 del pack)
```
kubejs-forge-2001.6.5-build.26.jar    rhino-forge-2001.2.3-build.10.jar
ftb-teams-forge-2001.3.2.jar          ftb-library-forge-2001.2.13.jar
architectury-9.2.14-forge.jar         lootr-forge-1.20-0.7.35.94.jar
jei-1.20.1-forge-15.59.0.212.jar
```
- Tutte `compileOnly`. Nel runtime locale dei test mettile solo se servono.
- La mod deve restare **senza dipendenze obbligatorie**, come l'originale. Le compat in `compat/` si attivano solo se la mod corrispondente è caricata. Gli addon `manhole`, `loot` e `team` compaiono solo con manholes, lootr e ftbteams.
- **Compat manholes:** la mod Manhole Travel è in porting a 1.20.1 in `C:\Users\stefy_zgbvz6k\Desktop\manholes-1.20.1`. Se lì c'è già `build/libs/manholes-1.7.3-1.20.1.jar`, copialo in `libs/` e usalo come `compileOnly`. Altrimenti, per non bloccarti, porta il resto e lascia la compat manholes per ultima: segnala che resta da fare e dimmi se vuoi aspettare il jar.
- **Lootr 1.20 (0.7.x)** ha API diverse da Lootr 1.21 (1.11.x). Verifica sul jar quali classi e metodi usa l'addon `loot`.

## Funzionalità da mantenere (tutto quello che descrive `SUMMARY.md`)
- **Radar FE**:
  - energia con capacità, ricezione e costo per scansione presi dal config;
  - tier 0-4 tramite `radar_module_1..4` e la ricetta di upgrade `signalradar:radar_upgrade`.
- **Display disegnato sull'item** con un BEWLR:
  - tenendo premuto il tasto destro il radar si alza;
  - sneak + destro apre il menu degli addon;
  - deve funzionare in entrambe le mani, con la rotazione per lo yaw e le frecce di altezza.
- **8 addon**: container, ore, biosign, structure, motion, più manhole, loot e team quando le mod sono presenti.
  - Ogni addon ha config propria: `enabled`, `minTier`, raggio, refresh, colore, costo energetico.
  - Gli slot degli addon dipendono dal tier.
- **Target narrativi** come datapack JSON `data/<ns>/signalradar/target/*.json`:
  - locator di tipo pos, structure, entity e block;
  - `requires_stage`;
  - quando un target viene trovato, scatta lo stage `signalradar_found_<path>`.
- **Tag**: `signalradar:scannable_structures`, `container_targets`, `biosign`, `trackable`. Un tag structure vuoto significa "tutte le strutture della dimensione", con cache per cella di regione.
- **Ricette di default**: `signalradar:default/*`, dietro lo startup config `recipes.enableDefaultRecipes` e la condizione `signalradar:default_recipes_enabled`. Quelle di compat si attivano solo se la mod è caricata.
- **API Java**: `SignalRadarAPI` più gli eventi `RadarScanEvent`, `RadarTargetFoundEvent`, `RadarUpgradedEvent`, `RadarAddonChangedEvent`.
- **KubeJS**:
  - eventi `SignalRadarEvents.registerAddons/scan/targetFound/upgraded/addonChanged`;
  - binding `SignalRadar`.
- **JEI**: pagine info e ricette.
- **Comandi**, **suoni** e **icone dei volti dei mob**. Per i volti servono l'access transformer o un'alternativa: su Forge 1.20.1 vale lo stesso file `META-INF/accesstransformer.cfg`, ma controlla che i nomi dei campi siano mappati correttamente.
- **Config**:
  - server: `energy.*`, `scan.*` (`rangeByTier`, `fuzzByTier`, `maxBlockChecksPerScan`…), `addons.*`;
  - client: `motionBeep`, `screenBrightness`, `showHeightArrows`.

## Stack di destinazione
- Minecraft **1.20.1**, Forge **47.4.23** (requisito `[47,)`), Java **17** (toolchain).
- Build con **ForgeGradle 6** dal MDK Forge 1.20.1, oppure con **ModDevGradle `legacyforge`**. Scegline uno e motiva la scelta.
- Mappings: official + Parchment 1.20.1.
- `META-INF/mods.toml`:
  - modId `signalradar`, display name "Signal Radar", versione **1.0.0-1.20.1**, licenza MIT;
  - dipendenze opzionali kubejs, ftbteams, lootr, jei, manholes (`mandatory=false`, side BOTH, ordering AFTER).

## Differenze NeoForge 1.21.1 → Forge 1.20.1 da gestire
| 1.21.1 (originale) | 1.20.1 (da fare) |
|---|---|
| Data Components sull'item radar (energia, tier, addon installati, stato) | tag NBT (`getOrCreateTag`), con helper di lettura e scrittura centralizzati |
| Capability energetica dell'item con `RegisterCapabilitiesEvent` | `Item#initCapabilities` → `ICapabilityProvider` con `ForgeCapabilities.ENERGY` |
| payload `CustomPacketPayload` + `StreamCodec` (`net/`) | `SimpleChannel` con encode/decode su `FriendlyByteBuf` |
| `ModConfig.Type.STARTUP` (`recipes.enableDefaultRecipes`) | su Forge non esiste: leggi un toml a mano prima della registrazione, oppure usa COMMON e documenta il compromesso |
| condizione di ricetta `signalradar:default_recipes_enabled`, condizioni `neoforge:mod_loaded` | `ICondition` + `IConditionSerializer` registrato con `CraftingHelper.register`; `forge:mod_loaded` |
| ricette custom (`recipe/`) con `MapCodec` / `StreamCodec` | `RecipeSerializer` con `fromJson` / `fromNetwork` / `toNetwork` |
| `data/*/recipe/`, `loot_table/`, `tags/item`, `tags/block`, `tags/worldgen/structure` | `recipes/`, `loot_tables/`, `tags/items`, `tags/blocks`, `tags/worldgen/structure` (verifica il percorso giusto su 1.20.1) |
| tag `c:` | `forge:` (o entrambi come voci opzionali) |
| BEWLR via `IClientItemExtensions` + `RegisterClientExtensionsEvent` | `Item#initializeClient(Consumer<IClientItemExtensions>)` con `getCustomRenderer()` |
| posa in mano o alzata (`ArmPose` / `IClientItemExtensions#getArmPose`) | stessa API Forge. Se usa un'enum extension, `HumanoidModel.ArmPose.create(...)` |
| `RenderGuiLayerEvent` / layer HUD | `RenderGuiOverlayEvent` / `RegisterGuiOverlaysEvent` |
| ricerca strutture (`StructureManager`, holder lookup) | API 1.20.1: `Registry`/`Holder` di `Registries.STRUCTURE`, `ChunkGenerator#findNearestMapStructure` |
| SavedData / attachment del giocatore | 1.20.1: `SavedData` con `load(CompoundTag)`, oppure capability del player / `getPersistentData()` |
| bus eventi NeoForge | `MinecraftForge.EVENT_BUS` / `FMLJavaModLoadingContext.get().getModEventBus()` |
| KubeJS 7 (2101): plugin, eventi, binding | KubeJS 6 (2001): `KubeJSPlugin#registerEvents` con `EventGroup`, `registerBindings(BindingsEvent)`, `kubejs.plugins.txt`. Verifica sul jar |
| JEI 19.x | JEI 15.x (`IModPlugin`, `IRecipeCategory`, info pages): API simile, controlla le firme |
| FTB Teams 2101 / Lootr 1.11 | FTB Teams 2001 / Lootr 0.7: verifica le API sui jar in `libs/` |
| GameTest e JUnit NeoForge | GameTest Forge (`@GameTestHolder`, `runGameTestServer`), JUnit invariato |

## Build
- Sul PC **non ci sono JDK 17 né 21**: in `C:\Program Files\Java` ci sono solo jdk-22, jdk-23 e jdk-25. Usa il toolchain Gradle con il resolver foojay per Java 17.
- ForgeGradle 6 richiede **Gradle 8.x**, non 9. Avvialo con `JAVA_HOME="C:\Program Files\Java\jdk-22"`. Se non parte, chiedi invece di installare JDK di sistema.
- Risultato atteso: `build/libs/signalradar-1.0.0-1.20.1.jar`, senza jar di altre mod dentro.

## Regole
- **Non modificare** `Desktop/signalradar`, i jar in `libs/` o il modpack. Il pack è pubblicato: niente patch ai jar di altre mod.
- **Non copiare il jar** in `C:\Users\stefy_zgbvz6k\curseforge\minecraft\Instances\RatLab (1)\mods` mentre Minecraft è aperto: un jar sostituito a caldo fa crashare il gioco. Controlla che non ci sia un processo `java`/`javaw` con `RatLab (1)` nella riga di comando e **chiedimi conferma** prima di copiarlo.
- Nella nuova cartella:
  - `git init`;
  - `.gitignore` per Gradle, IDE, `run*/` e `libs/`;
  - commit a ogni passo funzionante;
  - niente push e niente repository remoti.
- Copia e adatta `README.md`, `SUMMARY.md`, `CHANGELOG.md`, `CURSEFORGE.md` e `LICENSE`, indicando come target Forge 1.20.1 e le versioni delle dipendenze opzionali. Nel CHANGELOG aggiungi la voce del port.

## Ordine di lavoro
1. Setup del progetto, build vuota, access transformer.
2. Registry: item radar, moduli 1-4, item addon, suoni, ricette di default con la condizione.
3. Energia (capability FE) e dati del radar in NBT.
4. Networking con `SimpleChannel`.
5. Scansione: `ScanHandler`/`RadarScanner`, i 5 addon base, tag, cache delle strutture.
6. Target JSON e progress/stage.
7. Client: BEWLR del display, posa alzata, menu addon, frecce, suoni, icone dei volti dei mob.
8. Compat: JEI, KubeJS, FTB Teams, Lootr e per ultima manholes.
9. Config, comandi, test (JUnit + GameTest).
10. Documentazione.

Fermati a chiedere solo per le decisioni che spettano all'autore.

## Verifica (prima di dire che è finito)
1. `gradlew build` termina senza errori.
2. I JUnit passano.
3. `gradlew runGameTestServer`: i GameTest portati passano. Elenca quelli tolti o rimandati e spiega perché.
4. Se possibile, `runClient`:
   - il radar si carica di FE;
   - il display si vede in mano e si alza;
   - una scansione trova container, ore e strutture;
   - il menu addon si apre;
   - un target JSON di prova sblocca lo stage.
5. In `SUMMARY.md` scrivi cosa hai **verificato davvero** e cosa resta da provare nel modpack. Segna come "da testare in gioco" le parti client che non hai potuto provare: display, posa, menu, suoni, volti dei mob, compatibilità con shader ed Embeddium.
