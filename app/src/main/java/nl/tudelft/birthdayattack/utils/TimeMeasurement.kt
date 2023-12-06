package nl.tudelft.birthdayattack.utils

import android.util.Log

inline fun <T> measurePerformanceInMS(logger: (Long) -> Unit, function: () -> T): T {
    val startTime = System.currentTimeMillis()
    val result: T = function.invoke()
    val endTime = System.currentTimeMillis()
    logger.invoke( endTime - startTime)
    return result
}

//the logger function
fun logTime(time: Long){
    Log.d("TIME","PERFORMANCE IN MS: $time ms ")
}

