package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * checkScalePotential class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CheckScalePotential extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CheckScalePotential.class);

  String saltName;
  int phaseNumber = 1;
  String[][] resultTable = null;

  /**
   * <p>
   * Constructor for checkScalePotential.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumber a int
   */
  public CheckScalePotential(SystemInterface system, int phaseNumber) {
    super(system);
    this.phaseNumber = phaseNumber;
    logger.info("ok ");
  }

  /**
   * Maximum absolute value for ln(SR) to prevent overflow. exp(LN_SR_CLAMP) is approx 1e30.
   */
  private static final double LN_SR_CLAMP = 69.0;

  /**
   * Minimum meaningful molality (mol/kg). Below this, IAP is effectively zero.
   */
  private static final double MIN_MOLALITY = 1e-30;

  /** {@inheritDoc} */
  @Override
  public void run() {
    double ksp = 0.0;

    String saltName = "";
    String name1 = "";
    String name2 = "";
    int numb = 0;
    double stoc1 = 1.0;
    double stoc2 = 1.0;

    double numberOfMolesMEG = 0.0;

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM compsalt")) {
      if (system.getPhase(phaseNumber).hasComponent("MEG")) {
        numberOfMolesMEG =
            system.getPhase(phaseNumber).getComponent("MEG").getNumberOfMolesInPhase();
        system.addComponent("MEG", -numberOfMolesMEG * 0.9999, phaseNumber);
        system.addComponent("water", numberOfMolesMEG, phaseNumber);
        system.init(1);
        system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
      }

      // Count rows first to size the result table dynamically
      java.util.List<String[]> results = new java.util.ArrayList<String[]>();
      results.add(new String[] {"Salt", "relative solubility", ""});

      while (dataSet.next()) {
        saltName = dataSet.getString("SaltName").trim();
        name1 = dataSet.getString("ion1").trim();
        name2 = dataSet.getString("ion2").trim();

        stoc1 = Double.parseDouble(dataSet.getString("stoc1"));
        stoc2 = Double.parseDouble(dataSet.getString("stoc2"));
        // Temperature in Kelvin from system
        double temperatureK = system.getPhase(phaseNumber).getTemperature();
        // Ksp correlation: lnKsp = A/T + B + C*ln(T) + D*T + E/T² (T in Kelvin)
        double lnKsp = Double.parseDouble(dataSet.getString("Kspwater")) / temperatureK
            + Double.parseDouble(dataSet.getString("Kspwater2"))
            + Math.log(temperatureK) * Double.parseDouble(dataSet.getString("Kspwater3"))
            + temperatureK * Double.parseDouble(dataSet.getString("Kspwater4"))
            + Double.parseDouble(dataSet.getString("Kspwater5")) / (temperatureK * temperatureK);
        ksp = Math.exp(lnKsp);

        // Read molar volume change for pressure correction (cm³/mol)
        double vDelta = 0.0;
        try {
          vDelta = Double.parseDouble(dataSet.getString("Vdelta"));
        } catch (Exception ex2) {
          vDelta = 0.0;
        }

        if (saltName.equals("NaCl")) {
          // NaCl Ksp correlation using temperature in Kelvin
          ksp = 92.78 - 0.407 * temperatureK + 0.000747 * temperatureK * temperatureK;
        }
        if (saltName.equals("CaCO3")) {
          // CaCO3 (calcite) Ksp correlation from Plummer & Busenberg (1982)
          double log10Ksp = -171.9065 - 0.077993 * temperatureK + 2839.319 / temperatureK
              + 71.595 * Math.log10(temperatureK);
          ksp = Math.pow(10.0, log10Ksp);
        }
        if (saltName.equals("FeCO3")) {
          // FeCO3 (siderite) Ksp correlation from Greenberg & Tomson (1992)
          double log10Ksp = -59.3498 - 0.041377 * temperatureK + 2.1963 / temperatureK
              + 24.5724 * Math.log10(temperatureK) + 2.518e-5 * temperatureK * temperatureK;
          ksp = Math.pow(10.0, log10Ksp);
        }
        if (saltName.equals("FeS")) {
          if (!system.getPhase(phaseNumber).hasComponent("H3O+")) {
            continue;
          }
          int waterompNumb =
              system.getPhase(phaseNumber).getComponent("water").getComponentNumber();
          double h3ox = system.getPhase(phaseNumber).getComponent("H3O+").getx()
              / (system.getPhase(phaseNumber).getComponent(waterompNumb).getx()
                  * system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass());
          ksp *= h3ox;
        }

        // Pressure correction: ln(Ksp(P)/Ksp(P0)) = -ΔV°*(P-P0)/(R*T)
        double pressureBara = system.getPhase(phaseNumber).getPressure();
        if (Math.abs(vDelta) > 1e-10 && pressureBara > 1.013) {
          double R_cm3bar = 83.1446;
          double deltaPbar = pressureBara - 1.01325;
          double lnCorrection = -vDelta * deltaPbar / (R_cm3bar * temperatureK);
          // Clamp pressure correction to avoid extreme Ksp values
          if (lnCorrection > LN_SR_CLAMP) {
            lnCorrection = LN_SR_CLAMP;
          } else if (lnCorrection < -LN_SR_CLAMP) {
            lnCorrection = -LN_SR_CLAMP;
          }
          ksp *= Math.exp(lnCorrection);
        }

        // Validate Ksp: must be positive and finite
        if (ksp <= 0.0 || Double.isNaN(ksp) || Double.isInfinite(ksp)) {
          logger.warn("Invalid Ksp for " + saltName + ": " + ksp + " at T=" + temperatureK
              + " K, skipping");
          continue;
        }

        if (system.getPhase(phaseNumber).hasComponent(name1)
            && system.getPhase(phaseNumber).hasComponent(name2)) {
          numb++;
          logger.info("reaction added: " + name1 + " " + name2);
          logger.info("theoretic Ksp = " + ksp);

          int compNumb1 = system.getPhase(phaseNumber).getComponent(name1).getComponentNumber();
          int compNumb2 = system.getPhase(phaseNumber).getComponent(name2).getComponentNumber();
          int waterompNumb =
              system.getPhase(phaseNumber).getComponent("water").getComponentNumber();

          double xWater = system.getPhase(phaseNumber).getComponent(waterompNumb).getx();
          double mwWater = system.getPhase(phaseNumber).getComponent(waterompNumb).getMolarMass();
          double waterDenom = xWater * mwWater;
          if (waterDenom <= 0.0) {
            logger.warn("Zero water content in phase " + phaseNumber + ", skipping " + saltName);
            continue;
          }

          double x1 = system.getPhase(phaseNumber).getComponent(name1).getx() / waterDenom;
          double x2 = system.getPhase(phaseNumber).getComponent(name2).getx() / waterDenom;

          double scalePotentialFactor;

          if (saltName.contains("hydromagnesite (3MgCO3-Mg(OH)2-3H2O)")) {
            scalePotentialFactor = computeHydromagnesiteSR(x1, x2, compNumb1, compNumb2,
                waterompNumb, waterDenom, stoc1, stoc2, ksp);
          } else {
            scalePotentialFactor =
                computeSRLogSpace(x1, x2, compNumb1, compNumb2, waterompNumb, stoc1, stoc2, ksp);
          }

          logger.info("mol/kg " + name1 + "=" + x1 + ", " + name2 + "=" + x2);
          logger.info("Scale potential factor " + scalePotentialFactor);

          results.add(new String[] {saltName, Double.toString(scalePotentialFactor), ""});
        }
      }

      // Convert dynamic list to fixed array
      resultTable = results.toArray(new String[0][]);

    } catch (Exception ex) {
      logger.error("failed ", ex);
    } finally {
      // Always restore MEG if it was removed
      if (numberOfMolesMEG > 0.0 && system.getPhase(phaseNumber).hasComponent("MEG")) {
        system.addComponent("MEG", numberOfMolesMEG * 0.9999, phaseNumber);
        system.addComponent("water", -numberOfMolesMEG, phaseNumber);
        system.init(1);
        system.getChemicalReactionOperations().solveChemEq(phaseNumber, 1);
      }
    }
  }

  /**
   * Compute the saturation ratio (SR = IAP/Ksp) in log-space to avoid numerical underflow/overflow
   * when ion molalities or activity coefficients are very small or large.
   *
   * @param m1 molality of ion 1 (mol/kg water)
   * @param m2 molality of ion 2 (mol/kg water)
   * @param compNumb1 component number of ion 1
   * @param compNumb2 component number of ion 2
   * @param waterCompNumb component number of water
   * @param stoc1 stoichiometric coefficient of ion 1
   * @param stoc2 stoichiometric coefficient of ion 2
   * @param ksp solubility product constant
   * @return saturation ratio (SR), clamped to [0, exp(LN_SR_CLAMP)]
   */
  private double computeSRLogSpace(double m1, double m2, int compNumb1, int compNumb2,
      int waterCompNumb, double stoc1, double stoc2, double ksp) {
    if (m1 < MIN_MOLALITY || m2 < MIN_MOLALITY) {
      return 0.0;
    }

    double gamma1 = system.getPhase(phaseNumber).getActivityCoefficient(compNumb1, waterCompNumb);
    double gamma2 = system.getPhase(phaseNumber).getActivityCoefficient(compNumb2, waterCompNumb);

    if (gamma1 <= 0.0 || gamma2 <= 0.0 || Double.isNaN(gamma1) || Double.isNaN(gamma2)
        || Double.isInfinite(gamma1) || Double.isInfinite(gamma2)) {
      logger.warn("Invalid activity coefficient: gamma1=" + gamma1 + ", gamma2=" + gamma2);
      return 0.0;
    }

    // Compute ln(IAP) = stoc1*ln(gamma1*m1) + stoc2*ln(gamma2*m2)
    double lnIAP = stoc1 * Math.log(gamma1 * m1) + stoc2 * Math.log(gamma2 * m2);
    double lnKsp = Math.log(ksp);
    double lnSR = lnIAP - lnKsp;

    // Clamp to prevent extreme values
    if (lnSR < -LN_SR_CLAMP) {
      return 0.0;
    } else if (lnSR > LN_SR_CLAMP) {
      return Math.exp(LN_SR_CLAMP);
    }
    return Math.exp(lnSR);
  }

  /**
   * Compute the saturation ratio for hydromagnesite in log-space. Hydromagnesite
   * (3MgCO3-Mg(OH)2-3H2O) has a complex IAP involving four ion terms.
   *
   * @param m1 molality of Mg++ (mol/kg water)
   * @param m2 molality of CO3-- (mol/kg water)
   * @param compNumb1 component number of Mg++
   * @param compNumb2 component number of CO3--
   * @param waterCompNumb component number of water
   * @param waterDenom denominator for molality conversion (xWater * MWwater)
   * @param stoc1 stoichiometric coefficient of Mg++ (4)
   * @param stoc2 stoichiometric coefficient of CO3-- (3)
   * @param ksp solubility product constant
   * @return saturation ratio (SR), clamped to [0, exp(LN_SR_CLAMP)]
   */
  private double computeHydromagnesiteSR(double m1, double m2, int compNumb1, int compNumb2,
      int waterCompNumb, double waterDenom, double stoc1, double stoc2, double ksp) {
    if (m1 < MIN_MOLALITY || m2 < MIN_MOLALITY) {
      return 0.0;
    }

    // Water molality (always ~55.5 mol/kg)
    double m3 = system.getPhase(phaseNumber).getComponent(waterCompNumb).getx() / waterDenom;
    if (!system.getPhase(phaseNumber).hasComponent("OH-")) {
      return 0.0;
    }
    double m4 = system.getPhase(phaseNumber).getComponent("OH-").getx() / waterDenom;
    if (m3 < MIN_MOLALITY || m4 < MIN_MOLALITY) {
      return 0.0;
    }

    double gamma1 = system.getPhase(phaseNumber).getActivityCoefficient(compNumb1, waterCompNumb);
    double gamma2 = system.getPhase(phaseNumber).getActivityCoefficient(compNumb2, waterCompNumb);
    double gamma3 =
        system.getPhase(phaseNumber).getActivityCoefficient(waterCompNumb, waterCompNumb);
    int ohCompNumb = system.getPhase(phaseNumber).getComponent("OH-").getComponentNumber();
    double gamma4 = system.getPhase(phaseNumber).getActivityCoefficient(ohCompNumb, waterCompNumb);

    if (gamma1 <= 0.0 || gamma2 <= 0.0 || gamma3 <= 0.0 || gamma4 <= 0.0) {
      return 0.0;
    }

    // ln(IAP) = stoc1*ln(g1*m1) + stoc2*ln(g2*m2) + 3*ln(g3*m3) + 2*ln(g4*m4)
    double lnIAP = stoc1 * Math.log(gamma1 * m1) + stoc2 * Math.log(gamma2 * m2)
        + 3.0 * Math.log(gamma3 * m3) + 2.0 * Math.log(gamma4 * m4);
    double lnKsp = Math.log(ksp);
    double lnSR = lnIAP - lnKsp;

    if (lnSR < -LN_SR_CLAMP) {
      return 0.0;
    } else if (lnSR > LN_SR_CLAMP) {
      return Math.exp(LN_SR_CLAMP);
    }
    return Math.exp(lnSR);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    logger.info("checking table...scale " + resultTable[0][0]);
    logger.info("checking table...scale " + resultTable[0][1]);
    logger.info("checking table...scale " + resultTable[0][2]);
    logger.info("checking table...scale " + resultTable[1][0]);
    logger.info("checking table...scale " + resultTable[1][1]);
    logger.info("checking table...scale " + resultTable[1][2]);
    return resultTable;
  }
}
