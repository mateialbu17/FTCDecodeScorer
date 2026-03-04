package com.example.ftcdecodescorer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SessionDetailsActivity : AppCompatActivity() {
    companion object {
        var matchHolder: MatchResult? = null
    }
    private lateinit var saveCsvLauncher: ActivityResultLauncher<String>
    private var csvContentToSave: String = ""
    private lateinit var db: AppDatabase
    private var sessionId: Int = -1
    override fun onResume() {
        super.onResume()
        if (sessionId != -1) {
            loadMatches() // Reîncarcă lista cu datele noi
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_details)

        sessionId = intent.getIntExtra("SESSION_ID", -1)
        val sessionName = intent.getStringExtra("SESSION_NAME") ?: "Folder Details"

        findViewById<TextView>(R.id.tvTitle).text = sessionName
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        db = AppDatabase.getDatabase(this)

        val btnViewCharts = findViewById<Button>(R.id.btnViewCharts)
        btnViewCharts.setOnClickListener {
            val intentChart = Intent(this@SessionDetailsActivity, ChartActivity::class.java)
            intentChart.putExtra("SESSION_ID", sessionId)
            intentChart.putExtra("SESSION_NAME", sessionName)
            startActivity(intentChart)
        }

        saveCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                    writer.write(csvContentToSave)
                }
                Toast.makeText(this, "CSV Exported Successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        loadMatches()
    }

    private fun loadMatches() {
        val listView = findViewById<ListView>(R.id.lvMatches)
        val tvAverages = findViewById<TextView>(R.id.tvAverages)
        val btnViewCharts = findViewById<Button>(R.id.btnViewCharts)
        val btnExportCsv = findViewById<Button>(R.id.btnExportCsv)

        lifecycleScope.launch {
            val matches = db.appDao().getMatchesForSession(sessionId)

            if (matches.isNotEmpty()) {
                val avgScore = matches.map { it.totalScore }.average().toInt()
                val avgAuto = matches.map { it.autoPoints }.average().toInt()
                val avgTeleop = matches.map { it.teleopPoints }.average().toInt()
                tvAverages.text = "Average Total: $avgScore | Auto: $avgAuto | TeleOp: $avgTeleop"
            } else {
                tvAverages.text = "No matches recorded yet."
            }

            val adapter = object : ArrayAdapter<MatchResult>(this@SessionDetailsActivity, android.R.layout.simple_list_item_2, android.R.id.text1, matches) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)
                    val text2 = view.findViewById<TextView>(android.R.id.text2)
                    val match = getItem(position)

                    val isScouting = match?.teamNumber != null
                    val titlePrefix = if (isScouting) "Match ${match?.matchNumber} (${match?.matchOutcome})" else "Match ${match?.matchNumber}"

                    text1.text = titlePrefix
                    text1.setTextColor(Color.WHITE)
                    text1.textSize = 18f

                    text2.text = "Score: ${match?.totalScore} PTS | RP: ${match?.rp}"
                    text2.setTextColor(Color.parseColor("#AAAAAA"))

                    view.setPadding(20, 20, 20, 20)
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                val m = matches[position]
                showMatchReport(m)
            }

            // --- NOUA LOGICĂ PENTRU BUTONUL DE CHARTS (SELECȚIE MECIURI) ---
            btnViewCharts.setOnClickListener {
                if (matches.isEmpty()) {
                    Toast.makeText(this@SessionDetailsActivity, "Not enough matches to chart.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Desenăm lista de checkbox-uri direct din cod
                val scrollView = ScrollView(this@SessionDetailsActivity)
                val container = LinearLayout(this@SessionDetailsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 20, 60, 20)
                }

                val checkBoxes = mutableListOf<CheckBox>()
                matches.forEach { m ->
                    val cb = CheckBox(this@SessionDetailsActivity).apply {
                        val title = if (m.teamNumber != null) "Team ${m.teamNumber} (M${m.matchNumber})" else "Match ${m.matchNumber}"
                        text = "$title  •  ${m.totalScore} pts"
                        setTextColor(Color.WHITE)
                        buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0A7AFF"))
                        isChecked = true // Selectate by default
                        textSize = 16f
                        setPadding(0, 20, 0, 20)
                    }
                    checkBoxes.add(cb)
                    container.addView(cb)
                }
                scrollView.addView(container)

                val titleView = TextView(this@SessionDetailsActivity).apply {
                    text = "Select Matches for Chart"
                    textSize = 20f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(60, 60, 60, 20)
                }

                val dialog = AlertDialog.Builder(this@SessionDetailsActivity)
                    .setCustomTitle(titleView)
                    .setView(scrollView)
                    .setPositiveButton("GENERATE", null) // Setăm null temporar ca să nu se închidă singur
                    .setNegativeButton("CANCEL", null)
                    .create()

                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1E1E"))
                    cornerRadius = 40f
                }
                dialog.window?.setBackgroundDrawable(bg)
                dialog.show()

                // Suprascriem butonul "GENERATE" ca să verificăm dacă a bifat măcar un meci
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    setTextColor(Color.parseColor("#0A7AFF"))
                    setOnClickListener {
                        val selectedIndices = arrayListOf<Int>()
                        checkBoxes.forEachIndexed { i, cb -> if (cb.isChecked) selectedIndices.add(i) }

                        if (selectedIndices.isEmpty()) {
                            Toast.makeText(this@SessionDetailsActivity, "Please select at least one match!", Toast.LENGTH_SHORT).show()
                        } else {
                            val intentChart = Intent(this@SessionDetailsActivity, ChartActivity::class.java)
                            intentChart.putExtra("SESSION_ID", sessionId)
                            intentChart.putExtra("SESSION_NAME", intent.getStringExtra("SESSION_NAME") ?: "Folder Details")
                            intentChart.putIntegerArrayListExtra("SELECTED_INDICES", selectedIndices)
                            startActivity(intentChart)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#888888"))
            }

            btnExportCsv.setOnClickListener {
                if (matches.isEmpty()) {
                    Toast.makeText(this@SessionDetailsActivity, "No data to export.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val csv = java.lang.StringBuilder()
                fun clean(text: String?) = text?.replace(",", ";")?.replace("\n", " ") ?: ""

                val isScouting = matches.first().teamNumber != null
                if (!isScouting) {
                    csv.append("Match,Artifact Classified,Artifact Overflow,Artifact Depot,Artifact Matching Pattern,Park,Total,Notes\n")
                    matches.forEach { m ->
                        val artClass = m.autoClassified + m.teleopClassified
                        val artOver = m.autoOverflow + m.teleopOverflow
                        val artPattern = m.autoPattern + m.teleopPattern
                        val parkStatus = "${m.park1Status} / ${m.park2Status}"
                        csv.append("${m.matchNumber},$artClass,$artOver,${m.teleopDepot},$artPattern,$parkStatus,${m.totalScore},${clean(m.matchNotes)}\n")
                    }
                } else {
                    csv.append("Competition,Event Name,Team,Match,Type,Outcome,Total Score,RP,Auto Pts,TeleOp Pts,End Pts,Auto C,Auto O,Auto Pat,Leave 1,Leave 2,Tele C,Tele O,Tele D,Tele Pat,Park 1,Park 1 Lvl,Park 2,Park 2 Lvl,Drivetrain,Drive Notes,Intake Notes,Sort,Sort Notes,Turret,Hood,Outtake Notes,Close Acc,Far Acc,Playstyle (1-10),Climb,Climb Notes,General Notes\n")
                    matches.forEach { m ->
                        csv.append("${m.competitionType},${clean(m.eventName)},${m.teamNumber},${m.matchNumber},${m.matchType},${m.matchOutcome},${m.totalScore},${m.rp},${m.autoPoints},${m.teleopPoints},${m.endgamePoints},")
                        csv.append("${m.autoClassified},${m.autoOverflow},${m.autoPattern},${m.autoLeave1},${m.autoLeave2},")
                        csv.append("${m.teleopClassified},${m.teleopOverflow},${m.teleopDepot},${m.teleopPattern},")
                        csv.append("${m.park1Status},${m.park1Level},${m.park2Status},${m.park2Level},")
                        csv.append("${m.mechDrivetrain},${clean(m.mechDrivetrainNotes)},${clean(m.mechIntakeNotes)},")
                        csv.append("${m.mechSort},${clean(m.mechSortNotes)},${m.mechTurret},${m.mechHood},${clean(m.mechOuttakeNotes)},")
                        csv.append("${m.mechCloseAccuracy},${m.mechFarAccuracy},${m.mechPlaystyleDial},")
                        csv.append("${m.mechClimb},${clean(m.mechClimbNotes)},${clean(m.matchNotes)}\n")
                    }
                }

                csvContentToSave = csv.toString()
                val sessionName = intent.getStringExtra("SESSION_NAME") ?: "Folder"
                val safeName = sessionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                saveCsvLauncher.launch("${safeName}_export.csv")
            }
        }
    }

    private fun showMatchReport(m: MatchResult) {
        val title = if (m.teamNumber != null) "Team ${m.teamNumber} - Match ${m.matchNumber}" else "Match ${m.matchNumber}"

        val report = java.lang.StringBuilder()
        report.append("🏆 SCORE: ${m.totalScore} | RP: ${m.rp} | OUTCOME: ${m.matchOutcome}\n")
        if (m.teamNumber != null && m.eventName != "Unknown") {
            report.append("Event: ${m.competitionType} - ${m.eventName}\n")
        }
        report.append("Type: ${m.matchType}\n----------------------------------\n\n")

        if (m.teamNumber != null) {
            report.append("⚙️ MECHANISMS\n")
            report.append("• Drivetrain: ${m.mechDrivetrain}\n")
            if (!m.mechDrivetrainNotes.isNullOrEmpty()) report.append("  Note: ${m.mechDrivetrainNotes}\n")
            if (!m.mechIntakeNotes.isNullOrEmpty()) report.append("• Intake Note: ${m.mechIntakeNotes}\n")
            report.append("• Sorting: ${if(m.mechSort) "Yes" else "No"}\n")
            if (!m.mechSortNotes.isNullOrEmpty()) report.append("  Note: ${m.mechSortNotes}\n")
            report.append("• Outtake: Turret(${if(m.mechTurret) "Yes" else "No"}), Hood(${if(m.mechHood) "Yes" else "No"})\n")
            report.append("  - Close Accuracy: ${m.mechCloseAccuracy}/5 | Far Accuracy: ${m.mechFarAccuracy}/5\n")
            report.append("  - Playstyle (1=Close, 10=Far): ${m.mechPlaystyleDial}/10\n")
            if (!m.mechOuttakeNotes.isNullOrEmpty()) report.append("  Note: ${m.mechOuttakeNotes}\n")

            report.append("• Climb: ${if(m.mechClimb) "Yes" else "No"}\n")
            if (!m.mechClimbNotes.isNullOrEmpty()) report.append("  Note: ${m.mechClimbNotes}\n----------------------------------\n\n")
        }

        report.append("🟣 AUTONOMOUS: ${m.autoPoints} pts\n• Samples: ${m.autoClassified} (C) / ${m.autoOverflow} (O)\n• Pattern Bonus: ${m.autoPattern}\n• Leave: R1(${if(m.autoLeave1) "Yes" else "No"}), R2(${if(m.autoLeave2) "Yes" else "No"})\n\n")
        report.append("🟢 TELEOP: ${m.teleopPoints} pts\n• Samples: ${m.teleopClassified} (C) / ${m.teleopOverflow} (O)\n• Depot: ${m.teleopDepot}\n• Pattern Bonus: ${m.teleopPattern}\n\n")

        report.append("🟠 ENDGAME: ${m.endgamePoints} pts\n")
        val p1Lvl = if (!m.park1Level.isNullOrEmpty() && m.park1Level != "N/A") " (${m.park1Level})" else ""
        report.append("• Robot 1 Park: ${m.park1Status}$p1Lvl\n")
        val p2Lvl = if (!m.park2Level.isNullOrEmpty() && m.park2Level != "N/A") " (${m.park2Level})" else ""
        report.append("• Robot 2 Park: ${m.park2Status}$p2Lvl\n\n")

        if (!m.matchNotes.isNullOrEmpty()) {
            report.append("📝 GENERAL NOTES:\n${m.matchNotes}\n")
        }

        val titleView = TextView(this@SessionDetailsActivity).apply {
            text = title
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(60, 60, 60, 20)
        }

        val msgView = TextView(this@SessionDetailsActivity).apply {
            text = report.toString()
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(60, 0, 60, 20)
        }

        val scrollView = ScrollView(this@SessionDetailsActivity)
        scrollView.addView(msgView)

        val dialog = AlertDialog.Builder(this@SessionDetailsActivity)
            .setCustomTitle(titleView)
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit Match") { _, _ ->
                matchHolder = m
                startActivity(Intent(this@SessionDetailsActivity, EditMatchActivity::class.java))
            }
            .setNegativeButton("Delete") { _, _ ->
            // Customizare Pop-up Confirmare Ștergere
            val confirmTitleView = TextView(this@SessionDetailsActivity).apply {
                text = "Delete Match"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(60, 60, 60, 20)
            }

            val confirmMsgView = TextView(this@SessionDetailsActivity).apply {
                text = "Are you sure you want to permanently delete this match?"
                textSize = 16f
                setTextColor(Color.parseColor("#CCCCCC"))
                setPadding(60, 0, 60, 40)
            }

            val confirmDialog = AlertDialog.Builder(this@SessionDetailsActivity)
                .setCustomTitle(confirmTitleView)
                .setView(confirmMsgView)
                .setPositiveButton("DELETE") { _, _ ->
                    lifecycleScope.launch {
                        db.appDao().deleteMatch(m)
                        Toast.makeText(this@SessionDetailsActivity, "Match deleted!", Toast.LENGTH_SHORT).show()
                        loadMatches() // Refresh automat la ecran
                    }
                }
                .setNegativeButton("CANCEL", null)
                .create()

            // Aplicăm fundalul rotunjit Dark Mode
            val confirmBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = 40f
            }
            confirmDialog.window?.setBackgroundDrawable(confirmBg)
            confirmDialog.show()

            // Culori pentru butoanele confirmării
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF3B30")) // Roșu de ștergere
            confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#888888")) // Gri neutru
        }
            .create()

        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 40f
        }
        dialog.window?.setBackgroundDrawable(bg)
        dialog.show()

        // Culori One UI pentru butoane
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#888888"))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#0A7AFF"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF3B30")) // Roșu de ștergere
    }

    // --- LOGICA DE EDITARE LIVE ---
    // --- LOGICA DE EDITARE LIVE (REPARATĂ) ---
    // --- LOGICA DE EDITARE LIVE (CORECTATĂ) ---
    // --- LOGICA DE EDITARE LIVE ---

}