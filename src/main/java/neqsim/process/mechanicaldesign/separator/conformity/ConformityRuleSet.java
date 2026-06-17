package neqsim.process.mechanicaldesign.separator.conformity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;

/**
 * Defines conformity rules for a specific design standard.
 *
 * <p>
 * Each rule set defines what checks to perform based on the standard (TR3500,
 * Shell DEP, API 12J,
 * NORSOK P-002) and what internals are installed. The checks are
 * internals-aware: inlet devices
 * trigger momentum checks, demisting cyclones trigger drainage checks, mesh
 * pads trigger mesh
 * K-value checks, etc.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * ConformityRuleSet rules = ConformityRuleSet.create("TR3500");
 * ConformityReport report = rules.evaluate(scrubberMechDesign);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public abstract class ConformityRuleSet implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String name;

  /**
   * Constructs a ConformityRuleSet.
   *
   * @param name the standard name
   */
  protected ConformityRuleSet(String name) {
    this.name = name;
  }

  /**
   * Creates a rule set for the named standard.
   *
  * @param standardName the standard identifier: "TR3500", "TR1965", "API-12J",
  *        "Shell-DEP", "NORSOK-P002"
   * @return a ConformityRuleSet for the named standard
   * @throws IllegalArgumentException if the standard is not recognized
   */
  public static ConformityRuleSet create(String standardName) {
    if (standardName == null) {
      throw new IllegalArgumentException("Standard name cannot be null");
    }
    String normalized = standardName.trim().toUpperCase().replace(" ", "").replace("_", "");
    if (normalized.equals("TR3500") || normalized.equals("EQUINORTR3500")) {
      return new TR3500RuleSet();
    }
    if (normalized.equals("TR1965") || normalized.equals("EQUINORTR1965")) {
      return new TR1965RuleSet();
    }
    throw new IllegalArgumentException("Unknown conformity standard: " + standardName
        + ". Supported: TR3500, TR1965");
  }

  /**
   * Gets the standard name.
   *
   * @return the standard name
   */
  public String getName() {
    return name;
  }

  /**
   * Evaluates all applicable conformity checks against the given mechanical
   * design.
   *
   * <p>
   * The checks run depend on what internals are installed. The method reads
   * operating conditions
   * from the scrubber's current fluid state (after the most recent run).
   * </p>
   *
   * @param design the scrubber mechanical design to check
   * @return a conformity report with all check results
   */
  public abstract ConformityReport evaluate(GasScrubberMechanicalDesign design);

  /**
   * Returns the names of CapacityConstraints that this standard defines.
   *
   * <p>
   * These can be used to enable the corresponding constraints on the Separator
   * via
   * {@code separator.enableConstraints(...)}.
   * </p>
   *
   * @param design the mechanical design (to check which internals are installed)
   * @return list of constraint names to enable
   */
  public abstract List<String> getConstraintNames(GasScrubberMechanicalDesign design);

  // =====================================================================
  // TR3500 Implementation
  // =====================================================================

  /**
   * Equinor TR3500 conformity rules for gas scrubbers.
   *
   * <p>
   * Always checks:
   * </p>
   * <ul>
   * <li>K-factor (Souders-Brown) vs limit (depends on internals type)</li>
   * </ul>
   *
   * <p>
   * If inlet vane or inlet cyclones installed:
   * </p>
   * <ul>
   * <li>Inlet nozzle momentum vs limit</li>
   * </ul>
   *
   * <p>
   * If demisting cyclones installed:
   * </p>
   * <ul>
   * <li>Drainage head available vs required</li>
   * <li>Cyclone pressure drop to drain</li>
   * </ul>
   *
   * <p>
   * If mesh pad installed:
   * </p>
   * <ul>
   * <li>Mesh pad gas velocity (K-value through mesh area)</li>
   * </ul>
   */
  private static class TR3500RuleSet extends ConformityRuleSet {
    private static final long serialVersionUID = 1L;

    /** Maximum K-factor for scrubbers [m/s]. */
    private static final double K_FACTOR_LIMIT = 0.15;

    /** Maximum inlet nozzle momentum [Pa]. */
    private static final double INLET_MOMENTUM_LIMIT = 15000.0;

    /** Maximum mesh K-value [m/s]. */
    private static final double MESH_K_VALUE_LIMIT = 0.27;

    /**
     * Constructs a TR3500RuleSet.
     */
    TR3500RuleSet() {
      super("TR3500");
    }

    /** {@inheritDoc} */
    @Override
    public ConformityReport evaluate(GasScrubberMechanicalDesign design) {
      Separator sep = (Separator) design.getProcessEquipment();
      ConformityReport report = new ConformityReport(sep.getName(), getName());

      // Read operating conditions from the separator's current fluid state
      neqsim.thermo.system.SystemInterface fluid = sep.getThermoSystem();
      if (fluid == null) {
        return report;
      }
      fluid.initPhysicalProperties();

      double gasDensity = fluid.getPhase(0).getPhysicalProperties().getDensity();
      double gasFlowM3s = fluid.getPhase(0).getFlowRate("m3/sec");

      double liquidDensity = 1000.0; // default for dry gas
      if (fluid.getNumberOfPhases() >= 2) {
        if (fluid.hasPhaseType("oil")) {
          liquidDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();
        } else if (fluid.hasPhaseType("aqueous")) {
          liquidDensity = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
        }
      }

      // --- Vessel-level checks (ALWAYS) ---

      // K-factor (Souders-Brown)
      double vesselArea = Math.PI * Math.pow(design.getInnerDiameter() / 2.0, 2);
      double gasVelocity = vesselArea > 0 ? gasFlowM3s / vesselArea : 0;
      double kFactor = gasVelocity * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
      report.addResult(new ConformityResult("k-factor", getName(), "",
          kFactor, K_FACTOR_LIMIT, "m/s", ConformityResult.LimitDirection.MAXIMUM,
          "Souders-Brown K-factor at vessel cross-section"));

      // --- Inlet device checks ---
      if (design.hasInletCyclones() || design.getInletNozzleID() > 0) {
        double inletArea = Math.PI * Math.pow(design.getInletNozzleID() / 2.0, 2);
        double mixedDensity = gasDensity; // simplified; could weight by volume fraction
        if (fluid.getNumberOfPhases() >= 2) {
          double totalMassFlow = fluid.getFlowRate("kg/sec");
          double totalVolFlow = fluid.getPhase(0).getFlowRate("m3/sec");
          for (int i = 1; i < fluid.getNumberOfPhases(); i++) {
            totalVolFlow += fluid.getPhase(i).getFlowRate("m3/sec");
          }
          mixedDensity = totalVolFlow > 0 ? totalMassFlow / totalVolFlow : gasDensity;
        }
        double inletVelocity = inletArea > 0 ? gasFlowM3s / inletArea : 0;
        double inletMomentum = mixedDensity * inletVelocity * inletVelocity;
        report.addResult(new ConformityResult("inlet-momentum", getName(), "inlet-device",
            inletMomentum, INLET_MOMENTUM_LIMIT, "Pa", ConformityResult.LimitDirection.MAXIMUM,
            "Inlet nozzle momentum (rho*v^2)"));
      }

      // --- Demisting cyclones checks ---
      if (design.hasDemistingCyclones()) {
        // Drainage head check
        double cycloneDeckBottom = design.getCycloneDeckElevationM();
        double laHH = design.getLaHHElevationM();
        if (cycloneDeckBottom > 0 && laHH > 0) {
          double drainageHead = (cycloneDeckBottom - laHH) * 1000.0; // m to mm

          // Required drainage from cyclone dP
          int nCyclones = design.getNumberOfDemistingCyclones();
          double cycloneDiameter = design.getDemistingCycloneDiameterM();
          double cycloneArea = nCyclones * Math.PI * Math.pow(cycloneDiameter / 2.0, 2);
          double gasMomentumPerCyclone = cycloneArea > 0
              ? gasDensity * Math.pow(gasFlowM3s / cycloneArea, 2)
              : 0;
          double cycloneDpTotal = design.getCycloneEulerNumber() * gasMomentumPerCyclone;
          double cycloneDpToDrain = cycloneDpTotal * design.getCycloneDpToDrainPct() / 100.0;
          double requiredDrainage = liquidDensity > 0 ? cycloneDpToDrain / (liquidDensity * 9.81) * 1000.0 : 0;

          report.addResult(new ConformityResult("drainage-head", getName(), "demisting-cyclones",
              drainageHead, requiredDrainage, "mm", ConformityResult.LimitDirection.MINIMUM,
              "Available drainage head above LA(HH) vs required"));

          // Cyclone dP to drain
          report.addResult(new ConformityResult("cyclone-dp-to-drain", getName(),
              "demisting-cyclones",
              cycloneDpToDrain / 100.0, 50.0, "mbar", ConformityResult.LimitDirection.MAXIMUM,
              "Cyclone pressure drop available to drain"));
        } else {
          report.addResult(ConformityResult.notApplicable("drainage-head", getName(),
              "Cyclone deck or LA(HH) elevation not set"));
        }
      }

      // --- Mesh pad checks ---
      if (design.hasMeshPad()) {
        double meshArea = design.getMeshPadAreaM2();
        double meshGasVelocity = meshArea > 0 ? gasFlowM3s / meshArea : 0;
        double meshKValue = meshGasVelocity * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
        report.addResult(new ConformityResult("mesh-k-value", getName(), "mesh-pad",
            meshKValue, MESH_K_VALUE_LIMIT, "m/s", ConformityResult.LimitDirection.MAXIMUM,
            "K-value through mesh pad area"));
      }

      return report;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getConstraintNames(GasScrubberMechanicalDesign design) {
      List<String> names = new ArrayList<String>();
      names.add("gasLoadFactor");
      names.add("kValue");
      if (design.hasInletCyclones() || design.getInletNozzleID() > 0) {
        names.add("inletMomentum");
      }
      return names;
    }
  }

  // =====================================================================
  // TR1965 Implementation
  // =====================================================================

  /**
   * Equinor TR1965 gas scrubber conformity rules.
   *
   * <p>
   * The rule set covers gas-load K-factor limits by scrubber internals type, documented outlet
   * liquid entrainment, documented liquid design margin, and geometric distance checks when the
   * project has configured the relevant elevations on {@link GasScrubberMechanicalDesign}.
   * </p>
   */
  private static class TR1965RuleSet extends ConformityRuleSet {
    private static final long serialVersionUID = 1000L;

    /** Maximum K-factor for conventional mesh scrubbers [m/s]. */
    private static final double MESH_K_LIMIT = 0.11;

    /** Maximum K-factor for mesh plus axial-flow cyclone or vane pack scrubbers [m/s]. */
    private static final double MESH_WITH_AFC_OR_VANE_K_LIMIT = 0.15;

    /** Maximum K-factor for compact axial-flow cyclone scrubbers [m/s]. */
    private static final double COMPACT_CYCLONE_K_LIMIT = 0.90;

    /** Maximum liquid entrainment to gas outlet [litre/MSm3]. */
    private static final double MAX_LIQUID_ENTRAINMENT_LITRE_PER_MSM3 = 20.0;

    /** Minimum gas design margin fraction. */
    private static final double MIN_GAS_MARGIN_FRACTION = 0.10;

    /** Minimum liquid design margin fraction. */
    private static final double MIN_LIQUID_MARGIN_FRACTION = 0.20;

    /** Minimum HHLL to inlet device distance [m]. */
    private static final double MIN_HHLL_TO_INLET_M = 0.50;

    /** Minimum inlet device to mesh pad distance [m]. */
    private static final double MIN_INLET_TO_MESH_M = 0.90;

    /** Surface tension reference below which K-factor derating is applied [mN/m]. */
    private static final double SURFACE_TENSION_DERATING_REFERENCE_MN_PER_M = 10.0;

    /**
     * Constructs a TR1965RuleSet.
     */
    TR1965RuleSet() {
      super("TR1965");
    }

    /** {@inheritDoc} */
    @Override
    public ConformityReport evaluate(GasScrubberMechanicalDesign design) {
      Separator separator = (Separator) design.getProcessEquipment();
      ConformityReport report = new ConformityReport(separator.getName(), getName());
      neqsim.thermo.system.SystemInterface fluid = separator.getThermoSystem();
      if (fluid == null) {
        return report;
      }
      fluid.initPhysicalProperties();

      double gasDensity = getGasDensity(fluid);
      double liquidDensity = getLiquidDensity(fluid);
      double gasFlowM3s = getGasFlowRateM3s(fluid);
      double kFactor = calculateKFactor(design.getInnerDiameter(), gasFlowM3s, gasDensity,
          liquidDensity);
      double surfaceTensionMNPerM = getSurfaceTensionMNPerM(fluid);
      double deratingFactor = getSurfaceTensionDeratingFactor(surfaceTensionMNPerM);
      double kFactorLimit = selectKFactorLimit(design) * deratingFactor;

      report.addResult(new ConformityResult("tr1965-k-factor", getName(), "gas-load",
          kFactor, kFactorLimit, "m/s", ConformityResult.LimitDirection.MAXIMUM,
          "TR1965 gas-load K-factor limit including low-surface-tension derating"));

      if (Double.isFinite(kFactor) && kFactor > 0.0) {
        double gasMargin = kFactorLimit / kFactor - 1.0;
        report.addResult(new ConformityResult("tr1965-gas-design-margin", getName(),
            "gas-load", gasMargin, MIN_GAS_MARGIN_FRACTION, "fraction",
            ConformityResult.LimitDirection.MINIMUM,
            "Gas design margin between effective TR1965 limit and operating K-factor"));
      } else {
        report.addResult(ConformityResult.notApplicable("tr1965-gas-design-margin", getName(),
            "K-factor could not be calculated from the current fluid and vessel geometry"));
      }

      if (Double.isFinite(design.getLiquidEntrainmentLitresPerMSm3())) {
        report.addResult(new ConformityResult("tr1965-liquid-entrainment", getName(),
            "gas-outlet", design.getLiquidEntrainmentLitresPerMSm3(),
            MAX_LIQUID_ENTRAINMENT_LITRE_PER_MSM3, "litre/MSm3",
            ConformityResult.LimitDirection.MAXIMUM,
            "Documented liquid entrainment to gas outlet"));
      } else {
        report.addResult(ConformityResult.notApplicable("tr1965-liquid-entrainment", getName(),
            "Liquid entrainment value has not been configured on the mechanical design"));
      }

      if (Double.isFinite(design.getLiquidDesignMarginFraction())) {
        report.addResult(new ConformityResult("tr1965-liquid-design-margin", getName(),
            "liquid-handling", design.getLiquidDesignMarginFraction(), MIN_LIQUID_MARGIN_FRACTION,
            "fraction", ConformityResult.LimitDirection.MINIMUM,
            "Documented liquid handling design margin"));
      } else {
        report.addResult(ConformityResult.notApplicable("tr1965-liquid-design-margin", getName(),
            "Liquid design margin has not been configured on the mechanical design"));
      }

      addDistanceChecks(design, report);
      return report;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getConstraintNames(GasScrubberMechanicalDesign design) {
      List<String> names = new ArrayList<String>();
      names.add("gasLoadFactor");
      names.add("kValue");
      if (design.hasInletCyclones() || design.getInletNozzleID() > 0) {
        names.add("inletMomentum");
      }
      return names;
    }

    /**
     * Selects the base TR1965 K-factor limit for the configured internals.
     *
     * @param design scrubber mechanical design
     * @return base K-factor limit in m/s before surface-tension derating
     */
    private double selectKFactorLimit(GasScrubberMechanicalDesign design) {
      if (design.hasDemistingCyclones() && !design.hasMeshPad()) {
        return COMPACT_CYCLONE_K_LIMIT;
      }
      if ((design.hasDemistingCyclones() || design.hasVanePack()) && design.hasMeshPad()) {
        return MESH_WITH_AFC_OR_VANE_K_LIMIT;
      }
      if (design.hasVanePack()) {
        return MESH_WITH_AFC_OR_VANE_K_LIMIT;
      }
      return MESH_K_LIMIT;
    }

    /**
     * Adds TR1965 vessel-internal distance checks to a report.
     *
     * @param design scrubber mechanical design
     * @param report report receiving the conformity results
     */
    private void addDistanceChecks(GasScrubberMechanicalDesign design, ConformityReport report) {
      double laHH = design.getLaHHElevationM();
      double inletElevation = design.getInletDeviceElevationM();
      if (laHH > 0.0 && inletElevation > 0.0) {
        double distance = inletElevation - laHH;
        report.addResult(new ConformityResult("tr1965-hhll-to-inlet-distance", getName(),
            "layout", distance, MIN_HHLL_TO_INLET_M, "m",
            ConformityResult.LimitDirection.MINIMUM,
            "Distance from LA(HH) to inlet device centerline"));
      } else {
        report.addResult(ConformityResult.notApplicable("tr1965-hhll-to-inlet-distance",
            getName(), "LA(HH) and inlet device elevations are required for this check"));
      }

      double meshElevation = design.getMeshPadElevationM();
      if (inletElevation > 0.0 && meshElevation > 0.0) {
        double distance = meshElevation - inletElevation;
        report.addResult(new ConformityResult("tr1965-inlet-to-mesh-distance", getName(),
            "layout", distance, MIN_INLET_TO_MESH_M, "m",
            ConformityResult.LimitDirection.MINIMUM,
            "Distance from inlet device centerline to mesh pad centerline"));
      } else if (design.hasMeshPad()) {
        report.addResult(ConformityResult.notApplicable("tr1965-inlet-to-mesh-distance",
            getName(), "Inlet device and mesh pad elevations are required for this check"));
      }
    }

    /**
     * Calculates the gas-load K-factor.
     *
     * @param diameterM vessel internal diameter in m
     * @param gasFlowM3s gas volumetric flow rate in m3/s
     * @param gasDensity gas density in kg/m3
     * @param liquidDensity liquid density in kg/m3
     * @return Souders-Brown K-factor in m/s, or NaN when the inputs are invalid
     */
    private double calculateKFactor(double diameterM, double gasFlowM3s, double gasDensity,
        double liquidDensity) {
      double vesselArea = Math.PI * Math.pow(diameterM / 2.0, 2.0);
      if (vesselArea <= 0.0 || gasFlowM3s <= 0.0 || gasDensity <= 0.0
          || liquidDensity <= gasDensity) {
        return Double.NaN;
      }
      double gasVelocity = gasFlowM3s / vesselArea;
      return gasVelocity * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
    }

    /**
     * Gets the gas density from a fluid.
     *
     * @param fluid thermodynamic fluid
     * @return gas density in kg/m3, or NaN when unavailable
     */
    private double getGasDensity(neqsim.thermo.system.SystemInterface fluid) {
      try {
        if (fluid.hasPhaseType("gas")) {
          return fluid.getPhase("gas").getPhysicalProperties().getDensity();
        }
        return fluid.getPhase(0).getPhysicalProperties().getDensity();
      } catch (RuntimeException ex) {
        return Double.NaN;
      }
    }

    /**
     * Gets the liquid density used in the K-factor denominator.
     *
     * @param fluid thermodynamic fluid
     * @return liquid density in kg/m3, or 1000 kg/m3 for dry gas screening
     */
    private double getLiquidDensity(neqsim.thermo.system.SystemInterface fluid) {
      try {
        if (fluid.hasPhaseType("oil")) {
          return fluid.getPhase("oil").getPhysicalProperties().getDensity();
        }
        if (fluid.hasPhaseType("aqueous")) {
          return fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
        }
      } catch (RuntimeException ex) {
        return 1000.0;
      }
      return 1000.0;
    }

    /**
     * Gets gas volumetric flow from a fluid.
     *
     * @param fluid thermodynamic fluid
     * @return gas volumetric flow rate in m3/s, or zero when unavailable
     */
    private double getGasFlowRateM3s(neqsim.thermo.system.SystemInterface fluid) {
      try {
        if (fluid.hasPhaseType("gas")) {
          return fluid.getPhase("gas").getFlowRate("m3/sec");
        }
        return fluid.getPhase(0).getFlowRate("m3/sec");
      } catch (RuntimeException ex) {
        return 0.0;
      }
    }

    /**
     * Gets gas-liquid surface tension when a liquid phase exists.
     *
     * @param fluid thermodynamic fluid
     * @return surface tension in mN/m, or NaN when unavailable
     */
    private double getSurfaceTensionMNPerM(neqsim.thermo.system.SystemInterface fluid) {
      if (fluid.getNumberOfPhases() < 2) {
        return Double.NaN;
      }
      try {
        return fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1000.0;
      } catch (RuntimeException ex) {
        return Double.NaN;
      }
    }

    /**
     * Calculates the screening derating factor for low surface tension fluids.
     *
     * @param surfaceTensionMNPerM gas-liquid surface tension in mN/m
     * @return K-factor multiplier in the range 0.0 to 1.0
     */
    private double getSurfaceTensionDeratingFactor(double surfaceTensionMNPerM) {
      if (!Double.isFinite(surfaceTensionMNPerM)
          || surfaceTensionMNPerM >= SURFACE_TENSION_DERATING_REFERENCE_MN_PER_M) {
        return 1.0;
      }
      double ratio = Math.max(0.0, surfaceTensionMNPerM)
          / SURFACE_TENSION_DERATING_REFERENCE_MN_PER_M;
      return Math.pow(ratio, 0.25);
    }
  }
}
