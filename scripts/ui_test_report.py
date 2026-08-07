#!/usr/bin/env python3
"""Render one self-contained summary for the launcher validation run."""

from __future__ import annotations

import argparse
import glob
import html
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def junit_summary(pattern: str) -> tuple[dict[str, int], list[str]]:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    failed_cases: list[str] = []
    for filename in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(filename).getroot()
        except (ET.ParseError, OSError):
            continue
        suites = [root] if root.tag == "testsuite" else list(root.findall(".//testsuite"))
        for suite in suites:
            for key in totals:
                totals[key] += int(suite.attrib.get(key, "0"))
            for case in suite.findall("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failed_cases.append(f"{case.attrib.get('classname', '')}.{case.attrib.get('name', '')}")
    return totals, failed_cases


def lint_summary(filename: Path) -> dict[str, int]:
    totals = {"Fatal": 0, "Error": 0, "Warning": 0, "Information": 0}
    try:
        root = ET.parse(filename).getroot()
    except (ET.ParseError, OSError):
        return totals
    for issue in root.findall("issue"):
        severity = issue.attrib.get("severity", "Information")
        totals[severity] = totals.get(severity, 0) + 1
    return totals


def relative_link(output: Path, target: Path) -> str:
    return Path(os.path.relpath(target, output.parent)).as_posix()


def status_card(label: str, passed: bool, detail: str) -> str:
    state = "PASS" if passed else "FAIL"
    css = "pass" if passed else "fail"
    return (
        f'<section class="card"><div class="status {css}">{state}</div>'
        f'<h2>{html.escape(label)}</h2><p>{html.escape(detail)}</p></section>'
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--build-exit", type=int, required=True)
    parser.add_argument("--ui-exit", type=int, required=True)
    parser.add_argument("--duration", type=int, required=True)
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    unit, unit_failures = junit_summary(str(root / "app/build/test-results/testDebugUnitTest/*.xml"))
    ui, ui_failures = junit_summary(str(root / "app/build/outputs/androidTest-results/connected/**/*.xml"))
    lint = lint_summary(root / "app/build/reports/lint-results-debug.xml")

    build_passed = args.build_exit == 0 and unit["failures"] == 0 and unit["errors"] == 0
    ui_passed = args.ui_exit == 0 and ui["tests"] > 0 and ui["failures"] == 0 and ui["errors"] == 0
    lint_passed = lint["Fatal"] == 0 and lint["Error"] == 0
    overall = build_passed and ui_passed and lint_passed

    report_links = []
    for label, target in (
        ("Instrumented UI details", root / "app/build/reports/androidTests/connected/debug/index.html"),
        ("JVM unit-test details", root / "app/build/reports/tests/testDebugUnitTest/index.html"),
        ("Android lint details", root / "app/build/reports/lint-results-debug.html"),
    ):
        if target.exists():
            report_links.append(f'<li><a href="{relative_link(output, target)}">{html.escape(label)}</a></li>')

    artifact_directory = output.parent / "artifacts"
    screenshots = sorted(artifact_directory.glob("*.png")) if artifact_directory.exists() else []
    final_screen = output.parent / "final-screen.png"
    if final_screen.exists():
        screenshots.append(final_screen)
    screenshot_html = "".join(
        f'<a class="shot" href="{relative_link(output, shot)}">'
        f'<img src="{relative_link(output, shot)}" alt="{html.escape(shot.stem)}">'
        f'<span>{html.escape(shot.stem)}</span></a>'
        for shot in screenshots
    ) or "<p>No screenshots were collected.</p>"

    hierarchies = sorted(artifact_directory.glob("*.xml")) if artifact_directory.exists() else []
    final_hierarchy = output.parent / "final-hierarchy.xml"
    if final_hierarchy.exists():
        hierarchies.append(final_hierarchy)
    hierarchy_html = "".join(
        f'<li><a href="{relative_link(output, hierarchy)}">{html.escape(hierarchy.stem)}</a></li>'
        for hierarchy in hierarchies
    ) or "<li>Unavailable</li>"

    failures = unit_failures + ui_failures
    failure_html = "" if not failures else (
        "<section><h2>Failures</h2><ul>" +
        "".join(f"<li>{html.escape(case)}</li>" for case in failures) +
        "</ul></section>"
    )

    diagnostics = []
    for file in sorted(output.parent.glob("*.txt")) + sorted(output.parent.glob("*.log")):
        diagnostics.append(f'<li><a href="{relative_link(output, file)}">{html.escape(file.name)}</a></li>')

    document = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Minimal Launcher UI validation</title>
<style>
:root{{--bg:#101416;--panel:#181e21;--text:#f4f4f2;--muted:#9ba3a6;--accent:#b7f36b;--bad:#ff7b72}}
*{{box-sizing:border-box}} body{{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,sans-serif}}
main{{max-width:1100px;margin:auto;padding:36px 22px 70px}} h1{{font-size:30px;margin:0 0 6px}} h2{{font-size:17px}}
.meta{{color:var(--muted);margin-bottom:28px}} .overall{{color:{'var(--accent)' if overall else 'var(--bad)'};font-weight:700}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px}} .card{{background:var(--panel);padding:18px;border-radius:10px}}
.card h2{{margin:8px 0 4px}} .card p{{color:var(--muted);margin:0}} .status{{font-weight:800;letter-spacing:.12em}} .pass{{color:var(--accent)}} .fail{{color:var(--bad)}}
a{{color:var(--accent)}} section{{margin-top:30px}} .shots{{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}}
.shot{{background:var(--panel);border-radius:10px;overflow:hidden;text-decoration:none}} .shot img{{width:100%;height:260px;object-fit:contain;background:#080a0b;display:block}}
.shot span{{display:block;padding:9px 12px;overflow-wrap:anywhere}} code{{color:var(--accent)}}
</style></head><body><main>
<h1>Minimal Launcher UI validation</h1>
<p class="meta"><span class="overall">{'PASS' if overall else 'FAIL'}</span> · device {html.escape(args.serial)} · AVD {html.escape(args.avd)} · {args.duration}s</p>
<div class="grid">
{status_card('Build and JVM tests', build_passed, f"{unit['tests']} tests, {unit['failures'] + unit['errors']} failed")}
{status_card('Instrumented UI', ui_passed, f"{ui['tests']} tests, {ui['failures'] + ui['errors']} failed, {ui['skipped']} skipped")}
{status_card('Android lint', lint_passed, f"{lint['Error']} errors, {lint['Warning']} warnings")}
</div>
{failure_html}
<section><h2>Detailed reports</h2><ul>{''.join(report_links) or '<li>Unavailable</li>'}</ul></section>
<section><h2>UI screenshots</h2><div class="shots">{screenshot_html}</div></section>
<section><h2>UI hierarchies</h2><ul>{hierarchy_html}</ul></section>
<section><h2>Diagnostics</h2><ul>{''.join(diagnostics) or '<li>Unavailable</li>'}</ul></section>
</main></body></html>"""
    output.write_text(document, encoding="utf-8")


if __name__ == "__main__":
    main()
