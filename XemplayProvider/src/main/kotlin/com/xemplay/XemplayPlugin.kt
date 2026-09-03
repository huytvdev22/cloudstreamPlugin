package com.xemplay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class XemplayPlugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký XemplayProvider vào hệ sinh thái CloudStream
        registerMainAPI(XemplayProvider())
    }
}
