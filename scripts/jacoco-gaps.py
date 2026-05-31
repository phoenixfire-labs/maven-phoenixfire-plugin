import csv
from pathlib import Path

rows = list(csv.DictReader(Path("phoenixfire-coverage/target/site/jacoco-aggregate/jacoco.csv").open()))
rows = [r for r in rows if int(r["BRANCH_MISSED"]) > 0 or int(r["LINE_MISSED"]) > 0]
rows.sort(key=lambda r: (-int(r["BRANCH_MISSED"]), -int(r["LINE_MISSED"])))
for r in rows:
    print(
        f"{r['CLASS']:40} BR_M={r['BRANCH_MISSED']:>2} BR_C={r['BRANCH_COVERED']:>3} "
        f"LN_M={r['LINE_MISSED']:>2} IN_M={r['INSTRUCTION_MISSED']:>3}"
    )
