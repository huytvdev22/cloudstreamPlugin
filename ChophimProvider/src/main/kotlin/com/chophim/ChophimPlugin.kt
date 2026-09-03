package com.chophim

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ChophimPlugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký ChoPhimProvider vào hệ thống CloudStream
        registerMainAPI(ChophimProvider())
    }
}
