# Debt Tracker – Android App

A simple, offline-first Android application built with Kotlin to help you manage recurring loans and track payment progress.  
The app focuses on clarity, quick access to upcoming dues, and automatic local backups.

## Features

- **Monthly overview** – see total due, paid count, and next upcoming debts at a glance.
- **Debt management** – add, edit, and delete debts with details like creditor, contract number, monthly amount, and payment period.
- **Payment tracking** – log each payment with date and amount; the app auto‑calculates total paid and remaining months.
- **Payment history** – view all past payments per debt in an organised list.
- **Intuitive date pickers** – custom wheel‑style pickers and a shared calendar for all date selections.
- **Confirmation dialogs** – every critical action (delete, exit without saving) asks for confirmation.
- **Multi‑language** – Vietnamese (default) and English, ready for easy expansion via `strings.xml`.
- **Automatic CSV backup** – every change is saved as a CSV file in the `Downloads/Debt-Tracker` folder, keeping your data safe.

## Screenshots
*(Add your own screenshots here)*

## Architecture & Tech Stack

- **Language:** Kotlin
- **UI:** XML layouts with ViewBinding, Material Design Components
- **Architecture:** MVVM with Repository pattern
- **Navigation:** Jetpack Navigation (single‑activity)
- **Local database:** Room (SQLite)
- **Backup:** Manual CSV export to external storage (Downloads)
- **Min SDK:** 24 (Android 7.0)

## Getting Started

### Prerequisites
- Android Studio Hedgehog (or later)
- Kotlin 1.9+
- Gradle 8+

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/debt-tracker.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and run on an emulator or physical device.

### Permissions
The app requests storage permission (for Android 9 and below) or uses `MediaStore` on Android 10+ to save backup CSV files.  
No internet permission is needed – the app works entirely offline.

## How to Use

1. **Dashboard** – Shows total due this month and the next debts. Tap any debt to see details.
2. **Add a debt** – Tap the **+** button, fill in the form (creditor, contract number, due day, start/end dates).
3. **Record a payment** – Inside a debt’s detail screen, tap **Cập nhật thanh toán** (Update Payment) and enter the payment info.
4. **View history** – All payments appear below the debt details.
5. **Complete a debt** – Mark it as finished manually or let the app detect when total paid equals total expected.
6. **Backup** – Automatic; find the CSV in `Downloads/Debt-Tracker/`. Open it with any spreadsheet software.

## Localisation

All user‑facing text is stored in `res/values/strings.xml` (Vietnamese) and `res/values-en/strings.xml` (English).  
To add a new language, copy the strings file and translate. The app follows the device’s language settings.

## Customisation

### Icon
The app icon (adaptive icon) uses a simple coin‑ring with a checkmark.  
Source: `res/drawable/ic_launcher_foreground.xml` (vector).  
You can change colours in the vector or replace it with your own design.

## Contributing
Contributions are welcome! Please open an issue first to discuss changes.

## License
MIT