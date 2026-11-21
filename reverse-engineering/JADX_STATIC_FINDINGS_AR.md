# نتائج الفحص الثابت الأولية لـ Concepts — JADX

> الحالة: تقرير استطلاع أولي مبني على مخرجات JADX التي جُمعت محلياً في Termux وأُرسلت في جلسة التحليل. ليس تقرير تشغيل REA، ولا يثبت وحده سلوك التطبيق وقت التشغيل.

## هوية العينة

- اسم العينة: `Concepts 2022.04.10.apk`
- SHA-256: `47e4e5244f2e2284349888a3fad00f407c1f0503429ec3d0e469f24924a98fdf`
- package: `com.tophatch.concepts`
- versionName: `2022.04.10`
- versionCode: `220600201`
- ملفات DEX المرصودة: `classes.dex` و`classes2.dex` و`classes3.dex` و`classes4.dex`

لا يتضمن هذا المستودع ملف APK أو ملفات المكتبات الثنائية أو أصول التطبيق.

## نتيجة JADX

- اكتمل فك الحزمة مع 109 أخطاء decompilation معلنة في الملخص.
- عدد الملفات المستخرج إجمالاً: 12,312.
- عدد ملفات المصدر: 9,317.
- عدد ملفات الموارد: 1,449.
- الحجم التقريبي لمجلد الإخراج: 226 MB.
- لم يظهر تحذير مستقل في المخرجات التي نُقلت أثناء الجلسة.
- هذه الأخطاء لا تعني أن كل التحليل فشل؛ لكنها تعني أن بعض الأصناف قد لا تكون قابلة للقراءة أو قد تكون غير مكتملة. يلزم مراجعة سجل JADX التفصيلي قبل الحكم على أثرها.

## بنية الرسم التي ظهرت في أسماء الأصناف

ظهرت ضمن مساحة `com.tophatch.concepts.core` أصناف تحمل أسماء مرتبطة بـ:

- `Engine`, `Canvas`, `NativeCanvas`
- `CanvasRendererController`, `NativeCanvasRendererController`
- `NativeCanvasToolController`, `NativeCanvasDocumentController`
- `CanvasInputListener`, `CanvasInputEventHandler`, `InputEventPacker`
- `CanvasTransformController`, `CanvasLayerController`, `CanvasUndoController`
- `CanvasShapeGuideController`, `CanvasMeasurementController`
- `NativeCanvasSnapController`, `NativeCanvasInputController`

هذه الأسماء قرائن على تقسيم مسؤوليات الرسم والإدخال والتحويل والطبقات والتراجع، لكنها لا تكفي وحدها لإثبات تفاصيل التنفيذ.

## أدلة محددة أُرسلت أثناء التحليل

1. في `core/Engine.java` ظهر تصريح دالة native:
   `public final native Canvas createCanvas(Context applicationContext);`
2. في `di/EngineModule.java` ظهر إنشاء كائن `Engine` ثم استدعاء `initialize(...)`.
3. في `di/CanvasModule.java` ظهر استدعاء `engine.createCanvas(context)`.
4. في `CanvasFragment.java` ظهر استدعاء تهيئة سطح الرسم عبر `appCanvasSurfaceView.initialize(canvas, textView)`.
5. في `utility/NativeKt.java` ظهرت دالة عامة لتحميل مكتبة native باستخدام ReLinker، وتتلقى اسم المكتبة والإصدار كوسيطين.
6. لم يُحدّد بعد من المخرجات المتاحة اسم المكتبة التي تُحمّل فعلياً لتهيئة `Engine`، ولا يجوز افتراض أن `libCore.so` هي محرك الرسم دون تتبع الاستدعاءات.
7. ظهر وجود مجلدات مكتبات native ضمن موارد APK، ومن الأسماء التي وردت في قائمة المستخدم `libCore.so` و`libpdfium.cr.so` و`libcblite.so` و`librsjni.so` و`libgrpc.so`. لم يكتمل بعد جرد كل ABI وربط المكتبات بمستدعيها.

## الملفات ذات الأولوية للتحليل التالي

- `core/Engine.java`
- `core/NativeObject.java`
- `core/Canvas.java`
- `core/CanvasInputEventHandler.java`
- `core/InputEventPacker.java`
- `core/CanvasFragment.java`
- `utility/NativeKt.java`
- `di/EngineModule.java`
- `di/CanvasModule.java`

## الأسئلة المفتوحة

- ما اسم المكتبة التي تُحمّل قبل إنشاء `Engine)، وأين يقع استدعاء التحميل؟
- ما دورة حياة كائنات `NativeObject)، وكيف تُدار المراجع والتحرير؟
- كيف تُحوّل أحداث MotionEvent إلى صيغة الإدخال التي يستهلكها محرك Canvas؟
- كيف تنتقل بيانات الضغط والميل والإحداثيات إلى الطبقة native؟
- ما ملفات ABI الموجودة فعلياً، وما المكتبات المشتركة بينها؟
- ما حدود المسؤولية بين واجهة Android وطبقة JNI والمحرك الأصلي؟

## الخطوة التحليلية التالية

اجمع تقريراً منقحاً يحتوي على مواضع الاستدعاء ذات الصلة في الأصناف أعلاه، مع جرد المكتبات حسب ABI. لا تُدرج شيفرة مطولة أو ملفات ثنائية أو أصولاً محمية. اربط كل استنتاج باسم الملف والطريقة والدليل الذي يدعمه.

## حدود هذا التقرير

- التقرير ثابت/استطلاعي؛ لم يُشغّل APK ولم يُنفذ تحليل ديناميكي.
- لم يُنفذ REA على العينة حتى تاريخ إعداد هذا التقرير.
- لا يثبت هذا التقرير استعادة الشيفرة المصدرية الأصلية أو إمكان إعادة بناء محرك Concepts الأصلي.
- يجب عدم وصف إعادة البناء العربية بأنها نسخة مطابقة للأصل؛ الهدف تنفيذ مستقل للوظائف التي تثبتها الأدلة والاختبارات.
