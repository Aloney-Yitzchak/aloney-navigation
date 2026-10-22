# -*- coding: utf-8 -*-
"""Create labels, milestones and issues for the project. Safe to re-run: existing items are skipped.

Usage:  python3 setup_github.py OWNER/REPO
Needs:  the GitHub CLI (gh), logged in with access to the repo.
"""
import json, subprocess, sys
from tasks import MILESTONES, PHASE_LABEL, LABELS, TASKS

REPO = sys.argv[1]

def api(method, path, data=None):
    cmd = ["gh", "api", "-X", method, f"repos/{REPO}/{path}", "-H", "Accept: application/vnd.github+json"]
    if data is not None:
        cmd += ["--input", "-"]
    r = subprocess.run(cmd, input=json.dumps(data) if data is not None else None,
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{method} {path}: {r.stdout} {r.stderr}")
    return json.loads(r.stdout) if r.stdout.strip() else None

def paged(path):
    out, page = [], 1
    while True:
        sep = "&" if "?" in path else "?"
        batch = api("GET", f"{path}{sep}per_page=100&page={page}")
        out += batch
        if len(batch) < 100:
            return out
        page += 1

# labels
have = {l["name"] for l in paged("labels")}
for name, color in LABELS:
    if name not in have:
        api("POST", "labels", {"name": name, "color": color})
print("labels ok")

# milestones
ms = {m["title"]: m["number"] for m in paged("milestones?state=all")}
key_to_number = {}
for title, due, desc in MILESTONES:
    if title not in ms:
        m = api("POST", "milestones", {"title": title, "due_on": f"{due}T23:59:00Z", "description": desc})
        ms[title] = m["number"]
    key_to_number[title.split(" ")[0]] = ms[title]
print("milestones ok")

# issues
existing = {i["title"].split(":")[0]: i for i in paged("issues?state=all") if "pull_request" not in i}
for tid, title, mkey, typ, fr, prio, hours, what, done, page, state in TASKS:
    if tid in existing:
        continue
    checks = "\n".join(f"- [{'x' if state == 'closed' else ' '}] {d}" for d in done)
    req = fr or "—"
    # Full link: in an issue a relative "../../wiki/…" drops the repo name (github.com/OWNER/wiki/…).
    body = (f'<div dir="rtl">\n\n## מה צריך לעשות\n\n{what}\n\n'
            f'## תנאי סיום\n\n{checks}\n\n'
            f'| דרישה | הערכה | פירוט |\n| --- | --- | --- |\n'
            f'| {req} | {hours} שעות | [Wiki — {page.replace("-", " ")}](https://github.com/{REPO}/wiki/{page}) |\n\n</div>\n')
    labels = [PHASE_LABEL[mkey], f"type: {typ}", f"priority: {prio}"] + ([fr] if fr else [])
    issue = api("POST", "issues", {"title": f"{tid}: {title}", "body": body, "labels": labels,
                                   "milestone": key_to_number[mkey]})
    # finished tasks stay open here: they are closed by the Pull Request that delivers them
    print("issue", issue["number"], tid)
print("done")
