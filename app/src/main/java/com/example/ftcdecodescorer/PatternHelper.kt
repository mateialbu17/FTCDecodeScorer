package com.example.ftcdecodescorer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

object PatternHelper {
    var savedMotif = intArrayOf(1, 1, 2)

    // Memorii separate ca să nu se încurce Auto cu TeleOp
    var autoTubeState = IntArray(9) { 0 }
    var teleopTubeState = IntArray(9) { 0 }

    // Această funcție e deja apelată în MainActivity la resetAll() ca să golească tuburile pt meciul nou
    fun reset() {
        autoTubeState = IntArray(9) { 0 }
        teleopTubeState = IntArray(9) { 0 }
    }

    // Am adăugat "isAuto: Boolean" ca să știe ce memorie să deschidă
    fun showDialog(context: Context, isAuto: Boolean, onSave: (Int) -> Unit) {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_pattern, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Încărcăm memoria corectă într-o "ciornă" temporară
        val activeState = if (isAuto) autoTubeState else teleopTubeState
        val tempState = activeState.copyOf()

        val pm1: ImageView = view.findViewById(R.id.pm1)
        val pm2: ImageView = view.findViewById(R.id.pm2)
        val pm3: ImageView = view.findViewById(R.id.pm3)
        val mViews = listOf(pm1, pm2, pm3)

        val pc1: ImageView = view.findViewById(R.id.pc1)
        val pc2: ImageView = view.findViewById(R.id.pc2)
        val pc3: ImageView = view.findViewById(R.id.pc3)
        val pc4: ImageView = view.findViewById(R.id.pc4)
        val pc5: ImageView = view.findViewById(R.id.pc5)
        val pc6: ImageView = view.findViewById(R.id.pc6)
        val pc7: ImageView = view.findViewById(R.id.pc7)
        val pc8: ImageView = view.findViewById(R.id.pc8)
        val pc9: ImageView = view.findViewById(R.id.pc9)
        val cViews = listOf(pc1, pc2, pc3, pc4, pc5, pc6, pc7, pc8, pc9)

        val tvPts: TextView = view.findViewById(R.id.tvPatternPts)

        fun getDrawable(state: Int) = when(state) {
            1 -> R.drawable.ball_purple
            2 -> R.drawable.ball_green
            else -> R.drawable.ball_gray
        }

        fun updateUI() {
            mViews.forEachIndexed { i, v -> v.setImageResource(getDrawable(savedMotif[i])) }
            cViews.forEachIndexed { i, v -> v.setImageResource(getDrawable(tempState[i])) }

            var matches = 0
            for (i in 0..8) {
                if (tempState[i] != 0 && tempState[i] == savedMotif[i % 3]) {
                    matches++
                }
            }
            tvPts.text = "Matches: $matches"
        }

        cViews.forEachIndexed { i, v ->
            v.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                tempState[i] = (tempState[i] + 1) % 3
                updateUI()
            }
        }

        updateUI()

        val btnCancel: Button = view.findViewById(R.id.btnCancelP)
        btnCancel.setOnClickListener { dialog.dismiss() } // Dacă dă cancel, ciorna se aruncă

        val btnSave: Button = view.findViewById(R.id.btnSaveP)
        btnSave.setOnClickListener {
            // Dacă dă APPLY, salvăm ciorna oficial în memorie
            if (isAuto) {
                autoTubeState = tempState.copyOf()
            } else {
                teleopTubeState = tempState.copyOf()
            }

            var matches = 0
            for (i in 0..8) {
                if (tempState[i] != 0 && tempState[i] == savedMotif[i % 3]) matches++
            }
            onSave(matches)
            dialog.dismiss()
        }

        dialog.show()

        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}