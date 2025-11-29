# Fluid Characterization in NeqSim

Real reservoir fluids often contain a complex mixture of heavy hydrocarbons (C7+) that cannot be represented by standard pure components. NeqSim provides a robust characterization framework to model these fluids using TBP (True Boiling Point) fractions and Plus fractions.

## 1. Adding Heavy Fractions

You can add heavy fractions to a system using two primary methods: `addTBPfraction` and `addPlusFraction`.

### 1.1 TBP Fractions
Use `addTBPfraction` when you have data for specific carbon number cuts (e.g., C7, C8, C9) with defined properties.

```java
// addTBPfraction(name, moles, molarMass_kg_mol, density_kg_m3)
system.addTBPfraction("C7", 1.0, 0.092, 0.73); 
system.addTBPfraction("C8", 1.0, 0.104, 0.76);
```

### 1.2 Plus Fractions
Use `addPlusFraction` for the final residue or "plus" fraction (e.g., C10+, C20+) where you only have average properties.

```java
// addPlusFraction(name, moles, molarMass_kg_mol, density_kg_m3)
system.addPlusFraction("C10+", 10.0, 0.250, 0.85);
```

## 2. Characterization Process

After adding the components, you must run the characterization routine to split the plus fraction into pseudo-components and estimate their critical properties (Tc, Pc, w).

### 2.1 Setting the Model
NeqSim supports several characterization models. The most common is the Pedersen model.

```java
// Set the TBP Model (affects how TBP fractions are treated)
system.getCharacterization().setTBPModel("PedersenSRK"); 

// Set the Plus Fraction Model (affects how the plus fraction is split)
system.getCharacterization().setPlusFractionModel("Pedersen");
```

#### Available Plus Fraction Models
*   **"Pedersen"**: The standard Pedersen model (exponential distribution). Default.
*   **"Pedersen Heavy Oil"**: Adjusted for heavy oils (extends to C200).
*   **"Whitson Gamma Model"**: Uses a Gamma distribution for molar mass and Watson UOP for density.
    *   *Note*: The shape factor ($\alpha$) and minimum molar mass ($\eta$) are currently fixed to default values ($\alpha=1.0$, $\eta=90$ g/mol) in the standard API.

### 2.2 Running Characterization
Once models are set, execute the characterization.

```java
system.getCharacterization().characterisePlusFraction();
```

This process will:
1.  Extrapolate the molar distribution to C80+.
2.  Calculate properties for each carbon number.
3.  Group them into pseudo-components (if lumping is enabled).

## 3. Lumping (ModelLumping)

To reduce simulation time, it is often necessary to group the many characterized components into a smaller number of "lumped" pseudo-components.

### 3.1 Configuring Lumping
You can control the lumping behavior via the `LumpingModel`.

```java
// Set the lumping method (Default is "PVTlumpingModel")
system.getCharacterization().setLumpingModel("PVTlumpingModel");

// Configure the number of pseudo-components to generate
system.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);
```

### 3.2 Full Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CharacterizationExample {
    public static void main(String[] args) {
        // 1. Create System
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
        fluid.addComponent("nitrogen", 0.5);
        fluid.addComponent("CO2", 1.0);
        fluid.addComponent("methane", 60.0);
        fluid.addComponent("ethane", 5.0);
        fluid.addComponent("propane", 3.0);
        
        // 2. Add Heavy Fractions
        fluid.addTBPfraction("C6", 1.0, 0.086, 0.66);
        fluid.addTBPfraction("C7", 2.0, 0.092, 0.73);
        fluid.addTBPfraction("C8", 2.0, 0.104, 0.76);
        fluid.addTBPfraction("C9", 1.0, 0.118, 0.78);
        fluid.addPlusFraction("C10+", 15.0, 0.280, 0.84); // The Plus Fraction
        
        // 3. Configure Characterization
        fluid.getCharacterization().setTBPModel("PedersenSRK");
        fluid.getCharacterization().setPlusFractionModel("Pedersen");
        
        // 4. Configure Lumping
        // We want to lump the C10+ distribution into 5 pseudo-components
        fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
        fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(5);
        
        // 5. Run Characterization
        fluid.getCharacterization().characterisePlusFraction();
        
        // 6. Use the Fluid
        fluid.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        fluid.prettyPrint();
    }
}
```

## 4. Advanced Options

*   **Heavy Oil**: For very heavy oils, use `setPlusFractionModel("Pedersen Heavy Oil")`.
*   **Whitson Gamma**: Use `setPlusFractionModel("Whitson Gamma")` if you have specific gamma distribution parameters.
*   **No Lumping**: To keep all individual carbon number components (C6, C7... C80), use `setLumpingModel("no lumping")`. Note that this will result in a system with many components, which is slower to simulate.
