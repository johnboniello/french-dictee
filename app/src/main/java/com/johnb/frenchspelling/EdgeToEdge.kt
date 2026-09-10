package com.johnb.frenchspelling

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * From targetSdk 35 the OS forces edge-to-edge: content draws under the status
 * and navigation bars. The AppCompat action bar already offsets the top, so we
 * only need to pad the content view for the nav bar (and side cutouts) so the
 * last controls in a scrolling screen aren't hidden.
 */
fun Activity.padForSystemBars() {
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(bars.left, v.paddingTop, bars.right, bars.bottom)
        insets
    }
}
