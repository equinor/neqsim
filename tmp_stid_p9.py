import fitz
d = fitz.open(r"task_solve/2026-06-22_jsv_inlet_separator_diameter/step1_scope_and_research/references/07316655.PDF")
t = d[8].get_text()
print("LEN", len(t))
print(t)
