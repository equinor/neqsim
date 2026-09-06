package neqsim.process.equipment.pipeline.twophasepipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;

/**
 * Conservative subcell reconstruction of tracked slug bodies and the surrounding film.
 *
 * <p>
 * The seven Eulerian conservative variables are the sole inventory. This class only supplies face states to the
 * finite-volume flux calculation: it never withdraws, deposits, or independently transports liquid. Each partial cell
 * satisfies {@code Ubar = bodyFraction * Ubody + (1 - bodyFraction) * Ufilm} for every conservative variable. Available
 * inventory limits the body contrast; a marker cannot manufacture a prescribed high-holdup body.
 * </p>
 *
 * <p>
 * Phase densities are frozen during reconstruction. Liquid phase composition is preserved separately for oil and water.
 * Body velocities relax toward the mixture velocity, while film momenta and total energy are recovered from the cell
 * budgets. Velocity and available sensible-thermal-energy checks limit that relaxation. This subcell closure is not a
 * separate thermodynamic flash or an experimentally qualified three-phase entrainment law.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SlugFilmCoupling {
  private static final double FRACTION_TOLERANCE = 1.0e-12;

  /** Prevent construction of the stateless coupling utility. */
  private SlugFilmCoupling() {
  }

  /**
   * Immutable reconstruction; mutable state accessors return defensive copies.
   */
  public static final class Reconstruction {
    private final TwoFluidSection leftFace;
    private final TwoFluidSection rightFace;
    private final TwoFluidSection body;
    private final TwoFluidSection film;
    private final double bodyFraction;
    private final boolean active;

    /**
     * Store an independently owned reconstruction.
     *
     * @param leftFace state at the upstream cell face
     * @param rightFace state at the downstream cell face
     * @param body body state
     * @param film film state
     * @param fraction body fraction of the cell length
     * @param active whether subcell contrast is represented
     */
    private Reconstruction(TwoFluidSection leftFace, TwoFluidSection rightFace, TwoFluidSection body,
        TwoFluidSection film, double fraction, boolean active) {
      this.leftFace = leftFace.clone();
      this.rightFace = rightFace.clone();
      this.body = body.clone();
      this.film = film.clone();
      this.bodyFraction = fraction;
      this.active = active;
    }

    /** @return independently owned upstream face state */
    public TwoFluidSection getLeftFaceState() {
      return leftFace.clone();
    }

    /** @return independently owned downstream face state */
    public TwoFluidSection getRightFaceState() {
      return rightFace.clone();
    }

    /** @return independently owned body state */
    public TwoFluidSection getBodyState() {
      return body.clone();
    }

    /** @return independently owned film state */
    public TwoFluidSection getFilmState() {
      return film.clone();
    }

    /** @return seven body conservative variables per unit length */
    public double[] getBodyConservativeState() {
      return body.getStateVector();
    }

    /** @return seven film conservative variables per unit length */
    public double[] getFilmConservativeState() {
      return film.getStateVector();
    }

    /** @return union fraction of cell length occupied by slug bodies */
    public double getBodyFraction() {
      return bodyFraction;
    }

    /** @return whether admissible subcell body/film contrast was reconstructed */
    public boolean isActive() {
      return active;
    }
  }

  /**
   * Reconstruct a cell without changing the input section or any tracked slug.
   *
   * @param section cell with midpoint coordinates and seven conservative variables
   * @param slugs tracked body intervals; overlapping intervals are counted only once
   * @return immutable body, film, and face reconstruction
   * @throws IllegalArgumentException if the section geometry or conservative state is invalid
   */
  public static Reconstruction reconstruct(TwoFluidSection section, List<SlugBubbleUnit> slugs) {
    if (section == null || !Double.isFinite(section.getLength()) || section.getLength() <= 0.0
        || !Double.isFinite(section.getArea()) || section.getArea() <= 0.0) {
      throw new IllegalArgumentException("Slug-film reconstruction requires positive finite cell geometry");
    }
    double[] mean = section.getStateVector();
    for (int j = 0; j < mean.length; j++) {
      if (!Double.isFinite(mean[j]) || (j < 3 && mean[j] < 0.0)) {
        throw new IllegalArgumentException(
            "Slug-film reconstruction requires finite state and nonnegative phase masses");
      }
    }
    double left = section.getPosition() - section.getLength() / 2.0;
    double right = left + section.getLength();
    List<double[]> intervals = new ArrayList<double[]>();
    double targetHoldup = section.getLiquidHoldup();
    boolean leftInBody = false;
    boolean rightInBody = false;
    double targetVelocityWeight = 0.0;
    double targetBodyVelocity = 0.0;
    if (slugs != null) {
      for (SlugBubbleUnit slug : slugs) {
        if (slug == null || !Double.isFinite(slug.frontPosition) || !Double.isFinite(slug.tailPosition)) {
          continue;
        }
        double start = Math.max(left, slug.tailPosition);
        double end = Math.min(right, slug.frontPosition);
        if (end > start) {
          intervals.add(new double[] { start, end });
          if (Double.isFinite(slug.slugHoldup)) {
            targetHoldup = Math.max(targetHoldup, slug.slugHoldup);
          }
          leftInBody |= slug.tailPosition <= left && slug.frontPosition > left;
          rightInBody |= slug.tailPosition < right && slug.frontPosition >= right;
          if (slug.usesConservativeFilmCoupling && slug.hasConservativeVelocity
              && Double.isFinite(slug.slugLiquidVelocity)) {
            targetBodyVelocity += (end - start) * slug.slugLiquidVelocity;
            targetVelocityWeight += end - start;
          }
        }
      }
    }
    double fraction = unionLength(intervals) / section.getLength();
    fraction = Math.max(0.0, Math.min(1.0, fraction));
    if (fraction <= FRACTION_TOLERANCE || fraction >= 1.0 - FRACTION_TOLERANCE || mean[0] <= 0.0
        || mean[1] + mean[2] <= 0.0) {
      return unchanged(section, fraction);
    }
    double[] density = phaseDensities(section);
    double liquidVolume = mean[1] / density[1] + mean[2] / density[2];
    double totalVolume = liquidVolume + mean[0] / density[0];
    double meanHoldup = liquidVolume / totalVolume;
    double upperHoldup = Math.min(1.0, meanHoldup / fraction);
    double bodyHoldup = Math.min(Math.min(0.98, targetHoldup),
        meanHoldup + (upperHoldup - meanHoldup) * (1.0 - 1.0e-10));
    if (!(bodyHoldup > meanHoldup + FRACTION_TOLERANCE)) {
      return unchanged(section, fraction);
    }
    double[] body = new double[7];
    double[] film = new double[7];
    for (int phase = 0; phase < 3; phase++) {
      double massScale = phase == 0 ? (1.0 - bodyHoldup) / (1.0 - meanHoldup) : bodyHoldup / meanHoldup;
      body[phase] = mean[phase] * massScale;
      film[phase] = (mean[phase] - fraction * body[phase]) / (1.0 - fraction);
    }
    double meanKineticEnergy = kineticEnergy(mean);
    double mixtureVelocity = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      mixtureVelocity += mean[phase + 3] / density[phase];
    }
    mixtureVelocity /= totalVolume;
    if (targetVelocityWeight > 0.0) {
      mixtureVelocity = targetBodyVelocity / targetVelocityWeight;
    }
    double[] phaseInternalEnergy = phaseInternalEnergies(section, density);
    double relaxation = 1.0;
    boolean admissible = false;
    for (int attempt = 0; attempt < 42; attempt++) {
      if (attempt == 41) {
        relaxation = 0.0;
      }
      for (int phase = 0; phase < 3; phase++) {
        double velocity = mean[phase] > 0.0 ? mean[phase + 3] / mean[phase] : 0.0;
        body[phase + 3] = body[phase] * (velocity + relaxation * (mixtureVelocity - velocity));
        film[phase + 3] = (mean[phase + 3] - fraction * body[phase + 3]) / (1.0 - fraction);
      }
      double bodyKinetic = kineticEnergy(body);
      double filmKinetic = kineticEnergy(film);
      double referenceInternal = 0.0;
      double totalMass = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        referenceInternal += mean[phase] * phaseInternalEnergy[phase];
        totalMass += mean[phase];
      }
      double energyOffset = (mean[6] - fraction * bodyKinetic - (1.0 - fraction) * filmKinetic - referenceInternal)
          / totalMass;
      body[6] = bodyKinetic;
      for (int phase = 0; phase < 3; phase++) {
        body[6] += body[phase] * (phaseInternalEnergy[phase] + energyOffset);
      }
      film[6] = (mean[6] - fraction * body[6]) / (1.0 - fraction);
      double extraKineticEnergy = fraction * bodyKinetic + (1.0 - fraction) * filmKinetic - meanKineticEnergy;
      double sensibleEnergy = section.getMixtureHeatCapacity() > 0.0 && section.getTemperature() > 0.0
          ? totalMass * section.getMixtureHeatCapacity() * section.getTemperature()
          : 0.0;
      // EOS internal energies may have an arbitrary negative reference. Bound kinetic
      // variance by sensible thermal energy, not by the sign of the stored total energy.
      boolean thermalEnergyValid = extraKineticEnergy <= sensibleEnergy * (1.0 - 1.0e-10)
          + 1.0e-12 * Math.max(1.0, Math.abs(meanKineticEnergy));
      if (thermalEnergyValid && validState(body) && validState(film)) {
        admissible = true;
        break;
      }
      relaxation *= 0.5;
    }
    if (!admissible) {
      return unchanged(section, fraction);
    }
    TwoFluidSection bodySection = stateSection(section, body, density, phaseInternalEnergy);
    TwoFluidSection filmSection = stateSection(section, film, density, phaseInternalEnergy);
    return new Reconstruction(leftInBody ? bodySection : filmSection, rightInBody ? bodySection : filmSection,
        bodySection, filmSection, fraction, true);
  }

  /**
   * Return clones of the original state when no admissible reconstruction is needed.
   *
   * @param section original cell
   * @param fraction geometric body fraction
   * @return unchanged reconstruction
   */
  private static Reconstruction unchanged(TwoFluidSection section, double fraction) {
    return new Reconstruction(section, section, section, section, fraction, false);
  }

  /**
   * Integrate the union of clipped intervals without counting overlaps twice.
   *
   * @param intervals mutable list of start/end pairs
   * @return union length (m)
   */
  private static double unionLength(List<double[]> intervals) {
    Collections.sort(intervals, new Comparator<double[]>() {
      @Override
      public int compare(double[] a, double[] b) {
        return Double.compare(a[0], b[0]);
      }
    });
    double length = 0.0;
    double end = Double.NEGATIVE_INFINITY;
    for (double[] interval : intervals) {
      length += Math.max(0.0, interval[1] - Math.max(end, interval[0]));
      end = Math.max(end, interval[1]);
    }
    return length;
  }

  /**
   * Resolve phase densities with the section's existing absent-phase fallbacks.
   *
   * @param section original cell
   * @return gas, oil, and water densities (kg/m3)
   * @throws IllegalArgumentException if an active phase has no positive density
   */
  private static double[] phaseDensities(TwoFluidSection section) {
    double[] densities = { section.getGasDensity(), section.getOilDensity(), section.getWaterDensity() };
    if (!(densities[1] > 0.0)) {
      densities[1] = section.getLiquidDensity();
    }
    if (!(densities[2] > 0.0)) {
      densities[2] = 1000.0;
    }
    for (double density : densities) {
      if (!Double.isFinite(density) || density <= 0.0) {
        throw new IllegalArgumentException("Slug-film reconstruction requires positive phase densities");
      }
    }
    return densities;
  }

  /**
   * Recover reference phase specific internal energies from frozen thermodynamic properties.
   *
   * @param section original cell
   * @param densities phase densities (kg/m3)
   * @return phase internal energies (J/kg)
   */
  private static double[] phaseInternalEnergies(TwoFluidSection section, double[] densities) {
    double oilEnthalpy = section.getLiquidEnthalpy();
    double waterEnthalpy = oilEnthalpy;
    if (section instanceof ThreeFluidSection) {
      oilEnthalpy = ((ThreeFluidSection) section).getOilEnthalpy();
      waterEnthalpy = ((ThreeFluidSection) section).getWaterEnthalpy();
    }
    return new double[] { section.getGasEnthalpy() - section.getPressure() / densities[0],
        oilEnthalpy - section.getPressure() / densities[1], waterEnthalpy - section.getPressure() / densities[2] };
  }

  /**
   * Evaluate phase kinetic energy without assigning velocity to an absent phase.
   *
   * @param state seven conservative variables per unit length
   * @return kinetic energy per length (J/m)
   */
  private static double kineticEnergy(double[] state) {
    double energy = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      if (state[phase] > 0.0) {
        energy += 0.5 * state[phase + 3] * state[phase + 3] / state[phase];
      }
    }
    return energy;
  }

  /**
   * Check conservative positivity and the section's supported primitive velocity range.
   *
   * @param state seven conservative variables
   * @return whether finite primitives can be recovered without velocity clipping
   */
  private static boolean validState(double[] state) {
    for (double value : state) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    for (int phase = 0; phase < 3; phase++) {
      if (state[phase] < 0.0 || (state[phase] == 0.0 && state[phase + 3] != 0.0)) {
        return false;
      }
      double limit = phase == 0 ? 100.0 : 50.0;
      if (state[phase] > 0.0 && Math.abs(state[phase + 3]) > limit * state[phase]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Build an independent primitive face state and align its enthalpies with the conservative energy budget.
   *
   * @param original original cell
   * @param state reconstructed conservative state
   * @param densities frozen phase densities
   * @param referenceInternal phase reference internal energies
   * @return reconstructed cell clone
   */
  private static TwoFluidSection stateSection(TwoFluidSection original, double[] state, double[] densities,
      double[] referenceInternal) {
    TwoFluidSection result = original.clone();
    result.setOilDensity(densities[1]);
    result.setWaterDensity(densities[2]);
    result.setStateVector(state);
    result.extractPrimitiveVariables();
    // General section recovery deliberately retains old velocities for trace phases.
    // A reconstructed face must instead honor its exact phase momentum/mass ratio;
    // validState has already checked every ratio against the supported velocity range.
    result.setGasVelocity(state[0] > 0.0 ? state[3] / state[0] : 0.0);
    double liquidMass = state[1] + state[2];
    result.setLiquidVelocity(liquidMass > 0.0 ? (state[4] + state[5]) / liquidMass : 0.0);
    result.setOilVelocity(state[1] > 0.0 ? state[4] / state[1] : 0.0);
    result.setWaterVelocity(state[2] > 0.0 ? state[5] / state[2] : 0.0);
    result.updateDerivedQuantities();
    double reference = 0.0;
    double mass = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      reference += state[phase] * referenceInternal[phase];
      mass += state[phase];
    }
    double offset = (state[6] - kineticEnergy(state) - reference) / mass;
    double[] originalState = original.getStateVector();
    double originalReference = 0.0;
    double originalMass = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      originalReference += originalState[phase] * referenceInternal[phase];
      originalMass += originalState[phase];
    }
    double originalOffset = (originalState[6] - kineticEnergy(originalState) - originalReference) / originalMass;
    if (original.getMixtureHeatCapacity() > 0.0 && original.getTemperature() > 0.0) {
      result.setTemperature(original.getTemperature() + (offset - originalOffset) / original.getMixtureHeatCapacity());
    }
    double gasEnthalpy = referenceInternal[0] + offset + result.getPressure() / densities[0];
    double oilEnthalpy = referenceInternal[1] + offset + result.getPressure() / densities[1];
    double waterEnthalpy = referenceInternal[2] + offset + result.getPressure() / densities[2];
    result.setGasEnthalpy(gasEnthalpy);
    if (liquidMass > 0.0) {
      result.setLiquidEnthalpy((state[1] * oilEnthalpy + state[2] * waterEnthalpy) / liquidMass);
    }
    if (result instanceof ThreeFluidSection) {
      ((ThreeFluidSection) result).setOilEnthalpy(oilEnthalpy);
      ((ThreeFluidSection) result).setWaterEnthalpy(waterEnthalpy);
    }
    // Primitive recovery must never become another owner of the reconstructed inventory.
    result.setStateVector(state);
    return result;
  }
}
