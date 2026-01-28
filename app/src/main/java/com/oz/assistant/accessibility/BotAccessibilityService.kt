package com.oz.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.oz.assistant.bot.DomUtils
import com.oz.assistant.core.BotController
import android.content.Intent
import android.content.pm.PackageManager
import com.oz.assistant.bot.BotConfig

private var lastTickTime = 0L

class BotAccessibilityService : AccessibilityService() {

    private var lastActionTime = System.currentTimeMillis()
    private val TARGET_PACKAGE = "ru.ozon.hire"
    private val triedProcesses = HashSet<String>()
    private var lastLaunchAttempt = 0L

    // Watchdog threshold: если не делаем действий дольше этого времени — перезапустить приложение
    private val STUCK_RESTART_MS = 120_000L // 2 минуты, подрегулируй по необходимости

    private var goingToWarehouses = false

    private fun loadSettings(): Triple<List<String>, List<String>, Pair<String, String>> {
        val prefs = getSharedPreferences("bot_settings", MODE_PRIVATE)

        if (!prefs.contains("cutoff_morning")) {
            prefs.edit().putString("cutoff_morning", "06:00").apply()
        }
        if (!prefs.contains("cutoff_evening")) {
            prefs.edit().putString("cutoff_evening", "18:00").apply()
        }

        val datesStr = prefs.getString("dates", "") ?: ""
        val timeStr = prefs.getString("time", "") ?: ""
        val cutoffMorning = prefs.getString("cutoff_morning", "06:00")!!
        val cutoffEvening = prefs.getString("cutoff_evening", "18:00")!!

        val dates = datesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val times = if (timeStr.isNotEmpty()) listOf(timeStr) else emptyList()

        return Triple(dates, times, Pair(cutoffMorning, cutoffEvening))
    }

    private fun loadSelectedProcess(): String? {
        val prefs = getSharedPreferences("bot_settings", MODE_PRIVATE)
        return prefs.getString("process", null)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("OZ_BOT", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (!BotController.isRunning) return
        if (BotController.isPaused) return

        val root = rootInActiveWindow ?: return

        val now = System.currentTimeMillis()

        // Watchdog: если давно не было действий — считаем, что застряли и перезапускаем Ozon
        if (now - lastActionTime > STUCK_RESTART_MS) {
            Log.w("OZ_BOT", "Stuck detected (idle ${now - lastActionTime} ms). Attempting restart of Ozon app.")
            // очистим внутреннее состояние
            triedProcesses.clear()
            waitingForCalendar = false
            lastProcessClickTime = 0L
            // запустим/перезапустим приложение
            attemptRestartOzon()
            // обновим маркер
            lastActionTime = System.currentTimeMillis()
            return
        }

        if (now - lastTickTime < 300) return
        lastTickTime = now

        tick(root)
    }

    private fun tick(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()

        // ===== ПРОВЕРКА, ЧТО МЫ В OZON =====
        if (root.packageName?.toString() != TARGET_PACKAGE) {
            Log.d("OZ_BOT", "Not in Ozon app, launching it")

            launchOzon()
            lastActionTime = System.currentTimeMillis() + 800
            return
        }

        // ===== ГЛОБАЛЬНЫЙ ТОРМОЗ НА ДЕЙСТВИЯ =====
        if (now - lastActionTime < BotConfig.globalActionDelayMs) return

        // ===== CALENDAR =====
        if (isCalendarScreen(root)) {
            handleCalendar(root)
            return
        }

        // ===== WAITING FOR CALENDAR =====
        if (waitingForCalendar) {
            if (System.currentTimeMillis() - lastProcessClickTime > 800) {
                Log.d("OZ_BOT", "Calendar did not open, resetting to warehouses")
                waitingForCalendar = false
                triedProcesses.clear()
                goToWarehouses(root)
                lastActionTime = System.currentTimeMillis()
            } else {
                Log.d("OZ_BOT", "Waiting for calendar to open...")
            }
            return
        }

        if (isProcessListScreen(root)) {
            handleProcessList(root)
            return
        }

        if (DomUtils.hasText(root, "Выберите склад")) {
            goingToWarehouses = false
            clickFirstWarehouse(root)
            return
        }

        goToWarehouses(root)
    }

    private var waitingForCalendar = false
    private var lastProcessClickTime = 0L

    private fun isProcessListScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Инвентаризация")
                || DomUtils.hasText(root, "Приемка")
                || DomUtils.hasText(root, "Размещение")
                || DomUtils.hasText(root, "Производство")
    }

    private fun handleProcessList(root: AccessibilityNodeInfo) {
        val target = loadSelectedProcess()
        if (target.isNullOrEmpty()) {
            Log.d("OZ_BOT", "No selected process in settings")
            goToWarehouses(root)
            lastActionTime = System.currentTimeMillis()
            return
        }

        Log.d("OZ_BOT", "Looking for selected process: $target")

        val nodes = DomUtils.findAllNodesByText(root, target)

        for (node in nodes) {
            val name = node.text?.toString() ?: continue
            if (triedProcesses.contains(name)) continue

            val clickable = DomUtils.findClickableParent(node)
            if (clickable != null) {
                Log.d("OZ_BOT", "Clicking SELECTED process: $name")

                waitingForCalendar = true
                lastProcessClickTime = System.currentTimeMillis()

                if (!DomUtils.clickNode(clickable)) {
                    DomUtils.tapNode(this, clickable)
                }

                triedProcesses.add(name)
                lastActionTime = System.currentTimeMillis() + BotConfig.afterClickRecordMs
                return
            }
        }

        // ===== СКРОЛЛ =====
        val scrollable = DomUtils.findScrollable(root)
        if (scrollable != null) {
            Log.d("OZ_BOT", "Selected process not visible, scrolling")
            DomUtils.scrollDown(scrollable)
            lastActionTime = System.currentTimeMillis() + BotConfig.afterScrollMs
            return
        }

        Log.d("OZ_BOT", "Selected process not found, resetting")
        triedProcesses.clear()
        goToWarehouses(root)
        lastActionTime = System.currentTimeMillis()
    }

    private fun clickFirstWarehouse(root: AccessibilityNodeInfo) {
        val list = DomUtils.findAllNodesByText(root, "Записаться")
        if (list.isNotEmpty()) {
            val clickable = DomUtils.findClickableParent(list.first())
            if (clickable != null) {
                DomUtils.tapNode(this, clickable)
                lastActionTime = System.currentTimeMillis() + BotConfig.afterClickRecordMs
            }
        }
    }

    private fun goToWarehouses(root: AccessibilityNodeInfo) {
        if (goingToWarehouses) return

        val tabNode = DomUtils.findNodeByDesc(root, "warehouseTab")
        if (tabNode != null) {
            goingToWarehouses = true
            DomUtils.tapNodeByBounds(this, tabNode)
            lastActionTime = System.currentTimeMillis() + BotConfig.afterOpenWarehousesMs
        }
    }

    private fun isCalendarScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Записывайтесь")
                || DomUtils.hasText(root, "Выберите время")
                || DomUtils.hasText(root, "Записаться")
    }

    private fun isTimePickerOpen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "��ыберите время")
    }

    private fun handleCalendar(root: AccessibilityNodeInfo) {
        if (isTimePickerOpen(root)) {
            handleTimePicker(root)
            return
        }

        if (DomUtils.hasText(root, "Записаться")) {
            if (clickConfirm(root)) {
                triedProcesses.clear()
                waitingForCalendar = false
            }
            return
        }

        handleDatePicker(root)
    }

    private fun handleDatePicker(root: AccessibilityNodeInfo) {
        val (dates, times, cutoffs) = loadSettings()
        val (cutoffMorning, cutoffEvening) = cutoffs

        if (dates.isEmpty() || times.isEmpty()) {
            goToWarehouses(root)
            lastActionTime = System.currentTimeMillis()
            return
        }

        val selectedTime = times.first()

        val allowedDates = dates.filter { d ->
            val day = d.toIntOrNull() ?: return@filter false
            BotController.isDateAllowed(day, selectedTime, cutoffMorning, cutoffEvening)
        }

        for (d in allowedDates) {
            val nodes = DomUtils.findAllNodesByText(root, d)
            for (n in nodes) {
                val clickable = DomUtils.findClickableParent(n) ?: continue
                val id = clickable.viewIdResourceName ?: ""
                if (!id.contains("availableShift")) continue

                DomUtils.tapNode(this, clickable)
                lastActionTime = System.currentTimeMillis() + BotConfig.afterPickDateMs
                return
            }
        }

        goToWarehouses(root)
        lastActionTime = System.currentTimeMillis()
    }

    private fun handleTimePicker(root: AccessibilityNodeInfo) {
        val (_, times, _) = loadSettings()
        if (times.isEmpty()) return

        for (t in times) {
            val nodes = DomUtils.findAllNodesByText(root, t)
            for (n in nodes) {
                val clickable = DomUtils.findClickableParent(n) ?: continue
                DomUtils.tapNode(this, clickable)
                lastActionTime = System.currentTimeMillis() + BotConfig.afterPickTimeMs
                return
            }
        }
    }

    private fun clickConfirm(root: AccessibilityNodeInfo): Boolean {
        val nodes = DomUtils.findAllNodesByText(root, "Записаться")
        if (nodes.isNotEmpty()) {
            val btn = DomUtils.findClickableParent(nodes.first()) ?: return false
            DomUtils.tapNode(this, btn)
            lastActionTime = System.currentTimeMillis() + BotConfig.afterClickRecordMs
            return true
        }
        return false
    }

    private fun attemptRestartOzon() {
        val now = System.currentTimeMillis()
        if (now - lastLaunchAttempt < 800) {
            Log.d("OZ_BOT", "Skipping restart: cooldown")
            return
        }
        lastLaunchAttempt = now

        try {
            // Попробуем получить LaunchIntent для пакета сначала (более корректно)
            val pm: PackageManager = packageManager
            var intent: Intent? = pm.getLaunchIntentForPackage(TARGET_PACKAGE)

            if (intent == null) {
                // fallback: explicit activity we used before
                intent = Intent().apply {
                    setClassName(
                        TARGET_PACKAGE,
                        "ru.ozon.android.inAppUpdateFacade.activity.InAppUpdateLauncherActivity"
                    )
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            Log.d("OZ_BOT", "Attempting restart/launch of Ozon")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("OZ_BOT", "Failed to restart Ozon", e)
        }
    }

    private fun launchOzon() {
        // keep old method but delegate to attemptRestartOzon for reuse
        attemptRestartOzon()
    }

    override fun onInterrupt() {}
}