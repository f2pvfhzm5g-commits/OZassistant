package com.oz.assistant.core

object AppController {
    @Volatile
    var botEnabled = false

    // Процессы (можно выбрать несколько)
    @Volatile
    var selectedProcesses = mutableSetOf(
        "Инвентаризация",
        "Приемка",
        "Сортировка непрофиль",
        "Производство непрофиль",
        "Размещение"
    )

    // Время (можно выбрать несколько)
    @Volatile
    var selectedTimes: Set<String> = setOf(
        "08:00–20:00"
    )

    // Даты (1..31)
    @Volatile
    var selectedDates: Set<String> = setOf()
}


