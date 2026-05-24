package com.example.debt_tracker.ui.components

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.debt_tracker.R
import com.google.android.material.appbar.MaterialToolbar

class StylePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style_preview)

        val toolbar = findViewById<MaterialToolbar>(R.id.preview_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
}
