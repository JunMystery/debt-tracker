# Task: Implement Multi-Type Loan System in Debt Tracker App

## Context
The current Debt Tracker app (`https://github.com/JunMystery/debt-tracker`) only supports **fixed-rate installment loans** with a single unchanging monthly payment. The app needs to be upgraded to support multiple real-world loan types with different calculation methods.

**Tech Stack Reminder:**
- Kotlin, Android 10+ (API 29)
- MVC Architecture with Repository pattern
- Room Database with KSP
- Coroutines for async operations
- ViewBinding for UI

**Key Files You'll Need to Modify:**
- `app/src/main/java/com/.../data/local/entity/Debt.kt`
- `app/src/main/java/com/.../data/local/dao/DebtDao.kt`
- `app/src/main/java/com/.../data/repository/DebtRepository.kt`
- `app/src/main/java/com/.../ui/createdebt/CreateEditDebtFragment.kt`
- `app/src/main/java/com/.../ui/debtdetail/DebtDetailFragment.kt`
- `app/src/main/java/com/.../ui/debtdetail/PaymentDialog.kt`
- `app/src/main/java/com/.../util/` (create new calculation utility)
- `app/src/main/res/layout/fragment_create_edit_debt.xml`
- `app/src/main/res/layout/fragment_debt_detail.xml`
- CSV export logic (in repository)
- All language `strings.xml` files (7 locales)

---

## Phase 1: Update Data Model

### 1.1 Add New Fields to Debt Entity

Add these fields to the `Debt` data class:

```kotlin
// NEW FIELDS TO ADD:
val interestRate: Double = 0.0,           // Annual interest rate (e.g., 5.5 for 5.5%)
val interestType: InterestType = InterestType.FIXED,  // Fixed or Reducing Balance
val paymentType: PaymentType = PaymentType.INSTALLMENT, // Installment, Interest-Only, Bullet
val creditLimit: Double = 0.0,            // For credit line/reducing balance loans
val remainingBalance: Double = 0.0,       // Current outstanding balance
val currencyCode: String = "VND",         // ISO currency code (already may exist)
val lastInterestCalculationDate: Long? = null, // Track when interest was last accrued
val minimumPaymentPercent: Double = 0.0   // For credit lines (e.g., 5% of balance)
```

### 1.2 Create Enum Classes

Create a new file `app/src/main/java/com/.../data/model/LoanEnums.kt`:

```kotlin
enum class InterestType {
    FIXED,          // Traditional fixed-rate loan
    REDUCING,       // Reducing balance / credit line
    ISLAMIC;        // Islamic finance (profit rate based)
}

enum class PaymentType {
    INSTALLMENT,    // Equal monthly payments (annuity)
    INTEREST_ONLY,  // Interest-only, principal at maturity (bullet)
    CUSTOM;         // User-defined amounts (credit lines)
}
```

### 1.3 Database Migration

- Increment `@Database` version (e.g., from `version = 1` to `version = 2`)
- Create a `Migration(1, 2)` that adds the new columns with default values
- Default for existing debts: `interestType = FIXED`, `paymentType = INSTALLMENT`

---

## Phase 2: Implement Calculation Engine

### 2.1 Create LoanCalculator Utility

Create `app/src/main/java/com/.../util/LoanCalculator.kt` with these functions:

```kotlin
object LoanCalculator {

    /**
     * Calculate the next payment amount based on loan type
     */
    fun calculateNextPayment(debt: Debt): Double {
        return when {
            // Fixed Installment: return the fixed monthly amount
            debt.paymentType == PaymentType.INSTALLMENT && debt.interestType == InterestType.FIXED -> {
                debt.monthlyAmount
            }
            
            // Reducing Balance / Credit Line: calculate minimum or suggested payment
            debt.interestType == InterestType.REDUCING -> {
                calculateReducingBalancePayment(debt)
            }
            
            // Interest-Only (Bullet): calculate interest portion only
            debt.paymentType == PaymentType.INTEREST_ONLY -> {
                calculateInterestOnlyPayment(debt)
            }
            
            // Islamic Finance: similar to fixed but uses profit rate
            debt.interestType == InterestType.ISLAMIC -> {
                calculateIslamicPayment(debt)
            }
            
            else -> debt.monthlyAmount // fallback
        }
    }

    /**
     * Calculate monthly payment for reducing balance loan
     * Formula: Interest = (Remaining Balance * Annual Rate / 365) * Days in Month
     * Minimum payment = greater of (Interest + 1% principal) or (MinPaymentPercent * Balance)
     */
    fun calculateReducingBalancePayment(debt: Debt): Double {
        val dailyRate = debt.interestRate / 100.0 / 365.0
        val daysInMonth = YearMonth.now().lengthOfMonth()
        val interestCharge = debt.remainingBalance * dailyRate * daysInMonth
        val principalPayment = maxOf(debt.remainingBalance * 0.01, 0.0)
        val minimumPayment = maxOf(
            interestCharge + principalPayment,
            debt.remainingBalance * debt.minimumPaymentPercent / 100.0
        )
        return minimumPayment
    }

    /**
     * Calculate interest-only payment for bullet loans
     * Formula: Monthly Interest = (Principal * Annual Rate) / 12
     */
    fun calculateInterestOnlyPayment(debt: Debt): Double {
        return (debt.principal * debt.interestRate / 100.0) / 12.0
    }

    /**
     * Calculate payment for Islamic financing (Murabaha/Ijarah)
     * Uses profit rate instead of interest rate
     * Total financed amount = Principal + (Principal * ProfitRate * Tenor)
     * Monthly = Total / Number of Months
     */
    fun calculateIslamicPayment(debt: Debt): Double {
        val totalMonths = calculateTotalMonths(debt)
        val totalProfit = debt.principal * (debt.interestRate / 100.0) * (totalMonths / 12.0)
        val totalFinanced = debt.principal + totalProfit
        return totalFinanced / totalMonths
    }

    /**
     * Calculate remaining balance after payment
     */
    fun calculateNewBalance(debt: Debt, paymentAmount: Double): Double {
        return when (debt.interestType) {
            InterestType.FIXED, InterestType.ISLAMIC -> {
                // Fixed loans: reduce total expected
                val expectedTotal = debt.monthlyAmount * calculateTotalMonths(debt)
                val newTotalPaid = debt.totalPaid + paymentAmount
                maxOf(expectedTotal - newTotalPaid, 0.0)
            }
            InterestType.REDUCING -> {
                // Reducing balance: direct subtraction
                maxOf(debt.remainingBalance - paymentAmount, 0.0)
            }
        }
    }

    /**
     * Check if debt is fully paid
     */
    fun isDebtCompleted(debt: Debt): Boolean {
        return when (debt.interestType) {
            InterestType.FIXED, InterestType.ISLAMIC -> {
                val expectedTotal = debt.monthlyAmount * calculateTotalMonths(debt)
                debt.totalPaid >= expectedTotal
            }
            InterestType.REDUCING -> {
                debt.remainingBalance <= 0.0
            }
        }
    }

    private fun calculateTotalMonths(debt: Debt): Int {
        val start = YearMonth.parse(debt.startYearMonth)
        val end = YearMonth.parse(debt.endYearMonth)
        return ((end.year - start.year) * 12) + (end.monthValue - start.monthValue) + 1
    }
}
```

### 2.2 Update Repository Logic

Modify `DebtRepository.kt` to use the calculator:

```kotlin
suspend fun addPayment(debtId: Long, amount: Double, date: Long) {
    val debt = debtDao.getDebtByIdSync(debtId) ?: return
    
    // Insert payment record
    paymentDao.insert(Payment(debtId = debtId, paymentDate = date, amount = amount))
    
    // Calculate new state
    val newTotalPaid = debt.totalPaid + amount
    val newRemainingBalance = LoanCalculator.calculateNewBalance(debt, amount)
    val isCompleted = LoanCalculator.isDebtCompleted(
        debt.copy(totalPaid = newTotalPaid, remainingBalance = newRemainingBalance)
    )
    
    // Update debt
    debtDao.update(debt.copy(
        totalPaid = newTotalPaid,
        remainingBalance = newRemainingBalance,
        isCompleted = isCompleted,
        completedAt = if (isCompleted) System.currentTimeMillis() else null,
        lastInterestCalculationDate = date
    ))
    
    // Trigger CSV backup
    exportToCsv()
}
```

---

## Phase 3: Update UI Screens

### 3.1 Create/Edit Debt Screen Changes

**In `fragment_create_edit_debt.xml`**, add these fields (order matters):

```xml
<!-- After existing fields, add: -->

<!-- Loan Type Section -->
<com.google.android.material.textfield.TextInputLayout>
    <AutoCompleteTextView
        android:id="@+id/interestTypeDropdown"
        android:entries="@array/interest_types" />
</com.google.android.material.textfield.TextInputLayout>

<com.google.android.material.textfield.TextInputLayout>
    <AutoCompleteTextView
        android:id="@+id/paymentTypeDropdown"
        android:entries="@array/payment_types" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Interest/Profit Rate -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/interestRateLayout">
    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/interestRateInput"
        android:inputType="numberDecimal" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Credit Limit (visible only for REDUCING balance) -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/creditLimitLayout"
    android:visibility="gone">
    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/creditLimitInput"
        android:inputType="numberDecimal" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Minimum Payment Percent (visible only for REDUCING balance) -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/minPaymentPercentLayout"
    android:visibility="gone">
    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/minPaymentPercentInput"
        android:inputType="numberDecimal" />
</com.google.android.material.textfield.TextInputLayout>
```

**In `CreateEditDebtFragment.kt`**, add logic:

```kotlin
// Show/hide fields based on selection
interestTypeDropdown.setOnItemClickListener { _, _, position, _ ->
    val isReducing = position == 1 // REDUCING enum position
    creditLimitLayout.isVisible = isReducing
    minPaymentPercentLayout.isVisible = isReducing
}

// Change hint text based on type
// For FIXED -> "Interest Rate (%)"
// For REDUCING -> "Annual Interest Rate (%)"  
// For ISLAMIC -> "Profit Rate (%)"
```

**Add to `res/values/arrays.xml`** (for all 7 locales):
```xml
<string-array name="interest_types">
    <item>Fixed Rate</item>
    <item>Reducing Balance</item>
    <item>Islamic Finance</item>
</string-array>

<string-array name="payment_types">
    <item>Installment (Equal Payments)</item>
    <item>Interest-Only (Bullet)</item>
    <item>Custom Amounts</item>
</string-array>
```

### 3.2 Debt Detail Screen Changes

**In `fragment_debt_detail.xml`** or the detail layout:

Add display fields that adapt to loan type:
```xml
<!-- Dynamic Payment Info Section -->
<TextView
    android:id="@+id/nextPaymentLabel"
    android:text="Khoản thanh toán tiếp theo" />

<TextView
    android:id="@+id/nextPaymentAmount"
    android:textStyle="bold" />

<!-- For reducing balance, show remaining balance prominently -->
<TextView
    android:id="@+id/remainingBalanceLabel"
    android:text="Dư nợ còn lại"
    android:visibility="gone" />

<TextView
    android:id="@+id/remainingBalanceAmount"
    android:visibility="gone" />

<!-- Show interest rate info -->
<TextView
    android:id="@+id/interestRateDisplay"
    android:text="Lãi suất: 5.5%/năm" />
```

**In `DebtDetailFragment.kt`**:
```kotlin
// Update UI based on debt type
private fun updateDebtDisplay(debt: Debt) {
    when {
        debt.interestType == InterestType.REDUCING -> {
            // Show remaining balance, hide fixed amount
            nextPaymentLabel.text = getString(R.string.suggested_payment)
            nextPaymentAmount.text = formatCurrency(
                LoanCalculator.calculateReducingBalancePayment(debt)
            )
            remainingBalanceLabel.isVisible = true
            remainingBalanceAmount.text = formatCurrency(debt.remainingBalance)
            remainingBalanceAmount.setTextColor(
                if (debt.remainingBalance > 0) 
                    ContextCompat.getColor(requireContext(), R.color.red)
                else 
                    ContextCompat.getColor(requireContext(), R.color.green)
            )
        }
        debt.paymentType == PaymentType.INTEREST_ONLY -> {
            // Show interest component and balloon payment
            nextPaymentAmount.text = formatCurrency(
                LoanCalculator.calculateInterestOnlyPayment(debt)
            )
            // Show warning about balloon payment at maturity
        }
        else -> {
            // Fixed/Islamic: show standard fixed payment
            nextPaymentAmount.text = formatCurrency(debt.monthlyAmount)
        }
    }
}
```

### 3.3 Payment Dialog Update

**In `PaymentDialog.kt`**, update the pre-filled amount:
```kotlin
// Pre-fill suggested payment amount
val suggestedAmount = LoanCalculator.calculateNextPayment(debt)
binding.paymentAmountInput.setText(formatNumber(suggestedAmount))
```

---

## Phase 4: Update CSV Backup & Restore

### 4.1 CSV Export

Update the CSV header and rows to include new fields:
```kotlin
appendLine("ID,Creditor,Contract,DueDay,MonthlyAmount,Start,End,Principal,TotalPaid,Completed,InterestRate,InterestType,PaymentType,CreditLimit,RemainingBalance")
// Add corresponding values for each debt
```

### 4.2 CSV Import/Restore

Update the restoration logic to parse the new columns and create debts with correct types:
```kotlin
val interestType = InterestType.valueOf(csvFields[11].uppercase())
val paymentType = PaymentType.valueOf(csvFields[12].uppercase())
```

---

## Phase 5: Strings & Internationalization

Add these strings to all 7 locale files (`values-vi`, `values-en`, `values-zh`, etc.):

```xml
<!-- English (values-en/strings.xml) -->
<string name="interest_type">Interest Type</string>
<string name="payment_type">Payment Type</string>
<string name="fixed_rate">Fixed Rate</string>
<string name="reducing_balance">Reducing Balance</string>
<string name="islamic_finance">Islamic Finance</string>
<string name="installment">Installment</string>
<string name="interest_only">Interest-Only</string>
<string name="custom_payment">Custom</string>
<string name="interest_rate">Interest Rate (%)</string>
<string name="profit_rate">Profit Rate (%)</string>
<string name="credit_limit">Credit Limit</string>
<string name="min_payment_percent">Minimum Payment (%)</string>
<string name="remaining_balance">Remaining Balance</string>
<string name="suggested_payment">Suggested Payment</string>
<string name="balloon_warning">Principal due at maturity</string>

<!-- Vietnamese (values-vi/strings.xml) -->
<string name="interest_type">Loại lãi suất</string>
<string name="payment_type">Loại thanh toán</string>
<string name="fixed_rate">Lãi suất cố định</string>
<string name="reducing_balance">Dư nợ giảm dần</string>
<string name="islamic_finance">Tài chính Hồi giáo</string>
<string name="installment">Trả góp đều</string>
<string name="interest_only">Chỉ trả lãi</string>
<string name="custom_payment">Tùy chỉnh</string>
<string name="interest_rate">Lãi suất (%/năm)</string>
<string name="profit_rate">Tỷ suất lợi nhuận (%/năm)</string>
<string name="credit_limit">Hạn mức tín dụng</string>
<string name="min_payment_percent">Thanh toán tối thiểu (%)</string>
<string name="remaining_balance">Dư nợ còn lại</string>
<string name="suggested_payment">Khoản đề xuất</string>
<string name="balloon_warning">Tiền gốc đến hạn khi đáo hạn</string>
```

*(Translate similarly for Chinese, Japanese, Korean — use Google Translate or consult native speakers)*

---

## Phase 6: Testing Checklist

After implementation, verify:

- [ ] Creating a new debt with each loan type works
- [ ] Editing an existing debt preserves new fields
- [ ] Fixed-rate loans still calculate correctly (backward compatibility)
- [ ] Reducing balance payments decrease `remainingBalance`
- [ ] Interest-only loans show correct interest calculation
- [ ] Islamic finance uses profit rate, not interest
- [ ] CSV export includes all new fields
- [ ] CSV import correctly restores loan types
- [ ] All 7 languages display correct terminology
- [ ] Dashboard correctly shows different payment amounts per type
- [ ] Payment dialog suggests correct amount for each type
- [ ] Room migration works without data loss

---

## Additional Considerations

1. **Amortization Schedule:** Consider adding a feature to display a full payment schedule for fixed-rate loans
2. **Variable Interest Rates:** Future feature to support rate changes over time
3. **Compound Interest:** Some reducing balance loans compound daily vs. monthly
4. **Grace Periods:** Some loans have interest-free periods
5. **Fees:** Add support for processing fees, late payment fees

Start with the 4 types described above, then expand based on user feedback.