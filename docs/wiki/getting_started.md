---
title: "Getting Started with NeqSim"
description: "Install the current NeqSim release, run the repository-tested Java quick start, and choose a maintained thermodynamics, process, PVT, pipeline, or engineering guide."
---

Use this page to install NeqSim, run one complete thermodynamic calculation, and choose the
maintained guide that matches your task. NeqSim uses kelvin for constructor temperatures and bara
for constructor pressures unless an API explicitly accepts a unit string.

## Install the released library

Add the current release to a Maven project:

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.18.0</version>
</dependency>
```

Use [Maven Central](https://central.sonatype.com/artifact/com.equinor.neqsim/neqsim) to inspect
published artifacts and [GitHub Releases](https://github.com/equinor/neqsim/releases) for release
notes and downloadable assets. Application builds should pin a released version rather than track
the changing `master` branch.

## Build the current source

Clone the repository when contributing to NeqSim or validating changes against current source:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

On Windows, run `mvnw.cmd install`. The Maven wrapper supplies the repository's Maven version.
NeqSim source remains Java 8 compatible; continuous integration also tests newer supported JDKs.
See the [developer setup guide](../development/DEVELOPER_SETUP.md) for prerequisites, formatting,
tests, and platform-specific troubleshooting.

## Run the canonical Java quick start

The following complete program is shared with the documentation landing pages and protected by
the landing-page regression contract:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class NeqSimQuickStart {
  private static final Logger logger = LogManager.getLogger(NeqSimQuickStart.class);

  private NeqSimQuickStart() {}

  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.03);
    gas.addComponent("CO2", 0.02);
    gas.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(gas);
    operations.TPflash();
    gas.initProperties();

    logger.info("Density: {} kg/m3", gas.getDensity("kg/m3"));
    logger.info("Compressibility: {}", gas.getZ());
  }
}
```

`TPflash()` solves phase equilibrium at the system temperature and pressure. The explicit
`initProperties()` call prepares physical-property results before the example reads density. The
example logs values instead of writing directly to standard output, in line with repository rules.

For compilation and dependency details, continue with the
[Java getting-started guide](../java-getting-started.md). Python users should start from the
[Python integration on the documentation landing page](../index.md#python-integration), which uses
the supported `jneqsim` gateway.

## Choose a maintained workflow

| Goal | Recommended guide |
| --- | --- |
| Create fluids, run flashes, and read properties | [Thermodynamics recipes](../cookbook/thermodynamics-recipes.md) |
| Understand models and phase-equilibrium methods | [Thermodynamics guide](thermodynamics_guide.md) |
| Inspect flash equations and validation | [Flash equations and tests](flash_equations_and_tests.md) |
| Characterize oils and heavy fractions | [Fluid characterization](fluid_characterization.md) |
| Run laboratory-style PVT studies | [PVT simulation workflows](pvt_simulation_workflows.md) |
| Build a steady-state process | [Process recipes](../cookbook/process-recipes.md) |
| Understand process execution and reporting | [ProcessSystem guide](../process/processmodel/process_system.md) |
| Model absorbers and strippers | [Absorber and stripper guide](../process/equipment/absorbers.md) |
| Select a pipeline workflow | [Pipeline documentation index](pipeline_index.md) |
| Build dynamic cases | [Process transient simulation guide](process_transient_simulation_guide.md) |
| Screen oil-quality methods | [Oil-quality standards](../standards/oil_quality_standards.md) |
| Find runnable tutorials and notebooks | [Examples index](../examples/index.md) |
| Diagnose setup or runtime problems | [Troubleshooting guide](../troubleshooting/index.md) |

The [curated usage-example catalog](usage_examples.md) groups these guides by engineering task and
records the validation expectations for complete programs and notebooks.

## Interpret results responsibly

- Keep temperature, pressure, flow, composition, and property units explicit.
- Select the thermodynamic model and mixing rule for the fluid and property of interest.
- Check phase count before requesting a named phase from a result that may be single phase.
- Initialize the state required by each property API and verify mass or component closure where
  the workflow changes streams.
- Treat examples as reproducible screening workflows, not mechanical design, safety approval, or
  standards certification.

Use current source, focused tests, published benchmarks, and accountable engineering review before
using a result for design or operational decisions.
