# واجهة Goldvision GV-32HF900013TS-SMART (CVTE / Amlogic T950S)

واجهة رئيسية خفيفة وبدون إعلانات، مبنية على المشغّل المرجعي
[cleanyourtv/launcher](https://github.com/Gathin23/cleanyourtv) (الحزمة `com.gathin.tvhome`):
Java عادي، بلا صلاحية إنترنت، بلا تتبّع، وحجم APK أقل من 576 KB. مناسبة لتلفاز بذاكرة 1 GB.

الملف `goldvision-launcher.patch` يحتوي على كل التعديلات. طبّقه على الـ commit
`f47c893d285172d175c8f72c32658b1d767b3270` من cleanyourtv.

## الإعدادات المختارة

| الإعداد | القيمة |
|---|---|
| اللغة | عربية بأرقام غربية (`ar-u-nu-latn`)، والواجهة من اليمين إلى اليسار |
| الشريط السفلي (5 خانات) | YouTube، Stremio، MVAS IPTV، Downloader، Play Store |
| الشكل | `cinematic_home` (صورة التطبيق المحدد تملأ أعلى الشاشة)، 5 أعمدة، خلفية سوداء |
| الحركة | `reduced_motion=true` — أخف على الـ GPU والرام (مهم لتلفاز 1GB) |
| بطاقات الإضافات | مفعّلة — تظهر بطاقات Weather / Prayer / … بعد تثبيتها |
| تطبيقات مخفية من الشبكة (تبقى مُفعّلة) | `com.stark.store`، `com.cvte.tv.steward`، `com.cvte.tv.pictorial`، `com.android.traceur` |
| زر Inputs وزر SOURCE على الواجهة | يفتحان `com.cvte.tv.globalui/.InputSourceActivity` |
| زر Home | `stock_home_redirect=com.cvte.tv.launcher.mars`: كلما ظهرت واجهة CVTE تعيدك خدمة HomeKeyService إلى الواجهة الجديدة |
| `key_filter` | `false`: الخدمة لا تعترض أي زر، فيبقى الضغط المطوّل على الصوت يعمل بشكل طبيعي |

الواجهة القديمة (CVTE) **لا تُحذف ولا تُعطّل**. هذه قاعدة لأجهزة CVTE: لا توجد واجهة احتياطية
(FallbackHome)، وإذا عُطّلت قد لا يقلع التلفاز إلى واجهة. الواجهة الجديدة تحلّ محلها عبر إعادة التوجيه.

## الحزمة الكاملة (Launcher + إضافات)

سير العمل يبني:

| الملف | الوصف |
|---|---|
| `goldvision-tvhome.apk` | الواجهة الرئيسية (خفيف) |
| `addon-weather.apk` | الطقس (Open-Meteo، بدون مفتاح) |
| `addon-prayer-times.apk` | مواقيت الصلاة — **العرائش**، طريقة المغرب (21) |
| `addon-photo-screensaver.apk` | شاشة توقف بصورك |
| `addon-sleep-timer.apk` | مؤقت إطفاء |
| `addon-capacity-stats.apk` | حالة الرام والتخزين |

### تحميل من GitHub Actions

1. تبويب **Actions** → آخر تشغيل ناجح لـ **Build launcher + addons**
2. حمّل `goldvision-full-pack` من **Artifacts**
3. فك الضغط

## التثبيت من Termux

```sh
termux-setup-storage
adb connect 192.168.1.143:5555

# 1) الواجهة
adb push ~/storage/downloads/goldvision-tvhome.apk /data/local/tmp/tvhome.apk
adb shell pm install -r /data/local/tmp/tvhome.apk

# 2) الإضافات (ثبّت ما تريد)
for f in addon-weather addon-prayer-times addon-photo-screensaver addon-sleep-timer addon-capacity-stats; do
  adb push ~/storage/downloads/$f.apk /data/local/tmp/$f.apk
  adb shell pm install -r /data/local/tmp/$f.apk
  adb shell rm /data/local/tmp/$f.apk
done

adb shell rm /data/local/tmp/tvhome.apk

# جعل الواجهة الجديدة هي Home
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
```

ثم أعد التشغيل **مرة واحدة**:

```sh
adb reboot
# انتظر دقيقة ونصف تقريباً
adb connect 192.168.1.143:5555
```

بعد الإقلاع، فعّل خدمة زر Home:

```sh
adb shell 's=$(settings get secure enabled_accessibility_services); case "$s" in null|"") settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService ;; *gathin*) ;; *) settings put secure enabled_accessibility_services "$s:com.gathin.tvhome/.HomeKeyService" ;; esac; settings put secure accessibility_enabled 1'
```

اضغط زر **Home** في الريموت الحقيقي. عند أول ظهور لبطاقة إضافة اختر **Show card** لتظهر على الشبكة.

```sh
adb shell dumpsys activity activities | grep mFocusedApp   # يجب أن يظهر com.gathin.tvhome
```

## البناء يدوياً

```sh
git clone https://github.com/Gathin23/cleanyourtv && cd cleanyourtv
git checkout f47c893d285172d175c8f72c32658b1d767b3270
git apply /path/to/goldvision-launcher.patch
cp /path/to/addons-config/prayer-times-config.xml addons/prayer-times/app/src/main/res/values/config.xml
cd launcher && ./gradlew --no-daemon assembleRelease
# ثم ابنِ كل إضافة من مجلدها: cd ../addons/weather && ./gradlew assembleRelease
```

يحتاج JDK 17+ و Android SDK (platform 34).

## التراجع

```sh
adb shell settings put secure enabled_accessibility_services '""'
adb shell cmd package set-home-activity com.cvte.tv.launcher.mars/.ui.home.HomeActivity
adb shell pm uninstall com.gathin.tvhome
# الإضافات (اختياري):
# adb shell pm uninstall com.gathin.weather
# adb shell pm uninstall com.gathin.prayertimes
# ... حسب اسم الحزمة الفعلي لكل إضافة
```

## في النهاية

أطفئ **ADB ENABLE** من قائمة المصنع (MENU ثم 1147 على أحد المصادر، ثم DEBUG ثم ADB ENABLE ثم OFF).
