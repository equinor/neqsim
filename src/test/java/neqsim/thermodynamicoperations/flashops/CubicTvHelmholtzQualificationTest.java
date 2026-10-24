package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent Helmholtz and caloric closure for the corrected quadratic-covolume TV endpoint.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CubicTvHelmholtzQualificationTest {
  /** Frozen molar inventories from the public 50-addition TV fixture. */
  private static final double[] FEED = {702.5935344274613, 123.98709431063615, 10.27690550561485, 1.1418783893713018};
  /** Gas constant in NeqSim's internal cubic units. */
  private static final double R = 8.3144621;

  /**
   * Checks nearby temperatures against an independent EOS Helmholtz expression and its temperature derivative.
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {293.05, 293.15, 293.25})
  void lowerHelmholtzEndpointClosesEnergyAndMaterial(double temperature) {
    SystemInterface fluid = new SystemUMRPRUMCEos(temperature, 20.0);
    String[] names = {"methane", "ethane", "n-pentane", "nC16"};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], FEED[i]);
    }
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TVflash(0.998, "m3");
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(0.998, fluid.getVolume("m3"), 1.0e-10);
    double splitEnergy = 0.0;
    double totalVolume = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseEos phase = (PhaseEos) fluid.getPhase(p);
      double[] amounts = new double[FEED.length];
      double sum = 0.0;
      for (int i = 0; i < amounts.length; i++) {
        amounts[i] = phase.getComponent(i).getNumberOfMolesInPhase();
        sum += phase.getComponent(i).getx();
      }
      assertEquals(1.0, sum, 1.0e-12);
      assertEquals(fluid.getPressure(), phase.calcPressure(), 1.0e-8);
      double volume = phase.getTotalVolume();
      totalVolume += volume;
      splitEnergy += helmholtz(phase, amounts, volume, temperature);
      // Ures=-R*T^2*d(Fres/RT)/dT; adding PV-nRT gives Hres at this frozen state.
      double coarse = residualEnthalpy(phase, amounts, volume, temperature, 0.02);
      double fine = residualEnthalpy(phase, amounts, volume, temperature, 0.01);
      assertEquals(fine, coarse, 1.0e-3);
      assertEquals(phase.getHresTP(), fine, 1.0e-3);
    }
    double onePhaseEnergy = helmholtz((PhaseEos) fluid.getPhase(0), FEED, totalVolume, temperature);
    assertTrue(splitEnergy < onePhaseEnergy - 25000.0, "the split must lower A at fixed T,V,n by over 25 kJ");
    for (int i = 0; i < FEED.length; i++) {
      double recovered = 0.0;
      double reference = Math.log(fluid.getPhase(0).getComponent(i).getx())
          + Math.log(fluid.getPhase(0).getComponent(i).getFugacityCoefficient());
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        recovered += fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
        assertEquals(reference, Math.log(fluid.getPhase(p).getComponent(i).getx())
            + Math.log(fluid.getPhase(p).getComponent(i).getFugacityCoefficient()), 1.0e-8);
      }
      assertEquals(FEED[i], recovered, 1.0e-10);
    }
  }

  /**
   * Evaluates an independent molar-inventory Helmholtz expression, omitting component-only ideal constants.
   *
   * @param phase phase providing pure alpha functions and fixed interaction coefficients
   * @param amounts fixed component amounts in moles
   * @param volume extensive volume in internal units
   * @param temperature temperature in kelvin
   * @return Helmholtz energy in joules, with the same omitted ideal constants for every split
   */
  private static double helmholtz(PhaseEos phase, double[] amounts, double volume, double temperature) {
    double n = 0.0;
    double attraction = 0.0;
    double covolume = 0.0;
    double ideal = 0.0;
    EosMixingRulesInterface rule = phase.getMixingRule();
    for (int i = 0; i < amounts.length; i++) {
      ComponentEosInterface ci = (ComponentEosInterface) phase.getComponent(i);
      n += amounts[i];
      ideal += amounts[i] * (Math.log(amounts[i] / volume) - 1.0);
      for (int j = 0; j < amounts.length; j++) {
        ComponentEosInterface cj = (ComponentEosInterface) phase.getComponent(j);
        attraction += amounts[i] * amounts[j] * Math.sqrt(ci.aT(temperature) * cj.aT(temperature))
            * (1.0 - rule.getBinaryInteractionParameter(i, j));
        double rootB = 0.5 * (Math.sqrt(ci.getb()) + Math.sqrt(cj.getb()));
        covolume += amounts[i] * amounts[j] * rootB * rootB;
      }
    }
    covolume /= n;
    double residual = -n * Math.log(1.0 - covolume / volume)
        - attraction / (R * temperature * covolume * (phase.delta1 - phase.delta2))
            * Math.log((volume + phase.delta1 * covolume) / (volume + phase.delta2 * covolume));
    return R * temperature * (ideal + residual);
  }

  /**
   * Differentiates the independent Helmholtz expression at fixed V,n to recover residual enthalpy.
   *
   * @param phase initialized phase
   * @param amounts fixed molar inventories
   * @param volume fixed volume in internal units
   * @param temperature center temperature in kelvin
   * @param step temperature step in kelvin
   * @return residual enthalpy in joules
   */
  private static double residualEnthalpy(PhaseEos phase, double[] amounts, double volume, double temperature,
      double step) {
    double center = helmholtz(phase, amounts, volume, temperature);
    double slope = (helmholtz(phase, amounts, volume, temperature + step)
        - helmholtz(phase, amounts, volume, temperature - step)) / (2.0 * step);
    double n = 0.0;
    for (double amount : amounts) {
      n += amount;
    }
    return center - temperature * slope + phase.getPressure() * volume - n * R * temperature;
  }
}
