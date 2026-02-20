package com.example.ftcdecodescorer

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Buton Back
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val etArtifact = findViewById<EditText>(R.id.etArtifactThreshold)
        val etPattern = findViewById<EditText>(R.id.etPatternThreshold)

        // Citim setările (Default: 36 Artifacts, 9 Pattern Score)
        val prefs = getSharedPreferences("FTCSettings", Context.MODE_PRIVATE)
        etArtifact.setText(prefs.getInt("ARTIFACT_THRESH", 36).toString())
        etPattern.setText(prefs.getInt("PATTERN_THRESH", 9).toString())

        // Salvăm setările
        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val artVal = etArtifact.text.toString().toIntOrNull() ?: 36
            val patVal = etPattern.text.toString().toIntOrNull() ?: 9

            prefs.edit().apply {
                putInt("ARTIFACT_THRESH", artVal)
                putInt("PATTERN_THRESH", patVal)
                apply()
            }
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}