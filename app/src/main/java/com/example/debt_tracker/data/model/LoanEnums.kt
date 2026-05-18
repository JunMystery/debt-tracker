package com.example.debt_tracker.data.model

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
