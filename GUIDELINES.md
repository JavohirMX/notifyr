# 📱 Smart Notification Filter — MVP Specification

## 📝 Overview

The **Smart Notification Filter** is an Android app that intelligently reads and filters incoming notifications. Its goal is to **minimize distractions** while ensuring **urgent or important notifications** are surfaced promptly.  

Many users disable non-essential notifications, but even from “important” apps, not all notifications are urgent (e.g., group chats, status updates). This MVP focuses on **on-device filtering**, **rules-based classification**, and **notification history management**, without requiring a backend.

---

## 🚀 MVP Goals

- ✅ **Read notifications** (with user consent) and **categorize them** as “Urgent”, “Normal”, or “Ignore”.
- ✅ **Display only important notifications** in real time.
- ✅ Maintain a **local notification history** for reviewing non-urgent notifications later.
- ✅ Provide **basic rule configuration** per app/contact/keyword.
- ✅ Work **fully offline** with **privacy** in mind.
- ✅ Keep it **battery efficient**, **stable**, and **simple**.

---

## ✨ MVP Features

### 1. 🧠 **Notification Listener & Categorization**
- Register a `NotificationListenerService` to listen to **all incoming notifications**.
- Extract relevant data:
  - App package name
  - Notification title & text
  - Timestamp
  - Category (if available)
  - Contact (if applicable, e.g., from messaging apps)
- Pass extracted data to a **rule-based classifier** to determine:
  - `URGENT`
  - `NORMAL`
  - `IGNORE`

---

### 2. 🪄 **Rule-Based Filtering Engine**

#### a. **Keyword Rules**
- Predefined list of **urgent keywords** (e.g. “ASAP”, “urgent”, “call me”, “meeting”, “help”, “important”, “emergency”).
- Custom user-defined keywords.

#### b. **App-Level Rules**
- Example:
  - Messages from **WhatsApp** → Show only if keyword matches
  - **Banking apps** → Always URGENT
  - **Social media** → Default IGNORE

#### c. **Contact-Level Rules**
- Allow marking specific contacts as **always urgent**.
- Example: Boss, parents, spouse.

#### d. **Notification Type Rules**
- `MessagingStyle` notifications treated with higher priority than promotional notifications.

---

### 3. 📜 **Notification History Page**

- Local **SQLite / Room database** to store all notifications.
- Schema:

| Column            | Type       | Description                        |
|--------------------|-----------|--------------------------------------|
| id (PK)           | Int       | Unique notification ID             |
| packageName       | Text      | App package                        |
| title             | Text      | Notification title                 |
| text              | Text      | Notification content               |
| category          | Text      | Category (if available)            |
| importance        | Int       | 0 = Ignore, 1 = Normal, 2 = Urgent |
| timestamp         | Long      | Epoch time                         |

- History UI:
  - Tabs: **Urgent**, **Normal**, **Ignored**
  - Search & filter by app, keyword, or date
  - Mark notifications as read/archived

---

### 4. 🔔 **Smart Notification Display**

- If a notification is classified as **Urgent**:
  - Immediately trigger a **custom notification** with a special icon and sound.
  - Optionally show a **heads-up notification** even if original app doesn’t.
- If **Normal**:
  - Silently save to history, optionally group in a “Daily Digest”.
- If **Ignore**:
  - Log silently, no alert.

---

### 5. ⚙️ **Settings & Rules Configuration**

- **App Rules UI**:
  - List of installed apps with toggles:
    - Always show
    - Filter by keyword
    - Always ignore

- **Keyword Rules UI**:
  - Add / remove custom keywords.
  - Preload a default list.

- **Contacts Rules UI**:
  - Choose contacts (via Android Contacts API) to mark as “Always Urgent”.

- **History Retention Settings**:
  - Keep history for X days (e.g. 7 days)
  - Clear manually

---

### 6. 🧱 **Foundational Features**

- **Battery-efficient background service** (foreground service with minimal wake locks).
- **Permission management UI** to request notification listener access.
- **Onboarding flow** to explain privacy & get consent.
- **Basic error logging** (local, no external logs in MVP).
- **Light / Dark mode**.

---

## 🧠 Rules Hierarchy (Evaluation Order)

The filtering engine should evaluate rules in a **clear priority order**:

1. **Contact rules** (highest)
   - e.g., “If contact = boss → URGENT”
2. **App rules**
   - e.g., “If app = bank → URGENT”
3. **Keyword rules**
   - e.g., “If message contains urgent keyword → URGENT”
4. **Default handling**
   - e.g., “If none matched → NORMAL” or “IGNORE” based on app type

This hierarchy ensures **explicit rules override generic ones**.

---

## 🧰 Tech Stack (Kotlin, Native Android)

| Component                    | Tech / Library                  | Reason |
|-----------------------------|----------------------------------|--------|
| **Language**                | Kotlin                          | Official, modern, concise |
| **UI**                      | Jetpack Compose                 | Fast modern UI dev |
| **Notification Listener**   | `NotificationListenerService`   | Core Android API |
| **Storage**                 | Room (SQLite)                   | Local history storage |
| **Background Service**      | ForegroundService + WorkManager | Continuous processing |
| **Dependency Injection**    | Hilt                            | Clean architecture |
| **Architecture**           | MVVM / Clean Architecture       | Easy testing, scalable |
| **Keyword Engine**          | Simple Regex + String Matching | Fast on-device |
| **Contact Access**         | Android Contacts API            | Contact-based rules |
| **Material Design 3**     | Jetpack Material3 Components   | Clean UI |

Optional for future:
- **ML/NLP**: TensorFlow Lite / ONNX Runtime for smarter classification.
- **Cloud Sync**: Firebase / Supabase for syncing rules between devices.

---

## 📁 Project Structure (Recommended)

```

smart-notif-filter/
├─ app/
│  ├─ src/main/
│  │  ├─ java/com/example/smartnotif/
│  │  │  ├─ di/                # Hilt modules
│  │  │  ├─ data/              # Room entities, DAOs
│  │  │  ├─ domain/            # Models, use cases, rules engine
│  │  │  ├─ ui/                # Jetpack Compose screens
│  │  │  ├─ service/           # NotificationListenerService
│  │  │  ├─ utils/             # Keyword matchers, helpers
│  │  └─ res/
│  ├─ AndroidManifest.xml
├─ build.gradle
└─ README.md

```

---

## 🧪 Testing Plan (MVP Level)

- ✅ Unit tests for **rule evaluation** (e.g. keyword → urgency classification)
- ✅ Manual tests for:
  - Notifications from various apps
  - Rule changes reflected in real time
  - Battery usage (no aggressive wakelocks)
  - History display & search
- Optional:
  - UI tests for onboarding and settings pages

---

## 🛡 Privacy & Permissions

- Request only:
  - `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
  - Contacts permission (optional, only if contact rules are enabled)
- All processing **stays on device**
- No network access in MVP (except optional crash reporting)
- Transparent onboarding to build trust

---

## 🧭 Roadmap (Post-MVP Ideas)

- 🤖 On-device ML classifier to detect urgency contextually
- 🌐 Multi-device rule sync (Firebase / Supabase)
- 🕒 Scheduled “Digest Mode” notifications (e.g., summarize every 2 hours)
- 📊 Analytics dashboard (e.g., notification patterns)
- 📱 iOS companion app (limited features)
- 🧠 Smart “Focus Mode” integration with Android DND

---

## ⏱ Suggested MVP Timeline (Solo Dev)

| Week | Goals |
|------|-------|
| 1    | Set up project, permissions, notification listener service |
| 2    | Implement rule engine (keyword + app rules) |
| 3    | Implement notification history storage & UI |
| 4    | Settings UI for rules configuration |
| 5    | Polishing, bug fixing, onboarding flow |
| 6    | Closed beta testing, battery optimization |

---

## ✅ Success Criteria for MVP

- App runs continuously without major battery drain
- At least 90% of notifications categorized correctly by simple rules
- History UI is functional and fast
- User can configure basic rules easily
- App doesn’t crash or break OS notification system

---

## 📌 License & Distribution

- Internal beta via **Play Store Internal Testing** or direct APK
- MIT / Apache License (optional)
- Privacy-first positioning in marketing

---

