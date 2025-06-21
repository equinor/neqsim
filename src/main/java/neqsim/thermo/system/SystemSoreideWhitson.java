package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseSoreideWhitson;

/**
 * This class defines a thermodynamic system using the Søreide-Whitson Peng-Robinson EoS (modified
 * alpha and mixing rule).
 */
public class SystemSoreideWhitson extends SystemPrEos1978 {
  private static final long serialVersionUID = 1000L;
  private double salinity = 0.0; // salinity in mole/sec

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
   * @param saltType the type of salt (e.g., "Na2SO4", "MgSO4", "Mg(NO3)2", "NaCl", "NaNO3", "KCl",
   *        "KNO3")
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
            errorSalinityConcentration =
                Math.abs(((PhaseSoreideWhitson) aqueousPhase).getSalinityConcentration()
                    - salinityConcentration);
            if (errorSalinityConcentration > 1e-6) {

              ((PhaseSoreideWhitson) aqueousPhase).setSalinityConcentration(salinityConcentration);
              // Set salinityConcentration for each component's attractive term if SoreideWhitso

              updatedSalinity = true;
            }
          }
          // Assign the calculated salinityConcentration to every SoreideWhitson attractive term in
          // all phases
          for (int phaseN = 0; phaseN < this.getNumberOfPhases(); phaseN++) {
            neqsim.thermo.phase.PhaseInterface phase = this.getPhase(phaseN);
            for (int compN = 0; compN < phase.getNumberOfComponents(); compN++) {
              neqsim.thermo.component.ComponentInterface comp = phase.getComponent(compN);
              if (comp != null && comp.getClass().getName()
                  .equals("neqsim.thermo.component.ComponentEosInterface")) {
                neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface attractiveTerm =
                    ((neqsim.thermo.component.ComponentEosInterface) comp).getAttractiveTerm();
                if (attractiveTerm != null && attractiveTerm.getClass().getName().equals(
                    "neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson")) {
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
