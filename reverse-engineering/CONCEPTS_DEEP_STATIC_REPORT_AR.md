# تقرير التحليل الثابت المتعمق — Concepts 2022.04.10

- SHA-256 للعينة: `47e4e5244f2e2284349888a3fad00f407c1f0503429ec3d0e469f24924a98fdf`
- ملفات Java الناتجة من JADX: 9317
- ملفات DEX داخل APK: classes.dex, classes2.dex, classes3.dex, classes4.dex
- مكتبات native: 90

## أصناف المحرك والإدخال
- `Engine`: com/tophatch/concepts/core/Engine.java
- `NativeObject`: com/tophatch/concepts/core/NativeObject.java
- `Canvas`: com/tophatch/concepts/core/Canvas.java
- `CanvasInputEventHandler`: com/tophatch/concepts/core/CanvasInputEventHandler.java
- `InputEventPacker`: com/tophatch/concepts/core/InputEventPacker.java
- `CanvasTransformController`: com/tophatch/concepts/core/CanvasTransformController.java
- `CanvasLayerController`: com/tophatch/concepts/core/CanvasLayerController.java
- `CanvasUndoController`: com/tophatch/concepts/core/CanvasUndoController.java
- `CanvasRendererController`: com/tophatch/concepts/core/CanvasRendererController.java
- `NativeCanvasRendererController`: com/tophatch/concepts/core/NativeCanvasRendererController.java
- `NativeCanvasInputController`: com/tophatch/concepts/core/NativeCanvasInputController.java
- `NativeCanvasToolController`: com/tophatch/concepts/core/NativeCanvasToolController.java
- `NativeCanvasDocumentController`: com/tophatch/concepts/core/NativeCanvasDocumentController.java
- `CanvasFragment`: com/tophatch/concepts/CanvasFragment.java
- `NativeKt`: com/tophatch/concepts/utility/NativeKt.java
- `EngineModule`: com/tophatch/concepts/di/EngineModule.java
- `CanvasModule`: com/tophatch/concepts/di/CanvasModule.java

## مؤشرات أحداث اللمس والقلم
- `getPressure(`: com/tophatch/concepts/core/InputProxy.java; com/tophatch/concepts/core/DeviceInfo.java
- `getAxisValue(`: com/tophatch/concepts/core/InputProxy.java; com/tophatch/concepts/core/DeviceInfo.java; androidx/recyclerview/widget/RecyclerView.java; androidx/core/widget/NestedScrollView.java; androidx/core/view/MotionEventCompat.java
- `AXIS_TILT`: androidx/core/view/MotionEventCompat.java
- `AXIS_ORIENTATION`: androidx/core/view/MotionEventCompat.java
- `ACTION_POINTER_DOWN`: androidx/core/view/MotionEventCompat.java
- `ACTION_POINTER_UP`: androidx/core/view/MotionEventCompat.java
- `MotionEvent`: com/helpshift/views/bottomsheet/HSBottomSheetBehaviour.java; com/helpshift/support/conversations/NewConversationFragment.java; com/tophatch/concepts/PinchZoomTouchHandler.java; com/tophatch/concepts/MainActivity.java; com/tophatch/concepts/StoreActivity.java
- `createCanvas(`: com/tophatch/concepts/di/CanvasModule.java; com/tophatch/concepts/core/Engine.java
- `loadLibrary(`: com/tophatch/concepts/BugsnagImplementation.java; com/tophatch/concepts/utility/NativeKt.java; com/google/android/play/core/splitinstall/SplitInstallHelper.java; com/getkeepsafe/relinker/ReLinkerInstance.java; com/getkeepsafe/relinker/ReLinker.java
- `ReLinker.loadLibrary`: com/tophatch/concepts/BugsnagImplementation.java

## مواضع تحميل المكتبات
- com/tophatch/concepts/BugsnagImplementation.java
- com/tophatch/concepts/utility/NativeKt.java
- com/google/android/play/core/splitinstall/SplitInstallHelper.java
- com/getkeepsafe/relinker/ReLinkerInstance.java
- com/getkeepsafe/relinker/ReLinker.java
- com/getkeepsafe/relinker/SystemLibraryLoader.java
- com/bugsnag/android/AnrPlugin.java
- com/bugsnag/android/LibraryLoader.java
- com/bugsnag/android/RootDetector.java
- com/bugsnag/android/NdkPlugin.java
- androidx/renderscript/RenderScript.java

## تصريحات native التي أمكن قراءتها
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native void addListenerImpl(CanvasBackgroundListener listener);
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native int customColorImpl();
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native boolean isDarkImpl();
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native String nameImpl(BackgroundType type);
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native void removeListenerImpl(CanvasBackgroundListener listener);
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native void setImpl(BackgroundType type, int customColor);
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native int solidColorImpl();
- com/tophatch/concepts/core/NativeCanvasBackgroundController.java: native BackgroundType typeImpl();
- com/tophatch/concepts/core/NativeFingerActionProperty.java: native FingerActionState getStateImpl();
- com/tophatch/concepts/core/NativeFingerActionProperty.java: native void setKindImpl(FingerActionKind kind);
- com/tophatch/concepts/core/NativeFingerActionProperty.java: native void setThicknessImpl(float thickness);
- com/tophatch/concepts/core/NativePDFRenderTask.java: native void dispose();
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void addListenerImpl(CanvasToolListener listener);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void createToolPreviewCanvasImpl();
- com/tophatch/concepts/core/NativeCanvasToolController.java: native Integer getActiveToolBarSlotImpl();
- com/tophatch/concepts/core/NativeCanvasToolController.java: native ToolType[] getAllToolsImpl();
- com/tophatch/concepts/core/NativeCanvasToolController.java: native int getToolBarSlotCountImpl();
- com/tophatch/concepts/core/NativeCanvasToolController.java: native ToolSlotState getToolBarSlotStateImpl(int index);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void removeListenerImpl(CanvasToolListener listener);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void replaceActiveSlotToolImpl(ToolType tool, UUID brushId);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void setActiveToolBarSlotImpl(int index);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void setToolColorImpl(int color);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void setToolOpacityImpl(float opacity, boolean undoable, Integer bookmarkIndex);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void setToolSmoothnessImpl(float smoothness, boolean undoable, Integer bookmarkIndex);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void setToolThicknessImpl(float thickness, boolean undoable, Integer bookmarkIndex);
- com/tophatch/concepts/core/NativeCanvasToolController.java: native void tearDownToolPreviewCanvasImpl();
- com/tophatch/concepts/core/NativeCanvasNudgeController.java: native void addListenerImpl(CanvasNudgeListener listener);
- com/tophatch/concepts/core/NativeCanvasNudgeController.java: native NudgeMenuState getMenuStateImpl();
- com/tophatch/concepts/core/NativeCanvasNudgeController.java: native NudgeState getStateImpl();
- com/tophatch/concepts/core/NativeCanvasNudgeController.java: native void removeListenerImpl(CanvasNudgeListener listener);
- com/tophatch/concepts/core/NativeCanvasNudgeController.java: native void setMenuStateImpl(NudgeMenuState state);
- com/tophatch/concepts/core/NativeAccountManager.java: native int getLoginType();
- com/tophatch/concepts/core/NativeAccountManager.java: native boolean hasValidLicense();
- com/tophatch/concepts/core/NativeAccountManager.java: native DBDocument privateProfile();
- com/tophatch/concepts/core/NativeAccountManager.java: native void refreshCredentials(String[] credentials);
- com/tophatch/concepts/core/NativeAccountManager.java: native boolean setProperty(String keyPath, Object value);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native void addListenerImpl(CanvasDocumentListener listener);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native boolean createNewImpl(String metadata, ByteBuffer resourceData);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native void finishAsyncWritesImpl();
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native DocumentInfo getInfoImpl();
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native boolean isLoadedImpl();
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native boolean loadImpl(File file);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native void removeListenerImpl(CanvasDocumentListener listener);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native boolean saveAsImpl(File file);
- com/tophatch/concepts/core/NativeCanvasDocumentController.java: native boolean saveImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void activateColorPickerImpl(ColorPickerTarget target);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void addListenerImpl(CanvasPickerListener listener);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native ColorPickerState getColorPickerStateImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native ItemPickerState getItemPickerStateImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native PickerMenuState getMenuStateImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native PickerMode getModeImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void loadToolPropertiesFromColorPickerImpl();
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void removeListenerImpl(CanvasPickerListener listener);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void setIgnoreAlphaImpl(boolean value);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void setIncludeAllLayersImpl(boolean value);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void setIncludeLockedItemsImpl(boolean value);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void setIncludePartialItemsImpl(boolean value);
- com/tophatch/concepts/core/NativeCanvasPickerController.java: native void toggleModeImpl();
- com/tophatch/concepts/core/NativeCanvasDebugController.java: native int countImpl();
- com/tophatch/concepts/core/NativeCanvasDebugController.java: native int indexImpl(String action);

## جرد المكتبات حسب ABI
- **armeabi-v7a** (90): libCore.so, libRSSupport.so, libabsl_bad_any_cast_impl.so, libabsl_bad_optional_access.so, libabsl_bad_variant_access.so, libabsl_base.so, libabsl_city.so, libabsl_civil_time.so, libabsl_cord.so, libabsl_cord_internal.so, libabsl_cordz_functions.so, libabsl_cordz_handle.so, libabsl_cordz_info.so, libabsl_cordz_sample_token.so, libabsl_debugging_internal.so, libabsl_demangle_internal.so, libabsl_examine_stack.so, libabsl_exponential_biased.so, libabsl_failure_signal_handler.so, libabsl_flags.so, libabsl_flags_commandlineflag.so, libabsl_flags_commandlineflag_internal.so, libabsl_flags_config.so, libabsl_flags_internal.so, libabsl_flags_marshalling.so, libabsl_flags_parse.so, libabsl_flags_private_handle_accessor.so, libabsl_flags_program_name.so, libabsl_flags_reflection.so, libabsl_flags_usage.so, libabsl_flags_usage_internal.so, libabsl_graphcycles_internal.so, libabsl_hash.so, libabsl_hashtablez_sampler.so, libabsl_int128.so, libabsl_leak_check.so, libabsl_leak_check_disable.so, libabsl_log_severity.so, libabsl_low_level_hash.so, libabsl_malloc_internal.so, libabsl_periodic_sampler.so, libabsl_random_distributions.so, libabsl_random_internal_distribution_test_util.so, libabsl_random_internal_platform.so, libabsl_random_internal_pool_urbg.so, libabsl_random_internal_randen.so, libabsl_random_internal_randen_hwaes.so, libabsl_random_internal_randen_hwaes_impl.so, libabsl_random_internal_randen_slow.so, libabsl_random_internal_seed_material.so, libabsl_random_seed_gen_exception.so, libabsl_random_seed_sequences.so, libabsl_raw_hash_set.so, libabsl_raw_logging_internal.so, libabsl_scoped_set_env.so, libabsl_spinlock_wait.so, libabsl_stacktrace.so, libabsl_status.so, libabsl_statusor.so, libabsl_str_format_internal.so, libabsl_strerror.so, libabsl_strings.so, libabsl_strings_internal.so, libabsl_symbolize.so, libabsl_synchronization.so, libabsl_throw_delegate.so, libabsl_time.so, libabsl_time_zone.so, libabsl_wyhash.so, libaddress_sorting.so, libbugsnag-ndk.so, libbugsnag-plugin-android-anr.so, libbugsnag-root-detection.so, libc++.cr.so, libc++_shared.so, libcblite.so, libchrome_zlib.cr.so, libcrypto.so, libgpr.so, libgrpc++.so, libgrpc.so, libicui18n.cr.so, libicuuc.cr.so, libpdfium.cr.so, libprotobuf.so, libre2.so, librsjni.so, librsjni_androidx.so, libssl.so, libupb.so

## حدود الاستنتاج
- هذا تقرير أسماء أصناف ومؤشرات API وجرد مكتبات؛ لا يعيد نشر الشيفرة المفكوكة أو الأصول.
- وجود اسم API أو صنف لا يثبت وحده أنه مستخدم في مسار التشغيل الفعلي.
- لم يُشغّل APK؛ يلزم تحليل JNI وربط الرموز الثنائية ثم اختبار التطبيق العربي على أجهزة فعلية.
