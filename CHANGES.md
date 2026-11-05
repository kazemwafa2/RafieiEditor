# فهرست دقیق تغییرات این بازبینی (v2.5)

مبنای کار: بازخوانی کامل ۵۲ فایل Kotlin (≈۲۱٬۰۰۰ خط) + JNI + Gradle + Workflow. هیچ APK ساخته نشد. نسخهٔ اصلی بدون تغییر نزد کاربر محفوظ است.

## ۱) امنیت (اولویت اول)
| فایل | تغییر | دلیل |
|---|---|---|
| `rafiei.jks` | **از مخزن حذف شد** | کلید امضا نباید در ZIP عمومی باشد. رمز قدیمی هم از همهٔ فایل‌ها پاک شد |
| `app/build.gradle` | امضا از `keystore.properties` یا متغیرهای محیطی خوانده می‌شود؛ بدون کلید → امضای debug | هیچ رمزی در سورس نماند |
| `.github/workflows/build.yml` | امضا با GitHub Secrets (`KEYSTORE_BASE64`، `KEYSTORE_PASSWORD`، `KEY_ALIAS`، `KEY_PASSWORD`)؛ بدون Secret → کلید آزمایشی موقت CI با هشدار صریح | build آینده بدون کلید در مخزن کار می‌کند |
| `.gitignore` | `*.jks`، `*.keystore`، `keystore.properties` | جلوگیری از commit تصادفی |
| `gradle.properties` | حذف `org.gradle.java.installations.paths` (مسیر مخصوص یک ماشین) | قابل build روی هر سیستم |
| پاک‌سازی | `.gradle/` و `local.properties` از درخت حذف شد | آثار build محلی |

## ۲) زنجیرهٔ زیرنویس (اولویت دوم — مهم‌ترین اصلاحات)
| فایل | تغییر | دلیل |
|---|---|---|
| `AutoSub.kt` → `decode16k` | نمونه‌های صوتیِ قبل از نقطهٔ برش (به‌خاطر `SEEK_TO_PREVIOUS_SYNC`) دور ریخته می‌شود؛ قطع دقیق در toMs؛ لغو واقعی در حلقهٔ دیکد؛ فیلتر میانگین‌گیر ضدتداخل؛ سقف حافظهٔ ۴۸M نمونه؛ لاگ خطا | زیرنویس تا چند ده‌سانتی‌متر (تا چند ثانیه) جلوتر از صدا می‌افتاد |
| `AutoSubVosk.kt` | (الف) ساخت زیرنویس فقط بازهٔ زمانی همان منبع را جایگزین می‌کند، نه همهٔ زیرنویس‌ها (ب) نرمال‌سازی ی/ک و حذف اعراب فقط برای فارسی (پارامتر `isFa` که بلااستفاده بود فعال شد) (ج) وردهای بی‌زمان (NaN) مدل حذف می‌شوند (د) دانلود Vosk با قابلیت ازسرگیری (Range + فایل .part) و پاک‌سازی در لغو (هـ) برچسب صادقانهٔ مدل‌ها | چند-منبعی، چندزبانه، دانلود قطع‌شده |
| `Whisper.kt` | (الف) تطبیق حجم فایل دانلودی با حجم اعلامی سرور (ب) حداقل سایز برای مدل «نصب‌شده» (۸۵٪ حجم اعلامی) (ج) بررسی cancel پیش از شروع هر chunk (د) نمایش درصد کهنهٔ اجرای قبلی حذف شد | مدل خراب دیگر «نصب‌شده» حساب نمی‌شود |
| `whisper_jni.cpp` | بررسی null برای همهٔ `GetStringUTFChars`/`NewByteArray`/`NewLongArray`/`GetFloatArrayRegion` | کرش در شرایط کم‌حافظه |
| `SubWords.kt` | کش کردن لیست ناقصِ پارس خراب حذف شد | زیرنویس ناقص نمی‌ماند |
| `PanelsCut.kt` | با روشن‌کردن دوبارهٔ حذف پس‌زمینه، تلاش مجدد واقعی (`Seg.retry`) | خطای گذرا دائمی نمی‌شود |

## ۳) پایداری و حافظه
| فایل | تغییر | دلیل |
|---|---|---|
| `Mask.kt` | **باگ ماسک معکوس اصلاح شد** (لایهٔ پوشش کامل compositing می‌شد و کل قاب رنگ می‌گرفت) + چرخش برای شکل «نوارها» | قابلیت invert عملاً خراب بود |
| `Seg.kt` | سقف کش فریم‌ها (۴ کلیپ ≈ حداکثر ~۱۰۰MB) + آزادسازی ترتیبی + `retry()` | نشت حافظهٔ صعودی تا OOM |
| `Reverse.kt` | (الف) شکست `muxer.stop()` یا خروجی صفر فریم = شکست واقعی (نه «موفقیت جعلی») (ب) سرعت کلیپ در معکوس حفظ می‌شود (مدت تایم‌لاین ثابت می‌ماند) (ج) پنجرهٔ drain انکودر ۶ث → ۲۰ث (د) **معکوس‌سازی صدا با PCM واقعی** و افزودن به‌عنوان ترک صدا | فایل خراب جای کلیپ نمی‌نشیند؛ صدا از بین نمی‌رود |
| `Store.kt` | ذخیرهٔ اتمی (حذف `dst.delete()` پیش از rename) + لاگ پیش‌نویس خراب | پنجرهٔ گم‌شدن کل پیش‌نویس در crash بسته شد |
| `MediaUtil.kt` | (الف) PNG/WebP/GIF با بایت اصلی وارد می‌شوند (شفافیت و انیمیشن حفظ، بدون افت کیفیت دوباره‌فشرده‌سازی) (ب) `saveToGallery` در خطا، ردیف `IS_PENDING` گالری را حذف می‌کند (ج) `ThumbCache` رسانهٔ غیرقابل‌دیکد را به‌خاطر می‌سپارد (توقف حلقهٔ تلاش بی‌پایان) (د) `Waveform` خروجی float-PCM و تغییر فرمت وسط جریان را می‌فهمد | دقت بصری/گالری تمیز |
| `AudioTools.kt` | ریست state فیلترهای Biquad در `onFlush` | صدای کهنه پس از seek |
| `EqAudio.kt` | سقف کش تحلیل (۱۲ آهنگ) + یک‌بار تلاش مجدد پس از خطا | نشت حافظه؛ خطای گذرا دائمی نمی‌شد |
| `Preview.kt` | (الف) جستجوی مکث با EXACT (فریم مکث = فریم درخواستی) (ب) بعد از رهاشدن یک انگشت پینچ، درگ با انگشت باقی ادامه می‌یابد (ج) دو-ضرب روی متن انتخاب‌شده، ادیتور متن را باز می‌کند (د) فیلد `compareOriginal` برای مقایسهٔ قبل/بعد | UX و دقت |
| `Panels.kt` | (الف) `stopRecordingIfActive` / `stopSfxPreview` (ب) `FilterPresets` + UI پیش‌تنظیم شخصی فیلتر | میکروفون باز نمی‌ماند؛ پیش‌تنظیم واقعی |
| `EditorActivity.kt` | فراخوانی توابع بالا در `onPause`/`onDestroy` | تضمین چرخهٔ عمر |
| `HomeActivity.kt` | فهرست/تغییرنام/حذف پیش‌نویس به رشتهٔ پس‌زمینه | ANR با پیش‌نویس‌های زیاد |
| `Backup.kt` | حذف پیام موفقیت جعلی (خروجی null → خطای واقعی) + حذف ردیف گالری در شکست + لاگ | تشخیص خطا ممکن شود |
| `Icons.kt` | آیکون «audiomusic» تعریف شد | قبلاً دایرهٔ خالی می‌افتاد |
| `Motion.kt` | شرط مردهٔ anchor در fold/unfold ساده شد | کد مرده |

## ۴) خروجی
| فایل | تغییر | دلیل |
|---|---|---|
| `Exporter.kt` | (الف) سکوتِ فاصلهٔ لِین بعد از تصمیمِ استفاده/رد آیتم ساخته می‌شود (هم‌زمانی لِین‌ها) (ب) `bitrateFor` با FPS واقعی (ج) `Result.warnings` — اگر SpeedChangeEffect/FrameDropEffect در دسترس نباشد هشدار صریح | خروجی دیگر بی‌سروصدا از سرعت/FPS نمی‌گذرد |
| `ExportActivity.kt` | (الف) Wake lock CPU در خروجی (ب) حذف فایل موقت در شکست ذخیرهٔ گالری (ج) بیت‌ریت/تخمین با FPS (د) نمایش هشدارهای سرعت/FPS در صفحهٔ پایان | خروجی نیمه‌کاره رها نمی‌شود |

## ۵) قابلیت‌های تازه (همه پیاده‌سازی واقعی، بدون دکمهٔ تزئینی)
1. **معکوس‌سازی صدا** (`Reverser.reverseAudio`) — دیکد، معکوس فریمی، خروجی WAV، افزودن خودکار به تایم‌لاین.
2. **گام فریم** (`stepFrame`) — دکمه‌های ‹ › با گام یک فریم ۳۰fps.
3. **ذخیرهٔ فایل زیرنویس** (`saveSubtitleFile`) — SRT/VTT/ASS واقعی در Downloads/RafieiEditor.
4. **مقایسهٔ قبل/بعد عکس** — دکمهٔ «اصلی» (نگه‌داشتن).
5. **پیش‌تنظیم شخصی فیلتر/تنظیم** — ذخیره/اعمال/حذف با SharedPreferences.
6. **ازسرگیری دانلود Vosk** + پاک‌سازی لغو.

## ۶) مستندات
- `FEATURES.md` بازنویسی شد: جدول ممیزی کامل بخش‌های A تا H با چهار وضعیت (موجود و تأییدشده / موجود ولی ناقص / غایب و اضافه‌شده / نیازمند مدل یا آزمون).
- `README.md` بازنویسی شد: ساخت با GitHub Secrets و ساخت محلی.
- این فایل (`CHANGES.md`).

## ۷) چیزهایی که عمداً انجام نشد (صداقت)
- ساخت APK / دانلود Android SDK — به‌خواست کاربر.
- Inpainting، Upscale، ردیابی حرکت، لرزش‌گیری، GIF/PNG-Sequence، کراس‌فید دو-لایه، کی‌فریم شفافیت — بدون مدل یا بدون امکان آزمون واقعی اضافه نشدند (دکمهٔ جعلی ممنوع). همه در `FEATURES.md` با وضعیت ⏳ مستندند.
- کد مردهٔ تشخیص گفتار پلتفرمی در `AutoSub.kt` (خطوط ~۲۹۰–۴۹۰) دست‌نخورده ماند تا رفتارهای جانبی به‌خطر نیفتد؛ هیچ مسیری آن را صدا نمی‌زند.

## ۸) آزمون‌های اجراشده بدون build
- توازن براکت/پرانتز هر ۵۲ فایل Kotlin.
- تطابق کلاس‌های manifest با سورس (`.App` در Util.kt موجود است).
- سازگاری Gradle 8.7 ↔ AGP 8.5.2 ↔ Kotlin 1.9.24 ↔ minSdk 29 ↔ Media3 1.4.1.
- اسکن رمز در کل درخت (فقط ارجاع‌های `$ENV` باقی است).
- امضای توابع جدید (تعریف یکتا + ارجاع‌ها) و سازگاری امضای `makeSubs` در همهٔ فراخوانی‌ها.
- تحلیل JSON ذخیره/خواندن کلاس‌های مدل (متقارن).

---

# بازبینی v2.1 (کد ۳) — موتور رندر، اندروید ۷ تا ۱۳، به‌روزرسانی‌یاب و درباره ما

مبنای کار: چک‌لیست بخش ۸ و ۱۲ و ۱۴ تا ۲۱. قانون ثابت: هیچ قابلیت نمایشی/جعلی اضافه نشد؛ هر مورد یا واقعاً پیاده‌سازی شد یا صادقانه در FEATURES.md ثبت شد.

## ۱) موتور رندر و خروجی — بخش ۱۴ و ۱۵ (مهم‌ترین اصلاح)
| فایل | تغییر | دلیل |
|---|---|---|
| `Exporter.kt` → `needsOverlay` | **باگ ویدیوی خروجی بدون طرح اصلاح شد**: حالت «حذف/تعویض پس‌زمینه» (`cut`) به شرط‌های افزودن لایهٔ روی تصویر اضافه شد. قبلاً اگر تنها افکتِ پروژه تعویض پس‌زمینه بود، لایهٔ رندر به خروجی وصل نمی‌شد و **ویدیوی خام اصلی** ذخیره می‌شد | دقیقاً مشکل گزارش‌شده: «ذخیره‌شدن ویدیوی اصلی بدون طرح» |
| `Exporter.kt` → `renderPhoto` | ماسک‌های سگمنتیشن قبل از رندر عکس به‌صورت همگام آماده می‌شوند (`Seg.prepareBlocking`) | طرح تعویض پس‌زمینه روی عکس در فایل نهایی اعمال نمی‌شد (یا فقط گاهی) |
| `Seg.kt` → `lookAt(clip, relMs, sync)` | مسیر همگام برای رندر نهایی: اگر کش تخلیه شده باشد، ماسک همان‌جا محاسبه می‌شود؛ در پیش‌نمایش مثل قبل غیرهمگام | با بیش از ۴ کلیپِ دارای حذف پس‌زمینه، کش (۴ کلیپ) در میانهٔ خروجی تخلیه می‌شد و کلیپ‌های اول خام می‌ماندند |
| `Render.kt` | پرچم ThreadLocal `exportRender` + `segSync()` در هر ۴ نقطهٔ `Seg.lookAt` (drawCut، drawEqBehind، متن حلقه‌ای، افکت‌های دور بدن)؛ `TimelineOverlay.getBitmap` و `renderPhoto` آن را روشن می‌کنند | تفکیک تمیز مسیر خروجی از پیش‌نمایش؛ هیچ فریزی در UI پیش‌نمایش |
| ممیزی اتصال Preview→Export | مسیرهای زیر خط‌به‌خط دنبال شد و در هر دو سمت همان توابع‌اند: رنگ و فیلتر (`Fx.baseColor`/`colorAt` → RgbMatrix)، حرکت/ترنزیشن/انیمیشن (`Fx.dyn`/`xform` → MatrixTransformation)، کی‌فریم (`Keyframes.applyToDyn`)، وینیت/گرین/نور/ماسک/متن/استیکر/PIP/اکولایزر/افکت‌های رو بدن (`Render.drawBackground`+`drawTop` در `TimelineOverlay`)، صدا (gain/fade/EQ/duck/pitch/Sonic) | پاسخ به «بررسی اتصال سیستم Preview به سیستم Rendering» |

## ۲) پشتیبانی اندروید ۷٫۰ تا ۱۳ — بخش ۲ چک‌لیست
| فایل | تغییر | دلیل |
|---|---|---|
| `app/build.gradle` | `minSdk 29 → 24`، `versionCode 3`، `versionName "2.1"`، pin `ndkVersion 26.1.10909125` | درخواست صریح چک‌لیست؛ pin NDK برای build تکرارپذیر |
| `MediaUtil.kt` | (الف) `frameScaled` — جایگزین `getScaledFrameAtTime` (API 27+) با مسیر getFrameAtTime+scale برای API 24-26؛ در Reverse، Seg و TvShots استفاده شد (ب) شاخهٔ ذخیرهٔ گالری برای API < 29 (نوشتن مستقیم در Movies/Pictures + MediaScanner) | اندروید ۷ و ۸ این APIها را ندارند |
| `Backup.kt` | شاخهٔ ذخیرهٔ پشتیبان برای API < 29 (Downloads/RafieiEditor + MediaScanner) | همان دلیل |
| `Util.kt` → `Compat` | درخواست مجوز `WRITE_EXTERNAL_STORAGE` فقط برای API < 29، قبل از خروجی/پشتیبان‌گیری؛ پاسخ در ExportActivity و HomeActivity | مجوز runtime در اندروید ۷-۹ لازم است |
| `res/drawable/ic_launcher.xml` | آیکون adaptive به `drawable-anydpi-v26` منتقل شد؛ برای API 24-25 یک layer-list ساده ساخته شد | adaptive-icon روی اندروید ۷ کامپایل نمی‌شد |
| manifest | `WRITE_EXTERNAL_STORAGE` با `maxSdkVersion=28` + `REQUEST_INSTALL_PACKAGES` + FileProvider + AboutActivity + queries واتساپ/فیس‌بوک | حداقل تغییر با سازگاری کامل |

## ۳) به‌روزرسانی‌یاب گیت‌هاب — بخش ۸ چک‌لیست
| فایل | محتوا |
|---|---|
| `Updater.kt` (جدید) | بررسی `api.github.com/repos/<repo>/releases/latest`، مقایسهٔ عددی نسخه، نمایش عنوان/تاریخ/تغیییرات (changelog)، دانلود APK با نوار پیشرفت و لغو، بررسی سلامت فایل (پارس بسته + تطبیق حجم) و **تطبیق امضای دیجیتال با برنامهٔ نصب‌شده**؛ اگر امضا یکی باشد نصب از طریق نصب‌کنندهٔ سیستم انجام می‌شود و داده‌های کاربر حفظ می‌شود؛ اگر متفاوت باشد **قبل از** نصب هشدار صریح داده می‌شود که حذف برنامه و پاک شدن پروژه‌ها لازم خواهد بود. هیچ‌چیز خودکار نیست؛ همه‌چیز با کلیک کاربر |
| `assets/repo.txt` | یک خط `owner/repository` — بعد از انتشار مخزن پر می‌شود؛ تا آن موقع بررسی‌کننده صادقانه می‌گوید «مخزن تنظیم نشده» (نه پیام موفقیت جعلی) |
| `res/xml/file_paths.xml` | مسیر cache برای FileProvider |

## ۴) درباره ما و تماس — بخش ۱۲ چک‌لیست
| فایل | محتوا |
|---|---|
| `AboutActivity.kt` (جدید) | نام برنامه (رفیعی ادیتور)، توسعه‌دهنده (Rafi'i / رفیعی)، شماره نسخه و کد، حق نشر © 2026، دکمهٔ **واتساپ** (`wa.me/93770230338`) — اگر واتساپ نصب نباشد پیام صریح با شماره —، دکمهٔ **فیس‌بوک** (`facebook.com/share/19wCpvTPeu/`)، دکمهٔ گیت‌هاب (فقط وقتی مخزن تنظیم شده باشد) و دکمهٔ بررسی به‌روزرسانی |
| `HomeActivity.kt` | دو آیتم جدید در تنظیمات: «درباره ما و تماس با ما» و «بررسی به‌روزرسانی» |

## ۵) خطاهای کامپایل واقعی که در این بازبینی پیدا و رفع شد
- `MediaUtil.kt`: `dequeueOutputBuffer(5000)` بدون `BufferInfo` (فراخوانی ناموجود) — اصلاح شد.
- `Reverse.kt`: `import MediaExtractor` جا افتاده بود — اضافه شد.
- `EqHazaragi.kt` (۳ خط): `Math.sin/cos` با آرگومان Float — به Double تبدیل شد.
- `Panels.kt`: فراخوانی `act.roundedBg`/`act.lp` روی توابع top-level — اصلاح شد.
- `PanelsLight.kt`: مسیر اشتباه `DownloadConditions` (`com.google.mlkit.nl.translate` → `com.google.mlkit.common.model`) — اصلاح شد.
- آیکون adaptive (خطای aapt با minSdk 24) — تقسیم به v26 و legacy.

## ۶) نتیجهٔ build واقعی
- `gradle assembleRelease` (Gradle 8.7، AGP 8.5.2، JDK 21): **BUILD SUCCESSFUL** — APK ۱۳۲MB، `versionName 2.1 (3)`، `minSdk 24`، `targetSdk 34`، هر دو ABI (`arm64-v8a` + `armeabi-v7a`) با `librafiei_whisper.so` بومی، ۲۰ فونت، مدل Vosk فارسی.
- امضا: بدون وجود کلید انتشار، APK با کلید debug امضا شده (نصب آزمایشی ممکن است؛ برای انتشار با Secrets در CI کلید اصلی امضا می‌شود).
- `apksigner verify` روی خروجی اجرا شد (امضای معتبر).

---

# v2.2 — خروجی بدون افکت (بررسی خودکار + مرحلهٔ دوم)
- علت قطعی از روی خواندن کد پیدا نشد (Media3 روی این دستگاه آزمایش نشده است). مسیر ذخیره‌سازی پروژه، `needsOverlay` و ساخت `TimelineOverlay` درست بودند.
- `Exporter.overlayVisible`: بعد از رندر، در لحظه‌ای که لایهٔ افکت پیکسل‌های کاملاً مات دارد، فریم فایل خروجی با آن مقایسه می‌شود.
- اگر لایهٔ افکت در فایل نبود: `Exporter.buildOverlayPass` افکت‌ها را روی خودِ فایل مرحلهٔ اول (به‌عنوان افکت خود آیتم، روش معمول واترمارک) رسم می‌کند و دوباره بررسی می‌شود.
- پیام صفحهٔ پایان صادقانه می‌گوید: تأیید شد / در مرحلهٔ دوم اعمال شد / اعمال نشد.

## v2.3
- Export now has a 3-stage chain so effects/designs always reach the saved video: (1) Media3 overlay, (2) overlay pass over the result if verification shows nothing was drawn, (3) SoftOverlay: frame-by-frame software compositing with Render (slower, audio copied).
- Photos are rendered through the canvas path (Exporter.renderPhoto), same Render code as the preview.
- Not compiled/tested on a device.

## v2.3.1
- Done screen now shows version/pass/check result, and says honestly when the effects could not be verified (before it claimed success).

## v2.3.2
- Verification result "unknown" (translucent layer, e.g. opacity < 100%) is no longer trusted after pass 1: pass 2 runs, so opacity changes reach the file.

## v2.3.3
- Pass-2 check is now translucency-aware (Exporter.overlayMatches): it blends the layer over the pass-1 frame and compares with the pass-2 frame, so a layer at opacity < 100% is verified too; a mismatch goes to pass 3 (SoftOverlay), which now reads the pass-1 file (no double layer).
