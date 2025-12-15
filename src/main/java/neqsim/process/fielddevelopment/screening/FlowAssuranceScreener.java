package neqsim.process.fielddevelopment.screening;

import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Flow assurance screener for rapid envelope-based assessment.
 *
 * <p>
 * This class provides automated screening of flow assurance risks for field development concepts.
 * It uses NeqSim's thermodynamic engine to calculate phase behavior and identify potential
 * production issues that could impact facility design and operating strategy.
 * </p>
 *
 * <h2>Screening Categories</h2>
 * <p>
 * The screener evaluates the following risk categories:
 * </p>
 * <ul>
 * <li><b>Hydrate formation</b>: Uses CPA equation of state for accurate hydrate equilibrium
 * temperature prediction. Critical for subsea tiebacks and cold ambient conditions.</li>
 * <li><b>Wax deposition</b>: Estimates Wax Appearance Temperature (WAT) based on fluid type.
 * Important for waxy crudes in cold environments.</li>
 * <li><b>Asphaltene precipitation</b>: Flags risk based on fluid type and GOR. Particularly
 * relevant near bubble point conditions.</li>
 * <li><b>Corrosion</b>: Evaluates CO2 and H2S content against industry thresholds for material
 * selection (NACE MR0175 compliance).</li>
 * <li><b>Scale formation</b>: Assesses risk based on water cut and water injection mixing.</li>
 * <li><b>Erosion</b>: Flags high-rate wells that may require velocity management.</li>
 * </ul>
 *
 * <h2>Result Classification</h2>
 * <p>
 * Each risk category is classified as:
 * </p>
 * <ul>
 * <li>{@link FlowAssuranceResult#PASS}: No mitigation required</li>
 * <li>{@link FlowAssuranceResult#MARGINAL}: Monitoring or simple mitigation recommended</li>
 * <li>{@link FlowAssuranceResult#FAIL}: Active mitigation mandatory</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * FlowAssuranceScreener screener = new FlowAssuranceScreener();
 * 
 * // Full screening with specific conditions
 * FlowAssuranceReport report = screener.screen(concept, 4.0, 150.0); // 4°C seabed, 150 bara
 * 
 * // Quick screening with default conditions
 * FlowAssuranceReport quickReport = screener.quickScreen(concept);
 * 
 * // Check specific risks
 * if (report.getHydrateResult() == FlowAssuranceResult.FAIL) {
 *   System.out.println("Hydrate mitigation required: " + report.getHydrateMargin() + "°C margin");
 *   report.getMitigationOptions().forEach((k, v) -&gt; System.out.println("  - " + v));
 * }
 * </pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 * <li>Fluid compositions are estimated from fluid type; use actual PVT for detailed design</li>
 * <li>WAT calculations are correlation-based; laboratory measurements recommended</li>
 * <li>Asphaltene stability requires specialized analysis (e.g., SARA fractionation)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see FlowAssuranceReport
 * @see FlowAssuranceResult
 */
public class FlowAssuranceScreener {

  // ============================================================================
  // CONSTANTS - HYDRATE AND WAX MARGINS
  // ============================================================================

  /**
   * Temperature margin below which hydrate risk is classified as PASS (°C). Operating temperature
   * must be this much above hydrate formation temperature.
   */
  private static final double HYDRATE_SAFE_MARGIN_C = 5.0;

  /**
   * Temperature margin below which hydrate risk is classified as MARGINAL (°C). Between this and
   * HYDRATE_SAFE_MARGIN_C requires monitoring.
   */
  private static final double HYDRATE_MARGINAL_MARGIN_C = 2.0;

  /**
   * Temperature margin for WAT above which wax risk is classified as PASS (°C). Operating
   * temperature must be this much above wax appearance temperature.
   */
  private static final double WAX_SAFE_MARGIN_C = 10.0;

  /**
   * Temperature margin for WAT below which wax risk is classified as MARGINAL (°C). Between this
   * and WAX_SAFE_MARGIN_C requires monitoring.
   */
  private static final double WAX_MARGINAL_MARGIN_C = 5.0;

  // ============================================================================
  // CONSTANTS - CORROSION THRESHOLDS
  // ============================================================================

  /**
   * CO2 concentration above which high corrosion risk is flagged (mol%). Requires CRA materials or
   * continuous inhibition.
   */
  private static final double CO2_HIGH_CORROSION_PERCENT = 3.0;

  /**
   * CO2 concentration above which marginal corrosion risk is flagged (mol%). Consider corrosion
   * allowance.
   */
  private static final double CO2_MARGINAL_CORROSION_PERCENT = 1.0;

  /**
   * H2S concentration above which high corrosion/SSC risk is flagged (ppm). Requires full sour
   * service design per NACE MR0175.
   */
  private static final double H2S_HIGH_CORROSION_PPM = 100.0;

  /**
   * H2S concentration above which marginal sour service is required (ppm). NACE MR0175 material
   * selection required.
   */
  private static final double H2S_MARGINAL_CORROSION_PPM = 20.0;

  /**
   * Creates a new flow assurance screener.
   *
   * <p>
   * The screener is stateless and can be reused for multiple concept evaluations.
   * </p>
   */
  public FlowAssuranceScreener() {
    // Default constructor
  }

  /**
   * Performs flow assurance screening for a field concept.
   *
   * <p>
   * This method creates a representative fluid composition based on the concept's reservoir
   * properties and performs thermodynamic calculations to assess flow assurance risks. The method
   * evaluates all risk categories and returns a comprehensive report.
   * </p>
   *
   * <h3>Thermodynamic Calculations</h3>
   * <ul>
   * <li>Uses SystemSrkCPAstatoil for accurate hydrate calculations when water is present</li>
   * <li>Falls back to SystemSrkEos for dry gas systems</li>
   * <li>Estimates phase behavior from fluid type classification</li>
   * </ul>
   *
   * @param concept field concept to screen (must have reservoir input)
   * @param minAmbientTempC minimum ambient temperature in °C (e.g., seabed temperature for subsea,
   *        winter air temperature for topsides)
   * @param operatingPressureBara operating pressure in bara (typically wellhead or separator
   *        pressure)
   * @return comprehensive flow assurance report with all risk assessments
   * @throws IllegalArgumentException if concept has no reservoir input
   */
  public FlowAssuranceReport screen(FieldConcept concept, double minAmbientTempC,
      double operatingPressureBara) {
    ReservoirInput reservoir = concept.getReservoir();
    if (reservoir == null) {
      throw new IllegalArgumentException("Concept must have reservoir input");
    }

    FlowAssuranceReport.Builder builder = FlowAssuranceReport.builder();
    builder.minOperatingTemp(minAmbientTempC);

    // Create representative fluid for calculations
    SystemInterface fluid =
        createRepresentativeFluid(reservoir, minAmbientTempC, operatingPressureBara);

    // Screen hydrate risk
    screenHydrateRisk(builder, fluid, minAmbientTempC, operatingPressureBara);

    // Screen wax risk
    screenWaxRisk(builder, reservoir, minAmbientTempC);

    // Screen asphaltene risk
    screenAsphalteneRisk(builder, reservoir);

    // Screen corrosion risk
    screenCorrosionRisk(builder, reservoir);

    // Screen scaling risk
    screenScalingRisk(builder, reservoir, concept);

    // Screen erosion risk
    screenErosionRisk(builder, concept);

    return builder.build();
  }

  /**
   * Performs quick screening using estimated fluid properties.
   *
   * @param concept field concept
   * @return flow assurance report
   */
  public FlowAssuranceReport quickScreen(FieldConcept concept) {
    // Use typical subsea conditions
    double minTemp = concept.isSubseaTieback() ? 4.0 : 15.0;
    double pressure = concept.getWells() != null ? concept.getWells().getTubeheadPressure() : 80.0;
    return screen(concept, minTemp, pressure);
  }

  private SystemInterface createRepresentativeFluid(ReservoirInput reservoir, double tempC,
      double pressureBara) {
    SystemInterface fluid;

    // Use CPA for accurate hydrate calculations if water present
    // Temperature input is in Kelvin
    double tempK = tempC + 273.15;
    if (reservoir.getWaterCutPercent() > 0) {
      fluid = new SystemSrkCPAstatoil(tempK, pressureBara);
    } else {
      fluid = new SystemSrkEos(tempK, pressureBara);
    }

    // Add components based on fluid type
    switch (reservoir.getFluidType()) {
      case LEAN_GAS:
        fluid.addComponent("methane", 0.90);
        fluid.addComponent("ethane", 0.05);
        fluid.addComponent("propane", 0.02);
        fluid.addComponent("n-butane", 0.01);
        break;
      case RICH_GAS:
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.03);
        fluid.addComponent("n-pentane", 0.02);
        break;
      case GAS_CONDENSATE:
        fluid.addComponent("methane", 0.70);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.06);
        fluid.addComponent("n-butane", 0.04);
        fluid.addComponent("n-pentane", 0.03);
        fluid.addComponent("n-hexane", 0.02);
        fluid.addComponent("n-heptane", 0.02);
        break;
      case VOLATILE_OIL:
      case BLACK_OIL:
      case HEAVY_OIL:
        fluid.addComponent("methane", 0.40);
        fluid.addComponent("ethane", 0.05);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.05);
        fluid.addComponent("n-pentane", 0.05);
        fluid.addComponent("n-hexane", 0.05);
        fluid.addComponent("n-heptane", 0.10);
        fluid.addComponent("n-octane", 0.10);
        fluid.addComponent("nC10", 0.10);
        break;
      default:
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.05);
        fluid.addComponent("propane", 0.03);
    }

    // Add CO2 if present
    if (reservoir.getCo2Percent() > 0.1) {
      double co2Frac = reservoir.getCo2Percent() / 100.0;
      fluid.addComponent("CO2", co2Frac);
    }

    // Add H2S if present
    if (reservoir.getH2SPercent() > 0.001) {
      double h2sFrac = reservoir.getH2SPercent() / 100.0;
      fluid.addComponent("H2S", h2sFrac);
    }

    // Add water for hydrate calculations
    if (reservoir.getWaterCutPercent() > 0) {
      fluid.addComponent("water", 0.02);
    }

    fluid.setMixingRule("classic");
    fluid.createDatabase(true);

    return fluid;
  }

  private void screenHydrateRisk(FlowAssuranceReport.Builder builder, SystemInterface fluid,
      double minTempC, double pressureBara) {
    try {
      // Calculate hydrate formation temperature
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();

      double hydrateFormTempC = fluid.getTemperature() - 273.15;
      builder.hydrateFormationTemp(hydrateFormTempC);

      // Calculate margin
      double margin = minTempC - hydrateFormTempC;
      builder.hydrateMargin(margin);

      // Classify result
      if (margin > HYDRATE_SAFE_MARGIN_C) {
        builder.hydrateResult(FlowAssuranceResult.PASS);
      } else if (margin > HYDRATE_MARGINAL_MARGIN_C) {
        builder.hydrateResult(FlowAssuranceResult.MARGINAL);
        builder.addRecommendation("hydrate",
            "Consider MEG injection or insulation for shutdown scenarios");
        builder.addMitigationOption("hydrate_meg", "MEG injection (continuous or intermittent)");
        builder.addMitigationOption("hydrate_insulation", "Pipe insulation");
      } else {
        builder.hydrateResult(FlowAssuranceResult.FAIL);
        builder.addRecommendation("hydrate",
            "Hydrate mitigation mandatory - MEG injection or electrical heating");
        builder.addMitigationOption("hydrate_meg", "Continuous MEG injection");
        builder.addMitigationOption("hydrate_heating", "Electrical heating (DEH/ETH)");
        builder.addMitigationOption("hydrate_ldhi", "Low dosage hydrate inhibitor (LDHI)");
      }
    } catch (Exception e) {
      // If calculation fails, use conservative estimate based on pressure
      double estimatedHydrateTemp = 5.0 + pressureBara / 10.0; // Simple correlation
      builder.hydrateFormationTemp(estimatedHydrateTemp);
      builder.hydrateMargin(minTempC - estimatedHydrateTemp);
      builder.hydrateResult(FlowAssuranceResult.MARGINAL);
      builder.addRecommendation("hydrate",
          "Hydrate calculation estimated - detailed analysis required");
    }
  }

  private void screenWaxRisk(FlowAssuranceReport.Builder builder, ReservoirInput reservoir,
      double minTempC) {
    // Estimate WAT based on fluid type (simplified correlation)
    double estimatedWAT;
    switch (reservoir.getFluidType()) {
      case LEAN_GAS:
      case RICH_GAS:
        // Gases typically don't have wax issues
        builder.waxResult(FlowAssuranceResult.PASS);
        builder.waxMargin(Double.POSITIVE_INFINITY);
        return;
      case GAS_CONDENSATE:
        estimatedWAT = 15.0; // Low wax potential
        break;
      case VOLATILE_OIL:
        estimatedWAT = 25.0;
        break;
      case BLACK_OIL:
        estimatedWAT = 35.0;
        break;
      case HEAVY_OIL:
        estimatedWAT = 45.0;
        break;
      default:
        estimatedWAT = 30.0;
    }

    builder.waxAppearanceTemp(estimatedWAT);
    double margin = minTempC - estimatedWAT;
    builder.waxMargin(margin);

    if (margin > WAX_SAFE_MARGIN_C) {
      builder.waxResult(FlowAssuranceResult.PASS);
    } else if (margin > WAX_MARGINAL_MARGIN_C) {
      builder.waxResult(FlowAssuranceResult.MARGINAL);
      builder.addRecommendation("wax", "Monitor for wax buildup, consider pigging frequency");
      builder.addMitigationOption("wax_pigging", "Regular pigging");
    } else {
      builder.waxResult(FlowAssuranceResult.FAIL);
      builder.addRecommendation("wax", "Wax management required - insulation and/or chemical");
      builder.addMitigationOption("wax_insulation", "Pipe insulation to maintain temperature");
      builder.addMitigationOption("wax_inhibitor", "Wax inhibitor injection");
      builder.addMitigationOption("wax_pigging", "Frequent pigging with heating");
    }
  }

  private void screenAsphalteneRisk(FlowAssuranceReport.Builder builder, ReservoirInput reservoir) {
    // Asphaltene risk based on fluid type and pressure drop
    switch (reservoir.getFluidType()) {
      case LEAN_GAS:
      case RICH_GAS:
      case GAS_CONDENSATE:
        builder.asphalteneResult(FlowAssuranceResult.PASS);
        break;
      case VOLATILE_OIL:
        builder.asphalteneResult(FlowAssuranceResult.MARGINAL);
        builder.addRecommendation("asphaltene",
            "Monitor for asphaltene precipitation near bubble point");
        break;
      case BLACK_OIL:
      case HEAVY_OIL:
        // Heavy oils with high asphaltene content
        if (reservoir.getGor() < 50) {
          builder.asphalteneResult(FlowAssuranceResult.MARGINAL);
          builder.addRecommendation("asphaltene",
              "Low GOR heavy oil - evaluate asphaltene stability");
        } else {
          builder.asphalteneResult(FlowAssuranceResult.PASS);
        }
        break;
      default:
        builder.asphalteneResult(FlowAssuranceResult.PASS);
    }
  }

  private void screenCorrosionRisk(FlowAssuranceReport.Builder builder, ReservoirInput reservoir) {
    double co2 = reservoir.getCo2Percent();
    double h2s = reservoir.getH2SPercent() * 10000; // Convert to ppm

    FlowAssuranceResult co2Risk;
    FlowAssuranceResult h2sRisk;

    // CO2 corrosion assessment
    if (co2 < CO2_MARGINAL_CORROSION_PERCENT) {
      co2Risk = FlowAssuranceResult.PASS;
    } else if (co2 < CO2_HIGH_CORROSION_PERCENT) {
      co2Risk = FlowAssuranceResult.MARGINAL;
      builder.addRecommendation("co2_corrosion", "Consider corrosion allowance or CRA materials");
    } else {
      co2Risk = FlowAssuranceResult.FAIL;
      builder.addRecommendation("co2_corrosion",
          "High CO2 - CRA materials or continuous inhibition required");
      builder.addMitigationOption("cra_materials", "Corrosion resistant alloys (13Cr, 22Cr)");
      builder.addMitigationOption("corrosion_inhibitor", "Continuous corrosion inhibitor");
    }

    // H2S corrosion/SSC assessment
    if (h2s < H2S_MARGINAL_CORROSION_PPM) {
      h2sRisk = FlowAssuranceResult.PASS;
    } else if (h2s < H2S_HIGH_CORROSION_PPM) {
      h2sRisk = FlowAssuranceResult.MARGINAL;
      builder.addRecommendation("h2s_corrosion", "Sour service - NACE MR0175 materials required");
    } else {
      h2sRisk = FlowAssuranceResult.FAIL;
      builder.addRecommendation("h2s_corrosion",
          "High H2S - full sour service design, consider H2S removal");
      builder.addMitigationOption("sour_materials", "Full sour service metallurgy");
      builder.addMitigationOption("h2s_scavenger", "H2S scavenger injection");
    }

    builder.corrosionResult(co2Risk.combine(h2sRisk));
  }

  private void screenScalingRisk(FlowAssuranceReport.Builder builder, ReservoirInput reservoir,
      FieldConcept concept) {
    // Scale risk based on water cut and injection
    if (reservoir.getWaterCutPercent() < 5) {
      builder.scalingResult(FlowAssuranceResult.PASS);
    } else if (concept.hasWaterInjection()) {
      // Mixing formation and injection water increases scale risk
      builder.scalingResult(FlowAssuranceResult.MARGINAL);
      builder.addRecommendation("scale",
          "Water injection mixing - evaluate barium/strontium sulfate scale");
      builder.addMitigationOption("scale_inhibitor", "Scale inhibitor squeeze or continuous");
    } else if (reservoir.getWaterCutPercent() > 50) {
      builder.scalingResult(FlowAssuranceResult.MARGINAL);
      builder.addRecommendation("scale", "High water cut - monitor for carbonate scale");
    } else {
      builder.scalingResult(FlowAssuranceResult.PASS);
    }
  }

  private void screenErosionRisk(FlowAssuranceReport.Builder builder, FieldConcept concept) {
    // Erosion risk based on production rate and sand
    if (concept.getWells() != null) {
      double rate = concept.getWells().getRatePerWellSm3d();
      if (rate > 5.0e6) {
        // Very high rate
        builder.erosionResult(FlowAssuranceResult.MARGINAL);
        builder.addRecommendation("erosion", "High flow rates - verify erosional velocity limits");
        builder.addMitigationOption("erosion_velocity", "Velocity control via pipe sizing");
      } else {
        builder.erosionResult(FlowAssuranceResult.PASS);
      }
    } else {
      builder.erosionResult(FlowAssuranceResult.PASS);
    }
  }
}
