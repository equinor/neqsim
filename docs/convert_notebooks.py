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
import sys
import json
import re
from pathlib import Path
from datetime import datetime
from urllib.parse import quote

NOTEBOOK_DOCUMENTATION_OVERRIDES = {
    "MercuryRemoval_LNG_Pretreatment": {
        "title": "Mercury Removal in LNG Pre-Treatment",
        "description": (
            "Executable NeqSim mercury-removal screening with transient "
            "loading, preliminary design and cost boundaries, and internal "
            "verification"
        ),
        "show_generated_title": False,
        "strip_notebook_title": True,
        "colab_link_text": "open it in Google Colab",
    },
    "process equipmentutl": {
        "title": (
            "Reservoir-to-Market Optimisation with NeqSim Process Equipment"
        ),
        "description": (
            "Executed reservoir-to-market process-equipment workflow with "
            "field-life depletion, well and flowline hydraulics, export "
            "compression, production optimisation, and value-chain economics."
        ),
        "show_generated_title": False,
    },
}


def get_notebook_documentation_metadata(notebook, notebook_name):
    """Return bounded legacy defaults updated by notebook-owned metadata."""

    documentation_metadata = dict(
        NOTEBOOK_DOCUMENTATION_OVERRIDES.get(notebook_name, {})
    )
    notebook_metadata = notebook.get("metadata", {}).get("neqsim_docs", {})
    if isinstance(notebook_metadata, dict):
        documentation_metadata.update(notebook_metadata)
    return documentation_metadata


def get_first_markdown_h1(notebook):
    """Return the first notebook-owned level-one heading, if present."""

    for cell in notebook.get("cells", []):
        if cell.get("cell_type") != "markdown":
            continue
        source = "".join(cell.get("source", []))
        match = re.search(r"(?m)^#\s+(.+?)\s*$", source)
        if match:
            return match.group(1).strip()
    return None


# Ensure Unicode output works on Windows consoles (cp1252 by default).
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass


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
    documentation_metadata = get_notebook_documentation_metadata(
        nb,
        notebook_name,
    )
    default_title = (
        get_first_markdown_h1(nb)
        or notebook_name.replace('_', ' ').replace('-', ' ')
    )
    title = documentation_metadata.get('title', default_title)
    description = documentation_metadata.get(
        'description',
        (
            f"Notebook for {title}, including NeqSim Python examples "
            "and workflow context."
        ),
    )
    generated_title = (
        f"# {title}\n\n"
        if documentation_metadata.get('show_generated_title', False)
        else ''
    )
    colab_link_text = documentation_metadata.get(
        'colab_link_text',
        'open in Google Colab',
    )
    strip_first_h1 = documentation_metadata.get(
        'strip_first_h1',
        documentation_metadata.get('strip_notebook_title', True),
    )
    title_yaml = json.dumps(str(title), ensure_ascii=False)
    description_yaml = json.dumps(str(description), ensure_ascii=False)
    encoded_notebook_filename = quote(
        f"{notebook_name}.ipynb",
        safe="",
    )

    # Jekyll front matter
    front_matter = f"""---
layout: default
title: {title_yaml}
description: {description_yaml}
parent: Examples
nav_order: 1
---

{generated_title}> **Note:** This is an auto-generated Markdown version of the Jupyter notebook
> [`{notebook_name}.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/{encoded_notebook_filename}).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/{encoded_notebook_filename})
> or [{colab_link_text}](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/{encoded_notebook_filename}).

---

"""

    markdown_content = []
    first_h1_stripped = False

    for cell in nb.get('cells', []):
        cell_type = cell.get('cell_type', '')
        source = ''.join(cell.get('source', []))

        if cell_type == 'markdown':
            if strip_first_h1 and not first_h1_stripped:
                # The Jekyll page title is supplied by front matter. Keep the
                # notebook's H1 for Colab while avoiding a duplicate page H1.
                source, replacements = re.subn(
                    r'(?m)^# [^\r\n]*(?:\r?\n){0,2}',
                    '',
                    source,
                    count=1,
                )
                first_h1_stripped = replacements == 1
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



CURATED_NOTEBOOKS = (
    {
        "title": "Complete Offshore Process Engineering Study",
        "description": (
            "Full three-stage oil/gas process benchmark, closed design loop, "
            "discipline results, closed-loop SIF/reliability/HAZOP-LOPA-SRS/"
            "facility-response lifecycle, revisioned model packages, change "
            "revalidation, method benchmarks, and inline PyDEXPI P&ID rendering"
        ),
        "path": (
            "examples/notebooks/"
            "complete_offshore_process_engineering_study.ipynb"
        ),
        "guide": "../integration/complete-offshore-process-engineering-study.md",
    },
    {
        "title": "Full DEXPI Engineering ProcessSystem",
        "description": (
            "Executed line-list, relief, SIL/PFD/voting, shutdown, PSV, "
            "blowdown/flare, materials, readiness, and governed DEXPI workflow"
        ),
        "path": (
            "examples/notebooks/"
            "dexpi_engineering_full_processsystem.ipynb"
        ),
    },
    {
        "title": "DEXPI Engineering ProcessModel",
        "description": (
            "Executed multi-area packages with area-specific engineering "
            "inputs and readiness comparison"
        ),
        "path": (
            "examples/notebooks/"
            "dexpi_engineering_processmodel.ipynb"
        ),
    },
    {
        "title": "DEXPI P&ID Visualization",
        "description": (
            "Executed, dependency-light native DEXPI/Proteus parser and "
            "deterministic structural P&ID PNG/SVG renderer with a committed "
            "figure"
        ),
        "path": "examples/notebooks/dexpi_pid_visualization.ipynb",
    },
    {
        "title": "Energy Network Dispatch and Reporting",
        "description": (
            "Executed multi-source and multi-load electrical dispatch with "
            "priorities, shortage and curtailment allocation, cost, emissions, "
            "and auditable network reports"
        ),
        "path": (
            "examples/notebooks/energy_networks/"
            "01_energy_dispatch_and_reporting.ipynb"
        ),
    },
    {
        "title": "Rotating Equipment and Converter Maps",
        "description": (
            "Executed motor and VFD part-load performance, shaft coupling, "
            "and load-dependent generator and prime-mover efficiency maps"
        ),
        "path": (
            "examples/notebooks/energy_networks/"
            "02_rotating_equipment_and_converter_maps.ipynb"
        ),
    },
    {
        "title": "Thermal Utilities and Hydraulics",
        "description": (
            "Executed utility mass-flow, temperature-quality, exergy, cooling-"
            "water pressure-drop, and pump-power screening workflow"
        ),
        "path": (
            "examples/notebooks/energy_networks/"
            "03_thermal_utilities_and_hydraulics.ipynb"
        ),
    },
    {
        "title": "Chronological Offshore Energy Benchmark",
        "description": (
            "Executed time-series energy balance, generator commitment, "
            "operating-cost and CO2 accounting, and offshore wind-gas benchmark"
        ),
        "path": (
            "examples/notebooks/energy_networks/"
            "04_time_series_commitment_offshore_benchmark.ipynb"
        ),
    },
)

JAVA_EXAMPLE_DESCRIPTIONS = {
    'EclipseE300ExportImportExample': 'Eclipse E300 fluid export and import workflow',
    'FlowRegimeDebug': 'Flow-regime diagnostic calculations',
    'FlowRegimeDetectionExample': 'Flow-regime detection across operating cases',
    'MultiScenarioVFPExample': 'Multi-scenario vertical-flow-performance comparison',
    'MultiphaseModelPressureDropComparison': (
        'Multiphase pressure-drop model comparison'
    ),
    'OffshoreEmissionReportingExample': 'Offshore emissions accounting workflow',
    'RealTimeIntegrationExample': 'Real-time process-data integration pattern',
    'SlugTrackingComparisonExample': 'Slug-tracking model comparison',
    'TransientPipelineLiquidAccumulationExample': (
        'Transient pipeline liquid-accumulation study'
    ),
    'TwoFluidPipeExample': 'Two-fluid pipe setup and reporting',
    'TwoFluidPipeSlugTrackingExample': 'Two-fluid slug-tracking workflow',
    'TwoFluidPipelineLiquidAccumulationExample': (
        'Two-fluid pipeline accumulation study'
    ),
    'TwoFluidVsDriftFluxComparisonExample': (
        'Two-fluid and drift-flux comparison'
    ),
    'WellToOilStabilizationExample': 'Well-to-oil-stabilization process workflow',
}


def markdown_table_cell(value):
    """Return one normalized, escaped Markdown table cell."""

    return " ".join(str(value).split()).replace("|", r"\|")


def notebook_view_links(repository_path, markdown_path=None, guide=None):
    """Build URL-safe Markdown, nbviewer, and Colab links for one notebook."""

    encoded_repository_path = quote(repository_path, safe="/")
    links = []
    if guide:
        links.append(f"[Guide]({guide})")
    if markdown_path:
        links.append(f"[Markdown]({quote(markdown_path, safe='/')})")
    links.extend(
        (
            "[nbviewer]("
            "https://nbviewer.org/github/equinor/neqsim/blob/master/"
            f"{encoded_repository_path})",
            "[Colab]("
            "https://colab.research.google.com/github/equinor/neqsim/"
            f"blob/master/{encoded_repository_path})",
        )
    )
    return r" \| ".join(links)


def notebook_stored_status(notebook):
    """Describe the execution evidence stored in one notebook."""

    code_cells = [
        cell
        for cell in notebook.get('cells', [])
        if cell.get('cell_type') == 'code'
    ]
    execution_counts = [cell.get('execution_count') for cell in code_cells]
    outputs = [
        output
        for cell in code_cells
        for output in cell.get('outputs', [])
    ]

    has_error = any(output.get('output_type') == 'error' for output in outputs)
    has_stderr = any(output.get('name') == 'stderr' for output in outputs)
    if has_error or has_stderr:
        raise ValueError('Notebook retains an exception or stderr output')

    executed_count = sum(count is not None for count in execution_counts)
    if executed_count == len(code_cells):
        return 'Executed'
    if executed_count:
        return 'Partial'
    return 'Source only'


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

This section contains tutorials, code examples, and Jupyter notebooks demonstrating NeqSim capabilities.

## Maintained workflow notebooks

These repository workflows live under `examples/notebooks/` and are maintained
with their engineering guides. Their rows describe the validation evidence stored
with each notebook; follow the linked guide for exact scope and limitations.

| Notebook | Description | View Options |
|----------|-------------|--------------|
"""

    for entry in CURATED_NOTEBOOKS:
        content += (
            f"| **{markdown_table_cell(entry['title'])}** | "
            f"{markdown_table_cell(entry['description'])} | "
            f"{notebook_view_links(entry['path'], guide=entry.get('guide'))} |\n"
        )

    content += """
## Local notebook catalog

Stored status describes the committed notebook only; it is not a rerun against the
current `master` branch:

- **Executed** — every code cell has a stored execution count and there is no stored
  exception or standard-error stream.
- **Partial** — some, but not all, code cells have stored execution counts.
- **Source only** — no code cell has a stored execution count.

For engineering use, rerun from a clean environment, inspect all outputs, and validate
the model, units, assumptions, and operating range. A rendered Markdown page is a
reading aid, not execution evidence.

| Stored status | Notebook | Description | View options |
|---------------|----------|-------------|--------------|
"""

    for nb in notebooks:
        name = nb.stem
        with open(nb, 'r', encoding='utf-8') as notebook_file:
            notebook = json.load(notebook_file)
        documentation_metadata = get_notebook_documentation_metadata(
            notebook,
            name,
        )
        title = documentation_metadata.get(
            'title',
            name.replace('_', ' ').replace('-', ' '),
        )
        description = documentation_metadata.get(
            'description',
            'See notebook for details',
        )
        links = notebook_view_links(
            f"docs/examples/{name}.ipynb",
            f"{name}.md",
        )
        stored_status = notebook_stored_status(notebook)
        content += (
            f"| **{stored_status}** | "
            f"**{markdown_table_cell(title)}** | "
            f"{markdown_table_cell(description)} | {links} |\n"
        )

    if java_files:
        content += """
## Standalone Java source examples

These files are outside Maven's compiled source tree. They are retained as source-only
references and are not catalogued as build-verified or policy-compliant examples.
They currently contain legacy console output; review and port the required calls into a
tested `src/test/java` example before reuse. For a supported starting point, use the
[Java getting-started guide](../java-getting-started.md).

| Example | Stored status | Capability |
|---------|---------------|------------|
"""
        for java_file in java_files:
            name = java_file.stem
            title = name.replace('_', ' ')
            encoded_name = quote(java_file.name, safe='')
            description = JAVA_EXAMPLE_DESCRIPTIONS.get(name, 'Java example')
            content += (
                f"| [{title}]({encoded_name}) | **Source only** | "
                f"{description} |\n"
            )

    if md_files:
        content += """
## Other Tutorials

Additional documentation and guides:

"""
        for md_file in md_files:
            name = md_file.stem
            title = name.replace('_', ' ').replace('-', ' ').title()
            content += f"- [{title}]({quote(md_file.name, safe='')})\n"

    content += """
---

## Running the Notebooks

### Prerequisites

1. Install neqsim-python:
   ```bash
   pip install neqsim
   ```

2. Or open a Google Colab link above. Run and inspect the notebook's setup cell first; dependency installation and stored execution status vary by notebook.

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
