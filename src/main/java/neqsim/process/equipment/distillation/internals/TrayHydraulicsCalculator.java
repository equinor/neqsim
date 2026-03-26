package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tray hydraulics calculator for distillation column internals sizing.
 *
 * <p>
 * Calculates per-tray hydraulics for sieve, valve, and bubble-cap trays using industry-standard
 * correlations including:
 * </p>
 * <ul>
 * <li>Flooding velocity — Fair correlation (Souders-Brown with tray spacing and FLV
 * correction)</li>
 * <li>Weeping — Sinnott correlation (minimum vapor velocity to prevent liquid weeping)</li>
 * <li>Entrainment — Fair entrainment correlation (fractional entrainment vs percent flood)</li>
 * <li>Downcomer backup — Francis weir formula and backup height calculation</li>
 * <li>Pressure drop — dry tray + liquid head + residual head</li>
 * <li>Tray efficiency — O'Connell correlation (function of relative volatility and viscosity)</li>
 * <li>Turndown ratio — ratio of minimum to design vapor rate</li>
 * </ul>
 *
 * <p>
 * References: Kister, H.Z. "Distillation Design" (1992); Ludwig, E.E. "Applied Process Design vol.
 * 2" (2001); Fair, J.R. Petro/Chem Eng. (1961); Sinnott, R.K. "Chemical Engineering Design" (2005).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TrayHydraulicsCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TrayHydraulicsCalculator.class);

  // ======================== Tray geometry inputs ========================

  /** Tray type: "sieve", "valve", or "bubble-cap". */
  private String trayType = "sieve";

  /** Column internal diameter [m]. */
  private double columnDiameter = 1.0;

  /** Tray spacing [m]. */
  private double traySpacing = 0.6;

  /** Weir height [m]. */
  private double weirHeight = 0.05;

  /** Weir length [m]. Set &lt; 0 to auto-calculate as 0.73 * diameter. */
  private double weirLength = -1.0;

  /** Downcomer area as fraction of total column area. */
  private double downcommerAreaFraction = 0.10;

  /** Hole diameter [mm] (sieve trays). */
  private double holeDiameter = 12.7;

  /** Hole area as fraction of active area (sieve trays: 0.05-0.16, typical 0.10). */
  private double holeAreaFraction = 0.10;

  /** Active area as fraction of total column area (1 - 2 * downcomer fraction). */
  private double activeAreaFraction = 0.80;

  /** Design flooding fraction (0.70-0.85 typical). */
  private double designFloodFraction = 0.80;

  // ======================== Operating conditions ========================

  /** Vapor mass flow rate [kg/s]. */
  private double vaporMassFlow = 0.0;

  /** Liquid mass flow rate [kg/s]. */
  private double liquidMassFlow = 0.0;

  /** Vapor density [kg/m3]. */
  private double vaporDensity = 1.0;

  /** Liquid density [kg/m3]. */
  private double liquidDensity = 800.0;

  /** Liquid viscosity [Pa.s]. */
  private double liquidViscosity = 0.001;

  /** Liquid surface tension [N/m]. */
  private double surfaceTension = 0.02;

  /** Relative volatility of key components. */
  private double relativeVolatility = 2.0;

  // ======================== Calculated results ========================

  /** Total column cross-sectional area [m2]. */
  private double totalArea = 0.0;

  /** Active (bubbling) area [m2]. */
  private double activeArea = 0.0;

  /** Downcomer area (one side) [m2]. */
  private double downcommerArea = 0.0;

  /** Hole area [m2] (sieve trays). */
  private double holeArea = 0.0;

  /** Flooding velocity based on net area [m/s]. */
  private double floodingVelocity = 0.0;

  /** Actual vapor velocity through net area [m/s]. */
  private double actualVaporVelocity = 0.0;

  /** Percent of flooding [%]. */
  private double percentFlood = 0.0;

  /** Minimum vapor velocity before weeping [m/s] (sieve trays). */
  private double minimumVaporVelocity = 0.0;

  /** Weeping check: true if actual velocity is above minimum. */
  private boolean weepingOk = true;

  /** Fractional liquid entrainment (mol liquid/mol vapor). */
  private double entrainment = 0.0;

  /** Entrainment check: true if entrainment &lt; 0.1 (10%). */
  private boolean entrainmentOk = true;

  /** Total downcomer backup [m of clear liquid]. */
  private double downcommerBackup = 0.0;

  /** Downcomer backup as fraction of tray spacing + weir height. */
  private double downcommerBackupFraction = 0.0;

  /** Downcomer backup check: true if backup &lt; 50% of (spacing + weir height). */
  private boolean downcommerBackupOk = true;

  /** Weir loading (crest over weir) [m3/s per m of weir length]. */
  private double weirCrest = 0.0;

  /** Dry tray pressure drop [Pa]. */
  private double dryTrayPressureDrop = 0.0;

  /** Liquid head on tray [Pa]. */
  private double liquidHeadPressureDrop = 0.0;

  /** Residual head pressure drop [Pa]. */
  private double residualHeadPressureDrop = 0.0;

  /** Total tray pressure drop [Pa]. */
  private double totalTrayPressureDrop = 0.0;

  /** Tray efficiency (O'Connell). */
  private double trayEfficiency = 0.0;

  /** Turndown ratio (min vapor/design vapor). */
  private double turndownRatio = 0.0;

  /** F-factor = u_v * sqrt(rho_v) [Pa^0.5]. */
  private double fsFactor = 0.0;

  /** Calculated weir length [m]. */
  private double calculatedWeirLength = 0.0;

  /** Overall design verdict: true if all checks pass. */
  private boolean designOk = false;

  /**
   * Default constructor.
   */
  public TrayHydraulicsCalculator() {}

  /**
   * Perform all tray hydraulic calculations.
   *
   * <p>
   * Call this after setting all geometry and operating parameters. Results are available through
   * getter methods.
   * </p>
   */
  public void calculate() {
    calculateAreas();
    calculateFloodingVelocity();
    calculateActualVelocity();
    calculateWeepingCheck();
    calculateEntrainment();
    calculatePressureDrop();
    calculateDowncommerBackup();
    calculateTrayEfficiency();
    calculateTurndownRatio();
    assessDesign();
  }

  /**
   * Calculate all tray areas from geometry.
   */
  private void calculateAreas() {
    totalArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    downcommerArea = totalArea * downcommerAreaFraction;

    // Active area = total - 2 * downcomer (one on each side for cross-flow)
    activeAreaFraction = 1.0 - 2.0 * downcommerAreaFraction;
    activeArea = totalArea * activeAreaFraction;

    // Hole area (sieve trays only)
    if ("sieve".equalsIgnoreCase(trayType)) {
      holeArea = activeArea * holeAreaFraction;
    } else if ("valve".equalsIgnoreCase(trayType)) {
      // Valve trays: open area varies; use typical full-open fraction
      holeArea = activeArea * 0.14;
    } else {
      // Bubble-cap: slot area ≈ 12% of active area
      holeArea = activeArea * 0.12;
    }

    // Weir length — auto-calculate if not set
    if (weirLength <= 0) {
      calculatedWeirLength = 0.73 * columnDiameter;
    } else {
      calculatedWeirLength = weirLength;
    }
  }

  /**
   * Calculate flooding velocity using the Fair correlation.
   *
   * <p>
   * Uses the Souders-Brown equation with the capacity factor K from the Fair plot as a function of
   * tray spacing and flow parameter (FLV). Surface tension correction is applied per the
   * Kister-Haas modification.
   * </p>
   */
  private void calculateFloodingVelocity() {
    // Flow parameter (Lockhart-Martinelli)
    double flv = 0.0;
    if (vaporMassFlow > 0 && vaporDensity > 0 && liquidDensity > 0) {
      flv = (liquidMassFlow / vaporMassFlow) * Math.sqrt(vaporDensity / liquidDensity);
    }

    // Capacity factor K from Fair correlation (simplified fit for FLV = 0.01 - 1.0)
    // K depends on tray spacing — using Ludwig/Fair tabulation fit
    double kBase = getCapacityFactor(traySpacing, flv);

    // Surface tension correction (reference: 0.020 N/m)
    double sigmaCorrected = kBase * Math.pow(surfaceTension / 0.020, 0.2);

    // Tray type correction factor
    double trayFactor = 1.0;
    if ("valve".equalsIgnoreCase(trayType)) {
      trayFactor = 1.05;
    } else if ("bubble-cap".equalsIgnoreCase(trayType)) {
      trayFactor = 0.85;
    }

    double kFinal = sigmaCorrected * trayFactor;

    // Souders-Brown: u_flood = K * sqrt((rho_L - rho_V) / rho_V)
    double deltaRho = Math.max(liquidDensity - vaporDensity, 0.1);
    floodingVelocity = kFinal * Math.sqrt(deltaRho / vaporDensity);
  }

  /**
   * Get the Fair capacity factor K as a function of tray spacing and flow parameter.
   *
   * <p>
   * Implements a curve fit to the Fair flooding correlation for sieve trays. Data from Kister
   * (1992), Table 6.3.
   * </p>
   *
   * @param spacing tray spacing [m]
   * @param flv flow parameter [-]
   * @return capacity factor K [m/s]
   */
  private double getCapacityFactor(double spacing, double flv) {
    // Base K at FLV = 0.1 for different tray spacings (from Fair chart)
    // Spacing (m): 0.15 0.23 0.30 0.46 0.61 0.91
    // K (m/s): 0.025 0.040 0.050 0.070 0.085 0.105
    double kAtFlv01;
    if (spacing <= 0.15) {
      kAtFlv01 = 0.025;
    } else if (spacing <= 0.23) {
      kAtFlv01 = 0.025 + (spacing - 0.15) / (0.23 - 0.15) * (0.040 - 0.025);
    } else if (spacing <= 0.30) {
      kAtFlv01 = 0.040 + (spacing - 0.23) / (0.30 - 0.23) * (0.050 - 0.040);
    } else if (spacing <= 0.46) {
      kAtFlv01 = 0.050 + (spacing - 0.30) / (0.46 - 0.30) * (0.070 - 0.050);
    } else if (spacing <= 0.61) {
      kAtFlv01 = 0.070 + (spacing - 0.46) / (0.61 - 0.46) * (0.085 - 0.070);
    } else if (spacing <= 0.91) {
      kAtFlv01 = 0.085 + (spacing - 0.61) / (0.91 - 0.61) * (0.105 - 0.085);
    } else {
      kAtFlv01 = 0.105 + (spacing - 0.91) * 0.02; // Extrapolate
    }

    // FLV correction — K decreases at high FLV (heavy liquid load)
    // Approximate by: K = K_base * exp(-1.463 * FLV^0.842) per Kister
    double flvClamped = Math.max(flv, 0.01);
    flvClamped = Math.min(flvClamped, 2.0);
    double flvCorrection = Math.exp(-1.463 * Math.pow(flvClamped, 0.842));

    // Normalize: at FLV=0.1 the correction is ~0.87, so K_base was at FLV=0.1
    double normalizer = Math.exp(-1.463 * Math.pow(0.1, 0.842));

    return kAtFlv01 * flvCorrection / normalizer;
  }

  /**
   * Calculate actual vapor velocity and percent flooding.
   */
  private void calculateActualVelocity() {
    // Net area = total area - one downcomer area
    double netArea = totalArea - downcommerArea;
    if (netArea <= 0 || vaporDensity <= 0) {
      actualVaporVelocity = 0.0;
      percentFlood = 0.0;
      return;
    }

    double vaporVolumeFlow = vaporMassFlow / vaporDensity;
    actualVaporVelocity = vaporVolumeFlow / netArea;
    fsFactor = actualVaporVelocity * Math.sqrt(vaporDensity);

    if (floodingVelocity > 0) {
      percentFlood = (actualVaporVelocity / floodingVelocity) * 100.0;
    }
  }

  /**
   * Check for weeping condition (sieve and valve trays).
   *
   * <p>
   * Uses the Sinnott correlation: minimum vapor velocity through holes to prevent weeping: u_min =
   * [K_w - 0.90 * (25.4 - d_h)] / sqrt(rho_v), where K_w is from the weeping chart.
   * </p>
   */
  private void calculateWeepingCheck() {
    if ("bubble-cap".equalsIgnoreCase(trayType)) {
      // Bubble-cap trays have positive liquid seal — weeping is not a concern
      minimumVaporVelocity = 0.0;
      weepingOk = true;
      return;
    }

    // Weir crest (Francis weir formula): h_ow = 750 * (Q_L / L_w)^(2/3) [mm]
    double liquidVolFlow = liquidMassFlow / liquidDensity; // m3/s
    double howMm = 0.0;
    if (calculatedWeirLength > 0) {
      howMm = 750.0 * Math.pow(liquidVolFlow / calculatedWeirLength, 2.0 / 3.0);
    }
    weirCrest = liquidVolFlow / Math.max(calculatedWeirLength, 0.01);

    // hw + how in mm
    double hwPlusHow = weirHeight * 1000.0 + howMm;

    // K_w from Sinnott weeping chart (simplified fit)
    // For hw+how = 5-100 mm, K_w ≈ 7 + 0.27 * (hw+how)^0.5
    double kw;
    if (hwPlusHow < 5.0) {
      kw = 7.0;
    } else {
      kw = 7.0 + 0.27 * Math.sqrt(hwPlusHow);
    }

    // Minimum hole velocity
    double dhMm = holeDiameter;
    double numerator = kw - 0.90 * (25.4 - dhMm);
    if (numerator < 0) {
      numerator = 0.5; // Minimum fallback
    }
    double uMinHole = numerator / Math.sqrt(Math.max(vaporDensity, 0.01));

    // Actual hole velocity
    double actualHoleVelocity = 0.0;
    if (holeArea > 0 && vaporDensity > 0) {
      actualHoleVelocity = (vaporMassFlow / vaporDensity) / holeArea;
    }

    // Convert minimum to net area basis for comparison
    if (holeArea > 0) {
      double netArea = totalArea - downcommerArea;
      minimumVaporVelocity = uMinHole * holeArea / netArea;
    } else {
      minimumVaporVelocity = 0.0;
    }

    weepingOk = actualHoleVelocity >= uMinHole;
    turndownRatio = (uMinHole > 0) ? actualHoleVelocity / uMinHole : 1.0;
  }

  /**
   * Calculate entrainment using Fair's correlation.
   *
   * <p>
   * Fractional entrainment (psi) as a function of percent flood and flow parameter. High
   * entrainment (&gt; 0.1) reduces tray efficiency.
   * </p>
   */
  private void calculateEntrainment() {
    // Fair's correlation: psi = f(percent_flood, FLV)
    // Simplified: psi ≈ exp(a + b * (% flood/100)) where a, b depend on FLV
    double flv = 0.0;
    if (vaporMassFlow > 0) {
      flv = (liquidMassFlow / vaporMassFlow) * Math.sqrt(vaporDensity / liquidDensity);
    }

    double frac = percentFlood / 100.0;
    if (frac <= 0) {
      entrainment = 0.0;
      entrainmentOk = true;
      return;
    }

    // Empirical fit to Fair's entrainment curves
    // At low FLV (< 0.02): psi ≈ 0.085 * (frac)^4.2
    // At medium FLV (0.02-0.2): psi ≈ 0.05 * (frac)^3.8
    // At high FLV (> 0.2): psi ≈ 0.03 * (frac)^3.5
    double a;
    double b;
    if (flv < 0.02) {
      a = 0.085;
      b = 4.2;
    } else if (flv < 0.2) {
      a = 0.05;
      b = 3.8;
    } else {
      a = 0.03;
      b = 3.5;
    }

    entrainment = a * Math.pow(frac, b);
    entrainment = Math.min(entrainment, 1.0);
    entrainmentOk = entrainment < 0.1;
  }

  /**
   * Calculate tray pressure drop components.
   *
   * <p>
   * Total tray DP = dry tray DP + liquid head + residual head. Dry tray DP uses the orifice
   * equation. Liquid head includes weir crest. Residual head accounts for surface tension at hole
   * rim.
   * </p>
   */
  private void calculatePressureDrop() {
    double g = 9.81;

    // 1. Dry tray pressure drop: h_dry = 51 * (u_h/Co)^2 * (rho_v/rho_l) [mm liquid]
    double orificeCoeff = getOrificeCoefficient();
    double uHole = 0.0;
    if (holeArea > 0 && vaporDensity > 0) {
      uHole = (vaporMassFlow / vaporDensity) / holeArea;
    }

    // h_dry in mm of liquid
    double hDryMm = 0.0;
    if (orificeCoeff > 0 && liquidDensity > 0) {
      hDryMm = 51.0 * Math.pow(uHole / orificeCoeff, 2) * (vaporDensity / liquidDensity);
    }
    dryTrayPressureDrop = hDryMm * liquidDensity * g / 1000.0; // Pa

    // 2. Liquid head on tray (weir height + weir crest)
    double liquidVolFlow = liquidMassFlow / liquidDensity; // m3/s
    double howMm = 0.0;
    if (calculatedWeirLength > 0) {
      howMm = 750.0 * Math.pow(liquidVolFlow / calculatedWeirLength, 2.0 / 3.0);
    }
    double hLiquidMm = weirHeight * 1000.0 + howMm;
    liquidHeadPressureDrop = hLiquidMm * liquidDensity * g / 1000.0; // Pa

    // 3. Residual head (surface tension effect)
    double hResidualMm = 0.0;
    if (holeDiameter > 0 && liquidDensity > 0) {
      // h_r = 6 * sigma / (rho_L * g * d_h) in mm liquid
      hResidualMm = 6.0 * surfaceTension * 1000.0 / (liquidDensity * g * holeDiameter / 1000.0);
    }
    residualHeadPressureDrop = hResidualMm * liquidDensity * g / 1000.0; // Pa

    totalTrayPressureDrop = dryTrayPressureDrop + liquidHeadPressureDrop + residualHeadPressureDrop;
  }

  /**
   * Get the orifice discharge coefficient for the tray type.
   *
   * @return orifice coefficient Co
   */
  private double getOrificeCoefficient() {
    // Co from Liebson correlation: depends on Ah/Aa and thickness/diameter ratio
    // Simplified values by tray type
    if ("sieve".equalsIgnoreCase(trayType)) {
      // Typical for t/d = 0.5-1.0 and Ah/Aa = 0.06-0.15
      return 0.73;
    } else if ("valve".equalsIgnoreCase(trayType)) {
      return 0.80; // Higher than sieve due to directing action
    } else {
      return 0.65; // Bubble-cap — higher resistance
    }
  }

  /**
   * Calculate downcomer backup height.
   *
   * <p>
   * Downcomer backup = tray pressure drop (as liquid head) + liquid head in downcomer + friction
   * under downcomer. Must be less than tray spacing + weir height to prevent flooding.
   * </p>
   */
  private void calculateDowncommerBackup() {
    double g = 9.81;

    // Tray DP as liquid head [m]
    double htLiquid = totalTrayPressureDrop / (liquidDensity * g);

    // Head loss under downcomer apron (Francis weir formula)
    // h_ap = 166 * (q / A_ap)^2 in mm of liquid
    double areaApron = Math.max(calculatedWeirLength * 0.025, 0.001); // gap ~25mm
    double liquidVolFlow = liquidMassFlow / Math.max(liquidDensity, 1.0);
    double hapMm = 166.0 * Math.pow(liquidVolFlow / areaApron, 2);
    double hapM = hapMm / 1000.0;

    // Liquid head in downcomer (weir height + crest)
    double howMm = 0.0;
    if (calculatedWeirLength > 0) {
      howMm = 750.0 * Math.pow(liquidVolFlow / calculatedWeirLength, 2.0 / 3.0);
    }
    double hwMm = weirHeight * 1000.0;
    double liquidInDowncomer = (hwMm + howMm) / 1000.0;

    downcommerBackup = htLiquid + liquidInDowncomer + hapM;

    double maxAllowed = traySpacing + weirHeight;
    if (maxAllowed > 0) {
      downcommerBackupFraction = downcommerBackup / maxAllowed;
    }

    // Design criterion: backup < 50% of (spacing + weir height)
    downcommerBackupOk = downcommerBackup < 0.5 * maxAllowed;
  }

  /**
   * Calculate tray efficiency using the O'Connell correlation.
   *
   * <p>
   * E_o = 51 - 32.5 * log10(alpha * mu_L) where alpha is relative volatility and mu_L is liquid
   * viscosity in centipoise. Valid range: alpha*mu from 0.1 to 10.
   * </p>
   */
  private void calculateTrayEfficiency() {
    // mu_L in cP (input is Pa.s)
    double muCp = liquidViscosity * 1000.0;

    double alphaMu = relativeVolatility * muCp;
    alphaMu = Math.max(alphaMu, 0.1);
    alphaMu = Math.min(alphaMu, 10.0);

    // O'Connell (1946) — percent efficiency
    double eoPercent = 51.0 - 32.5 * Math.log10(alphaMu);
    eoPercent = Math.max(eoPercent, 10.0);
    eoPercent = Math.min(eoPercent, 100.0);

    trayEfficiency = eoPercent / 100.0;
  }

  /**
   * Calculate turndown ratio.
   */
  private void calculateTurndownRatio() {
    if (actualVaporVelocity > 0 && minimumVaporVelocity > 0) {
      turndownRatio = actualVaporVelocity / minimumVaporVelocity;
    }
  }

  /**
   * Assess overall design feasibility.
   */
  private void assessDesign() {
    designOk = weepingOk && entrainmentOk && downcommerBackupOk && percentFlood >= 50.0
        && percentFlood <= 85.0;
    if (!designOk) {
      StringBuilder sb = new StringBuilder("Tray hydraulics issues: ");
      if (!weepingOk) {
        sb.append("[WEEPING: vapor velocity below minimum] ");
      }
      if (!entrainmentOk) {
        sb.append("[ENTRAINMENT: > 10%, tray spacing or diameter too small] ");
      }
      if (!downcommerBackupOk) {
        sb.append("[DOWNCOMER BACKUP: > 50% of spacing, increase diameter or spacing] ");
      }
      if (percentFlood > 85.0) {
        sb.append("[FLOODING: > 85%, increase column diameter] ");
      }
      if (percentFlood < 50.0) {
        sb.append("[LOW LOAD: < 50% flood, column oversized or turndown issue] ");
      }
      logger.warn(sb.toString());
    }
  }

  /**
   * Size column diameter for a given design flooding fraction.
   *
   * <p>
   * Given the operating conditions, calculates the minimum column diameter required to stay below
   * the design flooding fraction. The diameter is rounded up to the nearest standard vessel size.
   * </p>
   *
   * @return required column diameter [m]
   */
  public double sizeColumnDiameter() {
    // First calculate flooding velocity using a trial diameter
    double trialDia = 1.0;
    columnDiameter = trialDia;
    calculateAreas();
    calculateFloodingVelocity();

    // Required net area at design flood fraction
    double vaporVolFlow = vaporMassFlow / Math.max(vaporDensity, 0.01);
    double uDesign = floodingVelocity * designFloodFraction;
    if (uDesign <= 0) {
      return 1.0;
    }

    double requiredNetArea = vaporVolFlow / uDesign;
    double requiredTotalArea = requiredNetArea / (1.0 - downcommerAreaFraction);
    double reqDiameter = Math.sqrt(4.0 * requiredTotalArea / Math.PI);

    // Round to standard diameter
    columnDiameter = roundToStandardDiameter(reqDiameter);
    return columnDiameter;
  }

  /**
   * Round diameter to nearest standard vessel size (API/ASME).
   *
   * @param diameter raw diameter in meters
   * @return nearest standard diameter in meters (rounded up)
   */
  private double roundToStandardDiameter(double diameter) {
    double[] standardSizes = {0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.5, 1.6, 1.8, 2.0, 2.2,
        2.4, 2.6, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0, 4.5, 5.0, 5.5, 6.0, 7.0, 8.0, 9.0, 10.0};
    for (double stdSize : standardSizes) {
      if (stdSize >= diameter) {
        return stdSize;
      }
    }
    return Math.ceil(diameter * 2.0) / 2.0; // Round to nearest 0.5 m
  }

  // ========================== Getters and Setters ==========================

  /**
   * Get the tray type.
   *
   * @return tray type string
   */
  public String getTrayType() {
    return trayType;
  }

  /**
   * Set tray type: "sieve", "valve", or "bubble-cap".
   *
   * @param trayType the tray type
   */
  public void setTrayType(String trayType) {
    this.trayType = trayType;
  }

  /**
   * Get column diameter [m].
   *
   * @return column diameter
   */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /**
   * Set column diameter [m].
   *
   * @param columnDiameter column internal diameter
   */
  public void setColumnDiameter(double columnDiameter) {
    this.columnDiameter = columnDiameter;
  }

  /**
   * Get tray spacing [m].
   *
   * @return tray spacing
   */
  public double getTraySpacing() {
    return traySpacing;
  }

  /**
   * Set tray spacing [m].
   *
   * @param traySpacing tray spacing
   */
  public void setTraySpacing(double traySpacing) {
    this.traySpacing = traySpacing;
  }

  /**
   * Get weir height [m].
   *
   * @return weir height
   */
  public double getWeirHeight() {
    return weirHeight;
  }

  /**
   * Set weir height [m].
   *
   * @param weirHeight weir height
   */
  public void setWeirHeight(double weirHeight) {
    this.weirHeight = weirHeight;
  }

  /**
   * Set weir length [m]. Use -1 for automatic calculation (0.73 * diameter).
   *
   * @param weirLength weir length or -1 for auto
   */
  public void setWeirLength(double weirLength) {
    this.weirLength = weirLength;
  }

  /**
   * Get the downcomer area fraction.
   *
   * @return downcomer area fraction
   */
  public double getDowncommerAreaFraction() {
    return downcommerAreaFraction;
  }

  /**
   * Set the downcomer area fraction (typically 0.08-0.12).
   *
   * @param fraction downcomer area fraction
   */
  public void setDowncommerAreaFraction(double fraction) {
    this.downcommerAreaFraction = fraction;
  }

  /**
   * Set hole diameter [mm] (sieve trays).
   *
   * @param holeDiameter hole diameter in mm
   */
  public void setHoleDiameter(double holeDiameter) {
    this.holeDiameter = holeDiameter;
  }

  /**
   * Set hole area fraction (sieve trays, typical 0.05-0.16).
   *
   * @param fraction hole area as fraction of active area
   */
  public void setHoleAreaFraction(double fraction) {
    this.holeAreaFraction = fraction;
  }

  /**
   * Set design flooding fraction (0.70-0.85).
   *
   * @param fraction design flooding fraction
   */
  public void setDesignFloodFraction(double fraction) {
    this.designFloodFraction = fraction;
  }

  /**
   * Set vapor mass flow [kg/s].
   *
   * @param flow vapor mass flow rate
   */
  public void setVaporMassFlow(double flow) {
    this.vaporMassFlow = flow;
  }

  /**
   * Set liquid mass flow [kg/s].
   *
   * @param flow liquid mass flow rate
   */
  public void setLiquidMassFlow(double flow) {
    this.liquidMassFlow = flow;
  }

  /**
   * Set vapor density [kg/m3].
   *
   * @param density vapor density
   */
  public void setVaporDensity(double density) {
    this.vaporDensity = density;
  }

  /**
   * Set liquid density [kg/m3].
   *
   * @param density liquid density
   */
  public void setLiquidDensity(double density) {
    this.liquidDensity = density;
  }

  /**
   * Set liquid viscosity [Pa.s].
   *
   * @param viscosity liquid dynamic viscosity
   */
  public void setLiquidViscosity(double viscosity) {
    this.liquidViscosity = viscosity;
  }

  /**
   * Set liquid surface tension [N/m].
   *
   * @param tension surface tension
   */
  public void setSurfaceTension(double tension) {
    this.surfaceTension = tension;
  }

  /**
   * Set relative volatility of key components.
   *
   * @param alpha relative volatility
   */
  public void setRelativeVolatility(double alpha) {
    this.relativeVolatility = alpha;
  }

  // ========================== Result getters ==========================

  /**
   * Get flooding velocity [m/s].
   *
   * @return flooding velocity based on net area
   */
  public double getFloodingVelocity() {
    return floodingVelocity;
  }

  /**
   * Get actual vapor velocity through net area [m/s].
   *
   * @return actual vapor velocity
   */
  public double getActualVaporVelocity() {
    return actualVaporVelocity;
  }

  /**
   * Get percent of flooding [%].
   *
   * @return percent flood
   */
  public double getPercentFlood() {
    return percentFlood;
  }

  /**
   * Get minimum vapor velocity to prevent weeping [m/s].
   *
   * @return minimum velocity based on net area
   */
  public double getMinimumVaporVelocity() {
    return minimumVaporVelocity;
  }

  /**
   * Check if tray is above the weeping point.
   *
   * @return true if no weeping
   */
  public boolean isWeepingOk() {
    return weepingOk;
  }

  /**
   * Get fractional entrainment.
   *
   * @return fractional liquid entrainment (mol/mol)
   */
  public double getEntrainment() {
    return entrainment;
  }

  /**
   * Check if entrainment is below the 10% threshold.
   *
   * @return true if entrainment is acceptable
   */
  public boolean isEntrainmentOk() {
    return entrainmentOk;
  }

  /**
   * Get the total downcomer backup [m of clear liquid].
   *
   * @return downcomer backup height
   */
  public double getDowncommerBackup() {
    return downcommerBackup;
  }

  /**
   * Get downcomer backup as fraction of available height.
   *
   * @return downcomer backup fraction
   */
  public double getDowncommerBackupFraction() {
    return downcommerBackupFraction;
  }

  /**
   * Check if downcomer backup is within limits.
   *
   * @return true if backup is acceptable
   */
  public boolean isDowncommerBackupOk() {
    return downcommerBackupOk;
  }

  /**
   * Get total tray pressure drop [Pa].
   *
   * @return total tray pressure drop
   */
  public double getTotalTrayPressureDrop() {
    return totalTrayPressureDrop;
  }

  /**
   * Get total tray pressure drop in mbar.
   *
   * @return total tray pressure drop in mbar
   */
  public double getTotalTrayPressureDropMbar() {
    return totalTrayPressureDrop / 100.0;
  }

  /**
   * Get dry tray pressure drop [Pa].
   *
   * @return dry tray pressure drop
   */
  public double getDryTrayPressureDrop() {
    return dryTrayPressureDrop;
  }

  /**
   * Get liquid head pressure drop component [Pa].
   *
   * @return liquid head pressure drop
   */
  public double getLiquidHeadPressureDrop() {
    return liquidHeadPressureDrop;
  }

  /**
   * Get residual head pressure drop component [Pa].
   *
   * @return residual head pressure drop
   */
  public double getResidualHeadPressureDrop() {
    return residualHeadPressureDrop;
  }

  /**
   * Get tray efficiency (O'Connell).
   *
   * @return tray efficiency (0-1)
   */
  public double getTrayEfficiency() {
    return trayEfficiency;
  }

  /**
   * Get the F-factor (Fs = u_v * sqrt(rho_v)).
   *
   * @return F-factor [Pa^0.5]
   */
  public double getFsFactor() {
    return fsFactor;
  }

  /**
   * Get the turndown ratio (min velocity / actual velocity).
   *
   * @return turndown ratio
   */
  public double getTurndownRatio() {
    return turndownRatio;
  }

  /**
   * Get the calculated weir length [m].
   *
   * @return weir length
   */
  public double getCalculatedWeirLength() {
    return calculatedWeirLength;
  }

  /**
   * Get the active area [m2].
   *
   * @return active bubbling area
   */
  public double getActiveArea() {
    return activeArea;
  }

  /**
   * Get the total column area [m2].
   *
   * @return total cross-sectional area
   */
  public double getTotalArea() {
    return totalArea;
  }

  /**
   * Get the hole area [m2].
   *
   * @return hole (or slot) area
   */
  public double getHoleArea() {
    return holeArea;
  }

  /**
   * Get the downcomer area (one side) [m2].
   *
   * @return downcomer area
   */
  public double getDowncommerArea() {
    return downcommerArea;
  }

  /**
   * Check if overall design is feasible.
   *
   * @return true if all hydraulic checks pass
   */
  public boolean isDesignOk() {
    return designOk;
  }
}
