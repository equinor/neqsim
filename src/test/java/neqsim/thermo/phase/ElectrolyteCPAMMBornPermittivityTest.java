package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemElectrolyteCPAMM;

/**
 * Checks that ion-ion and ion-solvation energies describe the same dielectric medium.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMBornPermittivityTest {
  /**
   * Validates the common-medium Born equation and its independent temperature derivative. The physical basis is
   * Kontogeorgis et al. (2018), doi:10.1016/j.fluid.2018.01.004.
   */
  @Test
  void bornEnergyUsesTheSameMixturePermittivityAsDebyeHuckel() {
    SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(298.15, 1);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1);
    fluid.addComponent("Cl-", 1);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    PhaseElectrolyteCPAMM phase = (PhaseElectrolyteCPAMM) fluid.getPhase(1);
    double prefactor = ThermodynamicConstantsInterface.avagadroNumber * 1.60217662e-19 * 1.60217662e-19
        / (8 * Math.PI * 8.854187817e-12 * ThermodynamicConstantsInterface.R * phase.getTemperature());
    double expected = prefactor * (1 / phase.getMixturePermittivity() - 1) * phase.getBornX();
    assertEquals(expected, phase.FBorn(), 1e-12);
    assertTrue(phase.getMixturePermittivity() < phase.getSolventPermittivity());
    assertTrue(Math.abs(phase.dFBorndV()) > 1e-8, "Ionic packing gives Born energy a volume derivative");
    double step = 0.001;
    PhaseElectrolyteCPAMM high = phase.clone();
    high.setTemperature(phase.getTemperature() + step);
    high.initElectrolyteProperties();
    PhaseElectrolyteCPAMM low = phase.clone();
    low.setTemperature(phase.getTemperature() - step);
    low.initElectrolyteProperties();
    assertEquals((high.FBornX() - low.FBornX()) / (2 * step), phase.FBornXT(), 1e-18);
  }
}
