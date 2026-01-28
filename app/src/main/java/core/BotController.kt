package com.oz.assistant.core

object BotController {
    var isRunning = false
    var isPaused = false

    fun start() {
        isRunning = true
        isPaused = false
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        isRunning = false
        isPaused = false
    }
    private fun parseTimeToMinutes(t: String): Int {
        val parts = t.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun nowMinutes(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun todayDay(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    // shiftTime example: "20:00–08:00"
    fun isDateAllowed(date: Int, shiftTime: String, cutoffMorning: String, cutoffEvening: String): Boolean {
        val today = todayDay()
        if (date != today) return true

        val nowMin = nowMinutes()

        val cutoffMorningMin = parseTimeToMinutes(cutoffMorning)
        val cutoffEveningMin = parseTimeToMinutes(cutoffEvening)

        // determine shift type by start time
        val start = shiftTime.split("–")[0]  // "20:00"
        val startMin = parseTimeToMinutes(start)

        val isNightShift = startMin >= 18 * 60  // всё что начинается после 18:00 считаем ночным

        return if (isNightShift) {
            nowMin < cutoffEveningMin
        } else {
            nowMin < cutoffMorningMin
        }
    }
}
