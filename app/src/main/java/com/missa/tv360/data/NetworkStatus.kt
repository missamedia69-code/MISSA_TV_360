package com.missa.tv360.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkStatus {
    @Suppress("DEPRECATION")
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return manager.activeNetworkInfo?.isConnected == true
        }
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
