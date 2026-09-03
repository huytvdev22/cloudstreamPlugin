package com.motchill

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MotchillPlugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký MotchillProvider vào hệ sinh thái CloudStream
        registerMainAPI(MotchillProvider())
    }
}
