# NuvioTR depo incelemesi ve geliştirme planı

İnceleme: 26 Kasım 2026. Karşılaştırma tabanı: bizim `180caf1` commitimiz. Bu belge hazırlık raporudur; yeni sağlayıcı eklenmedi ve üretimdeki domainler değiştirilmedi.

## Sonuç

NuvioTR sayfası merkezi bir domain takip servisi değil, depo dizini. `repos.json` içindeki bağlantıları gösteriyor; Nuvio için manifestteki `scrapers`, Cloudstream için `repo.json → pluginLists → plugins.json` kayıtlarını okuyor. Sayfada görünmek, kaynağın arama, bölüm veya video aşamasının çalıştığını kanıtlamıyor.

Dizinde 22 kayıt var: 10 Nuvio eklenti deposu, 8 Cloudstream deposu, 3 katalog, 1 koleksiyon. Bu incelemede 22 girişin manifest/koleksiyon dosyası ve Cloudstream listeleri indirilebildi. Sayılar benzersiz site sayısı değildir: farklı depolarda aynı siteler, toplayıcılar, canlı yayınlar ve film oynatıcıları tekrar ediyor. Canlı oynatma testi bu araştırmanın kapsamında yapılmadı; önceki box sonuçları `DURUM.md` içinde.

Bizim güçlü tarafımız zaten mevcut: ortak `domains.json`, dört saatte bir kontrol, iki uygulamada çalışma anında adres okuma ve alternatif deneme. En büyük eksikler daha fazla site eklemekten önce **domain kimliği, hata sınıflandırması, çalışan son adresin korunması ve uçtan uca oynatma doğrulaması**.

## Depolarda ne var?

Aşağıdaki sayılar indirme anındaki manifest/listelerde bulunan kayıtlardır; kullanılabilirlik puanı değildir. Katalog ve koleksiyonları sağlayıcı sayısına katmadım.

| Depo / manifest | Tür | Bildirilen sağlayıcı kaydı |
|---|---|---:|
| [izlelan Katalog](https://katalog.izlelan.com/manifest.json) | Nuvio | — |
| [izlemeli.com Katalog](https://izlemeli.github.io/nuvio/tr/manifest.json) | Nuvio | — |
| [jasoelys Katalog](https://katalog.jasoelys.workers.dev/manifest.json) | Nuvio | — |
| [izlemeli.com Koleksiyon](https://raw.githubusercontent.com/izlemeli/nuvio/refs/heads/main/collections.json) | Nuvio | — |
| [Manitux Nuvio Eklentileri](https://raw.githubusercontent.com/manitux-app/nuvio-test-plugins/refs/heads/main/manifest.json) | Nuvio - NuvioTR | 8 |
| [izlelan Eklentileri](https://izlelan.com/manifest.json) | Nuvio | 40 |
| [patr0n Eklentileri](https://raw.githubusercontent.com/patr0nq/nuvioaddons/refs/heads/main/manifest.json) | Nuvio | 11 |
| [Wiojelt WioSinema Eklentileri](https://raw.githubusercontent.com/Wiojelt/Nuvio-Addons/main/manifest.json) | Nuvio | 11 |
| [Wiojelt WioCinema Eklentileri](https://raw.githubusercontent.com/Wiojelt/Nuvio-Addons/main/wiocinema/manifest.json) | Nuvio | 10 |
| [Dexter.pw](https://raw.githubusercontent.com/justemir84/nuvio-dexter-plugin/main/manifest.json) | Nuvio | 30 |
| [Jasoelys](https://nuvio.jasoelys.workers.dev/manifest.json) | Nuvio | 9 |
| [burhanb3](https://burhanb3.github.io/nuvio-tr-hd/manifest.json) | Nuvio | 7 |
| [CVNFlix](https://raw.githubusercontent.com/onrcvndev/nuvio-plugin-tr/main/manifest.json) | Nuvio | 24 |
| [TVMatik](https://raw.githubusercontent.com/tvmatik/nuviotr-live/refs/heads/main/manifest.json) | NuvioTR | 2 |
| [Manitux Cloudstream Eklentileri](https://raw.githubusercontent.com/manitux-app/cs-plugins/refs/heads/main/repo.json) | Cloudstream | 9 |
| [Wiojelt WioSinema Cloudstream Eklentileri](https://raw.githubusercontent.com/Wiojelt/WioSinema/builds/repo.json) | Cloudstream | 43 |
| [Wiojelt Turksinema Cloudstream Eklentileri](https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json) | Cloudstream | 122 |
| [Wiojelt Wiokids Cloudstream Eklentileri](https://raw.githubusercontent.com/Wiojelt/WioKids/main/repo.json) | Cloudstream | 11 |
| [Wiojelt WioAnime Cloudstream Eklentileri](https://raw.githubusercontent.com/Wiojelt/WioAnime/builds/repo.json) | Cloudstream | 10 |
| [MRC Cloudstream Eklentileri](https://raw.githubusercontent.com/lepotane/MRC-builds/builds/repo.json) | Cloudstream | 73 |
| [NeO Cloudstream Eklentileri](https://raw.githubusercontent.com/neoser1984/neowatch/main/repo.json) | Cloudstream | 37 |
| [BigBullApp Cloudstream Eklentileri](https://raw.githubusercontent.com/F0Rextasy/bigbullapp-cs/master/repo.json) | Cloudstream | 134 |

Envanterin sağlayıcı adları, sürümleri, manifest adresleri, kontrol zamanları ve incelenen kaynak commitleri: [JSON kaydı](nuviotr-inventory-2026-11-26.json).

## Domain takibini nasıl yapıyorlar?

### NeoWatch: yönlendirme, kimlik kontrolü, sürüm artışı ve PR

İncelenen commit: `13b96229ed698251827568ff914ba3b6830e16c9`.

`KONTROL.py`, Kotlin dosyalarındaki `mainUrl` değerlerini buluyor ve HTTP yönlendirmelerini izliyor. Aday adresi kabul etmeden önce HTTP 200, en az 2000 karakter gövde, park sayfası işaretleri, sorgu/fragment, yol değişikliği ve eski/yeni hostname marka benzerliğini kontrol ediyor. RecTV için ayrı Firebase Remote Config sorgusu var. Kabul edilen değişiklik Kotlin koduna yazılıyor, modül sürümü artırılıyor. Workflow her 9 saatte bir (`11 */9 * * *`) çalışıyor ve `domain-degisikligi` dalından PR açıyor. **Kodda PR oluşturulması var; otomatik birleştirildiği sonucu çıkarılamaz.**

Bizim alacağımız fikir: yönlendirmeyi marka/yol/içerik kimliğiyle doğrulamak, şüpheli değişiklikleri incelemeye ayırmak. Sadece HTML boyutu veya marka sözcüğü, aynı sitenin doğru parser ile çalıştığını yine de garanti etmez.

Kaynaklar: [kontrol kodu](https://github.com/neoser1984/neowatch/blob/13b96229ed698251827568ff914ba3b6830e16c9/KONTROL.py), [workflow](https://github.com/neoser1984/neowatch/blob/13b96229ed698251827568ff914ba3b6830e16c9/.github/workflows/Kontrol.yml).

### Manitux: merkezi domain dosyası; yayın ile çalışma bağımlılığı farklı olabilir

`cs-plugins` ve `nuvio-test-plugins` depolarında 16 anahtarlı `domains.json` var. Ancak incelediğim Nuvio DiziBox sağlayıcısındaki `DOMAINS` adresi **`manitux-app/nuvio-plugins`** deposunu gösteriyor. Test deposundaki listeyi okumak, cihazın kullandığı adresi bulmaya yetmiyor. Örneğin inceleme anında test listesindeki Dizilla `dizilla.la`, çalışma bağımlılığındaki `dizilla.now`; DiziPal sırasıyla `2147` ve `2140`. Bu sayılar hangisinin canlı olduğunu kanıtlamaz.

JS dosyaları karartılmış; DiziBox örneğinde `getStreams`, `getCatalog`, `search`, `comments` dışa aktarımları görülebiliyor. İki listede fark bulunması merkezi dosyanın kendiliğinden güncel tutulduğunu göstermiyor. İncelenen ana dallarda domain güncelleyen workflow görünmedi; özel veya harici otomasyon bulunup bulunmadığı bilinmiyor. CS paketlerinin domain okuma davranışı bu araştırmada tersine mühendislikle doğrulanmadı.

Kaynaklar: [test listesi](https://github.com/manitux-app/nuvio-test-plugins/blob/efbc8301711f170d1d23bf834a4431f3f5d129db/domains.json), [DiziBox kodu](https://github.com/manitux-app/nuvio-test-plugins/blob/efbc8301711f170d1d23bf834a4431f3f5d129db/providers/dizibox.js), [çalışma anında başvurulan liste](https://raw.githubusercontent.com/manitux-app/nuvio-plugins/refs/heads/main/domains.json).

### Patron: ayrı veri deposu ve yerel yedek

DiziPal örneği `patr0nq/veriler/main/siteurl.txt` okuyor, sonucu 5 dakika bellekte tutuyor; okunamazsa kod içindeki adrese dönüyor. DiziYou da aynı veri kaynağına başvuruyor. İnceleme anında dosyada birden fazla DiziPal anahtarı var. Ortak metin dosyası adres dağıtımı sağlıyor; dosyayı kimin/hangi sağlık kontrolünün güncellediği, incelediğimiz sağlayıcı koduyla ispatlanamaz.

Bizim alacağımız fikir: domain verisini koddan ayırmak zaten mevcut; buna kaynak adresi, doğrulama zamanı ve kalıcı son başarılı adres eklemek gerekiyor. Başkasının metin dosyasını doğrulamadan üretim listemiz yapmamalıyız.

Kaynaklar: [DiziPal kodu](https://github.com/patr0nq/nuvioaddons/blob/b44eadd4c6c6c0fe6fb2d49c4c1bec6dfa9f8bbd/providers/patronDizipal.js), [veri dosyası](https://raw.githubusercontent.com/patr0nq/veriler/refs/heads/main/siteurl.txt).

### Wiojelt Nuvio: yalnız domain değil, upstream davranış sözleşmesi takibi

`Nuvio-Addons` workflow'u 6 saatte bir (`17 */6 * * *`) kaynak commitlerini kontrol ediyor. `config/upstreams.json` dosya eşlemelerini, `generated/upstream-state.json` son SHA durumunu tutuyor. Üreticiler bazı API adreslerini kaynak kodundan alıyor, beklenen işaretler kaybolursa eski varsayımla sessizce üretmek yerine hata veriyor. Workflow'da eşzamanlı çalışmayı sınırlayan `concurrency` var.

Belge açıkça bazı kaynakların özel olduğunu söylüyor: gerekli token yoksa özel depolar atlanıyor, mevcut üretilmiş dosyalar korunuyor. Bu nedenle listelenen bütün adaptörleri herkese açık Kotlin kaynaklardan yeniden üretebileceğimiz söylenemez. WioSpor domain adayları için ayrı public runtime manifest bağımlılığı da belgelenmiş.

Bizim alacağımız fikir: upstream SHA ve extractor sözleşmesi takibi. Site domaini değişmeden player şifrelemesi veya API değişebilir; yalnız adres taraması bu sorunu çözmez.

Kaynaklar: [otomasyon tasarımı](https://github.com/Wiojelt/Nuvio-Addons/blob/8fb8a12137590970b9c4e4f7db3a1c73de2b44ce/docs/AUTOMATION.md), [workflow](https://github.com/Wiojelt/Nuvio-Addons/blob/8fb8a12137590970b9c4e4f7db3a1c73de2b44ce/.github/workflows/sync.yml), [üretici doğrulamaları](https://github.com/Wiojelt/Nuvio-Addons/blob/8fb8a12137590970b9c4e4f7db3a1c73de2b44ce/scripts/generate-wiosinema.mjs).

### Diğerlerinin görünürlük sınırı

CVNFlix'in yayımlanmış JS paketleri görülebiliyor; DiziPal ve DiziBox örnekleri paketlenmiş çok sayıda extractor/metadata yardımcısı içeriyor. Buradan tüm sağlayıcılar için ortak bir otomatik domain botu olduğu sonucu çıkmıyor. MRC-builds ve bigbullapp-cs'nin incelenen dallarında ağırlık derlenmiş CS paketleri ve manifestlerde; domain güncelleme algoritması görünür kaynaklardan doğrulanamadı. Wio Cloudstream paketleri için Nuvio deposundaki otomasyonu doğrudan varsaymadım.

İzlelan bağlantısı GitHub profilini, Jasoelys kaydı ise bir worker manifestini gösteriyor; dizinde denetlenebilir kaynak deposu bağlantısı yok. Manifest erişimi, backend domain takibinin nasıl yapıldığını açıklamıyor. Bunları “kodu yok” yerine **bu kayıt üzerinden yöntem doğrulanamadı** diye sınıflandırıyoruz.

## Bizim repoda somut eksikler

| Öncelik | Bulgu ve etkisi | Değişecek yer / kabul ölçütü |
|---|---|---|
| P0 | Domain sağlık eşiği Python'da 500, Kotlin/JS'de 1500 karakter. Aynı adres bir katmanda kabul, diğerinde ret alabilir. | Ortak sağlık sözleşmesi; aynı park, challenge ve gerçek site örnekleri üç katmanda aynı sınıflandırılmalı. |
| P0 | `healthCheckText` ve uzunluk doğru site/parser kimliğini kanıtlamıyor. DiziPal yönlendirmesi aynı marka altında farklı şablona gidebilir. | `templateId`, gerekli selector/API işaretleri, izinli yönlendirme politikası. Uyumsuz siteye otomatik geçilmemeli. |
| P0 | Runtime Kotlin `take(4)`, JS `slice(0,4)` kullanıyor. Çok sayıda mirror varsa sonraki sıralı adaylar hiç denenmeyebilir. | Aday türleri arasında dengeli seçim + toplam süre bütçesi; sıralı adayın gerçekten denendiği hata senaryosu. |
| P0 | Updater tüm sitelere erişemese de eski adresleri koruyup başarılı çıkabiliyor. Yeşil Actions sonucu site sağlığı değil. | Her siteye `dns_error/tls_error/timeout/challenge/parked/parser_mismatch/healthy` sonucu; job özeti ve eşik aşılırsa uyarı/başarısız kontrol. |
| P0 | `updatedAt` değişiklik zamanı; son kontrol veya son oynatma zamanı değil. Kaynağın nereden geldiği kayıtlı değil. | Ayrı `checkedAt`, `verifiedAt`, `lastPlaybackAt`, `sourceUrl`, `sourceType`, önceki adres ve değişiklik gerekçesi. |
| P0 | Runtime son iyi adres yalnız bellekte. Soğuk başlangıçta merkezi ham GitHub erişimi kesilirse gömülü adres eskimiş olabilir. | Uygulamanın izin verdiği kalıcı depoda doğrulanmış son adres; süresi ve şeması doğrulanan ikincil kayıt. Nuvio host depolama desteği önce kontrol edilmeli. |
| P0 | Kaynak anasayfasının açılması video başarısı olarak ölçülmüyor; mevcut yerel testler gerçek yayın garantisi değil. | Film ve diziyi farklı doğru TMDB ID'leriyle test et; arama → doğru içerik → sezon/bölüm → stream → HLS manifest/segment → box'ta ilerleyen süre raporu. |
| P1 | Telegram/redirect stratejileri updater'da destekleniyor ama `domains.json` bunlara kanal/redirect kaynağı atamıyor. Runtime bu stratejileri yürütmüyor. | Sadece doğrulanmış resmi duyuru kaynağı atansın; olmayan entegrasyon varmış gibi belgelenmesin. |
| P1 | İçe alınan sağlayıcılarda doğrudan `app.get/app.post` çağrıları ortak `getDoc/getText` davranışını her yerde kullanmıyor. | Çerez, referer, POST ve hata politikasını bozmadan her ağ giriş noktasını denetle; ortak transport'a uygun olanları taşı. |
| P1 | Domain değişiminden bağımsız extractor/API değişiklikleri otomatik izlenmiyor. | `UPSTREAM.md` yanında makine okunur repo/SHA/dosya eşlemesi; SHA farkını raporla, davranış fixture'ları geçmeden yayınlama. |
| P1 | Domain job'unda concurrency ve değişiklik öncesi doğrulama adımı yok. | Tek updater grubu, güncel dala kontrollü yazma, şema/test/doğrulama, sağlık artifact'i ve geri dönüş kaydı. Şüpheli adaylar PR'a ayrılsın. |
| P1 | Nuvio paketimiz ağırlıkla stream odaklı; yerel katalog/arama/yorum arayüzleri yok. | Hedef Nuvio/NuvioTR sürümünün sözleşmesi doğrulansın; katalog ve arama ilk, yorum isteğe bağlı. Normal Nuvio ile fork davranışı ayrı test edilmeli. |
| P2 | Nuvio eşleştirme kalitesi, yıl/tür/alternatif ad ve altyazı/kalite tercihleri için daha görünür kontroller gerekli. | Aynı adlı farklı yıl yapımları, Türkçe karakterler ve dub/sub tercihleri için kabul örnekleri; boş sonuç gerekçesi kullanıcıya anlaşılır sunulsun. |

`normalize_origin` ayrıca yönlendirmenin yolunu atıyor; yol altında çalışan gelecekteki kaynaklar için `baseUrl` ile `origin` ayrımı gerekli. Kaynaklardan gelen URL'ler scheme/host/userinfo açısından doğrulanmalı; marka eşleşmesi tek güven ölçütü yapılmamalı.

## Hangi kaynakları eklemeye değer?

Bunlar **aday**, bu araştırmada çalışıyor diye onaylanmış kaynaklar değil. Önce kaynak lisansı, hedefte erişim, doğru içerik eşleşmesi ve box oynatma testi gerekiyor. Aynı sitenin DiziPal/DiziPalHub/Original varyantlarını ayrı yeni site gibi saymamalıyız.

| Sıra | Aday grup | Neden / envanterdeki örnek |
|---|---|---|
| 1 | FilmEkseni, JetFilmizle, KultFilmler, RareFilmm | Mevcut film kapsamını genişletir; NeoWatch/MRC/Manitux/Patron listelerinde örnekleri var. Açık ve lisansı uygun kaynak bulunan adaptörlerden başlanmalı. |
| 2 | DiziBal, DDizi, DiziGom, YabanciDizi | Mevcut 13 siteye alternatif dizi araması sağlar. Kopya domain değil içerik kapsamı ve gerçek başarı oranına göre en iyi 1–2 seçilmeli. |
| 3 | WebDramaTurkey, AsyaWatch, DiziKorea | Asya dizisi açığını kapatır. CVNFlix/Wio/MRC/NeoWatch listelerinde görülüyor; Türkçe altyazı ve doğru sezon eşleştirmesi şart. |
| 4 | AnimeciX, Anizm, Anizium, TürkAnime | Anime ayrı içerik türü/numaralandırma gerektirir. WioAnime listesi karşılaştırma için yararlı; mevcut dizi parser'ına toplu eklenmemeli. |
| 5 | CizgiMax ve BelgeselX | Çocuk/belgesel kapsamı için ayrı, küçük paketler düşünülebilir. Yaş/kategori ve dil metadatası doğrulanmalı. |
| Ayrı kapsam | TVMatik, WioSpor, Minika ve diğer canlı TV kaynakları | Canlı yayın akışı, EPG ve zaman aşımı farklı; kullanıcının dizi hedefinin yanına ayrı modül olmalı. |
| İsteğe bağlı | İzlemeli/İzlelan/Jasoelys katalogları ve İzlemeli koleksiyonu | Keşif arayüzünü zenginleştirir; stream/domain onarımı değildir. Harici backend bağımlılığı ve uygulama uyumluluğu ayrıca değerlendirilmeli. |

Dexter manifestinde uluslararası oynatıcılar ve Türkçe kaynaklar birlikte görünüyor; dizindeki dil/dublaj açıklaması tüm alt sağlayıcıların gerçek ses/altyazı sonucu yerine kullanılamaz. İzlelan'ın takma adlı 40 kaydını 40 doğrulanmış Türkçe site olarak adlandıramayız. Uluslararası embed toplayıcılarını eklemek, Türkçe dublaj şartını kendiliğinden karşılamaz.

## Uygulama sırası ve teslim ölçütleri

1. **Mevcut 13 kaynağın durumunu ölç:** önceki box testlerini güncel tarihli film+dizi örnekleriyle yenile. Cloudflare engeli, parser bozulması, yanlış eşleşme ve bozuk medya ayrı raporlansın. Yedi doğrulanmamış/sorunlu kaynağı yeni site sayısıyla örtmeyelim.
2. **Domain sözleşmesini birleştir:** `domains.json`, `scripts/update_domains.py`, Kotlin resolver ve JS şablonuna provenance, site kimliği ve hata sınıfları ekle. Dört aday sınırının aç bırakma durumunu ve kalıcı son iyi adresi çöz. Üretim adresini yalnız şema/kimlik kontrollerini geçen aday değiştirsin.
3. **Otomasyonu gözlenebilir yap:** domain job'una concurrency, sağlık özeti/artifact ve değişiklik doğrulaması ekle. Beklenen hiçbir site doğrulanamazsa yeşil “her şey çalışıyor” izlenimi vermesin. Önceki registry sürümüne dönüş yolu belgeli olsun.
4. **Extractor/upstream takibini kur:** NeoWatch ve uygun diğer açık kaynaklar için SHA/dosya eşlemesi tut; player örnekleri bozulduğunda kontrol hata versin. Lisans/atıf ve kaynak erişimi doğrulanmadan karartılmış/özel kod taşınmasın.
5. **Küçük genişletme yap:** önce FilmEkseni + bir dizi alternatifi için pilot; sonra sonuçlara göre Asya/anime. Bir sağlayıcı ancak arama, doğru detay, bölüm ve gerçek oynatma geçince destek listesine alınsın.
6. **Nuvio keşif özelliklerini değerlendir:** çalışan kaynak tabanından sonra hedef uygulamada katalog/arama; koleksiyon ve yorum daha sonra. Katalog eklemek “video sorunu çözüldü” olarak raporlanmasın.

### Önerilen domain kaydı alanları

Mevcut `currentUrl`, `mirrors`, `strategy`, sıralı aday alanları korunabilir. Yanına `templateId`, `allowedRedirectHosts`, `requiredMarkers`, `sourceType`, `sourceUrl`, `checkedAt`, `verifiedAt`, `lastPlaybackAt` eklenmeli. Sık değişen hata/sağlık geçmişi ayrı `health-report.json` artifact'inde tutulmalı; her başarısız kontrolde dağıtım registry'sine gereksiz commit atılmamalı. Zamanların anlamı ve şema sürümü açık olmalı. Bu öneri şemadır, bu araştırmada uygulanmadı.

## Kapsam ve kanıt sınırı

Dizin ve 22 bağlı manifest/koleksiyon okundu; yukarıda adı geçen sekiz deponun ağaçları ve seçilmiş domain/otomasyon/sağlayıcı dosyaları incelendi. Bütün sağlayıcıların kodu veya bütün depoların geçmişi incelenmedi. Manifest erişimi sırasında üçüncü taraf JS çalıştırılmadı; box'a bağlanılmadı ve gerçek medya oynatımı yapılmadı. Açık kaynakta görünmeyen otomasyonun yok olduğu iddia edilmiyor. Ham manifestlerden gelen adlar ve destek beyanları doğrulanmış uygulama yeteneği olarak sunulmuyor.

Birincil dizin kaynakları: [NuvioTR sayfası](https://nuviotr.github.io/), [depo kayıtları](https://github.com/NuvioTR/nuvio-tr-repos/blob/main/repos.json). Bu rapordaki anlık sayılar için yanındaki tarihli JSON esas alınmalı; canlı bağlantılar sonradan değişebilir.
