package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Packing hydraulics calculator for packed distillation columns.
 *
 * <p>
 * Calculates hydraulic performance and HETP for random and structured packing using
 * industry-standard correlations:
 * </p>
 * <ul>
 * <li>Flooding velocity — Eckert generalized correlation (GPDC chart)</li>
 * <li>Pressure drop — Leva correlation for wet packing</li>
 * <li>HETP — Onda correlation for mass transfer coefficients, then HETP from HTU</li>
 * <li>Liquid distribution — minimum wetting rate check</li>
 * <li>Loading/flooding transition</li>
 * </ul>
 *
 * <p>
 * References: Eckert, J.S. Chem. Eng. Prog. (1970); Leva, M. Chem. Eng. Prog. (1954); Onda, K. J.
 * Chem. Eng. Japan (1968); Kister, H.Z. "Distillation Design" (1992); Billet, R. "Packed Towers"
 * (1995).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackingHydraulicsCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PackingHydraulicsCalculator.class);

  // ======================== Packing specification ========================

  /** Packing category: "random" or "structured". */
  private String packingCategory = "random";

  /** Packing type name (e.g., "Pall-Ring-50", "Mellapak-250Y"). */
  private String packingName = "Pall-Ring-50";

  /** Packing specific surface area [m2/m3]. */
  private double specificSurfaceArea = 120.0;

  /** Packing void fraction (porosity) [-]. */
  private double voidFraction = 0.94;

  /** Packing factor Fp [m-1] (used in Eckert GPDC correlation). */
  private double packingFactor = 66.0;

  /** Nominal packing size [mm] (for random packing). */
  private double nominalSize = 50.0;

  /** Critical surface tension of packing material [N/m] (for Onda). */
  private double criticalSurfaceTension = 0.061; // Metal ≈ 0.075, ceramic ≈ 0.061, plastic ≈ 0.033

  // ======================== Column geometry ========================

  /** Column internal diameter [m]. */
  private double columnDiameter = 1.0;

  /** Packed bed height [m]. */
  private double packedHeight = 5.0;

  /** Design flooding fraction (typically 0.65-0.75 for packed columns). */
  private double designFloodFraction = 0.70;

  // ======================== Operating conditions ========================

  /** Vapor mass flow rate [kg/s]. */
  private double vaporMassFlow = 0.0;

  /** Liquid mass flow rate [kg/s]. */
  private double liquidMassFlow = 0.0;

  /** Vapor density [kg/m3]. */
  private double vaporDensity = 1.0;

  /** Liquid density [kg/m3]. */
  private double liquidDensity = 800.0;

  /** Vapor viscosity [Pa.s]. */
  private double vaporViscosity = 1.0e-5;

  /** Liquid viscosity [Pa.s]. */
  private double liquidViscosity = 0.001;

  /** Liquid surface tension [N/m]. */
  private double surfaceTension = 0.02;

  /** Vapor diffusivity [m2/s]. */
  private double vaporDiffusivity = 1.0e-5;

  /** Liquid diffusivity [m2/s]. */
  private double liquidDiffusivity = 1.0e-9;

  // ======================== Calculated results ========================

  /** Flooding gas velocity [m/s]. */
  private double floodingVelocity = 0.0;

  /** Actual gas superficial velocity [m/s]. */
  private double actualVelocity = 0.0;

  /** Percent of flooding [%]. */
  private double percentFlood = 0.0;

  /** Pressure drop [Pa/m of packing]. */
  private double pressureDropPerMeter = 0.0;

  /** Total pressure drop [Pa]. */
  private double totalPressureDrop = 0.0;

  /** Gas-phase mass transfer coefficient [mol/(m2.s.Pa)] or [1/s]. */
  private double kGa = 0.0;

  /** Liquid-phase mass transfer coefficient [m/s]. */
  private double kLa = 0.0;

  /** Wetted specific area [m2/m3]. */
  private double wettedArea = 0.0;

  /** Height of a gas-phase transfer unit [m]. */
  private double htuG = 0.0;

  /** Height of a liquid-phase transfer unit [m]. */
  private double htuL = 0.0;

  /** Height of an overall gas-phase transfer unit [m]. */
  private double htuOG = 0.0;

  /** HETP — Height Equivalent to a Theoretical Plate [m]. */
  private double hetp = 0.0;

  /** Number of theoretical stages in packed height. */
  private double numberOfTheoreticalStages = 0.0;

  /** Minimum liquid wetting rate [m3/(m2.s)]. */
  private double minimumWettingRate = 0.0;

  /** Actual liquid superficial velocity [m/s]. */
  private double actualLiquidVelocity = 0.0;

  /** Wetting check: true if liquid rate exceeds minimum. */
  private boolean wettingOk = true;

  /** F-factor = u_v * sqrt(rho_v) [Pa^0.5]. */
  private double fsFactor = 0.0;

  /** Overall design verdict. */
  private boolean designOk = false;

  /**
   * Default constructor.
   */
  public PackingHydraulicsCalculator() {}

  /**
   * Perform all packing hydraulic calculations.
   *
   * <p>
   * Call after setting packing specs and operating conditions. Results available through getters.
   * </p>
   */
  public void calculate() {
    calculateFloodingVelocity();
    calculateActualVelocity();
    calculatePressureDrop();
    calculateMassTransfer();
    calculateHETP();
    calculateWettingCheck();
    assessDesign();
  }

  /**
   * Calculate flooding velocity using the Eckert Generalized Pressure Drop Correlation (GPDC).
   *
   * <p>
   * The GPDC relates the flow parameter FLV to the capacity parameter Y at flooding. The
   * Sherwood/Leva/Eckert chart is fitted with a polynomial in log-log space.
   * </p>
   */
  private void calculateFloodingVelocity() {
    // Flow parameter
    double flv = 0.0;
    if (vaporMassFlow > 0) {
      flv = (liquidMassFlow / vaporMassFlow) * Math.sqrt(vaporDensity / liquidDensity);
    }
    flv = Math.max(flv, 0.005);
    flv = Math.min(flv, 5.0);

    // GPDC flooding line: Y_flood = f(FLV)
    // Y = u_v^2 * Fp * rho_v / (g * (rho_L - rho_v)) * (mu_L/mu_water)^0.1
    // At flooding, log10(Y_flood) = a0 + a1*X + a2*X^2 + a3*X^3
    // where X = log10(FLV)
    // Coefficients from Kister/Eckert flooding fit:
    double x = Math.log10(flv);
    double logYflood = -1.668 - 1.085 * x - 0.297 * x * x;

    double yFlood = Math.pow(10.0, logYflood);

    // Solve for u_v_flood from Y
    double g = 9.81;
    double rhoFactor = vaporDensity / (g * (liquidDensity - vaporDensity));
    double muFactor = Math.pow(liquidViscosity / 0.001, 0.1); // Reference: water viscosity

    double uFloodSq = yFlood / (packingFactor * rhoFactor * muFactor);
    floodingVelocity = Math.sqrt(Math.max(uFloodSq, 0.0));
  }

  /**
   * Calculate actual velocity and percent flooding.
   */
  private void calculateActualVelocity() {
    double columnArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    if (columnArea <= 0 || vaporDensity <= 0) {
      actualVelocity = 0.0;
      return;
    }

    actualVelocity = (vaporMassFlow / vaporDensity) / columnArea;
    fsFactor = actualVelocity * Math.sqrt(vaporDensity);

    if (floodingVelocity > 0) {
      percentFlood = (actualVelocity / floodingVelocity) * 100.0;
    }

    actualLiquidVelocity = (liquidMassFlow / liquidDensity) / columnArea;
  }

  /**
   * Calculate pressure drop using the Leva correlation for irrigated packing.
   *
   * <p>
   * Uses the dry packing Ergun-type equation with a Leva wet correction factor that increases with
   * liquid loading.
   * </p>
   */
  private void calculatePressureDrop() {
    double g = 9.81;

    // Dry packing pressure drop (Ergun equation adapted for packing):
    // dP/dz = (a1 * Fp * rho_v * uv^2 / epsilon^3) * (1 + a2 * (uL/uV))
    double a1 = 1.0; // Empirical coefficient
    double a2 = 40.0; // Liquid loading correction

    // Simplified Leva approach: dP/dz_wet = dP/dz_dry * 10^(C * L)
    // where L is liquid mass rate per unit area [kg/(m2.s)]
    double columnArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    double liquidLoading = (columnArea > 0) ? liquidMassFlow / columnArea : 0.0;

    // Dry pressure drop per meter
    double dryDp = a1 * packingFactor * vaporDensity * actualVelocity * actualVelocity
        / Math.pow(voidFraction, 3) * 0.01; // Pa/m, empirical scale

    // Wet correction factor (Leva): increases exponentially with liquid loading
    double levaC = 0.015; // Typical for metal packing
    if ("structured".equalsIgnoreCase(packingCategory)) {
      levaC = 0.010; // Lower for structured packing
    }
    double wetFactor = Math.pow(10.0, levaC * liquidLoading);

    pressureDropPerMeter = dryDp * wetFactor;

    // Clamp to reasonable range (100-3000 Pa/m for normal operation)
    pressureDropPerMeter = Math.max(pressureDropPerMeter, 0.0);
    if (pressureDropPerMeter > 3000.0) {
      logger.warn("Packing pressure drop exceeds 3000 Pa/m — likely at or above flooding");
    }

    totalPressureDrop = pressureDropPerMeter * packedHeight;
  }

  /**
   * Calculate mass transfer coefficients using the Onda correlation.
   *
   * <p>
   * The Onda correlation (1968) predicts gas and liquid phase mass transfer coefficients and the
   * effective wetted area for random and structured packings.
   * </p>
   */
  private void calculateMassTransfer() {
    double g = 9.81;
    double columnArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    if (columnArea <= 0) {
      return;
    }

    // Superficial mass velocities [kg/(m2.s)]
    double gV = vaporMassFlow / columnArea;
    double gL = liquidMassFlow / columnArea;
    if (gV <= 0 || gL <= 0) {
      return;
    }

    // Liquid superficial velocity [m/s]
    double uL = gL / liquidDensity;

    // === Wetted area (Onda) ===
    // aw/a = 1 - exp(-1.45 * (sigma_c/sigma)^0.75 * (uL*a*rhoL/(muL*g))^0.1
    // * (uL^2*a/g)^(-0.05) * (uL^2*rhoL/(sigma*a))^0.2)
    double reL = uL * liquidDensity / (liquidViscosity * specificSurfaceArea); // Modified Re_L
    double frL = uL * uL * specificSurfaceArea / g;
    double weL = uL * uL * liquidDensity / (surfaceTension * specificSurfaceArea);
    double sigmaRatio = criticalSurfaceTension / surfaceTension;

    double exponent = -1.45 * Math.pow(sigmaRatio, 0.75) * Math.pow(reL, 0.1) * Math.pow(frL, -0.05)
        * Math.pow(weL, 0.2);
    double awRatio = 1.0 - Math.exp(exponent);
    awRatio = Math.max(awRatio, 0.2);
    awRatio = Math.min(awRatio, 1.0);
    wettedArea = awRatio * specificSurfaceArea;

    // === Gas-phase mass transfer coefficient (Onda) ===
    // kG * (a * dp)^2 / D_G = C * (G_V/(a*mu_V))^0.7 * (mu_V/(rho_V*D_G))^(1/3) * (a*dp)^(-2)
    // Simplified for engineering use:
    double reV = gV / (specificSurfaceArea * vaporViscosity);
    double scV = vaporViscosity / (vaporDensity * vaporDiffusivity);
    double dp = getEffectivePackingDiameter();

    // kG*a = C * (a * D_G / dp^2) * Re_V^0.7 * Sc_V^(1/3)
    double cKg = 5.23; // Onda constant for metal packing
    kGa = cKg * (specificSurfaceArea * vaporDiffusivity / (dp * dp)) * Math.pow(reV, 0.7)
        * Math.pow(scV, 1.0 / 3.0);

    // === Liquid-phase mass transfer coefficient (Onda) ===
    // kL * (rho_L / (mu_L * g))^(1/3) = 0.0051 * (L/(aw*mu_L))^(2/3) * (mu_L/(rho_L*D_L))^(-1/2) *
    // (a*dp)^0.4
    double reL2 = gL / (wettedArea * liquidViscosity);
    double scL = liquidViscosity / (liquidDensity * liquidDiffusivity);

    double lhsFactor = Math.pow(liquidDensity / (liquidViscosity * g), 1.0 / 3.0);
    kLa = 0.0051 * Math.pow(reL2, 2.0 / 3.0) * Math.pow(scL, -0.5)
        * Math.pow(specificSurfaceArea * dp, 0.4) / lhsFactor;
    kLa = kLa * wettedArea; // volumetric coefficient [1/s]
  }

  /**
   * Calculate HETP from mass transfer coefficients.
   *
   * <p>
   * Uses the two-resistance model: 1/KOG = 1/kG + m/kL, then HTU_OG = G/(KOG * a * P), and HETP =
   * HTU_OG * ln(lambda) / (lambda - 1), where lambda = m * G_m / L_m is the stripping factor.
   * </p>
   */
  private void calculateHETP() {
    double columnArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    if (columnArea <= 0 || vaporDensity <= 0 || liquidDensity <= 0) {
      hetp = estimateHETP();
      numberOfTheoreticalStages = (hetp > 0) ? packedHeight / hetp : 0;
      return;
    }

    double gV = (columnArea > 0) ? vaporMassFlow / columnArea : 0;
    double gL = (columnArea > 0) ? liquidMassFlow / columnArea : 0;

    if (kGa <= 0 || kLa <= 0 || gV <= 0 || gL <= 0) {
      hetp = estimateHETP();
      numberOfTheoreticalStages = (hetp > 0) ? packedHeight / hetp : 0;
      return;
    }

    // HTU_G = G_V / (kG * a * rho_V)
    htuG = gV / (kGa * vaporDensity);

    // HTU_L = L_V / (kL * a * rho_L)
    htuL = gL / kLa;

    // Stripping factor lambda = m * V_m / L_m (approximate m ≈ 1 for middle of column)
    // Use a typical stripping factor of ~1.0 for distillation
    double lambda = 1.0;

    // HTU_OG = HTU_G + (1/lambda) * HTU_L
    htuOG = htuG + (1.0 / lambda) * htuL;

    // HETP = HTU_OG * ln(lambda) / (lambda - 1)
    // For lambda close to 1.0: HETP ≈ HTU_OG (limit of the formula)
    if (Math.abs(lambda - 1.0) < 0.01) {
      hetp = htuOG;
    } else {
      hetp = htuOG * Math.log(lambda) / (lambda - 1.0);
    }

    // Clamp HETP to reasonable range
    hetp = Math.max(hetp, 0.1);

    // Apply empirical bounds by packing type
    double maxHetp = estimateHETP() * 2.0;
    if (hetp > maxHetp) {
      hetp = maxHetp;
    }

    numberOfTheoreticalStages = (hetp > 0) ? packedHeight / hetp : 0;
  }

  /**
   * Estimate HETP from empirical rules of thumb when mass transfer calculation is not feasible.
   *
   * @return estimated HETP [m]
   */
  private double estimateHETP() {
    if ("structured".equalsIgnoreCase(packingCategory)) {
      // Structured packing: HETP ≈ 100/a + 0.1 (Kister rule)
      double hetpEst = 100.0 / specificSurfaceArea + 0.1;
      return Math.max(hetpEst, 0.15);
    } else {
      // Random packing: HETP ≈ 0.12 + 0.012 * dp (mm) per Kister/Strigle rules
      // Typical: 25mm Pall Ring → 0.42m, 50mm Pall Ring → 0.72m
      double hetpEst = 0.12 + 0.012 * nominalSize;
      if (columnDiameter > 1.0) {
        hetpEst *= (1.0 + 0.1 * (columnDiameter - 1.0));
      }
      return Math.max(hetpEst, 0.15);
    }
  }

  /**
   * Get effective packing diameter for mass-transfer correlations.
   *
   * <p>
   * Random packings normally provide a nominal size. Structured packings often do not, so an
   * equivalent hydraulic diameter {@code 4 epsilon / a} is used as a finite fallback.
   * </p>
   *
   * @return effective packing diameter in metres
   */
  private double getEffectivePackingDiameter() {
    if (nominalSize > 0.0) {
      return nominalSize / 1000.0;
    }
    if (specificSurfaceArea > 0.0 && voidFraction > 0.0) {
      return Math.max(4.0 * voidFraction / specificSurfaceArea, 1.0e-4);
    }
    return 0.025;
  }

  /**
   * Check minimum liquid wetting rate.
   */
  private void calculateWettingCheck() {
    // Minimum wetting rate: MWR = 1.5e-5 m3/(m2.s) for unglazed ceramic
    // MWR = 5e-5 for metal, plastic
    if ("structured".equalsIgnoreCase(packingCategory)) {
      minimumWettingRate = 2.5e-5; // m3/(m2.s) — structured more sensitive
    } else {
      minimumWettingRate = 5.0e-5; // m3/(m2.s) — random packing typical
    }

    double columnArea = Math.PI / 4.0 * columnDiameter * columnDiameter;
    double actualRate = 0.0;
    if (columnArea > 0 && liquidDensity > 0 && specificSurfaceArea > 0) {
      actualRate = (liquidMassFlow / liquidDensity) / (columnArea * specificSurfaceArea);
    }

    wettingOk = actualRate >= minimumWettingRate;
    if (!wettingOk) {
      logger.warn("Liquid rate below minimum wetting rate for packing. " + "Actual: " + actualRate
          + " m3/(m2.s), Min: " + minimumWettingRate);
    }
  }

  /**
   * Assess overall design.
   */
  private void assessDesign() {
    designOk = wettingOk && percentFlood >= 40.0 && percentFlood <= 80.0;
    if (!designOk) {
      StringBuilder sb = new StringBuilder("Packing hydraulics issues: ");
      if (!wettingOk) {
        sb.append("[WETTING: liquid rate below minimum for packing] ");
      }
      if (percentFlood > 80.0) {
        sb.append("[FLOODING: > 80% of packing flood, increase diameter] ");
      }
      if (percentFlood < 40.0) {
        sb.append("[LOW LOAD: < 40% flood, column oversized] ");
      }
      logger.warn(sb.toString());
    }
  }

  /**
   * Size column diameter for packed column at the design flood fraction.
   *
   * @return required column diameter [m]
   */
  public double sizeColumnDiameter() {
    double trialDia = 1.0;
    columnDiameter = trialDia;
    calculateFloodingVelocity();

    double vaporVolFlow = vaporMassFlow / Math.max(vaporDensity, 0.01);
    double uDesign = floodingVelocity * designFloodFraction;
    if (uDesign <= 0) {
      return 1.0;
    }

    double requiredArea = vaporVolFlow / uDesign;
    double reqDiameter = Math.sqrt(4.0 * requiredArea / Math.PI);

    // Round up to standard
    columnDiameter = roundToStandardDiameter(reqDiameter);
    return columnDiameter;
  }

  /**
   * Round diameter to nearest standard vessel size.
   *
   * @param diameter raw diameter [m]
   * @return standard diameter [m]
   */
  private double roundToStandardDiameter(double diameter) {
    double[] standardSizes = {0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.5, 1.6, 1.8,
        2.0, 2.2, 2.4, 2.6, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0, 4.5, 5.0, 5.5, 6.0, 7.0, 8.0};
    for (double stdSize : standardSizes) {
      if (stdSize >= diameter) {
        return stdSize;
      }
    }
    return Math.ceil(diameter * 2.0) / 2.0;
  }

  // ========================== Packing Presets ==========================

  /**
   * Set packing to a standard random packing type.
   *
   * <p>
   * Supported presets are provided by {@link PackingSpecificationLibrary}. The old hard-coded names
   * remain available, and additional entries can be supplied through
   * {@code designdata/Packing.csv}.
   * </p>
   *
   * @param preset packing name preset
   */
  public void setPackingPreset(String preset) {
    PackingSpecification specification = PackingSpecificationLibrary.get(preset);
    if (specification == null) {
      logger.warn("Unknown packing preset: " + preset + ". Using Pall-Ring-50 defaults.");
      specification = PackingSpecificationLibrary.getOrDefault("Pall-Ring-50");
    }
    setPackingSpecification(specification);
  }

  /**
   * Set packing to a standard structured packing type.
   *
   * <p>
   * Supported presets are provided by {@link PackingSpecificationLibrary}. The old hard-coded names
   * remain available, and additional entries can be supplied through
   * {@code designdata/Packing.csv}.
   * </p>
   *
   * @param preset structured packing name preset
   */
  public void setStructuredPackingPreset(String preset) {
    PackingSpecification specification = PackingSpecificationLibrary.get(preset);
    if (specification == null) {
      logger
          .warn("Unknown structured packing preset: " + preset + ". Using Mellapak-250Y defaults.");
      specification = PackingSpecificationLibrary.getOrDefault("Mellapak-250Y");
    }
    setPackingSpecification(specification);
  }

  /**
   * Apply a reusable packing specification.
   *
   * @param specification packing specification to apply
   * @throws IllegalArgumentException if the specification is null
   */
  public void setPackingSpecification(PackingSpecification specification) {
    if (specification == null) {
      throw new IllegalArgumentException("packing specification can not be null");
    }
    packingCategory = specification.getCategory();
    packingName = specification.getName();
    specificSurfaceArea = specification.getSpecificSurfaceArea();
    voidFraction = specification.getVoidFraction();
    packingFactor = specification.getPackingFactor();
    nominalSize = specification.getNominalSizeMm();
    criticalSurfaceTension = specification.getCriticalSurfaceTension();
  }

  // ========================== Getters and Setters ==========================

  /**
   * Get packing category ("random" or "structured").
   *
   * @return packing category
   */
  public String getPackingCategory() {
    return packingCategory;
  }

  /**
   * Set packing category.
   *
   * @param category "random" or "structured"
   */
  public void setPackingCategory(String category) {
    this.packingCategory = category;
  }

  /**
   * Get packing name.
   *
   * @return packing name
   */
  public String getPackingName() {
    return packingName;
  }

  /**
   * Set packing name.
   *
   * @param name packing name
   */
  public void setPackingName(String name) {
    this.packingName = name;
  }

  /**
   * Get specific surface area [m2/m3].
   *
   * @return specific surface area
   */
  public double getSpecificSurfaceArea() {
    return specificSurfaceArea;
  }

  /**
   * Set specific surface area [m2/m3].
   *
   * @param area specific surface area
   */
  public void setSpecificSurfaceArea(double area) {
    this.specificSurfaceArea = area;
  }

  /**
   * Get void fraction (porosity).
   *
   * @return void fraction
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Set void fraction.
   *
   * @param fraction void fraction
   */
  public void setVoidFraction(double fraction) {
    this.voidFraction = fraction;
  }

  /**
   * Get packing factor [m-1].
   *
   * @return packing factor
   */
  public double getPackingFactor() {
    return packingFactor;
  }

  /**
   * Set packing factor [m-1].
   *
   * @param factor packing factor
   */
  public void setPackingFactor(double factor) {
    this.packingFactor = factor;
  }

  /**
   * Set nominal packing size [mm].
   *
   * @param size nominal size
   */
  public void setNominalSize(double size) {
    this.nominalSize = size;
  }

  /**
   * Set critical surface tension of packing material [N/m].
   *
   * @param tension critical surface tension
   */
  public void setCriticalSurfaceTension(double tension) {
    this.criticalSurfaceTension = tension;
  }

  /**
   * Set column diameter [m].
   *
   * @param diameter column diameter
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
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
   * Set packed bed height [m].
   *
   * @param height packed height
   */
  public void setPackedHeight(double height) {
    this.packedHeight = height;
  }

  /**
   * Get packed bed height [m].
   *
   * @return packed height
   */
  public double getPackedHeight() {
    return packedHeight;
  }

  /**
   * Set design flooding fraction (typical 0.65-0.75).
   *
   * @param fraction design flooding fraction
   */
  public void setDesignFloodFraction(double fraction) {
    this.designFloodFraction = fraction;
  }

  /**
   * Set vapor mass flow [kg/s].
   *
   * @param flow vapor mass flow
   */
  public void setVaporMassFlow(double flow) {
    this.vaporMassFlow = flow;
  }

  /**
   * Set liquid mass flow [kg/s].
   *
   * @param flow liquid mass flow
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
   * Set vapor viscosity [Pa.s].
   *
   * @param viscosity vapor viscosity
   */
  public void setVaporViscosity(double viscosity) {
    this.vaporViscosity = viscosity;
  }

  /**
   * Set liquid viscosity [Pa.s].
   *
   * @param viscosity liquid viscosity
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
   * Set vapor diffusivity [m2/s].
   *
   * @param diffusivity vapor diffusivity
   */
  public void setVaporDiffusivity(double diffusivity) {
    this.vaporDiffusivity = diffusivity;
  }

  /**
   * Set liquid diffusivity [m2/s].
   *
   * @param diffusivity liquid diffusivity
   */
  public void setLiquidDiffusivity(double diffusivity) {
    this.liquidDiffusivity = diffusivity;
  }

  // ========================== Result Getters ==========================

  /**
   * Get flooding velocity [m/s].
   *
   * @return flooding velocity
   */
  public double getFloodingVelocity() {
    return floodingVelocity;
  }

  /**
   * Get actual gas superficial velocity [m/s].
   *
   * @return actual velocity
   */
  public double getActualVelocity() {
    return actualVelocity;
  }

  /**
   * Get percent of flooding.
   *
   * @return percent flood
   */
  public double getPercentFlood() {
    return percentFlood;
  }

  /**
   * Get pressure drop per meter of packing [Pa/m].
   *
   * @return pressure drop per meter
   */
  public double getPressureDropPerMeter() {
    return pressureDropPerMeter;
  }

  /**
   * Get total pressure drop [Pa].
   *
   * @return total pressure drop
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Get HETP [m].
   *
   * @return height equivalent to a theoretical plate
   */
  public double getHETP() {
    return hetp;
  }

  /**
   * Get number of theoretical stages in the packed bed.
   *
   * @return number of theoretical stages
   */
  public double getNumberOfTheoreticalStages() {
    return numberOfTheoreticalStages;
  }

  /**
   * Get gas-phase mass transfer coefficient [1/s].
   *
   * @return kG*a
   */
  public double getKGa() {
    return kGa;
  }

  /**
   * Get liquid-phase mass transfer coefficient [1/s].
   *
   * @return kL*a
   */
  public double getKLa() {
    return kLa;
  }

  /**
   * Get wetted specific area [m2/m3].
   *
   * @return wetted area
   */
  public double getWettedArea() {
    return wettedArea;
  }

  /**
   * Get HTU_G [m].
   *
   * @return gas-phase height of transfer unit
   */
  public double getHtuG() {
    return htuG;
  }

  /**
   * Get HTU_L [m].
   *
   * @return liquid-phase height of transfer unit
   */
  public double getHtuL() {
    return htuL;
  }

  /**
   * Get HTU_OG [m].
   *
   * @return overall gas-phase height of transfer unit
   */
  public double getHtuOG() {
    return htuOG;
  }

  /**
   * Get F-factor [Pa^0.5].
   *
   * @return F-factor
   */
  public double getFsFactor() {
    return fsFactor;
  }

  /**
   * Check if wetting is adequate.
   *
   * @return true if above minimum wetting rate
   */
  public boolean isWettingOk() {
    return wettingOk;
  }

  /**
   * Check if overall design is feasible.
   *
   * @return true if all checks pass
   */
  public boolean isDesignOk() {
    return designOk;
  }
}
