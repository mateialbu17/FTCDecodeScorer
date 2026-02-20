package com.example.ftcdecodescorer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
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
    private var isFirstLaunch = true // Previne pop-up-ul automat la pornire

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)
        isFirstLaunch = true

        setupCounters()
        setupTimerLogic()
        setupSpinner()
        setupMatchTypeSelector()
        setupModeToggle()
        setupRobotModeSelector()
        setupDrivetrainSpinner()

        findViewById<Button>(R.id.btnViewHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnRandomize).setOnClickListener { randomizeMotif() }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            if (isScoutingMode) {
                val teamNum = findViewById<EditText>(R.id.etTeamNumber).text.toString()
                val matchNum = findViewById<EditText>(R.id.etMatchNumber).text.toString()
                if (teamNum.isEmpty() || matchNum.isEmpty()) {
                    Toast.makeText(this, "Please enter Team & Match #", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveMatch(teamNum, matchNum)
            } else {
                if (selectedSessionId == -1) {
                    Toast.makeText(this, "Please select a folder!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveMatch(null, null)
            }
        }

        findViewById<Button>(R.id.btnDiscard).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Discard Match?")
                .setMessage("Are you sure you want to clear current match data?")
                .setPositiveButton("Yes") { _, _ -> resetAll() }
                .setNegativeButton("No", null)
                .show()
        }

        val updateListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<CheckBox>(R.id.cbAutoLeave1).setOnCheckedChangeListener(updateListener)
        findViewById<CheckBox>(R.id.cbAutoLeave2).setOnCheckedChangeListener(updateListener)
        findViewById<RadioGroup>(R.id.rgPark1).setOnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<RadioGroup>(R.id.rgPark2).setOnCheckedChangeListener { _, _ -> updateDisplay() }
        findViewById<RadioGroup>(R.id.rgMatchResult).setOnCheckedChangeListener { _, _ -> updateDisplay() }

        enableScoringControls(false)
        updateTimerUI("2:30", "PRE-MATCH", "#888888")
        randomizeMotif()
        updateUIForMatchType()
    }

    override fun onResume() {
        super.onResume()
        updateDisplay()
    }

    private fun getWhiteTextAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 16f
                val scale = context.resources.displayMetrics.density
                val padV = (8 * scale + 0.5f).toInt()
                val padH = (12 * scale + 0.5f).toInt()
                view.setPadding(padH, padV, padH, padV)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#2C2C2C"))
                view.textSize = 20f
                val scale = context.resources.displayMetrics.density
                val padV = (16 * scale + 0.5f).toInt()
                val padH = (24 * scale + 0.5f).toInt()
                view.setPadding(padH, padV, padH, padV)
                return view
            }
        }
    }

    private fun setupDrivetrainSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinDrivetrain)
        val items = listOf("Mecanum", "Swerve", "Other")
        spinner.adapter = getWhiteTextAdapter(items)
    }

    private fun setupMatchTypeSelector() {
        val spinner = findViewById<Spinner>(R.id.spinnerMatchType)
        val types = listOf("Full Match", "Auto Only", "TeleOp Only")
        spinner.adapter = getWhiteTextAdapter(types)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMatchType = types[position]
                updateUIForMatchType()
                resetAll()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUIForMatchType() {
        val cardAuto = findViewById<View>(R.id.cardAuto)
        val cardTeleop = findViewById<View>(R.id.cardTeleop)
        val cardEndgame = findViewById<View>(R.id.cardEndgame)

        when(currentMatchType) {
            "Auto Only" -> {
                cardAuto.visibility = View.VISIBLE
                cardTeleop.visibility = View.GONE
                cardEndgame.visibility = View.GONE
            }
            "TeleOp Only" -> {
                cardAuto.visibility = View.GONE
                cardTeleop.visibility = View.VISIBLE
                cardEndgame.visibility = View.VISIBLE
            }
            else -> {
                cardAuto.visibility = View.VISIBLE
                cardTeleop.visibility = View.VISIBLE
                cardEndgame.visibility = View.VISIBLE
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
                layoutPractice.visibility = View.GONE
                layoutScouting.visibility = View.VISIBLE
                layoutTimer.visibility = View.GONE
                layoutTimerControls.visibility = View.GONE

                rgRobotCount.visibility = View.GONE
                rbOneBot.isChecked = true

                cardRandomization.visibility = View.GONE
                cardMechanisms.visibility = View.VISIBLE

                rgPark1Level?.visibility = View.VISIBLE
                rgPark2Level?.visibility = View.VISIBLE

                enableScoringControls(true)
            } else {
                isScoutingMode = false
                layoutPractice.visibility = View.VISIBLE
                layoutScouting.visibility = View.GONE
                layoutTimer.visibility = View.VISIBLE
                layoutTimerControls.visibility = View.VISIBLE

                rgRobotCount.visibility = View.VISIBLE
                cardRandomization.visibility = View.VISIBLE
                cardMechanisms.visibility = View.GONE

                rgPark1Level?.visibility = View.GONE
                rgPark2Level?.visibility = View.GONE

                enableScoringControls(false)
                updateTimerUI("2:30", "PRE-MATCH", "#888888")
            }
        }
    }

    private fun saveMatch(scoutingTeam: String?, scoutingMatchNum: String?) {
        val total = calculateTotal()
        val rp = calculateRP()

        val park1Str = when(findViewById<RadioGroup>(R.id.rgPark1).checkedRadioButtonId) { R.id.rbPark1Full -> "Full"; R.id.rbPark1Partial -> "Partial"; else -> "None" }
        val park2Str = when(findViewById<RadioGroup>(R.id.rgPark2).checkedRadioButtonId) { R.id.rbPark2Full -> "Full"; R.id.rbPark2Partial -> "Partial"; else -> "None" }
        val resultStr = when(findViewById<RadioGroup>(R.id.rgMatchResult).checkedRadioButtonId) { R.id.rbWin -> "WIN"; R.id.rbTie -> "TIE"; else -> "LOSS" }

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

        if (isScoutingMode) {
            mechDrive = findViewById<Spinner>(R.id.spinDrivetrain).selectedItem.toString()
            mechDriveNote = findViewById<EditText>(R.id.etDrivetrainNotes).text.toString()
            mechIntakeNote = findViewById<EditText>(R.id.etIntakeNotes).text.toString()
            mechSort = findViewById<RadioButton>(R.id.rbSortYes).isChecked
            mechSortNote = findViewById<EditText>(R.id.etSortNotes).text.toString()
            mechTurret = findViewById<CheckBox>(R.id.cbTurret).isChecked
            mechHood = findViewById<CheckBox>(R.id.cbHood).isChecked
            mechOuttakeNote = findViewById<EditText>(R.id.etOuttakeNotes).text.toString()
            mechClimb = findViewById<RadioButton>(R.id.rbClimbYes).isChecked
            mechClimbNote = findViewById<EditText>(R.id.etClimbNotes).text.toString()

            p1LevelStr = when(findViewById<RadioGroup>(R.id.rgPark1Level)?.checkedRadioButtonId) { R.id.rbPark1LevelUp -> "Up"; R.id.rbPark1LevelDown -> "Down"; else -> "N/A" }
            p2LevelStr = when(findViewById<RadioGroup>(R.id.rgPark2Level)?.checkedRadioButtonId) { R.id.rbPark2LevelUp -> "Up"; R.id.rbPark2LevelDown -> "Down"; else -> "N/A" }
        }

        val generalNotes = findViewById<EditText>(R.id.etMatchNotes)?.text?.toString()

        lifecycleScope.launch {
            var targetName = if (isScoutingMode) scoutingTeam!! else sessionList.find { it.sessionId == selectedSessionId }?.name ?: "Default"
            if (currentMatchType != "Full Match") targetName += " - $currentMatchType"

            val existingSession = db.appDao().getSessionByName(targetName)
            if (existingSession == null) {
                db.appDao().insertSession(Session(name = targetName))
            }
            val finalSessId = db.appDao().getSessionByName(targetName)?.sessionId ?: 0

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
                totalScore = total,
                autoPoints = calculateAuto(),
                teleopPoints = calculateTeleop(),
                endgamePoints = calculateEndgame(),
                rp = rp,
                autoClassified = counters[R.id.incAutoClassified] ?: 0,
                autoOverflow = counters[R.id.incAutoOverflow] ?: 0,
                autoPattern = counters[R.id.incAutoPattern] ?: 0,
                autoLeave1 = findViewById<CheckBox>(R.id.cbAutoLeave1).isChecked,
                autoLeave2 = findViewById<CheckBox>(R.id.cbAutoLeave2).isChecked,
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
                mechClimb = mechClimb,
                mechClimbNotes = mechClimbNote
            )

            db.appDao().insertMatch(match)
            Toast.makeText(this@MainActivity, "Saved to '$targetName'", Toast.LENGTH_SHORT).show()
            resetAll()
        }
    }

    private fun resetAll() {
        stopTimerSequence()
        matchState = MatchState.PRE_MATCH

        val defaultTime = when(currentMatchType) { "Auto Only" -> "0:30"; "TeleOp Only" -> "2:00"; else -> "2:30" }
        if(!isScoutingMode) updateTimerUI(defaultTime, "PRE-MATCH", "#888888")

        counters.keys.forEach { key -> counters[key] = 0; findViewById<View>(key).findViewById<TextView>(R.id.tvValue).text = "0" }
        findViewById<CheckBox>(R.id.cbAutoLeave1).isChecked = false
        findViewById<CheckBox>(R.id.cbAutoLeave2).isChecked = false
        findViewById<RadioGroup>(R.id.rgPark1).check(R.id.rbPark1None)
        findViewById<RadioGroup>(R.id.rgPark2).check(R.id.rbPark2None)
        findViewById<RadioGroup>(R.id.rgMatchResult).check(R.id.rbWin)
        findViewById<EditText>(R.id.etMatchNumber).setText("")
        findViewById<EditText>(R.id.etTeamNumber).setText("")

        findViewById<RadioGroup>(R.id.rgPark1Level)?.check(R.id.rbPark1LevelNA)
        findViewById<RadioGroup>(R.id.rgPark2Level)?.check(R.id.rbPark2LevelNA)
        findViewById<EditText>(R.id.etMatchNotes)?.setText("")

        findViewById<Spinner>(R.id.spinDrivetrain).setSelection(0)
        findViewById<EditText>(R.id.etDrivetrainNotes).setText("")
        findViewById<EditText>(R.id.etIntakeNotes).setText("")
        findViewById<RadioButton>(R.id.rbSortNo).isChecked = true
        findViewById<EditText>(R.id.etSortNotes).setText("")
        findViewById<CheckBox>(R.id.cbTurret).isChecked = false
        findViewById<CheckBox>(R.id.cbHood).isChecked = false
        findViewById<EditText>(R.id.etOuttakeNotes).setText("")
        findViewById<RadioButton>(R.id.rbClimbNo).isChecked = true
        findViewById<EditText>(R.id.etClimbNotes).setText("")

        randomizeMotif()
        updateDisplay()
        enableScoringControls(isScoutingMode)
    }

    // --- TIMERS & AUDIO LOGIC ---

    private fun setupTimerLogic() {
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnReset = findViewById<Button>(R.id.btnResetTimer)

        btnStart.setOnClickListener { startMatchSequence() }

        btnStop.setOnClickListener {
            stopTimerSequence()
            findViewById<TextView>(R.id.tvMatchPhase).text = "MATCH STOPPED"
        }

        btnReset.setOnClickListener { resetAll() }
    }

    private fun stopTimerSequence() {
        sequenceHandler.removeCallbacksAndMessages(null)
        timer?.cancel()
        stopAudio()
        findViewById<Button>(R.id.btnStart).isEnabled = true
        findViewById<Button>(R.id.btnStop).isEnabled = false
        enableScoringControls(false)
    }

    private fun startMatchSequence() {
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        enableScoringControls(true)

        when(currentMatchType) {
            "Auto Only" -> {
                startMatchAudio(0)
                updateTimerUI("0:30", "AUTONOMOUS", "#BB86FC")
                matchState = MatchState.AUTO
                sequenceHandler.postDelayed({
                    timer = object : CountDownTimer(30000, 1000) {
                        override fun onTick(millis: Long) { updateTimerUI(String.format("0:%02d", millis / 1000), "AUTONOMOUS", "#BB86FC") }
                        override fun onFinish() {
                            updateTimerUI("0:00", "FINISHED", "#888888")
                            btnStart.isEnabled = true; btnStop.isEnabled = false; enableScoringControls(false)
                            sequenceHandler.postDelayed({ stopAudio() }, 1500)
                        }
                    }.start()
                }, 2500) // Delay corectat pt sincronizare buzzer
            }
            "TeleOp Only" -> {
                startMatchAudio(128000) // Minutul 2:08 din fisier
                updateTimerUI("2:00", "PREPARING...", "#FFFFFF")
                sequenceHandler.postDelayed({
                    startTeleopPeriod()
                }, 4000) // Asteapta vocea: "Drivers, pick up your controllers..."
            }
            else -> { // Full Match
                startMatchAudio(0)
                updateTimerUI("2:30", "AUTONOMOUS", "#BB86FC")
                sequenceHandler.postDelayed({
                    matchState = MatchState.AUTO
                    timer = object : CountDownTimer(30000, 1000) {
                        override fun onTick(millis: Long) { updateTimerUI(String.format("2:%02d", millis / 1000), "AUTONOMOUS", "#BB86FC") }
                        override fun onFinish() { updateTimerUI("2:00", "END AUTO", "#BB86FC"); startTransitionPeriod() }
                    }.start()
                }, 2500) // Delay corectat pt sincronizare buzzer
            }
        }
    }

    private fun startTransitionPeriod() {
        matchState = MatchState.TRANSITION
        updateTimerUI("2:00", "PICK UP CONTROLLERS", "#FFFFFF")
        timer = object : CountDownTimer(8000, 1000) {
            override fun onTick(millis: Long) { if (millis/1000 <= 3) updateTimerUI("2:00", "READY? ${millis/1000}", "#FF0000") }
            override fun onFinish() { startTeleopPeriod() }
        }.start()
    }

    private fun startTeleopPeriod() {
        matchState = MatchState.TELEOP
        updateTimerUI("2:00", "TELEOP", "#03DAC5")
        timer = object : CountDownTimer(120000, 1000) {
            override fun onTick(millis: Long) {
                val secTotal = millis / 1000
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopAudio() {
        try {
            if (matchAudioPlayer?.isPlaying == true) matchAudioPlayer?.stop()
            matchAudioPlayer?.release()
            matchAudioPlayer = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateTimerUI(time: String, phase: String, colorHex: String) {
        findViewById<TextView>(R.id.tvTimer).apply { text = time; setTextColor(Color.parseColor(colorHex)) }
        findViewById<TextView>(R.id.tvMatchPhase).apply { text = phase; setTextColor(Color.parseColor(colorHex)) }
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
        score += when (rg1.checkedRadioButtonId) { R.id.rbPark1Partial -> 5; R.id.rbPark1Full -> 10; else -> 0 }
        if (findViewById<RadioButton>(R.id.rbTwoBots).isChecked) {
            val rg2 = findViewById<RadioGroup>(R.id.rgPark2)
            score += when (rg2.checkedRadioButtonId) { R.id.rbPark2Partial -> 5; R.id.rbPark2Full -> 10; else -> 0 }
            if (findViewById<RadioButton>(R.id.rbPark1Full).isChecked && findViewById<RadioButton>(R.id.rbPark2Full).isChecked) score += 10
        }
        return score
    }

    private fun calculateRP(): Int {
        var rp = 0
        val rgResult = findViewById<RadioGroup>(R.id.rgMatchResult)
        rp += when(rgResult.checkedRadioButtonId) { R.id.rbWin -> 3; R.id.rbTie -> 1; else -> 0 }

        val autoLeave = findViewById<CheckBox>(R.id.cbAutoLeave1).isChecked || findViewById<CheckBox>(R.id.cbAutoLeave2).isChecked
        val park1 = findViewById<RadioGroup>(R.id.rgPark1).checkedRadioButtonId != R.id.rbPark1None
        val park2 = findViewById<RadioGroup>(R.id.rgPark2).checkedRadioButtonId != R.id.rbPark2None
        if (autoLeave && (park1 || park2)) rp += 1

        val prefs = getSharedPreferences("FTCSettings", Context.MODE_PRIVATE)
        val artifactThreshold = prefs.getInt("ARTIFACT_THRESH", 36)
        val patternThreshold = prefs.getInt("PATTERN_THRESH", 9)

        val patternScore = (counters[R.id.incAutoPattern] ?: 0) * 2 + (counters[R.id.incTeleopPattern] ?: 0) * 2
        if (patternScore >= patternThreshold) rp += 1

        val totalArtifacts = (counters[R.id.incAutoClassified] ?: 0) + (counters[R.id.incAutoOverflow] ?: 0) + (counters[R.id.incTeleopClassified] ?: 0) + (counters[R.id.incTeleopOverflow] ?: 0) + (counters[R.id.incTeleopDepot] ?: 0)
        if (totalArtifacts >= artifactThreshold) rp += 1
        return rp
    }

    private fun setupCounters() {
        setupCounterLogic(R.id.incAutoClassified, "Classified (3p)")
        setupCounterLogic(R.id.incAutoOverflow, "Overflow (1p)")
        setupCounterLogic(R.id.incAutoPattern, "Pattern (2p)")
        setupCounterLogic(R.id.incTeleopClassified, "Classified (3p)")
        setupCounterLogic(R.id.incTeleopOverflow, "Overflow (1p)")
        setupCounterLogic(R.id.incTeleopDepot, "Depot (1p)")
        setupCounterLogic(R.id.incTeleopPattern, "Pattern (2p)")
    }

    private fun setupCounterLogic(includeId: Int, label: String) {
        val container = findViewById<View>(includeId)
        container.findViewById<TextView>(R.id.tvLabel).text = label
        counters[includeId] = 0
        val tvValue = container.findViewById<TextView>(R.id.tvValue)
        container.findViewById<Button>(R.id.btnPlus).setOnClickListener { counters[includeId] = counters[includeId]!! + 1; tvValue.text = counters[includeId].toString(); updateDisplay() }
        container.findViewById<Button>(R.id.btnMinus).setOnClickListener { if (counters[includeId]!! > 0) { counters[includeId] = counters[includeId]!! - 1; tvValue.text = counters[includeId].toString(); updateDisplay() } }
    }

    private fun enableScoringControls(enable: Boolean) {
        findViewById<CheckBox>(R.id.cbAutoLeave1).isEnabled = enable
        findViewById<CheckBox>(R.id.cbAutoLeave2).isEnabled = enable
        for (id in counters.keys) {
            val container = findViewById<View>(id)
            container.findViewById<Button>(R.id.btnPlus).isEnabled = enable
            container.findViewById<Button>(R.id.btnMinus).isEnabled = enable
            container.alpha = if (enable) 1.0f else 0.5f
        }
        enableRadioGroup(findViewById(R.id.rgPark1), enable)
        enableRadioGroup(findViewById(R.id.rgPark2), enable)
        enableRadioGroup(findViewById(R.id.rgMatchResult), enable)
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
            if (checkedId == R.id.rbOneBot) { cbLeave2.visibility = View.GONE; layoutPark2.visibility = View.GONE; cbLeave2.isChecked = false; rgPark2.check(R.id.rbPark2None) }
            else { cbLeave2.visibility = View.VISIBLE; layoutPark2.visibility = View.VISIBLE }
            updateDisplay()
        }
    }

    private fun setupSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerSessions)
        lifecycleScope.launch { refreshSessionList(spinner) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Bug Fix: Nu afiseaza pop-up la prima deschidere
                if (isFirstLaunch) {
                    isFirstLaunch = false
                    if (sessionList.isEmpty()) return
                }

                if (position == sessionList.size) {
                    showCreateSessionDialog(spinner)
                } else if (position < sessionList.size) {
                    selectedSessionId = sessionList[position].sessionId
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private suspend fun refreshSessionList(spinner: Spinner) {
        sessionList = db.appDao().getAllSessions().toMutableList()
        val items = sessionList.map { it.name }.toMutableList()
        items.add("+ New Folder")

        spinner.adapter = getWhiteTextAdapter(items)

        if (sessionList.isNotEmpty()) {
            spinner.setSelection(0, false)
            selectedSessionId = sessionList[0].sessionId
        } else {
            selectedSessionId = -1
            spinner.setSelection(0, false)
        }
    }

    private fun showCreateSessionDialog(spinner: Spinner) {
        val input = EditText(this)
        input.hint = "Folder Name"

        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.appDao().insertSession(Session(name = name))
                        refreshSessionList(spinner)
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                if(sessionList.isNotEmpty()) spinner.setSelection(0)
            }.show()
    }

    // --- FIX: Randomizare strictă pe tiparele FTC (PGP, PPG, GPP) ---
    private fun randomizeMotif() {
        val img1 = findViewById<ImageView>(R.id.imgBall1)
        val img2 = findViewById<ImageView>(R.id.imgBall2)
        val img3 = findViewById<ImageView>(R.id.imgBall3)

        val patterns = listOf(
            listOf(R.drawable.ball_green, R.drawable.ball_purple, R.drawable.ball_purple), // GPP
            listOf(R.drawable.ball_purple, R.drawable.ball_green, R.drawable.ball_purple), // PGP
            listOf(R.drawable.ball_purple, R.drawable.ball_purple, R.drawable.ball_green)  // PPG
        )
        val chosen = patterns.random()
        img1.setImageResource(chosen[0])
        img2.setImageResource(chosen[1])
        img3.setImageResource(chosen[2])
    }
}