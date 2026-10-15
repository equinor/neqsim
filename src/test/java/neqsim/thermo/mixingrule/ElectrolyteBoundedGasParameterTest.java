package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compatibility and derivative contracts for the bounded Statoil gas/salt defaults.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteBoundedGasParameterTest extends neqsim.NeqSimTest {
  /**
   * Explicit three-coefficient pairs must retain priority through refresh, cloning and flash.
   *
   * @param gasName gas component name
   * @param ionName cation component name
   */
  @ParameterizedTest
  @CsvSource({"nitrogen, Na+", "propane, Ca++"})
  void explicitPairRetainsPriority(String gasName, String ionName) {
    SystemInterface fluid = brine(gasName, ionName, true);
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      ElectrolyteMixingRulesInterface rule = rule(phase);
      int gas = phase.getComponent(gasName).getComponentNumber();
      int ion = phase.getComponent(ionName).getComponentNumber();
      rule.setWijParameter(gas, ion, 0.0);
      rule.setWijT1Parameter(gas, ion, 2.34e-3);
      rule.setWijT2Parameter(gas, ion, 3.45e-5);
    }
    fluid.init(0);
    SystemInterface copy = fluid.clone();
    new ThermodynamicOperations(copy).TPflash();
    for (SystemInterface result : new SystemInterface[] {fluid, copy}) {
      for (int p = 0; p < result.getNumberOfPhases(); p++) {
        PhaseInterface phase = result.getPhase(p);
        ElectrolyteMixingRulesInterface rule = rule(phase);
        int gas = phase.getComponent(gasName).getComponentNumber();
        int ion = phase.getComponent(ionName).getComponentNumber();
        for (int[] pair : new int[][] {{gas, ion}, {ion, gas}}) {
          assertEquals(0.0, rule.getWijParameter(pair[0], pair[1]), 0.0);
          assertEquals(2.34e-3, rule.gettWijT1Parameter(pair[0], pair[1]), 0.0);
          assertEquals(3.45e-5, rule.gettWijT2Parameter(pair[0], pair[1]), 0.0);
        }
      }
    }
  }

  /**
   * Either changed global entry disables the specialized default, including on an existing fluid.
   *
   * @param gasName gas component name
   * @param ionName cation component name
   * @param changedIndex changed global parameter index
   * @param cationIndex global cation parameter index
   */
  @ParameterizedTest
  @CsvSource({"nitrogen, Na+, 12, 12", "nitrogen, Na+, 13, 12", "propane, Ca++, 6, 6", "propane, Ca++, 7, 6"})
  void globalOverridesRetainPriority(String gasName, String ionName, int changedIndex, int cationIndex) {
    SystemInterface fluid = brine(gasName, ionName, true);
    double original = FurstElectrolyteConstants.getFurstParamGasIon(changedIndex);
    try {
      FurstElectrolyteConstants.setFurstParamGasIon(changedIndex, original + 1e-6);
      assertFalse(FurstElectrolyteConstants.isDefaultGasIonParameter(changedIndex));
      fluid.init(0);
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        PhaseInterface phase = fluid.getPhase(p);
        int gas = phase.getComponent(gasName).getComponentNumber();
        int ion = phase.getComponent(ionName).getComponentNumber();
        assertEquals(FurstElectrolyteConstants.getFurstParamGasIon(cationIndex), rule(phase).getWijParameter(gas, ion),
            0.0);
        int offset = phase.getComponent(ion).getIonicCharge() == 2.0 ? 8 : 0;
        double expectedT1 = FurstElectrolyteConstants.getFurstParamTDep(offset)
            * phase.getComponent(ion).getStokesCationicDiameter()
            + FurstElectrolyteConstants.getFurstParamTDep(offset + 1);
        assertEquals(expectedT1, rule(phase).gettWijT1Parameter(gas, ion), 0.0);
      }
    } finally {
      FurstElectrolyteConstants.setFurstParamGasIon(changedIndex, original);
    }
  }

  /**
   * The unrelated ScRK electrolyte model retains its existing gas-ion coefficients.
   *
   * @param gasName gas component name
   * @param ionName cation component name
   * @param cationIndex global cation parameter index
   */
  @ParameterizedTest
  @CsvSource({"nitrogen, Na+, 12", "propane, Ca++, 6"})
  void otherEosFamilyRetainsDefault(String gasName, String ionName, int cationIndex) {
    SystemInterface fluid = brine(gasName, ionName, false);
    PhaseInterface phase = fluid.getPhase(0);
    assertEquals(FurstElectrolyteConstants.getFurstParamGasIon(cationIndex), rule(phase).getWijParameter(
        phase.getComponent(gasName).getComponentNumber(), phase.getComponent(ionName).getComponentNumber()), 0.0);
  }

  /**
   * Checks analytic temperature derivatives of the fitted nitrogen pair against finite differences.
   *
   * @param temperature temperature in K
   */
  @ParameterizedTest
  @CsvSource({"285.75", "303.15", "344.85"})
  void fittedTemperatureDerivatives(double temperature) {
    PhaseInterface phase = brine("nitrogen", "Na+", true).getPhase(0);
    ElectrolyteMixingRulesInterface rule = rule(phase);
    int gas = phase.getComponent("nitrogen").getComponentNumber();
    int ion = phase.getComponent("Na+").getComponentNumber();
    double step = 0.01;
    double first = (rule.getWij(gas, ion, temperature + step) - rule.getWij(gas, ion, temperature - step))
        / (2.0 * step);
    double second = (rule.getWijT(gas, ion, temperature + step) - rule.getWijT(gas, ion, temperature - step))
        / (2.0 * step);
    assertEquals(first, rule.getWijT(gas, ion, temperature), 2e-13);
    assertEquals(second, rule.getWijTT(gas, ion, temperature), 2e-15);
  }

  /**
   * Gets a phase's electrolyte mixing rule.
   *
   * @param phase electrolyte phase
   * @return electrolyte mixing rule
   */
  private static ElectrolyteMixingRulesInterface rule(PhaseInterface phase) {
    return ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
  }

  /**
   * Builds a charge-balanced brine using the requested EOS family.
   *
   * @param gas gas component name
   * @param ion cation component name
   * @param cpa whether to select the Statoil CPA model
   * @return initialized brine
   */
  private static SystemInterface brine(String gas, String ion, boolean cpa) {
    SystemInterface fluid = cpa ? new SystemElectrolyteCPAstatoil(298.15, 50.0)
        : new SystemFurstElectrolyteEos(298.15, 50.0);
    fluid.addComponent(gas, 0.01);
    fluid.addComponent("water", 1.0);
    fluid.addComponent(ion, 0.018);
    fluid.addComponent("Cl-", ion.equals("Ca++") ? 0.036 : 0.018);
    fluid.setMixingRule(10);
    fluid.init(0);
    return fluid;
  }
}
