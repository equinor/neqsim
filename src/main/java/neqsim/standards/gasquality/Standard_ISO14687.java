package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of ISO 14687 - Hydrogen fuel quality - Product specification.
 *
 * <p>
 * ISO 14687 specifies quality requirements for hydrogen used as fuel for:
 * </p>
 * <ul>
 * <li>Grade A: PEM fuel cell road vehicles (strictest purity)</li>
 * <li>Grade B: PEM fuel cell industrial vehicles (forklifts etc.)</li>
 * <li>Grade C: PEM fuel cell stationary applications</li>
 * <li>Grade D: Internal combustion engines</li>
 * <li>Grade E: Gas turbines and other applications</li>
 * </ul>
 *
 * <p>
 * Key specifications for Grade A (PEM fuel cell road vehicles - ISO 14687:2019):
 * </p>
 * <ul>
 * <li>Minimum hydrogen purity: 99.97% (mole basis)</li>
 * <li>Maximum total non-hydrogen gases: 300 umol/mol</li>
 * <li>Maximum water: 5 umol/mol</li>
 * <li>Maximum total hydrocarbons (C1 basis): 2 umol/mol</li>
 * <li>Maximum oxygen: 5 umol/mol</li>
 * <li>Maximum helium: 300 umol/mol</li>
 * <li>Maximum nitrogen: 300 umol/mol</li>
 * <li>Maximum argon: 300 umol/mol</li>
 * <li>Maximum CO2: 2 umol/mol</li>
 * <li>Maximum CO: 0.2 umol/mol</li>
 * <li>Maximum total sulfur: 0.004 umol/mol</li>
 * <li>Maximum formaldehyde (HCHO): 0.2 umol/mol</li>
 * <li>Maximum formic acid (HCOOH): 0.2 umol/mol</li>
 * <li>Maximum ammonia: 0.1 umol/mol</li>
 * <li>Maximum total halogenated compounds: 0.05 umol/mol</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO14687 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO14687.class);

  /** Hydrogen grade: "A", "B", "C", "D", "E". */
  private String grade = "A";

  /** Hydrogen purity in mol%. */
  private double h2Purity = 0.0;

  /** Total non-hydrogen content in umol/mol (ppmv). */
  private double totalNonH2 = 0.0;

  /** Water content in umol/mol. */
  private double waterContent = 0.0;

  /** Total hydrocarbons in umol/mol. */
  private double totalHC = 0.0;

  /** Oxygen content in umol/mol. */
  private double o2Content = 0.0;

  /** Nitrogen content in umol/mol. */
  private double n2Content = 0.0;

  /** CO2 content in umol/mol. */
  private double co2Content = 0.0;

  /** CO content in umol/mol. */
  private double coContent = 0.0;

  /** Total sulfur in umol/mol. */
  private double totalSulfur = 0.0;

  /** Helium content in umol/mol. */
  private double heContent = 0.0;

  /** Argon content in umol/mol. */
  private double arContent = 0.0;

  // Grade A (PEM fuel cell vehicles) specification limits in umol/mol
  /** Max water for Grade A. */
  private static final double GRADE_A_WATER_MAX = 5.0;
  /** Max total HC for Grade A. */
  private static final double GRADE_A_HC_MAX = 2.0;
  /** Max O2 for Grade A. */
  private static final double GRADE_A_O2_MAX = 5.0;
  /** Max He for Grade A. */
  private static final double GRADE_A_HE_MAX = 300.0;
  /** Max N2 for Grade A. */
  private static final double GRADE_A_N2_MAX = 300.0;
  /** Max Ar for Grade A. */
  private static final double GRADE_A_AR_MAX = 300.0;
  /** Max CO2 for Grade A. */
  private static final double GRADE_A_CO2_MAX = 2.0;
  /** Max CO for Grade A. */
  private static final double GRADE_A_CO_MAX = 0.2;
  /** Max total sulfur for Grade A. */
  private static final double GRADE_A_SULFUR_MAX = 0.004;
  /** Min H2 purity for Grade A in mol%. */
  private static final double GRADE_A_PURITY_MIN = 99.97;
  /** Max total non-H2 for Grade A. */
  private static final double GRADE_A_TOTAL_MAX = 300.0;

  // Grade D (ICE) specification limits in umol/mol - less strict
  /** Min H2 purity for Grade D in mol%. */
  private static final double GRADE_D_PURITY_MIN = 99.90;
  /** Max water for Grade D. */
  private static final double GRADE_D_WATER_MAX = 100.0;
  /** Max total HC for Grade D. */
  private static final double GRADE_D_HC_MAX = 100.0;
  /** Max O2 for Grade D. */
  private static final double GRADE_D_O2_MAX = 50.0;

  /**
   * Constructor for Standard_ISO14687.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO14687(SystemInterface thermoSystem) {
    super("Standard_ISO14687", "Hydrogen fuel quality - Product specification", thermoSystem);
  }

  /**
   * Constructor for Standard_ISO14687 with grade.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param grade hydrogen grade: "A", "B", "C", "D", or "E"
   */
  public Standard_ISO14687(SystemInterface thermoSystem, String grade) {
    this(thermoSystem);
    this.grade = grade;
  }

  /**
   * Sets the hydrogen grade.
   *
   * @param grade "A" through "E"
   */
  public void setGrade(String grade) {
    this.grade = grade;
  }

  /**
   * Gets the hydrogen grade.
   *
   * @return the grade string
   */
  public String getGrade() {
    return grade;
  }

  /**
   * Helper to get mole fraction safely.
   *
   * @param componentName the component name
   * @return mole fraction or 0 if not present
   */
  private double getMoleFraction(String componentName) {
    if (thermoSystem.getPhase(0).hasComponent(componentName)) {
      return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      thermoSystem.init(0);

      double h2MoleFrac = getMoleFraction("hydrogen");
      h2Purity = h2MoleFrac * 100.0;

      // Convert mol fractions to umol/mol (ppmv)
      double toUmol = 1.0e6;
      waterContent = getMoleFraction("water") * toUmol;
      o2Content = getMoleFraction("oxygen") * toUmol;
      n2Content = getMoleFraction("nitrogen") * toUmol;
      co2Content = getMoleFraction("CO2") * toUmol;
      coContent = getMoleFraction("CO") * toUmol;
      heContent = getMoleFraction("helium") * toUmol;
      arContent = getMoleFraction("argon") * toUmol;

      // Total sulfur
      totalSulfur = getMoleFraction("H2S") * toUmol;

      // Total hydrocarbons
      totalHC = 0.0;
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        String type = thermoSystem.getPhase(0).getComponent(i).getComponentType();
        String name = thermoSystem.getPhase(0).getComponent(i).getName();
        if ("HC".equals(type) || "methane".equals(name) || "ethane".equals(name)
            || "propane".equals(name)) {
          totalHC += thermoSystem.getPhase(0).getComponent(i).getz() * toUmol;
        }
      }

      // Total non-hydrogen
      totalNonH2 = (1.0 - h2MoleFrac) * toUmol;

    } catch (Exception ex) {
      logger.error("ISO 14687 calculation failed", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("purity".equals(returnParameter) || "H2".equals(returnParameter)) {
      return h2Purity;
    }
    if ("totalNonH2".equals(returnParameter)) {
      return totalNonH2;
    }
    if ("water".equals(returnParameter)) {
      return waterContent;
    }
    if ("totalHC".equals(returnParameter)) {
      return totalHC;
    }
    if ("O2".equals(returnParameter)) {
      return o2Content;
    }
    if ("N2".equals(returnParameter)) {
      return n2Content;
    }
    if ("CO2".equals(returnParameter)) {
      return co2Content;
    }
    if ("CO".equals(returnParameter)) {
      return coContent;
    }
    if ("totalSulfur".equals(returnParameter)) {
      return totalSulfur;
    }
    if ("He".equals(returnParameter)) {
      return heContent;
    }
    if ("Ar".equals(returnParameter)) {
      return arContent;
    }
    return h2Purity;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("purity".equals(returnParameter) || "H2".equals(returnParameter)) {
      return "mol%";
    }
    return "umol/mol";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if ("A".equals(grade) || "B".equals(grade) || "C".equals(grade)) {
      return h2Purity >= GRADE_A_PURITY_MIN && waterContent <= GRADE_A_WATER_MAX
          && totalHC <= GRADE_A_HC_MAX && o2Content <= GRADE_A_O2_MAX && n2Content <= GRADE_A_N2_MAX
          && co2Content <= GRADE_A_CO2_MAX && coContent <= GRADE_A_CO_MAX
          && totalSulfur <= GRADE_A_SULFUR_MAX && totalNonH2 <= GRADE_A_TOTAL_MAX;
    } else if ("D".equals(grade)) {
      return h2Purity >= GRADE_D_PURITY_MIN && waterContent <= GRADE_D_WATER_MAX
          && totalHC <= GRADE_D_HC_MAX && o2Content <= GRADE_D_O2_MAX;
    } else {
      // Grade E: most relaxed - 98% purity minimum
      return h2Purity >= 98.0;
    }
  }
}
