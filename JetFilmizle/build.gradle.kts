version = 2

cloudstream {
    authors = listOf("re-cox", "NeO", "neoser1984")
    language    = "tr"
    description = "Deneysel: player API erişimi doğrulanamadı (HTTP 403)."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status = 3 // Experimental: player API returned HTTP 403 in live tests.
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=jetfilmizle.app&sz=%size%"
}