# Getting started with NeqSim and Github

The NeqSim library as a jar files can be downloaded from the [NeqSim release pages](https://github.com/equinor/neqsimsource/releases). A shaded library is distributed including all dependent libraries used by NeqSim. Use of NeqSim in a Java program is done by adding NeqSim.jar to the classpath.

Building NeqSim from source is done by cloning the project to a local directory on the developer computer. Building the code is done using JDK8+. NeqSim uses a number of libraries (jar files) for various calculations. These libraries must be available as part of the compilation process. The required libraries are listed in the [pom.xml](https://github.com/equinor/neqsimsource/blob/master/pom.xml) file. NeqSim can be built using the Maven build system ([https://maven.apache.org/](https://maven.apache.org/)). All NeqSim build dependencies are given in the pom.xml file.

An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).

Also see [NeqSim JavaDoc](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html).

And also see the [Java tests](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim) where a lot of the functionality is demonstrated.

---

## 📚 NeqSim Java Documentation - Table of Contents

Navigate through the documentation organized from introductory concepts to advanced topics.

---

### 🚀 Part I: Getting Started (Beginner)

| # | Topic | Description |
|---|-------|-------------|
| 1 | [Getting started with NeqSim and GitHub](https://github.com/equinor/neqsimsource/wiki/Getting-started-with-NeqSim-and-Github) | Installation and quick start |
| 2 | [Getting started as a NeqSim developer](https://github.com/equinor/neqsim/wiki/Getting-started-as-a-NeqSim-developer) | Development environment setup |
| 3 | [The NeqSim parameter database](https://github.com/equinor/neqsimsource/wiki/The-NeqSim-parameter-database) | Component database and parameters |

---

### 🧪 Part II: Fundamentals - Fluids & Thermodynamics (Beginner-Intermediate)

| # | Topic | Description |
|---|-------|-------------|
| 4 | [Example of setting up a fluid and running simple flash calculations](https://github.com/equinor/neqsimsource/wiki/Example-of-setting-up-a-fluid-and-running-simple-flash-calculations) | Basic fluid creation and flash |
| 5 | [Select thermodynamic model and mixing rule](https://github.com/equinor/neqsim/wiki/Create-a-fluid-and-select-Equation-of-State-and-Mixing-Rule) | EOS selection and configuration |
| 6 | [Flash calculations and phase envelope calculations using NeqSim](https://github.com/equinor/neqsimsource/wiki/Flash-calculations-and-phase-envelope-calculations-using-NeqSim) | Phase equilibria calculations |
| 7 | [Calculation of thermodynamic and physical properties using NeqSim](https://github.com/equinor/neqsimsource/wiki/Calculation-of-thermodynamic-and-physical-properties-using-NeqSim) | Property calculation methods |

---

### 🛢️ Part III: Fluid Characterization (Intermediate)

| # | Topic | Description |
|---|-------|-------------|
| 8 | [Oil Characterization in NeqSim](https://github.com/equinor/neqsimsource/wiki/Oil-Characterization-in-NeqSim) | Crude oil and condensate characterization |
| 9 | [Aqueous fluids and NeqSim](https://github.com/equinor/neqsimsource/wiki/Aqueous-fluids-and-NeqSim) | Water and brine systems |
| 10 | [Electrolytes and NeqSim](https://github.com/equinor/neqsimsource/wiki/Electrolytes-and-NeqSim) | Electrolyte thermodynamics |

---

### ⚙️ Part IV: Process Simulation (Intermediate)

| # | Topic | Description |
|---|-------|-------------|
| 11 | [Process Calculations in NeqSim](https://github.com/equinor/neqsimsource/wiki/Process-Calculations-in-NeqSim) | Process simulation fundamentals |

#### Equipment-Specific Guides
| Topic | Description |
|-------|-------------|
| [Compressor calculations](https://github.com/equinor/neqsimsource/wiki/Compressor-Calc) | Compressor modeling |
| [Compressor curves](https://github.com/equinor/neqsim/wiki/Compressor-curves) | Performance curves and maps |

---

### 🔧 Part V: Extending NeqSim (Advanced)

| # | Topic | Description |
|---|-------|-------------|
| 12 | [Adding a thermodynamic model in NeqSim](https://github.com/equinor/neqsimsource/wiki/Adding-a-thermodynamic-model-in-NeqSim) | Implement custom EOS models |
| 13 | [Adding a viscosity model in NeqSim](https://github.com/equinor/neqsimsource/wiki/Adding-a-viscosity-model-in-NeqSim) | Implement custom viscosity correlations |
| 14 | [Adding an unit operation in NeqSim](https://github.com/equinor/neqsimsource/wiki/Adding-an-unit-operation-in-NeqSim) | Create custom process equipment |

---

### 🖥️ Part VI: Integration & Deployment (Advanced)

| # | Topic | Description |
|---|-------|-------------|
| 15 | [How to make a NeqSim API](https://github.com/equinor/neqsim/wiki/How-to-make-a-NeqSim-API) | Building REST APIs with NeqSim |
| 16 | [Create native image using GraalVM](https://github.com/equinor/neqsim/wiki/Create-native-image-using-GraalVM) | Native compilation for performance |

---

### 📈 Part VII: Performance & Debugging (Advanced)

| # | Topic | Description |
|---|-------|-------------|
| 17 | [Profiling calculations](https://github.com/equinor/neqsim/wiki/Profiling) | Performance analysis and optimization |
| 18 | [Dynamic process simulations](https://github.com/equinor/neqsim/wiki/Dynamic-process-simulation) | Transient and dynamic modeling |

---

## 📖 Additional Documentation Resources

### In-Repository Documentation (docs/ folder)

The repository contains extensive documentation organized by module:

#### Core Modules
- [Modules Overview](https://github.com/equinor/neqsim/blob/master/docs/modules.md) - Architecture and module structure
- [Reference Manual Index](https://github.com/equinor/neqsim/blob/master/docs/REFERENCE_MANUAL_INDEX.md) - Complete documentation index

#### Thermodynamics
- [Thermodynamics Guide](https://github.com/equinor/neqsim/blob/master/docs/wiki/thermodynamics_guide.md) - Comprehensive thermodynamics overview
- [Flash Equations and Tests](https://github.com/equinor/neqsim/blob/master/docs/wiki/flash_equations_and_tests.md) - Flash calculation methods
- [Viscosity Models](https://github.com/equinor/neqsim/blob/master/docs/wiki/viscosity_models.md) - Viscosity calculation models
- [Steam Tables IF97](https://github.com/equinor/neqsim/blob/master/docs/wiki/steam_tables_if97.md) - Steam table implementation

#### Process Simulation
- [Process Simulation Guide](https://github.com/equinor/neqsim/blob/master/docs/wiki/process_simulation.md) - Process modeling patterns
- [Advanced Process Simulation](https://github.com/equinor/neqsim/blob/master/docs/wiki/advanced_process_simulation.md) - Advanced techniques
- [Distillation Column](https://github.com/equinor/neqsim/blob/master/docs/wiki/distillation_column.md) - Column modeling
- [Pump Usage Guide](https://github.com/equinor/neqsim/blob/master/docs/wiki/pump_usage_guide.md) - Pump calculations

#### Pipeline & Multiphase Flow
- [Pipeline Index](https://github.com/equinor/neqsim/blob/master/docs/wiki/pipeline_index.md) - Pipeline documentation hub
- [Two-Fluid Model](https://github.com/equinor/neqsim/blob/master/docs/wiki/two_fluid_model.md) - Two-phase flow
- [Beggs and Brill Correlation](https://github.com/equinor/neqsim/blob/master/docs/wiki/beggs_and_brill_correlation.md) - Pressure drop
- [Pipeline Heat Transfer](https://github.com/equinor/neqsim/blob/master/docs/wiki/pipeline_heat_transfer.md) - Thermal modeling

#### Safety & Dynamics
- [PSV Dynamic Sizing](https://github.com/equinor/neqsim/blob/master/docs/wiki/psv_dynamic_sizing_example.md) - Relief valve sizing
- [Process Transient Simulation Guide](https://github.com/equinor/neqsim/blob/master/docs/wiki/process_transient_simulation_guide.md) - Dynamic simulation
- [ESD Fire Alarm System](https://github.com/equinor/neqsim/blob/master/docs/wiki/esd_fire_alarm_system.md) - Safety systems

#### PVT & Characterization
- [PVT Simulation Workflows](https://github.com/equinor/neqsim/blob/master/docs/wiki/pvt_simulation_workflows.md) - PVT studies
- [Fluid Characterization](https://github.com/equinor/neqsim/blob/master/docs/wiki/fluid_characterization.md) - Fluid setup
- [Black Oil Flash Playbook](https://github.com/equinor/neqsim/blob/master/docs/wiki/black_oil_flash_playbook.md) - Black oil models

#### Integration
- [AI Platform Integration](https://github.com/equinor/neqsim/blob/master/docs/integration/ai_platform_integration.md) - AI/ML integration
- [MPC Integration](https://github.com/equinor/neqsim/blob/master/docs/integration/mpc_integration.md) - Model predictive control
- [Real-Time Integration Guide](https://github.com/equinor/neqsim/blob/master/docs/integration/REAL_TIME_INTEGRATION_GUIDE.md) - Real-time systems

#### Development
- [Developer Setup](https://github.com/equinor/neqsim/blob/master/docs/development/DEVELOPER_SETUP.md) - Environment setup
- [Test Overview](https://github.com/equinor/neqsim/blob/master/docs/wiki/test-overview.md) - Testing guidelines
- [Usage Examples](https://github.com/equinor/neqsim/blob/master/docs/wiki/usage_examples.md) - Code examples

---

## 🔗 Quick Links

| Resource | Link |
|----------|------|
| **Source Code** | [github.com/equinor/neqsim](https://github.com/equinor/neqsim) |
| **JavaDoc** | [NeqSim JavaDoc](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html) |
| **Java Tests** | [Test Examples](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim) |
| **Colab Demo** | [Interactive Tutorial](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7) |
| **Releases** | [Download JAR](https://github.com/equinor/neqsimsource/releases) |
| **Discussions** | [GitHub Discussions](https://github.com/equinor/neqsim/discussions) |
