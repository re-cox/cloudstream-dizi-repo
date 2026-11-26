# re-cox Canlı TV

Nuvio/NuvioTV için ayrı bir katalog eklentisi:

```text
https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/live/manifest.json
```

İlk doğrulama paketi TRT 1, Kanal D ve NTV içerir. Bu üç kanalın public yayın adresleri
BigBull deposundaki kanal kayıtlarından incelendi; stream dosyaları yayıncı/CDN adreslerine
doğrudan gider. Kanal bazındaki kaynak referansları `channels.json` içindedir.

26 Kasım 2026 HTTP kontrolünde HLS ve ilk MPEG-TS verisi alındı. Bu, box'ta uzun süreli
oynatma veya tüm bölgesel koşullarda erişim garantisi değildir. EPG/program rehberi henüz yoktur.
Uydu alıcısının HDMI 2 girişini değiştirmez; internet üzerinden Nuvio'nun oynatıcısında açılır.

NuvioTV `tv` türünü dizi eşanlamlısı olarak kullanabildiği için bu eklenti `channel` türü
kullanır. İstek başlıkları `behaviorHints.proxyHeaders.request` içine de yerleştirilir;
yalnız üst düzey `headers` alanına güvenilmez. Yapım ve test komutları:

```text
python scripts/generate_live_tv.py
python -m unittest discover -s scripts -v
```

HTTP kanıtı: [live-tv-http-2026-11-26.json](../docs/live-tv-http-2026-11-26.json).
Yayımlanan liste tüm üçüncü taraf depoların veya ücretli kanalların bir kopyası değildir;
doğrulanmış küçük bir başlangıç listesidir.
