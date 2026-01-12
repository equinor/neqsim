#!/usr/bin/env python3
"""
NeqSim Jupyter Notebook to Markdown Converter

This script converts Jupyter notebooks (.ipynb) in the docs/examples folder
to Markdown files for proper rendering on GitHub Pages.

Usage:
    python convert_notebooks.py

Requirements:
    pip install nbconvert nbformat
"""

import os
import json
import re
from pathlib import Path
from datetime import datetime


def escape_liquid_tags(content):
    """
    Escape Liquid template tags that would cause Jekyll errors.
    
    Only escape {{ }} when they look like Liquid variable interpolation,
    NOT when they're part of LaTeX equations (which typically use single braces).
    """
    # Only escape {{ ... }} patterns that look like Liquid variables
    # These typically have spaces around them and contain variable names
    # LaTeX uses single braces like \frac{a}{b}, not double braces
    
    # Pattern: {{ followed by word characters (possibly with dots/brackets), then }}
    # This matches {{ variable }} but not nested braces in LaTeX
    def escape_liquid_var(match):
        inner = match.group(1)
        # If it looks like a Liquid variable (word chars, dots, brackets, pipes)
        if re.match(r'^[\s\w\.\[\]\|\'":\-]+$', inner):
            return '{% raw %}{{' + inner + '}}{% endraw %}'
        return match.group(0)
    
    content = re.sub(r'\{\{([^{}]*)\}\}', escape_liquid_var, content)
    return content


def notebook_to_markdown(notebook_path):
    """
    Convert a Jupyter notebook to Markdown format suitable for Jekyll.
    
    Args:
        notebook_path: Path to the .ipynb file
        
    Returns:
        Markdown string with Jekyll front matter
    """
    with open(notebook_path, 'r', encoding='utf-8') as f:
        nb = json.load(f)
    
    notebook_name = Path(notebook_path).stem
    title = notebook_name.replace('_', ' ').replace('-', ' ')
    
    # Jekyll front matter
    front_matter = f"""---
layout: default
title: "{title}"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# {title}

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`{notebook_name}.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/{notebook_name}.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/{notebook_name}.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/{notebook_name}.ipynb).

---

"""
    
    markdown_content = []
    
    for cell in nb.get('cells', []):
        cell_type = cell.get('cell_type', '')
        source = ''.join(cell.get('source', []))
        
        if cell_type == 'markdown':
            # Add markdown content directly
            markdown_content.append(source)
            markdown_content.append('\n\n')
            
        elif cell_type == 'code':
            # Determine language from notebook metadata
            language = nb.get('metadata', {}).get('language_info', {}).get('name', 'python')
            
            # Add code block
            markdown_content.append(f'```{language}\n')
            markdown_content.append(source)
            if not source.endswith('\n'):
                markdown_content.append('\n')
            markdown_content.append('```\n\n')
            
            # Add outputs if present
            outputs = cell.get('outputs', [])
            if outputs:
                has_output = False
                output_text = []
                
                for output in outputs:
                    output_type = output.get('output_type', '')
                    
                    if output_type == 'stream':
                        text = ''.join(output.get('text', []))
                        if text.strip():
                            output_text.append(text)
                            has_output = True
                            
                    elif output_type == 'execute_result':
                        data = output.get('data', {})
                        if 'text/plain' in data:
                            text = ''.join(data['text/plain'])
                            if text.strip():
                                output_text.append(text)
                                has_output = True
                                
                    elif output_type == 'error':
                        # Include error traceback
                        traceback = output.get('traceback', [])
                        if traceback:
                            # Strip ANSI codes
                            error_text = '\n'.join(traceback)
                            error_text = re.sub(r'\x1b\[[0-9;]*m', '', error_text)
                            output_text.append(f"Error: {error_text}")
                            has_output = True
                
                if has_output:
                    markdown_content.append('<details>\n<summary>Output</summary>\n\n')
                    markdown_content.append('```\n')
                    markdown_content.append('\n'.join(output_text))
                    if not output_text[-1].endswith('\n'):
                        markdown_content.append('\n')
                    markdown_content.append('```\n\n')
                    markdown_content.append('</details>\n\n')
    
    full_content = front_matter + ''.join(markdown_content)
    
    # Escape Liquid tags
    full_content = escape_liquid_tags(full_content)
    
    return full_content


def convert_all_notebooks(examples_dir):
    """
    Convert all notebooks in the examples directory to Markdown.
    
    Args:
        examples_dir: Path to the docs/examples directory
    """
    examples_path = Path(examples_dir)
    
    if not examples_path.exists():
        print(f"Error: Directory not found: {examples_dir}")
        return
    
    notebooks = list(examples_path.glob('*.ipynb'))
    
    if not notebooks:
        print("No notebooks found in examples directory")
        return
    
    print(f"Found {len(notebooks)} notebooks to convert:")
    
    for nb_path in notebooks:
        md_filename = nb_path.stem + '.md'
        md_path = examples_path / md_filename
        
        print(f"  Converting: {nb_path.name} -> {md_filename}")
        
        try:
            markdown = notebook_to_markdown(nb_path)
            with open(md_path, 'w', encoding='utf-8') as f:
                f.write(markdown)
            print(f"    ✓ Successfully created {md_filename}")
        except Exception as e:
            print(f"    ✗ Error converting {nb_path.name}: {e}")
    
    print("\nDone!")


def create_examples_index(examples_dir):
    """
    Create an index.md file listing all notebooks.
    
    Args:
        examples_dir: Path to the docs/examples directory
    """
    examples_path = Path(examples_dir)
    notebooks = sorted(examples_path.glob('*.ipynb'))
    java_files = sorted(examples_path.glob('*.java'))
    md_files = sorted([f for f in examples_path.glob('*.md') 
                       if f.name not in ['index.md', 'README.md'] 
                       and not any(nb.stem == f.stem for nb in notebooks)])
    
    content = """---
layout: default
title: "Examples"
description: "NeqSim code examples and tutorials"
nav_order: 5
has_children: true
---

# NeqSim Examples

This section contains tutorials, code examples, and Jupyter notebooks demonstrating NeqSim capabilities.

## Jupyter Notebook Tutorials

Interactive Python notebooks using NeqSim through [neqsim-python](https://github.com/equinor/neqsim-python):

| Notebook | Description | View Options |
|----------|-------------|--------------|
"""
    
    for nb in notebooks:
        name = nb.stem
        title = name.replace('_', ' ').replace('-', ' ')
        
        # Create links
        md_link = f"[Markdown]({name}.md)"
        nbviewer_link = f"[nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/{name}.ipynb)"
        colab_link = f"[Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/{name}.ipynb)"
        github_link = f"[GitHub](https://github.com/equinor/neqsim/blob/master/docs/examples/{name}.ipynb)"
        
        content += f"| **{title}** | See notebook for details | {md_link} \\| {nbviewer_link} \\| {colab_link} |\n"
    
    if java_files:
        content += """
## Java Examples

Example Java code demonstrating NeqSim APIs:

| Example | Description |
|---------|-------------|
"""
        for java_file in java_files:
            name = java_file.stem
            title = name.replace('_', ' ')
            github_link = f"https://github.com/equinor/neqsim/blob/master/docs/examples/{java_file.name}"
            content += f"| [{title}]({github_link}) | Java example |\n"
    
    if md_files:
        content += """
## Other Tutorials

Additional documentation and guides:

"""
        for md_file in md_files:
            name = md_file.stem
            title = name.replace('_', ' ').replace('-', ' ').title()
            content += f"- [{title}]({md_file.name})\n"
    
    content += """
---

## Running the Notebooks

### Prerequisites

1. Install neqsim-python:
   ```bash
   pip install neqsim
   ```

2. Or use Google Colab (click the Colab links above) - no installation needed!

### Local Jupyter Setup

```bash
# Create a virtual environment
python -m venv neqsim-env
source neqsim-env/bin/activate  # On Windows: neqsim-env\\Scripts\\activate

# Install dependencies
pip install neqsim jupyter matplotlib pandas numpy

# Start Jupyter
jupyter notebook
```

Then open any of the `.ipynb` files from this directory.
"""
    
    index_path = examples_path / 'index.md'
    with open(index_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f"Created examples index: {index_path}")


if __name__ == '__main__':
    # Get the docs/examples directory
    script_dir = Path(__file__).parent
    examples_dir = script_dir / 'examples'
    
    print("NeqSim Notebook Converter")
    print("=" * 50)
    
    # Convert all notebooks
    convert_all_notebooks(examples_dir)
    
    print()
    
    # Create index
    create_examples_index(examples_dir)
