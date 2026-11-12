# Siqaret Anbarı — hesablama və anbar idarəetmə proqramı

Kotlin + Jetpack Compose + Room ilə yazılmış, tam offline işləyən Android tətbiqi.

**Hazır APK:** [`apk/SiqaretAnbar-1.0.0.apk`](apk/SiqaretAnbar-1.0.0.apk) (Android 7.0+). Telefona köçürün və quraşdırın ("Naməlum mənbələr"ə icazə verin).

| Ana səhifə | Yeni məhsul (canlı hesablama) | Tarixçə | Hesabat | Qaranlıq rejim |
|---|---|---|---|---|
| ![](docs/screenshots/04_home.png) | ![](docs/screenshots/02_add_product_live_calc.png) | ![](docs/screenshots/05_history.png) | ![](docs/screenshots/06_report.png) | ![](docs/screenshots/08_home_dark.png) |

## İmkanlar

**Məhsul məlumatları:** ad, bir qutunun qiyməti, bir blokun qiyməti, blok sayı, ümumi qutu sayı, ümumi məbləğ.

**Hesablama (real vaxtda):**
- `Blok sayı × bir blokun qiyməti = ümumi məbləğ`
- `Blok sayı × 10 = ümumi qutu sayı`
- "Avtomatik qiymət" açıqdırsa: qutu qiymətini yazanda blok qiyməti (× 10) özü doldurulur və əksinə.
- Hesablamalar `BigDecimal` ilə aparılır, ona görə qəpik xətası olmur.

**Ana səhifə:** ümumi stok dəyəri, məhsul sayı, ümumi blok sayı, ümumi qutu sayı.

**Məhsul idarəetməsi:** əlavə et, redaktə et, sil (snackbar-dan "Geri qaytar" mümkündür), ada görə axtarış, ada/qiymətə/məbləğə/stoka görə sıralama. Hər kartda böyük **−/+** düymələri var, "Stok miqdarını dəyiş" pəncərəsi ilə istənilən miqdar artırılıb-azaldıla bilər. Eyni adlı məhsulun təkrar əlavə edilməsinin qarşısı alınır.

**Tarixçə:** hər əlavə, redaktə, silinmə, bərpa və stok dəyişikliyi qeyd olunur. Qeydlər günlərə görə qruplaşdırılır, hər gün üçün daxil olan və çıxan blok sayı göstərilir. Filtrlər: bu gün, 7 gün, 30 gün, hamısı.

**Hesabat və ixrac:**
- **PDF** (A4): xülasə, məhsul cədvəli və CƏMİ sətri, seçilmiş dövr üçün gündəlik tarixçə. Səhifələr avtomatik bölünür.
- **Excel (.xlsx)**: 3 vərəq (Məhsullar, Tarixçə, Gündəlik). Məbləğ və cəmlər Excel formulları ilə yazılır (`=ROUND(E5*D5,2)`, `=SUM(...)`), valyuta formatı ₼.
- Fayl istifadəçinin seçdiyi yerə saxlanılır, sonra "Paylaş" ilə WhatsApp, e-poçt və s. vasitəsilə göndərilə bilər.

**Dizayn:** Material 3, açıq, qaranlıq və sistem rejimi (seçim yadda qalır), qradiyent və parıltılı 3D ikonlar, yumru künclü kartlar, böyük düymələr, interfeys tam Azərbaycan dilindədir.

**Məlumatlar:** Room (SQLite) bazasında saxlanılır, internet tələb olunmur, tətbiq bağlananda məlumatlar itmir.

## Layihə strukturu

```
app/src/main/java/az/siqaret/anbar/
├── data/            Room: Product, HistoryEntry, DAO-lar, AppDatabase, InventoryRepository
├── util/            InventoryCalc (hesablama qaydaları), Fmt, Report, PdfExporter, XlsxExporter
├── ui/theme/        Rənglər (light/dark), tipoqrafiya, qradiyentlər
├── ui/components/   Icon3D, StatCard, GradientCard, ThemeMenuButton ...
├── ui/screens/      HomeScreen, ProductEditScreen, HistoryScreen, ReportScreen
├── InventoryViewModel.kt
└── MainActivity.kt  Naviqasiya + alt menyu
```

## Yığma (build)

Android Studio (Ladybug və ya daha yeni) ilə `SiqaretAnbar` qovluğunu açın və ya terminaldan:

```bash
cd SiqaretAnbar
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # hesablama, Excel və tam UI axını testləri
```

Release APK hazırda **debug açarı** ilə imzalanır ki, birbaşa quraşdırıla bilsin. Google Play-ə yükləmək üçün `app/build.gradle.kts` faylında öz release açarınızı (keystore) təyin edin.

## Testlər

- `CalcAndExportTest`: hesablama düsturları, ədəd daxiletməsi və formatlama, gündəlik xülasə, .xlsx faylının düzgünlüyü.
- `AppFlowTest` (Robolectric): real `MainActivity` + Room bazası işə salınır. Test iki məhsul əlavə edir, canlı hesablamanı yoxlayır, +1 blok düyməsinə basır, təkrar ad xətasını, axtarışı, tarixçəni, hesabatı və qaranlıq rejimi yoxlayır.
