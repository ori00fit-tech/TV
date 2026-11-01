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
| تطبيقات مخفية من الشبكة (تبقى مُفعّلة) | `com.stark.store`، `com.cvte.tv.steward`، `com.cvte.tv.pictorial`، `com.android.traceur` |
| زر Inputs وزر SOURCE على الواجهة | يفتحان `com.cvte.tv.globalui/.InputSourceActivity` |
| زر Home | `stock_home_redirect=com.cvte.tv.launcher.mars`: كلما ظهرت واجهة CVTE تعيدك خدمة HomeKeyService إلى الواجهة الجديدة |
| `key_filter` | `false`: الخدمة لا تعترض أي زر، فيبقى الضغط المطوّل على الصوت يعمل بشكل طبيعي |
| بطاقات الإضافات | معطّلة (لا نوافذ أسئلة) |

الواجهة القديمة (CVTE) **لا تُحذف ولا تُعطّل**. هذه قاعدة لأجهزة CVTE: لا توجد واجهة احتياطية
(FallbackHome)، وإذا عُطّلت قد لا يقلع التلفاز إلى واجهة. الواجهة الجديدة تحلّ محلها عبر إعادة التوجيه.

## البناء

```sh
git clone https://github.com/Gathin23/cleanyourtv && cd cleanyourtv
git checkout f47c893d285172d175c8f72c32658b1d767b3270
git apply ../goldvision-launcher.patch
cd launcher && ./gradlew --no-daemon testReleaseUnitTest assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk
```

يحتاج JDK 17 أو أحدث، وAndroid SDK (platform 34، build-tools 34.0.0).

## التثبيت من Termux على الهاتف

```sh
termux-setup-storage
adb connect 192.168.1.143:5555
adb push ~/storage/downloads/app-release.apk /data/local/tmp/tvhome.apk
adb shell pm install -r /data/local/tmp/tvhome.apk
adb shell rm /data/local/tmp/tvhome.apk

# التحقق من أن لوحة المصادر موجودة على هذا التلفاز
adb shell cmd package resolve-activity --brief -n com.cvte.tv.globalui/.InputSourceActivity

# جعل الواجهة الجديدة هي Home
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
```

ثم أعد التشغيل **مرة واحدة** وتحقق من أن التلفاز يقلع وأن ADB يعود:

```sh
adb reboot
# انتظر دقيقة ونصف تقريباً
adb connect 192.168.1.143:5555
adb shell dumpsys activity activities | grep mFocusedApp
```

بعد ذلك فقط فعّل خدمة زر Home (لا تفعلها قبل إعادة التشغيل الأولى، حسب قواعد PROMPT.md):

```sh
adb shell 's=$(settings get secure enabled_accessibility_services); case "$s" in null|"") settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService ;; *gathin*) ;; *) settings put secure enabled_accessibility_services "$s:com.gathin.tvhome/.HomeKeyService" ;; esac; settings put secure accessibility_enabled 1'
adb shell settings get secure enabled_accessibility_services
```

اضغط زر Home في **الريموت الحقيقي**. الزر المُرسل من ADB لا يُثبت شيئاً، ثم:

```sh
adb shell dumpsys activity activities | grep mFocusedApp   # يجب أن يظهر com.gathin.tvhome
adb exec-out screencap -p > ~/storage/downloads/shot.png
```

ملاحظات:
- عند الضغط على Home قد تظهر واجهة CVTE لجزء من الثانية قبل التحويل. هذا طبيعي مع `key_filter=false`.
- `am force-stop com.gathin.tvhome` يُطفئ خدمة HomeKeyService. أعد تشغيلها بأمر التفعيل أعلاه.

## التراجع

```sh
adb shell settings put secure enabled_accessibility_services '""'
adb shell cmd package set-home-activity com.cvte.tv.launcher.mars/.ui.home.HomeActivity
adb shell pm uninstall com.gathin.tvhome   # حذف تطبيقنا نحن فقط، وليس تطبيق نظام
```

## في النهاية

أطفئ **ADB ENABLE** من قائمة المصنع (MENU ثم 1147 على أحد المصادر، ثم DEBUG ثم ADB ENABLE ثم OFF)،
وتحقق من أن المنفذ 5555 مغلق:

```sh
nc -zv 192.168.1.143 5555   # يجب أن يفشل الاتصال
```
