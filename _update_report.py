"""Temporary script to update CNG task report generator and regenerate reports."""
import sys
sys.path.insert(0, "devtools")
from new_task import GENERATE_REPORT

# Write updated template
report = GENERATE_REPORT.replace(
    'TITLE = "Task Report"',
    'TITLE = "CNG tank filling and emptying temperature estimation"'
)

target = (
    "task_solve/2026-03-07_cng_tank_filling_and_emptying_temperature_estimation"
    "/step3_report/generate_report.py"
)
with open(target, "w", encoding="utf-8") as f:
    f.write(report)

print("Updated generate_report.py ({} chars)".format(len(report)))

# Also update template
template = (
    "task_solve/TASK_TEMPLATE/step3_report/generate_report.py"
)
with open(template, "w", encoding="utf-8") as f:
    f.write(GENERATE_REPORT)

print("Updated TASK_TEMPLATE ({} chars)".format(len(GENERATE_REPORT)))
