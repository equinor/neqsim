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
   * @param standardName the standard identifier: "TR3500", "API-12J",
   *                     "Shell-DEP", "NORSOK-P002"
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
    throw new IllegalArgumentException("Unknown conformity standard: " + standardName
        + ". Supported: TR3500");
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
        // Drainage head check — required liquid column (mesh dP + cyclone dP)
        // vs available elevation between cyclone deck and LA(HH)
        double cycloneDeckBottom = design.getCycloneDeckElevationM();
        double laHH = design.getLaHHElevationM();
        if (cycloneDeckBottom > 0 && laHH > 0) {
          neqsim.process.mechanicaldesign.separator.DrainageHeadResult dh = design
              .computeDrainageHead();

          // Primary check: required head vs available head
          report.addResult(new ConformityResult("drainage-head", getName(), "demisting-cyclones",
              dh.getRequiredHeadMm(), dh.getAvailableHeadMm(), "mm",
              ConformityResult.LimitDirection.MAXIMUM,
              "Required liquid column (mesh+cyclone dP) vs available elevation"));

          // Secondary check: percent of available (governing metric for comparisons)
          report.addResult(new ConformityResult("drainage-head-pct", getName(),
              "demisting-cyclones",
              dh.getPercentOfAvailable(), 100.0, "%",
              ConformityResult.LimitDirection.MAXIMUM,
              "Drainage head required as % of available elevation"));

          // Cyclone dP to drain
          report.addResult(new ConformityResult("cyclone-dp-to-drain", getName(),
              "demisting-cyclones",
              dh.getCycloneDpToDrainPa() / 100.0, 50.0, "mbar",
              ConformityResult.LimitDirection.MAXIMUM,
              "Cyclone pressure drop reaching the drain chamber"));
        } else {
          report.addResult(ConformityResult.notApplicable("drainage-head", getName(),
              "Cyclone deck or LA(HH) elevation not set"));
          report.addResult(ConformityResult.notApplicable("drainage-head-pct", getName(),
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
}
