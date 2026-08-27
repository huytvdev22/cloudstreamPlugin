package com.ophim

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * EntryPoint của Plugin OPhim.
 * Đăng ký OPhimProvider vào Cloudstream khi plugin được nạp.
 */
@CloudstreamPlugin
class OPhimPlugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider OPhim
        registerMainAPI(OPhimProvider())
    }
}
