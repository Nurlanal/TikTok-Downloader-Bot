# Son Tarix İzləyici — Market üçün Android tətbiqi

Marketdə satılan məhsulların son istifadə tarixlərini izləyən, tarix yaxınlaşdıqda telefona avtomatik
bildiriş göndərən və məhsulları başqa istifadəçilərə ixrac/idxal edən **native Android APK** (Kotlin + Jetpack Compose).
Bütün interfeys Azərbaycan dilindədir, internetsiz işləyir.

## APK-nı necə əldə etmək olar

1. GitHub-da **Actions → "Son Tarix İzləyici — APK"** iş axınına keçin (hər push-da avtomatik işləyir),
   və ya **Releases** bölməsindən ən son `sontarix-build-N` buraxılışını açın.
2. `SonTarixIzleyici-v1.0.0.apk` faylını telefona yükləyin və quraşdırın
   (telefonda "Naməlum mənbələrdən quraşdırma" icazəsi lazım ola bilər).

Lokal yığmaq üçün (Android Studio Ladybug+ və ya JDK 17 + Android SDK 34):

```bash
cd SonTarixIzleyici
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # unit testlər
```

> `app/release.keystore` layihə ilə birlikdə gəlir ki, bütün yığımlar eyni imza ilə yenilənə bilsin.
> Play Store-a yükləməzdən əvvəl öz açarınızı yaradıb `app/build.gradle.kts` faylındakı `signingConfigs` hissəsini dəyişin.

## Funksiyalar

| Bölmə | Nə edir |
|---|---|
| **Ana səhifə** | 5 statistik kart (ümumi, normal, yaxınlaşan, bu gün bitən, keçmiş), status diaqramı, kateqoriya statistikası, son xəbərdarlıqlar |
| **Məhsullar** | Axtarış (ad/barkod/partiya), status və kateqoriya filtrləri, 5 növ sıralama, barkodla axtarış, çoxlu seçim → silmə / ixrac, şəklə baxış |
| **Məhsul əlavə et** | Şəkil (kamera/qalereya), ad, barkod (əl ilə və ya kamera skaneri), kateqoriya, say, son tarix (təqvim və ya əl ilə), partiya nömrəsi, qeyd |
| **Partiyalar** | Eyni məhsulun fərqli tarixli partiyaları ayrıca izlənir; detal səhifəsində digər partiyalar göstərilir, "Yeni partiya" ilə məlumatlar avtomatik doldurulur |
| **Bildirişlər** | 30/15/7/3/1/0 gün əvvəl və tarix keçdikdə; günləri, saatı, səsi istifadəçi dəyişir; gündəlik ümumi hesabat; bildirişə toxunduqda məhsul açılır |
| **İxrac** | Bütün / seçilmiş / kateqoriya / tarix aralığı; ZIP (şəkil + JSON), JSON, Excel (.xlsx), PDF hesabatı; WhatsApp, Telegram, Bluetooth, Quick Share və s. ilə paylaşma |
| **İdxal** | ZIP/JSON seçimi (və ya WhatsApp/Telegram-dan birbaşa "Aç"), təhlükəsizlik yoxlaması, şəkilli önizləmə, məhsul seçimi, təkrar nəzarəti, təsdiq |
| **Təkrar nəzarəti** | Məhsul ID-si, barkod, partiya və son tarix müqayisəsi; seçim: saxla / yenilə / ayrıca əlavə et / idxal etmə |
| **Tarixçə** | Son ixrac/idxal tarixi və sayı, hər əməliyyatın statusu |
| **Ehtiyat nüsxə** | Bütün məlumatların (şəkillər daxil) ZIP nüsxəsi və atomik bərpa |
| **Parametrlər** | Dark/Light/Sistem rejimi, bildiriş parametrləri, test bildirişi, kateqoriyaların idarəsi, batareya optimallaşdırması |

## Texniki quruluş

```
app/src/main/java/az/sontarix/market/
├── App.kt, MainActivity.kt           # tətbiq, DI konteyneri, deep-link (bildiriş/fayl)
├── data/                             # Room: Product, Category, NotificationLog, HistoryEntry + DAO-lar
├── notify/                           # NotificationHelper, ExpiryChecker, WorkManager + AlarmManager, BootReceiver
├── transfer/                         # TransferManager (ZIP/JSON/Excel/PDF ixrac, təhlükəsiz idxal), XlsxWriter, PdfReportWriter
├── util/                             # DateUtils, ExpiryStatus, ImageStorage
└── ui/                               # Compose ekranları, tema, 3D ikonlar
```

* **Bildirişlərin etibarlılığı:** seçilmiş saatda `AlarmManager.setAndAllowWhileIdle` + ehtiyat olaraq 24 saatlıq `WorkManager`
  periodik işi. Telefon yenidən başladıqda, tətbiq yeniləndikdə və ya saat qurşağı dəyişdikdə `BootReceiver` hər şeyi bərpa edir.
* **Təkrar bildiriş yoxdur:** `notification_log` cədvəlində `(məhsul, son tarix, hədd)` unikaldır. Tarix dəyişdikdə köhnə qeydlər
  silinir və yeni tarix üçün bildirişlər yenidən planlaşdırılır; məhsul silindikdə bildirişlər kaskadla ləğv olunur.
* **İdxal təhlükəsizliyi:** fayl ölçüsü limitləri, ZIP-bomba və `../` yol keçidi qoruması, yalnız `products.json` və
  `images/*.jpg|png|webp` qəbul edilir, şəkillər imza (magic bytes) və dekodla yoxlanılır, JSON sxemi və tarixlər yoxlanılır.
* **Məlumat itkisi yoxdur:** idxal və bərpa vahid Room tranzaksiyasında icra olunur; xəta olarsa tranzaksiya geri qaytarılır,
  kopyalanmış şəkillər silinir və mövcud məlumatlar dəyişmir. İstifadəçi icazəsi olmadan mövcud məhsul yenilənmir.
* **Məxfilik:** məlumatlar tətbiqin şəxsi qovluğundadır, bulud ehtiyatı (`allowBackup`) söndürülüb, heç bir funksiya internet tələb etmir.

### İxrac faylının formatı

```
mehsullar_YYYYMMDD_HHMM.zip
├── products.json   # {"format":"sontarix-market-export","version":1,"products":[{uid,name,barcode,category,quantity,expiryDate,batchNumber,note,image}]}
└── images/<uid>.jpg
```
