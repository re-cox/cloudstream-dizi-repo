# CloudStream & Nuvio Özel Dizi/Film Repo (`re-cox`)

Değişen site adreslerini (domain) otomatik takip eden **CloudStream 3** ve **Nuvio** uyumlu Türkçe dizi/film reposu.

> **26 Kasım 2026 güncellemesi:** Domain kimlik kontrolü, sağlık raporu, kalıcı Cloudstream adres önbelleği ve upstream değişiklik takibi eklendi. DiziBal ve KültFilmler yeni kaynaklar; JetFilmizle erişim sorunu nedeniyle deneysel/varsayılan kapalı. 16 CS paketi derleniyor; bu sayı çalışan site sayısı değildir. [Güncel doğrulama ve kalan sınırlar](docs/GUNCELLEME-2026-11-26.md).

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

## Dahil Edilen Kaynaklar

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

| **DiziBal** | Dizi & Film | Kimlik kontrolü | `https://dizibal.org` |
| **KültFilmler** | Film & Dizi | Kimlik kontrolü | `https://kultfilmler.net` |
| **JetFilmizle (deneysel)** | Film & Dizi | Kimlik kontrolü | `https://jetfilmizle.app` |

---

## Otomatik Domain Takibi Nasıl Çalışır?

1. **GitHub Actions Zamanlayıcısı (`.github/workflows/update-domains.yml`)**
   - Her **4 saatte bir** `scripts/update_domains.py` scriptini çalıştırır.
   - Yalnız tanımlı/izinli hedeflere giden yönlendirmeleri ve site şablonunu kabul eder; BTK engel sayfalarını ve domain park sayfalarını (`sedoparking`, `buy this domain` vb.) eler.
   - Her site için en fazla 45 saniye kontrol bütçesi, dört paralel kontrol, ayrı sağlık artifact'i ve Actions özeti üretir. Hiçbir site doğrulanamazsa hata koduyla çıkar.
   - Sayfa içi duyuruları (ör. *"Sonraki adresimiz Dizipal2151.com olacaktır"*) okuyup `nextUrl` olarak önceden kaydeder.
   - Mevcut adres kapandığında sırayla `nextUrl` → `mirrors` (TLD alternatifleri) → sıralı numara (`+1 .. +15`) taraması yaparak `domains.json` dosyasını otomatik günceller.
2. **Uygulama İçi Canlı Çözümleyici (CloudStream & Nuvio)**
   - Hem CloudStream eklentileri (`common/src/main/kotlin/com/dizi/DomainResolver.kt`) hem de Nuvio JS eklentileri (`nuvio/providers/*.js`) güncel adresi doğrudan `domains.json` üzerinden çeker.
   - CloudStream'in ortak `getDoc` / `getText` akışında ve Nuvio site isteklerinde bağlantı/engel hatası olursa alternatif adresler denenir. 404 hatası domain değişikliği sayılmaz.
   - Ayna ve sıralı adaylar dönüşümlü denenir. Cloudstream denemeleri toplam 16 saniye ile sınırlıdır; JS tarafı da yeni denemeleri 16 saniye sonunda durdurur, tek isteğin zaman aşımı istemcinin fetch ortamına bağlıdır. Sıralı adaylar `N+1..N+3` ile sınırlıdır. Bu mekanizma eski sayfa yollarını veya siteye özel oynatıcıları kendiliğinden onarmaz. Ortak akışın dışındaki JSON/POST istekleri ayrıca ele alınmalıdır.

## Yerel kontroller

```powershell
python scripts/generate_nuvio.py
python -m unittest discover -s scripts -v
node --test scripts/test_nuvio.cjs scripts/test_live_checker.cjs
python scripts/check_sites.py --output site-health.json
```

Nuvio kaynakları `scripts/nuvio_provider.js` şablonundan ve lisans bilgisi `scripts/vendor/` altında bulunan yerel kripto yardımcılarından üretilir. Şablonu değiştirdikten sonra üreticiyi çalıştırın. Testler eşleşme, bölüm seçimi, adres geçişi ve temel bağlantı çözümünü denetler; bütün sitelerin canlı oynatma garantisi değildir. `check_sites.py` yalnızca ana sayfalara erişimi ölçer ve domain listesini değiştirmez.

CloudStream derlemesi (JDK 17+ ve Android SDK 34 gerekir):

```powershell
.\gradlew.bat make makePluginsJson :Dizilla:testDebugUnitTest
```

Canlı Nuvio HTTP denetimi (medyanın tamamını indirmez; başarısız kaynak varsa çıkış kodu 1):

```powershell
node scripts/check_nuvio_live.cjs --output site-streams.json
```

Kaynak ve çıkarıcı uyarlamaları GPL-3.0 kapsamındadır; katkı bilgisi [UPSTREAM.md](UPSTREAM.md), üçüncü taraf lisansları `scripts/vendor/` altındadır.

## İzlemeli kataloğu ve hero alanı

İzlemeli'yi kendi güncel manifestinden **doğrudan** kullanın; akış sağlayıcılarımızla birlikte çalışır:

```text
https://izlemeli.github.io/nuvio/tr/manifest.json
```

PC Nuvio'da **Ayarlar → Görünüm → Düzen → Ana ekran → Öne çıkan kaynakları** bölümünden en fazla iki katalog seçin. Önerilen: **Şu An Popüler — Filmler** ve **En Yüksek Puanlı Diziler — Diziler**. “Öne çıkanları göster” açık olsa bile sıfır kaynak seçiliyse hero alanı boş kalabilir. Bu iki seçim PC uygulamasında yapıldı ve arka plan görseliyle doğrulandı. Box'ta aynı ayar ayrıca kontrol edilmelidir; eşitlendiği varsayılmıyor.

Kataloğun güncellemelerini almak için kopyasını yayımlamıyoruz. Kaynak eklentileri ile katalog eklentisi farklı işler yapar.

## Nuvio içinde Canlı TV

TRT 1, TV8, Kanal D, ATV, NOW / FOX, Star TV, Show TV, Kanal 7, Beyaz TV, tv2, TV8,5, TV8 Int, A2 TV ve NTV için küçük canlı kanal paketi:

```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/live/manifest.json
```

Nuvio'nun katalog eklentisi olarak eklenir. Üç kanalın HLS ve ilk video parçası HTTP kontrolünden geçti; box oynatma testi akşam yapılacak. [Kanal paketi ve kaynak bilgileri](live/README.md). HDMI 2'deki uydu alıcısından bağımsız olarak internetten oynatır.
