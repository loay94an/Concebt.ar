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

## تشغيل REA على APK Concepts الأصلي

أضيف مسار مستقل:
`.github/workflows/rea-concepts-analysis.yml`

بعد تجهيز متغيرات المصدر الخاص والسر أعلاه، افتح:
Actions → **REA analysis of original Concepts APK** → Run workflow.

يشغّل المسار REA الرسمي على Ubuntu x64 مع Node.js 24 وJDK 21، ويستخدم JADX MCP المثبّت من fixture الرسمي، ثم ينفذ:
- فحص حزمة APK.
- البحث عن `Engine` و`Canvas` و`InputEventPacker`.
- فحص صنفي `Engine` و`Canvas` والدالة `Engine.createCanvas`.
- تتبع مراجع `Engine` و`NativeObject`.

يُرفض الملف ما لم تطابق بصمة SHA-256 النسخة الأصلية المعروفة. لا تُنشر سجلات REA الخام أو ملفات JADX أو APK؛ يُنشر ملخص عربي منقح فقط. نجاح workflow يعني أن أوامر REA نُفذت على APK الأصلي، وليس أن كل المكتبات الأصلية JNI قد فُهمت أو أن التطبيق أُعيد بناؤه.
