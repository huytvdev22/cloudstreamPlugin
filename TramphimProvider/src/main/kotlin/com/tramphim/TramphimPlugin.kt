package com.tramphim

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TramphimPlugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider Trạm Phim vào hệ thống CloudStream
        registerMainAPI(TramphimProvider())
    }
}
