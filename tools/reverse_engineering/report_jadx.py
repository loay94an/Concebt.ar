#!/usr/bin/env python3
"""Create a sanitized evidence index from JADX output; never publish decompiled code."""
import argparse, hashlib, re
from pathlib import Path
TARGETS = {
 "محرك الرسم واللوحة": [r"\b(class|interface)\s+(Engine|Canvas|CanvasFragment|NativeCanvasRendererController)\b", r"\bcreateCanvas\b", r"\binitialize\s*\("],
 "إدخال القلم واللمس": [r"CanvasInputEventHandler", r"InputEventPacker", r"MotionEvent", r"PointerEvent", r"Stylus", r"pressure", r"tilt"],
 "الطبقات": [r"Layer", r"layer"],
 "الفرش والأدوات": [r"Brush", r"brush", r"Pencil", r"Marker", r"Eraser"],
 "الاستيراد والتصدير": [r"Import", r"Export", r"PDF", r"PSD", r"SVG", r"DXF"],
}
def digest(path):
 h=hashlib.sha256()
 with open(path,"rb") as f:
  for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
 return h.hexdigest()
def main():
 p=argparse.ArgumentParser()
 for arg in ("apk","decompiled","jadx-log","output"): p.add_argument("--"+arg,required=True)
 a=p.parse_args(); apk=Path(a.apk); root=Path(a.decompiled); log=Path(a.jadx_log)
 java=list(root.rglob("*.java")) if root.exists() else []
 allfiles=[x for x in root.rglob("*") if x.is_file()] if root.exists() else []
 logtext=log.read_text(errors="replace") if log.exists() else ""
 errors=[x.strip() for x in logtext.splitlines() if re.search(r"error|exception|failed",x,re.I)]
 out=["# تقرير JADX الآلي — تحليل ثابت","","> ناتج JADX CLI في GitHub Actions. لا يثبت سلوك وقت التشغيل ولا يعني تنفيذ REA.","","## بصمة العينة",f"- الملف: {apk.name}",f"- الحجم: {apk.stat().st_size:,} bytes",f"- SHA-256: {digest(apk)}","","## الإحصاءات",f"- ملفات Java: {len(java):,}",f"- إجمالي الملفات الناتجة: {len(allfiles):,}",f"- أسطر الأخطاء/الاستثناءات: {len(errors):,}"]
 for title,patterns in TARGETS.items():
  found=[]; regs=[re.compile(x,re.I) for x in patterns]
  for f in java:
   try: data=f.read_text(errors="replace")
   except OSError: continue
   hits=[r.pattern for r in regs if r.search(data)]
   if hits: found.append((f.relative_to(root).as_posix(),hits))
  out += ["",f"## {title}",f"- الملفات ذات الصلة: {len(found)}"]
  out += [f"- {rel} — مؤشرات: {', '.join(hits[:8])}" for rel,hits in found[:100]]
  if len(found)>100: out.append(f"- اختصار القائمة: {len(found)-100} ملف إضافي.")
  if not found: out.append("- لا توجد مطابقات نصية؛ هذا لا يثبت غياب الميزة.")
 out += ["","## حدود الإثبات","- فهرسة نصية آلية؛ قد تنتج مطابقات غير دلالية أو تفوّت أسماء مشفرة.","- لم تُنشر الشيفرة المفكوكة أو الموارد أو الأصول أو المكتبات الثنائية.","- لا يوجد تحليل ديناميكي في هذا المسار.","- لم يُشغّل REA؛ النتائج تخص JADX CLI فقط.","","## سجل الأخطاء"]
 out += [f"- {x[:240]}" for x in errors[:30]] if errors else ["- لم يرصد السكربت أسطر خطأ صريحة."]
 if len(errors)>30: out.append(f"- اختصار {len(errors)-30} سطراً إضافياً.")
 dest=Path(a.output); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text("\n".join(out)+"\n",encoding="utf-8")
if __name__=="__main__": main()
