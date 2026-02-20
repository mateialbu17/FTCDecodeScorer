package com.example.ftcdecodescorer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // --- BUTON BACK (NOU) ---
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val db = AppDatabase.getDatabase(this)
        val listView = findViewById<ListView>(R.id.lvHistory)

        lifecycleScope.launch {
            val sessions = db.appDao().getAllSessions()

            // Adapter simplu pentru foldere
            val adapter = object : ArrayAdapter<Session>(this@HistoryActivity, android.R.layout.simple_list_item_1, sessions) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text = view.findViewById<TextView>(android.R.id.text1)
                    val session = getItem(position)

                    text.text = "📁  ${session?.name}"
                    text.textColor = android.graphics.Color.WHITE
                    text.textSize = 18f
                    text.setPadding(20, 20, 20, 20)
                    return view
                }
            }
            listView.adapter = adapter

            // Click pe folder -> Deschide detaliile
            listView.setOnItemClickListener { _, _, position, _ ->
                val session = sessions[position]
                val intent = Intent(this@HistoryActivity, SessionDetailsActivity::class.java)
                intent.putExtra("SESSION_ID", session.sessionId)
                intent.putExtra("SESSION_NAME", session.name)
                startActivity(intent)
            }
        }
    }

    // Extensie pentru a seta proprietăți TextView ușor (opțional, doar pt codul de mai sus)
    private var TextView.textColor: Int
        get() = currentTextColor
        set(v) = setTextColor(v)
}