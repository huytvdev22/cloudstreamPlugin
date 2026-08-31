package com.animevietsub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * EntryPoint của Plugin AnimeVietsub.
 * Độc lập hoàn toàn với các Plugin khác.
 */
@CloudstreamPlugin
class AnimeVietsubPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeVietsubProvider())
    }
}
