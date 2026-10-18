"""Export check counts and locations without source snippets, response bodies or exception text."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(root):
    suites = []
    for pattern in ("test-results/**/TEST-*.xml", "outputs/androidTest-results/**/TEST-*.xml"):
        for path in sorted(root.glob(pattern)):
            suite = ET.parse(path).getroot()
            suites.append(dict(report=path.relative_to(root).as_posix(),
                               **{key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}))
    lint = []
    for path in sorted(root.glob("reports/lint-results-*.xml")):
        for issue in ET.parse(path).getroot().findall("issue"):
            locations = []
            for loc in issue.findall("location"):
                file = loc.get("file", "").replace("\\", "/")
                marker = "/app/src/"
                if marker in file:
                    file = "app/src/" + file.split(marker, 1)[1]
                locations.append(dict(file=file, line=loc.get("line")))
            lint.append(dict(id=issue.get("id"), severity=issue.get("severity"), locations=locations))
    return dict(testReportsAvailable=bool(suites), suites=suites,
                totals={key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")},
                lintReportsAvailable=bool(list(root.glob("reports/lint-results-*.xml"))), lint=lint)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = summarize(args.root.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("testReportsAvailable", "totals", "lintReportsAvailable")}))


if __name__ == "__main__":
    main()
