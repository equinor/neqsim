package neqsim.thermo.system;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fluent builder for creating pre-configured thermodynamic fluid systems.
 *
 * <p>
 * Provides both a fluent builder API for custom fluids and static factory methods for common
 * industry fluid types. All fluids are returned as {@link SystemInterface} with the mixing rule
 * already set, ready for flash calculations.
 * </p>
 *
 * <h2>Fluent Builder:</h2>
 *
 * <pre>
 * SystemInterface fluid = FluidBuilder.create(273.15 + 25.0, 60.0).addComponent("methane", 0.85)
 *     .addComponent("ethane", 0.10).addComponent("propane", 0.05).withMixingRule("classic")
 *     .build();
 * </pre>
 *
 * <h2>Preset Fluids:</h2>
 *
 * <pre>
 * SystemInterface gas = FluidBuilder.leanNaturalGas(273.15 + 25.0, 60.0);
 * SystemInterface oil = FluidBuilder.typicalBlackOil(273.15 + 80.0, 200.0);
 * SystemInterface co2 = FluidBuilder.co2Rich(273.15 + 40.0, 100.0);
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see SystemInterface
 */
public class FluidBuilder implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FluidBuilder.class);

  /** Temperature in Kelvin. */
  private double temperatureK;

  /** Pressure in bara. */
  private double pressureBara;

  /** EOS type to use. */
  private EOSType eosType = EOSType.SRK;

  /** Mixing rule name (for string-based rules). */
  private String mixingRuleName = "classic";

  /** Mixing rule number (for numeric rules like CPA). */
  private int mixingRuleNumber = -1;

  /** Ordered component list (name, mole fraction). */
  private final List<ComponentEntry> components = new ArrayList<>();

  /** TBP fraction entries. */
  private final List<TBPEntry> tbpFractions = new ArrayList<>();

  /** Plus fraction entry (optional). */
  private TBPEntry plusFraction = null;

  /** Whether to enable multi-phase check. */
  private boolean multiPhaseCheck = false;

  /** Whether to enable solid phase check. */
  private boolean solidPhaseCheck = false;

  /** Number of lumped components for characterization. */
  private int numberOfLumpedComponents = -1;

  /**
   * Supported equation of state types.
   */
  public enum EOSType {
    /** Soave-Redlich-Kwong EOS. */
    SRK,
    /** Peng-Robinson EOS. */
    PR,
    /** SRK with CPA (Cubic Plus Association). */
    SRK_CPA,
    /** Peng-Robinson with CPA. */
    PR_CPA,
    /** Electrolyte CPA. */
    ELECTROLYTE_CPA,
    /** GERG-2008 reference EOS. */
    GERG2008,
    /** SRK with Peneloux volume correction. */
    SRK_PENELOUX,
    /** PR 1978 modification. */
    PR_1978,
    /**
     * Peng-Robinson with PR76 alpha function and Lee-Kesler BWR enthalpy (PR-LK).
     */
    PR_LK
  }

  /**
   * Creates a new FluidBuilder with the given conditions.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   */
  private FluidBuilder(double temperatureK, double pressureBara) {
    this.temperatureK = temperatureK;
    this.pressureBara = pressureBara;
  }

  /**
   * Creates a new fluent builder for a thermodynamic fluid.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return a new FluidBuilder instance
   */
  public static FluidBuilder create(double temperatureK, double pressureBara) {
    return new FluidBuilder(temperatureK, pressureBara);
  }

  /**
   * Adds a component with a mole fraction.
   *
   * @param name component name (e.g., "methane", "CO2")
   * @param moleFraction mole fraction (0 to 1)
   * @return this builder for chaining
   */
  public FluidBuilder addComponent(String name, double moleFraction) {
    components.add(new ComponentEntry(name, moleFraction));
    return this;
  }

  /**
   * Adds a TBP (True Boiling Point) fraction for oil characterization.
   *
   * @param name fraction name (e.g., "C7", "C8")
   * @param moleFraction mole fraction
   * @param molarMassKgPerMol molar mass in kg/mol
   * @param density density in g/cm3
   * @return this builder for chaining
   */
  public FluidBuilder addTBPFraction(String name, double moleFraction, double molarMassKgPerMol,
      double density) {
    tbpFractions.add(new TBPEntry(name, moleFraction, molarMassKgPerMol, density));
    return this;
  }

  /**
   * Adds a plus fraction (e.g., C20+) for oil characterization.
   *
   * @param name fraction name (e.g., "C20+")
   * @param moleFraction mole fraction
   * @param molarMassKgPerMol molar mass in kg/mol
   * @param density density in g/cm3
   * @return this builder for chaining
   */
  public FluidBuilder addPlusFraction(String name, double moleFraction, double molarMassKgPerMol,
      double density) {
    plusFraction = new TBPEntry(name, moleFraction, molarMassKgPerMol, density);
    return this;
  }

  /**
   * Selects the equation of state type.
   *
   * @param eosType the EOS type to use
   * @return this builder for chaining
   */
  public FluidBuilder withEOS(EOSType eosType) {
    this.eosType = eosType;
    return this;
  }

  /**
   * Sets the mixing rule by name (e.g., "classic", "HV", "WS").
   *
   * @param mixingRule the mixing rule name
   * @return this builder for chaining
   */
  public FluidBuilder withMixingRule(String mixingRule) {
    this.mixingRuleName = mixingRule;
    this.mixingRuleNumber = -1;
    return this;
  }

  /**
   * Sets the mixing rule by number (e.g., 10 for CPA).
   *
   * @param mixingRuleNumber the mixing rule number
   * @return this builder for chaining
   */
  public FluidBuilder withMixingRule(int mixingRuleNumber) {
    this.mixingRuleNumber = mixingRuleNumber;
    this.mixingRuleName = null;
    return this;
  }

  /**
   * Enables multi-phase check (needed for water-bearing systems).
   *
   * @return this builder for chaining
   */
  public FluidBuilder withMultiPhaseCheck() {
    this.multiPhaseCheck = true;
    return this;
  }

  /**
   * Enables solid phase check (for wax, hydrate, or solid precipitation).
   *
   * @return this builder for chaining
   */
  public FluidBuilder withSolidPhaseCheck() {
    this.solidPhaseCheck = true;
    return this;
  }

  /**
   * Sets the number of lumped components for C7+ characterization.
   *
   * @param numberOfLumpedComponents number of lumped pseudo-components
   * @return this builder for chaining
   */
  public FluidBuilder withLumpedComponents(int numberOfLumpedComponents) {
    this.numberOfLumpedComponents = numberOfLumpedComponents;
    return this;
  }

  /**
   * Builds the configured thermodynamic system.
   *
   * @return the configured SystemInterface, ready for flash calculations
   */
  public SystemInterface build() {
    SystemInterface system = createSystem();

    for (ComponentEntry comp : components) {
      system.addComponent(comp.name, comp.moleFraction);
    }

    for (TBPEntry tbp : tbpFractions) {
      system.addTBPfraction(tbp.name, tbp.moleFraction, tbp.molarMass, tbp.density);
    }

    if (plusFraction != null) {
      system.addPlusFraction(plusFraction.name, plusFraction.moleFraction, plusFraction.molarMass,
          plusFraction.density);
    }

    // Mixing rule must be set before characterization
    if (mixingRuleNumber >= 0) {
      system.setMixingRule(mixingRuleNumber);
    } else if (mixingRuleName != null) {
      system.setMixingRule(mixingRuleName);
    }

    if (numberOfLumpedComponents > 0) {
      system.getCharacterization().getLumpingModel()
          .setNumberOfLumpedComponents(numberOfLumpedComponents);
      system.getCharacterization().characterisePlusFraction();
    }

    if (multiPhaseCheck) {
      system.setMultiPhaseCheck(true);
    }

    if (solidPhaseCheck) {
      system.setSolidPhaseCheck(true);
    }

    logger.debug("FluidBuilder created {} with {} components", eosType, components.size());
    return system;
  }

  /**
   * Creates the appropriate SystemInterface implementation based on EOSType.
   *
   * @return a new SystemInterface instance
   */
  private SystemInterface createSystem() {
    switch (eosType) {
      case PR:
        return new SystemPrEos(temperatureK, pressureBara);
      case SRK_CPA:
        return new SystemSrkCPAstatoil(temperatureK, pressureBara);
      case PR_CPA:
        return new SystemPrCPA(temperatureK, pressureBara);
      case ELECTROLYTE_CPA:
        return new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
      case GERG2008:
        return new SystemGERG2008Eos(temperatureK, pressureBara);
      case SRK_PENELOUX:
        return new SystemSrkPenelouxEos(temperatureK, pressureBara);
      case PR_1978:
        return new SystemPrEos1978(temperatureK, pressureBara);
      case PR_LK:
        return new SystemPrLeeKeslerEos(temperatureK, pressureBara);
      case SRK:
      default:
        return new SystemSrkEos(temperatureK, pressureBara);
    }
  }

  // ============================================================
  // Static factory methods for common industry fluid types
  // ============================================================

  /**
   * Creates a lean natural gas (dry gas) with typical North Sea composition.
   *
   * <p>
   * Composition: CH4 (85%), C2H6 (8%), C3H8 (3%), iC4 (0.5%), nC4 (1%), N2 (1.5%), CO2 (1%). Uses
   * SRK EOS with classic mixing rule.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured lean gas fluid
   */
  public static SystemInterface leanNaturalGas(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).addComponent("methane", 0.85)
        .addComponent("ethane", 0.08).addComponent("propane", 0.03).addComponent("i-butane", 0.005)
        .addComponent("n-butane", 0.01).addComponent("nitrogen", 0.015).addComponent("CO2", 0.01)
        .withMixingRule("classic").build();
  }

  /**
   * Creates a rich natural gas (wet gas) with heavier components.
   *
   * <p>
   * Composition: CH4 (72%), C2H6 (10%), C3H8 (6%), iC4 (2%), nC4 (3%), iC5 (1%), nC5 (1%), N2 (1%),
   * CO2 (2%), nC6 (1%), nC8 (1%). Uses SRK EOS with classic mixing rule.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured rich gas fluid
   */
  public static SystemInterface richNaturalGas(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).addComponent("methane", 0.72)
        .addComponent("ethane", 0.10).addComponent("propane", 0.06).addComponent("i-butane", 0.02)
        .addComponent("n-butane", 0.03).addComponent("i-pentane", 0.01)
        .addComponent("n-pentane", 0.01).addComponent("nitrogen", 0.01).addComponent("CO2", 0.02)
        .addComponent("n-hexane", 0.01).addComponent("n-octane", 0.01).withMixingRule("classic")
        .build();
  }

  /**
   * Creates a typical black oil with C7+ characterization.
   *
   * <p>
   * Includes light ends plus TBP fractions for C7-C10 and a C20+ plus fraction. Uses PR EOS with
   * classic mixing rule and automatic characterization with 6 lumped components.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured black oil fluid
   */
  public static SystemInterface typicalBlackOil(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).withEOS(EOSType.PR).addComponent("methane", 0.30)
        .addComponent("ethane", 0.08).addComponent("propane", 0.06).addComponent("i-butane", 0.02)
        .addComponent("n-butane", 0.04).addComponent("i-pentane", 0.02)
        .addComponent("n-pentane", 0.03).addComponent("n-hexane", 0.04)
        .addTBPFraction("C7", 0.06, 92.0 / 1000.0, 0.727)
        .addTBPFraction("C8", 0.05, 104.0 / 1000.0, 0.749)
        .addTBPFraction("C9", 0.04, 119.0 / 1000.0, 0.768)
        .addTBPFraction("C10", 0.03, 133.0 / 1000.0, 0.786)
        .addPlusFraction("C20", 0.23, 350.0 / 1000.0, 0.88).withLumpedComponents(6)
        .withMixingRule("classic").build();
  }

  /**
   * Creates a CO2-rich stream typical for carbon capture and storage (CCS).
   *
   * <p>
   * Composition: CO2 (95%), N2 (2%), methane (2%), H2S (0.5%), water (0.5%). Uses SRK-CPA EOS
   * (mixing rule 10) with multi-phase check to handle water-CO2 phase behavior.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured CO2-rich fluid
   */
  public static SystemInterface co2Rich(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).withEOS(EOSType.SRK_CPA).addComponent("CO2", 0.95)
        .addComponent("nitrogen", 0.02).addComponent("methane", 0.02).addComponent("H2S", 0.005)
        .addComponent("water", 0.005).withMixingRule(10).withMultiPhaseCheck().build();
  }

  /**
   * Creates an acid gas stream with significant H2S and CO2 content.
   *
   * <p>
   * Composition: methane (70%), CO2 (10%), H2S (5%), ethane (5%), propane (3%), n-butane (2%),
   * water (2%), nitrogen (3%). Uses SRK-CPA EOS with multi-phase check.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured acid gas fluid
   */
  public static SystemInterface acidGas(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).withEOS(EOSType.SRK_CPA).addComponent("methane", 0.70)
        .addComponent("CO2", 0.10).addComponent("H2S", 0.05).addComponent("ethane", 0.05)
        .addComponent("propane", 0.03).addComponent("n-butane", 0.02).addComponent("water", 0.02)
        .addComponent("nitrogen", 0.03).withMixingRule(10).withMultiPhaseCheck().build();
  }

  /**
   * Creates a gas condensate fluid.
   *
   * <p>
   * Composition: CH4 (75%), C2H6 (7%), C3H8 (4%), iC4 (1.5%), nC4 (2%), iC5 (1%), nC5 (1%), nC6
   * (1.5%), plus C7-C10 TBP fractions and C15+ plus fraction. Uses SRK EOS.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured gas condensate fluid
   */
  public static SystemInterface gasCondensate(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).addComponent("methane", 0.75)
        .addComponent("ethane", 0.07).addComponent("propane", 0.04).addComponent("i-butane", 0.015)
        .addComponent("n-butane", 0.02).addComponent("i-pentane", 0.01)
        .addComponent("n-pentane", 0.01).addComponent("n-hexane", 0.015)
        .addTBPFraction("C7", 0.02, 92.0 / 1000.0, 0.727)
        .addTBPFraction("C8", 0.015, 104.0 / 1000.0, 0.749)
        .addTBPFraction("C9", 0.01, 119.0 / 1000.0, 0.768)
        .addTBPFraction("C10", 0.008, 133.0 / 1000.0, 0.786)
        .addPlusFraction("C15", 0.027, 220.0 / 1000.0, 0.84).withMixingRule("classic").build();
  }

  /**
   * Creates a dry export gas with simple composition.
   *
   * <p>
   * Composition: CH4 (92%), C2H6 (4%), C3H8 (1.5%), N2 (1.5%), CO2 (1%). Uses SRK EOS. Suitable for
   * pipeline transport calculations.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured dry gas fluid
   */
  public static SystemInterface dryExportGas(double temperatureK, double pressureBara) {
    return create(temperatureK, pressureBara).addComponent("methane", 0.92)
        .addComponent("ethane", 0.04).addComponent("propane", 0.015).addComponent("nitrogen", 0.015)
        .addComponent("CO2", 0.01).withMixingRule("classic").build();
  }

  // ============================================================
  // Internal data classes
  // ============================================================

  /**
   * Internal holder for component name and mole fraction.
   */
  private static class ComponentEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final double moleFraction;

    /**
     * Creates a component entry.
     *
     * @param name component name
     * @param moleFraction mole fraction
     */
    ComponentEntry(String name, double moleFraction) {
      this.name = name;
      this.moleFraction = moleFraction;
    }
  }

  /**
   * Internal holder for TBP fraction data.
   */
  private static class TBPEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final double moleFraction;
    final double molarMass;
    final double density;

    /**
     * Creates a TBP entry.
     *
     * @param name fraction name
     * @param moleFraction mole fraction
     * @param molarMass molar mass in kg/mol
     * @param density density in g/cm3
     */
    TBPEntry(String name, double moleFraction, double molarMass, double density) {
      this.name = name;
      this.moleFraction = moleFraction;
      this.molarMass = molarMass;
      this.density = density;
    }
  }
}
