package com.example.ftcdecodescorer
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

enum class MatchState { PRE_MATCH, AUTO, TRANSITION, TELEOP, END }

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val counters = mutableMapOf<Int, Int>()
    private var sessionList = mutableListOf<Session>()
    private var selectedSessionId: Int = -1

    private var matchState = MatchState.PRE_MATCH
    private var timer: CountDownTimer? = null
    private var matchAudioPlayer: MediaPlayer? = null
    private var isScoutingMode = false
    private var currentMatchType = "Full Match"

    private val sequenceHandler = Handler(Looper.getMainLooper())
    private var isFirstLaunch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            try { db.appDao().updateRegionalFolders() } catch (e: Exception) { e.printStackTrace() }
        }

        isFirstLaunch = true
        setupCounters()
        setupHapticFeedback()
        setupTimerLogic()
        setupSpinner()
        setupMatchTypeSelector()
        setupModeToggle()
        setupRobotModeSelector()
        setupDrivetrainSpinner()
        setupCompetitionSpinner()

        applyModernDropdownStyle(
            findViewById(R.id.spinnerSessions),
            findViewById(R.id.spinnerMatchType),
            findViewById(R.id.spinnerCompetitionType),
            findViewById(R.id.spinDrivetrain)
        )

        findViewById<Button>(R.id.btnViewHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnRandomize).setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            randomizeMotif()
        }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (isScoutingMode) {
                val teamNum = findViewById<EditText>(R.id.etTeamNumber).text.toString()
                val matchNum = findViewById<EditText>(R.id.etMatchNumber).text.toString()
                if (teamNum.isNotEmpty() && matchNum.isNotEmpty()) {
                    saveMatch(teamNum, matchNum)
                } else {
                    Toast.makeText(this, "Please enter Team & Match #", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (selectedSessionId == -1) {
                    Toast.makeText(this, "Please select a folder!", Toast.LENGTH_SHORT).show()
                } else {
                    saveMatch(null, null)
                }
            }
        }

        findViewById<Button>(R.id.btnDiscard).setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            val titleView = TextView(this).apply {
                text = "Discard Match?"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(60, 60, 60, 20)
            }
            val msgView = TextView(this).apply {
                text = "Are you sure you want to clear current match data?"
                textSize = 16f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(60, 0, 60, 20)
            }

            val dialog = AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setView(msgView)
                .setPositiveButton("Discard") { _, _ -> resetAll() }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_oneui)
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF3B30"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#0A7AFF"))
        }

        val updateListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<CheckBox>(R.id.cbAutoLeave1)?.setOnCheckedChangeListener(updateListener)
        findViewById<CheckBox>(R.id.cbAutoLeave2)?.setOnCheckedChangeListener(updateListener)
        findViewById<RadioGroup>(R.id.rgPark1)?.setOnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<RadioGroup>(R.id.rgPark2)?.setOnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<RadioGroup>(R.id.rgMatchResult)?.setOnCheckedChangeListener { _, _ -> updateDisplay() }

        enableScoringControls(false)
        updateTimerUI("2:30", "PRE-MATCH", "#888888")
        randomizeMotif()
        updateUIForMatchType()
    }

    override fun onResume() {
        super.onResume()
        updateDisplay()
        findViewById<Spinner>(R.id.spinnerSessions)?.let { spinner ->
            lifecycleScope.launch { refreshSessionList(spinner) }
        }
    }

    private fun updateTimerPreview() {
        when (currentMatchType) {
            "Auto Only" -> updateTimerUI("0:30", "PRE-MATCH", "#888888")
            "TeleOp Only" -> updateTimerUI("2:00", "PRE-MATCH", "#888888")
            else -> updateTimerUI("2:30", "PRE-MATCH", "#888888")
        }
    }

    // --- RECREAT PERFECT DUPĂ CODUL TĂU ORIGINAL ---
    private fun getWhiteTextAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, null, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 18f
                view.isSingleLine = false
                val scale = context.resources.displayMetrics.density
                val padV = (10 * scale + 0.5f).toInt()
                val padH = (12 * scale + 0.5f).toInt()
                view.setPadding(padH, padV, padH, padV)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val text = getItem(position)
                if (text == "No folders yet...") {
                    val emptyView = TextView(context).apply {
                        height = 0
                        visibility = View.GONE
                        setPadding(0, 0, 0, 0)
                    }
                    return emptyView
                }
                val view = super.getDropDownView(position, null, parent) as TextView
                view.setTextColor(Color.WHITE)
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                view.setBackgroundResource(outValue.resourceId)
                view.textSize = 20f
                val scale = context.resources.displayMetrics.density
                val padV = (16 * scale + 0.5f).toInt()
                val padH = (24 * scale + 0.5f).toInt()
                view.setPadding(padH, padV, padH, padV)
                return view
            }
        }
    }

    private fun applyModernDropdownStyle(vararg spinners: Spinner?) {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 40f
        }
        for (spinner in spinners) {
            spinner?.setPopupBackgroundDrawable(bg)
        }
    }

    private fun setupDrivetrainSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinDrivetrain)
        val items = listOf("Mecanum", "Swerve") // DOAR ASTEA DOUĂ!
        spinner.adapter = getWhiteTextAdapter(items)
    }

    private fun refreshEventNameDropdown(compType: String) {
        lifecycleScope.launch {
            val pastEvents = db.appDao().getEventNamesForCompType(compType)
            val actv = findViewById<AutoCompleteTextView>(R.id.actvEventName)
            if (actv != null && pastEvents.isNotEmpty()) {
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, pastEvents)
                actv.setAdapter(adapter)
            }
        }
    }

    private fun setupCompetitionSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerCompetitionType)
        val items = listOf("Scrimmage", "League Meet", "League Tournament", "Regional Championship", "Premier Event", "World Championship")
        spinner.adapter = getWhiteTextAdapter(items)

        var previousCompType = ""

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedComp = items[position]
                val actv = findViewById<AutoCompleteTextView>(R.id.actvEventName)

                if (previousCompType.isNotEmpty() && previousCompType != selectedComp) {
                    actv?.text?.clear()
                }
                previousCompType = selectedComp

                lifecycleScope.launch {
                    val events = db.appDao().getEventNamesForCompType(selectedComp)
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, events)
                    actv?.setAdapter(adapter)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupMatchTypeSelector() {
        val spinner = findViewById<Spinner>(R.id.spinnerMatchType)
        val items = listOf("Full Match", "Auto Only", "TeleOp Only")
        spinner.adapter = getWhiteTextAdapter(items)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMatchType = items[position]
                updateTimerPreview()
                updateUIForMatchType()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUIForMatchType() {
        val cardAuto = findViewById<View>(R.id.cardAuto)
        val cardTeleop = findViewById<View>(R.id.cardTeleop)
        val cardEndgame = findViewById<View>(R.id.cardEndgame)
        val cardMatchResult = findViewById<View>(R.id.cardMatchResult)

        when(currentMatchType) {
            "Auto Only" -> {
                cardAuto?.visibility = View.VISIBLE
                cardTeleop?.visibility = View.GONE
                cardEndgame?.visibility = View.GONE
                cardMatchResult?.visibility = View.GONE
            }
            "TeleOp Only" -> {
                cardAuto?.visibility = View.GONE
                cardTeleop?.visibility = View.VISIBLE
                cardEndgame?.visibility = View.VISIBLE
                cardMatchResult?.visibility = View.VISIBLE
            }
            else -> { // Full Match
                cardAuto?.visibility = View.VISIBLE
                cardTeleop?.visibility = View.VISIBLE
                cardEndgame?.visibility = View.VISIBLE
                cardMatchResult?.visibility = View.VISIBLE
            }
        }
    }

    private fun setupModeToggle() {
        val rgMode = findViewById<RadioGroup>(R.id.rgAppMode)
        val layoutPractice = findViewById<LinearLayout>(R.id.layoutPracticeHeader)
        val layoutScouting = findViewById<LinearLayout>(R.id.layoutScoutingHeader)
        val layoutTimer = findViewById<LinearLayout>(R.id.layoutTimerSection)
        val layoutTimerControls = findViewById<LinearLayout>(R.id.layoutTimerControls)
        val cardMechanisms = findViewById<View>(R.id.cardMechanisms)

        val rgRobotCount = findViewById<RadioGroup>(R.id.rgRobotCount)
        val rbOneBot = findViewById<RadioButton>(R.id.rbOneBot)
        val cardRandomization = findViewById<View>(R.id.cardRandomization)
        val rgPark1Level = findViewById<View>(R.id.rgPark1Level)
        val rgPark2Level = findViewById<View>(R.id.rgPark2Level)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbModeScouting) {
                isScoutingMode = true
                layoutPractice?.visibility = View.GONE
                layoutScouting?.visibility = View.VISIBLE
                layoutTimer?.visibility = View.GONE
                layoutTimerControls?.visibility = View.GONE

                rgRobotCount?.visibility = View.GONE
                rbOneBot?.isChecked = true

                cardRandomization?.visibility = View.GONE
                cardMechanisms?.visibility = View.VISIBLE

                rgPark1Level?.visibility = View.VISIBLE
                rgPark2Level?.visibility = View.VISIBLE

                enableScoringControls(true)
            } else {
                isScoutingMode = false
                layoutPractice?.visibility = View.VISIBLE
                layoutScouting?.visibility = View.GONE
                layoutTimer?.visibility = View.VISIBLE
                layoutTimerControls?.visibility = View.VISIBLE

                rgRobotCount?.visibility = View.VISIBLE
                cardRandomization?.visibility = View.VISIBLE
                cardMechanisms?.visibility = View.GONE

                rgPark1Level?.visibility = View.GONE
                rgPark2Level?.visibility = View.GONE

                enableScoringControls(false)
                updateTimerUI("2:30", "PRE-MATCH", "#888888")
            }
        }
    }

    // --- RECREAT EXACT CUM ERA ÎN FIȘIERUL DECOMPILAT ---
    private fun saveMatch(scoutingTeam: String?, scoutingMatchNum: String?) {
        try {
            val total = calculateTotal()
            val rp = calculateRP()

            val park1Str = when (findViewById<RadioGroup>(R.id.rgPark1)?.checkedRadioButtonId) {
                R.id.rbPark1Full -> "Full"; R.id.rbPark1Partial -> "Partial"; else -> "None"
            }
            val park2Str = when (findViewById<RadioGroup>(R.id.rgPark2)?.checkedRadioButtonId) {
                R.id.rbPark2Full -> "Full"; R.id.rbPark2Partial -> "Partial"; else -> "None"
            }
            val resultStr = when (findViewById<RadioGroup>(R.id.rgMatchResult)?.checkedRadioButtonId) {
                R.id.rbWin -> "WIN"; R.id.rbTie -> "TIE"; else -> "LOSS"
            }

            var mechDrive: String? = null
            var mechDriveNote: String? = null
            var mechIntakeNote: String? = null
            var mechSort = false
            var mechSortNote: String? = null
            var mechTurret = false
            var mechHood = false
            var mechOuttakeNote: String? = null
            var mechClimb = false
            var mechClimbNote: String? = null
            var p1LevelStr: String? = null
            var p2LevelStr: String? = null

            var closeAcc = 0
            var farAcc = 0
            var playstyle = 5

            if (isScoutingMode) {
                mechDrive = findViewById<Spinner>(R.id.spinDrivetrain)?.selectedItem?.toString() ?: "Unknown"
                mechDriveNote = findViewById<EditText>(R.id.etDrivetrainNotes)?.text?.toString()
                mechIntakeNote = findViewById<EditText>(R.id.etIntakeNotes)?.text?.toString()
                mechSort = findViewById<RadioButton>(R.id.rbSortYes)?.isChecked ?: false
                mechSortNote = findViewById<EditText>(R.id.etSortNotes)?.text?.toString()
                mechTurret = findViewById<CheckBox>(R.id.cbTurret)?.isChecked ?: false
                mechHood = findViewById<CheckBox>(R.id.cbHood)?.isChecked ?: false
                mechOuttakeNote = findViewById<EditText>(R.id.etOuttakeNotes)?.text?.toString()
                mechClimb = findViewById<RadioButton>(R.id.rbClimbYes)?.isChecked ?: false
                mechClimbNote = findViewById<EditText>(R.id.etClimbNotes)?.text?.toString()

                p1LevelStr = when (findViewById<RadioGroup>(R.id.rgPark1Level)?.checkedRadioButtonId) {
                    R.id.rbPark1LevelUp -> "Up"; R.id.rbPark1LevelDown -> "Down"; else -> "N/A"
                }
                p2LevelStr = when (findViewById<RadioGroup>(R.id.rgPark2Level)?.checkedRadioButtonId) {
                    R.id.rbPark2LevelUp -> "Up"; R.id.rbPark2LevelDown -> "Down"; else -> "N/A"
                }

                closeAcc = when (findViewById<RadioGroup>(R.id.rgCloseAcc)?.checkedRadioButtonId) {
                    R.id.rbC1 -> 1; R.id.rbC2 -> 2; R.id.rbC3 -> 3; R.id.rbC4 -> 4; R.id.rbC5 -> 5; else -> 0
                }
                farAcc = when (findViewById<RadioGroup>(R.id.rgFarAcc)?.checkedRadioButtonId) {
                    R.id.rbF1 -> 1; R.id.rbF2 -> 2; R.id.rbF3 -> 3; R.id.rbF4 -> 4; R.id.rbF5 -> 5; else -> 0
                }
                playstyle = (findViewById<SeekBar>(R.id.sbPlaystyle)?.progress ?: 4) + 1
            }

            val generalNotes = findViewById<EditText>(R.id.etMatchNotes)?.text?.toString()

            val compType = if (isScoutingMode) findViewById<Spinner>(R.id.spinnerCompetitionType)?.selectedItem?.toString() ?: "None" else "None"
            val evtName = if (isScoutingMode) findViewById<AutoCompleteTextView>(R.id.actvEventName)?.text?.toString()?.ifEmpty { "Unknown" } ?: "Unknown" else "None"

            lifecycleScope.launch {
                try {
                    var baseName = if (isScoutingMode) {
                        "$compType - $evtName - ${scoutingTeam ?: "Unknown"}"
                    } else {
                        sessionList.find { it.sessionId == selectedSessionId }?.name ?: "Default"
                    }

                    baseName = baseName.removeSuffix(" - Auto Only").removeSuffix(" - TeleOp Only").trim()

                    val targetName = if (currentMatchType != "Full Match") {
                        "$baseName - $currentMatchType"
                    } else {
                        baseName
                    }

                    val existingSession = db.appDao().getSessionByName(targetName)
                    val finalSessId = if (existingSession == null) {
                        db.appDao().insertSession(Session(name = targetName)).toInt()
                    } else {
                        existingSession.sessionId
                    }

                    val finalMatchNum: String = if (isScoutingMode) {
                        scoutingMatchNum ?: "1"
                    } else {
                        val existingMatches = db.appDao().getMatchesForSession(finalSessId)
                        (existingMatches.size + 1).toString()
                    }

                    val match = MatchResult(
                        parentSessionId = finalSessId,
                        matchNumber = finalMatchNum,
                        teamNumber = scoutingTeam,
                        matchType = currentMatchType,
                        competitionType = compType,
                        eventName = evtName,
                        totalScore = total,
                        autoPoints = calculateAuto(),
                        teleopPoints = calculateTeleop(),
                        endgamePoints = calculateEndgame(),
                        rp = rp,
                        autoClassified = counters[R.id.incAutoClassified] ?: 0,
                        autoOverflow = counters[R.id.incAutoOverflow] ?: 0,
                        autoPattern = counters[R.id.incAutoPattern] ?: 0,
                        autoLeave1 = findViewById<CheckBox>(R.id.cbAutoLeave1)?.isChecked ?: false,
                        autoLeave2 = findViewById<CheckBox>(R.id.cbAutoLeave2)?.isChecked ?: false,
                        teleopClassified = counters[R.id.incTeleopClassified] ?: 0,
                        teleopOverflow = counters[R.id.incTeleopOverflow] ?: 0,
                        teleopDepot = counters[R.id.incTeleopDepot] ?: 0,
                        teleopPattern = counters[R.id.incTeleopPattern] ?: 0,
                        park1Status = park1Str,
                        park2Status = park2Str,
                        matchOutcome = resultStr,
                        matchNotes = generalNotes,
                        park1Level = p1LevelStr,
                        park2Level = p2LevelStr,
                        mechDrivetrain = mechDrive,
                        mechDrivetrainNotes = mechDriveNote,
                        mechIntakeNotes = mechIntakeNote,
                        mechSort = mechSort,
                        mechSortNotes = mechSortNote,
                        mechTurret = mechTurret,
                        mechHood = mechHood,
                        mechOuttakeNotes = mechOuttakeNote,
                        mechCloseAccuracy = closeAcc,
                        mechFarAccuracy = farAcc,
                        mechPlaystyleDial = playstyle,
                        mechClimb = mechClimb,
                        mechClimbNotes = mechClimbNote
                    )

                    db.appDao().insertMatch(match)

                    runOnUiThread {
                        findViewById<Spinner>(R.id.spinnerSessions)?.let { refreshSessionList(it) }
                        if (isScoutingMode) {
                            refreshEventNameDropdown(compType)
                        }
                        Toast.makeText(this@MainActivity, "Saved to '$targetName'", Toast.LENGTH_SHORT).show()
                        resetAll()
                    }
                } catch (dbError: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "DB Error: ${dbError.message}", Toast.LENGTH_LONG).show() }
                }
            }
        } catch (uiError: Exception) {
            Toast.makeText(this, "UI Error: ${uiError.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetAll() {
        stopTimerSequence()
        counters.keys.forEach { counters[it] = 0 }

        val viewsToReset = listOf(
            R.id.incAutoClassified, R.id.incAutoOverflow, R.id.incAutoPattern,
            R.id.incTeleopClassified, R.id.incTeleopOverflow, R.id.incTeleopDepot, R.id.incTeleopPattern
        )
        viewsToReset.forEach { id ->
            findViewById<View>(id)?.findViewById<TextView>(R.id.tvValue)?.text = "0"
        }

        findViewById<CheckBox>(R.id.cbAutoLeave1)?.isChecked = false
        findViewById<CheckBox>(R.id.cbAutoLeave2)?.isChecked = false
        findViewById<RadioGroup>(R.id.rgPark1)?.check(R.id.rbPark1None)
        findViewById<RadioGroup>(R.id.rgPark2)?.check(R.id.rbPark2None)
        findViewById<RadioGroup>(R.id.rgPark1Level)?.check(R.id.rbPark1LevelNA)
        findViewById<RadioGroup>(R.id.rgPark2Level)?.check(R.id.rbPark2LevelNA)
        findViewById<RadioGroup>(R.id.rgMatchResult)?.check(R.id.rbWin)

        findViewById<EditText>(R.id.etMatchNotes)?.text?.clear()

        if (isScoutingMode) {
            findViewById<EditText>(R.id.etTeamNumber)?.text?.clear()
            findViewById<EditText>(R.id.etMatchNumber)?.text?.clear()
            findViewById<EditText>(R.id.etDrivetrainNotes)?.text?.clear()
            findViewById<EditText>(R.id.etIntakeNotes)?.text?.clear()
            findViewById<RadioGroup>(R.id.rgSort)?.check(R.id.rbSortNo)
            findViewById<EditText>(R.id.etSortNotes)?.text?.clear()
            findViewById<CheckBox>(R.id.cbTurret)?.isChecked = false
            findViewById<CheckBox>(R.id.cbHood)?.isChecked = false
            findViewById<EditText>(R.id.etOuttakeNotes)?.text?.clear()
            findViewById<RadioGroup>(R.id.rgClimb)?.check(R.id.rbClimbNo)
            findViewById<EditText>(R.id.etClimbNotes)?.text?.clear()

            findViewById<RadioGroup>(R.id.rgCloseAcc)?.clearCheck()
            findViewById<RadioGroup>(R.id.rgFarAcc)?.clearCheck()
            findViewById<SeekBar>(R.id.sbPlaystyle)?.progress = 4
        }

        findViewById<TextView>(R.id.tvTotalScore)?.text = "0"
        findViewById<TextView>(R.id.tvRP)?.text = "0"
        updateTimerPreview()
        randomizeMotif()

        findViewById<Button>(R.id.btnRandomize)?.apply {
            isEnabled = true
            alpha = 1.0f
        }

        if (!isScoutingMode) {
            enableScoringControls(false)
        }
        PatternHelper.reset()
    }

    private fun setupTimerLogic() {
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnReset = findViewById<Button>(R.id.btnResetTimer)

        btnStart.setOnClickListener {
            animateButtonPress(it)
            startMatchSequence()
        }
        btnStop.setOnClickListener {
            animateButtonPress(it)
            stopTimerSequence()
        }
        btnReset.setOnClickListener { resetAll() }
    }

    private fun stopTimerSequence() {
        sequenceHandler.removeCallbacksAndMessages(null)
        timer?.cancel()
        stopAudio()
        findViewById<Button>(R.id.btnStart).isEnabled = true
        findViewById<Button>(R.id.btnStop).isEnabled = false
        findViewById<TextView>(R.id.tvMatchPhase).text = "MATCH STOPPED"
    }

    private fun startMatchSequence() {
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        enableScoringControls(true)
        findViewById<Button>(R.id.btnRandomize)?.apply {
            isEnabled = false
            alpha = 0.5f
        }
        when(currentMatchType) {
            "Auto Only" -> {
                startMatchAudio(0)
                updateTimerUI("0:30", "AUTONOMOUS", "#BB86FC")
                matchState = MatchState.AUTO

                sequenceHandler.postDelayed({
                    timer = object : CountDownTimer(30000, 50) {
                        override fun onTick(millis: Long) {
                            val sec = Math.ceil(millis / 1000.0).toInt()
                            updateTimerUI(String.format("0:%02d", sec), "AUTONOMOUS", "#BB86FC")
                        }
                        override fun onFinish() {
                            updateTimerUI("0:00", "FINISHED", "#888888")
                            btnStart.isEnabled = true
                            btnStop.isEnabled = false
                            stopAudio()
                        }
                    }.start()
                }, 1000)
            }
            "TeleOp Only" -> {
                startMatchAudio(32000)
                startTransitionPeriod(6500)
            }
            else -> { // Full Match
                startMatchAudio(0)
                updateTimerUI("2:30", "AUTONOMOUS", "#BB86FC")
                matchState = MatchState.AUTO

                sequenceHandler.postDelayed({
                    timer = object : CountDownTimer(30000, 50) {
                        override fun onTick(millis: Long) {
                            val sec = Math.ceil(millis / 1000.0).toInt()
                            updateTimerUI(String.format("2:%02d", sec), "AUTONOMOUS", "#BB86FC")
                        }
                        override fun onFinish() {
                            updateTimerUI("2:00", "END AUTO", "#BB86FC")
                            startTransitionPeriod(8000)
                        }
                    }.start()
                }, 1000)
            }
        }
    }

    private fun startTransitionPeriod(durationMs: Long) {
        matchState = MatchState.TRANSITION
        updateTimerUI("2:00", "PICK UP CONTROLLERS", "#FFFFFF")

        timer = object : CountDownTimer(durationMs, 50) {
            override fun onTick(millis: Long) {
                val sec = Math.ceil(millis / 1000.0).toInt()
                if (sec <= 3) updateTimerUI("2:00", "READY? $sec", "#FF0000")
            }

            override fun onFinish() {
                startTeleopPeriod()
            }
        }.start()
    }

    private fun startTeleopPeriod() {
        matchState = MatchState.TELEOP
        updateTimerUI("2:00", "TELEOP", "#03DAC5")

        timer = object : CountDownTimer(120000, 50) {
            override fun onTick(millis: Long) {
                val secTotal = Math.ceil(millis / 1000.0).toInt()
                val color = if (secTotal <= 20) "#CF6679" else "#03DAC5"
                val phase = if (secTotal <= 20) "ENDGAME" else "TELEOP"
                updateTimerUI(String.format("%d:%02d", secTotal / 60, secTotal % 60), phase, color)
            }

            override fun onFinish() {
                matchState = MatchState.END
                updateTimerUI("0:00", "MATCH ENDED", "#888888")
                findViewById<Button>(R.id.btnStart).isEnabled = true
                findViewById<Button>(R.id.btnStop).isEnabled = false
                sequenceHandler.postDelayed({ stopAudio() }, 1500)
            }
        }.start()
    }

    private fun startMatchAudio(seekToMs: Int) {
        try {
            stopAudio()
            matchAudioPlayer = MediaPlayer.create(this, R.raw.full_match)
            if (seekToMs > 0) matchAudioPlayer?.seekTo(seekToMs)
            matchAudioPlayer?.setOnCompletionListener { stopAudio() }
            matchAudioPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudio() {
        try {
            if (matchAudioPlayer?.isPlaying == true) matchAudioPlayer?.stop()
            matchAudioPlayer?.release()
            matchAudioPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTimerUI(time: String, phase: String, colorHex: String) {
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvMatchPhase = findViewById<TextView>(R.id.tvMatchPhase)

        tvTimer.text = time
        tvTimer.setTextColor(Color.parseColor(colorHex))

        tvTimer.animate().scaleX(1.02f).scaleY(1.02f).setDuration(50).withEndAction {
            tvTimer.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
        }.start()

        tvMatchPhase.text = phase
        tvMatchPhase.setTextColor(Color.parseColor(colorHex))
    }

    private fun updateDisplay() {
        findViewById<TextView>(R.id.tvTotalScore).text = calculateTotal().toString()
        findViewById<TextView>(R.id.tvRP).text = calculateRP().toString()
    }

    private fun calculateTotal() = calculateAuto() + calculateTeleop() + calculateEndgame()

    private fun calculateAuto(): Int {
        var score = 0
        if (findViewById<CheckBox>(R.id.cbAutoLeave1).isChecked) score += 3
        if (findViewById<CheckBox>(R.id.cbAutoLeave2).isChecked) score += 3
        score += (counters[R.id.incAutoClassified] ?: 0) * 3
        score += (counters[R.id.incAutoOverflow] ?: 0) * 1
        score += (counters[R.id.incAutoPattern] ?: 0) * 2
        return score
    }

    private fun calculateTeleop(): Int {
        var score = 0
        score += (counters[R.id.incTeleopClassified] ?: 0) * 3
        score += (counters[R.id.incTeleopOverflow] ?: 0) * 1
        score += (counters[R.id.incTeleopDepot] ?: 0) * 1
        score += (counters[R.id.incTeleopPattern] ?: 0) * 2
        return score
    }

    private fun calculateEndgame(): Int {
        var score = 0
        val rg1 = findViewById<RadioGroup>(R.id.rgPark1)
        score += when (rg1.checkedRadioButtonId) {
            R.id.rbPark1Partial -> 5; R.id.rbPark1Full -> 10; else -> 0
        }
        if (findViewById<RadioButton>(R.id.rbTwoBots).isChecked) {
            val rg2 = findViewById<RadioGroup>(R.id.rgPark2)
            score += when (rg2.checkedRadioButtonId) {
                R.id.rbPark2Partial -> 5; R.id.rbPark2Full -> 10; else -> 0
            }
            if (findViewById<RadioButton>(R.id.rbPark1Full).isChecked && findViewById<RadioButton>(R.id.rbPark2Full).isChecked) score += 10
        }
        return score
    }

    private fun calculateRP(): Int {
        var rp = 0
        val rgResult = findViewById<RadioGroup>(R.id.rgMatchResult)
        rp += when (rgResult.checkedRadioButtonId) {
            R.id.rbWin -> 3; R.id.rbTie -> 1; else -> 0
        }

        val autoLeave = findViewById<CheckBox>(R.id.cbAutoLeave1).isChecked || findViewById<CheckBox>(R.id.cbAutoLeave2).isChecked
        val park1 = findViewById<RadioGroup>(R.id.rgPark1).checkedRadioButtonId != R.id.rbPark1None
        val park2 = findViewById<RadioGroup>(R.id.rgPark2).checkedRadioButtonId != R.id.rbPark2None
        if (autoLeave && (park1 || park2)) rp += 1

        val prefs = getSharedPreferences("FTCSettings", Context.MODE_PRIVATE)
        val artifactThreshold = prefs.getInt("ARTIFACT_THRESH", 36)
        val patternThreshold = prefs.getInt("PATTERN_THRESH", 9)

        val patternScore = (counters[R.id.incAutoPattern] ?: 0) * 2 + (counters[R.id.incTeleopPattern] ?: 0) * 2
        if (patternScore >= patternThreshold) rp += 1

        val totalArtifacts = (counters[R.id.incAutoClassified] ?: 0) + (counters[R.id.incAutoOverflow] ?: 0) +
                (counters[R.id.incTeleopClassified] ?: 0) + (counters[R.id.incTeleopOverflow] ?: 0) +
                (counters[R.id.incTeleopDepot] ?: 0)
        if (totalArtifacts >= artifactThreshold) rp += 1
        return rp
    }

    private fun setupCounters() {
        setupCounterLogic(R.id.incAutoClassified, "Classified (3p)")
        setupCounterLogic(R.id.incAutoOverflow, "Overflow (1p)")
        setupCounterLogic(R.id.incTeleopClassified, "Classified (3p)")
        setupCounterLogic(R.id.incTeleopOverflow, "Overflow (1p)")
        setupCounterLogic(R.id.incTeleopDepot, "Depot (1p)")

        val autoPatternView = findViewById<View>(R.id.incAutoPattern)
        autoPatternView?.findViewById<TextView>(R.id.tvLabel)?.text = "Pattern"
        counters[R.id.incAutoPattern] = 0
        autoPatternView?.findViewById<Button>(R.id.btnSelect)?.setOnClickListener {
            // Trimitem "true" pentru că e apelat din AUTONOMOUS
            PatternHelper.showDialog(this, true) { matches ->
                counters[R.id.incAutoPattern] = matches
                autoPatternView.findViewById<TextView>(R.id.tvValue)?.text = matches.toString()
                updateDisplay()
            }
        }

        val teleopPatternView = findViewById<View>(R.id.incTeleopPattern)
        teleopPatternView?.findViewById<TextView>(R.id.tvLabel)?.text = "Pattern"
        counters[R.id.incTeleopPattern] = 0
        teleopPatternView?.findViewById<Button>(R.id.btnSelect)?.setOnClickListener {
            // Trimitem "false" pentru că e apelat din TELEOP
            PatternHelper.showDialog(this, false) { matches ->
                counters[R.id.incTeleopPattern] = matches
                teleopPatternView.findViewById<TextView>(R.id.tvValue)?.text = matches.toString()
                updateDisplay()
            }
        }
    }

    private fun setupCounterLogic(includeId: Int, label: String) {
        val container = findViewById<ViewGroup>(includeId)
        container.clipChildren = false
        container.clipToPadding = false
        (container.parent as? ViewGroup)?.clipChildren = false
        (container.parent as? ViewGroup)?.clipToPadding = false

        val btnPlus = container.findViewById<Button>(R.id.btnPlus)
        val btnMinus = container.findViewById<Button>(R.id.btnMinus)
        val innerLayout = btnPlus.parent as? ViewGroup

        innerLayout?.clipChildren = false
        innerLayout?.clipToPadding = false

        container.findViewById<TextView>(R.id.tvLabel).text = label
        counters[includeId] = 0
        val tvValue = container.findViewById<TextView>(R.id.tvValue)

        btnPlus.setOnClickListener {
            animateScorePress(it)
            counters[includeId] = counters[includeId]!! + 1
            tvValue.text = counters[includeId].toString()
            updateDisplay()
        }

        btnMinus.setOnClickListener {
            animateScorePress(it)
            if (counters[includeId]!! > 0) {
                counters[includeId] = counters[includeId]!! - 1
                tvValue.text = counters[includeId].toString()
                updateDisplay()
            }
        }
    }

    private fun setupHapticFeedback() {
        val scoringViews = listOf(
            R.id.cbAutoLeave1, R.id.cbAutoLeave2,
            R.id.rbPark1None, R.id.rbPark1Partial, R.id.rbPark1Full,
            R.id.rbPark2None, R.id.rbPark2Partial, R.id.rbPark2Full,
            R.id.rbWin, R.id.rbTie, R.id.rbLoss,
            R.id.rbSortYes, R.id.rbSortNo,
            R.id.cbTurret, R.id.cbHood,
            R.id.rbC1, R.id.rbC2, R.id.rbC3, R.id.rbC4, R.id.rbC5,
            R.id.rbF1, R.id.rbF2, R.id.rbF3, R.id.rbF4, R.id.rbF5,
            R.id.rbClimbYes, R.id.rbClimbNo
        )

        for (id in scoringViews) {
            findViewById<View>(id)?.setOnClickListener { view -> animateScorePress(view) }
        }

        findViewById<SeekBar>(R.id.sbPlaystyle)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun enableScoringControls(enable: Boolean) {
        val alphaVal = if (enable) 1.0f else 0.5f

        val cbLeave1 = findViewById<CheckBox>(R.id.cbAutoLeave1)
        val cbLeave2 = findViewById<CheckBox>(R.id.cbAutoLeave2)
        cbLeave1?.isEnabled = enable
        cbLeave2?.isEnabled = enable
        cbLeave1?.alpha = alphaVal
        cbLeave2?.alpha = alphaVal

        for (id in counters.keys) {
            val container = findViewById<View>(id)
            container?.findViewById<View>(R.id.btnPlus)?.isEnabled = enable
            container?.findViewById<View>(R.id.btnMinus)?.isEnabled = enable
            container?.findViewById<View>(R.id.btnSelect)?.isEnabled = enable
            container?.alpha = alphaVal
        }

        val rgPark1 = findViewById<RadioGroup>(R.id.rgPark1)
        val rgPark2 = findViewById<RadioGroup>(R.id.rgPark2)
        val rgMatchResult = findViewById<RadioGroup>(R.id.rgMatchResult)

        rgPark1?.let { enableRadioGroup(it, enable) }
        rgPark2?.let { enableRadioGroup(it, enable) }
        rgMatchResult?.let { enableRadioGroup(it, enable) }

        rgPark1?.alpha = alphaVal
        rgPark2?.alpha = alphaVal
        rgMatchResult?.alpha = alphaVal
    }

    private fun enableRadioGroup(rg: RadioGroup, enable: Boolean) {
        for (i in 0 until rg.childCount) rg.getChildAt(i).isEnabled = enable
    }

    private fun setupRobotModeSelector() {
        val rgRobotCount = findViewById<RadioGroup>(R.id.rgRobotCount)
        val cbLeave2 = findViewById<CheckBox>(R.id.cbAutoLeave2)
        val layoutPark2 = findViewById<LinearLayout>(R.id.layoutRobot2Parking)
        val rgPark2 = findViewById<RadioGroup>(R.id.rgPark2)
        rgRobotCount.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbOneBot) {
                cbLeave2.visibility = View.GONE; layoutPark2.visibility = View.GONE; cbLeave2.isChecked = false; rgPark2.check(R.id.rbPark2None)
            } else {
                cbLeave2.visibility = View.VISIBLE; layoutPark2.visibility = View.VISIBLE
            }
            updateDisplay()
        }
    }

    private fun setupSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerSessions)
        lifecycleScope.launch { refreshSessionList(spinner) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedText = parent?.getItemAtPosition(position).toString()

                if (isFirstLaunch) {
                    isFirstLaunch = false
                    if (sessionList.isEmpty()) return
                }

                if (selectedText == "+ New Folder") {
                    spinner.setSelection(0)
                    showCreateSessionDialog(spinner)
                } else if (selectedText != "No folders yet...") {
                    if (sessionList.isNotEmpty() && position < sessionList.size) {
                        selectedSessionId = sessionList[position].sessionId
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // --- RECREAT PERFECT DUPĂ CODUL DECOMPILAT PENTRU A NU MAI AMESTECA FOLDERELE ---
    private fun refreshSessionList(spinner: Spinner?) {
        if (spinner == null) return
        lifecycleScope.launch {
            val allMatches = db.appDao().getAllMatches()
            // Filtrul tău original: aflăm ce id-uri de sesiuni aparțin meciurilor de Scouting (adică au teamNumber completat)
            val scoutingSessionIds = allMatches.filter { it.teamNumber != null }.map { it.parentSessionId }.toSet()

            // Le excludem din listă!
            sessionList = db.appDao().getAllSessions().filter { it.sessionId !in scoutingSessionIds }.toMutableList()

            val names = mutableListOf<String>()
            if (sessionList.isEmpty()) names.add("No folders yet...")
            names.addAll(sessionList.map { it.name })
            names.add("+ New Folder")

            spinner.adapter = getWhiteTextAdapter(names)

            if (selectedSessionId != -1) {
                val idx = sessionList.indexOfFirst { it.sessionId == selectedSessionId }
                if (idx != -1) spinner.setSelection(if (sessionList.isEmpty()) idx + 1 else idx)
            }
        }
    }

    private fun showCreateSessionDialog(spinner: Spinner) {
        val titleView = TextView(this).apply {
            text = "Create New Folder"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(60, 60, 60, 20)
        }

        val input = EditText(this).apply {
            hint = "Folder name..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2C"))
                cornerRadius = 24f
            }
            val scale = resources.displayMetrics.density
            val padV = (14 * scale + 0.5f).toInt()
            val padH = (20 * scale + 0.5f).toInt()
            setPadding(padH, padV, padH, padV)
        }

        val container = FrameLayout(this)
        container.setPadding(60, 10, 60, 20)
        container.addView(input)

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val newId = db.appDao().insertSession(Session(name = newName)).toInt()
                        selectedSessionId = newId
                        refreshSessionList(spinner)
                        Toast.makeText(this@MainActivity, "Folder '$newName' created", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch { refreshSessionList(spinner) }
                }
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.cancel()
                lifecycleScope.launch { refreshSessionList(spinner) }
            }
            .setOnCancelListener {
                lifecycleScope.launch { refreshSessionList(spinner) }
            }
            .create()

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 40f
        }
        dialog.window?.setBackgroundDrawable(bg)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#0A7AFF"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#888888"))
    }

    private fun randomizeMotif() {
        val validMotifs = listOf(
            intArrayOf(1, 1, 2), // PPG
            intArrayOf(1, 2, 1), // PGP
            intArrayOf(2, 1, 1)  // GPP
        )
        val states = validMotifs.random()
        PatternHelper.savedMotif = states

        val images = listOf(R.id.imgBall1, R.id.imgBall2, R.id.imgBall3)
        for (i in 0..2) {
            val res = if (states[i] == 1) R.drawable.ball_purple else R.drawable.ball_green
            findViewById<ImageView>(images[i])?.setImageResource(res)
        }
    }

    private fun animateScorePress(view: View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
    }

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }
}