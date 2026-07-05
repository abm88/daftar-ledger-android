package com.daftar.app.core.time

import java.util.Calendar

/** Injectable clock so business rules stay testable. */
interface TimeProvider {
    fun nowMillis(): Long

    fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun startOfMonthMillis(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
