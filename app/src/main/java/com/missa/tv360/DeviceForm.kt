package com.missa.tv360

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceForm {
    fun isTelevision(context: Context): Boolean {
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (manager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
