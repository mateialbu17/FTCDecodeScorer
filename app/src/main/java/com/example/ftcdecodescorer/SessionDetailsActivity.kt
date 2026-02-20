package com.example.ftcdecodescorer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class SessionDetailsActivity : AppCompatActivity() {

    private lateinit var saveCsvLauncher: ActivityResultLauncher<String>
    private var csvContentToSave: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_details)

        val sessionId = intent.getIntExtra("SESSION_ID", -1)
        val sessionName = intent.getStringExtra("SESSION_NAME") ?: "Folder Details"

        findViewById<TextView>(R.id.tvTitle).text = sessionName
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val db = AppDatabase.getDatabase(this)
        val listView = findViewById<ListView>(R.id.lvMatches)
        val tvAverages = findViewById<TextView>(R.id.tvAverages)
        val btnViewCharts = findViewById<Button>(R.id.btnViewCharts)
        val btnExportCsv = findViewById<Button>(R.id.btnExportCsv)

        saveCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                    writer.write(csvContentToSave)
                }
                Toast.makeText(this, "CSV Saved Successfully!", Toast.LENGTH_LONG).show()
            }
        }

        btnViewCharts.setOnClickListener {
            val intent = Intent(this, ChartActivity::class.java)
            intent.putExtra("SESSION_ID", sessionId)
            intent.putExtra("SESSION_NAME", sessionName)
            startActivity(intent)
        }

        lifecycleScope.launch {
            val matches = db.appDao().getMatchesForSession(sessionId)

            if (matches.isNotEmpty()) {
                val avgTot = matches.map { it.totalScore }.average().toInt()
                val avgAuto = matches.map { it.autoPoints }.average().toInt()
                val avgTele = matches.map { it.teleopPoints }.average().toInt()
                val avgEnd = matches.map { it.endgamePoints }.average().toInt()
                val avgRp = matches.map { it.rp }.average()

                tvAverages.text = "Total Score: $avgTot pts\nAuto: $avgAuto | TeleOp: $avgTele | End: $avgEnd\nRP Average: ${String.format(Locale.US, "%.1f", avgRp)}"
            } else {
                tvAverages.text = "No matches recorded yet."
                btnViewCharts.isEnabled = false
                btnExportCsv.isEnabled = false
            }

            btnExportCsv.setOnClickListener {
                val isScouting = matches.firstOrNull()?.teamNumber?.isNotEmpty() == true
                val csv = StringBuilder()

                fun clean(text: String?) = text?.replace(",", ";")?.replace("\n", " ") ?: ""

                if (!isScouting) {
                    csv.append("Match,Artifact Classified,Artifact Overflow,Artifact Depot,Artifact Matching Pattern,Park,Total,Notes\n")
                    matches.forEach { m ->
                        val artClass = m.autoClassified + m.teleopClassified
                        val artOver = m.autoOverflow + m.teleopOverflow
                        val artDepot = m.teleopDepot
                        val artPattern = m.autoPattern + m.teleopPattern
                        val parkStatus = "${m.park1Status} / ${m.park2Status}"
                        val obs = clean(m.matchNotes)

                        csv.append("${m.matchNumber},$artClass,$artOver,$artDepot,$artPattern,$parkStatus,${m.totalScore},$obs\n")
                    }
                } else {
                    csv.append("Team,Match,Type,Outcome,Total Score,RP,Auto Pts,TeleOp Pts,End Pts,Auto C,Auto O,Auto Pat,Leave 1,Leave 2,Tele C,Tele O,Tele D,Tele Pat,Park 1,Park 1 Lvl,Park 2,Park 2 Lvl,Drivetrain,Drive Notes,Intake Notes,Sort,Sort Notes,Turret,Hood,Outtake Notes,Climb,Climb Notes,General Notes\n")
                    matches.forEach { m ->
                        csv.append("${m.teamNumber},${m.matchNumber},${m.matchType},${m.matchOutcome},${m.totalScore},${m.rp},${m.autoPoints},${m.teleopPoints},${m.endgamePoints},")
                        csv.append("${m.autoClassified},${m.autoOverflow},${m.autoPattern},${m.autoLeave1},${m.autoLeave2},")
                        csv.append("${m.teleopClassified},${m.teleopOverflow},${m.teleopDepot},${m.teleopPattern},")
                        csv.append("${m.park1Status},${m.park1Level},${m.park2Status},${m.park2Level},")
                        csv.append("${m.mechDrivetrain},${clean(m.mechDrivetrainNotes)},${clean(m.mechIntakeNotes)},")
                        csv.append("${m.mechSort},${clean(m.mechSortNotes)},${m.mechTurret},${m.mechHood},${clean(m.mechOuttakeNotes)},")
                        csv.append("${m.mechClimb},${clean(m.mechClimbNotes)},${clean(m.matchNotes)}\n")
                    }
                }

                csvContentToSave = csv.toString()
                saveCsvLauncher.launch("${sessionName.replace(" ", "_")}_export.csv")
            }

            val adapter = object : ArrayAdapter<MatchResult>(this@SessionDetailsActivity, R.layout.item_list_with_icon, matches) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list_with_icon, parent, false)
                    val tvText = view.findViewById<TextView>(R.id.tvText)
                    val tvSubtext = view.findViewById<TextView>(R.id.tvSubtext)
                    val imgIcon = view.findViewById<ImageView>(R.id.imgIcon)
                    val match = getItem(position)

                    if (match != null) {
                        if (!match.teamNumber.isNullOrEmpty()) {
                            tvText.text = "Team ${match.teamNumber} - Match ${match.matchNumber}"
                            tvSubtext?.text = "Score: ${match.totalScore} | RP: ${match.rp} | ${match.matchType}"
                        } else {
                            tvText.text = "Match #${match.matchNumber} (${match.matchType})"
                            tvSubtext?.text = "Score: ${match.totalScore} | RP: ${match.rp}"
                        }

                        when (match.matchOutcome) {
                            "WIN" -> { imgIcon.setImageResource(android.R.drawable.btn_star_big_on); imgIcon.setColorFilter(Color.parseColor("#4CAF50")) }
                            "TIE" -> { imgIcon.setImageResource(android.R.drawable.ic_menu_help); imgIcon.setColorFilter(Color.parseColor("#FFC107")) }
                            else -> { imgIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); imgIcon.setColorFilter(Color.parseColor("#F44336")) }
                        }
                    }
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                val m = matches[position]
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(m.timestamp)
                val title = if (!m.teamNumber.isNullOrEmpty()) "Team ${m.teamNumber} - Match ${m.matchNumber}" else "Match #${m.matchNumber}"
                val report = StringBuilder()

                report.append("RESULT: ${m.matchOutcome}\nTYPE: ${m.matchType}\n----------------------------------\n")
                report.append("🏆 TOTAL SCORE: ${m.totalScore}\n⭐ RP EARNED: ${m.rp}\n----------------------------------\n\n")

                if (!m.mechDrivetrain.isNullOrEmpty()) {
                    report.append("🔧 MECHANISMS REPORT\n• Drivetrain: ${m.mechDrivetrain}\n")
                    if (!m.mechDrivetrainNotes.isNullOrEmpty()) report.append("  Note: ${m.mechDrivetrainNotes}\n")
                    report.append("• Intake Note: ${m.mechIntakeNotes ?: "-"}\n")
                    report.append("• Sort: ${if(m.mechSort) "Yes" else "No"}\n")
                    if (!m.mechSortNotes.isNullOrEmpty()) report.append("  Note: ${m.mechSortNotes}\n")
                    report.append("• Outtake: Turret(${if(m.mechTurret) "Yes" else "No"}), Hood(${if(m.mechHood) "Yes" else "No"})\n")
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

                if (!m.matchNotes.isNullOrEmpty()) { report.append("📝 MATCH NOTES:\n${m.matchNotes}\n\n") }
                report.append("📅 Date: $dateStr")

                AlertDialog.Builder(this@SessionDetailsActivity)
                    .setTitle(title)
                    .setMessage(report.toString())
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }
}