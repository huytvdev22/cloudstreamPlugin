package com.vieflix

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * EntryPoint của Plugin Vieflix.
 * Độc lập hoàn toàn với các Plugin khác.
 */
@CloudstreamPlugin
class VieflixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VieflixProvider())
    }
}
