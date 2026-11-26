# Domain güvenilirliği, yeni kaynaklar ve İzlemeli

26 Kasım 2026. Asya dizisi ve anime sağlayıcısı eklenmedi.

## Tamamlanan değişiklikler

- `domain-policy.json` sağlık eşiği ve engel işaretlerini tek yerde tanımlar; Python doğrudan okur, üretici Kotlin/JS sabitlerini oluşturur. Üç katmanda 1500 karakter eşiği kullanılır.
- Domain kontrolü artık DNS, TLS, timeout, challenge, HTTP, kimlik/şablon uyumsuzluğu, izinsiz yönlendirme ve bütçe aşımını ayırır. Tanımlı adresler/izinli hostlar dışındaki yönlendirmeler otomatik kabul edilmez; yol ve query kaybı da reddedilir. Bazı kaynakların parser işaretleri kayıtlıdır; bütün siteler için tam DOM sözleşmesi olduğu iddia edilmez.
- Updater dört siteyi paralel kontrol eder; site başına 45 saniye bütçe uygular. Başarısızlıkta mevcut adresi korur. `checkedAt` ve tüm denemeler ayrı sağlık artifact'ine gider; `updatedAt` dağıtım kaydının değişiklik zamanı olarak kalır. Adres değişirse önceki adres, doğrulama zamanı ve kaynağı kaydedilir.
- Domain Actions job'u eşzamanlı yazmayı engeller, test çalıştırır, artifact ve site bazında özet üretir. Hiçbir site doğrulanamazsa kontrol başarısızdır. Kısmi başarı “bütün siteler oynuyor” anlamına gelmez; raporda her site ayrı görünür.
- Runtime dört aday sınırı kaldırıldı; ayna ve sıralı adaylar dönüşümlü denenir. Kotlin toplam 16 saniyede iptal eder. JS bu süre sonunda yeni aday başlatmaz; sürmekte olan isteğin zaman aşımı host uygulamaya bağlıdır.
- Cloudstream kayıt okumalarını Mutex ile birleştirir; doğrulanmış failover adresini ve son parse edilen registry'yi uygulama depolamasına kaydeder. Ağ kesilince son doğrulanmış adres en fazla yedi gün kullanılır; taze merkezi kayıt önceliklidir. JS hostlar için belgelenmiş ortak kalıcı depolama API'si olmadığından JS'de bellek + gömülü yedek kullanılmaya devam eder.
- `upstreams.json` kaynak repo/commit/lisans/modül eşlemesini tutar. Altı saatte bir çalışan workflow SHA farkını ve karşılaştırma bağlantısını raporlar; yerel uyarlamaları otomatik ezmez. Değişiklik veya kontrol hatası job'u başarısız yapar.
- Nuvio eşleştirmesi iki dilde birlikte yazılan tam adları kabul eder; bir devam filmini kısmi ad benzerliğiyle seçmez. Yapılandırılmış metadata varsa yıl ve Movie/TVSeries türü uyuşmazlığı reddedilir; metadata bulunmayan sayfa doğrulanmış gibi işaretlenmez.
- FirePlayer'ın public `do=getVideo` akışı JS'e eklendi. HLS kontrolü uzun adresleri TCP parçaları arasında eksik okumaz; master/alt liste ve ilk medya parçasını denetler. Tam video indirilmez.

## Yeni kaynaklar

| Kaynak | Paket durumu | Bu bilgisayardaki canlı kanıt |
|---|---|---|
| DiziBal | CS ve JS eklendi, etkin | Matrix filmi ve Breaking Bad S1E1 için HLS; bölümün ilk TS parçası HTTP 200. |
| KültFilmler | CS ve JS eklendi, etkin | Matrix için FirePlayer → HLS → TS parçası HTTP 200. Bazı sunucular TS verisine `text/html` MIME tipi veriyor; test MPEG-TS imzasını da kontrol ediyor. |
| JetFilmizle | CS beta (`status=3`); JS manifestinde varsayılan kapalı | Arama/detay açıldı, player POST istekleri HTTP 403 verdi. Çalışıyor diye sınıflandırılmadı. |

Kaynaklar NeoWatch'ın GPL-3.0 kapsamındaki sabit commitinden uyarlanmıştır; lisans ve atıf `UPSTREAM.md` içindedir. Üç yeni native modülde ortak resolver, URL yeniden yazma ve stream gerçekten üretilince başarı dönme davranışı uygulanmıştır. Her eski upstream ağ isteği bu turda ortak transport'a taşınmadı.

FilmEkseni'nin dizinde bildirilen `filmekseni.vip` adresi DNS hatası verdi; bozuk eklenti sayısını artırmamak için yayımlanmadı. RareFilmm'in incelenen kaynak kodu dili `en`; Türkçe içerik hedefi için eklenmedi. DDizi, DiziGom ve YabanciDizi için bu turda doğrulanmış yeni adaptör yayımlanmadı. Ayrı canlı TV, çocuk ve belgesel paketleri kapsamı genişletilmedi.

## İzlemeli ve hero düzeltmesi

Doğrudan upstream manifest kullanılmaya devam ediyor:

`https://izlemeli.github.io/nuvio/tr/manifest.json`

Katalog yanıtlarında afiş ve arka plan zaten var. PC Nuvio 0.1.39-alpha'da hero açıktı fakat **0 öne çıkan kaynak** seçiliydi. **Ayarlar → Görünüm → Düzen → Ana ekran → Öne çıkan kaynakları** altında **Şu An Popüler — Filmler** ve **En Yüksek Puanlı Diziler — Diziler** seçildi. Ana ekranda büyük arka plan, başlık, yıl, puan ve açıklamanın geldiği görsel olarak doğrulandı. İzlemeli'nin dosyalarını kopyalayan veya önerilerini yeniden yayımlayan bir servis kurulmadı.

Bu PC ayarıdır. Box erişilemedi ve kullanıcı cihazın yanında olmadığını bildirdi; box'ta aynı ayarın eşitlendiği veya yeni sürümlerin çalıştığı varsayılmıyor.

## Doğrulama

- 15 Python testi, 24 Node testi ve 5 Kotlin testi: toplam 44 otomatik kontrol.
- 16 Cloudstream modülünün `make`, `makePluginsJson` ve Dizilla unit-test görevleri yerelde tamamlandı. Eski 13 modül ortak resolver değişikliği nedeniyle sürüm 6; yeni üç modül sürüm 1. Nuvio sağlayıcıları 1.4.0.
- Güncel tüm kaynak HTTP sonuçları: [Nuvio canlı kontrol](nuvio-live-2026-11-26.json).
- DiziBal film: [kanıt](dizibal-live-2026-11-26.json); doğru bölüm: [Breaking Bad S1E1 kanıtı](dizibal-tv-live-2026-11-26.json).
- KültFilmler: [HLS ve medya parçası](kultfilmler-live-2026-11-26.json).
- JetFilmizle: [403 denemeleri](jetfilmizle-live-2026-11-26.json).
- Domain anasayfa kimliği kontrolleri: [ayrı sağlık raporu](domain-health-2026-11-26.json). Bu rapor oynatma testi değildir.

HTTP'den TS parçası almak cihazda decode/altyazı/ses/uzun süreli oynatma doğrulaması değildir. Nuvio PC'de hero düzeltmesi doğrulandı; yeni üç native eklentinin box oynatımı bu turda test edilmedi. Bazı mevcut kaynakların JS istekleri hâlâ challenge/403, ağ hatası veya boş sonuç üretiyor. Cloudstream/NuvioTV WebView ve yerel extractor yolunun davranışı farklı olabilir.

## Kalan işler ve geri dönüş

JS kalıcı önbelleği, tüm eski sağlayıcıların POST/çerez transport birleştirmesi, site bazında tam DOM/API sözleşmesi ve uygulama içinden anlaşılır hata gösterimi tamamlanmış sayılmıyor. Yeni bir katalog/yorum motoru yazılmadı; İzlemeli keşif gereksinimini doğrudan karşılıyor. Her sağlayıcıya dil/kalite/altyazı tercih ekranı eklenmedi. Bu alanlar, mevcut çalışan akışları koruyarak ayrıca ele alınmalı.

Şüpheli domain geçişinde sağlık artifact'indeki `unapproved_redirect` / `parser_mismatch` kaydını inceleyin; doğrulamadan `allowedRedirectHosts` eklemeyin. Geri dönmek için önceki doğrulanmış `domains.json` sürümünü Git geçmişinden geri yükleyip JS gömülü adreslerini `python scripts/generate_nuvio.py` ile yeniden üretin. Cihaza kaydedilen eski failover adresleri 30 dakikalık bellek veya ağ kesintisinde yedi günlük disk sınırına tabidir; taze merkezi registry önceliklidir.
