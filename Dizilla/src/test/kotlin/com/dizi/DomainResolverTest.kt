package com.dizi

import org.junit.Assert.*
import org.junit.Test

class DomainResolverTest {
    @Test fun sequenceIsNotStarvedByLongMirrorList() {
        val config = SiteDomainConfig(currentUrl="https://dizipal2150.com", prefix="https://dizipal", suffix=".com",
            currentNumber=2150, mirrors=(1..8).map { "https://dead$it.test" })
        val candidates = DomainResolver.candidateUrls(config, config.currentUrl)
        assertTrue(candidates.indexOf("https://dizipal2151.com") < 3)
        assertFalse(candidates.contains(config.currentUrl))
    }
    @Test fun invalidRegistryDestinationsAreRejected() {
        for (url in listOf("https://user:pass@site.test", "https://127.0.0.1", "https://site.test:8080", "file:///x")) {
            assertNull(url, DomainResolver.validOrigin(url))
        }
        assertEquals("https://site.test", DomainResolver.validOrigin("https://site.test/"))
    }
    @Test fun cloudflareAndParkingAreNotHealthyPages() {
        assertTrue(DomainResolver.isBlockedPage("<title>Just a moment...</title>"))
        assertTrue(DomainResolver.isBlockedPage("Buy this domain"))
        assertFalse(DomainResolver.isBlockedPage("DiziBal — online dizi arşivi"))
    }
}
