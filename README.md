# Fitness Tracking System

Offline-first desktop fitness tracker built with **Java 17**, **JavaFX**, and **SQLite**. Track workouts, nutrition, body metrics, and progress — most data stays on your machine; USDA FoodData Central is used only when searching new ingredients.

---

## For users

### Requirements

- **JDK 17+**
- **Maven 3.8+**

### Get started

```bash
cd Java_project
mvn javafx:run
```

On first launch the app:

1. Creates `data/fittrack.db`
2. Applies database migrations
3. Seeds the bundled exercise catalog ([free-exercise-db](https://github.com/yuhonas/free-exercise-db))

Then **Register** a new account (username, password, name, date of birth, sex, height, starting weight) or **Log in** if you already have one.

### Optional setup (USDA ingredient search)

Ingredient search works offline against your local cache. To fetch new foods from USDA:

1. Get a free API key at [fdc.nal.usda.gov/api-key-signup.html](https://fdc.nal.usda.gov/api-key-signup.html)
2. Either:
   - Paste it under **Settings → USDA API**, or
   - Copy config and set the key:

```bash
cp src/main/resources/config.properties config.local.properties
# edit usda.api.key=YOUR_KEY
```

Or set environment variables:

| Variable | Purpose |
|---|---|
| `USDA_API_KEY` | FoodData Central API key |
| `FITTRACK_DB_PATH` | Custom SQLite database path (default `data/fittrack.db`) |

A limited `DEMO_KEY` is bundled for light testing and may be rate-limited.

### What you can do

| Area | Features |
|---|---|
| **Home** | Today’s remaining calories/protein, recent weight log, recent personal records, active plan count |
| **Profile** | Edit name/DOB/sex/height, preferred 1RM formula, units; log weight; see BMI, BMR, TDEE and weight history |
| **Exercises** | Browse/search the exercise library; filter by muscle; add or edit custom exercises (name, primary/secondary muscles, equipment, rest, instructions) |
| **Plans** | Create workout plans; add exercises with sets, rep ranges, target weight, rest, notes; see planned muscle-set volume; start a live workout; archive or delete plans |
| **Live workout** | Follow the plan exercise-by-exercise; log weight/reps/RPE; rest timer; estimated 1RM; personal-record alerts; skip exercise; finish session |
| **Nutrition** | Search/cache ingredients (USDA + local); build dishes with portions; meal diary; set calorie/macro goals and see today’s adherence |
| **Progress** | Weight & BMI history as logs; body weight goal; weekly completed sets per muscle; personal records; per-exercise 1RM history; 7-day nutrition adherence |
| **Settings** | Save USDA API key; export/restore full SQLite backup; export/import JSON user snapshot; recent PR notifications |

### Typical first session

1. Register and open **Profile** — confirm metrics look right; log a weight if needed.
2. **Exercises** — search for lifts you use (or leave the catalog as-is).
3. **Plans** — create a plan, add a few exercises, save, then **Start workout** and log sets.
4. **Nutrition** — set an API key (optional), search a food, **Cache macros**, build a dish or log a meal, then set **Goals**.
5. **Progress** / **Home** — check logs and adherence.
6. **Settings** — export a SQLite backup so you can restore later.

### Notes

- Age is always calculated from date of birth.
- Progress is shown as **tables and text logs**, not charts.
- Plan edits do not rewrite past workout sessions.
- Passwords are stored hashed (PBKDF2), never in plain text.

---

## For developers

### Stack

| Layer | Technology |
|---|---|
| Language | Java 17 (`--release 17`) |
| UI | JavaFX 21 (programmatic — no FXML) |
| Database | SQLite via `sqlite-jdbc` |
| JSON | Jackson (`jackson-databind`) |
| HTTP | `java.net.http.HttpClient` (USDA) |
| Build | Maven + `javafx-maven-plugin` |

Main class: `com.fittrack.app.FitTrackApp`

### Project layout

```
Java_project/
├── pom.xml
├── README.md
├── config.local.properties          # optional local overrides (not committed)
├── data/
│   └── fittrack.db                  # created at runtime
└── src/main/
    ├── java/com/fittrack/
    │   ├── app/                     # bootstrap & composition root
    │   ├── api/nutrition/           # USDA HTTP client
    │   ├── concurrent/              # PR notification buffer (Vector)
    │   ├── domain/                  # entities & pure domain logic
    │   │   ├── common/              # enums, macros, exceptions, BaseEntity
    │   │   ├── user/                # User, Profile, WeightEntry, BodyGoal
    │   │   ├── exercise/            # Exercise, catalog loader, muscle volume
    │   │   ├── workout/             # WorkoutPlan, PlanItem, Session, LoggedSet
    │   │   ├── nutrition/           # Ingredient, Dish, MealLog, NutritionGoal, …
    │   │   └── progress/            # 1RM calculators, PersonalRecord
    │   ├── persistence/
    │   │   ├── migration/           # numbered SQL migrations
    │   │   └── sqlite/              # Database + DAOs
    │   ├── service/                 # application services / use cases
    │   ├── export/                  # SQLite & JSON backup
    │   ├── util/                    # hashing, password strength, body metrics
    │   └── ui/                      # JavaFX views by feature
    │       ├── auth/
    │       ├── home/
    │       ├── profile/
    │       ├── library/
    │       ├── plans/
    │       ├── live/
    │       ├── nutrition/
    │       ├── progress/
    │       ├── settings/
    │       └── common/              # Theme, UiSupport, SearchableComboBox
    └── resources/
        ├── config.properties
        ├── css/fittrack.css
        ├── fonts/                   # Manrope
        └── exercises/
            └── free-exercise-db.json
```

### Package responsibilities

| Package | Role |
|---|---|
| `app` | `FitTrackApp` launches JavaFX; `AppContext` wires DAOs/services; `AppConfig` loads classpath + local + env config |
| `api.nutrition` | `UsdaFoodDataClient` — search and fetch foods; maps to domain `Ingredient` |
| `domain.*` | Persistence-free models; 1RM strategies (`OneRepMaxCalculator`); `PortionScaler`; catalog mapping |
| `persistence.sqlite` | JDBC DAOs; `Database` connection; `ConnectionTxn` for multi-statement work |
| `persistence.migration` | `MigrationRunner` — versioned schema in `app_meta` |
| `service` | Auth, profile, exercises, plans, live sessions, nutrition, goals, dashboard, progress, muscle volume |
| `export` | `BackupService` — copy SQLite file; Jackson JSON user snapshot import/export |
| `concurrent` | `PrNotificationBuffer` — bounded recent PR list for Settings |
| `util` | `PasswordHasher` (PBKDF2), `PasswordStrengthChecker`, `BodyMetrics` (BMI/BMR/TDEE) |
| `ui` | `ShellController` side-nav shell; feature views share `UiSupport.page` / `embed` chrome |

### Architecture notes

- **Composition root:** `AppContext.initialize()` opens the DB, runs migrations, constructs DAOs/services once.
- **Session:** `UserSession` holds the logged-in user; services call `requireUser()`.
- **Offline-first nutrition:** search hits local SQLite first; USDA fills gaps when online; cache persists macros per 100 g.
- **1RM:** Epley / Brzycki / Lombardi strategies behind `OneRepMaxCalculator`; all three stored on each weighted set; profile picks preferred display formula.
- **Muscle volume:** primary muscle = 1.0 set, secondary = 0.5; shown on plan editor (planned) and Progress (week completed).
- **Backups:** full DB file replace (`restoreDatabaseFrom`) or portable JSON (profile, weights, goals, meals, dishes). PRs live in SQLite and are regenerated from training history, not JSON round-tripped.
- **UI theme:** Manrope fonts + `css/fittrack.css` (teal/slate); applied in `Theme.apply(scene)`.

### Build & run

```bash
mvn compile
mvn javafx:run
```

There is no test suite in this project; validation is manual against the app UI.

### Configuration

| Source | Examples |
|---|---|
| `src/main/resources/config.properties` | Defaults (`db.path`, `usda.api.*`, `app.version`) |
| `config.local.properties` (cwd) | Local overrides written by Settings / `AppConfig.saveLocalOverride` |
| Environment | `FITTRACK_DB_PATH`, `USDA_API_KEY` |

### Syllabus → code map

Useful if this is graded coursework:

| Topic | Where |
|---|---|
| OOP / encapsulation | `domain.*` aggregates (User–Profile, Dish–DishItem, Plan–PlanItem) |
| Interfaces / polymorphism | `OneRepMaxCalculator` + Epley / Brzycki / Lombardi |
| Collections | Exercise muscle maps; plan item lists; PR `Vector` buffer |
| Strings / regex | `PasswordStrengthChecker` |
| Custom exceptions | `ValidationException`, `AuthenticationException`, `NutritionApiException`, `OfflineDataException`, … |
| Multithreading | Nutrition search workers, backup `ExecutorService`, rest `Timeline` |
| REST client | `UsdaFoodDataClient` |
| JDBC | DAOs under `persistence.sqlite` |
| File I/O | `BackupService`, `AppConfig` local overrides |
