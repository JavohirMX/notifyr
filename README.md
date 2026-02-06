## Notifyr – Smart Notification Filter for Android

Notifyr is a **privacy‑first notification manager** for Android (API 24–34) that **reduces distractions** while making sure truly important notifications still break through.

Instead of just mirroring what your apps send, Notifyr:
- **Intercepts all notifications**
- **Suppresses** the noisy ones
- **Shows enhanced, filtered notifications and digests** based on importance, context, and your focus mode
- Optionally uses an **on‑device ML model** that learns from your feedback (no network access, no cloud).

---

## Why Notifyr?

Modern apps send far more notifications than most people can reasonably handle. Group chats, social updates, promotions, and status pings compete with urgent work, family, banking, and system alerts.

Notifyr is built to:
- **Minimize notification overload** without forcing you to disable everything
- **Surface urgent and time‑sensitive items** when they matter
- **Respect your attention and privacy** by keeping all processing on your device

---

## Key Features

- **Intelligent notification interception**
  - Uses Android’s `NotificationListenerService` to listen to all notifications
  - Extracts app, title, text, timestamp, category, sender, and more

- **Hybrid classification engine**
  - **Rules engine**: app rules, keywords, categories, focus modes
  - **ML classifier** (optional): learns from your corrections and behavior
  - Final decision balances rules with ML confidence

- **Original notification suppression**
  - Cancels the original notifications from apps
  - Replaces them with **Notifyr’s own enhanced notifications**
  - Avoids duplicate alerts (original + custom)
  - Carefully preserves ongoing notifications (music, calls, media)

- **Smart tags and multi‑dimensional categorization**
  - Tags each notification with:
    - **Priority**: CRITICAL, IMPORTANT, NORMAL, LOW
    - **Context**: WORK, PERSONAL, FINANCIAL, SOCIAL, SHOPPING, TRAVEL, HEALTH, SYSTEM, etc.
    - **Time sensitivity**: IMMEDIATE, SOON, LATER, WHENEVER
    - **Action type**: NEEDS_RESPONSE, FYI, CONVERSATIONAL, etc.
  - Enables nuanced filtering and better digests, not just “urgent vs not”

- **Focus modes with auto‑switching**
  - Modes like **Normal**, **Work**, **Personal**, **Deep Focus**, **Sleep**
  - Auto‑switching based on time of day and day of week
  - Each mode controls which tags/contexts are allowed to interrupt you

- **Smart digest with grouping and summaries**
  - Groups by **conversation**, **sender**, and **app**
  - Summarizes what happened (“John sent 5 messages”, “3 bank alerts”)
  - Shows at **intelligent times** (e.g., when you unlock after a period of inactivity) instead of spamming on a fixed schedule

- **Notification history & search**
  - Full local history stored in a Room database
  - Filters by importance, app, context, time, and tags
  - Search for past notifications

- **Rules and keyword management**
  - Per‑app rules: always show, filter by keyword, always ignore
  - Default categories (e.g., banking → important, social → often ignored)
  - Custom keyword lists with optional regex support

- **Screen time & insights (optional)**
  - Tracks app usage using `UsageStatsManager` (with explicit permission)
  - Shows how much time you spend in each app and when
  - **Not used for classification**, only for user insights

- **On‑device ML, privacy‑first**
  - Hybrid classifier with feature extractor and simple neural model
  - Training data stored locally; model weights saved on device
  - No external API calls, no cloud sync

---

## Screens & UX (Overview)

Notifyr uses a single‑activity, Jetpack Compose UI with a bottom navigation and dedicated screens:

- **Dashboard**
  - Current focus mode and notification listener status
  - Recent urgent notifications
  - Quick stats and shortcuts to settings

- **History**
  - Tabs for **Urgent**, **Normal**, **Ignored**
  - Filters and search
  - Detail view for each notification

- **Settings**
  - App rules
  - Keywords and advanced filters
  - Focus modes & digest behavior
  - Permissions and advanced options

- **Insights & Screen Time** (if enabled)
  - App usage charts and patterns
  - High‑level productivity insights

- **Digest & Smart Features**
  - Digest settings and preview
  - Focus mode configuration

For a more detailed, user‑oriented walkthrough, see [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md).

---

## Privacy & Data Handling

Notifyr is built as a **privacy‑first, offline‑only** app.

- **All processing is on‑device**
  - Classification, tagging, digest generation, and ML training all run locally
  - No notification contents are sent to any server

- **Network usage**
  - The app does **not** require network access for its core functionality
  - Any future analytics/telemetry should be strictly opt‑in and privacy‑respecting

- **Stored data**
  - **Notifications**: Stored in a local Room database for history and digests
  - **Rules & settings**: Stored in Android DataStore (JSON/Protobuf‑style)
  - **ML training data & model**: Stored in app storage (no external export unless explicitly triggered)

- **Permissions**
  - `Notification Listener` permission to read notifications
  - Optional `Usage Access` permission for screen time statistics
  - Other permissions are requested only if a feature explicitly requires them

See the architecture and persistence details in [`docs/ARCHITECTURE_OVERVIEW.md`](docs/ARCHITECTURE_OVERVIEW.md).

---

## Requirements

- **Android version**
  - **Min SDK**: 24 (Android 7.0)
  - **Target/Compile SDK**: 34 (Android 14)

- **Device requirements**
  - Phone or tablet running Android 7.0+
  - Ability to grant **Notification Listener** and (optionally) **Usage Access**

---

## Installation & Setup

Notifyr is currently distributed as a source project (Android Studio / Gradle). To run it on a device or emulator:

### 1. Clone the repository

```bash
git clone https://github.com/your-org-or-user/notifyr.git
cd Notifyr
```

*(Update the URL above to your actual repository location if needed.)*

### 2. Open in Android Studio

- Open **Android Studio Hedgehog or later**
- Choose **“Open an existing project”** and select the `Notifyr` folder
- Let Gradle sync and download dependencies

### 3. Build & run

You can build from the IDE, or via command line:

```bash
./gradlew assembleDebug     # Build debug APK
./gradlew installDebug      # Install on connected device/emulator
```

Once installed, launch **Notifyr** on your device.

### 4. Grant required permissions

On first run, the onboarding flow will help you:

- Enable **Notification Listener** access:
  - System Settings → Notifications → Special App Access → Notification Access
  - Turn on Notifyr
- (Optional) Enable **Usage Access**:
  - System Settings → Apps → Special App Access → Usage Access
  - Turn on Notifyr for screen time features

Follow the on‑screen instructions in the app to complete setup.

For more detailed, step‑by‑step setup instructions and screenshots, see [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md).

---

## How It Works (High‑Level Architecture)

Notifyr follows a **Clean Architecture + MVVM** approach with separate data, domain, and presentation layers, plus a dedicated service layer.

### Layered structure

- **`data/`**
  - Room database entities and DAOs (notifications, screen time, etc.)
  - Repositories wrapping database and DataStore access
  - Data mappers between entities and domain models

- **`domain/`**
  - Core models like `NotificationData`, `NotificationTags`, `AppRule`, `FocusMode`
  - Rules engines: classic rules + enhanced tagging
  - ML components: feature extractor, hybrid classifier, training data manager
  - Use cases for digest generation, insights, and screen time

- **`ui/`**
  - Jetpack Compose screens (Dashboard, History, Settings, Insights, Digest, ML settings, Focus modes)
  - ViewModels using StateFlow and coroutines
  - Navigation graph and routes

- **`service/`**
  - `NotificationListenerService` for interception and suppression
  - Screen time collection service
  - Export/import service

- **`di/`**
  - Hilt modules wiring up Room, repositories, DataStore, ML components, and WorkManager

### Notification data flow

```mermaid
flowchart TD
  incoming[System Notification] --> listener[NotificationListenerService]
  listener --> rules[NotificationRulesEngine]
  rules --> enhanced[EnhancedNotificationRulesEngine]
  enhanced --> hybrid[HybridNotificationClassifier]
  hybrid --> decision[NotificationDecision]
  decision --> suppress[Suppress/Store]
  suppress --> manager[NotifyrNotificationManager]
  suppress --> db[Room Database]
  db --> ui[Compose UI (History/Digest)]
  ui --> feedback[User Feedback]
  feedback --> hybrid
  feedback --> training[MLTrainingDataManager]
  training --> hybrid
```

For a deeper technical explanation, see:
- [`docs/ARCHITECTURE_OVERVIEW.md`](docs/ARCHITECTURE_OVERVIEW.md)
- [`docs/ML_SYSTEM.md`](docs/ML_SYSTEM.md)
- `.github/copilot-instructions.md` (internal architecture & coding guidelines)

---

## Developer Quick Start

If you want to work on Notifyr as a developer:

1. **Environment**
   - Android Studio Hedgehog or later
   - JDK 17 (or the version bundled with your IDE)
   - Android SDK 24–34 installed

2. **Build commands**

```bash
./gradlew assembleDebug           # Build debug APK
./gradlew testDebug               # Run unit tests (when available)
./gradlew connectedAndroidTest    # Run instrumented tests
```

3. **Where to start in code**
   - Entry points:
     - `NotifyrApplication` – Hilt setup, global initialization
     - `MainActivity` – Single activity hosting the Compose navigation
     - `NotificationListenerService` – Core notification interception and classification
   - Classification pipeline:
     - `NotificationRulesEngine` – Base rules
     - `EnhancedNotificationRulesEngine` – Tags and contexts
     - `HybridNotificationClassifier` – Hybrid rules + ML orchestrator
   - Digests and focus:
     - `SmartDigestScheduler`, `DigestGenerator`
     - `FocusModeManager`

4. **Architectural & coding guidelines**
   - See `.github/copilot-instructions.md` for project‑specific patterns
   - See [`docs/ARCHITECTURE_OVERVIEW.md`](docs/ARCHITECTURE_OVERVIEW.md) for a conceptual map
   - See [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for contribution guidelines

---

## Documentation Map

- **End users**
  - [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) – Setup, everyday usage, and tips
  - [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) – Common issues and fixes

- **Architecture & internals**
  - [`docs/ARCHITECTURE_OVERVIEW.md`](docs/ARCHITECTURE_OVERVIEW.md) – Layers, components, and data flows
  - [`docs/ML_SYSTEM.md`](docs/ML_SYSTEM.md) – Hybrid classifier, features, and training
  - [`docs/IMPLEMENTATION_SUMMARY.md`](docs/IMPLEMENTATION_SUMMARY.md) – Enhanced features summary

- **Development & contribution**
  - [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) – Dev setup, style, testing, and workflow
  - [`docs/TEST_INSTRUCTIONS.md`](docs/TEST_INSTRUCTIONS.md) – Additional testing details
  - [`GUIDELINES.md`](GUIDELINES.md) – Original MVP specification
  - [`ROADMAP.md`](ROADMAP.md) – Detailed development roadmap and status

---

## Status

Notifyr is a **feature‑complete MVP** with:
- Full notification interception and classification pipeline
- Original notification suppression and enhanced notifications
- Focus modes, smart digest, history, rules, and keyword management
- Onboarding, settings, and in‑app help

The next major focus areas are:
- Systematic **testing** (unit, integration, UI)
- **Battery optimization** and OEM‑specific behavior
- Optional **analytics/monitoring** with strict privacy controls

For details on what is implemented and what’s next, see [`ROADMAP.md`](ROADMAP.md).

