package neqsim.process.mechanicaldesign.separator.pressuredrop;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.entrainment.InletDeviceModel;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Stateless utility that computes a gas-side pressure drop breakdown for a separator or scrubber
 * from its mechanical design and current thermodynamic state.
 *
 * <p>
 * Currently modelled contributions:
 * </p>
 *
 * <ul>
 * <li><b>Inlet expansion</b> from the inlet nozzle into the vessel. When no inlet device is fitted
 * (or the device type is {@code NONE}), a Borda–Carnot sudden-expansion loss is used:
 * {@code dP = (1 - A_n/A_v)^2 · 0.5 · rho_in · v_n^2}. When an inlet device is fitted, the
 * device's own velocity-head coefficient is used instead and Borda–Carnot is not added (the
 * device coefficient already accounts for the expansion losses inherent to the device).</li>
 * <li><b>Mist eliminator</b> (mesh pad) loss, {@code dP = Eu_mesh · 0.5 · rho_g · v_g^2}.</li>
 * <li><b>Demisting cyclone</b> bank loss (gas scrubbers only),
 * {@code dP = Eu_cyc · rho_g · v_cyc^2}, matching the existing convention in
 * {@link GasScrubberMechanicalDesign#computeDrainageHead()}.</li>
 * <li><b>Outlet contraction</b> into the gas outlet nozzle,
 * {@code dP = K_con · 0.5 · rho_g · v_out^2}, with a sharp-edged default of 0.5.</li>
 * </ul>
 *
 * <p>
 * Gas-side densities are taken from the gas phase of the current thermo state. The inlet
 * nozzle uses the mixed density of the inlet stream (gas + entrained liquid). All velocities are
 * computed from the corresponding flow areas; if an area is zero or unknown the contribution is
 * skipped silently rather than throwing.
 * </p>
 *
 * <p>
 * The calculator is deliberately stateless: it never mutates the separator. Apply the result via
 * {@link Separator#setEnhancedPressureDropCalculation(boolean)} or by reading the breakdown and
 * calling {@link Separator#setPressureDrop(double)} manually.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class SeparatorPressureDropCalculator {

  /** Default sharp-edged contraction loss coefficient (gas outlet nozzle). */
  public static final double DEFAULT_OUTLET_CONTRACTION_K = 0.5;

  /** Default mesh-pad Euler number when none is configured (standard knitmesh). */
  public static final double DEFAULT_MESH_EU = 0.5;

  private SeparatorPressureDropCalculator() {}

  /**
   * Computes the breakdown for the given separator using its current mechanical design and
   * thermo state.
   *
   * @param separator the separator (or scrubber) to evaluate; must have a mechanical design
   *                  initialised and a thermodynamic system attached
   * @return the breakdown; the list is empty when no mechanism contributes (e.g. no nozzle ID,
   *         no internals)
   * @throws IllegalStateException when the separator has no thermo system available — this is a
   *                               programmer error indicating {@code run()} has not been called
   *                               and the gas-phase density cannot be evaluated
   */
  public static PressureDropBreakdown compute(Separator separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator must not be null");
    }
    SeparatorMechanicalDesign md = separator.getMechanicalDesign();
    if (md == null) {
      throw new IllegalStateException(
          "Separator has no MechanicalDesign. Call initMechanicalDesign() first.");
    }
    SystemInterface fluid = separator.getThermoSystem();
    if (fluid == null) {
      throw new IllegalStateException(
          "Separator has no thermo system. Call run() before computing pressure drop.");
    }
    fluid.initPhysicalProperties();

    List<PressureDropContribution> items = new ArrayList<PressureDropContribution>();

    // Densities and gas volumetric flow
    double rhoIn = fluid.getDensity("kg/m3");
    double rhoGas = rhoIn;
    double gasFlowM3s = 0.0;
    if (fluid.hasPhaseType("gas")) {
      rhoGas = fluid.getPhase("gas").getPhysicalProperties().getDensity();
      gasFlowM3s = fluid.getPhase("gas").getFlowRate("m3/sec");
    }

    double vesselId = separator.getInternalDiameter();
    double vesselArea = vesselId > 0 ? Math.PI * vesselId * vesselId / 4.0 : 0.0;

    // 1) Inlet expansion / inlet device
    double inletNozzleId = md.getInletNozzleID();
    if (inletNozzleId > 0 && vesselArea > 0) {
      double nozzleArea = Math.PI * inletNozzleId * inletNozzleId / 4.0;
      // Inlet nozzle carries the full mixed flow
      double mixFlow = fluid.getFlowRate("m3/sec");
      double vNozzle = mixFlow / nozzleArea;
      InletDeviceModel.InletDeviceType deviceType = getInletDeviceType(separator);
      double k;
      String source;
      String name;
      if (deviceType == null || deviceType == InletDeviceModel.InletDeviceType.NONE) {
        // Borda–Carnot sudden expansion
        double areaRatio = nozzleArea / vesselArea;
        k = (1.0 - areaRatio) * (1.0 - areaRatio);
        source = "Borda-Carnot";
        name = "inletExpansion";
      } else {
        k = deviceType.getPressureDropCoefficient();
        source = "InletDeviceType." + deviceType.name();
        name = "inletDevice";
      }
      double userK = md.getInletExpansionLossCoefficient();
      if (!Double.isNaN(userK)) {
        k = userK;
        source = "user";
        name = "inletExpansion";
      }
      double dp = k * 0.5 * rhoIn * vNozzle * vNozzle;
      items.add(
          new PressureDropContribution(name, source, k, vNozzle, rhoIn, nozzleArea, dp));
    }

    // 2) Mist eliminator (mesh pad) — using the same convention as
    // Separator.getMistEliminatorPressureDrop()
    computeMeshDp(separator, md, rhoGas, gasFlowM3s, items);

    // 3) Demisting cyclone bank (gas scrubber only)
    if (separator instanceof GasScrubber
        && md instanceof GasScrubberMechanicalDesign) {
      addCycloneContribution((GasScrubberMechanicalDesign) md, rhoGas, gasFlowM3s, items);
    }

    // 4) Outlet contraction
    double gasOutletId = md.getGasOutletNozzleID();
    if (gasOutletId > 0 && gasFlowM3s > 0) {
      double outletArea = Math.PI * gasOutletId * gasOutletId / 4.0;
      double vOut = gasFlowM3s / outletArea;
      double k = md.getOutletContractionLossCoefficient();
      String source = "user";
      if (Double.isNaN(k)) {
        k = DEFAULT_OUTLET_CONTRACTION_K;
        source = "default";
      }
      double dp = k * 0.5 * rhoGas * vOut * vOut;
      items.add(new PressureDropContribution("outletContraction", source, k, vOut, rhoGas,
          outletArea, dp));
    }

    return new PressureDropBreakdown(items);
  }

  /**
   * Adds the mesh pressure-drop contribution to the list when a mesh pad exists.
   *
   * @param sep the separator
   * @param md the mechanical design
   * @param rhoGas gas density, kg/m3
   * @param gasFlowM3s gas volumetric flow, m3/s
   * @param items list to append the contribution to
   */
  private static void computeMeshDp(Separator sep, SeparatorMechanicalDesign md, double rhoGas,
      double gasFlowM3s, List<PressureDropContribution> items) {
    if (gasFlowM3s <= 0) {
      return;
    }
    double euMesh = sep.getMistEliminatorDpCoeff();
    String source = "user";
    if (euMesh <= 0) {
      // Only count a default mesh drop when we know a mesh exists. For a generic Separator
      // we have no flag, so skip silently. For a GasScrubber the hasMeshPad flag is checked
      // below.
      if (md instanceof GasScrubberMechanicalDesign
          && ((GasScrubberMechanicalDesign) md).hasMeshPad()) {
        euMesh = DEFAULT_MESH_EU;
        source = "default";
      } else {
        return;
      }
    }

    // Mesh area: prefer scrubber-specific area, else use vessel cross-section.
    double meshArea = 0.0;
    if (md instanceof GasScrubberMechanicalDesign) {
      meshArea = ((GasScrubberMechanicalDesign) md).getMeshPadAreaM2();
    }
    if (meshArea <= 0) {
      double id = sep.getInternalDiameter();
      meshArea = id > 0 ? Math.PI * id * id / 4.0 : 0.0;
    }
    if (meshArea <= 0) {
      return;
    }
    double vMesh = gasFlowM3s / meshArea;
    double dp = euMesh * 0.5 * rhoGas * vMesh * vMesh;
    items.add(new PressureDropContribution("mesh", source, euMesh, vMesh, rhoGas, meshArea, dp));
  }

  /**
   * Adds the demisting cyclone contribution (full bank ΔP across the cyclones, using the same
   * Eu·ρv² convention as
   * {@link GasScrubberMechanicalDesign#computeDrainageHead()}).
   *
   * @param md the gas scrubber mechanical design
   * @param rhoGas gas density, kg/m3
   * @param gasFlowM3s gas volumetric flow, m3/s
   * @param items list to append the contribution to
   */
  private static void addCycloneContribution(GasScrubberMechanicalDesign md, double rhoGas,
      double gasFlowM3s, List<PressureDropContribution> items) {
    if (!md.hasDemistingCyclones() || md.getNumberOfDemistingCyclones() <= 0
        || md.getDemistingCycloneDiameterM() <= 0 || gasFlowM3s <= 0) {
      return;
    }
    double cycD = md.getDemistingCycloneDiameterM();
    double cycArea = md.getNumberOfDemistingCyclones() * Math.PI * cycD * cycD / 4.0;
    double vCyc = gasFlowM3s / cycArea;
    double eu = md.getCycloneEulerNumber();
    // Existing convention: total dp vs rho*v^2 (no 0.5)
    double dp = eu * rhoGas * vCyc * vCyc;
    items.add(new PressureDropContribution("cyclones", "user", eu, vCyc, rhoGas, cycArea, dp));
  }

  /**
   * Looks up the inlet device type from the separator's performance calculator.
   *
   * @param separator the separator
   * @return the inlet device type, or {@code null} when no performance calculator / device is
   *         configured (treated as no inlet device by the caller)
   */
  private static InletDeviceModel.InletDeviceType getInletDeviceType(Separator separator) {
    try {
      if (separator.getPerformanceCalculator() != null
          && separator.getPerformanceCalculator().getInletDeviceModel() != null) {
        return separator.getPerformanceCalculator().getInletDeviceModel().getDeviceType();
      }
    } catch (RuntimeException e) {
      // defensive: any access failure is treated as "no device"
      return null;
    }
    return null;
  }
}
