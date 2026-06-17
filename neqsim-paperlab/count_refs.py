import re

html = open('books/the_cubic_plus_association_equation_of_state_2026/submission/book.html', 'r', encoding='utf-8').read()

refs = re.findall(r'<li id="ref-(\d+)"', html)
print(f'Total references in bibliography: {len(refs)}')
if refs:
    print(f'Last ref number: {refs[-1]}')

cites = re.findall(r'class="citation"', html)
print(f'Citation markers in text: {len(cites)}')

# Also try finding cite spans with numbers
cite_nums = re.findall(r'<a href="#ref-(\d+)"', html)
unique_cited = sorted(set(int(n) for n in cite_nums))
print(f'Unique references cited in text: {len(unique_cited)}')

# Check for unresolved \cite tags
unresolved = re.findall(r'\\cite\{([^}]+)\}', html)
print(f'Unresolved \\cite tags: {len(unresolved)}')
if unresolved:
    for u in unresolved[:20]:
        print(f'  - {u}')
