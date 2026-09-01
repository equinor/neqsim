package neqsim.thermo.system;

import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;
import neqsim.thermo.phase.PhaseSoreideWhitson;

/**
 * This class defines a thermodynamic system using the Søreide-Whitson Peng-Robinson EoS (modified alpha and mixing
 * rule).
 *
 * @author sviat
 */
public class SystemSoreideWhitson extends SystemPrEos1978 {
  private static final long serialVersionUID = 1000L;
  private double salinity = 0.0; // salinity in mole/sec
  private SoreideWhitsonParameterization aqueousCO2Parameterization = SoreideWhitsonParameterization.LEGACY;

  /**
   * Default constructor: 298.15 K, 1.0 bara, no solid check.
   */
  public SystemSoreideWhitson() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor with temperature and pressure.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bara
   */
  public SystemSoreideWhitson(double T, double P) {
    this(T, P, false);
  }

  /**
   * Full constructor.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bara
   * @param checkForSolids check for solids
   */
  public SystemSoreideWhitson(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Soreide-Whitson-PR-EoS";
    attractiveTermNumber = 20;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new neqsim.thermo.phase.PhaseSoreideWhitson();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /**
   * Add the salinity value.
   *
   * @param value the salinity value
   * @param unit the unit of the value ("mole/hr" or "mole/sec")
   */
  public void addSalinity(double value, String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Unit cannot be null");
    }
    switch (unit.toLowerCase()) {
    case "mole/hr":
      this.salinity = this.salinity + value / 3600.0;
      break;
    case "mole/sec":
      this.salinity = this.salinity + value;
      break;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Add the salinity value for a specific salt type and unit.
   *
   * @param saltType the type of salt (e.g., "Na2SO4", "MgSO4", "Mg(NO3)2", "NaCl", "NaNO3", "KCl", "KNO3")
   * @param value the amount of salt added
   * @param unit the unit of the value ("mole/hr" or "mole/sec")
   */
  public void addSalinity(String saltType, double value, String unit) {
    if (saltType == null) {
      throw new IllegalArgumentException("Salt type cannot be null");
    }
    if (unit == null) {
      throw new IllegalArgumentException("Unit cannot be null");
    }
    double valueInMoleSec;
    switch (unit.toLowerCase()) {
    case "mole/hr":
      valueInMoleSec = value / 3600.0;
      break;
    case "mole/sec":
      valueInMoleSec = value;
      break;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
    switch (saltType.trim().toUpperCase()) {
    case "Na2SO4":
      this.salinity = this.salinity + 3.0 * valueInMoleSec;
      break;
    case "MgSO4":
      this.salinity = this.salinity + 2.75 * valueInMoleSec;
      break;
    case "Mg(NO3)2":
      this.salinity = this.salinity + 1.3 * valueInMoleSec;
      break;
    case "NaCl":
      this.salinity = this.salinity + 1.0 * valueInMoleSec;
      break;
    case "NaNO3":
      this.salinity = this.salinity + 0.6 * valueInMoleSec;
      break;
    case "KCl":
      this.salinity = this.salinity + 0.5 * valueInMoleSec;
      break;
    case "KNO3":
      this.salinity = this.salinity + 0.3 * valueInMoleSec;
      break;
    default:
      throw new IllegalArgumentException("Unsupported salt type: " + saltType);
    }
  }

  /**
   * Set the salinity value.
   *
   * @param value the salinity value
   * @param unit the unit of the value ("mole/hr" or "mole/sec")
   */
  public void setSalinity(double value, String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Unit cannot be null");
    }
    switch (unit.toLowerCase()) {
    case "mole/hr":
      this.salinity = value / 3600.0;
      break;
    case "mole/sec":
      this.salinity = value;
      break;
    default:
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Get the salinity value in mole/sec.
   *
   * @return salinity in mole/sec
   */
  public double getSalinity() {
    return this.salinity;
  }

  /**
   * Select the Soreide-Whitson binary-interaction parameterization.
   *
   * <p>
   * The default is {@link SoreideWhitsonParameterization#LEGACY}, which preserves historical NeqSim results. Select
   * {@link SoreideWhitsonParameterization#CHABAB_2019} explicitly for the modified CO2 correlation published by Chabab
   * et al. (2019), or {@link SoreideWhitsonParameterization#BURGOYNE_NIELSEN_2026} for the 2026 drop-in water-gas
   * parameter set.
   *
   * @param parameterization parameterization to use
   * @throws IllegalArgumentException if {@code parameterization} is null
   */
  public void setSoreideWhitsonParameterization(SoreideWhitsonParameterization parameterization) {
    if (parameterization == null) {
      throw new IllegalArgumentException("Soreide-Whitson parameterization cannot be null");
    }
    aqueousCO2Parameterization = parameterization;
    for (int phaseNumber = 0; phaseNumber < phaseArray.length; phaseNumber++) {
      if (phaseArray[phaseNumber] instanceof PhaseSoreideWhitson) {
        ((PhaseSoreideWhitson) phaseArray[phaseNumber]).setSoreideWhitsonParameterization(parameterization);
      }
    }
  }

  /**
   * Select the Soreide-Whitson binary-interaction parameterization by name.
   *
   * <p>
   * This overload is convenient for Python/JPype callers.
   *
   * @param parameterizationName {@code LEGACY}, {@code CHABAB_2019}, {@code BURGOYNE_NIELSEN_2026}, or a supported
   * alias
   * @throws IllegalArgumentException if the name is null or unsupported
   */
  public void setSoreideWhitsonParameterization(String parameterizationName) {
    setSoreideWhitsonParameterization(SoreideWhitsonParameterization.byName(parameterizationName));
  }

  /**
   * Get the selected Soreide-Whitson binary-interaction parameterization.
   *
   * @return selected parameterization
   */
  public SoreideWhitsonParameterization getSoreideWhitsonParameterization() {
    return aqueousCO2Parameterization;
  }

  /**
   * Select the aqueous CO2-water parameterization using the historical API name.
   *
   * @param parameterization parameterization to use
   */
  public void setAqueousCO2Parameterization(SoreideWhitsonParameterization parameterization) {
    setSoreideWhitsonParameterization(parameterization);
  }

  /**
   * Select the parameterization by name using the historical API name.
   *
   * @param parameterizationName parameterization name
   */
  public void setAqueousCO2Parameterization(String parameterizationName) {
    setSoreideWhitsonParameterization(parameterizationName);
  }

  /**
   * Get the parameterization using the historical API name.
   *
   * @return selected parameterization
   */
  public SoreideWhitsonParameterization getAqueousCO2Parameterization() {
    return getSoreideWhitsonParameterization();
  }

  /** {@inheritDoc} */
  @Override
  public void clearAll() {
    super.clearAll();
    setSoreideWhitsonParameterization(aqueousCO2Parameterization);
  }

  /**
   * calcSalinity.
   *
   * @return a boolean
   */
  public boolean calcSalinity() {
    boolean updatedSalinity = false;
    double systemSalinity = this.getSalinity();
    double salinityConcentration = 0.0;
    double errorSalinityConcentration = 0.0;
    for (int i = 0; i < this.getNumberOfPhases(); i++) {
      if (systemSalinity > 0.0) {
        // Check for aqueous phase
        neqsim.thermo.phase.PhaseInterface aqueousPhase;
        try {
          aqueousPhase = this.getPhase(neqsim.thermo.phase.PhaseType.AQUEOUS);
        } catch (Exception e) {
          aqueousPhase = null;
        }
        if (aqueousPhase != null) {
          double massKgWater = aqueousPhase.getNumberOfMolesInPhase() * aqueousPhase.getMolarMass();
          if (massKgWater > 0.0) {
            salinityConcentration = systemSalinity / massKgWater;
            errorSalinityConcentration = Math
                .abs(((PhaseSoreideWhitson) aqueousPhase).getSalinityConcentration() - salinityConcentration);
            if (errorSalinityConcentration > 1e-6) {
              ((PhaseSoreideWhitson) aqueousPhase).setSalinityConcentration(salinityConcentration);
              // Set salinityConcentration for each component's attractive
              // term if SoreideWhitso

              updatedSalinity = true;
            }
          }
          // Assign the calculated salinityConcentration to every SoreideWhitson
          // attractive term in
          // all phases
          for (int phaseN = 0; phaseN < this.getNumberOfPhases(); phaseN++) {
            neqsim.thermo.phase.PhaseInterface phase = this.getPhase(phaseN);
            for (int compN = 0; compN < phase.getNumberOfComponents(); compN++) {
              neqsim.thermo.component.ComponentInterface comp = phase.getComponent(compN);
              if (comp != null && comp.getClass().getName().equals("neqsim.thermo.component.ComponentEosInterface")) {
                neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface attractiveTerm = comp
                    .getAttractiveTerm();
                if (attractiveTerm != null && attractiveTerm.getClass().getName()
                    .equals("neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson")) {
                  ((neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson) attractiveTerm)
                      .setSalinityFromPhase(salinityConcentration);
                }
              }
            }
          }
        }
      }
    }
    return updatedSalinity;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSoreideWhitson clone() {
    SystemSoreideWhitson clonedSystem = null;
    try {
      clonedSystem = (SystemSoreideWhitson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
