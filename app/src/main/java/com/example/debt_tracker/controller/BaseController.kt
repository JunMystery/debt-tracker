package com.example.debt_tracker.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseController {
    protected val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
