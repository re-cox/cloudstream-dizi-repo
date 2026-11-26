package com.dizi

// Generated from domain-policy.json by scripts/generate_nuvio.py.
object DomainPolicy {
    const val minBodyLength = 1500
    const val runtimeBudgetMs = 16000L
    const val probeTimeoutSeconds = 4
    val blockedMarkers = listOf("bilgi teknolojileri ve iletişim kurumu", "erişime engellenmiştir", "access to this site has been blocked", "5651 sayılı kanun", "buy this domain", "domain is for sale", "sedoparking", "dan.com", "afternic", "godaddy", "parked free", "cf-chl-", "<title>just a moment", "<title>access denied")
}
