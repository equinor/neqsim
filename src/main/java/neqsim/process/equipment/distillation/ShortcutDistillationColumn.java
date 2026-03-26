package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Short-cut distillation column using the Fenske-Underwood-Gilliland (FUG) method.
 *
 * <p>
 * This class provides rapid conceptual design estimates for distillation columns without requiring
 * full rigorous tray-by-tray calculations. It calculates:
 * </p>
 * <ul>
 * <li>Minimum number of stages (Fenske equation)</li>
 * <li>Minimum reflux ratio (Underwood equations)</li>
 * <li>Actual number of stages for a given reflux ratio (Gilliland correlation)</li>
 * <li>Optimal feed tray location (Kirkbride equation)</li>
 * <li>Condenser and reboiler duties</li>
 * </ul>
 *
 * <p>
 * The user must specify the light key component, heavy key component, and desired recoveries or
 * product compositions. The column operates at the feed pressure.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ShortcutDistillationColumn extends ProcessEquipmentBaseClass
    implements DistillationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ShortcutDistillationColumn.class);

  /** Feed stream. */
  private StreamInterface feedStream;

  /** Light key component name. */
  private String lightKey;

  /** Heavy key component name. */
  private String heavyKey;

  /** Recovery of light key in distillate (molar fraction, 0 to 1). */
  private double lightKeyRecoveryDistillate = 0.99;

  /** Recovery of heavy key in bottoms (molar fraction, 0 to 1). */
  private double heavyKeyRecoveryBottoms = 0.99;

  /** Reflux ratio multiplier (actual / minimum). */
  private double refluxRatioMultiplier = 1.2;

  /** Condenser pressure in bara. */
  private double condenserPressure = -1.0;

  /** Reboiler pressure in bara (default = feed pressure). */
  private double reboilerPressure = -1.0;

  // --- Results ---
  /** Minimum number of theoretical stages (Fenske). */
  private double nMin = 0.0;

  /** Minimum reflux ratio (Underwood). */
  private double rMin = 0.0;

  /** Actual number of theoretical stages (Gilliland). */
  private double nActual = 0.0;

  /** Actual reflux ratio. */
  private double rActual = 0.0;

  /** Optimal feed tray number from top (Kirkbride). */
  private int feedTrayNumber = 0;

  /** Condenser duty in Watts. */
  private double condenserDuty = 0.0;

  /** Reboiler duty in Watts. */
  private double reboilerDuty = 0.0;

  /** Relative volatility of light key to heavy key. */
  private double alphaLKHK = 0.0;

  /** Distillate stream. */
  private StreamInterface distillateStream;

  /** Bottoms stream. */
  private StreamInterface bottomsStream;

  /** Flag indicating if the column has been solved. */
  private boolean solved = false;

  /**
   * Constructor for ShortcutDistillationColumn.
   *
   * @param name column name
   */
  public ShortcutDistillationColumn(String name) {
    super(name);
  }

  /**
   * Constructor for ShortcutDistillationColumn.
   *
   * @param name column name
   * @param feedStream the feed stream
   */
  public ShortcutDistillationColumn(String name, StreamInterface feedStream) {
    super(name);
    this.feedStream = feedStream;
  }

  /**
   * Set the feed stream.
   *
   * @param feedStream the feed stream
   */
  public void setFeedStream(StreamInterface feedStream) {
    this.feedStream = feedStream;
  }

  /**
   * Set the light key component.
   *
   * @param componentName light key component name
   */
  public void setLightKey(String componentName) {
    this.lightKey = componentName;
  }

  /**
   * Set the heavy key component.
   *
   * @param componentName heavy key component name
   */
  public void setHeavyKey(String componentName) {
    this.heavyKey = componentName;
  }

  /**
   * Set the recovery of light key in the distillate (mole fraction 0 to 1).
   *
   * @param recovery light key recovery in distillate (default 0.99)
   */
  public void setLightKeyRecoveryDistillate(double recovery) {
    this.lightKeyRecoveryDistillate = recovery;
  }

  /**
   * Set the recovery of heavy key in the bottoms (mole fraction 0 to 1).
   *
   * @param recovery heavy key recovery in bottoms (default 0.99)
   */
  public void setHeavyKeyRecoveryBottoms(double recovery) {
    this.heavyKeyRecoveryBottoms = recovery;
  }

  /**
   * Set the reflux ratio multiplier (R_actual / R_minimum). Default is 1.2.
   *
   * @param multiplier ratio of actual to minimum reflux (must be &gt; 1.0)
   */
  public void setRefluxRatioMultiplier(double multiplier) {
    if (multiplier <= 1.0) {
      logger.warn("Reflux ratio multiplier should be > 1.0, got: " + multiplier);
    }
    this.refluxRatioMultiplier = multiplier;
  }

  /**
   * Set the condenser pressure.
   *
   * @param pressure condenser pressure in bara
   */
  public void setCondenserPressure(double pressure) {
    this.condenserPressure = pressure;
  }

  /**
   * Set the condenser pressure with units.
   *
   * @param pressure condenser pressure
   * @param unit pressure unit (e.g., "bara", "barg")
   */
  public void setCondenserPressure(double pressure, String unit) {
    if ("barg".equals(unit)) {
      this.condenserPressure = pressure + 1.01325;
    } else {
      this.condenserPressure = pressure;
    }
  }

  /**
   * Set the reboiler pressure.
   *
   * @param pressure reboiler pressure in bara
   */
  public void setReboilerPressure(double pressure) {
    this.reboilerPressure = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfTrays(int number) {
    // Not used directly — number of trays is calculated
    logger.info("ShortcutDistillationColumn calculates stages; setNumberOfTrays ignored.");
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (feedStream == null) {
      throw new IllegalStateException("Feed stream not set for " + getName());
    }
    if (lightKey == null || heavyKey == null) {
      throw new IllegalStateException("Light key and heavy key must be set for " + getName());
    }

    SystemInterface feedFluid = feedStream.getFluid().clone();
    double feedPressure = feedFluid.getPressure();

    if (condenserPressure < 0) {
      condenserPressure = feedPressure;
    }
    if (reboilerPressure < 0) {
      reboilerPressure = feedPressure;
    }

    // Run a TP flash on the feed to get equilibrium K-values
    ThermodynamicOperations feedOps = new ThermodynamicOperations(feedFluid);
    feedOps.TPflash();
    feedFluid.init(2);

    // Get feed composition and K-values
    int lkIdx = feedFluid.getPhase(0).getComponent(lightKey).getComponentNumber();
    int hkIdx = feedFluid.getPhase(0).getComponent(heavyKey).getComponentNumber();
    int numComponents = feedFluid.getNumberOfComponents();

    // Calculate K-values (from equilibrium phases)
    double[] kValues = new double[numComponents];
    if (feedFluid.getNumberOfPhases() >= 2) {
      for (int i = 0; i < numComponents; i++) {
        double yi = feedFluid.getPhase(0).getComponent(i).getx();
        double xi = feedFluid.getPhase(1).getComponent(i).getx();
        kValues[i] = (xi > 1.0e-20) ? yi / xi : 1.0e10;
      }
    } else {
      // Single phase — estimate K-values using Wilson correlation
      for (int i = 0; i < numComponents; i++) {
        double Tc = feedFluid.getPhase(0).getComponent(i).getTC();
        double Pc = feedFluid.getPhase(0).getComponent(i).getPC();
        double omega = feedFluid.getPhase(0).getComponent(i).getAcentricFactor();
        double T = feedFluid.getTemperature();
        double P = feedFluid.getPressure();
        kValues[i] = (Pc / P) * Math.exp(5.373 * (1.0 + omega) * (1.0 - Tc / T));
      }
    }

    double kLK = kValues[lkIdx];
    double kHK = kValues[hkIdx];
    alphaLKHK = kLK / kHK;

    if (alphaLKHK <= 1.0) {
      logger.error("Relative volatility alpha_LK/HK <= 1.0 (" + alphaLKHK
          + "). Light key is less volatile than heavy key. Check key assignment.");
      solved = false;
      return;
    }

    // Relative volatilities for all components
    double[] alpha = new double[numComponents];
    for (int i = 0; i < numComponents; i++) {
      alpha[i] = kValues[i] / kHK;
    }

    // Feed composition (mole fractions)
    double[] zFeed = new double[numComponents];
    for (int i = 0; i < numComponents; i++) {
      zFeed[i] = feedFluid.getPhase(0).getComponent(i).getz();
    }

    // ============================
    // 1. FENSKE — Minimum Stages
    // ============================
    // Nmin = ln[(xLK_D/xHK_D) * (xHK_B/xLK_B)] / ln(alpha_LK/HK)
    double xLK_D = lightKeyRecoveryDistillate;
    double xHK_D = 1.0 - heavyKeyRecoveryBottoms;
    double xLK_B = 1.0 - lightKeyRecoveryDistillate;
    double xHK_B = heavyKeyRecoveryBottoms;

    nMin = Math.log((xLK_D / xHK_D) * (xHK_B / xLK_B)) / Math.log(alphaLKHK);

    // ============================
    // 2. UNDERWOOD — Minimum Reflux
    // ============================
    // Solve for theta: sum(alpha_i * z_i / (alpha_i - theta)) = 1 - q
    // where q = feed quality (1.0 for saturated liquid, 0.0 for saturated vapor)
    double q = computeFeedQuality(feedFluid);

    // Find theta between 1 and alpha_LK/HK using bisection
    double theta = solveUnderwood(alpha, zFeed, q, numComponents);

    // Calculate Rmin from: Rmin + 1 = sum(alpha_i * xD_i / (alpha_i - theta))
    // Estimate distillate composition for Underwood
    double[] xD = estimateDistillateComposition(zFeed, alpha, numComponents, lkIdx, hkIdx);

    double rMinPlusOne = 0.0;
    for (int i = 0; i < numComponents; i++) {
      if (xD[i] > 1.0e-15 && Math.abs(alpha[i] - theta) > 1.0e-10) {
        rMinPlusOne += alpha[i] * xD[i] / (alpha[i] - theta);
      }
    }
    rMin = Math.max(rMinPlusOne - 1.0, 0.0);

    // ============================
    // 3. GILLILAND — Actual Stages
    // ============================
    rActual = rMin * refluxRatioMultiplier;

    double X = (rActual - rMin) / (rActual + 1.0);
    // Molokanov correlation (improved Gilliland)
    double Y;
    if (X >= 0.0 && X <= 1.0) {
      Y = 1.0 - Math.exp((1.0 + 54.4 * X) / (11.0 + 117.2 * X) * (X - 1.0) / Math.sqrt(X));
    } else {
      Y = 0.5; // Fallback
    }

    // Y = (N - Nmin) / (N + 1), solve for N
    nActual = (Y + nMin) / (1.0 - Y);

    // ============================
    // 4. KIRKBRIDE — Feed Tray
    // ============================
    double zLK_F = zFeed[lkIdx];
    double zHK_F = zFeed[hkIdx];
    double B_over_D = computeBottomsToDistillateRatio(zFeed, alpha, numComponents, lkIdx, hkIdx);
    double xHK_distillate = xHK_D * zHK_F;
    double xLK_bottoms = xLK_B * zLK_F;

    double kirkbrideRatio = 0.0;
    if (xLK_bottoms > 1.0e-15 && B_over_D > 1.0e-15) {
      kirkbrideRatio =
          Math.pow((zHK_F / zLK_F) * (xLK_bottoms / xHK_distillate) * (B_over_D * B_over_D), 0.206);
    }

    double nRectifying = nActual / (1.0 + kirkbrideRatio);
    double nStripping = nActual - nRectifying;
    feedTrayNumber = (int) Math.round(nRectifying) + 1;

    // ============================
    // 5. CONDENSER & REBOILER DUTY
    // ============================
    computeDuties(feedFluid, rActual, zFeed, xD, numComponents, lkIdx, hkIdx);

    // Create output streams
    createOutputStreams(feedFluid, zFeed, xD, numComponents, lkIdx, hkIdx);

    solved = true;
  }

  /**
   * Compute feed quality parameter q. q = 1 for saturated liquid, q = 0 for saturated vapor.
   *
   * @param feedFluid the feed fluid after flash
   * @return feed quality q
   */
  private double computeFeedQuality(SystemInterface feedFluid) {
    if (feedFluid.getNumberOfPhases() == 1) {
      if (feedFluid.getPhase(0).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        return 0.0; // Saturated vapor
      } else {
        return 1.0; // Saturated liquid
      }
    }
    // q = liquid fraction
    return 1.0 - feedFluid.getBeta();
  }

  /**
   * Solve the Underwood equation for theta using bisection.
   *
   * @param alpha relative volatilities
   * @param zFeed feed composition
   * @param q feed quality
   * @param n number of components
   * @return theta value
   */
  private double solveUnderwood(double[] alpha, double[] zFeed, double q, int n) {
    // theta is between alpha_HK (=1.0) and alpha_LK
    double thetaLow = 1.0 + 1.0e-6;
    double thetaHigh = alphaLKHK - 1.0e-6;

    for (int iter = 0; iter < 200; iter++) {
      double thetaMid = 0.5 * (thetaLow + thetaHigh);
      double fMid = underwoodFunction(alpha, zFeed, thetaMid, q, n);

      if (Math.abs(fMid) < 1.0e-10 || (thetaHigh - thetaLow) < 1.0e-12) {
        return thetaMid;
      }

      double fLow = underwoodFunction(alpha, zFeed, thetaLow, q, n);
      if (fLow * fMid < 0.0) {
        thetaHigh = thetaMid;
      } else {
        thetaLow = thetaMid;
      }
    }
    return 0.5 * (thetaLow + thetaHigh);
  }

  /**
   * Evaluate the Underwood function: sum(alpha_i * z_i / (alpha_i - theta)) - (1 - q).
   *
   * @param alpha relative volatilities
   * @param zFeed feed composition
   * @param theta current theta estimate
   * @param q feed quality
   * @param n number of components
   * @return function value
   */
  private double underwoodFunction(double[] alpha, double[] zFeed, double theta, double q, int n) {
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      if (Math.abs(alpha[i] - theta) > 1.0e-10) {
        sum += alpha[i] * zFeed[i] / (alpha[i] - theta);
      }
    }
    return sum - (1.0 - q);
  }

  /**
   * Estimate distillate composition based on key component recoveries.
   *
   * @param zFeed feed composition
   * @param alpha relative volatilities
   * @param n number of components
   * @param lkIdx light key index
   * @param hkIdx heavy key index
   * @return estimated distillate mole fractions (normalized)
   */
  private double[] estimateDistillateComposition(double[] zFeed, double[] alpha, int n, int lkIdx,
      int hkIdx) {
    double[] xD = new double[n];
    double totalD = 0.0;

    for (int i = 0; i < n; i++) {
      if (i == lkIdx) {
        xD[i] = zFeed[i] * lightKeyRecoveryDistillate;
      } else if (i == hkIdx) {
        xD[i] = zFeed[i] * (1.0 - heavyKeyRecoveryBottoms);
      } else if (alpha[i] > alphaLKHK) {
        // Lighter than LK — goes mostly to distillate
        xD[i] = zFeed[i] * 0.999;
      } else if (alpha[i] < 1.0) {
        // Heavier than HK — goes mostly to bottoms
        xD[i] = zFeed[i] * 0.001;
      } else {
        // Distributing component — use Fenske at Nmin
        double recovery = 1.0 / (1.0 + Math.pow(alpha[i] / alphaLKHK, -nMin)
            * (1.0 - lightKeyRecoveryDistillate) / lightKeyRecoveryDistillate);
        xD[i] = zFeed[i] * recovery;
      }
      totalD += xD[i];
    }

    // Normalize
    if (totalD > 0.0) {
      for (int i = 0; i < n; i++) {
        xD[i] /= totalD;
      }
    }
    return xD;
  }

  /**
   * Compute bottoms to distillate molar ratio.
   *
   * @param zFeed feed composition
   * @param alpha relative volatilities
   * @param n number of components
   * @param lkIdx light key index
   * @param hkIdx heavy key index
   * @return B/D ratio
   */
  private double computeBottomsToDistillateRatio(double[] zFeed, double[] alpha, int n, int lkIdx,
      int hkIdx) {
    double totalD = 0.0;
    double totalB = 0.0;
    for (int i = 0; i < n; i++) {
      double distFrac;
      if (i == lkIdx) {
        distFrac = lightKeyRecoveryDistillate;
      } else if (i == hkIdx) {
        distFrac = 1.0 - heavyKeyRecoveryBottoms;
      } else if (alpha[i] > alphaLKHK) {
        distFrac = 0.999;
      } else if (alpha[i] < 1.0) {
        distFrac = 0.001;
      } else {
        distFrac = 1.0 / (1.0 + Math.pow(alpha[i] / alphaLKHK, -nMin)
            * (1.0 - lightKeyRecoveryDistillate) / lightKeyRecoveryDistillate);
      }
      totalD += zFeed[i] * distFrac;
      totalB += zFeed[i] * (1.0 - distFrac);
    }
    return (totalD > 1.0e-15) ? totalB / totalD : 1.0;
  }

  /**
   * Compute condenser and reboiler duties.
   *
   * @param feedFluid the feed fluid
   * @param reflux actual reflux ratio
   * @param zFeed feed composition
   * @param xD distillate composition
   * @param n number of components
   * @param lkIdx light key index
   * @param hkIdx heavy key index
   */
  private void computeDuties(SystemInterface feedFluid, double reflux, double[] zFeed, double[] xD,
      int n, int lkIdx, int hkIdx) {
    double feedMoles = feedFluid.getTotalNumberOfMoles();
    double totalDistillateFrac = 0.0;
    for (int i = 0; i < n; i++) {
      if (i == lkIdx) {
        totalDistillateFrac += zFeed[i] * lightKeyRecoveryDistillate;
      } else if (i == hkIdx) {
        totalDistillateFrac += zFeed[i] * (1.0 - heavyKeyRecoveryBottoms);
      } else {
        // Use approximate split
        totalDistillateFrac += xD[i] * computeDistillateTotalFraction(zFeed, n, lkIdx, hkIdx);
      }
    }
    totalDistillateFrac = Math.min(totalDistillateFrac, 0.999);
    totalDistillateFrac = Math.max(totalDistillateFrac, 0.001);

    double D = feedMoles * totalDistillateFrac;
    double V = D * (reflux + 1.0);

    // Estimate latent heat from feed enthalpy
    double feedEnthalpy = feedFluid.getEnthalpy();
    double avgHvap = 30000.0; // J/mol approximate

    condenserDuty = -V * avgHvap;
    reboilerDuty = -condenserDuty + feedEnthalpy * 0.01; // Approximate energy balance
  }

  /**
   * Compute total distillate fraction.
   *
   * @param zFeed feed composition
   * @param n number of components
   * @param lkIdx light key index
   * @param hkIdx heavy key index
   * @return total distillate fraction
   */
  private double computeDistillateTotalFraction(double[] zFeed, int n, int lkIdx, int hkIdx) {
    double total = 0.0;
    for (int i = 0; i < n; i++) {
      if (i == lkIdx) {
        total += zFeed[i] * lightKeyRecoveryDistillate;
      } else if (i == hkIdx) {
        total += zFeed[i] * (1.0 - heavyKeyRecoveryBottoms);
      }
    }
    return total;
  }

  /**
   * Create output streams for distillate and bottoms.
   *
   * @param feedFluid feed fluid
   * @param zFeed feed composition
   * @param xD distillate composition
   * @param n number of components
   * @param lkIdx light key index
   * @param hkIdx heavy key index
   */
  private void createOutputStreams(SystemInterface feedFluid, double[] zFeed, double[] xD, int n,
      int lkIdx, int hkIdx) {
    // Create distillate fluid
    SystemInterface distFluid = feedFluid.clone();
    double[] distComp = new double[n];
    double totalD = 0.0;
    for (int i = 0; i < n; i++) {
      distComp[i] = xD[i];
      totalD += xD[i];
    }
    if (totalD > 0.0) {
      for (int i = 0; i < n; i++) {
        distFluid.getPhase(0).getComponent(i).setz(distComp[i] / totalD);
      }
    }
    if (condenserPressure > 0) {
      distFluid.setPressure(condenserPressure);
    }
    ThermodynamicOperations distOps = new ThermodynamicOperations(distFluid);
    distOps.TPflash();
    distillateStream = new Stream(getName() + " distillate", distFluid);

    // Create bottoms fluid
    SystemInterface botFluid = feedFluid.clone();
    double[] botComp = new double[n];
    double totalB = 0.0;
    for (int i = 0; i < n; i++) {
      double distFrac;
      if (i == lkIdx) {
        distFrac = lightKeyRecoveryDistillate;
      } else if (i == hkIdx) {
        distFrac = 1.0 - heavyKeyRecoveryBottoms;
      } else {
        distFrac = xD[i]; // Already normalized
      }
      botComp[i] = Math.max(zFeed[i] - zFeed[i] * distFrac, 1.0e-20);
      totalB += botComp[i];
    }
    if (totalB > 0.0) {
      for (int i = 0; i < n; i++) {
        botFluid.getPhase(0).getComponent(i).setz(botComp[i] / totalB);
      }
    }
    if (reboilerPressure > 0) {
      botFluid.setPressure(reboilerPressure);
    }
    ThermodynamicOperations botOps = new ThermodynamicOperations(botFluid);
    botOps.TPflash();
    bottomsStream = new Stream(getName() + " bottoms", botFluid);
  }

  /**
   * Get the minimum number of theoretical stages (Fenske).
   *
   * @return minimum number of stages
   */
  public double getMinimumNumberOfStages() {
    return nMin;
  }

  /**
   * Get the minimum reflux ratio (Underwood).
   *
   * @return minimum reflux ratio
   */
  public double getMinimumRefluxRatio() {
    return rMin;
  }

  /**
   * Get the actual number of theoretical stages (Gilliland).
   *
   * @return actual number of stages
   */
  public double getActualNumberOfStages() {
    return nActual;
  }

  /**
   * Get the actual reflux ratio.
   *
   * @return actual reflux ratio
   */
  public double getActualRefluxRatio() {
    return rActual;
  }

  /**
   * Get the optimal feed tray number from the top.
   *
   * @return feed tray number
   */
  public int getFeedTrayNumber() {
    return feedTrayNumber;
  }

  /**
   * Get the condenser duty in Watts.
   *
   * @return condenser duty (negative = heat removed)
   */
  public double getCondenserDuty() {
    return condenserDuty;
  }

  /**
   * Get the reboiler duty in Watts.
   *
   * @return reboiler duty (positive = heat added)
   */
  public double getReboilerDuty() {
    return reboilerDuty;
  }

  /**
   * Get the relative volatility of light key to heavy key.
   *
   * @return alpha_LK/HK
   */
  public double getRelativeVolatility() {
    return alphaLKHK;
  }

  /**
   * Get the distillate (overhead) stream.
   *
   * @return distillate stream
   */
  public StreamInterface getDistillateStream() {
    return distillateStream;
  }

  /**
   * Get the bottoms stream.
   *
   * @return bottoms stream
   */
  public StreamInterface getBottomsStream() {
    return bottomsStream;
  }

  /**
   * Check if the column has been solved.
   *
   * @return true if solved
   */
  public boolean isSolved() {
    return solved;
  }

  /**
   * Get a complete results summary as JSON string.
   *
   * @return JSON string with all FUG results
   */
  public String getResultsJson() {
    JsonObject results = new JsonObject();
    results.addProperty("name", getName());
    results.addProperty("method", "Fenske-Underwood-Gilliland (FUG)");
    results.addProperty("lightKey", lightKey);
    results.addProperty("heavyKey", heavyKey);
    results.addProperty("lightKeyRecoveryDistillate", lightKeyRecoveryDistillate);
    results.addProperty("heavyKeyRecoveryBottoms", heavyKeyRecoveryBottoms);
    results.addProperty("relativeVolatility_LK_HK", alphaLKHK);
    results.addProperty("minimumStages_Fenske", nMin);
    results.addProperty("minimumRefluxRatio_Underwood", rMin);
    results.addProperty("actualRefluxRatio", rActual);
    results.addProperty("refluxRatioMultiplier", refluxRatioMultiplier);
    results.addProperty("actualStages_Gilliland", nActual);
    results.addProperty("feedTrayFromTop_Kirkbride", feedTrayNumber);
    results.addProperty("condenserDuty_W", condenserDuty);
    results.addProperty("reboilerDuty_W", reboilerDuty);
    results.addProperty("condenserPressure_bara", condenserPressure);
    results.addProperty("reboilerPressure_bara", reboilerPressure);
    results.addProperty("solved", solved);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(results);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return getResultsJson();
  }
}
