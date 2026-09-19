package com.gbw.android.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ToastMessage(message: String?, long: Boolean = false) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        val text = message?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        Toast.makeText(
            context,
            text,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
}
