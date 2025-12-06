# Viscosity Models in NeqSim

NeqSim provides several methods for calculating the viscosity of fluids, ranging from standard correlations to advanced corresponding states models. This document details the primary models available, recent improvements, and how to use them in your simulations.

## Available Models

### 1. Lohrenz-Bray-Clark (LBC)
The **LBC** method is a widely used correlation for calculating the viscosity of reservoir fluids. It is based on the correspondence between the viscosity of a fluid and its density, using a fourth-degree polynomial in reduced density.

*   **Keyword**: `"LBC"`
*   **Best for**: General oil and gas systems, reservoir fluids.
*   **Recent Improvements**:
    *   **Whitson Consistency**: The implementation has been aligned with the standard "Whitson" interpretation of the LBC correlation.
    *   **Heavy Oil Handling**: The critical volume mixing rule was updated to a linear summation ($\sum x_i V_{ci}$) instead of the previous cubic root rule. This significantly improves predictions for high-pressure mixtures containing heavy components (e.g., TBP fractions).
    *   **Unit Corrections**: Internal pressure and viscosity unit conversions have been rigorously verified against literature.

### 2. Corresponding States Principle (CSP)
The **CSP** method (often referred to as PFCT in NeqSim) uses the Corresponding States Principle to calculate viscosity. It relates the viscosity of the mixture to that of a reference substance (typically Methane) at a corresponding state.

*   **Keyword**: `"PFCT"`
*   **Best for**: Light to medium hydrocarbon mixtures, natural gas.
*   **Recent Improvements**:
    *   Fixed a bug where the reference fluid state was initialized using the actual mixture temperature/pressure instead of the corresponding state temperature/pressure ($T_0, P_0$). This ensures consistent behavior across gas and liquid phases.

### 3. Corresponding States Principle for Heavy Oil (CSP-Heavy-Oil)
A variant of the CSP model specifically tuned for heavy oil systems. It includes additional terms or modifications to better represent the viscous behavior of heavy fractions.

*   **Keyword**: `"PFCT-Heavy-Oil"`
*   **Best for**: Heavy oils, systems with significant TBP fractions.

### 4. Friction Theory
The **Friction Theory** (f-theory) model links viscosity to the equation of state (EOS) by separating the total viscosity into a dilute gas contribution and a residual friction contribution. The friction contribution is correlated against the attractive and repulsive pressure terms of the EOS.

*   **Keyword**: `"friction theory"`
*   **Best for**: Wide range of fluids, consistent with EOS thermodynamics.

## Usage in NeqSim

To use a specific viscosity model, you must set it on the `PhysicalProperties` object of a phase. This is typically done after creating the system but before performing calculations.

### Java Example

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ViscosityExample {
    public static void main(String[] args) {
        // 1. Create a system
        SystemInterface system = new SystemSrkEos(298.15, 100.0); // 298.15 K, 100 bar
        system.addComponent("methane", 0.5);
        system.addComponent("n-heptane", 0.5);
        
        // 2. Set mixing rule and initialize
        system.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();

        // 3. Set Viscosity Model for a specific phase (e.g., oil/liquid)
        // Available options: "LBC", "PFCT", "PFCT-Heavy-Oil", "friction theory"
        
        // Example: Using LBC
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
        system.initProperties();
        double lbcViscosity = system.getPhase("oil").getViscosity();
        System.out.println("LBC Viscosity: " + lbcViscosity + " kg/(m*s)");

        // Example: Using PFCT
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT");
        system.initProperties();
        double pfctViscosity = system.getPhase("oil").getViscosity();
        System.out.println("PFCT Viscosity: " + pfctViscosity + " kg/(m*s)");
        
        // Example: Using PFCT for Heavy Oil
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT-Heavy-Oil");
        system.initProperties();
        double pfctHeavyViscosity = system.getPhase("oil").getViscosity();
        System.out.println("PFCT Heavy Oil Viscosity: " + pfctHeavyViscosity + " kg/(m*s)");

        // Example: Using Friction Theory
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
        system.initProperties();
        double frictionViscosity = system.getPhase("oil").getViscosity();
        System.out.println("Friction Theory Viscosity: " + frictionViscosity + " kg/(m*s)");
    }
}
```

### Tuning LBC dense-fluid parameters

The LBC implementation exposes the dense-fluid polynomial coefficients ("Whitson/Bray-Clark"
$a_0 \dots a_4$ parameters) so you can tune the model against laboratory data. After selecting the
`"LBC"` viscosity model, update the coefficients via `setLbcParameters` or `setLbcParameter`, then
re-initialize properties to apply them:

```java
system.getPhase(1).getPhysicalProperties().setViscosityModel("LBC");
system.initProperties();
double baseViscosity = system.getPhase(1).getViscosity();

double[] tunedCoefficients = new double[] {0.11, 0.030, 0.065, -0.045, 0.010};
system.getPhase(1).getPhysicalProperties().setLbcParameters(tunedCoefficients);
system.getPhase(1).getPhysicalProperties().setLbcParameter(2, 0.070); // tweak a single term
system.initProperties();
double tunedViscosity = system.getPhase(1).getViscosity();

System.out.println("Base viscosity:  " + baseViscosity);
System.out.println("Tuned viscosity: " + tunedViscosity);
```

### Python Example (via JPype)

```python
from neqsim.thermo import SystemSrkEos
from neqsim.thermodynamicoperations import ThermodynamicOperations

# 1. Create system
system = SystemSrkEos(298.15, 100.0)
system.addComponent("methane", 0.5)
system.addComponent("n-heptane", 0.5)
system.setMixingRule("classic")

# 2. Flash
ops = ThermodynamicOperations(system)
ops.TPflash()

# 3. Set Viscosity Model
# Note: Phase index 0 is usually gas, 1 is oil/liquid
system.getPhase(1).getPhysicalProperties().setViscosityModel("LBC")
system.initProperties()
print("LBC Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")

system.getPhase(1).getPhysicalProperties().setViscosityModel("PFCT")
system.initProperties()
print("PFCT Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")

system.getPhase(1).getPhysicalProperties().setViscosityModel("friction theory")
system.initProperties()
print("Friction Theory Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")
```

## Mathematical Details

### 1. Lohrenz-Bray-Clark (LBC) Model
The LBC model calculates the viscosity of a fluid ($\eta$) as the sum of a low-pressure gas contribution ($\eta^*$) and a dense-fluid contribution ($\eta_{dense}$):

$$ \eta = \eta^* + \frac{\eta_{dense}}{\xi_m} $$

where $\xi_m$ is the mixture viscosity parameter:

$$ \xi_m = \frac{T_{cm}^{1/6}}{M_m^{1/2} P_{cm}^{2/3}} $$

The dense-fluid contribution is a function of the reduced density $\rho_r = \rho_m / \rho_{cm}$:

$$ [(\eta - \eta^*) \xi_m + 10^{-4}]^{1/4} = a_0 + a_1 \rho_r + a_2 \rho_r^2 + a_3 \rho_r^3 + a_4 \rho_r^4 $$

**Mixing Rules:**
*   $T_{cm} = \sum_i x_i T_{ci}$
*   $P_{cm} = \sum_i x_i P_{ci}$ (Note: LBC typically uses specific mixing rules for $T_c, P_c$ involving $V_c$, but NeqSim implementations may vary. The critical volume mixing rule is key.)
*   $M_m = \sum_i x_i M_i$
*   **Critical Volume ($V_{cm}$)**:
    $$ V_{cm} = \sum_{i} x_i V_{ci} $$
    *(Note: This linear mixing rule replaces the previous cubic root rule $\left(\sum x_i V_{ci}^{1/3}\right)^3$ for better heavy oil prediction.)*

### 2. Corresponding States Principle (CSP)
The CSP model uses the Corresponding States Principle to relate the viscosity of a mixture to that of a reference substance (typically Methane) at a corresponding state ($T_0, P_0$).

**Viscosity Mapping:**
$$ \eta_{mix}(T, P) = \eta_{ref}(T_0, P_0) \cdot F_{\eta} \cdot \frac{\alpha_{mix}}{\alpha_{ref}} $$

where the scaling factor $F_{\eta}$ is:
$$ F_{\eta} = \left(\frac{T_{cm}}{T_{c,ref}}\right)^{-1/6} \left(\frac{P_{cm}}{P_{c,ref}}\right)^{2/3} \left(\frac{M_{mix}}{M_{ref}}\right)^{1/2} $$

**Corresponding State ($T_0, P_0$):**
The reference substance is evaluated at:
$$ T_0 = T \cdot \frac{T_{c,ref}}{T_{cm}} \cdot \frac{\alpha_{ref}}{\alpha_{mix}} $$
$$ P_0 = P \cdot \frac{P_{c,ref}}{P_{cm}} \cdot \frac{\alpha_{ref}}{\alpha_{mix}} $$

The parameter $\alpha$ accounts for deviations from the simple CSP and is typically a function of reduced density and molecular weight.

### 3. Friction Theory (f-theory)
The Friction Theory model separates the total viscosity into a dilute gas term ($\eta_0$) and a friction term ($\eta_f$):

$$ \eta = \eta_0 + \eta_f $$

The friction term is derived from mechanical friction concepts applied to the van der Waals repulsive and attractive pressure terms of the Equation of State (EOS):

$$ \eta_f = \kappa_r P_r + \kappa_a P_a + \kappa_{rr} P_r^2 $$

where:
*   $P_r$: Repulsive pressure term from the EOS (e.g., $RT/(v-b)$ for SRK/PR).
*   $P_a$: Attractive pressure term from the EOS (e.g., $-a/(v(v+b))$ for SRK).
*   $\kappa_r, \kappa_a, \kappa_{rr}$: Friction coefficients, which are functions of temperature.

This approach ensures that the viscosity model is consistent with the thermodynamic behavior predicted by the EOS, making it robust across a wide range of conditions, including high pressure and near-critical regions.
