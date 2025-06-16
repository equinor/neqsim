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
