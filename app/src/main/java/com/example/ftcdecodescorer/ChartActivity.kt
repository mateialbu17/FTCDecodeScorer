package com.example.ftcdecodescorer

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChartActivity : AppCompatActivity() {

    private lateinit var chartContainer: FrameLayout
    private lateinit var rootLayout: LinearLayout
    private lateinit var saveImageLauncher: ActivityResultLauncher<String>

    private var matches: List<MatchResult> = emptyList()
    private var isDarkMode = true
    private var themeColor = Color.parseColor("#03DAC5")
    private var sessionNameStr = "Chart"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        val sessionId = intent.getIntExtra("SESSION_ID", -1)
        sessionNameStr = intent.getStringExtra("SESSION_NAME") ?: "Chart"
        findViewById<TextView>(R.id.tvChartTitle).text = "$sessionNameStr Progress"

        val db = AppDatabase.getDatabase(this)

        rootLayout = findViewById(R.id.rootLayoutChart)
        chartContainer = findViewById(R.id.chartContainer)

        findViewById<ImageView>(R.id.btnBackChart).setOnClickListener { finish() }

        // --- BUTTON ROTATE ---
        findViewById<ImageView>(R.id.btnRotateChart).setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
        }

        saveImageLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outStream ->
                    val bitmap = Bitmap.createBitmap(chartContainer.width, chartContainer.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(if (isDarkMode) Color.parseColor("#121212") else Color.WHITE)
                    chartContainer.draw(canvas)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }
                Toast.makeText(this, "Chart Saved Successfully!", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<ImageView>(R.id.btnSaveChart).setOnClickListener {
            if (matches.isNotEmpty()) {
                saveImageLauncher.launch("${sessionNameStr.replace(" ", "_")}_Chart.png")
            }
        }

        val switchDark = findViewById<Switch>(R.id.switchDarkMode)
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            updateThemeUI()
        }

        findViewById<RadioGroup>(R.id.rgColors).setOnCheckedChangeListener { _, checkedId ->
            themeColor = when(checkedId) {
                R.id.rbColorPurple -> Color.parseColor("#BB86FC")
                R.id.rbColorGreen -> Color.parseColor("#4CAF50")
                R.id.rbColorOrange -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#03DAC5")
            }
            drawChart()
        }

        lifecycleScope.launch {
            matches = db.appDao().getMatchesForSession(sessionId)
            drawChart()
        }
    }

    private fun updateThemeUI() {
        val bgColor = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#F5F5F5")
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK

        rootLayout.setBackgroundColor(bgColor)
        findViewById<TextView>(R.id.tvChartTitle).setTextColor(textColor)
        findViewById<Switch>(R.id.switchDarkMode).setTextColor(textColor)

        findViewById<RadioButton>(R.id.rbColorCyan).setTextColor(textColor)
        findViewById<RadioButton>(R.id.rbColorPurple).setTextColor(textColor)
        findViewById<RadioButton>(R.id.rbColorGreen).setTextColor(textColor)
        findViewById<RadioButton>(R.id.rbColorOrange).setTextColor(textColor)

        drawChart()
    }

    private fun drawChart() {
        chartContainer.removeAllViews()
        if (matches.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Not enough data to plot."
            tv.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
            chartContainer.addView(tv)
            return
        }
        val customChart = BeautifulLineChart(this, matches, isDarkMode, themeColor)
        chartContainer.addView(customChart)
    }
}

class BeautifulLineChart(
    context: Context,
    private val data: List<MatchResult>,
    private val isDark: Boolean,
    private val themeColor: Int
) : View(context) {

    private val linePaint = Paint().apply {
        color = themeColor
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val pointPaint = Paint().apply {
        color = if(isDark) Color.WHITE else Color.parseColor("#333333")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val valueTextPaint = Paint().apply {
        color = themeColor
        textSize = 45f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val labelTextPaint = Paint().apply {
        color = if (isDark) Color.LTGRAY else Color.DKGRAY
        textSize = 35f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = if (isDark) Color.parseColor("#333333") else Color.parseColor("#DDDDDD")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val paddingX = 120f
        val paddingTop = 150f
        val paddingBottom = 120f
        val w = width.toFloat()
        val h = height.toFloat()
        val graphW = w - 2 * paddingX
        val graphH = h - paddingTop - paddingBottom

        val maxScore = data.maxOf { it.totalScore }.toFloat().coerceAtLeast(10f)
        val minScore = 0f

        val xStep = if (data.size > 1) graphW / (data.size - 1) else graphW / 2

        // Draw grid
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = h - paddingBottom - i * (graphH / gridLines)
            canvas.drawLine(paddingX, y, w - paddingX, y, gridPaint)
            val scoreVal = (minScore + (i * maxScore / gridLines)).toInt()
            canvas.drawText(scoreVal.toString(), paddingX / 2f, y + 10f, labelTextPaint)
        }

        val points = mutableListOf<PointF>()
        for ((i, match) in data.withIndex()) {
            val x = if (data.size == 1) w / 2f else paddingX + i * xStep
            val y = h - paddingBottom - (match.totalScore / maxScore) * graphH
            points.add(PointF(x, y))
        }

        // Draw Smooth Path
        val path = Path()
        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, 15f, pointPaint)
            canvas.drawText(data[0].totalScore.toString(), points[0].x, points[0].y - 40f, valueTextPaint)
        } else {
            path.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i+1]
                val conX1 = (p1.x + p2.x) / 2f
                val conY1 = p1.y
                val conX2 = (p1.x + p2.x) / 2f
                val conY2 = p2.y
                path.cubicTo(conX1, conY1, conX2, conY2, p2.x, p2.y)
            }

            // Draw Gradient Fill
            val fillPath = Path(path)
            fillPath.lineTo(points.last().x, h - paddingBottom)
            fillPath.lineTo(points.first().x, h - paddingBottom)
            fillPath.close()
            val fillPaint = Paint().apply {
                shader = LinearGradient(0f, paddingTop, 0f, h - paddingBottom, themeColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                alpha = 80
                style = Paint.Style.FILL
            }
            canvas.drawPath(fillPath, fillPaint)

            canvas.drawPath(path, linePaint)

            for ((i, p) in points.withIndex()) {
                canvas.drawCircle(p.x, p.y, 15f, pointPaint)
                canvas.drawText(data[i].totalScore.toString(), p.x, p.y - 40f, valueTextPaint)

                val label = if (data[i].teamNumber.isNullOrEmpty()) "M${data[i].matchNumber}" else data[i].teamNumber
                canvas.drawText(label ?: "", p.x, h - paddingBottom + 60f, labelTextPaint)
            }
        }
    }
}