package com.lagradost.cloudstream3.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class CloudflareKiller : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        Log.w("CloudflareKiller", "CloudflareKiller non implémenté en profondeur (pass-through).")
        return chain.proceed(chain.request())
    }
}
