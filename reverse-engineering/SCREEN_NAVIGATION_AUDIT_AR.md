# تدقيق الشاشات الفعلية والتنقل — Concepts 2022.04.10

## مرجع التدقيق وحدود الإثبات

العينة المرجعية هي Concepts 2022.04.10 (package: `com.tophatch.concepts`). يعتمد هذا الملف على نتائج JADX الثابتة المسجلة في `JADX_STATIC_FINDINGS_AR.md` و`CONCEPTS_DEEP_STATIC_REPORT_AR.md`، وعلى التصريحات الأصلية التي أمكن رصدها. لم تُسجّل حتى الآن جلسة تشغيل مصورة للأصل أو تفريغ كامل لـ AndroidManifest وموارد التنقل. لذلك لا ننسب مسارات انتقال غير مثبتة إلى التطبيق.

## الشاشات/الأسطح المؤكدة من أسماء الأصناف واستدعاءاتها

| السطح | النوع المعروف | الدليل الثابت | ما يمكن إثباته | حالة إعادة البناء |
|---|---|---|---|---|
| MainActivity | Activity | `com/tophatch/concepts/MainActivity` ووجود MotionEvent في التقرير | نشاط رئيسي/حاوية تفاعل ظاهرة في التطبيق؛ دور launcher يحتاج manifest الأصلي لتأكيده | لدينا MainActivity عربية مستقلة |
| CanvasFragment | Fragment | `CanvasFragment.java` ونداء `appCanvasSurfaceView.initialize(canvas, textView)` | سطح مساحة الرسم يهيأ باستخدام Canvas أصلي وعرض نص مرافق | مساحة الرسم العربية موجودة؛ ليست مطابقة للأصل |
| StoreActivity | Activity | `com/tophatch/concepts/StoreActivity` وظهور MotionEvent ضمن نتائج البحث | وجود نشاط متجر؛ محتوى المتجر والتنقل الداخلي غير موثقين بعد | غير منفذ كمتجر أصلي؛ لا نعيد بناء خدمات/حسابات مغلقة |
| Canvas Surface | View/Surface مخصص | `appCanvasSurfaceView.initialize(...)` | سطح عرض منفصل يستقبل كائن Canvas | استبدال مستقل بـ Compose Canvas |
| Tool preview canvas | سطح/مورد معاينة | `NativeCanvasToolController.createToolPreviewCanvasImpl` و`tearDownToolPreviewCanvasImpl` | وجود دورة حياة لمعاينة الأدوات | معاينات الفرش في واجهتنا غير مكتملة |
| PDF render task | مهمة خلفية/مورد | `NativePDFRenderTask.dispose` | وجود كائن مهمة عرض PDF | استيراد/عرض PDF غير منفذ |

## خريطة التنقل التي يمكن إثباتها حالياً

- `MainActivity` و`StoreActivity` صنفان من نوع Activity في نتائج التحليل.
- `CanvasFragment` مسؤول عن تهيئة سطح الرسم.
- توجد طبقة DI باسم `EngineModule` تنشئ Engine، و`CanvasModule` تستدعي `engine.createCanvas(context)`.
- لم يثبت من الملفات المتاحة بعد هل MainActivity يضيف CanvasFragment مباشرة أو عبر Navigation Component، ولا كيف يتم الانتقال إلى StoreActivity. لا نضع سهماً بين الشاشات قبل قراءة موضع الاستدعاء الفعلي.
- لا توجد حتى الآن قائمة موثقة بكل Dialog/BottomSheet/PopupMenu/Preference screen في النسخة الأصلية؛ يجب استخراجها من Manifest وموارد navigation وواجهات Fragment/Dialog في JADX.

## عناصر التنقل والواجهات التي يجب استكمال استخراجها

1. AndroidManifest الأصلي: كل Activity، exported، intent filters، deep links، launch mode.
2. كل Fragment subclasses مع مواضع إنشائها/إضافتها.
3. Navigation XML/Compose graph إن وجد، وموارد menu وpreference.
4. كل DialogFragment وBottomSheetDialogFragment وPopupWindow.
5. شاشات الحساب/المتجر/المساعدة/الإعدادات مع فصل ما يعتمد على خدمة خارجية.
6. ربط كل زر/قائمة بمستمع الحدث والوجهة، لا الاكتفاء باسم resource أو النص.

## شاشات إعادة البناء العربية الحالية

- مساحة الرسم الرئيسية في `MainActivity`.
- معرض المستندات (حوار داخل MainActivity).
- إدارة الطبقات (حوار داخل MainActivity).
- اختيار/استيراد صورة عبر Android document picker.
- تصدير SVG وPDF وDXF وPSD عبر Android share chooser.
- لا يوجد حالياً تدفق مستقل لإدارة الصفحات/Artboards، أو شاشة إعدادات شاملة، أو متجر، أو استيراد PDF.

هذه قائمة بواجهة مشروعنا وليست ادعاءً بأنها مطابقة لشاشات الأصل.

## شروط إغلاق تدقيق الشاشات

لا تُعلّم شاشة بأنها مكتملة إلا عند وجود:
- مصدر الدليل (Manifest/ملف Java/موضع استدعاء/مورد).
- صورة مرجعية من تشغيل APK الأصلي.
- مسار دخول وخروج موثق.
- عناصر تحكم وحالات فارغة/تحميل/خطأ.
- اختبار تنقل آلي أو يدوي موثق.
