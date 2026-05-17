"""Survey all figures in the book — print chapter + figure title + output filename."""
import json
import glob
import re
import sys

nbs = sorted(glob.glob(r'books\Industrial Agentic Engineering with NeqSim_2026\chapters\*\notebooks\*_figures.ipynb'))
for nb_path in nbs:
    nb = json.load(open(nb_path, encoding='utf-8'))
    chapter = nb_path.split('\\')[-3]
    print(f'\n=== {chapter} ===')
    pending_title = None
    for c in nb['cells']:
        src = ''.join(c['source'])
        m = re.search(r'## (Figure \d+) [—-] (.+)', src)
        if m:
            pending_title = (m.group(1), m.group(2).strip().split('\n')[0])
        m2 = re.search(r"save\(fig, '([^']+)'\)", src)
        if m2 and pending_title:
            print(f'  {pending_title[0]}: {pending_title[1]}  ->  {m2.group(1)}')
            pending_title = None
