package com.oz.assistant.overlay

import android.app.Dialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import com.oz.assistant.R
import com.oz.assistant.core.BotController

class OverlayService : Service() {

    private val processes = listOf(
        "Инвентаризация",
        "Приемка",
        "Сортировка непрофиль",
        "Производство непрофиль",
        "Размещение"
    )

    private val times = listOf(
        "08:00–20:00",
        "20:00–08:00",
        "12:00–20:00",
        "20:00–05:00",
        "10:00–22:00",
        "09:00–21:00"
    )

    private lateinit var prefs: android.content.SharedPreferences
    private var selectedDates = mutableSetOf<String>()

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("bot_settings", Context.MODE_PRIVATE)

        val savedDates = prefs.getString("dates", "") ?: ""
        selectedDates = savedDates.split(",").filter { it.isNotBlank() }.toMutableSet()

        initOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ================== INIT OVERLAY ==================

    private fun initOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_card, null)

        createParams()
        windowManager.addView(overlayView, params)
        makeDraggable(overlayView)

        setupMenuUI()
        updateMenuTexts()
    }

    // ================== UI ==================

    private fun setupMenuUI() {
        val rowProcess = overlayView.findViewById<View>(R.id.rowProcess)
        val rowTime = overlayView.findViewById<View>(R.id.rowTime)
        val rowDays = overlayView.findViewById<View>(R.id.rowDays)

        val btnStart = overlayView.findViewById<Button>(R.id.btnStart)
        val btnClose = overlayView.findViewById<Button>(R.id.btnClose)

        rowProcess.setOnClickListener { showProcessDialog() }
        rowTime.setOnClickListener { showTimeDialog() }
        rowDays.setOnClickListener { showDatesDialog() }

        btnStart.setOnClickListener {
            val process = prefs.getString("process", null)
            val time = prefs.getString("time", null)

            if (process.isNullOrEmpty() || time.isNullOrEmpty() || selectedDates.isEmpty()) {
                Toast.makeText(this, "Заполни все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            BotController.start()
            switchToStatusView() // Теперь меню пропадет и появится окно статуса
        }

        btnClose.setOnClickListener {
            BotController.stop()
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // ================== DIALOGS ==================

    private fun showProcessDialog() {
        showListDialog("Выбор процесса", processes) {
            prefs.edit().putString("process", it).apply()
            updateMenuTexts()
        }
    }

    private fun showTimeDialog() {
        showListDialog("Выбор времени", times) {
            prefs.edit().putString("time", it).apply()
            updateMenuTexts()
        }
    }

    private fun showDatesDialog() {
        val dialog = Dialog(this)
        // Убираем белую рамку и фон стандартного диалога (Скрин 3)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.window?.let {
            it.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_days_grid, null)
        dialog.setContentView(view)

        val grid = view.findViewById<GridLayout>(R.id.gridDays)
        val tvMonthTitle = view.findViewById<TextView>(R.id.tvMonthTitle)

        tvMonthTitle.text = "Январь 2026" // Или используй динамическое название
        grid.removeAllViews()

        val daysInMonth = java.time.YearMonth.now().lengthOfMonth()
        val today = java.time.LocalDate.now().dayOfMonth

        for (day in 1..daysInMonth) {
            val tv = TextView(this)
            tv.text = day.toString()
            tv.gravity = Gravity.CENTER
            tv.setTextColor(0xFFFFFFFF.toInt())

            val dayStr = day.toString()

            // Функция для правильной отрисовки стиля из твоих drawable
            fun updateStyle() {
                when {
                    selectedDates.contains(dayStr) -> tv.setBackgroundResource(R.drawable.bg_day_cell_selected) // Зеленый
                    day == today -> tv.setBackgroundResource(R.drawable.bg_day_cell_today) // С обводкой
                    else -> tv.setBackgroundResource(R.drawable.bg_day_cell) // Обычный темный
                }
            }

            updateStyle()

            tv.setOnClickListener {
                if (selectedDates.contains(dayStr)) selectedDates.remove(dayStr)
                else selectedDates.add(dayStr)
                updateStyle()
            }

            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = 110 // Высота ячейки
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.setMargins(8, 8, 8, 8)
            tv.layoutParams = lp
            grid.addView(tv)
        }

        view.findViewById<Button>(R.id.btnOk).setOnClickListener {
            prefs.edit().putString("dates", selectedDates.joinToString(",")).apply()
            updateMenuTexts()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showListDialog(title: String, items: List<String>, onSelect: (String) -> Unit) {
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Убирает белое (Скрин 2)

        dialog.window?.let {
            it.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
        }

        val view = LayoutInflater.from(this).inflate(R.layout.panel_list, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text = title
        val container = view.findViewById<LinearLayout>(R.id.containerItems)
        container.removeAllViews()

        for (item in items) {
            val tv = TextView(this)
            tv.text = item
            tv.setPadding(40, 35, 40, 35)
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.setBackgroundResource(R.drawable.bg_dark_row) // Твой темный фон для строк

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8)
            tv.layoutParams = lp

            tv.setOnClickListener {
                onSelect(item)
                dialog.dismiss()
            }
            container.addView(tv)
        }
        dialog.show()
    }

    private fun switchToStatusView() {
        // Удаляем старое меню
        windowManager.removeView(overlayView)

        // Загружаем новое окно статуса (Скрин 5)
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_status, null)

        // Настраиваем текст в новом окне
        val process = prefs.getString("process", "")
        val time = prefs.getString("time", "")
        val dates = selectedDates.joinToString(",")

        overlayView.findViewById<TextView>(R.id.txtStatus).text = "$process | $dates | $time"

        // Оживляем кнопки в новом окне
        overlayView.findViewById<Button>(R.id.btnStop).setOnClickListener {
            BotController.stop()
            stopSelf()
        }

        // Снова делаем его перетаскиваемым и добавляем на экран
        windowManager.addView(overlayView, params)
        makeDraggable(overlayView)
    }

    // ================== UPDATE UI ==================

    private fun updateMenuTexts() {
        val tvProcessValue = overlayView.findViewById<TextView>(R.id.tvProcessValue)
        val tvTimeValue = overlayView.findViewById<TextView>(R.id.tvTimeValue)
        val tvDaysValue = overlayView.findViewById<TextView>(R.id.tvDaysValue)

        tvProcessValue.text = prefs.getString("process", "Не выбран")
        tvTimeValue.text = prefs.getString("time", "Не выбран")

        val dates = prefs.getString("dates", "")
        if (dates.isNullOrEmpty()) {
            tvDaysValue.text = "Не выбраны"
        } else {
            tvDaysValue.text = "Выбрано: ${dates.split(",").size} дн."
        }
    }

    // ================== WINDOW ==================

    private fun createParams() {
        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300
    }

    private fun makeDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }
}
