#!/usr/bin/env python3
"""Create a concise, non-source report from JADX output and APK native libraries."""
from __future__ import annotations

import argparse
import hashlib
import re
import zipfile
from pathlib import Path

TARGETS = [
    "Engine", "NativeObject", "Canvas", "CanvasInputEventHandler",
    "InputEventPacker", "CanvasTransformController", "CanvasLayerController",
    "CanvasUndoController", "CanvasRendererController",
    "NativeCanvasRendererController", "NativeCanvasInputController",
    "NativeCanvasToolController", "NativeCanvasDocumentController",
    "CanvasFragment", "NativeKt", "EngineModule", "CanvasModule",
]
EVENT_TOKENS = [
    "getPressure(", "getAxisValue(", "AXIS_TILT", "AXIS_ORIENTATION",
    "ACTION_POINTER_DOWN", "ACTION_POINTER_UP", "MotionEvent",
    "createCanvas(", "loadLibrary(", "ReLinker.loadLibrary",
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--sources", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    java_files = list(args.sources.rglob("*.java"))
    class_hits: dict[str, list[str]] = {name: [] for name in TARGETS}
    token_files: dict[str, list[str]] = {token: [] for token in EVENT_TOKENS}
    native_methods: list[str] = []
    load_files: list[str] = []

    for path in java_files:
        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        rel = path.relative_to(args.sources).as_posix()
        for name in TARGETS:
            if re.search(r"\b(?:class|interface|enum)\s+" + re.escape(name) + r"\b", content):
                class_hits[name].append(rel)
        for token in EVENT_TOKENS:
            if token in content and len(token_files[token]) < 20:
                token_files[token].append(rel)
        if "loadLibrary(" in content or "ReLinker.loadLibrary" in content:
            load_files.append(rel)
        for match in re.findall(r"\bnative\s+[^;]+;", content):
            normalized = re.sub(r"\s+", " ", match).strip()[:180]
            native_methods.append(f"{rel}: {normalized}")

    with zipfile.ZipFile(args.apk) as archive:
        libs = sorted(
            n for n in archive.namelist()
            if n.startswith("lib/") and n.endswith(".so")
        )
        dex = sorted(
            n for n in archive.namelist()
            if re.fullmatch(r"classes(?:\d+)?\.dex", Path(n).name)
        )

    lines = [
        "# تقرير التحليل الثابت المتعمق — Concepts 2022.04.10",
        "",
        f"- SHA-256 للعينة: `{digest}`",
        f"- ملفات Java الناتجة من JADX: {len(java_files)}",
        f"- ملفات DEX داخل APK: {', '.join(dex) if dex else 'لم تُكتشف'}",
        f"- مكتبات native: {len(libs)}",
        "",
        "## أصناف المحرك والإدخال",
    ]
    for name, paths in class_hits.items():
        if paths:
            lines.append(f"- `{name}`: " + "; ".join(paths[:3]))
    lines += ["", "## مؤشرات أحداث اللمس والقلم"]
    for token, paths in token_files.items():
        if paths:
            lines.append(f"- `{token}`: " + "; ".join(paths[:5]))
    lines += ["", "## مواضع تحميل المكتبات"]
    lines += [f"- {p}" for p in load_files[:30]] or [
        "- لم يظهر استدعاء تحميل في ملفات Java المفكوكة."
    ]
    lines += ["", "## تصريحات native التي أمكن قراءتها"]
    lines += [f"- {m}" for m in native_methods[:60]] or [
        "- لم تُستخرج تصريحات native قابلة للقراءة."
    ]
    lines += ["", "## جرد المكتبات حسب ABI"]
    by_abi: dict[str, list[str]] = {}
    for lib in libs:
        parts = lib.split("/")
        abi = parts[1] if len(parts) > 2 else "غير معروف"
        by_abi.setdefault(abi, []).append(parts[-1])
    for abi, names in sorted(by_abi.items()):
        lines.append(f"- **{abi}** ({len(names)}): " + ", ".join(names))
    lines += [
        "",
        "## حدود الاستنتاج",
        "- هذا تقرير أسماء أصناف ومؤشرات API وجرد مكتبات؛ لا يعيد نشر الشيفرة المفكوكة أو الأصول.",
        "- وجود اسم API أو صنف لا يثبت وحده أنه مستخدم في مسار التشغيل الفعلي.",
        "- لم يُشغّل APK؛ يلزم تحليل JNI وربط الرموز الثنائية ثم اختبار التطبيق العربي على أجهزة فعلية.",
        "",
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
