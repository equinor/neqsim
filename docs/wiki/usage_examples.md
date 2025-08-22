# Usage examples

Below is a minimal Java code snippet showing how to perform a TPflash calculation with NeqSim.

```java
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.ThermodynamicOperations;

SystemSrkCPA system = new SystemSrkCPA(298.15, 50.0);
system.addComponent("methane", 0.8);
system.addComponent("ethane", 0.2);
system.createDatabase();

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

System.out.println("Phase fractions: " + java.util.Arrays.toString(system.getPhaseFraction()));
```

More detailed examples are available in the test suite under `src/test/java`.

## Wind turbine

The `WindTurbine` unit converts kinetic energy in wind into electrical power using a simple
actuator-disk formulation. Air density is assumed constant at 1.225 kg/m³ and all inefficiencies
are lumped into the power coefficient.

```java
import neqsim.process.equipment.powergeneration.WindTurbine;

WindTurbine turbine = new WindTurbine("turbine");
turbine.setWindSpeed(12.0);    // m/s
turbine.setRotorArea(50.0);    // m^2
turbine.setPowerCoefficient(0.4);
turbine.run();

System.out.println("Power produced: " + turbine.getPower() + " W");
```
