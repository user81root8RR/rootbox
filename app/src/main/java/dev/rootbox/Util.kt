package dev.rootbox

import android.content.Context

fun Context.dp(i: Int): Int = (i * resources.displayMetrics.density).toInt()
