# ALEN · Platform Gündemi

Nuvio/Stremio katalog eklentisi: `https://raw.githubusercontent.com/re-cox/cloudstream-dizi-repo/main/platforms/manifest.json`

Bant sırası: Netflix · Gündemdekiler, Exxen · Öne Çıkanlar, Disney+ · Türkiye’de Bugünün Top 10 Dizileri. Veriler oturum açmadan resmî Türkiye ana sayfalarından alınır; Exxen'in herkese açık web uygulama anahtarı hesap/abonelik anahtarı değildir. Öne Çıkanlar listesi keşif sayfasındaki başlığından bulunur; ana carousel ile karıştırılmaz. Sayfalar JSON veya HTML olarak ayrıştırılır; indirilen JavaScript çalıştırılmaz.

GitHub Actions altı saatte bir yeniler. Hatalı/boş yanıt önceki başarılı bandın üzerine yazılmaz. İçerikler kaynak sırasını korur. Kimlik eşleşmesi belirsiz yapımlar listeden çıkarılır ve refresh-report.json içinde raporlanır; yanlış diziye bağlanmaz. Asya dizileri kişisel tercihe göre hariç tutulur; Disney+ bandında bu nedenle 10'dan az kart bulunabilir.

Kartlar IMDb/TMDB kimliği taşır. IMDb detayları statik meta dosyalarıyla, TMDB kimlikleri Nuvio'nun yerleşik metadata desteğiyle açılır. Bölüm bilgileri her yenilemede alınır; katalog eklentisi yayın akışı sağlamaz, kurulu kaynakları devre dışı bırakmaz. Film/dizi içeriğinin bir platformda öne çıkması mevcut kaynaklarda oynatılabilirlik garantisi değildir.

Geliştirme: `pip install beautifulsoup4==4.14.2`, ardından `python scripts/update_platform_catalogs.py`. `TMDB_API_KEY` ortam değişkeni desteklenir. Scriptteki varsayılan anahtar mevcut projenin genel uygulama anahtarıdır; kullanıcı oturumu kullanılmaz. Test: `python -m unittest discover -s scripts -p test_platform_catalogs.py`.
