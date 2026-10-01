package neqsim.process.util.combustion;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Transfer a separately calculated useful heating duty to a nonreactive circulating-oil stream.
 *
 * <p>
 * The duty is a physical power in watts. Only the oil's own enthalpy difference is used; absolute mechanism formation
 * enthalpies are never mixed with the NeqSim EOS enthalpy reference. The helper imposes no pressure loss, oil
 * degradation model or tube-wall temperature calculation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HotOilHeatBalance {
  /** Prevent construction of this stateless utility. */
  private HotOilHeatBalance() {
  }

  /**
   * Apply absorbed heat to a cloned oil stream and reject an unclosed oil duty.
   *
   * @param inlet circulating-oil supply, left unchanged
   * @param usefulHeatW independently calculated heat delivered to oil [W], nonnegative
   * @param outletName name of the cloned oil outlet
   * @param id calculation identifier
   * @return accepted oil outlet at the inlet pressure and flow
   */
  public static StreamInterface apply(StreamInterface inlet, double usefulHeatW, String outletName, UUID id) {
    return applyWithClosure(inlet, usefulHeatW, outletName, id).getOutlet();
  }

  /**
   * Apply absorbed heat and return independent EOS duty-closure diagnostics.
   *
   * @param inlet circulating-oil supply, left unchanged
   * @param usefulHeatW independently calculated heat delivered to oil [W], nonnegative
   * @param outletName name of the cloned oil outlet
   * @param id calculation identifier
   * @return oil outlet and inlet/outlet enthalpy-flow diagnostics
   */
  public static Result applyWithClosure(StreamInterface inlet, double usefulHeatW, String outletName, UUID id) {
    if (inlet == null || inlet.getThermoSystem() == null || id == null || outletName == null
        || outletName.trim().isEmpty()) {
      throw new IllegalArgumentException("Oil inlet, outlet name and calculation identifier are required");
    }
    if (!Double.isFinite(usefulHeatW) || usefulHeatW < 0.0) {
      throw new IllegalArgumentException("Useful heat must be finite and nonnegative in watts");
    }
    SystemInterface oil = inlet.getThermoSystem().clone();
    if (oil.isChemicalSystem()) {
      throw new IllegalArgumentException("Circulating oil must use a nonreactive thermodynamic system");
    }
    double massFlow = oil.getFlowRate("kg/sec");
    if (!Double.isFinite(massFlow) || massFlow <= 0.0) {
      throw new IllegalArgumentException("Circulating oil must have finite positive mass flow");
    }
    oil.init(2);
    double inletEnthalpyW = oil.getEnthalpy();
    double targetEnthalpyW = inletEnthalpyW + usefulHeatW;
    if (!Double.isFinite(inletEnthalpyW) || !Double.isFinite(targetEnthalpyW)) {
      throw new IllegalStateException("Oil enthalpy flow must be finite");
    }
    if (usefulHeatW > 0.0) {
      new ThermodynamicOperations(oil).PHflash(targetEnthalpyW, 0);
    }
    oil.initProperties();
    double outletEnthalpyW = oil.getEnthalpy();
    double absorbedDutyW = outletEnthalpyW - inletEnthalpyW;
    double residualW = absorbedDutyW - usefulHeatW;
    double relativeResidual = Math.abs(residualW) / Math.max(1.0, usefulHeatW);
    if (!Double.isFinite(relativeResidual) || relativeResidual > 1.0e-5) {
      throw new IllegalStateException("Oil PH flash failed duty closure: relative residual " + relativeResidual);
    }
    StreamInterface outlet = inlet.clone(outletName);
    outlet.setThermoSystem(oil);
    outlet.setCalculationIdentifier(id);
    return new Result(outlet, inletEnthalpyW, outletEnthalpyW, usefulHeatW, absorbedDutyW, residualW, relativeResidual);
  }

  /**
   * Accepted oil outlet and independently read oil-side enthalpy-flow diagnostics.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result {
    private final StreamInterface outlet;
    private final double inletEnthalpyW;
    private final double outletEnthalpyW;
    private final double requestedDutyW;
    private final double absorbedDutyW;
    private final double dutyResidualW;
    private final double relativeDutyResidual;

    /**
     * Store accepted oil-side results.
     *
     * @param outlet accepted outlet stream
     * @param inletEnthalpyW inlet EOS enthalpy flow [W]
     * @param outletEnthalpyW outlet EOS enthalpy flow [W]
     * @param requestedDutyW external useful-heat specification [W]
     * @param absorbedDutyW independently read outlet-minus-inlet EOS enthalpy [W]
     * @param dutyResidualW absorbed minus requested duty [W]
     * @param relativeDutyResidual absolute residual divided by requested duty, with a 1 W floor
     */
    private Result(StreamInterface outlet, double inletEnthalpyW, double outletEnthalpyW, double requestedDutyW,
        double absorbedDutyW, double dutyResidualW, double relativeDutyResidual) {
      this.outlet = outlet;
      this.inletEnthalpyW = inletEnthalpyW;
      this.outletEnthalpyW = outletEnthalpyW;
      this.requestedDutyW = requestedDutyW;
      this.absorbedDutyW = absorbedDutyW;
      this.dutyResidualW = dutyResidualW;
      this.relativeDutyResidual = relativeDutyResidual;
    }

    /**
     * Get the accepted oil stream.
     *
     * @return outlet stream
     */
    public StreamInterface getOutlet() {
      return outlet;
    }

    /**
     * Get inlet EOS enthalpy flow in its native reference.
     *
     * @return inlet enthalpy flow [W]
     */
    public double getInletEnthalpyW() {
      return inletEnthalpyW;
    }

    /**
     * Get outlet EOS enthalpy flow in the same reference.
     *
     * @return outlet enthalpy flow [W]
     */
    public double getOutletEnthalpyW() {
      return outletEnthalpyW;
    }

    /**
     * Get externally specified useful heat.
     *
     * @return requested duty [W]
     */
    public double getRequestedDutyW() {
      return requestedDutyW;
    }

    /**
     * Get independently read oil enthalpy gain.
     *
     * @return absorbed duty [W]
     */
    public double getAbsorbedDutyW() {
      return absorbedDutyW;
    }

    /**
     * Get signed absorbed-minus-requested duty.
     *
     * @return signed duty residual [W]
     */
    public double getDutyResidualW() {
      return dutyResidualW;
    }

    /**
     * Get normalized oil duty-closure error.
     *
     * @return absolute relative residual, using a 1 W normalization floor
     */
    public double getRelativeDutyResidual() {
      return relativeDutyResidual;
    }
  }
}
