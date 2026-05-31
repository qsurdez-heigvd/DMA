package ch.heigvd.iict.dma.labo2

import java.util.Calendar
import java.util.Locale

/**
 * Converts an epoch  timestamp in milliseconds to a readable time string.
 *
 * @param millis Epoch time in milliseconds.
 * @return A string formatted as HH:mm:ss in the device's default locale.
 *
 * @author 
 * @author 
 * @author Quentin Surdez
 */
fun convertMillisToTimeFormat(millis: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = millis
    }
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
