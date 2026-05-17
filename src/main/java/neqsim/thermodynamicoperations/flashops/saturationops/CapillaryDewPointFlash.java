package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;

/**
 * Computes the capillary dew point temperature for multicomponent gas mixtures.
 *
 * <p>
 * The capillary dew point accounts for the Young-Laplace pressure difference across the curved
 * gas-liquid interface in a capillary or pore. The liquid phase pressure {@code P_L} differs from
 * the vapor phase pressure {@code P_V} by the capillary pressure:
 * </p>
 *
 * <p>
 * {@code P_L = P_V - 2 * sigma * cos(theta) / r}
 * </p>
 *
 * <p>
 * where {@code sigma} is the interfacial tension, {@code theta} is the contact angle, and {@code r}
 * is the pore radius. This shifts the dew point to higher temperatures compared to the bulk
 * (flat-interface) dew point.
 * </p>
 *
 * <p>
 * The algorithm uses the standard K-factor dew point iteration with a Poynting correction to
 * account for the pressure difference between phases:
 * </p>
 *
 * <p>
 * {@code K_i_cap = K_i_bulk * exp(-Vm_L * deltaP_cap / (R * T))}
 * </p>
 *
 * @author experiment
 * @version 1.0
 */
public class CapillaryDewPointFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Universal gas constant in J/(mol*K). */
  private static final double R_GAS = 8.31446;

  /** Effective capillary (pore) radius in meters. */
  private double poreRadius;

  /** Contact angle in radians (0 = perfect wetting). */
  private double contactAngle;

  /** Computed capillary pressure in Pa. */
  private double capillaryPressure;

  /** Computed surface tension in N/m. */
  private double surfaceTension;

  /** Bulk (flat-surface) dew point temperature in K (stored for reporting). */
  private double bulkDewPointTemperature;

  /**
   * Constructor with default contact angle of 0 (perfect wetting).
   *
   * @param system the thermodynamic system
   * @param poreRadiusM the effective pore radius in meters, must be positive
   */
  public CapillaryDewPointFlash(SystemInterface system, double poreRadiusM) {
    super(system);
    if (poreRadiusM <= 0) {
      throw new IllegalArgumentException("Pore radius must be positive, got: " + poreRadiusM);
    }
    this.poreRadius = poreRadiusM;
    this.contactAngle = 0.0;
  }

  /**
   * Constructor with specified contact angle.
   *
   * @param system the thermodynamic system
   * @param poreRadiusM the effective pore radius in meters, must be positive
   * @param contactAngleRad the contact angle in radians (0 = perfect wetting)
   */
  public CapillaryDewPointFlash(SystemInterface system, double poreRadiusM,
      double contactAngleRad) {
    super(system);
    if (poreRadiusM <= 0) {
      throw new IllegalArgumentException("Pore radius must be positive, got: " + poreRadiusM);
    }
    this.poreRadius = poreRadiusM;
    this.contactAngle = contactAngleRad;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      runSingleComponent();
      return;
    }
    runMultiComponent();
  }

  /**
   * Runs the capillary dew point calculation for single-component systems.
   *
   * <p>
   * Uses the bulk saturation point from {@link BubblePointTemperatureNoDer} and applies the
   * linearized Kelvin temperature correction.
   * </p>
   */
  private void runSingleComponent() {
    if (system.getPressure() >= system.getPhase(0).getComponent(0).getPC()) {
      setSuperCritical(true);
      return;
    }

    BubblePointTemperatureNoDer bubble = new BubblePointTemperatureNoDer(system);
    bubble.run();
    setSuperCritical(bubble.isSuperCritical());
    if (isSuperCritical()) {
      return;
    }

    bulkDewPointTemperature = system.getTemperature();

    // Get liquid properties at saturation for Kelvin correction
    system.initProperties();
    double sigma = getSurfaceTensionSafe();
    double vmL = getLiquidMolarVolumeSafe();

    surfaceTension = sigma;
    capillaryPressure = 2.0 * sigma * Math.cos(contactAngle) / poreRadius;

    // Linearized Kelvin temperature correction: deltaT = 2*sigma*Vm_L / (r * R)
    double deltaT = 2.0 * sigma * vmL / (poreRadius * R_GAS);

    system.setTemperature(bulkDewPointTemperature + deltaT);
    system.setBeta(0, 1.0 - 1e-10);
    system.setBeta(1, 1e-10);
  }

  /**
   * Runs the capillary dew point calculation for multicomponent systems.
   *
   * <p>
   * Based on the standard dew point K-factor iteration (same algorithm as
   * {@link DewPointTemperatureFlash}) with an additional Poynting correction term applied to all
   * K-factors to account for the capillary pressure difference between phases.
   * </p>
   */
  private void runMultiComponent() {
    int iterations = 0;
    int maxNumberOfIterations = 1000;
    double xold = 0;
    double xtotal = 1;

    system.init(0);
    system.setBeta(0, 1.0 - 1e-15);
    system.setBeta(1, 1e-15);
    system.init(1);
    system.setNumberOfPhases(2);

    double oldTemp = 0;
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(0);
      system.getChemicalReactionOperations().solveChemEq(1);
    }

    // Initialize liquid compositions (same as DewPointTemperatureFlash)
    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getz());
      if (system.getPhases()[0].getComponent(i).getIonicCharge() != 0) {
        system.getPhases()[0].getComponent(i).setx(1e-40);
      } else {
        if (system.getPhases()[1].getComponent(i).getName().equals("water")) {
          system.getPhases()[1].getComponent(i).setx(1.0);
        } else if (system.getPhases()[1].hasComponent("water")) {
          system.getPhases()[1].getComponent(i).setx(1.0e-10);
        } else {
          system.getPhases()[1].getComponent(i)
              .setx(1.0 / system.getPhases()[0].getComponent(i).getK()
                  * system.getPhases()[1].getComponent(i).getz());
        }
      }
    }

    xtotal = 0.0;
    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      xtotal += system.getPhases()[1].getComponent(i).getx();
    }

    double ktot = 0.0;
    double oldTemperature = 0.0;
    double fold = 0;

    do {
      for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
        system.getPhases()[1].getComponent(i)
            .setx(system.getPhases()[1].getComponent(i).getx() / xtotal);
      }
      system.init(1);
      oldTemp = system.getTemperature();
      iterations++;

      // Compute Kelvin/Poynting correction for capillary effect
      double kelvinShift = computeKelvinShift();

      ktot = 0.0;
      for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
        do {
          xold = system.getPhases()[1].getComponent(i).getx();
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            system.getPhases()[0].getComponent(i).setK(1e-40);
          } else {
            // Standard K-factor from fugacity coefficients
            double lnK = system.getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                - system.getPhases()[0].getComponent(i).getLogFugacityCoefficient();
            // Apply capillary correction (Poynting factor):
            // ln(K_cap) = ln(K_bulk) - Vm_L * deltaP_cap / (R*T)
            double lnKcap = lnK - kelvinShift;
            system.getPhases()[0].getComponent(i).setK(Math.exp(lnKcap));
          }
          system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
          system.getPhases()[1].getComponent(i)
              .setx(1.0 / system.getPhases()[0].getComponent(i).getK()
                  * system.getPhases()[1].getComponent(i).getz());
        } while (Math.abs(system.getPhases()[1].getComponent(i).getx() - xold) > 1e-6);
        ktot += Math.abs(system.getPhases()[0].getComponent(i).getK() - 1.0);
      }

      xtotal = 0.0;
      for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
        xtotal += system.getPhases()[1].getComponent(i).getx();
      }

      double f = xtotal - 1.0;
      double dfdT = (f - fold) / (system.getTemperature() - oldTemperature);
      fold = f;

      oldTemperature = system.getTemperature();
      if (iterations < 3) {
        system.setTemperature(system.getTemperature() + iterations / (iterations + 100.0)
            * (xtotal * system.getTemperature() - system.getTemperature()));
      } else {
        system
            .setTemperature(system.getTemperature() - iterations / (iterations + 10.0) * f / dfdT);
      }
    } while (((Math.abs(xtotal - 1.0) > 1e-10)
        || Math.abs(oldTemp - system.getTemperature()) / oldTemp > 1e-8)
        && (iterations < maxNumberOfIterations));

    if (Math.abs(xtotal - 1.0) > 1e-5
        || (ktot < 1.0e-3 && system.getPhase(0).getNumberOfComponents() > 1)) {
      setSuperCritical(true);
    }
  }

  /**
   * Computes the Kelvin shift term: Vm_L * deltaP_cap / (R*T).
   *
   * <p>
   * This value is subtracted from ln(K_i) to produce the capillary-corrected K-factor:
   * {@code ln(K_cap) = ln(K_bulk) - kelvinShift}.
   * </p>
   *
   * @return the dimensionless Kelvin shift
   */
  private double computeKelvinShift() {
    double T = system.getTemperature();

    // Surface tension from parachor model
    double sigma = getSurfaceTensionSafe();
    surfaceTension = sigma;

    // Capillary pressure (Pa)
    capillaryPressure = 2.0 * sigma * Math.cos(contactAngle) / poreRadius;

    // Liquid molar volume (m3/mol)
    double vmL = getLiquidMolarVolumeSafe();

    // Kelvin shift: Vm_L * deltaP / (R*T)
    return vmL * capillaryPressure / (R_GAS * T);
  }

  /**
   * Gets the surface tension from the system's interphase properties, with a fallback value.
   *
   * @return surface tension in N/m
   */
  private double getSurfaceTensionSafe() {
    double sigma = 0.005; // 5 mN/m fallback
    try {
      system.initPhysicalProperties();
      sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      if (sigma <= 0 || Double.isNaN(sigma)) {
        sigma = 0.005;
      }
    } catch (Exception e) {
      logger.debug("Surface tension calculation failed, using fallback: " + e.getMessage());
    }
    return sigma;
  }

  /**
   * Gets the liquid phase molar volume from the system, with a fallback value.
   *
   * @return liquid molar volume in m3/mol
   */
  private double getLiquidMolarVolumeSafe() {
    double vmL = 1e-4; // 100 cm3/mol fallback
    try {
      vmL = system.getPhase(1).getMolarVolume("m3/mol");
      if (vmL <= 0 || vmL > 0.01 || Double.isNaN(vmL)) {
        vmL = 1e-4;
      }
    } catch (Exception e) {
      logger.debug("Liquid molar volume failed, using fallback: " + e.getMessage());
    }
    return vmL;
  }

  /**
   * Gets the effective pore radius.
   *
   * @return pore radius in meters
   */
  public double getPoreRadius() {
    return poreRadius;
  }

  /**
   * Sets the effective pore radius.
   *
   * @param poreRadiusM the pore radius in meters, must be positive
   */
  public void setPoreRadius(double poreRadiusM) {
    if (poreRadiusM <= 0) {
      throw new IllegalArgumentException("Pore radius must be positive, got: " + poreRadiusM);
    }
    this.poreRadius = poreRadiusM;
  }

  /**
   * Gets the contact angle.
   *
   * @return contact angle in radians
   */
  public double getContactAngle() {
    return contactAngle;
  }

  /**
   * Sets the contact angle.
   *
   * @param contactAngleRad contact angle in radians
   */
  public void setContactAngle(double contactAngleRad) {
    this.contactAngle = contactAngleRad;
  }

  /**
   * Gets the computed capillary pressure.
   *
   * @return capillary pressure in Pa
   */
  public double getCapillaryPressure() {
    return capillaryPressure;
  }

  /**
   * Gets the computed surface tension.
   *
   * @return surface tension in N/m
   */
  public double getSurfaceTension() {
    return surfaceTension;
  }

  /**
   * Gets the bulk dew point temperature (only available after single-component run).
   *
   * @return bulk dew point temperature in K
   */
  public double getBulkDewPointTemperature() {
    return bulkDewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
