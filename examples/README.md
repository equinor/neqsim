# NeqSim Examples

This directory contains Jupyter notebooks, Java examples, and case studies demonstrating NeqSim capabilities.

## Quick Start (5 minutes)

### Jupyter Notebooks
Browse the [notebooks/](notebooks/) directory for interactive Python examples using neqsim-python.

**Getting Started:**
```bash
pip install neqsim jupyter
jupyter notebook
```

### Java Examples
See [neqsim/](neqsim/) for standalone Java code you can copy into your projects.

## Examples by Topic

### Process Simulation
Complete flowsheets and unit operations:
- Gas compression trains
- TEG dehydration systems
- Gas-Oil Separation Plants (GOSP)
- Heat exchanger networks
- Distillation columns

**Browse**: [notebooks/](notebooks/) - Look for files with `process`, `separator`, `compressor`, `teg`, `gosp`

### Thermodynamics & Properties
Fluid phase behavior and physical properties:
- Phase envelopes
- Flash calculations (TP, PH, PS)
- Hydrate prediction
- Dew point calculations
- Density, viscosity, thermal conductivity

**Browse**: [notebooks/](notebooks/) - Look for files with `flash`, `hydrate`, `phase`, `property`

### PVT Analysis
Laboratory test simulations:
- Constant Mass Expansion (CME)
- Constant Volume Depletion (CVD)
- Differential Liberation (DL)
- Separator tests
- Swelling tests

**Browse**: [notebooks/](notebooks/) - Look for files with `pvt`, `cme`, `cvd`, `dl`

### Field Development & Subsea
Production systems and economics:
- Subsea well design and cost estimation
- Pipeline sizing and pressure drop
- Production forecasting
- Economic analysis (NPV, CAPEX, OPEX)
- SURF cost estimation

**Browse**: [notebooks/](notebooks/) - Look for files with `subsea`, `well`, `pipeline`, `npv`, `field`

### Standards & Gas Quality
Industry standard calculations:
- ISO 6976 calorific values and Wobbe index
- Gas quality specifications
- Dew point standards
- Sales gas contracts

**Browse**: [notebooks/](notebooks/) - Look for files with `iso`, `standard`, `quality`, `dew`

### Safety & Depressurization
Process safety calculations:
- Vessel blowdown
- Relief valve sizing
- Fire case modeling
- Source term generation

**Browse**: [notebooks/](notebooks/) - Look for files with `safety`, `depressur`, `blowdown`, `psv`

## Case Studies

Complete worked examples from real engineering projects:

### CNG Tank Thermal Analysis
- **Location**: [CNGtankmodelling/](CNGtankmodelling/)
- **Topic**: Temperature changes during CNG tank filling and emptying
- **Methods**: Joule-Thomson effect, heat transfer modeling

### Sulfur Deposition Analysis
- **Location**: [sulfurtask/](sulfurtask/)
- **Topic**: Sulfur precipitation in gas processing
- **Methods**: Chemical equilibrium, solid flash, corrosion assessment

## Contributing Examples

Have a useful example? Share it with the community:

1. **Create a self-contained notebook** or Java file
2. **Add clear documentation** - what it demonstrates, how to run it
3. **Include references** - if based on published data or standards
4. **Test it works** - fresh kernel run for notebooks, compile for Java
5. **Submit a PR** - see [CONTRIBUTING.md](../CONTRIBUTING.md)

### Example Checklist
- [ ] Descriptive title and introduction
- [ ] Required imports clearly shown
- [ ] Input data defined (no external file dependencies if possible)
- [ ] Results validated or compared to known values
- [ ] Figures have axis labels, titles, and units
- [ ] Runtime < 5 minutes (or marked as "long-running")

## Related Resources

- **Documentation**: [docs/](../docs/) - Full reference manual
- **Tests**: [src/test/](../src/test/) - Unit test examples
- **Task Solving**: [task_solve/](../task_solve/) - AI-assisted workflow for new problems
- **API Reference**: [JavaDoc](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html)

## Getting Help

- **Questions**: [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Issues**: [GitHub Issues](https://github.com/equinor/neqsim/issues)
- **Documentation**: [NeqSim Docs](https://equinor.github.io/neqsim/)
