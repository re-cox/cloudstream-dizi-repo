# re-cox Canlı TV

Nuvio/NuvioTV manifesti:

```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/live/manifest.json
```

Sürüm 1.1.0: TRT 1, TV8, Kanal D, ATV, NOW / FOX, Star TV, Show TV, Kanal 7,
Beyaz TV, tv2, TV8,5, TV8 Int, A2 TV ve mevcut NTV. FOX, NOW'un eski adı olduğu
 için ikinci bir kopya oluşturulmadı. Kullanıcı isteğiyle Şaban TV eklenmedi.

Yayıncı/CDN adresleri, NuvioTR'deki BigBull ve TVMatik kaynakları incelenerek
hazırlandı. ATV, NOW ve TV8 Int için TVVoo'nun herkese açık, kanal kimliğini
çözen HLS uç noktası kullanılır; geçici imzalı segment URL'leri depoya kaydedilmez.
Kaynak referansları channels.json içinde. Kanal logoları PNG olarak bu depoda tutulur.

26 Kasım 2026 kontrolünde 14 kanalın HLS listesi ve ilk MPEG-TS medya verisi
alındı. Bu kontrol TV'deki görüntü/ses veya uzun süreli oynatma doğrulaması değildir.
EPG/program rehberi yoktur. Uydu alıcısının HDMI 2 girişini değiştirmez.

NuvioTV uyumluluğu için `channel` türü ve
`behaviorHints.proxyHeaders.request` başlıkları kullanılır. Diğer katalog veya
film/dizi sağlayıcılarından bağımsız kurulur.

```text
python scripts/generate_live_tv.py
python -m unittest discover -s scripts -v
node scripts/check_live_tv.cjs --output docs/live-tv-expanded-http-2026-11-26.json
```

[HTTP doğrulama kaydı](../docs/live-tv-expanded-http-2026-11-26.json)
