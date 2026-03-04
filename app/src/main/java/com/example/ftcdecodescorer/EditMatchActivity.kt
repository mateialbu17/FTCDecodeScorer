package com.example.ftcdecodescorer

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EditMatchActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var matchToEdit: MatchResult? = null
    private val counters = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_edit_match)

            db = AppDatabase.getDatabase(this)

            matchToEdit = SessionDetailsActivity.matchHolder
            if (matchToEdit == null) {
                Toast.makeText(this, "Eroare: Meciul nu a putut fi încărcat din memorie.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val m = matchToEdit!!
            findViewById<TextView>(R.id.tvEditTitle)?.text = if (m.teamNumber != null) "Edit Team ${m.teamNumber}" else "Edit Match ${m.matchNumber}"

            findViewById<ImageView>(R.id.btnBackEdit)?.setOnClickListener { finish() }
            findViewById<Button>(R.id.btnCancelEdit)?.setOnClickListener { finish() }

            fun setupCounter(plusId: Int, minId: Int, valId: Int, key: String, startVal: Int) {
                counters[key] = startVal
                val tvVal = findViewById<TextView>(valId)
                tvVal?.text = startVal.toString()

                findViewById<Button>(plusId)?.setOnClickListener { v ->
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    counters[key] = counters[key]!! + 1
                    tvVal?.text = counters[key].toString()
                    updateScores()
                }
                findViewById<Button>(minId)?.setOnClickListener { v ->
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    if (counters[key]!! > 0) {
                        counters[key] = counters[key]!! - 1
                        tvVal?.text = counters[key].toString()
                        updateScores()
                    }
                }
            }

            // Setăm Contoarele Clasice
            setupCounter(R.id.btnPlusAC, R.id.btnMinAC, R.id.valAC, "AC", m.autoClassified)
            setupCounter(R.id.btnPlusAO, R.id.btnMinAO, R.id.valAO, "AO", m.autoOverflow)
            setupCounter(R.id.btnPlusTC, R.id.btnMinTC, R.id.valTC, "TC", m.teleopClassified)
            setupCounter(R.id.btnPlusTO, R.id.btnMinTO, R.id.valTO, "TO", m.teleopOverflow)
            setupCounter(R.id.btnPlusTD, R.id.btnMinTD, R.id.valTD, "TD", m.teleopDepot)

            // --- SETĂM LOGICA NOUĂ PENTRU BUTOANELE DE PATTERN ---
            counters["AP"] = m.autoPattern
            findViewById<TextView>(R.id.valAP)?.text = m.autoPattern.toString()
            findViewById<Button>(R.id.btnSelectAP)?.setOnClickListener {
                // Pentru Auto (AP), punem "true"
                PatternHelper.showDialog(this, true) { matches ->
                    counters["AP"] = matches
                    findViewById<TextView>(R.id.valAP)?.text = matches.toString()
                    updateScores()
                }
            }

            counters["TP"] = m.teleopPattern
            findViewById<TextView>(R.id.valTP)?.text = m.teleopPattern.toString()
            findViewById<Button>(R.id.btnSelectTP)?.setOnClickListener {
                // Pentru TeleOp (TP), punem "false"
                PatternHelper.showDialog(this, false) { matches ->
                    counters["TP"] = matches
                    findViewById<TextView>(R.id.valTP)?.text = matches.toString()
                    updateScores()
                }
            }
            // -----------------------------------------------------

            val cbL1 = findViewById<CheckBox>(R.id.cbEditLeave1)
            val cbL2 = findViewById<CheckBox>(R.id.cbEditLeave2)
            cbL1?.isChecked = m.autoLeave1
            cbL2?.isChecked = m.autoLeave2
            cbL1?.setOnCheckedChangeListener { _, _ -> updateScores() }
            cbL2?.setOnCheckedChangeListener { _, _ -> updateScores() }

            val rgP1 = findViewById<RadioGroup>(R.id.rgEditPark1)
            val rgP2 = findViewById<RadioGroup>(R.id.rgEditPark2)
            when (m.park1Status) { "Full" -> rgP1?.check(R.id.rbEditP1Full); "Partial" -> rgP1?.check(R.id.rbEditP1Part); else -> rgP1?.check(R.id.rbEditP1None) }
            when (m.park2Status) { "Full" -> rgP2?.check(R.id.rbEditP2Full); "Partial" -> rgP2?.check(R.id.rbEditP2Part); else -> rgP2?.check(R.id.rbEditP2None) }
            rgP1?.setOnCheckedChangeListener { _, _ -> updateScores() }
            rgP2?.setOnCheckedChangeListener { _, _ -> updateScores() }

            val rgRes = findViewById<RadioGroup>(R.id.rgEditResult)
            when (m.matchOutcome) { "WIN" -> rgRes?.check(R.id.rbEditWin); "TIE" -> rgRes?.check(R.id.rbEditTie); else -> rgRes?.check(R.id.rbEditLoss) }
            rgRes?.setOnCheckedChangeListener { _, _ -> updateScores() }

            findViewById<EditText>(R.id.etEditNotes)?.setText(m.matchNotes)

            if (m.matchType == "Auto Only") {
                findViewById<View>(R.id.cardEditTeleop)?.visibility = View.GONE
                findViewById<View>(R.id.cardEditEndgame)?.visibility = View.GONE
                findViewById<View>(R.id.cardEditResult)?.visibility = View.GONE
            } else if (m.matchType == "TeleOp Only") {
                findViewById<View>(R.id.cardEditAuto)?.visibility = View.GONE
            }

            if (m.teamNumber != null) {
                cbL2?.visibility = View.GONE
                findViewById<View>(R.id.layoutEditPark2)?.visibility = View.GONE
            }

            val hapticViews = listOf(R.id.cbEditLeave1, R.id.cbEditLeave2, R.id.rbEditP1None, R.id.rbEditP1Part, R.id.rbEditP1Full, R.id.rbEditP2None, R.id.rbEditP2Part, R.id.rbEditP2Full, R.id.rbEditWin, R.id.rbEditTie, R.id.rbEditLoss)
            hapticViews.forEach { id ->
                findViewById<View>(id)?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
            }

            updateScores()

            findViewById<Button>(R.id.btnSaveEdit)?.setOnClickListener {
                try {
                    lifecycleScope.launch {
                        val updatedMatch = m.copy(
                            autoClassified = counters["AC"] ?: 0, autoOverflow = counters["AO"] ?: 0, autoPattern = counters["AP"] ?: 0,
                            teleopClassified = counters["TC"] ?: 0, teleopOverflow = counters["TO"] ?: 0, teleopDepot = counters["TD"] ?: 0, teleopPattern = counters["TP"] ?: 0,
                            autoLeave1 = cbL1?.isChecked == true, autoLeave2 = cbL2?.isChecked == true,
                            park1Status = when (rgP1?.checkedRadioButtonId) { R.id.rbEditP1Full -> "Full"; R.id.rbEditP1Part -> "Partial"; else -> "None" },
                            park2Status = when (rgP2?.checkedRadioButtonId) { R.id.rbEditP2Full -> "Full"; R.id.rbEditP2Part -> "Partial"; else -> "None" },
                            matchOutcome = when (rgRes?.checkedRadioButtonId) { R.id.rbEditWin -> "WIN"; R.id.rbEditTie -> "TIE"; else -> "LOSS" },
                            matchNotes = findViewById<EditText>(R.id.etEditNotes)?.text?.toString() ?: "",
                            totalScore = findViewById<TextView>(R.id.tvEditTotalScore)?.text?.toString()?.toIntOrNull() ?: 0,
                            rp = findViewById<TextView>(R.id.tvEditRP)?.text?.toString()?.toIntOrNull() ?: 0
                        )
                        db.appDao().updateMatch(updatedMatch)
                        SessionDetailsActivity.matchHolder = null
                        Toast.makeText(this@EditMatchActivity, "Match Updated!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EditMatchActivity, "Eroare la salvare: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare la deschiderea paginii: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateScores() {
        try {
            var autoPts = 0
            if (findViewById<CheckBox>(R.id.cbEditLeave1)?.isChecked == true) autoPts += 3
            if (findViewById<CheckBox>(R.id.cbEditLeave2)?.isChecked == true) autoPts += 3
            autoPts += (counters["AC"] ?: 0) * 3 + (counters["AO"] ?: 0) * 1 + (counters["AP"] ?: 0) * 2

            var telePts = 0
            telePts += (counters["TC"] ?: 0) * 3 + (counters["TO"] ?: 0) * 1 + (counters["TD"] ?: 0) * 1 + (counters["TP"] ?: 0) * 2

            var endPts = 0
            val rg1 = findViewById<RadioGroup>(R.id.rgEditPark1)?.checkedRadioButtonId
            val rg2 = findViewById<RadioGroup>(R.id.rgEditPark2)?.checkedRadioButtonId
            endPts += when (rg1) { R.id.rbEditP1Part -> 5; R.id.rbEditP1Full -> 10; else -> 0 }
            endPts += when (rg2) { R.id.rbEditP2Part -> 5; R.id.rbEditP2Full -> 10; else -> 0 }
            if (rg1 == R.id.rbEditP1Full && rg2 == R.id.rbEditP2Full) endPts += 10

            val total = autoPts + telePts + endPts

            var rp = 0
            rp += when (findViewById<RadioGroup>(R.id.rgEditResult)?.checkedRadioButtonId) { R.id.rbEditWin -> 3; R.id.rbEditTie -> 1; else -> 0 }

            val autoL = (findViewById<CheckBox>(R.id.cbEditLeave1)?.isChecked == true) || (findViewById<CheckBox>(R.id.cbEditLeave2)?.isChecked == true)
            if (autoL && (rg1 != R.id.rbEditP1None || rg2 != R.id.rbEditP2None)) rp += 1

            val prefs = getSharedPreferences("FTCSettings", Context.MODE_PRIVATE)
            val patScore = (counters["AP"] ?: 0) * 2 + (counters["TP"] ?: 0) * 2
            if (patScore >= prefs.getInt("PATTERN_THRESH", 9)) rp += 1

            val totArts = (counters["AC"] ?: 0) + (counters["AO"] ?: 0) + (counters["TC"] ?: 0) + (counters["TO"] ?: 0) + (counters["TD"] ?: 0)
            if (totArts >= prefs.getInt("ARTIFACT_THRESH", 36)) rp += 1

            findViewById<TextView>(R.id.tvEditTotalScore)?.text = total.toString()
            findViewById<TextView>(R.id.tvEditRP)?.text = rp.toString()
        } catch (e: Exception) {
            // Ignorăm erorile silențioase de la calcule
        }
    }
}