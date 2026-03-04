package com.example.ftcdecodescorer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

enum class HistoryState { ROOT, PRACTICE_FOLDERS, SCOUTING_COMPS, SCOUTING_EVENTS, SCOUTING_TEAMS }

class HistoryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var container: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView

    private lateinit var saveCsvLauncher: ActivityResultLauncher<String>
    private var csvContentToSave: String = ""

    private var currentState = HistoryState.ROOT
    private var selectedComp = ""
    private var selectedEvent = ""

    private var allSessions: List<Session> = emptyList()
    private var allMatches: List<MatchResult> = emptyList()

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()
    private val selectedEvents = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        db = AppDatabase.getDatabase(this)
        container = findViewById(R.id.listContainer)
        tvTitle = findViewById(R.id.tvHistoryTitle)
        btnBack = findViewById(R.id.btnBack)

        saveCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(csvContentToSave) }
                Toast.makeText(this, "CSV Exported Successfully!", Toast.LENGTH_LONG).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                    return
                }
                when (currentState) {
                    HistoryState.ROOT -> finish()
                    HistoryState.PRACTICE_FOLDERS, HistoryState.SCOUTING_COMPS -> { currentState = HistoryState.ROOT; renderUI() }
                    HistoryState.SCOUTING_EVENTS -> { currentState = HistoryState.SCOUTING_COMPS; renderUI() }
                    HistoryState.SCOUTING_TEAMS -> { currentState = HistoryState.SCOUTING_EVENTS; renderUI() }
                }
            }
        })
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        loadDataAndRender()
    }

    private fun loadDataAndRender() {
        lifecycleScope.launch {
            allSessions = db.appDao().getAllSessions()
            allMatches = db.appDao().getAllMatches()
            renderUI()
        }
    }

    private fun renderUI() {
        container.removeAllViews()
        updateHeaderUI()

        when (currentState) {
            HistoryState.ROOT -> renderRoot()
            HistoryState.PRACTICE_FOLDERS -> renderPracticeFolders()
            HistoryState.SCOUTING_COMPS -> renderScoutingComps()
            HistoryState.SCOUTING_EVENTS -> renderScoutingEvents()
            HistoryState.SCOUTING_TEAMS -> renderScoutingTeams()
        }

        if (isSelectionMode && (selectedItems.isNotEmpty() || selectedEvents.isNotEmpty())) {
            renderDeleteButton()
        }
    }

    private fun updateHeaderUI() {
        if (isSelectionMode) {
            val count = if (selectedEvents.isNotEmpty()) selectedEvents.size else selectedItems.size
            tvTitle.text = "$count Selected"
            btnBack.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            btnBack.setImageResource(android.R.drawable.ic_menu_revert)
        }
    }

    private fun toggleSelection(sessionId: Int) {
        if (selectedItems.contains(sessionId)) selectedItems.remove(sessionId)
        else selectedItems.add(sessionId)
        if (selectedItems.isEmpty()) exitSelectionMode() else renderUI()
    }

    private fun enterSelectionMode(sessionId: Int) {
        isSelectionMode = true
        selectedItems.clear()
        selectedEvents.clear()
        selectedItems.add(sessionId)
        renderUI()
    }

    private fun toggleEventSelection(eventName: String) {
        if (selectedEvents.contains(eventName)) selectedEvents.remove(eventName)
        else selectedEvents.add(eventName)
        if (selectedEvents.isEmpty()) exitSelectionMode() else renderUI()
    }

    private fun enterEventSelectionMode(eventName: String) {
        isSelectionMode = true
        selectedItems.clear()
        selectedEvents.clear()
        selectedEvents.add(eventName)
        renderUI()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        selectedEvents.clear()
        renderUI()
    }

    private fun renderDeleteButton() {
        val count = if (selectedEvents.isNotEmpty()) selectedEvents.size else selectedItems.size
        val deleteBtn = Button(this).apply {
            text = "DELETE SELECTED ($count)"
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FF3B30"))
                cornerRadius = 24f
            }
            setPadding(40, 40, 40, 40)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 40, 0, 40) }

            setOnClickListener { confirmDeletion() }
        }
        container.addView(deleteBtn)
    }

    private fun confirmDeletion() {
        val count = if (selectedEvents.isNotEmpty()) selectedEvents.size else selectedItems.size
        val typeStr = if (selectedEvents.isNotEmpty()) "event(s)" else "folder(s)"

        val titleView = TextView(this).apply {
            text = "Delete $typeStr"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(60, 60, 60, 20)
        }

        val msgView = TextView(this).apply {
            text = "Are you sure you want to delete $count $typeStr and all associated matches? This cannot be undone."
            textSize = 16f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(60, 0, 60, 20)
        }

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(msgView)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    if (selectedEvents.isNotEmpty()) {
                        selectedEvents.forEach { evName ->
                            val sessionIds = allMatches
                                .filter { it.eventName == evName && it.competitionType == selectedComp }
                                .map { it.parentSessionId }
                                .distinct()

                            sessionIds.forEach { sId ->
                                val sessionToDelete = allSessions.find { it.sessionId == sId }
                                sessionToDelete?.let {
                                    db.appDao().deleteMatchesForSession(it.sessionId)
                                    db.appDao().deleteSession(it)
                                }
                            }
                        }
                    } else {
                        selectedItems.forEach { sessionId ->
                            val sessionToDelete = allSessions.find { it.sessionId == sessionId }
                            sessionToDelete?.let {
                                db.appDao().deleteMatchesForSession(it.sessionId)
                                db.appDao().deleteSession(it)
                            }
                        }
                    }
                    Toast.makeText(this@HistoryActivity, "Deleted $count $typeStr", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    loadDataAndRender()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 40f
        }
        dialog.window?.setBackgroundDrawable(bg)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF3B30"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#888888"))
    }


    private fun renderRoot() {
        if (!isSelectionMode) tvTitle.text = "History & Export"
        container.addView(createCard("Practice Mode", "View local practice runs", android.R.drawable.ic_menu_edit, null,
            onClick = { if(!isSelectionMode) { currentState = HistoryState.PRACTICE_FOLDERS; renderUI() } },
            onLongClick = null
        ))
        container.addView(createCard("Scouting Data", "View & Export Competition Intel", android.R.drawable.ic_menu_sort_by_size, null,
            onClick = { if(!isSelectionMode) { currentState = HistoryState.SCOUTING_COMPS; renderUI() } },
            onLongClick = null
        ))
    }

    // --- RECREAT PERFECT DUPĂ CODUL DECOMPILAT ---
    private fun renderPracticeFolders() {
        if (!isSelectionMode) tvTitle.text = "Practice Folders"
        val scoutingSessionIds = allMatches.filter { it.teamNumber != null }.map { it.parentSessionId }.toSet()
        val practiceSessions = allSessions.filter { it.sessionId !in scoutingSessionIds }

        if (practiceSessions.isEmpty()) {
            container.addView(createEmptyState("No practice folders found."))
            return
        }

        practiceSessions.forEach { session ->
            val isSelected = selectedItems.contains(session.sessionId)
            container.addView(
                createCard("📁 ${session.name}", "Tap to view matches", android.R.drawable.ic_menu_view, isSelected,
                    onClick = {
                        if (isSelectionMode) toggleSelection(session.sessionId)
                        else openSessionDetails(session.sessionId, session.name)
                    },
                    onLongClick = {
                        if (!isSelectionMode) enterSelectionMode(session.sessionId)
                    }
                )
            )
        }
    }

    private fun renderScoutingComps() {
        if (!isSelectionMode) tvTitle.text = "Scouting Modes"
        val allScoutingMatches = allMatches.filter { it.teamNumber != null }

        if (allScoutingMatches.isNotEmpty() && !isSelectionMode) {
            container.addView(createExportButton("EXPORT ALL SCOUTING DATA", "#0A7AFF") { exportCsv(allScoutingMatches, "Global_Scouting_Data.csv", true) })
        }

        val comps = listOf("Scrimmage", "League Meet", "League Tournament", "Regional Championship", "Premier Event", "World Championship")

        comps.forEach { comp ->
            val compMatches = allScoutingMatches.filter { it.competitionType == comp }
            if (compMatches.isNotEmpty()) {
                val eventsCount = compMatches.map { it.eventName }.distinct().size

                val displayTitle = when (comp) {
                    "Scrimmage" -> "Scrimmages"
                    "League Meet" -> "League Meets"
                    "League Tournament" -> "League Tournaments"
                    "Regional Championship" -> "Regional Championships"
                    "Premier Event" -> "Premier Events"
                    "World Championship" -> "World Championship"
                    else -> comp
                }

                container.addView(createCard(displayTitle, "$eventsCount events recorded", android.R.drawable.ic_menu_myplaces, null,
                    onClick = {
                        if (!isSelectionMode) {
                            selectedComp = comp
                            currentState = HistoryState.SCOUTING_EVENTS
                            renderUI()
                        }
                    },
                    onLongClick = null
                ))
            }
        }
    }

    private fun renderScoutingEvents() {
        val displayTitle = when (selectedComp) {
            "Scrimmage" -> "Scrimmages"
            "League Meet" -> "League Meets"
            "League Tournament" -> "League Tournaments"
            "Regional Championship" -> "Regional Championships"
            "Premier Event" -> "Premier Events"
            "World Championship" -> "World Championship"
            else -> selectedComp
        }

        if (!isSelectionMode) tvTitle.text = displayTitle
        val compMatches = allMatches.filter { it.teamNumber != null && it.competitionType == selectedComp }

        if (!isSelectionMode) {
            container.addView(createExportButton("EXPORT ${displayTitle.uppercase()} DATA", "#4CAF50") { exportCsv(compMatches, "${selectedComp.replace(" ", "_")}_Scouting.csv", true) })
        }

        val events = compMatches.map { it.eventName }.distinct()
        events.forEach { eventName ->
            val eventMatches = compMatches.filter { it.eventName == eventName }
            val teamsCount = eventMatches.map { it.teamNumber }.distinct().size

            val isSelected = selectedEvents.contains(eventName)

            container.addView(createCard(eventName, "$teamsCount teams scouted", android.R.drawable.ic_menu_today, isSelected,
                onClick = {
                    if (isSelectionMode) {
                        toggleEventSelection(eventName)
                    } else {
                        selectedEvent = eventName
                        currentState = HistoryState.SCOUTING_TEAMS
                        renderUI()
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) enterEventSelectionMode(eventName)
                }
            ))
        }
    }

    private fun renderScoutingTeams() {
        if (!isSelectionMode) tvTitle.text = selectedEvent
        val eventMatches = allMatches.filter { it.teamNumber != null && it.competitionType == selectedComp && it.eventName == selectedEvent }

        if (!isSelectionMode) {
            container.addView(createExportButton("EXPORT THIS EVENT", "#FF9500") { exportCsv(eventMatches, "${selectedEvent.replace(" ", "_")}_Data.csv", true) })
        }

        val teams = eventMatches.mapNotNull { it.teamNumber }.distinct()
        teams.forEach { team ->
            val teamMatches = eventMatches.filter { it.teamNumber == team }
            val avgTotal = teamMatches.map { it.totalScore }.average().toInt()
            val sessionId = teamMatches.first().parentSessionId

            val isSelected = selectedItems.contains(sessionId)

            container.addView(createCard("Team $team", "${teamMatches.size} matches | Avg Score: $avgTotal", android.R.drawable.ic_menu_info_details, isSelected,
                onClick = {
                    if (isSelectionMode) toggleSelection(sessionId)
                    else openSessionDetails(sessionId, "$selectedComp - $selectedEvent - Team $team")
                },
                onLongClick = {
                    if (!isSelectionMode) enterSelectionMode(sessionId)
                }
            ))
        }
    }

    private fun openSessionDetails(sessionId: Int, name: String) {
        val intent = Intent(this, SessionDetailsActivity::class.java)
        intent.putExtra("SESSION_ID", sessionId)
        intent.putExtra("SESSION_NAME", name)
        startActivity(intent)
    }

    private fun createCard(title: String, subtitle: String, iconRes: Int, isSelected: Boolean?, onClick: () -> Unit, onLongClick: (() -> Unit)?): View {
        val view = layoutInflater.inflate(R.layout.item_list_with_icon, container, false)
        val tvTitleView = view.findViewById<TextView>(R.id.tvText)
        tvTitleView.text = title
        view.findViewById<TextView>(R.id.tvSubtext).text = subtitle
        val imgIcon = view.findViewById<ImageView>(R.id.imgIcon)

        if (isSelected == true) {
            imgIcon.setImageResource(iconRes)
            imgIcon.setColorFilter(Color.parseColor("#0A7AFF"))
            tvTitleView.setTextColor(Color.parseColor("#0A7AFF"))
            view.setBackgroundColor(Color.parseColor("#1A0A7AFF"))
        } else {
            imgIcon.setImageResource(iconRes)
            imgIcon.setColorFilter(Color.WHITE)
            tvTitleView.setTextColor(Color.WHITE)
            view.setBackgroundColor(Color.TRANSPARENT)
        }

        view.setOnClickListener { onClick() }

        if (onLongClick != null) {
            view.setOnLongClickListener {
                onLongClick()
                true
            }
        }

        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 24) }

        return view
    }

    private fun createExportButton(text: String, colorHex: String, onClick: () -> Unit): View {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(colorHex))
            setPadding(40, 40, 40, 40)
            textSize = 16f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }
    }

    private fun createEmptyState(msg: String): View {
        return TextView(this).apply { text = msg; setTextColor(Color.parseColor("#888888")); textSize = 16f; setPadding(20, 40, 20, 40) }
    }

    private fun exportCsv(matches: List<MatchResult>, filename: String, isScouting: Boolean) {
        val csv = StringBuilder()
        fun clean(text: String?) = text?.replace(",", ";")?.replace("\n", " ") ?: ""

        if (!isScouting) {
            csv.append("Match,Artifact Classified,Artifact Overflow,Artifact Depot,Artifact Matching Pattern,Park,Total,Notes\n")
            matches.forEach { m ->
                val parkStatus = "${m.park1Status} / ${m.park2Status}"
                csv.append("${m.matchNumber},${m.autoClassified + m.teleopClassified},${m.autoOverflow + m.teleopOverflow},${m.teleopDepot},${m.autoPattern + m.teleopPattern},$parkStatus,${m.totalScore},${clean(m.matchNotes)}\n")
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
        saveCsvLauncher.launch(filename)
    }
}