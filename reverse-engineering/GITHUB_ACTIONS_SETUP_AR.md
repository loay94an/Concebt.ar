# إعداد التحليل الآلي عبر GitHub Actions

لا ترفع APK الأصلي أو ملفات JADX المفكوكة إلى المستودع العام.

## مصدر العينة الخاص
1. أنشئ مستودع GitHub خاصاً.
2. أنشئ Release tag باسم `concepts-original`.
3. أرفق ملف `Concepts 2022.04.10.apk` كـ Release asset.
4. يجب أن تطابق بصمته SHA-256:
   `47e4e5244f2e2284349888a3fad00f407c1f0503429ec3d0e469f24924a98fdf`.

## صلاحية القراءة
أنشئ Fine-grained Personal Access Token بصلاحية Contents: read للمستودع الخاص فقط. لا ترسل الرمز في المحادثة ولا تضعه في أي ملف.

في مستودع `loay94an/Concebt.ar` افتح Settings → Secrets and variables → Actions.

أضف Repository secret:
- `APK_SOURCE_TOKEN`: الرمز الذي يقرأ المستودع الخاص.

أضف Repository variables:
- `APK_SOURCE_REPO`: المستودع الخاص بصيغة `owner/repo`.
- `APK_RELEASE_TAG`: `concepts-original` أو وسم الإصدار الذي اخترته.
- `APK_ASSET_NAME`: `Concepts 2022.04.10.apk`.

## التشغيل
Actions → Static reverse engineering (private APK) → Run workflow.

يتحقق المسار من SHA-256 قبل فك الحزمة، ويثبت Java وJADX، ثم ينشئ تقريراً منقحاً في:
`reverse-engineering/JADX_AUTOMATED_REPORT_AR.md`

لا يرفع ملفات Java المفكوكة أو الموارد أو المكتبات الثنائية. يحتفظ بتقرير آلي كـ artifact لمدة 30 يوماً.

## حدود واضحة
- هذا المسار يشغّل JADX CLI وليس REA MCP.
- JADX تحليل ثابت ولا يشغّل التطبيق.
- لا يستطيع GitHub الوصول إلى ملف الهاتف قبل توفيره في المصدر الخاص.
- الهدف استخلاص مواصفات السلوك وإعادة تنفيذ مستقلة، لا إعادة نشر الشيفرة أو الأصول التجارية.

## تشغيل REA على APK Concepts الأصلي من نفس المستودع

يستخدم المسار الآن مستودع `loay94an/Concebt.ar` نفسه كمصدر للملف، ولا يحتاج إلى مستودع ثانٍ أو `APK_SOURCE_TOKEN` أو متغيرات Repository variables.

### تجهيز ملف APK مرة واحدة
1. افتح صفحة [Releases](https://github.com/loay94an/Concebt.ar/releases).
2. أنشئ Release جديداً بالوسم:
   `concepts-original-2022.04.10`
3. أرفق الملف باسمه المطابق تماماً:
   `Concepts 2022.04.10.apk`
4. يجب أن تطابق بصمة SHA-256:
   `47e4e5244f2e2284349888a3fad00f407c1f0503429ec3d0e469f24924a98fdf`.

**تنبيه خصوصية:** المستودع الحالي عام؛ لذلك فإن رفع APK كـ Release asset في هذا المستودع يجعله متاحاً للعامة. لا ترفع الملف هنا إذا لم تكن تملك حق نشره علناً. في هذه الحالة يلزم استخدام مستودع خاص منفصل بدلاً من هذا الإعداد.

### التشغيل
بعد إرفاق الملف، افتح Actions → **REA analysis of original Concepts APK** → Run workflow. لا تشغّل المسار قبل إنشاء الإصدار وإرفاق الملف.

ينزّل المسار الملف من Release في المستودع نفسه باستخدام رمز GitHub Actions المدمج، ويتحقق من SHA-256 قبل التحليل. ثم يشغّل REA الرسمي على Ubuntu x64 مع Node.js 24 وJDK 21، وينفذ:
- فحص حزمة APK.
- البحث عن `Engine` و`Canvas` و`InputEventPacker`.
- فحص صنفي `Engine` و`Canvas` والدالة `Engine.createCanvas`.
- تتبع مراجع `Engine` و`NativeObject`.

لا تُنشر سجلات REA الخام أو ملفات JADX المفكوكة؛ ينشر ملخص عربي منقح فقط. نجاح workflow يعني أن أوامر REA نُفذت على APK الأصلي، وليس أن كل المكتبات الأصلية JNI قد فُهمت أو أن التطبيق أُعيد بناؤه.
