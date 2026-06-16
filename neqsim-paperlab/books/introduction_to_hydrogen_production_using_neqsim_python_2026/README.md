# Introduction to Hydrogen Production using NeqSim and Python

[![Open companion notebook in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/neqsim-paperlab/books/introduction_to_hydrogen_production_using_neqsim_python_2026/notebooks/hydrogen_neqsim_workflows.ipynb)
[![Launch on Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/equinor/neqsim/master)

This is a NeqSim PaperLab book project generated for a hydrogen-production
textbook based on `process_modeling_with_neqsim_python_2026`.

Useful commands from `neqsim-paperlab/`:

```bash
python paperflow.py book-status books/introduction_to_hydrogen_production_using_neqsim_python_2026
python paperflow.py book-check books/introduction_to_hydrogen_production_using_neqsim_python_2026
python paperflow.py book-render books/introduction_to_hydrogen_production_using_neqsim_python_2026 --format html
python paperflow.py book-render books/introduction_to_hydrogen_production_using_neqsim_python_2026 --format pdf
```

The root-level `cover_front.png` and `cover_back.png` are picked up by the HTML
and PDF renderers.

The companion notebook is generated at
`notebooks/hydrogen_neqsim_workflows.ipynb`. Every chapter also includes a
`notebooks/parameter_study.ipynb` file that regenerates the parameter-study
table, graph, CSV, and JSON evidence embedded in the manuscript. A compatibility
copy is kept as `notebooks/expected_output.ipynb` for older PaperLab workflows.

## Reproducibility

This book is designed to be reproducible. To set up an environment that runs the
companion notebook and the chapter parameter studies:

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Every chapter code block is marked `<!-- noexec -->` because it must be run by
the reader in a controlled environment against a known NeqSim version. To make a
result citable, record the NeqSim version in each notebook:

```python
from neqsim import jneqsim
print("NeqSim version:", jneqsim.util.NeqSimInfo().getVersion())
```

To cite the book, use the metadata in `CITATION.cff`. The pinned Python
dependencies are listed in `requirements.txt`.
