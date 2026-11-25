# Test ve düzeltme durumu — 26 Kasım 2026

13 sağlayıcının katalog, arama, bölüm ve oynatıcı kodları gözden geçirildi; eski genel şablonların yerine güncel site akışları eklendi. Kaynakların tamamının her ağda ve her içerikte oynadığı iddia edilmiyor.

## Tekrarlanabilir kontroller

- 8 Python domain testi: geçti.
- 17 Node/Nuvio testi: geçti. Başlık eşleşmesi, tam bölüm seçimi, alternatif domain, RSC içindeki JSON, SetPlay köprüsü, bağımsız Node kripto uygulamasıyla AES/PBKDF2 karşılaştırması ve oyuncu nonce doğrulaması kapsanır.
- 2 Kotlin JVM testi: geçti. İç içe ve kaçışlı JSON/RSC çözümü denetlendi.
- Gradle: 13 `.cs3` paketi ve `plugins.json` üretildi. Arşivlerde DEX ve eklenti manifesti bulunduğu doğrulandı. Gradle wrapper eklendi.
- NuvioTV / MiTV / Android 11: 13 geliştirme paketi indirildi; sağlayıcı yükleme ve uygulamanın yerleşik test akışı tüm paketlerde denendi. Sonuçlar [cihaz raporunda](nuvio-device-2026-11-25.json).

NuvioTV'nin yerleşik testi film kaynaklarında TMDB 603 / Matrix, dizi kaynaklarında ise **TMDB 603 / Veronica's Closet** kullanıyor. Dizi testindeki sıfır sonuç, popüler bir dizinin de bulunamayacağını kanıtlamaz.

Bu nedenle ayrıca gerçek **Breaking Bad, 1. sezon 1. bölüm** akışı açıldı: DiziYou (2), Dizilla (1), SetFilmIzle (1), SelcukFlix (1), FilmModu (1), DiziMom (2) bağlantı döndürdü. DiziYou'nun yeni yerel sağlayıcı bağlantısı oynatıldı; ExoPlayer `playbackState=3`, `isPlaying=true` bildirdi ve oynatma konumu ilerledi. Deneme ardından durduruldu. [Oynatma kaydı](box-playback-2026-11-26.json).

Bilgisayardan yapılan Nuvio HTTP testi ayrıca DiziYou, DiziMom ve SetFilm için geçerli manifest döndürdü. Diğerlerinde ağ/Cloudflare/oynatıcı erişimi sınırlamaları görüldü; bilgisayarın sonucu cihazın sonucuyla aynı değil. [HTTP test raporu](nuvio-live-2026-11-25.json).

## Kaynak bazında değişiklikler ve sınırlar

| Kaynak | Düzeltme / gözlem |
| --- | --- |
| DiziPal | Domain kaydı `.rest` adresine yönlenmişti; bu sitenin gerçek XHR `/search` yanıtı, kartları ve `/1-sezon/1-bolum` bağlantıları için sağlayıcı yenilendi. Sıralı `.com` takibinin numarasını yanlışlıkla 1'e düşüren bot hatası düzeltildi. Katalog/arama/bölüm yapısı canlı doğrulandı; örnek harici Montana oynatıcısı bilgisayarda 503 verdi. |
| HDFilmCehennemi | JSON araması ortak hata/Cloudflare akışına taşındı; mevcut alternatif oyuncu ve gizli JS akışları eklendi. Cihaz testinde zaman aşımı, tarayıcı API isteğinde Cloudflare 403 görüldü. Tam oynatma doğrulanmadı. |
| DiziBox | Güncel WordPress detay/katalog ve özel oyuncu akışları eklendi; arama sorgusu kodlaması ve Referer düzeltildi. Tarayıcıda Breaking Bad arama kartı doğrulandı. Yerleşik cihaz testinin nadir dizi örneği sonuç vermedi; tam oynatma doğrulanmadı. |
| Dizilla | Eski şifreli API kaldırılmıştı. Yeni `getSearchSuggest`, `getBrowseTitles`, RSC `episodes` / `sources` biçimleri uygulandı. Cihazda gerçek bölüm için bağlantı geldi. |
| DiziYou | Arama ID seçicisi, bölüm bağlantısının üst `<a>` ilişkisi, değişken storage adresi ve dublaj seçenekleri düzeltildi. Gerçek bölümün manifestleri ve cihaz oynatması doğrulandı. |
| SezonlukDizi | `#dilsec` bölüm kimliği, JavaScript'ten okunan ASP endpoint sürümleri, dil alternatifleri ve Byse çözücüsü eklendi. Yerleşik nadir dizi testi sonuç vermedi; bilgisayarda arama isteği 403 verdi. Tam oynatma doğrulanmadı. |
| FilmModu | Yeni `/ara`, film/dizi kartları, sezonlar ve Pilavyer akışı uygulandı. Cihazda film ve gerçek bölüm için bağlantı geldi. Bilgisayarın aynı domaine erişimi zaman aşımına uğrayabiliyor. |
| DiziMom | Yeni `/diziler`, `/filmler`, `/ara`, sezon sayfaları, Pilavyer/FirePlayer akışları eklendi. Cihazda film ve gerçek bölüm için bağlantı geldi. |
| FullHDFilmizlesene | Güncel film ve oyuncu biçimleri, RapidVid/TurboImgz yardımcıları eklendi. Boşluk içeren arama yolunun ortak URI katmanında bozulması düzeltildi. Arama sayfasında Cloudflare kısıtı sürüyor; tam oynatma doğrulanmadı. |
| FilmMakinesi | Güncel kaynak akışlarına ek olarak erişilebilir `.co` şablonunun posterleri ve WordPress arama yedeği desteklendi. Denenen içeriklerde bağlantı doğrulanmadı. |
| WebteIzle | Gerçek `/filtre?a=...` araması, dil/ASP alternatifleri ve VidMoly akışı eklendi. Cihazdaki örnek filmde oynatılabilir bağlantı çıkmadı. |
| SelcukFlix | Yeni `/api/mj`, RSC sezonları ve şifreli oyuncu adresi, ContentX ailesi desteği eklendi. Cihazda film ve gerçek bölüm için bağlantı geldi. |
| SetFilmIzle | Yeni `a.card-link` kartları, AJAX `get_video_url`, SetPlay köprüsü ve FastPlay `.txt` HLS manifesti çözüldü. Bilgisayarda manifest, cihazda film ve gerçek bölüm bağlantıları doğrulandı. |

## Ortak iyileştirmeler

Domain yedeği ortak HTML isteklerine bağlandı; 404 adres değişikliği sayılmıyor. Geçici domain tercihlerinin ömrü sınırlandı. Cloudflare/park sayfaları sağlıklı domain kabul edilmiyor. Nuvio yanlış ilk arama sonucunu veya bölüm yerine fragmanı seçmiyor; imzalı sorguları ve oyuncu Referer/Origin başlıklarını koruyor. Doğrudan medya yanıtları HTML gibi okunmuyor.

NuvioTV için ana `repo.json` adresindeki yerel `.cs3` paketleri tercih edilmelidir. JS çalıştırıcıları, gerçek WebView isteyen tüm oyuncuları çözemez. Paket sürümü 5, Nuvio JS sürümü 1.3.0'dır. Site erişim engelleri, içerik eksikliği veya kapanan oyuncular kod güncellemesiyle her zaman giderilemez.

Uyarlanan kaynakların GPL lisansı ve katkı bilgileri [UPSTREAM.md](../UPSTREAM.md) içinde; dağıtım lisansları kaynak ve builds dallarında bulunur.
