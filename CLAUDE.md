# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

This repo holds the actual novel-source extension implementations consumed by the main `IReader` app (sibling repo `../IReader`). `IReader` itself only defines/consumes the extension API surface (`source-api`) and a JS runtime (`source-runtime-js`) — it does not contain any source scraping logic. All per-site scraping code (FreeWebNovel, RoyalRoad, ScribbleHub, ReadNovelFull, Ranobes, ~85 others) lives here under `sources/`.

Originally forked/inspired by Tachiyomi Extensions; sources extend `tachiyomix`-annotated base classes (`ParsedHttpSource`, `SourceFactory`) defined in the main `IReader` repo's `source-api`.

## Module Structure

```
sources/<lang>/<sourcename>/        One module per source (e.g. sources/en/freewebnovel)
  build.gradle.kts                  Extension(...) DSL: name, versionCode, libVersion, lang, nsfw, icon
  main/src/ireader/<sourcename>/    Kotlin source implementation
  main/assets/icon.png
sources/multisrc/                   Shared base classes for families of similar sites (Madara, etc.)
sources-v5-batch/                   Batch-converted plugins (V5 format), included separately in settings.gradle.kts
common/                             Shared utilities: RateLimiter, HtmlCleaner, DateParser, StatusParser, ErrorHandler, ImageUrlHelper, SelectorConstants
annotations/                        Custom annotations: @Extension, @AutoSourceId, @GenerateFilters, @GenerateCommands, @GenerateTests, @TestFixture, @UrlValidation, @SelectorSnapshot
compiler/                           KSP processors that generate boilerplate from the annotations above (incl. JsExtensionProcessor for JS bundle generation)
js-sources/                         Kotlin/JS module that bundles ALL sources into a single self-contained sources-bundle.js (see JS_INTEGRATION.md)
source-test-server/                 Local Ktor server (localhost:8080) for testing compiled sources without the full app
buildSrc/                           Gradle convention plugins, the `Extension(...)` build DSL, `register()`
scripts/                            Python scaffolding scripts (add-source.py, create-empty-source.py, js-to-kotlin-converter.py)
```

`settings.gradle.kts` auto-includes every `sources/<lang>/<name>` directory with a `build.gradle.kts` as `:extensions:individual:<lang>:<name>` (and `sources/multisrc/*` as `:extensions:multisrc:<name>`).

## Two Source Base Classes

- **`ParsedHttpSource`** — lower-level, full control. Override `chaptersSelector()`, `chapterFromElement()`, `detailParse()`, `pageContentParse()`, request builders. Used when a site needs custom logic per listing type (e.g. FreeWebNovel's AJAX chapter pagination, Ranobes' embedded-JSON chapter data).
- **`SourceFactory`** — declarative, selector-only. Provide `exploreFetchers: List<BaseExploreFetcher>` (each with `selector`/`nameSelector`/`coverSelector`/`linkSelector`/`endpoint`), `detailFetcher: Detail`, `chapterFetcher: Chapters`, `contentFetcher: Content`. Used for straightforward sites (RoyalRoad, ScribbleHub, ReadNovelFull).

Both are constructed with `deps: Dependencies` and annotated `@Extension` for KSP pickup.

## Build & Test Commands

```bash
# Build one source (fast path — always prefer this over building everything)
./gradlew :extensions:individual:en:freewebnovel:assembleDebug

# List all available sources + their build commands
./gradlew listSources

# Local test server (visual browser + JSON API against compiled sources)
./gradlew testServer               # quick start, uses cached APKs
./gradlew buildAndTest             # rebuild all sources, then start server
# Server: http://localhost:8080  (/ = API tester, /browse = visual browser,
#   /api/sources/{id}/search?q=..., /api/sources/{id}/details|chapters|content, /api/sources/{id}/test)

# JS bundle build (for the JS distribution path — see caveat below)
./gradlew :extensions:individual:en:freewebnovel:kspEnReleaseKotlin
./gradlew :js-sources:jsBrowserProductionWebpack :js-sources:createSourceIndex
# Output: js-sources/build/js-dist/{sources-bundle.js, sources-bundle.js.map, index.json}
```

After modifying a source: recompile it, then restart `source-test-server` — it discovers APKs under `sources/*/build/intermediates/apk/*/debug/*.apk` and dex2jar-converts them (cached in `source-test-server/jar-cache/`, auto-invalidated on APK change).

## Verified: how to sideload a local fix into the desktop app (no publishing needed)

FreeWebNovel, RoyalRoad, ScribbleHub, ReadNovelFull, and Ranobes are installed on desktop as **traditional compiled `.apk` extensions**, not JS plugins — confirmed by inspecting `~/Library/Caches/IReader/Extensions/<pkgName>/` on macOS (`.jar` and `.apk` files present) and reading the loader code:

- Install path: `DesktopCatalogInstaller.kt` (`../IReader/data/.../catalog/impl/`) picks the JAR/APK branch whenever `catalog.pkgUrl` does NOT end in `.js` — true for all five of these sources.
- Actual runtime loading: `DesktopCatalogLoader.kt:110-114,149-155` reads `ExtensionDir/<pkgName>/<pkgName>.apk` directly (via `ApkFile(file)`) — the `.jar` sitting alongside is a separate artifact (used by `source-test-server`'s dex2jar pipeline, not by the main app's loader) and can be ignored for this workflow.
- `ExtensionDir` resolves per-OS from `source-api/.../core/storage/CacheDir.kt`: macOS → `~/Library/Caches/IReader/Extensions/`, Linux → `~/.cache/IReader/Extensions/`, Windows → `%AppData%/IReader/cache/Extensions/`.

**To test a fix without publishing:**
1. Edit the Kotlin source under `sources/en/<name>/main/src/ireader/<name>/`.
2. Build just that module's debug APK: `./gradlew :extensions:individual:en:<name>:assembleDebug`.
3. Find the built APK (README: "scans `sources/*/build/intermediates/apk/*/debug/*.apk`") — e.g. `sources/en/freewebnovel/build/intermediates/apk/en/debug/*.apk` (exact variant dir may vary; `find sources/en/<name>/build -name '*.apk'` to locate it).
4. Copy it over the installed one: `cp <built.apk> ~/Library/Caches/IReader/Extensions/ireader.<name>.en/ireader.<name>.en.apk` (confirm exact `pkgName` matches the existing directory name first).
5. Restart the IReader desktop app and retest.

The `js-sources` module / `JS_INTEGRATION.md` bundle format and `~/.ireader/js-plugins/` sideload path are a **separate, unrelated** mechanism for actual JS-based sources (`pkgUrl` ending in `.js`) — not applicable to the 5 sources above.

## Known Issues Spotted While Reading (2026-07-25)

- **`Ranobes.kt`**: `baseUrl = "https://ranobes.top"` (line 40), but `getChapterList()` hardcodes `https://ranobes.net/chapters/...` (lines 302, 319) — inconsistent domain, likely stale after a site domain migration. Worth checking whether `ranobes.top` still serves the same content/markup as `.net` before "fixing" selectors here.
- Several sources wrap parsing in broad `try/catch { emptyList() / MangasPageInfo(emptyList(), false) }` (e.g. `ReadNovelFull.kt`) — this silently swallows real errors, making "0 results" look identical to "network/parse failure" to both the extension code and the main app's `isLikelyBrokenSource` heuristic (`ExploreState.kt` in `../IReader`), which flags ANY zero-result page as "broken" regardless of cause. When fixing, prefer letting real failures surface rather than catching-and-returning-empty, so the two failure modes are distinguishable in logs.

## Sources Being Investigated

| Source | Base URL | Base class | Notes |
|---|---|---|---|
| FreeWebNovel | freewebnovel.com | ParsedHttpSource | AJAX chapter pagination (`?ajax=chapters&page=N`), search via `submitForm` |
| RoyalRoad | www.royalroad.com | SourceFactory | Straightforward selector-driven, 11 explore fetchers (latest/trending/popular/etc.) |
| ScribbleHub | www.scribblehub.com | SourceFactory | Chapters fetched via WP AJAX (`wp-admin/admin-ajax.php`, action `wi_getreleases_pagination`) |
| ReadNovelFull | readnovelfull.com | SourceFactory | Uses a headless "browser" fetch (`deps.httpClients.browser.fetch`) for JS-heavy detail/content pages, falls back to plain `client.get` |
| Ranobes | ranobes.top (see issue above) | ParsedHttpSource | Chapters come from an embedded `window.__DATA__` JSON blob, not plain HTML selectors |
