# Simple Debt Tracker — Android Application

A premium, offline-first Android application built with Kotlin to manage recurring debts, track payments, and calculate future balances. It features a clinical minimalist design system, advanced scheduling formulas, automatic multi-currency localization, real-time exchange rates, and secure local CSV backups with data import/restoration.

---

## ✨ Features

- **Clinical Minimalist Dashboard** — Quickly view monthly statistics, paid progress metrics, and next upcoming dues with color-coded status stripes (Indigo for active, Red for overdue, Green for completed).
- **Advanced Equidistant Reminder Engine** — Eliminates redundant timing menus by dynamically dividing your daily active notification window (Start Hour to End Hour) into perfectly equidistant notification moments. Schedules precisely *one* exact Android `AlarmManager` wakeup at a time to optimize resources.
- **Double-Wheel Time Picker** — Supports full `00:00` to `23:59` daily scheduling coverage using a polished, custom two-wheel popup selector (Hours and Minutes).
- **Multi-Language Support (7 Locales)** — Includes complete visual flag-prefixed options:
  - 🇺🇸 English (US) — *Currency: USD (`$`)*
  - 🇬🇧 English (UK) — *Currency: GBP (`£`)*
  - 🇻🇳 Tiếng Việt (Default) — *Currency: VND (`₫`)*
  - 🇨🇳 简体中文 — *Currency: CNY (`¥`)*
  - 🇨🇳 繁體中文 — *Currency: TWD (`NT$`)*
  - 🇯🇵 日本語 — *Currency: JPY (`¥`)*
  - 🇰🇷 한국어 — *Currency: KRW (`₩`)*
- **Dynamic Localized Formatting** — Automatically resolves the standard local numeric formats (such as `dd/MM/yyyy` vs `yyyy/MM/dd`) based on device configurations, and hides decimals for non-fractional currencies (VND, KRW, JPY, TWD).
- **Real-Time Exchange Rate Conversions** — Dynamically fetches current exchange rates from the Budjet API (`GET https://api.budjet.org`) to show automatic, side-by-side currency conversions directly on the monthly dashboard ticker (e.g. `≈ $120.50 (Rate: 7.24 ¥/USD)`).
- **Auto-Save Local Backups** — Obsessively monitors database CRUD operations to auto-export datasets securely as CSV files into the device default `Download/Debt-Tracker` directory. Works permissionless on modern Android 10+ using Scoped Storage APIs.
- **CSV Data Restoration** — Restore all debts and payment logs from any previously exported backup file in a single tap, with full coroutine transactions, validation safety, and self-healing alarm rescheduling.
- **Saved State Warnings** — Warns users when backing out of unfinished obligations forms ("Thoát mà chưa lưu?") to protect active data entry.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **Architecture:** Model-View-Controller (MVC) with clean repository abstraction layer.
- **Database:** Room (SQLite) configured with modern Google Kotlin Symbol Processing (**KSP**).
- **Threading:** Kotlin Coroutines (`Dispatchers.IO` and `lifecycleScope`).
- **Alarms:** `AlarmManager` (Exact RTC Wakeups) with custom Broadcast Receivers.
- **Backups:** Scoped Storage MediaStore API.
- **Min SDK:** Android 10 (API level 29)
- **Target SDK:** Android 14 (API level 34)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (or later)
- JDK 17+
- Gradle 8+

### Build & Run
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/debt-tracker.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and compile:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📂 Version History & Change Logs

Detailed architectural improvements, layout refactoring details, and release notes are maintained in the `/versions` directory:
- [v1.0.0 Change Log](file:///e:/Github/Debt-Tracker/versions/v1.0.0.md) — Initial feature-complete release including multi-currency localizations, dynamic exchange rates, exact scheduling, and CSV import/export.

---

## 📄 License

This project is licensed under the MIT License.