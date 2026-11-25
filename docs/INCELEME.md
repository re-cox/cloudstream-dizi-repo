# İlk inceleme — 25 Kasım 2026

> Bu belge ilk aşamanın tarihsel kaydıdır. Güncel düzeltmeler, derleme ve cihaz testleri için [son duruma](DURUM.md) bakın.

Sorun yalnızca domain değişikliği değil. Mevcut kodda eski sayfa yolları, siteyle uyuşmayan HTML seçicileri, kullanılmayan domain yedekleme fonksiyonu ve eksik Nuvio oynatıcı desteği var. Derlemenin veya sözdizimi kontrolünün geçmesi, bir içeriğin bulunup oynatılabildiğini göstermiyor.

## Canlı erişim kontrolü

Bu bilgisayarın bağlantısından standart HTTP istekleriyle alınan sonuçlar aşağıdadır. HTTP 403 tek başına sitenin kapandığını veya BTK engelini kanıtlamaz; Cloudflare, IP veya istemci kısıtlaması olabilir. Ana sayfanın açılması da eklentinin çalıştığı anlamına gelmez.

| Kaynak | Ana sayfa | Ek bulgu / kalan iş |
| --- | --- | --- |
| DiziPal | Zaman aşımı; başka denemede TLS sertifika hatası | Güncel adres, arama ve oynatıcı ayrıca doğrulanmalı. TLS doğrulaması kapatılmadı. |
| HDFilmCehennemi | HTTP 403 | Uygulamanın Cloudflare oturumuyla ve kaynak oynatıcılarıyla denenmeli. |
| DiziBox | HTTP 403 | Arama, sezon listesi ve oynatıcı henüz doğrulanmadı. |
| Dizilla | HTTP 200 | `/tum-bolumler` üzerinde mevcut katalog seçicisi 0 sonuç verdi. Nuvio'nun `/bg/searchcontent` araması HTML varsayıyor; güncel arama/veri modeli ayrıca uyarlanmalı. |
| DiziYou | HTTP 200 | Aşağıdaki somut hatalar düzeltildi; örnek bölümün iki HLS manifesti doğrulandı. |
| SezonlukDizi | HTTP 200 | İlk katalog yolu ve `div.afis a` seçicisi 36 bağlantı buldu. Bölüm ve AJAX oynatıcı zinciri Nuvio için ayrıca uygulanmalı. |
| FilmModu | Zaman aşımı | Hem kayıtlı `.icu` hem `.news` denemesi yanıt vermedi. Mevcut `div.movie` ve `get-source` yaklaşımının güncel siteye uyduğu doğrulanmadı. |
| DiziMom | HTTP 200 | Kodun ilk katalog yolu `/tum-bolumler/` HTTP 404 verdi. Eski WordPress seçicileri ve Nuvio `?s=` araması ayrıca incelenmeli. |
| FullHDFilmizlesene | HTTP 403 | Arama ve gömülü oynatıcılar henüz doğrulanmadı. |
| FilmMakinesi | HTTP 200 | Kodun ilk katalog yolu `/filmler/` HTTP 404 verdi. Katalog, arama ve oynatıcı uyarlaması gerekli. |
| WebteIzle | HTTP 403 | CloudStream'deki AJAX akışı Nuvio'ya uygulanmamış. |
| SelcukFlix | HTTP 200 | Kodun ilk katalog yolu `/diziler` HTTP 404 verdi. Güncel rota/veri modeli gerekli. |
| SetFilmIzle | HTTP 200 | İlk aksiyon kataloğu 200 döndü fakat `div.items article` seçicisi 0 sonuç buldu. Katalog ve oynatıcı uyarlaması gerekli. |

Ham sonuçlar: [ana sayfalar](site-health-2026-11-25.json), [katalog yolları ve seçiciler](catalog-check-2026-11-25.json). Bunlar anlık gözlemlerdir; adres listesi bu sonuçlara dayanarak değiştirilmedi.

## Düzeltilenler

- CloudStream'de `DomainResolver.failover()` hiçbir yerden çağrılmıyordu. Ortak `getDoc` / `getText` akışına hata sonrası domain geçişi bağlandı; sağlayıcıların doğrudan HTML GET çağrıları bu akışa taşındı. JSON ve POST isteklerinin tamamı bu kapsamda değildir.
- Geçici domain tercihlerine 30 dakika ömür verildi; paylaşılan harita eşzamanlı erişime uygun hale getirildi. Kotlin tarafında iptal sinyalleri yutulmuyor. 404 yanıtı domain arızası kabul edilmiyor.
- Python, Kotlin ve Nuvio doğrulamasında Cloudflare bekleme sayfalarının sağlıklı alternatif domain sayılması engellendi. Bu bir Cloudflare çözme yöntemi değildir.
- Nuvio aramada ilk bağlantıyı rastgele seçmek yerine TMDB adlarıyla eşleşen adayları kullanıyor. Bölüm bulunamazsa dizi sayfasından fragman çıkarılmıyor; 1. bölüm, 10. bölümle karıştırılmıyor. `series` türü `tv` olarak kabul ediliyor.
- Nuvio göreli iframe adreslerini bulundukları sayfaya göre çözüyor; kaçışlı JSON bağlantılarını ve imzalı sorgu parametrelerini koruyor. Kalite bilgisi olmayan bağlantılara otomatik `1080p` yazılması kaldırıldı. Siteye özel şifreli/JavaScript oynatıcılar için genel iframe taraması yeterli değildir.
- DiziYou aramasında `.list-series` yerine gerçek `#list-series` yapısı kullanıldı. Bölüm bağlantısı `.bolumust` öğesinin üstündeki `<a>` üzerinden alınıyor. Sabit eski storage domaini kaldırıldı; sunulan dil seçeneklerine göre orijinal ve `_tr` dublaj akışları üretiliyor.
- CloudStream sağlayıcı sürümleri 4'e, Nuvio sağlayıcı sürümleri 1.2.0'a yükseltildi. Üretilen dosyaların şablonla aynı kalmasını ve regresyon testlerini denetleyen CI eklendi.

## Doğrulama ve sınırlar

- 7 Python testi ve 11 Node testi geçti. Testler gerçek hataları temsil eden sahte HTTP yanıtları kullanır; canlı oynatma testleri değildir.
- Yerel Nuvio DiziYou sağlayıcısı canlı TMDB `1396`, `tv`, sezon `1`, bölüm `1` ile çalıştırıldı. TMDB → arama → dizi → bölüm zinciri HTTP 200 verdi. `10551/play.m3u8` ve `10551_tr/play.m3u8` manifestleri HTTP 200 döndürdü ve `#EXTM3U` ile başladı. Video segmentleri indirilmedi; uygulama içinde görüntü/ses/altyazı testi yapılmadı.
- Komut yolundaki `java` Java 8; `JAVA_HOME` altında ayrıca JDK 21 mevcut. Gradle veya Gradle wrapper yok; Android SDK da yapılandırılmış değil. Değişikliklerden sonra `.cs3` derlemesi veya Android uygulama testi yapılmadı. Var olan GitHub derlemesinin başarılı olması bu yeni kodu doğrulamaz.
- Değişiklikler yerel çalışma alanında hazırlandı; GitHub'a push veya yayın yapılmadı. Kalan sağlayıcılar tabloda belirtilen siteye özel çalışmalar tamamlanmadan çalışır kabul edilmemeli.

Başvuru olarak [NuvioTV çalışma motorunun kaynak kodu](https://github.com/NuvioMedia/NuvioTV/blob/dev/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt) ve [Neowatch sağlayıcı kaynakları](https://github.com/neoser1984/neowatch) incelendi. Yukarıdaki canlı sonuçlar bu repodaki kod ve bu bilgisayardan yapılan isteklerle elde edildi.
