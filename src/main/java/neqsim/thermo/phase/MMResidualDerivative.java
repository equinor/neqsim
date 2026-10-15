package neqsim.thermo.phase;

import java.util.Arrays;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentSrkCPAMM;

/**
 * Exact forward differentiation of the electrolyte energies implemented by {@link PhaseElectrolyteCPAMM}.
 *
 * <p>
 * Composition derivatives hold total volume fixed. All dependence of screening length, mean ion diameter, packing
 * fractions and mixed-solvent permittivity is retained. Only the requested independent directions are propagated, so
 * third volume derivatives do not require a third-order composition tensor. Exact-state derivative reuse is phase-local
 * and never shared by cloned phases. It preserves the existing model energies and parameters; it does not replace the
 * empirical short-range or permittivity model by the full published e-CPA model.
 * </p>
 *
 * <p>
 * At exactly zero total ionic amount, the DH energy and its first derivatives have their continuous zero limit. The
 * physical composition Hessian diverges as ionic strength approaches zero; returning zero for the DH contribution at
 * the exactly inactive boundary is a solver convention, not a finite physical Hessian. Positive trace amounts retain
 * the singular limiting behavior.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class MMResidualDerivative {
  /** Avogadro constant used by the phase energy. */
  private static final double AVOGADRO = ThermodynamicConstantsInterface.avagadroNumber;
  /** Gas constant used by the phase energy. */
  private static final double R = ThermodynamicConstantsInterface.R;
  /** Elementary charge, matching the phase energy. */
  private static final double ELECTRON_CHARGE = 1.60217662e-19;
  /** Vacuum permittivity, matching the phase energy. */
  private static final double VACUUM_PERMITTIVITY = 8.854187817e-12;

  /** Prevents construction of this utility. */
  private MMResidualDerivative() {
  }

  /**
   * Evaluates a derivative of an electrolyte residual energy at the current phase state.
   *
   * @param phase current phase
   * @param term -1 for all terms, 0 for Debye-Huckel, 1 for Born, 2 for short range
   * @param first first direction: component index, component count for V, or count + 1 for T
   * @param second second direction, or -1 for a first derivative
   * @param third third direction, or -1 for a first/second derivative; third derivatives allow at most two T directions
   * @return partial derivative of A/(RT); volume directions use NeqSim's volume units
   * @throws IllegalArgumentException if a third temperature derivative is requested
   */
  public static double derivative(PhaseElectrolyteCPAMM phase, int term, int first, int second, int third) {
    int count = phase.getNumberOfComponents();
    boolean volumeOnly = first == count && (second < 0 || second == count) && (third < 0 || third == count);
    if (term < 0 && (third < 0 || volumeOnly)) {
      double[] state = state(phase);
      Cache cache = phase.getElectrolyteDerivativeCache();
      if (cache == null || !Arrays.equals(cache.state, state)) {
        cache = new Cache(state);
        phase.setElectrolyteDerivativeCache(cache);
      }
      if (volumeOnly) {
        if (cache.volume == null) {
          cache.volume = evaluate(phase, -1, new int[] {count}, 1, 3);
        }
        return cache.volume.getPartialDerivative(1 + (second >= 0 ? 1 : 0) + (third >= 0 ? 1 : 0));
      }
      if (cache.hessian == null) {
        int[] directions = new int[count + 2];
        for (int i = 0; i < directions.length; i++) {
          directions[i] = i;
        }
        cache.hessian = evaluate(phase, -1, directions, directions.length, 2);
      }
      int[] orders = new int[count + 2];
      orders[first]++;
      if (second >= 0) {
        orders[second]++;
      }
      return cache.hessian.getPartialDerivative(orders);
    }
    return derivativeUncached(phase, term, first, second, third);
  }

  /**
   * Evaluates selected derivative directions without reuse, including mixed third derivatives with at most two T
   * directions.
   *
   * @param phase current phase
   * @param term electrolyte term selector
   * @param first first independent direction
   * @param second second independent direction, or -1
   * @param third third independent direction, or -1
   * @return requested derivative
   * @throws IllegalArgumentException if a third temperature derivative is requested
   */
  static double derivativeUncached(PhaseElectrolyteCPAMM phase, int term, int first, int second, int third) {
    int temperatureDirection = phase.getNumberOfComponents() + 1;
    if (first == temperatureDirection && second == temperatureDirection && third == temperatureDirection) {
      throw new IllegalArgumentException("MM electrolyte derivatives support at most two temperature directions");
    }

    int order = 1 + (second >= 0 ? 1 : 0) + (third >= 0 ? 1 : 0);
    int[] variables = new int[] {first, second, third};
    int[] unique = new int[3];
    int[] orders = new int[3];
    int dimensions = 0;
    for (int k = 0; k < order; k++) {
      int found = -1;
      for (int j = 0; j < dimensions; j++) {
        if (unique[j] == variables[k]) {
          found = j;
        }
      }
      if (found < 0) {
        found = dimensions;
        unique[dimensions++] = variables[k];
      }
      orders[found]++;
    }
    return evaluate(phase, term, unique, dimensions, order).getPartialDerivative(Arrays.copyOf(orders, dimensions));
  }

  /**
   * Evaluates the unchanged residual energies and all requested derivative directions together.
   *
   * @param phase current phase
   * @param term electrolyte term selector
   * @param unique independent variable indices
   * @param dimensions number of independent variables
   * @param order maximum derivative order
   * @return energy and derivatives
   */
  private static DerivativeStructure evaluate(PhaseElectrolyteCPAMM phase, int term, int[] unique, int dimensions,
      int order) {
    int count = phase.getNumberOfComponents();
    DerivativeStructure zero = new DerivativeStructure(dimensions, order, 0);
    DerivativeStructure temperature = variable(phase.getTemperature(), count + 1, unique, dimensions, order);
    DerivativeStructure volume = variable(phase.getTotalVolume(), count, unique, dimensions, order).multiply(1e-5);
    DerivativeStructure deltaT = temperature.subtract(phase.getTemperature());
    DerivativeStructure totalMoles = zero;
    DerivativeStructure packing = zero;
    DerivativeStructure ionPacking = zero;
    DerivativeStructure chargeSum = zero;
    DerivativeStructure diameterSum = zero;
    DerivativeStructure bornSum = zero;
    DerivativeStructure solventWeight = zero;
    DerivativeStructure permittivitySum = zero;
    DerivativeStructure[] moles = new DerivativeStructure[count];
    DerivativeStructure[] permittivity = new DerivativeStructure[count];
    DerivativeStructure[] weights = new DerivativeStructure[count];
    int solvents = 0;
    int[] solventIndices = new int[2];
    PhaseElectrolyteCPAMM.DielectricMixingRule mixingRule = phase.getDielectricMixingRule();
    for (int i = 0; i < count; i++) {
      ComponentInterface component = phase.getComponent(i);
      moles[i] = variable(component.getNumberOfMolesInPhase(), i, unique, dimensions, order);
      totalMoles = totalMoles.add(moles[i]);
      double diameter = component.getLennardJonesMolecularDiameter() * 1e-10;
      double charge = component.getIonicCharge();
      DerivativeStructure componentPacking = moles[i].multiply(AVOGADRO * Math.PI / 6 * diameter * diameter * diameter)
          .divide(volume);
      packing = packing.add(componentPacking);
      if (charge != 0) {
        ionPacking = ionPacking.add(componentPacking);
        chargeSum = chargeSum.add(moles[i].multiply(charge * charge));
        diameterSum = diameterSum.add(moles[i].multiply(charge * charge * diameter));
        double radius = ((ComponentSrkCPAMM) component).getBornRadiusMeters();
        if (radius > 0) {
          bornSum = bornSum.add(moles[i].multiply(charge * charge / radius));
        }
      } else {
        if (solvents < 2) {
          solventIndices[solvents] = i;
        }
        solvents++;
        // The temperature Taylor polynomial gives exact derivatives through the required second order.
        permittivity[i] = deltaT.multiply(component.getDielectricConstantdT(phase.getTemperature()))
            .add(deltaT.multiply(deltaT).multiply(0.5 * component.getDielectricConstantdTdT(phase.getTemperature())))
            .add(component.getDielectricConstant(phase.getTemperature()));
        double molarVolume = mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE ? 1
            : component.getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18;
        }
        weights[i] = moles[i].multiply(molarVolume);
        solventWeight = solventWeight.add(weights[i]);
        DerivativeStructure contribution = permittivity[i];
        if (mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA) {
          contribution = contribution.pow(1.0 / 3);
        } else if (mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.LICHTENECKER) {
          contribution = contribution.log();
        }
        permittivitySum = permittivitySum.add(weights[i].multiply(contribution));
      }
    }
    if (solventWeight.getValue() <= 0) {
      return zero;
    }
    DerivativeStructure solventPermittivity = permittivitySum.divide(solventWeight);
    if (mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA) {
      solventPermittivity = solventPermittivity.pow(3);
    } else if (mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.LICHTENECKER) {
      solventPermittivity = solventPermittivity.exp();
    } else if (mixingRule == PhaseElectrolyteCPAMM.DielectricMixingRule.OSTER && solvents == 2) {
      int i = solventIndices[0];
      int j = solventIndices[1];
      solventPermittivity = solventPermittivity
          .add(weights[i].multiply(weights[j]).divide(solventWeight.multiply(solventWeight))
              .multiply(permittivity[i].subtract(permittivity[j])).multiply(-0.3));
    }
    DerivativeStructure mixturePermittivity = solventPermittivity.multiply(ionPacking.negate().add(1))
        .divide(ionPacking.multiply(0.5).add(1));
    DerivativeStructure energy = zero;
    if ((term == 0 || term < 0) && phase.isDebyeHuckelOn() && chargeSum.getValue() > 0) {

      DerivativeStructure kappa = chargeSum
          .multiply(ELECTRON_CHARGE * ELECTRON_CHARGE * AVOGADRO / (VACUUM_PERMITTIVITY * (R / AVOGADRO)))
          .divide(volume.multiply(mixturePermittivity).multiply(temperature)).sqrt();
      DerivativeStructure diameter = diameterSum.divide(chargeSum);
      DerivativeStructure chi = kappa.multiply(diameter);
      DerivativeStructure psi;
      if (chi.getValue() < 0.1) {
        // Cancellation-free series for log(1 + chi) - chi + chi^2/2.
        psi = zero;
        DerivativeStructure power = chi.pow(3);
        for (int k = 3; k <= 16; k++) {
          psi = psi.add(power.multiply((k % 2 == 1 ? 1.0 : -1.0) / k));
          power = power.multiply(chi);
        }
      } else {
        psi = chi.log1p().subtract(chi).add(chi.multiply(chi).multiply(0.5));
      }
      energy = energy.add(volume.multiply(psi).divide(diameter.pow(3)).multiply(-1 / (4 * Math.PI * AVOGADRO)));
    }
    if ((term == 1 || term < 0) && phase.isBornOn()) {
      energy = energy.add(mixturePermittivity.reciprocal().subtract(1).multiply(bornSum).divide(temperature)
          .multiply(AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE / (8 * Math.PI * VACUUM_PERMITTIVITY * R)));
    }
    if ((term == 2 || term < 0) && phase.isShortRangeOn()) {
      DerivativeStructure interaction = zero;
      for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
          double parameter = phase.getWij(i, j);
          double temperatureDerivative = phase.getWijT(i, j);
          if (parameter != 0 || temperatureDerivative != 0) {
            interaction = interaction
                .add(moles[i].multiply(moles[j]).multiply(deltaT.multiply(temperatureDerivative).add(parameter)));
          }
        }
      }
      DerivativeStructure freeVolume = packing.negate().add(1);
      if (freeVolume.getValue() < 0.01) {
        freeVolume = zero.add(0.01);
      }
      energy = energy.add(interaction.divide(totalMoles.multiply(temperature).multiply(freeVolume)));
    }
    return energy;
  }

  /** Phase-local cache; no mutable state is shared between cloned phases. */
  static final class Cache {
    /** Exact input snapshot used for cache validation. */
    private final double[] state;
    /** Composition, temperature and volume derivatives through second order. */
    private DerivativeStructure hessian;
    /** Volume derivatives through third order. */
    private DerivativeStructure volume;

    /**
     * Creates an empty derivative cache for one exact state.
     *
     * @param state all inputs affecting the electrolyte residual energy
     */
    Cache(double[] state) {
      this.state = state;
    }
  }

  /**
   * Captures all model inputs, including mutable component parameters and term switches.
   *
   * @param phase current phase
   * @return exact values compared without a numerical tolerance
   */
  private static double[] state(PhaseElectrolyteCPAMM phase) {
    int nc = phase.getNumberOfComponents();
    double[] state = new double[6 + 8 * nc + 2 * nc * nc];
    int k = 0;
    state[k++] = phase.getTemperature();
    state[k++] = phase.getTotalVolume();
    state[k++] = phase.getDielectricMixingRule().ordinal();
    state[k++] = phase.isDebyeHuckelOn() ? 1 : 0;
    state[k++] = phase.isBornOn() ? 1 : 0;
    state[k++] = phase.isShortRangeOn() ? 1 : 0;
    for (int i = 0; i < nc; i++) {
      ComponentSrkCPAMM c = (ComponentSrkCPAMM) phase.getComponent(i);
      state[k++] = c.getNumberOfMolesInPhase();
      state[k++] = c.getLennardJonesMolecularDiameter();
      state[k++] = c.getIonicCharge();
      state[k++] = c.getBornRadiusMeters();
      state[k++] = c.getCriticalVolume();
      state[k++] = c.getDielectricConstant(phase.getTemperature());
      state[k++] = c.getDielectricConstantdT(phase.getTemperature());
      state[k++] = c.getDielectricConstantdTdT(phase.getTemperature());
    }
    for (int i = 0; i < nc; i++) {
      for (int j = 0; j < nc; j++) {
        state[k++] = phase.getWij(i, j);
        state[k++] = phase.getWijT(i, j);
      }
    }
    return state;
  }

  /**
   * Creates a constant or an independent automatic-differentiation variable.
   *
   * @param value current scalar value
   * @param direction physical variable index
   * @param variables selected physical variable indices
   * @param dimensions number of independent directions
   * @param order maximum derivative order
   * @return derivative structure for this variable
   */
  private static DerivativeStructure variable(double value, int direction, int[] variables, int dimensions, int order) {
    for (int i = 0; i < dimensions; i++) {
      if (variables[i] == direction) {
        return new DerivativeStructure(dimensions, order, i, value);
      }
    }
    return new DerivativeStructure(dimensions, order, value);
  }
}
