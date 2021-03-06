package com.supercilex.robotscouter.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import androidx.net.toUri
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.RobotScouter

fun launchUrl(context: Context, url: Uri) = CustomTabsIntent.Builder()
        .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
        .setShowTitle(true)
        .addDefaultShareMenuItem()
        .enableUrlBarHiding()
        .setStartAnimations(context, R.anim.fui_slide_in_right, R.anim.fui_slide_out_left)
        .setExitAnimations(context, R.anim.slide_in_left, R.anim.slide_out_right)
        .buildWithReferrer()
        .launchUrl(context, url)

private fun CustomTabsIntent.Builder.buildWithReferrer(): CustomTabsIntent {
    val customTabsIntent: CustomTabsIntent = build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        // Add referrer intent
        customTabsIntent.intent.putExtra(
                Intent.EXTRA_REFERRER,
                "${Intent.URI_ANDROID_APP_SCHEME}//${RobotScouter.packageName}".toUri()
        )
    }
    return customTabsIntent
}
