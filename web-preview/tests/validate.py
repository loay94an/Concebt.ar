from html.parser import HTMLParser
from pathlib import Path
import subprocess, tempfile
source = Path("web-preview/index.html").read_text(encoding="utf-8")
class Check(HTMLParser):
    def __init__(self):
        super().__init__(); self.ids=set(); self.duplicates=[]; self.scripts=[]; self.in_script=False; self.current=[]
    def handle_starttag(self, tag, attrs):
        attrs=dict(attrs)
        if "id" in attrs:
            if attrs["id"] in self.ids: self.duplicates.append(attrs["id"])
            self.ids.add(attrs["id"])
        if tag=="script": self.in_script=True; self.current=[]
    def handle_data(self, data):
        if self.in_script: self.current.append(data)
    def handle_endtag(self, tag):
        if tag=="script" and self.in_script: self.scripts.append("".join(self.current)); self.in_script=False
p=Check(); p.feed(source)
if p.duplicates: raise SystemExit("Duplicate ids: "+", ".join(p.duplicates))
if not p.scripts: raise SystemExit("No inline scripts")
with tempfile.TemporaryDirectory() as d:
    for i,s in enumerate(p.scripts):
        f=Path(d)/f"inline-{i}.js"; f.write_text(s,encoding="utf-8")
        subprocess.run(["node","--check",str(f)],check=True)
print(f"PASS: HTML parsed, {len(p.ids)} unique ids, {len(p.scripts)} inline scripts syntax-valid.")
