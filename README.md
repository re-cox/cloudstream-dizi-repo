# CloudStream & Nuvio Özel Dizi/Film Repo (`re-cox`)

Değişen site adreslerini (domain) otomatik takip eden **CloudStream 3** ve **Nuvio** uyumlu Türkçe dizi/film reposu.

> **26 Kasım 2026:** Sağlayıcılar güncel sayfa ve oynatıcı biçimlerine uyarlandı. 13 `.cs3` paketi derlendi; NuvioTV cihazında altı kaynaktan örnek bölüm bağlantısı alındı, DiziYou için kısa oynatma doğrulandı. Tüm siteler için kesintisiz erişim garantisi yoktur. [Test sonuçları ve kaynak durumu](docs/DURUM.md).

## Uygulamalara Ekleme Bağlantıları

### CloudStream 3
**Ayarlar → Eklentiler (Extensions) → Depo Ekle (Add Repository):**
```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/repo.json
```

### NuvioTV (box için önerilen)

**Ayarlar → İçerik ve Keşif → Uzantılar → Depo ekle:**
```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/repo.json
```

NuvioTV `.cs3` paketlerini doğrudan destekler. Bu yol, siteye özel çözücüleri ve gerektiğinde uygulamanın WebView ortamını kullanır.

### Nuvio JS / diğer uyumlu istemciler
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
   - CloudStream'in ortak `getDoc` / `getText` akışında ve Nuvio site isteklerinde bağlantı/engel hatası olursa alternatif adresler denenir. 404 hatası domain değişikliği sayılmaz.
   - Deneme bütçesi en fazla dört alternatif adrestir; sıralı adaylar `N+1..N+3` ile sınırlıdır. Bu mekanizma eski sayfa yollarını veya siteye özel oynatıcıları kendiliğinden onarmaz. Ortak akışın dışındaki JSON/POST istekleri ayrıca ele alınmalıdır.

## Yerel kontroller

```powershell
python scripts/generate_nuvio.py
python -m unittest discover -s scripts -v
node --test scripts/test_nuvio.cjs
python scripts/check_sites.py --output site-health.json
```

Nuvio kaynakları `scripts/nuvio_provider.js` şablonundan ve lisans bilgisi `scripts/vendor/` altında bulunan yerel kripto yardımcılarından üretilir. Şablonu değiştirdikten sonra üreticiyi çalıştırın. Testler eşleşme, bölüm seçimi, adres geçişi ve temel bağlantı çözümünü denetler; 13 sitenin canlı oynatma garantisi değildir. `check_sites.py` yalnızca ana sayfalara erişimi ölçer ve domain listesini değiştirmez.

CloudStream derlemesi (JDK 17+ ve Android SDK 34 gerekir):

```powershell
.\gradlew.bat make makePluginsJson :Dizilla:testDebugUnitTest
```

Canlı Nuvio HTTP denetimi (medyanın tamamını indirmez; başarısız kaynak varsa çıkış kodu 1):

```powershell
node scripts/check_nuvio_live.cjs --output site-streams.json
```

Kaynak ve çıkarıcı uyarlamaları GPL-3.0 kapsamındadır; katkı bilgisi [UPSTREAM.md](UPSTREAM.md), üçüncü taraf lisansları `scripts/vendor/` altındadır.
