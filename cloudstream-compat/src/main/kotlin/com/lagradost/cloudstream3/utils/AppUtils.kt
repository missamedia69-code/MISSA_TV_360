package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mapper

object AppUtils {
    fun toJson(value: Any?): String = mapper.writeValueAsString(value)
}
