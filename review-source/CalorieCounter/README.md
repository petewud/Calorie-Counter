# Calorie Counter 2.0 — complete Android project

## Phone-only GitHub builds
Upload `CalorieCounter.zip` to the repository root. Place the separately supplied workflow at `.github/workflows/build-apk.yml`. This is a complete project: do not run the old Python updates against it. The permanent workflow extracts the ZIP, runs calculation tests and builds a debug APK. Future normal updates require replacing only the ZIP under the same name. Keep the outer `CalorieCounter/` directory inside the archive.

Delete obsolete `update_app.py`, `update_nutrition.py`, `fix_search_json.py` and `apply_update.py` after switching to the new workflow. Optional repository README and GitHub folders may remain.

## Features
- Recipes with any number of ingredients; add, edit, remove and adjust amounts. Recipe draft uses saved-state restoration and is retained while scanning, searching and navigating. Save recipes explicitly: drafts are not a substitute for permanent storage or backups.
- Saved recipe editing. Logging uses a nutrition snapshot: later recipe edits do not rewrite past diary entries.
- Expandable diary rows; collapsed shows only name and kcal; expanded shows full nutrients and pencil/bin controls. Edit any entry and its amount. Deletions request confirmation.
- No Meal field. Grams, ml or custom slices/servings; fractional quantities supported. Database serving weights are used where available. Custom serving conversions can be remembered by food identity. Never equate g and ml without an appropriate label basis.
- First-run adult profile: equation sex coefficient, age, cm, kg, activity. Estimated maintenance via Mifflin–St Jeor; optional manual calorie target. Nutrient references are general adult label references, not individually calculated RDAs. Targets are editable. Fat, saturates and salt are limits. Total sugars reference is not a free-sugars allowance. Fibre goal 30 g.
- Dashboard progress and percentages, unknown/partial values clearly labelled. History with previous/next day and native calendar. Historical pages use current targets.
- UK preference defaults on and lives in Settings; reorders loaded search results only. Brand filter, full product lookup on selection and load-more results.
- Native home-screen calorie widget. Add using the Android launcher widget picker. Updates after entry changes, profile saves, app opens and periodic Android scheduling (about 30 minutes, may be delayed). Date displayed so stale content is visible; tap opens the app. Does not require continuous background service.
- JSON backup export/import from Settings. Import merges as new records and repeated import duplicates items. Standalone portion presets are not exported; conversions in recipes/entries are.

## Existing data and installing
Same application ID: com.example.caloriecounter. Keeps existing `calorie_counter.db`, adds nullable nutrient/snapshot columns transactionally via SQLiteOpenHelper version 3, and reads legacy Room tables. No destructive migration. Old recorded macro totals are reconstructed into editable entry snapshots. Unknown historic nutrients remain unknown. New profile is prompted once; previous calorie target is carried over if present. No automatic adjustment of profile targets after later weight changes unless the user chooses the estimate.

Try updating over the existing APK WITHOUT uninstalling. Existing data is retained only when Android accepts an in-place upgrade. Debug signatures from earlier builds may differ. This workflow caches its debug keystore for future runs, but cannot recover the previous build's signing key. If Android rejects installation as a signature conflict, stop: do not uninstall if data matters. Obtain the old signing key or plan backup/re-entry first. Cache eviction or separate branches can still affect debug signing. A private, stable release keystore in GitHub Secrets is recommended before long-term distribution. Do not publish keystores in the repository.

## Safety / privacy
For adults 18+; not a medical prescription or suitable for pregnancy, breastfeeding, growing children or clinical nutrition. Profile affects estimated calories only. Label reference values are population guides. No weight-loss deficit is automatically prescribed. Unknown nutrients are blank, not zero. Food lookup sends barcode/search terms to Open Food Facts; profile and diary stay on device except user-created exports and possible Android OS backups. Open Food Facts community data can be incomplete or out of date. Check product packaging, preparation state, serving weight and nutrition basis.

Sources: Mifflin–St Jeor equation (Medscape calculator); adult reference intakes (UK food labels); UK SACN fibre recommendation. Open Food Facts data under ODbL. Warburtons Super Toastie/Thick Toastie example: manufacturer average slice 57.5 g; always verify the actual pack.

## Build checks
The project includes JUnit calculation tests for partial tins, slice conversion, multi-ingredient recipes, missing values and the energy equation. GitHub runs these before building the APK. The generating assistant did not have an Android SDK/JVM to compile or device-test this project; successful Actions build and device testing remain required.

## Local development
Open in Android Studio with JDK 17. AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.20 / API 35. The CI workflow supplies Gradle (no binary wrapper is embedded in this source archive).
