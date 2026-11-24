package com.dizi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DiziMomPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziMom())
    }
}
