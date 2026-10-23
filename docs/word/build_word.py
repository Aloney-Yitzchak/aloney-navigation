#!/usr/bin/env python3
"""Builds the Word copies of the project documents in docs/word from the wiki pages.

    python docs/word/build_word.py                    # all documents
    python docs/word/build_word.py "מסמך-תיכון-SDD"   # one document

Needs pandoc (https://pandoc.org). Each existing .docx is used as its own style
template (--reference-doc), so fonts, header ("[שם התלמיד]") and page numbers
stay as they are. After pandoc, every table is set right-to-left as well.
"""

import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WIKI = ROOT / "wiki"
OUT = ROOT / "docs" / "word"

# wiki page -> Word file
DOCUMENTS = {
    "תיק-פרויקט": "תיק פרויקט - ניווט אלוני יצחק.docx",
    "מסמך-דרישות-SRS": "מסמך דרישות SRS - ניווט אלוני יצחק.docx",
    "מסמך-תיכון-SDD": "מסמך תיכון SDD - ניווט אלוני יצחק.docx",
    "תוכנית-ניהול-פרויקט": "תוכנית ניהול פרויקט - ניווט אלוני יצחק.docx",
    "מדריך-עיצוב-מסכים": "מדריך עיצוב מסכים - ניווט אלוני יצחק.docx",
}


def wiki_to_pandoc_markdown(text):
    """GitHub wiki markdown -> pandoc markdown."""
    text = re.sub(r"<details>.*?</details>\s*", "", text, flags=re.S)        # Mermaid source copy
    text = re.sub(r"^\s*</?div[^>]*>\s*$", "", text, flags=re.M)              # dir wrappers
    text = re.sub(r"```math\n(.*?)\n```", lambda m: "$$\n" + m.group(1) + "\n$$", text, flags=re.S)
    text = re.sub(r"^<sub>(.*)</sub>\s*$", r"*\1*", text, flags=re.M)        # captions
    text = re.sub(r"\]\((?!https?:|#|images/)([^)\s]+)\)", r"](https://github.com/Aloney-Yitzchak/aloney-navigation/wiki/\1)", text)
    return text


BORDERS = ("<w:tblBorders>" + "".join(
    f'<w:{side} w:val="single" w:sz="4" w:color="BFBFBF"/>'
    for side in ("top", "left", "bottom", "right", "insideH", "insideV")) + "</w:tblBorders>")


def make_tables_rtl(docx):
    """Every table runs right to left (<w:bidiVisual/>) and gets light grey borders."""
    tmp = docx.with_suffix(".tmp")
    with zipfile.ZipFile(docx) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "word/document.xml":
                xml = data.decode("utf-8")
                xml = re.sub(r"<w:tblPr>(?!<w:bidiVisual/>)", "<w:tblPr><w:bidiVisual/>", xml)
                xml = re.sub(r"(<w:tblPr>(?:(?!</w:tblPr>).)*?)</w:tblPr>",
                             lambda m: m.group(0) if "<w:tblBorders>" in m.group(1) else m.group(1) + BORDERS + "</w:tblPr>",
                             xml, flags=re.S)
                data = xml.encode("utf-8")
            zout.writestr(item, data)
    tmp.replace(docx)


def build(page, filename):
    source = (WIKI / f"{page}.md").read_text(encoding="utf-8")
    target = OUT / filename
    template = target if target.exists() else None
    with tempfile.TemporaryDirectory() as tmp:
        md = Path(tmp) / "doc.md"
        md.write_text(wiki_to_pandoc_markdown(source), encoding="utf-8")
        new = Path(tmp) / "out.docx"
        cmd = ["pandoc", str(md), "-f", "markdown+pipe_tables+tex_math_dollars-auto_identifiers",
               "-t", "docx", "-o", str(new), "--resource-path", str(WIKI),
               "-M", "lang=he-IL", "-M", "dir=rtl"]
        if template:
            cmd += ["--reference-doc", str(template)]
        subprocess.run(cmd, check=True, stderr=subprocess.DEVNULL)  # pandoc warns about missing Hebrew captions
        make_tables_rtl(new)
        target.write_bytes(new.read_bytes())
    print(f"{filename}: {target.stat().st_size // 1024} KB")


def main(argv):
    pages = argv or list(DOCUMENTS)
    for page in pages:
        build(page, DOCUMENTS[page])


if __name__ == "__main__":
    main(sys.argv[1:])
