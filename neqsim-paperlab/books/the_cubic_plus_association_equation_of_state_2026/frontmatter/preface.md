## Preface

The Cubic Plus Association (CPA) equation of state occupies a unique position in applied thermodynamics. It bridges the gap between the classical cubic equations of state — trusted workhorses of the oil and gas industry for half a century — and the more rigorous but complex molecular-based SAFT models. Since its introduction by Kontogeorgis and colleagues in 1996, CPA has become the preferred thermodynamic model for systems involving hydrogen bonding: water–hydrocarbon equilibria, gas dehydration, hydrate prediction, methanol and glycol injection, and carbon capture and storage.

This book grew out of two decades of practical experience implementing and applying CPA within the NeqSim thermodynamic library. NeqSim, an open-source Java toolkit for thermodynamic and process simulation, contains one of the most comprehensive CPA implementations available — including four different solver variants, extensive parameter databases, and integration with full process simulation capabilities. Every equation, table, and prediction in this book can be reproduced by the reader using NeqSim's Python interface.

The book is intended for three audiences:

1. **Graduate students** in chemical engineering and thermodynamics who need a thorough grounding in association models
2. **Practicing engineers** in oil and gas, CCS, and chemical processing who need to understand when and how to apply CPA
3. **Researchers** developing new applications or extensions of association models

### How to Use This Book

The book is organized in three parts. **Part I (Chapters 1–4)** covers the foundations: thermodynamic principles, classical cubic equations of state, and the statistical mechanical theory of association. These chapters assume knowledge of undergraduate thermodynamics but are otherwise self-contained. **Part II (Chapters 5–8)** presents the CPA model itself: its formulation, parameterization, mixing rules, and numerical implementation in NeqSim. **Part III (Chapters 9–12)** covers applications: water–hydrocarbon systems, gas processing, CO$_2$ and acid gas systems, and advanced topics including electrolyte CPA and comparisons with PC-SAFT.

Each chapter includes:
- **Learning objectives** at the beginning
- **NeqSim code examples** (Python) that can be run immediately
- **Tables** comparing CPA predictions with experimental data and other models
- **Exercises** ranging from computational to conceptual
- **A summary** of key points

### Software Requirements

All code examples use the `neqsim` Python package, which wraps the Java library via jpype. Install with:

```bash
pip install neqsim
```

The NeqSim source code is available at https://github.com/equinor/neqsim.

### Acknowledgments

I am grateful to the NeqSim community and to colleagues at Equinor for many years of collaborative work on thermodynamic modeling. Special thanks to Georgios Kontogeorgis and his group at DTU for their foundational work on CPA, and to the many researchers whose experimental data and theoretical insights made this book possible.

Even Solbraa
Stavanger, 2026
All figures are generated from Jupyter notebooks included with the book source,
ensuring full reproducibility.

### Prerequisites

Readers should have a working knowledge of:

- Thermodynamics fundamentals
- Python programming
- Basic chemical engineering concepts

### Acknowledgements

TODO: Thank contributors, reviewers, institutions, and funding sources.
