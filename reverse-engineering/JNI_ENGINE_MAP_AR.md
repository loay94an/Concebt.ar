# خريطة JNI لمحرك Concepts الأصلي

هذه الخريطة مبنية على تحليل ELF والرموز المصدّرة من `lib/armeabi-v7a/libCore.so` في العينة الأصلية، إضافة إلى تصريحات JNI التي استخرجها JADX. هي أدلة ثابتة وليست وصفاً مؤكداً لكل مسارات التشغيل.

## دورة المحرك والرسم

- `Engine.createCanvas`: نقطة إنشاء Canvas من طبقة Java إلى المحرك الأصلي.
- `Engine.dispose`: إنهاء مورد المحرك.
- `NativeCanvas.getControllerImpl`: مدخل إلى وحدات التحكم الخاصة باللوحة.
- ظهرت في السلاسل الثنائية رموز C++ مثل `TH::JNI::unpackInputEvent` و`TH::JNI::InputPoint`، ما يدل على طبقة تحويل لأحداث الإدخال إلى نقاط داخلية قبل وصولها إلى المحرك.

## الإدخال والقلم

- `NativeCanvasInputController`: موضع السحب، سلوك الضغط المطوّل، الإجراء الثانوي، وإعدادات إجراءات الإصبع.
- `NativeStylusManager`: اكتشاف القلم المتصل وتنفيذ إجراءات القلم.
- `InputProxy` و`DeviceInfo`: مواضع Java التي تقرأ الضغط ومحاور MotionEvent.
- استنتاج تنفيذي: يجب أن يحافظ تطبيق إعادة البناء على عينات الضغط والمحاور والتوقيت، لا أن يكتفي بنقاط x/y النهائية.

## التحويل والطبقات والأدوات

- `NativeCanvasTransformController`: تمكين التكبير وميزة Zoom Snap.
- `NativeCanvasLayerController`: الطبقة النشطة، نمط الطبقات، ومصغراتها.
- `NativeCanvasToolController`: الأدوات والفتحات النشطة واللون والشفافية والسماكة ونعومة الفرشاة.
- `NativeCanvasSnapController`: خيارات الالتقاط والمحاذاة.
- `NativeCanvasShapeGuideController`: نوع دليل الشكل وتدويره ومدى توفره.
- `NativeCanvasMeasurementController`: وحدة القياس المحلية وتحويل القياسات إلى الرسم.

## المستندات والتصدير

- `NativeCanvasDocumentController`: إنشاء مستند، تحميله، حفظه، وإكمال الكتابات غير المتزامنة.
- `NativeCanvasExportController`: امتداد الملف، مناطق التصدير، إعداد PPI، وتصدير PDF raster وPSD.
- `NativePDFHandle` و`NativePDFRenderTask`: عرض صفحات PDF وإدارة مهام العرض.

## توافق ABI

- جرد APK كشف 90 مكتبة أصلية ضمن `armeabi-v7a` فقط.
- `libCore.so` ملف ELF32 لمعمارية ARM.
- لا يظهر مجلد `arm64-v8a` في العينة. لذلك لا يجوز نسخ المكتبة مباشرة إلى تطبيق 64-bit والافتراض أنها ستعمل؛ يلزم بناء/تشغيل 32-bit متوافق أو إعادة تنفيذ المحرك.

## ترتيب التنفيذ المقترح

1. توثيق تسلسل إنشاء `Engine` و`Canvas` وربط Controllers مع مستند فارغ.
2. مقارنة حزم InputEvent وInputPoint وربط الضغط/الميل/الاتجاه بعينات القلم.
3. إعادة بناء Transform وSnap ثم ShapeGuide وMeasurement كوحدات مستقلة.
4. بناء نموذج مستند متعدد الطبقات، وربط Undo/Redo وحفظ غير متزامن.
5. مقارنة PDF/PSD export بعد تحديد صيغة البيانات الداخلية.
6. عدم دمج `libCore.so` أو نسخ شيفرتها/أصولها إلى التطبيق العربي؛ استخدم نتائج الواجهة لفهم السلوك وبناء تنفيذ مستقل مع مراجعة الترخيص.

## مصادر الأدلة

- [تقرير JADX وELF/JNI المتعمق](CONCEPTS_DEEP_STATIC_REPORT_AR.md)
- [تقرير REA الأولي](REA_CONCEPTS_ORIGINAL_REPORT_AR.md)
