package neqsim.thermo.system;

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
      phaseArray[i] = new neqsim.thermo.phase.PhaseWhitsonSoreide();
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
        neqsim.thermo.phase.PhaseInterface aqueousPhase =
            this.getPhase(neqsim.thermo.phase.PhaseType.AQUEOUS);
        if (aqueousPhase != null) {
          double massKgWater = aqueousPhase.getNumberOfMolesInPhase() * aqueousPhase.getMolarMass();
          if (massKgWater > 0.0) {
            salinityConcentration = systemSalinity / massKgWater;
            errorSalinityConcentration = Math.abs(aqueousPhase.getSalinityConcentration() - salinityConcentration);
            if (errorSalinityConcentration > 1e-6) {

              aqueousPhase.setSalinityConcentration(salinityConcentration);
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
              if (comp instanceof neqsim.thermo.component.ComponentEosInterface) {
                neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface attractiveTerm =
                    ((neqsim.thermo.component.ComponentEosInterface) comp).getAttractiveTerm();
                if (attractiveTerm instanceof neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson) {
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
