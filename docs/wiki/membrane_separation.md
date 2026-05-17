---
title: "Membrane separation"
description: "This page outlines the basic model implemented in the `MembraneSeparator` unit. The unit is intended for simple simulations of gas separation membranes or pervaporation modules used in purification an..."
---

# Membrane separation

This page outlines the basic model implemented in the `MembraneSeparator` unit. The unit is intended for simple simulations of gas separation membranes or pervaporation modules used in purification and CO2 capture.

## Flux model
For each component $i$ a constant permeate fraction $f_i$ can be specified. The molar amount transferred to the permeate side is
$$
N_i^{\text{perm}} = f_i N_i^{\text{feed}}
$$
where $N_i^{\text{feed}}$ is the molar amount in the feed stream. Components without a specified fraction use a global default value.

A more rigorous model could employ Fick's law of diffusion through the membrane
$$
J_i = P_i \left(p_{i,\text{feed}} - p_{i,\text{perm}}\right)
$$
where $P_i$ is the permeability of component $i$ and $p_i$ are partial pressures.
The separator can now perform this calculation mode when permeabilities and a membrane area are supplied.

## Usage
```java
MembraneSeparator mem = new MembraneSeparator("mem", feedStream);
mem.setDefaultPermeateFraction(0.1); // 10 % of each component permeates
mem.setPermeateFraction("CO2", 0.5); // override CO2 fraction
```
// Alternative using permeability coefficients
mem.clearPermeateFractions();
mem.setMembraneArea(5.0); // m^2
mem.setPermeability("CO2", 5e-6); // mol/(m2*s*Pa)
mem.setPermeability("methane", 1e-6);
```
After running the process, the permeate and retentate streams can be obtained via `getPermeateStream()` and `getRetentateStream()`.
