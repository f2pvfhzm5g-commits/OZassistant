package com.oz.assistant.core

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import com.oz.assistant.R
import java.text.SimpleDateFormat
import java.util.*

class CalendarDialog(
    context: Context,
    private val onResult: (selectedDays: Set<String>) -> Unit
) : Dialog(context) {

    private val selectedDays = mutableSetOf<String>()

    private var currentYear: Int
    private var currentMonth: Int

    private lateinit var tvMonthTitle: TextView
    private lateinit var gridDays: GridLayout
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnOk: Button
    private lateinit var btnCancel: Button

    init {
        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)

        val view = LayoutInflater.from(context).inflate(R.layout.panel_calendar, null)
        setContentView(view)

        tvMonthTitle = view.findViewById(R.id.tvMonthTitle)
        gridDays = view.findViewById(R.id.gridDays)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnOk = view.findViewById(R.id.btnOk)
        btnCancel = view.findViewById(R.id.btnCancel)

        btnPrev.setOnClickListener {
            currentMonth--
            if (currentMonth < 0) {
                currentMonth = 11
                currentYear--
            }
            renderCalendar()
        }

        btnNext.setOnClickListener {
            currentMonth++
            if (currentMonth > 11) {
                currentMonth = 0
                currentYear++
            }
            renderCalendar()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnOk.setOnClickListener {
            onResult(selectedDays)
            dismiss()
        }

        renderCalendar()
    }

    private fun renderCalendar() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, currentYear)
        cal.set(Calendar.MONTH, currentMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val titleFmt = SimpleDateFormat("LLLL yyyy", Locale("ru"))
        tvMonthTitle.text = titleFmt.format(cal.time).replaceFirstChar { it.uppercase() }

        gridDays.removeAllViews()

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Пустые ячейки до первого дня
        for (i in 1 until firstDayOfWeek) {
            val space = Space(context)
            gridDays.addView(space, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }

        for (day in 1..daysInMonth) {
            val btn = ToggleButton(context)
            btn.textOn = day.toString()
            btn.textOff = day.toString()
            btn.text = day.toString()

            val key = String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, day)

            btn.isChecked = selectedDays.contains(key)

            btn.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedDays.add(key) else selectedDays.remove(key)
            }

            gridDays.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }
}
