package com.dizi

import org.junit.Assert.*
import org.junit.Test

class AdPolicyTest {
    @Test fun explicitAdsAndEncodedPathsAreRejected() {
        listOf("https://ads.example.com/opaque.mp4", "https://cdn.example.com/%61ds/opaque.mp4",
            "https://cdn.example.com/preroll.mp4", "https://cdn.example.com/v.mp4?is_ad=true",
            "https://ad.doubleclick.net/opaque", "https://cdn.example.com/v?type=vast").forEach {
            assertTrue(it, AdPolicy.isAdvertisingUrl(it))
        }
    }
    @Test fun signedTokensAndSimilarNamesRemainUntouched() {
        listOf("https://cdn.example.com/shadow/adventure.m3u8?token=ads&exp=123",
            "https://cdn.example.com/download/video.mp4?is_ad=false",
            "https://doubleclick.net.example.com/movie.m3u8", "https://cdn.example.com/adaptive/movie.mp4").forEach {
            assertFalse(it, AdPolicy.isAdvertisingUrl(it))
        }
    }
}
