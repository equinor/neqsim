import re

# Read refs.bib
with open('books/the_cubic_plus_association_equation_of_state_2026/refs.bib', 'r', encoding='utf-8') as f:
    bib = f.read()

# Extract all bib keys
bib_keys = set(re.findall(r'@\w+\{(\w+)', bib))
print(f'Total bib entries: {len(bib_keys)}')

# Read all chapters and find cited keys
import glob
cited_keys = set()
for chapter_file in glob.glob('books/the_cubic_plus_association_equation_of_state_2026/chapters/*/chapter.md'):
    with open(chapter_file, 'r', encoding='utf-8') as f:
        text = f.read()
    cites = re.findall(r'\\cite\{([^}]+)\}', text)
    for cite_group in cites:
        for key in cite_group.split(','):
            cited_keys.add(key.strip())

print(f'Cited keys: {len(cited_keys)}')

uncited = sorted(bib_keys - cited_keys)
print(f'\nUncited bib entries ({len(uncited)}):')
for k in uncited:
    print(f'  {k}')
