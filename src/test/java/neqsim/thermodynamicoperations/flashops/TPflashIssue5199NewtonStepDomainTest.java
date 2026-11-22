package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for issue 5199, where a Newton step longer than the feasible composition interval made the two-phase
 * iteration report a converged single hydrocarbon phase.
 *
 * @author ESOL
 * @version 1.0
 */
class TPflashIssue5199NewtonStepDomainTest {
  /**
   * Creates the gas, oil and water fluid of the issue, flashed at 80 C and 170 bara.
   *
   * @return flashed three-phase fluid with 75,000 kg/hr total flow
   */
  private static SystemInterface createFlashedFeed() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 170.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("n-decane", 6.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(75000.0, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    return fluid;
  }

  /**
   * Flashes a copy of the feed at new conditions.
   *
   * @param feed flashed feed fluid
   * @param pressureBara flash pressure in bara
   * @param temperatureK flash temperature in K
   * @return flashed fluid with initialized properties
   */
  private static SystemInterface flashAt(SystemInterface feed, double pressureBara, double temperatureK) {
    SystemInterface fluid = feed.clone();
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperatureK, "K");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Checks that the fluid has gas, oil and aqueous phases in density order with the expected amounts.
   *
   * @param fluid flashed fluid
   * @param gasFraction expected gas mole fraction
   * @param gasDensity expected gas density in kg/m3
   * @param oilDensity expected oil density in kg/m3
   */
  private static void assertGasOilAqueous(SystemInterface fluid, double gasFraction, double gasDensity,
      double oilDensity) {
    assertEquals(3, fluid.getNumberOfPhases());
    assertEquals(PhaseType.GAS, fluid.getPhase(0).getType());
    assertEquals(PhaseType.OIL, fluid.getPhase(1).getType());
    assertEquals(PhaseType.AQUEOUS, fluid.getPhase(2).getType());
    assertEquals(gasFraction, fluid.getBeta(0), 1.0e-3);
    assertEquals(gasDensity, fluid.getPhase(0).getDensity("kg/m3"), 0.5);
    assertEquals(oilDensity, fluid.getPhase(1).getDensity("kg/m3"), 1.0);
  }

  /**
   * The two conditions of the issue returned one hydrocarbon phase of about 327 kg/m3 next to the aqueous phase.
   */
  @Test
  void keepsGasOilSplitWhenNewtonStepLeavesDomain() {
    SystemInterface feed = createFlashedFeed();

    assertGasOilAqueous(flashAt(feed, 172.1307121963362, 339.2846604025287), 0.5093, 134.2, 536.2);
    assertGasOilAqueous(flashAt(feed, 199.284052894054, 352.2238470289977), 0.4821, 155.65, 501.0);
  }

  /**
   * Before the fix this condition returned the oil phase after the aqueous phase.
   */
  @Test
  void ordersPhasesByDensityWhenNewtonStepLeavesDomain() {
    SystemInterface feed = createFlashedFeed();

    assertGasOilAqueous(flashAt(feed, 195.13439516761738, 347.6278016342866), 0.4830, 153.09, 508.7);
  }
}
