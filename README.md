# CloudStream & Nuvio Özel Dizi/Film Repo (`re-cox`)

Değişen site adreslerini (domain) otomatik takip eden **CloudStream 3** ve **Nuvio** uyumlu Türkçe dizi/film reposu.

## Uygulamalara Ekleme Bağlantıları

### CloudStream 3
**Ayarlar → Eklentiler (Extensions) → Depo Ekle (Add Repository):**
```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/repo.json
```

### Nuvio / NuvioTR
**Ayarlar → Eklentiler / Plugins → Depo Ekle:**
```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/nuvio/manifest.json
```

---

## Dahil Edilen 13 Kaynak

| Eklenti | Tür | Takip Stratejisi | Başlangıç Adresi |
| :--- | :--- | :--- | :--- |
| **DiziPal** | Dizi & Film | Duyuru + Sıralı Numara | `https://dizipal2150.com` |
| **HDFilmCehennemi** | Film & Dizi | Yönlendirme + TLD Mirror | `https://www.hdfilmcehennemi.nl` |
| **DiziBox** | Dizi | Yönlendirme + TLD Mirror | `https://www.dizibox.live` |
| **Dizilla** | Dizi | Yönlendirme + TLD Mirror | `https://dizilla.now` |
| **DiziYou** | Dizi | Yönlendirme + TLD Mirror | `https://www.diziyou.one` |
| **SezonlukDizi** | Dizi | Yönlendirme + TLD Mirror | `https://sezonlukdizi.cc` |
| **FilmModu** | Film | Yönlendirme + TLD Mirror | `https://filmmodu.news` |
| **DiziMom** | Dizi & Film | Yönlendirme + TLD Mirror | `https://ddizimom.net` |
| **FullHDFilmizlesene** | Film | Yönlendirme + TLD Mirror | `https://www.fullhdfilmizlesene.now` |
| **FilmMakinesi** | Film | Yönlendirme + TLD Mirror | `https://filmmakinesi.to` |
| **WebteIzle** | Film | Yönlendirme + TLD Mirror | `https://webteizle.info` |
| **SelcukFlix** | Dizi & Film | Yönlendirme + TLD Mirror | `https://selcukflix.app` |
| **SetFilmIzle** | Film & Dizi | Yönlendirme + TLD Mirror | `https://www.setfilmizle.ltd` |

---

## Otomatik Domain Takibi Nasıl Çalışır?

1. **GitHub Actions Zamanlayıcısı (`.github/workflows/update-domains.yml`)**
   - Her **4 saatte bir** `scripts/update_domains.py` scriptini çalıştırır.
   - HTTP yönlendirmelerini (301/302) takip eder, BTK engel sayfalarını ve domain park sayfalarını (`sedoparking`, `buy this domain` vb.) eler.
   - Sayfa içi duyuruları (ör. *"Sonraki adresimiz Dizipal2151.com olacaktır"*) okuyup `nextUrl` olarak önceden kaydeder.
   - Mevcut adres kapandığında sırayla `nextUrl` → `mirrors` (TLD alternatifleri) → sıralı numara (`+1 .. +15`) taraması yaparak `domains.json` dosyasını otomatik günceller.
2. **Uygulama İçi Canlı Çözümleyici (CloudStream & Nuvio)**
   - Hem CloudStream eklentileri (`common/src/main/kotlin/com/dizi/DomainResolver.kt`) hem de Nuvio JS eklentileri (`nuvio/providers/*.js`) güncel adresi doğrudan `domains.json` üzerinden çeker.
   - GitHub Actions henüz çalışmadan adres değişmişse uygulama içinde `nextUrl`, `mirrors` ve `N+1..N+5` denemelerini kendisi yapar.
