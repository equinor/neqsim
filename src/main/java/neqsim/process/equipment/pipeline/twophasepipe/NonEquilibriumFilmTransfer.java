package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.thermo.system.SystemInterface;

/**
 * Isothermal gas/aqueous finite-rate transfer for residual-film and pooled-liquid screening.
 *
 * <p>
 * Each configured species uses a supplied overall gas-side coefficient and the fugacity driving force
 * {@code k * cGas * (xLiquid * phiLiquid / phiGas - yGas)}. This dilute-transfer closure uses the actual, independently
 * transported bulk compositions. It is not a Maxwell-Stefan boundary solver or an experimentally qualified drying
 * correlation. Unconfigured species do not transfer.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class NonEquilibriumFilmTransfer implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String[] components;
  private final double[] coefficients;
  private final double wettedPerimeterFraction;

  /**
   * Construct an immutable transfer closure.
   *
   * @param components unique NeqSim names of transferring species
   * @param coefficients overall gas-side coefficients in m/s; zero disables a species
   * @param wettedPerimeterFraction fraction in [0, 1]; zero selects the hydraulic interface width, a positive value
   * prescribes that fraction of the full pipe circumference
   */
  public NonEquilibriumFilmTransfer(String[] components, double[] coefficients, double wettedPerimeterFraction) {
    if (components == null || coefficients == null || components.length == 0 || components.length != coefficients.length
        || !Double.isFinite(wettedPerimeterFraction) || wettedPerimeterFraction < 0.0
        || wettedPerimeterFraction > 1.0) {
      throw new IllegalArgumentException("Transfer species, coefficients and wetted fraction must be valid");
    }
    for (int i = 0; i < components.length; i++) {
      if (components[i] == null || components[i].trim().isEmpty() || !Double.isFinite(coefficients[i])
          || coefficients[i] < 0.0) {
        throw new IllegalArgumentException("Transfer coefficients must be finite and nonnegative");
      }
      for (int j = 0; j < i; j++) {
        if (components[i].equals(components[j])) {
          throw new IllegalArgumentException("Duplicate transfer component " + components[i]);
        }
      }
    }
    this.components = components.clone();
    this.coefficients = coefficients.clone();
    this.wettedPerimeterFraction = wettedPerimeterFraction;
  }

  /**
   * Evaluate unconstrained fluxes between independently specified homogeneous phases.
   *
   * @param gas single gas phase at the common pressure and temperature
   * @param aqueous single aqueous phase at the same pressure and temperature
   * @return gas-directed mass fluxes in kg/(m2 s), in constructor species order
   */
  public double[] gasMassFlux(SystemInterface gas, SystemInterface aqueous) {
    if (gas == null || aqueous == null || gas.getNumberOfPhases() != 1 || aqueous.getNumberOfPhases() != 1
        || !gas.hasPhaseType("gas") || !aqueous.hasPhaseType("aqueous")
        || Math.abs(gas.getPressure() - aqueous.getPressure()) > 1.0e-8
        || Math.abs(gas.getTemperature() - aqueous.getTemperature()) > 1.0e-8) {
      throw new IllegalArgumentException("Transfer requires homogeneous gas/aqueous phases at common P/T: " + "gas="
          + (gas == null ? "null"
              : gas.getNumberOfPhases()
                  + "/" + gas.getPhase(0).getType() + "/" + gas.getPressure() + "/" + gas.getTemperature())
          + ", liquid="
          + (aqueous == null ? "null"
              : aqueous.getNumberOfPhases() + "/" + aqueous.getPhase(0).getType() + "/" + aqueous.getPressure() + "/"
                  + aqueous.getTemperature()));
    }
    PhaseInterface g = gas.getPhase(0);
    PhaseInterface l = aqueous.getPhase(0);
    double concentration = g.getDensity("kg/m3") / g.getMolarMass();
    if (!(concentration > 0.0) || !Double.isFinite(concentration)) {
      throw new IllegalStateException("Gas molar density must be finite and positive");
    }
    double[] flux = new double[components.length];
    for (int i = 0; i < components.length; i++) {
      double phiGas = g.getComponent(components[i]).getFugacityCoefficient();
      double equilibriumY = l.getComponent(components[i]).getx()
          * l.getComponent(components[i]).getFugacityCoefficient() / phiGas;
      flux[i] = coefficients[i] * concentration * (equilibriumY - g.getComponent(components[i]).getx())
          * g.getComponent(components[i]).getMolarMass();
      if (!Double.isFinite(flux[i]) || !(phiGas > 0.0)) {
        throw new IllegalStateException("Nonfinite fugacity transfer for " + components[i]);
      }
    }
    return flux;
  }

  /**
   * Evaluate paired, donor-limited component source terms for one Euler substep.
   *
   * @param transport accepted component inventories
   * @param sections hydrodynamic cells
   * @param template EOS and named-component template
   * @param timeStepSeconds positive substep duration in seconds
   * @return cell/phase/component sources in kg/(m s), gas/oil/aqueous order
   */
  public double[][][] calculate(TwoFluidComponentTransport transport, TwoFluidSection[] sections,
      SystemInterface template, double timeStepSeconds) {
    if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("Transfer time step must be positive and finite");
    }
    String[] names = transport.getComponentNames();
    double[][][] inventory = transport.getInventoryKg();
    if (sections.length != inventory.length) {
      throw new IllegalArgumentException("Transfer grid must match component inventories");
    }
    int[] indices = new int[components.length];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = Arrays.asList(names).indexOf(components[i]);
      if (indices[i] < 0) {
        throw new IllegalArgumentException("Missing transfer species " + components[i]);
      }
    }
    double[][][] sources = new double[sections.length][3][names.length];
    for (int cell = 0; cell < sections.length; cell++) {
      TwoFluidSection section = sections[cell];
      if (section.getOilMassPerLength() > 0.0) {
        throw new IllegalStateException("Film transfer supports gas and aqueous liquid only");
      }
      if (section.getGasMassPerLength() == 0.0 || section.getWaterMassPerLength() == 0.0) {
        continue;
      }
      SystemInterface gas = transport.createPhaseState(cell, 0, template, section.getPressure(),
          section.getTemperature());
      SystemInterface liquid = transport.createPhaseState(cell, 2, template, section.getPressure(),
          section.getTemperature());
      double[] flux = gasMassFlux(gas, liquid);
      double perimeter = wettedPerimeterFraction == 0.0
          ? new GeometryCalculator().calculateFromHoldup(section.getLiquidHoldup(),
              section.getDiameter()).interfacialWidth
          : wettedPerimeterFraction * Math.PI * section.getDiameter();
      if (!Double.isFinite(perimeter) || perimeter < 0.0) {
        throw new IllegalStateException("Interface perimeter must be finite and nonnegative");
      }
      for (int i = 0; i < indices.length; i++) {
        int component = indices[i];
        double rate = flux[i] * perimeter;
        int donor = rate > 0.0 ? 2 : 0;
        // Reserve half the donor for advection. The pipe requires Euler and CFL <= 0.5.
        double limit = 0.5 * inventory[cell][donor][component] / section.getLength() / timeStepSeconds;
        rate = Math.copySign(Math.min(Math.abs(rate), limit), rate);
        sources[cell][0][component] = rate;
        sources[cell][2][component] = -rate;
      }
    }
    return sources;
  }
}
