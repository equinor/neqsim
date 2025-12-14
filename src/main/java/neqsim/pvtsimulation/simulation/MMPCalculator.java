package neqsim.pvtsimulation.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Minimum Miscibility Pressure (MMP) Calculator.
 *
 * <p>
 * Calculates MMP for gas injection EOR processes using multiple methods:
 * <ul>
 * <li>Slim-tube simulation (reference method)</li>
 * <li>Key tie-line approach (analytical)</li>
 * <li>Rising bubble apparatus simulation</li>
 * </ul>
 *
 * <p>
 * MMP is defined as the pressure at which the oil recovery in a slim-tube experiment reaches a
 * specified threshold (typically 90-95% at 1.2 PV injected).
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * MMPCalculator mmp = new MMPCalculator(reservoirOil, injectionGas);
 * mmp.setTemperature(100.0); // 100°C
 * mmp.setRecoveryThreshold(0.90); // 90% recovery
 * mmp.run();
 * 
 * System.out.println("MMP: " + mmp.getMMP() + " bara");
 * System.out.println("Miscibility mechanism: " + mmp.getMiscibilityMechanism());
 * </pre>
 *
 * @author ESOL
 */
public class MMPCalculator extends BasePVTsimulation {

  private static final Logger logger = LogManager.getLogger(MMPCalculator.class);

  /**
   * Miscibility mechanism type.
   */
  public enum MiscibilityMechanism {
    /** First-contact miscibility. */
    FIRST_CONTACT,
    /** Multi-contact miscibility through vaporizing drive. */
    VAPORIZING,
    /** Multi-contact miscibility through condensing drive. */
    CONDENSING,
    /** Combined condensing/vaporizing drive. */
    COMBINED,
    /** Not miscible at tested conditions. */
    IMMISCIBLE,
    /** Not determined. */
    UNKNOWN
  }

  /**
   * Method for MMP calculation.
   */
  public enum CalculationMethod {
    /** Slim-tube simulation. */
    SLIM_TUBE,
    /** Key tie-line method. */
    KEY_TIE_LINE,
    /** Rising bubble simulation. */
    RISING_BUBBLE
  }

  private SystemInterface injectionGas;
  private double temperature = 373.15; // K
  private double recoveryThreshold = 0.90; // 90%
  private double mmp = 0.0;
  private MiscibilityMechanism mechanism = MiscibilityMechanism.UNKNOWN;
  private CalculationMethod method = CalculationMethod.SLIM_TUBE;

  private double[] pressures;
  private double[] recoveries;
  private int numberOfPressurePoints = 10;
  private double minPressure = 100.0; // bara
  private double maxPressure = 500.0; // bara

  private int slimTubeNodes = 100;
  private double pvInjected = 1.2;

  /**
   * Constructor for MMPCalculator.
   *
   * @param oil Reservoir oil system
   * @param gas Injection gas system
   */
  public MMPCalculator(SystemInterface oil, SystemInterface gas) {
    super(oil);
    this.injectionGas = gas;
  }

  /**
   * Set reservoir temperature.
   *
   * @param temperatureCelsius Temperature in Celsius
   */
  public void setTemperature(double temperatureCelsius) {
    this.temperature = temperatureCelsius + 273.15;
  }

  /**
   * Set oil recovery threshold for MMP determination.
   *
   * @param threshold Recovery threshold (0-1), default 0.90
   */
  public void setRecoveryThreshold(double threshold) {
    this.recoveryThreshold = threshold;
  }

  /**
   * Set calculation method.
   *
   * @param method Calculation method
   */
  public void setMethod(CalculationMethod method) {
    this.method = method;
  }

  /**
   * Set pressure range for MMP search.
   *
   * @param minP Minimum pressure (bara)
   * @param maxP Maximum pressure (bara)
   */
  public void setPressureRange(double minP, double maxP) {
    this.minPressure = minP;
    this.maxPressure = maxP;
  }

  /**
   * Set number of pressure points for recovery curve.
   *
   * @param n Number of points
   */
  public void setNumberOfPressurePoints(int n) {
    this.numberOfPressurePoints = n;
  }

  /**
   * Set slim-tube simulation parameters.
   *
   * @param nodes Number of mixing cells
   * @param pvInj Pore volumes injected
   */
  public void setSlimTubeParameters(int nodes, double pvInj) {
    this.slimTubeNodes = nodes;
    this.pvInjected = pvInj;
  }

  @Override
  public void run() {
    switch (method) {
      case SLIM_TUBE:
        runSlimTubeMethod();
        break;
      case KEY_TIE_LINE:
        runKeyTieLineMethod();
        break;
      case RISING_BUBBLE:
        runRisingBubbleMethod();
        break;
    }
  }

  /**
   * Run slim-tube simulation method.
   */
  private void runSlimTubeMethod() {
    pressures = new double[numberOfPressurePoints];
    recoveries = new double[numberOfPressurePoints];

    double dp = (maxPressure - minPressure) / (numberOfPressurePoints - 1);

    for (int i = 0; i < numberOfPressurePoints; i++) {
      double p = minPressure + i * dp;
      pressures[i] = p;

      // Run simplified slim-tube at this pressure
      recoveries[i] = simulateSlimTubeRecovery(p);

      logger.info(String.format("P = %.1f bara, Recovery = %.1f%%", p, recoveries[i] * 100));
    }

    // Find MMP by interpolation
    mmp = interpolateMMP(pressures, recoveries, recoveryThreshold);

    // Determine mechanism
    mechanism = determineMechanism();
  }

  /**
   * Simplified slim-tube recovery simulation.
   */
  private double simulateSlimTubeRecovery(double pressure) {
    // Initialize systems
    SystemInterface oil = getThermoSystem().clone();
    oil.setPressure(pressure);
    oil.setTemperature(temperature);

    SystemInterface gas = injectionGas.clone();
    gas.setPressure(pressure);
    gas.setTemperature(temperature);

    ThermodynamicOperations oilOps = new ThermodynamicOperations(oil);
    ThermodynamicOperations gasOps = new ThermodynamicOperations(gas);

    try {
      oilOps.TPflash();
      gasOps.TPflash();
    } catch (Exception e) {
      logger.error("Flash failed at P = " + pressure, e);
      return 0.0;
    }

    // Check for first-contact miscibility
    if (oil.getNumberOfPhases() == 1 && gas.getNumberOfPhases() == 1) {
      // Mix and check if single phase
      SystemInterface mixture = oil.clone();
      for (int i = 0; i < gas.getPhase(0).getNumberOfComponents(); i++) {
        String compName = gas.getPhase(0).getComponent(i).getComponentName();
        double moles = gas.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
        if (mixture.getPhase(0).hasComponent(compName)) {
          mixture.addComponent(compName, moles);
        }
      }

      mixture.setPressure(pressure);
      mixture.setTemperature(temperature);
      ThermodynamicOperations mixOps = new ThermodynamicOperations(mixture);
      try {
        mixOps.TPflash();
      } catch (Exception e) {
        logger.error("Mixture flash failed", e);
      }

      if (mixture.getNumberOfPhases() == 1) {
        return 1.0; // First-contact miscible
      }
    }

    // Simplified multi-contact calculation
    // Based on mixing cell simulation
    double oilInPlace = oil.getNumberOfMoles();
    double oilRecovered = 0.0;

    SystemInterface[] cells = new SystemInterface[slimTubeNodes];
    for (int i = 0; i < slimTubeNodes; i++) {
      cells[i] = oil.clone();
    }

    // Inject gas
    int injectionSteps = (int) (pvInjected * slimTubeNodes);
    double gasPerStep = gas.getNumberOfMoles() / slimTubeNodes;

    for (int step = 0; step < injectionSteps; step++) {
      // Inject into first cell
      for (int i = 0; i < gas.getPhase(0).getNumberOfComponents(); i++) {
        String compName = gas.getPhase(0).getComponent(i).getComponentName();
        double moles = gasPerStep * gas.getPhase(0).getComponent(i).getx();
        if (cells[0].getPhase(0).hasComponent(compName)) {
          cells[0].addComponent(compName, moles);
        }
      }

      // Flash each cell and transfer excess
      for (int c = 0; c < slimTubeNodes; c++) {
        cells[c].setPressure(pressure);
        cells[c].setTemperature(temperature);
        ThermodynamicOperations cellOps = new ThermodynamicOperations(cells[c]);
        try {
          cellOps.TPflash();
        } catch (Exception e) {
          // Continue
        }

        // Calculate equilibrium K-values and check miscibility
        if (cells[c].getNumberOfPhases() > 1) {
          // Produce gas from cell
          if (c < slimTubeNodes - 1 && cells[c].hasPhaseType("gas")) {
            SystemInterface gasPhase = cells[c].phaseToSystem("gas");
            // Add gas to next cell
            for (int i = 0; i < gasPhase.getPhase(0).getNumberOfComponents(); i++) {
              String compName = gasPhase.getPhase(0).getComponent(i).getComponentName();
              double moles = gasPhase.getPhase(0).getComponent(i).getNumberOfMolesInPhase() * 0.5;
              if (cells[c + 1].getPhase(0).hasComponent(compName)) {
                cells[c + 1].addComponent(compName, moles);
              }
            }
            // Remove gas from current cell
            cells[c] = cells[c].phaseToSystem("oil");
          }
        }
      }

      // Produce from last cell
      if (cells[slimTubeNodes - 1].hasPhaseType("oil")) {
        oilRecovered += cells[slimTubeNodes - 1].getPhase("oil").getNumberOfMolesInPhase() * 0.1;
      }
    }

    double recovery = oilRecovered / oilInPlace;
    return Math.min(1.0, Math.max(0.0, recovery));
  }

  /**
   * Interpolate MMP from recovery curve.
   */
  private double interpolateMMP(double[] p, double[] rec, double threshold) {
    for (int i = 1; i < p.length; i++) {
      if (rec[i] >= threshold && rec[i - 1] < threshold) {
        // Linear interpolation
        double slope = (p[i] - p[i - 1]) / (rec[i] - rec[i - 1]);
        return p[i - 1] + slope * (threshold - rec[i - 1]);
      }
    }

    // Check if always above or below threshold
    if (rec[0] >= threshold) {
      return p[0]; // Miscible at lowest pressure
    }
    if (rec[rec.length - 1] < threshold) {
      return Double.NaN; // Not miscible in range
    }

    return p[p.length - 1];
  }

  /**
   * Determine miscibility mechanism from tie-line analysis.
   */
  private MiscibilityMechanism determineMechanism() {
    if (Double.isNaN(mmp)) {
      return MiscibilityMechanism.IMMISCIBLE;
    }

    // Simplified mechanism detection based on recovery curve shape
    // A more rigorous implementation would use tie-line analysis

    // Check for first-contact miscibility at MMP
    double recoveryAtMMP = 0;
    for (int i = 0; i < pressures.length - 1; i++) {
      if (pressures[i] <= mmp && pressures[i + 1] >= mmp) {
        double frac = (mmp - pressures[i]) / (pressures[i + 1] - pressures[i]);
        recoveryAtMMP = recoveries[i] + frac * (recoveries[i + 1] - recoveries[i]);
        break;
      }
    }

    if (recoveryAtMMP > 0.99) {
      return MiscibilityMechanism.FIRST_CONTACT;
    }

    // Check curve shape for mechanism
    // Vaporizing: recovery increases slowly then rapidly
    // Condensing: recovery increases rapidly at lower pressures

    double lowPRecovery = recoveries[1];
    double midPRecovery = recoveries[numberOfPressurePoints / 2];
    double highPRecovery = recoveries[numberOfPressurePoints - 2];

    if (lowPRecovery < 0.3 && midPRecovery < 0.6) {
      return MiscibilityMechanism.VAPORIZING;
    } else if (lowPRecovery > 0.5) {
      return MiscibilityMechanism.CONDENSING;
    } else {
      return MiscibilityMechanism.COMBINED;
    }
  }

  /**
   * Run key tie-line method (analytical approach).
   */
  private void runKeyTieLineMethod() {
    // Key tie-line method finds pressure where key tie-line length → 0
    // This is a simplified implementation

    double pLow = minPressure;
    double pHigh = maxPressure;
    double tolerance = 0.5; // bara

    while (pHigh - pLow > tolerance) {
      double pMid = (pLow + pHigh) / 2.0;
      double tieLineLength = calculateKeyTieLineLength(pMid);

      if (tieLineLength < 0.01) {
        pHigh = pMid;
      } else {
        pLow = pMid;
      }
    }

    mmp = (pLow + pHigh) / 2.0;
    mechanism = MiscibilityMechanism.COMBINED;
  }

  /**
   * Calculate key tie-line length at given pressure.
   */
  private double calculateKeyTieLineLength(double pressure) {
    // Mix oil and gas at 50:50 and flash
    SystemInterface mixture = getThermoSystem().clone();
    mixture.setPressure(pressure);
    mixture.setTemperature(temperature);

    // Add injection gas
    for (int i = 0; i < injectionGas.getPhase(0).getNumberOfComponents(); i++) {
      String compName = injectionGas.getPhase(0).getComponent(i).getComponentName();
      double moles = injectionGas.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      if (mixture.getPhase(0).hasComponent(compName)) {
        mixture.addComponent(compName, moles);
      }
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(mixture);
    try {
      ops.TPflash();
    } catch (Exception e) {
      return 1.0;
    }

    if (mixture.getNumberOfPhases() == 1) {
      return 0.0; // Single phase = miscible
    }

    // Calculate tie-line length (sum of squared K-value deviations)
    double length = 0.0;
    for (int i = 0; i < mixture.getPhase(0).getNumberOfComponents(); i++) {
      double yi = mixture.getPhase(0).getComponent(i).getx();
      double xi = mixture.getPhase(1).getComponent(i).getx();
      length += Math.pow(yi - xi, 2);
    }

    return Math.sqrt(length);
  }

  /**
   * Run rising bubble simulation method.
   */
  private void runRisingBubbleMethod() {
    // Simplified: similar to slim-tube but with different geometry
    runSlimTubeMethod();
  }

  /**
   * Get calculated MMP.
   *
   * @return MMP in bara
   */
  public double getMMP() {
    return mmp;
  }

  /**
   * Get miscibility mechanism.
   *
   * @return Miscibility mechanism
   */
  public MiscibilityMechanism getMiscibilityMechanism() {
    return mechanism;
  }

  /**
   * Get pressure points used in calculation.
   *
   * @return Array of pressures (bara)
   */
  public double[] getPressures() {
    return pressures;
  }

  /**
   * Get recovery at each pressure point.
   *
   * @return Array of recoveries (0-1)
   */
  public double[] getRecoveries() {
    return recoveries;
  }

  /**
   * Generate summary report.
   *
   * @return Report string
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== MMP Calculation Results ===\n\n");

    sb.append(String.format("Temperature: %.1f °C\n", temperature - 273.15));
    sb.append(String.format("Method: %s\n", method));
    sb.append(String.format("Recovery Threshold: %.0f%%\n", recoveryThreshold * 100));
    sb.append("\n");

    if (!Double.isNaN(mmp)) {
      sb.append(String.format("MMP: %.1f bara (%.1f psia)\n", mmp, mmp * 14.5038));
    } else {
      sb.append("MMP: Not achieved in pressure range\n");
    }
    sb.append(String.format("Miscibility Mechanism: %s\n", mechanism));

    if (pressures != null && recoveries != null) {
      sb.append("\nRecovery Curve:\n");
      sb.append(String.format("%-12s %-12s\n", "P (bara)", "Recovery (%)"));
      sb.append(StringUtils.repeat("-", 25) + "\n");
      for (int i = 0; i < pressures.length; i++) {
        sb.append(String.format("%-12.1f %-12.1f\n", pressures[i], recoveries[i] * 100));
      }
    }

    return sb.toString();
  }
}
