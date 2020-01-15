package ru.golfnorth.schedule

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class CalendarHelper(lastDay: String) {
    private val last: Calendar
    private val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")

    init {
        last = Calendar.getInstance()
        last.time = dateFormat.parse(lastDay)
    }

    fun getActualDay(): Calendar {
        val now: Calendar = Calendar.getInstance()
        val today = getToday()
        val endOfTheDay = getEndOfTheDay()
        val next = getNextDay()

        return when {
            last.after(endOfTheDay) -> last
            now.after(endOfTheDay) -> next
            else -> today
        }
    }

    fun getNextDay(): Calendar {
        val next: Calendar = Calendar.getInstance()
        next.add(Calendar.DATE, 1)
        next.set(Calendar.HOUR_OF_DAY, 0)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        val dow: Int = next.get(Calendar.DAY_OF_WEEK)

        if (dow == Calendar.SUNDAY)
            next.add(Calendar.DATE, 1)

        return next
    }

    fun getToday(): Calendar {
        val today: Calendar = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        return today
    }

    fun getEndOfTheDay(): Calendar {
        val endOfTheDay: Calendar = Calendar.getInstance()
        endOfTheDay.set(Calendar.HOUR_OF_DAY, 16)
        endOfTheDay.set(Calendar.MINUTE, 15)
        endOfTheDay.set(Calendar.SECOND, 0)
        endOfTheDay.set(Calendar.MILLISECOND, 0)

        return endOfTheDay
    }

    fun getLastDay(): Calendar {
        return last
    }

    fun dateFormat(calendar: Calendar): String {
        return dateFormat.format(calendar.time)
    }
}