# Getting started

Use this page as a launchpad into the NeqSim documentation. It mirrors the high-level structure from the Colab introduction notebook and links directly to reference guides and examples.

## Table of Contents
- [Set up NeqSim locally](#set-up-neqsim-locally)
- [Fundamentals and thermodynamics](#fundamentals-and-thermodynamics)
- [Fluid characterization and PVT workflows](#fluid-characterization-and-pvt-workflows)
- [Process simulation](#process-simulation)
- [Dynamic behavior and process safety](#dynamic-behavior-and-process-safety)
- [Unit operations and equipment models](#unit-operations-and-equipment-models)
- [Integration, control, and automation](#integration-control-and-automation)
- [Examples and tutorials](#examples-and-tutorials)

## Set up NeqSim locally

Clone the repository and build with the Maven wrapper:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

The command downloads dependencies, compiles the project, and runs the test suite. For environment notes and troubleshooting tips, see the [README](../../README.md) and [developer setup guide](../development/DEVELOPER_SETUP.md).

## Fundamentals and thermodynamics
- Read the [Thermodynamics Guide](thermodynamics_guide.md) for an overview of models, correlations, and implementation notes.
- Explore validated calculations in [Flash equations and tests](flash_equations_and_tests.md) and the [Thermodynamics of gas processing](process_simulation.md#thermodynamics).
- Review property-focused workflows in [Property flash workflows](property_flash_workflows.md) and viscosity models in [Viscosity models](viscosity_models.md).

## Fluid characterization and PVT workflows
- Follow [Fluid Characterization](fluid_characterization.md) for setting up equations of state and component data.
- Use the [PVT simulation workflows](pvt_simulation_workflows.md) and [Black-oil flash playbook](black_oil_flash_playbook.md) for reservoir-focused setups.
- See [Gas quality standards from tests](gas_quality_standards_from_tests.md) for handling analytical measurements.

## Process simulation
- Start with the [Process Simulation Guide](process_simulation.md) for steady-state modeling patterns.
- Dive deeper into [Advanced process simulation](advanced_process_simulation.md) and [Logical unit operations](logical_unit_operations.md) for custom flowsheets.
- Consult the [Modules overview](../modules.md) and [Process calculator](../simulation/process_calculator.md) when wiring NeqSim into larger systems.

## Dynamic behavior and process safety
- Study dynamic blowdown and protection behavior in [ESD blowdown systems](../safety/ESD_BLOWDOWN_SYSTEM.md), [PSV dynamic sizing](../safety/psv_dynamic_sizing_example.md), and [HIPPS implementation](../safety/hipps_implementation.md).
- Review layered safety topics in [Integrated safety systems](../safety/INTEGRATED_SAFETY_SYSTEMS.md), [HIPPS summary](../safety/HIPPS_SUMMARY.md), and [Layered safety architecture](../safety/layered_safety_architecture.md).
- For alarm logic and shutdown sequencing, see [Alarm system guide](../safety/alarm_system_guide.md), [SIS logic implementation](../safety/sis_logic_implementation.md), and [Integration safety chain tests](../safety/integration_safety_chain_tests.md).

## Unit operations and equipment models
- Browse individual equipment pages such as [Distillation column](distillation_column.md), [Air cooler](air_cooler.md), [Water cooler](water_cooler.md), and [Heat exchanger mechanical design](heat_exchanger_mechanical_design.md).
- For specialized models, see [Flow meter models](flow_meter_models.md), [Battery storage unit](battery_storage.md), [Solar panel](solar_panel.md), and [Pump usage guide](pump_usage_guide.md).
- Additional unit operations and mechanical details are covered in the [Process logic enhancements](../simulation/ProcessLogicEnhancements.md) series.

## Integration, control, and automation
- Connect NeqSim to control systems using the [Process control framework](process_control.md) and [Real-time integration guide](../integration/REAL_TIME_INTEGRATION_GUIDE.md).
- Learn about runtime flexibility in [Runtime logic flexibility](../simulation/RuntimeLogicFlexibility.md) and alarm handling in [Alarm triggered logic example](../safety/alarm_triggered_logic_example.md).
- For scripting and hybrid workflows, see [Java simulations from Colab notebooks](java_simulation_from_colab_notebooks.md) and [Java/Python usage examples](usage_examples.md).

## Examples and tutorials
- Work through the [Usage examples](usage_examples.md) for end-to-end flows in both Java and Python.
- Try the [Process transient simulation guide](process_transient_simulation_guide.md) and [Process simulation using NeqSim](process_simulation.md) for hands-on modeling patterns.
- Explore extended topics such as [Process automation and logic implementation summary](../simulation/process_logic_implementation_summary.md) and integration tests in [Test overview](test-overview.md).
