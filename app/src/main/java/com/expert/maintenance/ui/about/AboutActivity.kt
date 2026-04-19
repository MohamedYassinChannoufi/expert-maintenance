package com.expert.maintenance.ui.about

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.expert.maintenance.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * AboutActivity - Displays information about the application
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvVersion: TextView
    private lateinit var tvDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        initViews()
        setupToolbar()
        displayVersionInfo()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvVersion = findViewById(R.id.tv_version)
        tvDescription = findViewById(R.id.tv_description)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displayVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0.0"
            tvVersion.text = "Version $versionName"
            tvDescription.text = "Application de gestion des interventions de maintenance"
        } catch (e: Exception) {
            tvVersion.text = "Version 1.0.0"
        }
    }
}
