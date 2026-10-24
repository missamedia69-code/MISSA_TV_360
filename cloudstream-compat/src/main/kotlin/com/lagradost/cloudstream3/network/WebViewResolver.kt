package com.lagradost.cloudstream3.network

import com.missa.tv360.api.PluginLog

class WebViewResolver(
    val regex: Regex,
    val headers: List<String>? = null,
    val extraNetworkRequest: String? = null,
    val applyUrlRegex: Boolean = false,
    val userAgent: String? = null,
    val callback: ((String) -> Unit)? = null,
    val timeout: Long = 60_000L,
) {
    constructor(
        regex: Regex,
        headers: List<String>? = null,
        extraNetworkRequest: String? = null,
        applyUrlRegex: Boolean = false,
        userAgent: String? = null,
        callback: ((String) -> Unit)? = null,
        timeout: Long = 60_000L,
        marker: Any? = null,
    ) : this(regex, headers, extraNetworkRequest, applyUrlRegex, userAgent, callback, timeout)

    fun resolveUsingWebView(url: String): String? {
        PluginLog.e("WebViewResolver non implémenté : $url")
        return null
    }

    fun getInterceptedLink(url: String): String? {
        PluginLog.e("WebViewResolver non implémenté : $url")
        return null
    }
}
