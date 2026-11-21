#!/usr/bin/env python3
"""Inspect native ELF metadata and exported JNI symbols without publishing binaries."""
from __future__ import annotations

import argparse
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path

KEYWORDS = re.compile(
    r"jni|canvas|brush|stroke|render|pressure|tilt|layer|undo|document|"
    r"texture|bitmap|pdf|engine|input|gesture|tool",
    re.IGNORECASE,
)


def run(*args: str) -> str:
    try:
        return subprocess.run(args, check=False, capture_output=True, text=True).stdout
    except OSError:
        return ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    with zipfile.ZipFile(args.apk) as apk, tempfile.TemporaryDirectory() as tmp:
        names = [n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")]
        selected = [n for n in names if Path(n).name in {
            "libCore.so", "librsjni.so", "librsjni_androidx.so", "libpdfium.cr.so"
        }]
        lines = [
            "",
            "## تحليل ELF وJNI للمكتبات الأصلية",
            f"- عدد المكتبات الثنائية داخل APK: {len(names)}",
            "- جرى فحص بيانات ELF والرموز العامة والسلاسل النصية ذات الصلة؛ لم تُنشر أي مكتبة ثنائية.",
        ]
        abis = sorted({n.split("/")[1] for n in names if len(n.split("/")) > 2})
        lines.append(f"- ABI الموجودة: {', '.join(abis) if abis else 'غير محدد'}")
        if not selected:
            selected = names[:10]
        for index, name in enumerate(selected):
            path = Path(tmp) / f"native-{index}.so"
            path.write_bytes(apk.read(name))
            header = run("readelf", "-h", str(path))
            machine = re.search(r"Machine:\s*(.+)", header)
            elf_class = re.search(r"Class:\s*(.+)", header)
            lines.append("")
            lines.append(f"### {name}")
            if elf_class:
                lines.append(f"- ELF class: {elf_class.group(1).strip()}")
            if machine:
                lines.append(f"- Architecture: {machine.group(1).strip()}")
            symbols = run("readelf", "-Ws", str(path))
            exported = []
            for line in symbols.splitlines():
                if re.search(r"GLOBAL\s+DEFAULT\s+\d+", line):
                    symbol = line.split()[-1]
                    if symbol and (symbol.startswith("Java_") or "JNI_OnLoad" in symbol
                                   or "RegisterNatives" in symbol):
                        exported.append(symbol)
            lines.append("- Exported JNI-related symbols:")
            lines.extend(f"  - `{s}`" for s in exported[:100]) if exported else lines.append("  - No directly exported Java_* symbols found; JNI may use RegisterNatives.")
            strings = run("strings", "-n", "5", str(path))
            hits = []
            for value in strings.splitlines():
                value = value.strip()
                if KEYWORDS.search(value) and len(value) <= 180 and value not in hits:
                    hits.append(value)
            lines.append("- Relevant native strings (indicators only):")
            lines.extend(f"  - {value}" for value in hits[:80]) if hits else lines.append("  - No matching strings extracted.")
        lines += [
            "",
            "### قيود التحليل",
            "- غياب أسماء Java_* لا ينفي وجود JNI؛ قد يسجل المحرك الدوال ديناميكياً عبر RegisterNatives.",
            "- السلاسل النصية وأسماء الرموز قرائن وليست إثباتاً لسلوك الدوال.",
            "- العينة تحتوي مكتبات armeabi-v7a فقط وفق جرد APK؛ لا تتوفر مكتبات arm64-v8a ضمن هذه الحزمة.",
            "- إعادة استخدام libCore.so في تطبيق مستقل تتطلب توافق ABI وواجهة JNI والاعتمادات والترخيص؛ لم تُدمج المكتبة في التطبيق العربي.",
            "",
        ]
        report = args.output.read_text(encoding="utf-8")
        marker = "## تحليل ELF وJNI للمكتبات الأصلية"
        if marker in report:
            report = report[:report.index(marker)].rstrip()
        args.output.write_text(report + "\n" + "\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
