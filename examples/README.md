# NeqSim Examples

This directory contains Jupyter notebooks, Java examples, and case studies demonstrating NeqSim capabilities.

Start with the [maintained examples catalog](../docs/examples/index.md) for guided
entry points and stored notebook execution status. The links below are navigation,
not a claim that every example has been executed against the current checkout.
Read each example's setup, assumptions, validation evidence, and limitations before reuse.

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

**Start here**: [Process tutorials](../docs/tutorials/index.md), including
[TEG dehydration](../docs/tutorials/teg_dehydration_tutorial.md) and
[gas-oil separation](../docs/tutorials/gosp_tutorial.md).

### Thermodynamics & Properties
Fluid phase behavior and physical properties:
- Phase envelopes
- Flash calculations (TP, PH, PS)
- Hydrate prediction
- Dew point calculations
- Density, viscosity, thermal conductivity

**Start here**: [Reading fluid properties](../docs/examples/ReadingFluidProperties.md)
and the [thermodynamics cookbook](../docs/cookbook/thermodynamics-recipes.md).

### PVT Analysis
Laboratory test simulations:
- Constant Mass Expansion (CME)
- Constant Volume Depletion (CVD)
- Differential Liberation (DL)
- Separator tests
- Swelling tests

**Start here**: [PVT laboratory-test guide](../docs/pvtsimulation/pvt_lab_tests.md)
and [PVT simulation and tuning](../docs/examples/PVT_Simulation_and_Tuning.md).
These maintained guides are under `docs/`, not this directory's notebook collection.

### Field Development & Subsea
Production systems and economics:
- Subsea well design and cost estimation
- Pipeline sizing and pressure drop
- Production forecasting
- Economic analysis (NPV, CAPEX, OPEX)
- SURF cost estimation

**Start here**: [Field-development guide](../docs/fielddevelopment/README.md)
and [well/SURF network integration](../docs/fielddevelopment/SURF_NETWORK_INTEGRATION.md).

### Standards & Gas Quality
Industry standard calculations:
- ISO 6976 calorific values and Wobbe index
- Gas quality specifications
- Dew point standards
- Sales gas contracts

**Start here**: [Standards and gas quality](../docs/standards/README.md), including
the [ISO 6976 worked example](../docs/standards/iso6976_calorific_values.md).
The ISO 5167 metering notebook covers a different standard; it is not a calorific-value example.

### Safety & Depressurization
Process safety calculations:
- Vessel blowdown
- Relief valve sizing
- Fire case modeling
- Source term generation

**Start here**: [Safety documentation](../docs/safety/index.md).
Follow the linked method-specific guides for applicability, validation, and engineering-review boundaries.

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
- **Task Solving**: [Engineering-task workflow](../docs/tutorials/solve-engineering-task.md) - Setup, analysis, validation, and reporting
- **Java/API Setup**: [Java getting-started guide](../docs/java-getting-started.md) - Build and run against the selected NeqSim version
- **API Source**: [Current checkout's Java packages](../src/main/java/neqsim/) - Signatures and Javadoc comments matching this repository revision

`task_solve/` is the default local output directory for engineering tasks, not a
tracked examples catalog. The workflow guide explains how to configure its location.

## Getting Help

- **Questions**: [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Issues**: [GitHub Issues](https://github.com/equinor/neqsim/issues)
- **Documentation**: [NeqSim Docs](https://equinor.github.io/neqsim/)
