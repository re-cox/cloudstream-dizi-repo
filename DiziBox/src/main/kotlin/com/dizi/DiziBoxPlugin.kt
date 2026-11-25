// SPDX-License-Identifier: GPL-3.0-only
// Adapted from neoser1984/neowatch (13b96229ed698251827568ff914ba3b6830e16c9); see UPSTREAM.md.
package com.dizi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziBoxPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziBox())
    }
}
