# GymPad

A minimalist, offline gym workout tracker and food diary for Android. No accounts, no cloud, no social feed: just your sets and meals, fast enough to log with sweaty hands between sets.

Available in English and Polish.

| Logging | Variants | History | Polski |
|---|---|---|---|
| ![Logged sets](docs/screenshots/logging.png) | ![Editing an exercise's variants](docs/screenshots/variants.png) | ![Year history](docs/screenshots/history.png) | ![The app in Polish, with ghost values from the last workout](docs/screenshots/polish.png) |

## Features

- **One-tap logging.** Each set shows what you lifted last time, by set number, as a gray ghost value, so straight sets and pyramids both work. If you repeat it, one tap on ✓ logs it.
- **±2.5 kg steppers** next to the weight, so small jumps don't need the keyboard.
- **Named variants.** Bench taken? Swap to the dumbbell or machine variant right on the card. Each variant keeps its own history and PR.
- **Last time and PR** on every card, so you can see at a glance how you compare.
- **Date ribbon and year history.** Green means complete, yellow means incomplete, gray means rest. Past days are read-only, so they can't be edited by accident.
- **Five nameable routines**, with exercises, variants and set counts you can edit.
- **JSON backup and restore** through the system file picker.

### Food

- **Five meals a day:** breakfast, brunch, lunch, snacks and supper, with calories, protein, carbs and fat for each meal and the whole day.
- **Daily goals** for calories and each macro, all optional. Days within 10% of the calorie goal turn green on the ribbon.
- **Scan a barcode** with the camera, or search by name in English or Polish. About 35,000 products sold in Poland and 90 basic foods like chicken, rice and fruit are built in and work offline.
- **Your own foods.** Anything missing, you enter once from the label; a scanned barcode is remembered for next time.
- **One tap to repeat.** Recent foods remember the amount you last ate, and an empty meal offers to copy the same meal from the day before.
- **Any day is editable,** including past days you forgot to log and future days you plan ahead.
- **Meal photos with AI.** Snap your plate, optionally add a note like "fried in olive oil", and Google's Gemini estimates each food's weight, calories and macros. You check the list, fix any amount, and add it to the meal in one tap. It uses your own free Gemini API key; the app walks you through getting one.

## Your data

Everything stays on your device. There is no server and no account. The app asks for two permissions:

- **Camera,** for reading barcodes and taking meal photos. Barcodes are read on the phone.
- **Internet,** only for meal photos. A photo and your note go straight from your phone to Google's Gemini API with your own key, and nowhere else. On Gemini's free tier, Google may use them to improve its products. Everything else works offline.

The Gemini key is stored on the device and is never included in backups.

Switching language never touches your training data. History, PRs and ghost values follow an exercise's internal ID rather than its name, so changing language writes nothing to the database:

- **Built-in exercises** are stored under one canonical name and translated only for display.
- **Names you type** are your own data and are never translated.
- **Typing a built-in's name in either language** reaches the same exercise, so one lift can't split into two histories.
- **Backups** are language-independent: export in one language, restore in the other.

## Building

You need Java 17 or newer to run Gradle, plus the Android SDK for Android builds. Point Gradle at the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/sdk`. Gradle downloads the JDK 21 used for compiling on its own.

Preview the app in a desktop window, with changes reloading live:

```bash
./gradlew :shared:hotRunJvm --auto --mainClass com.gympad.MainKt
```

Run the tests. They drive the real UI against a real database, and write screenshots to `shared/build/screens/`:

```bash
./gradlew :shared:jvmTest
```

Build an installable debug APK, which needs no signing setup:

```bash
./gradlew :app:assembleDebug
```

### Release builds

Create a signing key. It isn't committed to the repository, so keep a backup: Android only installs updates signed with the same key.

```bash
mkdir -p keystore
keytool -genkeypair -keystore keystore/gympad-release.jks -alias gympad -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=GymPad"
```

Then create `keystore/keystore.properties`:

```properties
storeFile=keystore/gympad-release.jks
storePassword=your-password
keyAlias=gympad
keyPassword=your-password
```

Build the signed APK into `dist/GymPad.apk`:

```bash
./gradlew apk
```

### Food catalog

The product catalog ships with the app, built from public data by [`tools/catalog/build_catalog.py`](tools/catalog/build_catalog.py). Rebuild it to pick up new products; the instructions are at the top of the script. Basic foods are listed in [`basic_foods.tsv`](tools/catalog/basic_foods.tsv), one per line with English and Polish names.

## Project layout

```
app/       Android entry point: Application, Activity, launcher icon
shared/
  src/commonMain/   Everything shared by Android and desktop
    data/           Room database, repositories, food catalog, backup format
    domain/         Pure logic: day status, ghost values, PRs, built-in names, nutrition
    ui/             Compose screens, theme, icons
    ui/strings/     All UI text, one implementation per language
  src/androidMain/  Android file picker, back handling, database setup
  src/jvmMain/      Desktop window used for preview
  src/jvmTest/      Unit, migration and UI flow tests
  schemas/          Room schema history, needed for database migrations
tools/catalog/      Builds the bundled food catalog from Open Food Facts and USDA data
```

Built with Kotlin, Compose Multiplatform, Room and kotlinx.serialization.

### Adding a language

Add an entry to `AppLanguage`, then implement `AppStrings` for it. Any text you miss is a compile error. Add translations of the built-in exercise names to `BuiltInNames`; the test suite fails if one is missing.

## License

[MIT](LICENSE) for the code. The bundled food catalog is under the [ODbL](https://opendatacommons.org/licenses/odbl/1-0/), as it contains Open Food Facts data. Font, icon, data and library credits are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
