# Debt Tracker – Android App Development Plan (Kotlin)

## 1. Overview
Debt Tracker is a simple, offline-first Android application built with Kotlin. Its purpose is to help users manage their recurring loan/debt obligations, track payments, and visualise upcoming dues. The app follows Material Design guidelines and supports Vietnamese (default) and English languages via `strings.xml`.

## 2. Technology Stack & Architecture
- **Language:** Kotlin
- **UI:** XML layouts with ViewBinding, Material Components
- **Architecture:** MVVM (Model-View-ViewModel) with Repository pattern
- **Navigation:** Android Jetpack Navigation (single-activity, multiple fragments)
- **Local Database:** Room (SQLite)
- **Backup:** Manual CSV export to external storage (Downloads/Debt-Tracker)
- **Min SDK:** Android 10 (API level 29)
- **Dependency Injection:** Manual (or Hilt if desired – here described without DI framework for simplicity)

## 3. Core Principles & Global Behaviour

### 3.1 Multi‑Language
- All user‑facing strings stored in `res/values/strings.xml` (Vietnamese – default) and `res/values-en/strings.xml` (English).
- Locale can be switched from app settings (future feature) or follows system language; default Vietnamese.

### 3.2 Screen Navigation & Toolbar
- Every screen except the main dashboard has a **fixed back button** on the top‑left.
- The back button is accompanied by the screen **title**, which remains visible when scrolling (implemented via `CollapsingToolbarLayout` or a custom `Toolbar` with `app:layout_scrollFlags`).
- Back navigation uses the Navigation Component’s `popBackStack()` or `onBackPressedDispatcher`.

### 3.3 Critical Confirmation Dialogs
- All irreversible actions (delete debt, exit without saving, clear data, etc.) show a confirmation `AlertDialog` with positive/negative buttons.
- Dialogs use the same styling, driven by a reusable utility function.

### 3.4 Reusable UI Components
The following custom views/components will be created and reused across screens:
- **WheelPicker:** Date/time picker with three scrollable columns (day/month/year). Replaces default `DatePickerDialog` for a consistent UX.
- **Common Calendar:** A month‑view calendar component used when selecting payment dates.
- **DebtCard:** A styled `MaterialCardView` displaying summary debt information (creditor, amount, due date). Used in list screens.
- **ColorSets:** A predefined palette (primary, secondary, accent, danger, success) accessible as an object or theme attributes.
- **ConfirmDialog:** A wrapper function that builds an `AlertDialog` with title, message, confirm/cancel buttons.

## 4. Data Model & Persistence
### Room Entities
```kotlin
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditorName: String,
    val contractNumber: String,
    val dueDayOfMonth: Int,          // day (1–31) when payment is due each month
    val monthlyAmount: Double,
    val startYearMonth: String,      // "YYYY-MM"
    val endYearMonth: String,        // "YYYY-MM"
    val totalPaid: Double = 0.0,
    val principal: Double = 0.0,     // original loan principal (optional)
    val isCompleted: Boolean = false
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val paymentDate: Long,           // timestamp in millis
    val amount: Double
)
```
- `Debt` stores a recurring obligation. Completion is flagged manually or automatically when total paid reaches total expected.
- `Payment` logs each payment. History is retrieved by `debtId`.

### DAOs & Repository
- Standard Room DAOs with suspend functions for insert, update, delete, queries.
- A repository class wraps DAO calls, adding CSV export logic and backup triggers.

## 5. Screens & User Flows

### 5.1 DashboardScreen (Main Screen)
**Purpose:** Quick overview of current month’s obligations and imminent debts.

**Layout:**
1. **Top Card** – "Tổng số tiền cần đóng trong tháng"
   - Shows total amount due this month (sum of `monthlyAmount` for active debts where `dueDayOfMonth` falls within current month).
   - Below the amount: "X/Y khoản vay đã đóng" (X = number of debts with at least one payment this month, Y = total active debts).
   - Tapping the card navigates to the `DebtScreen` (Current tab).
2. **Upcoming Debts Section**
   - Horizontal or vertical list (vertical scroll) of `DebtCard` for debts sorted by next due date, showing the next 3–5 debts.
   - Each card displays:
     - Creditor name
     - Monthly amount
     - Next due date (e.g., "15/06/2026")
   - Tap on a card → `DebtDetailScreen(debtId)`.
3. **FAB (FloatingActionButton)** – "+" to create a new debt → `CreateEditDebtScreen(mode = CREATE)`.

**ViewModel:**
- Exposes `LiveData<DashboardData>` containing total due, paid/total count, upcoming debts list.

### 5.2 DebtScreen (All Debts)
**Two tabs:** `CurrentDebts` and `CompletedDebts` (using `TabLayout` + `ViewPager2` or `Fragment` with tabs).

- **CurrentDebts Tab**
  - Vertical `RecyclerView` of `DebtCard` for all active (`isCompleted == false`) debts, sorted by next due date ascending.
  - Each card: creditor, amount, next due date. Tap → detail.
  - Swipe-to-delete (with confirmation) or long-press menu.
- **CompletedDebts Tab**
  - Identical layout, filtered for `isCompleted == true`, sorted by completion date descending (or last payment date).
  - No swipe to delete by default (maybe archive).

**Shared Toolbar:** Title "Các khoản vay", back arrow (←) to pop back.

### 5.3 DebtDetailScreen (Debt Details)
**Scrollable content** displaying:
1. Creditor name (large, prominent)
2. Contract number with a copy icon (`ClipboardManager`).
3. Next due date (formatted).
4. Amount due (monthly).
5. Remaining months: calculated as months between now and `endYearMonth` (if not completed).
6. Total paid so far.
7. Principal (if available).
8. Payment history: chronological list of `Payment` entries (date + amount). Each row shows date (dd/MM/yyyy) and amount.

**Bottom Button:** "Cập nhật thanh toán" (Update Payment) → opens **PaymentDialog**.

**Mark as Completed:** An option (menu item or button) to manually mark the debt as finished (with confirmation).

**ViewModel:**
- `debt: LiveData<Debt>`
- `payments: LiveData<List<Payment>>`
- `markCompleted()` function.

#### PaymentDialog (Add Payment)
- Appears as a `BottomSheetDialogFragment` or full‑screen dialog.
- Fields:
  - **Payment Date:** default to today, selectable via `WheelPicker` (day, month, year).
  - **Amount Paid:** numeric input field (pre‑filled with monthly amount, editable).
- Buttons: **Lưu** (Save) and **Hủy** (Cancel).
- On Save: insert `Payment` record, update `totalPaid`, optionally check if debt is fully paid and mark completed. Trigger CSV backup.

### 5.4 CreateEditDebtScreen (Add / Edit Debt)
**Fields (vertical order):**
1. Tên nhà tín dụng cho vay (Creditor Name) – EditText
2. Số hợp đồng (Contract Number) – EditText
3. Ngày đến hạn mỗi tháng (Due Day of Month) – A number picker or `WheelPicker` (values 1–31). Could also be a simple `TextInputEditText` with input validation.
4. Số tiền cần đóng (Monthly Amount) – numeric EditText
5. Tháng/Năm bắt đầu (Start Month/Year) – `WheelPicker` with month and year columns.
6. Tháng/Năm kết thúc (End Month/Year) – `WheelPicker`, validated to be after start.
7. Principal (optional) – numeric EditText (nợ gốc).
8. Save / Cancel buttons at the bottom.

**Behaviour:**
- In **Create** mode: title "Tạo khoản vay mới", all fields empty.
- In **Edit** mode: title "Sửa khoản vay", fields pre‑filled from existing `Debt`.
- Save validates input (all required fields, end > start, amounts > 0). On success, insert/update Room and trigger CSV backup.
- Cancel with unsaved changes shows confirmation dialog.

## 6. Auto‑Backup (CSV Export)
- Whenever any `Debt` or `Payment` data is inserted, updated, or deleted, the app automatically exports the entire dataset to a CSV file.
- **Storage location:** `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)/Debt-Tracker/`
- File name: `debts_backup_yyyyMMdd_HHmmss.csv` (timestamped) or simply `debts_export.csv` (overwritten each time). Simpler approach: overwrite a single file.
- **CSV Format:**
  ```
  ID,Creditor,Contract,MonthlyAmount,DueDay,Start,End,TotalPaid,Principal,Completed
  1,Ngân hàng A,ABC123,5000000,15,2025-01,2027-01,15000000,100000000,false
  ...
  Payments:
  PaymentID,DebtID,PaymentDate,Amount
  1,1,2025-06-15,5000000
  ...
  ```
- Export performed on a background coroutine (IO dispatcher). Permission `WRITE_EXTERNAL_STORAGE` (for API < 29) or use `MediaStore` for Android 10+. Use `getExternalFilesDir` as a simpler alternative that doesn’t require permissions? Requirement says "Downloads folder". We'll target `DIRECTORY_DOWNLOADS` and handle runtime permissions gracefully.
- A Toast or Snackbar notifies the user that backup was saved.

## 7. Shared Assets & Component Details

| Asset            | Description | Implementation Approach |
|------------------|-------------|--------------------------|
| **WheelPicker**  | Custom view with three vertically scrolling wheels (day, month, year). Parameters: min/max date, initial selection. | RecyclerView-based or custom drawing. Exposed via custom attributes. |
| **Calendar**     | A month grid calendar showing days, with markers for due dates. Tapping a date returns a `LocalDate`. | Used in date selection dialogs; could be a full-screen dialog or embedded. |
| **DebtCard**     | Card layout for lists: creditor name, amount formatted, due date, visual indicator for overdue. | `MaterialCardView` with a constraint layout; styling via theme. |
| **ColorSets**    | Object `AppColors` holding primary, secondary, error, success, onPrimary, etc., referenced in XML via `@color/...` or theme attributes. | Defined in `colors.xml` with appropriate names; also available in Kotlin as `R.color.xxx`. |
| **ConfirmDialog**| Common method `showConfirmDialog(context, title, message, onConfirm)` that shows an AlertDialog. | Extracted into a utility object. |

## 8. Navigation & Toolbar Implementation
- Use `AppBarConfiguration` with top-level destinations (Dashboard).
- All other screens include a back arrow handled by `NavigationUI.setupActionBarWithNavController`.
- Toolbar title changes per screen; for scrolling screens, use `CollapsingToolbarLayout` with `app:layout_scrollFlags="scroll|exitUntilCollapsed"` so the title shrinks but stays visible, and back button remains pinned.
- An alternative simpler approach: use a standard `Toolbar` that never scrolls, and the content scrolls beneath it. Since requirement says "follow khi user cuộn" (follow when user scrolls), we'll implement the collapsing toolbar.

## 9. Development Phases
1. **Project Setup & Architecture**
   - Create project with Kotlin, Material theme, Navigation component.
   - Set up Room database, entities, DAOs, repository.
   - Implement base `ViewModel` and `LiveData` patterns.
   - Define `strings.xml` for both languages (initial set).

2. **Reusable Components & Theme**
   - Build `WheelPicker`, `DebtCard`, `ConfirmDialog`, `ColorSets`.
   - Create theme with primary colors, typography, shape.

3. **Dashboard Screen**
   - Implement UI, ViewModel, compute monthly totals.
   - Connect upcoming debts list with navigation to detail.

4. **Debt List Screen**
   - Tabs, Current/Completed lists, adapter with DebtCard.
   - Swipe-to-delete and mark-completed actions.

5. **Debt Detail Screen & Payment Dialog**
   - Detail UI, payment history, update payment dialog with WheelPicker.
   - Logic for total paid calculation and completion detection.

6. **Create/Edit Debt Screen**
   - Form layout, WheelPickers for dates, validation.
   - Save/update logic and navigation back.

7. **CSV Backup Module**
   - Permission handling, file writing in background.
   - Automatic trigger after data mutations via repository callbacks.

8. **Polishing & Testing**
   - Ensure confirmation dialogs for critical actions.
   - Back button and title scroll behaviour.
   - Multi‑language string usage review.
   - Manual testing on various screen sizes and Android versions.

## 10. Additional Notes
- **Permissions:** For Android 10+ use `MediaStore` or `getExternalFilesDir`; if strictly Downloads folder is required, `MediaStore.Downloads` is the modern approach. For simplicity, we will use `MediaStore.Downloads` with a content resolver.
- **Date Handling:** Use `java.time` (API 26+) with desugaring for older versions to support `LocalDate`, `YearMonth`.
- **State Management:** `LiveData` or `StateFlow`; described here as `LiveData` for familiarity.
- **Edge Cases:** Debts that are overdue should be visually highlighted (red accent). The dashboard card should reflect urgency.

This plan provides a clear, actionable roadmap for developing the Debt Tracker app, covering all requested features and global behaviours.