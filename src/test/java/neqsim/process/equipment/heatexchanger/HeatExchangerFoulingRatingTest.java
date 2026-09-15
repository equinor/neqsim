package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests fixed-geometry fouling rating and stream coupling for issue 3718. */
class HeatExchangerFoulingRatingTest extends neqsim.NeqSimTest {
  private HeatExchanger exchanger(double tubeTemperature, double shellTemperature) {
    SystemInterface fluid = new SystemSrkEos(tubeTemperature + 273.15, 20.0);
    fluid.addComponent("n-heptane", 0.7);
    fluid.addComponent("n-octane", 0.3);
    fluid.setMixingRule("classic");
    Stream tube = new Stream("tube feed", fluid);
    tube.setFlowRate(8.0, "kg/sec");
    Stream shell = new Stream("shell feed", fluid.clone());
    shell.setFlowRate(10.0, "kg/sec");
    shell.setTemperature(shellTemperature, "C");
    HeatExchanger exchanger = new HeatExchanger("feed preheater", tube, shell);
    ThermalDesignCalculator calculator = new ThermalDesignCalculator();
    calculator.setTubeIDm(0.020);
    calculator.setTubeODm(0.025);
    calculator.setTubePitchm(0.032);
    calculator.setTubeCount(100);
    calculator.setTubePasses(2);
    calculator.setTubeLengthm(4.0);
    calculator.setFoulingTube(0.0);
    calculator.setFoulingShell(0.0);
    exchanger.setRatingCalculator(calculator);
    exchanger.setRatingArea(Math.PI * 0.025 * 4.0 * 100);
    exchanger.setUseRatingPressureDrop(true);
    return exchanger;
  }

  private void assertBalances(HeatExchanger exchanger, UUID id) {
    double energyResidual = 0.0;
    for (int side = 0; side < 2; side++) {
      SystemInterface inlet = exchanger.getInStream(side).getThermoSystem();
      SystemInterface outlet = exchanger.getOutStream(side).getThermoSystem();
      energyResidual += outlet.getEnthalpy() - inlet.getEnthalpy();
      assertEquals(inlet.getFlowRate("kg/sec"), outlet.getFlowRate("kg/sec"), 1e-9);
      for (int component = 0; component < inlet.getNumberOfComponents(); component++) {
        assertEquals(inlet.getComponent(component).getNumberOfmoles(),
            outlet.getComponent(component).getNumberOfmoles(), 1e-9);
      }
      assertEquals(id, exchanger.getOutStream(side).getCalculationIdentifier());
      assertTrue(Double.isFinite(outlet.getCp("J/kgK")));
      assertEquals(1, outlet.getNumberOfPhases());
    }
    // The PH solver can leave milliwatt residuals for an isenthalpic (zero-duty) liquid pressure drop.
    assertEquals(0.0, energyResidual, Math.max(0.01, 1e-5 * exchanger.getDuty()));
    assertEquals(id, exchanger.getCalculationIdentifier());
    assertEquals(exchanger.getInStream(0).getPressure() - exchanger.getRatingCalculator().getTubeSidePressureDropBar(),
        exchanger.getOutStream(0).getPressure(), 1e-9);
    assertEquals(exchanger.getInStream(1).getPressure() - exchanger.getRatingCalculator().getShellSidePressureDropBar(),
        exchanger.getOutStream(1).getPressure(), 1e-9);
  }

  @Test
  void foulingUpdatesTheLiveProcessAndCleaningRestoresIt() {
    HeatExchanger exchanger = exchanger(30.0, 110.0);
    ProcessSystem process = new ProcessSystem();
    process.add(exchanger.getInStream(0));
    process.add(exchanger.getInStream(1));
    process.add(exchanger);
    process.run();
    double cleanDuty = exchanger.getDuty();
    double cleanPressure = exchanger.getOutStream(0).getPressure();
    double cleanTemperature = exchanger.getOutStream(0).getTemperature();
    exchanger.getRatingCalculator().setTubeFoulingLayer(0.001, 0.2);
    assertTrue(exchanger.needRecalculation(), "Mutable calculator changes must not be skipped");
    process.run();
    assertTrue(exchanger.getDuty() < cleanDuty);
    assertTrue(exchanger.getOutStream(0).getPressure() < cleanPressure);
    assertTrue(exchanger.getOutStream(0).getTemperature() < cleanTemperature);
    assertBalances(exchanger, exchanger.getCalculationIdentifier());
    double fouledDuty = exchanger.getDuty();
    process.run();
    assertEquals(fouledDuty, exchanger.getDuty(), 1e-8 * cleanDuty);
    exchanger.getRatingCalculator().setTubeFoulingLayer(0.0, 0.2);
    process.run();
    assertEquals(cleanDuty, exchanger.getDuty(), 1e-8 * cleanDuty);
    assertEquals(cleanPressure, exchanger.getOutStream(0).getPressure(), 1e-9);
  }

  @Test
  void eitherSideCanBeHotAndIdentityAndEnergyArePreserved() {
    for (double tubeTemperature : new double[] { 30.0, 110.0 }) {
      HeatExchanger exchanger = exchanger(tubeTemperature, 140.0 - tubeTemperature);
      UUID id = UUID.randomUUID();
      exchanger.run(id);
      assertTrue(exchanger.getDuty() > 0.0);
      assertBalances(exchanger, id);
    }
  }

  @Test
  void equalInletTemperaturesGiveZeroHeatDutyWithPressureLoss() {
    HeatExchanger exchanger = exchanger(60.0, 60.0);
    UUID id = UUID.randomUUID();
    exchanger.run(id);
    assertEquals(0.0, exchanger.getDuty(), 0.0);
    assertBalances(exchanger, id);
  }

  @Test
  void defaultRatingKeepsExistingOutletPressures() {
    HeatExchanger exchanger = exchanger(30.0, 110.0);
    exchanger.setUseRatingPressureDrop(false);
    exchanger.run();
    for (int side = 0; side < 2; side++) {
      assertEquals(exchanger.getInStream(side).getPressure(), exchanger.getOutStream(side).getPressure(), 1e-9);
    }
    assertFalse(exchanger.isUseRatingPressureDrop());
  }

  @Test
  void invalidPressureBudgetDoesNotCommitEitherOutlet() {
    HeatExchanger exchanger = exchanger(30.0, 110.0);
    exchanger.run();
    double oldTubeTemperature = exchanger.getOutStream(0).getTemperature();
    double oldShellPressure = exchanger.getOutStream(1).getPressure();
    UUID oldId = exchanger.getCalculationIdentifier();
    exchanger.getRatingCalculator().setTubeLengthm(1e7);
    assertThrows(IllegalStateException.class, () -> exchanger.run(UUID.randomUUID()));
    assertEquals(oldTubeTemperature, exchanger.getOutStream(0).getTemperature(), 0.0);
    assertEquals(oldShellPressure, exchanger.getOutStream(1).getPressure(), 0.0);
    assertEquals(oldId, exchanger.getCalculationIdentifier());
  }

  @Test
  void missingConfigurationAndConflictingSpecificationsFailExplicitly() {
    HeatExchanger exchanger = exchanger(30.0, 110.0);
    exchanger.setRatingArea(0.0);
    assertThrows(IllegalStateException.class, exchanger::run);
    exchanger.setRatingArea(30.0);
    exchanger.setOutTemperature(60.0, "C");
    assertThrows(IllegalStateException.class, exchanger::run);
    HeatExchanger missingCalculator = new HeatExchanger("unconfigured");
    missingCalculator.setUseRatingPressureDrop(true);
    assertThrows(IllegalStateException.class, missingCalculator::run);
  }

  @Test
  void multiphaseFeedIsRejectedInsteadOfUsingBulkSinglePhaseCorrelations() {
    HeatExchanger exchanger = exchanger(30.0, 110.0);
    SystemInterface wet = new SystemSrkEos(303.15, 20.0);
    wet.addComponent("methane", 0.5);
    wet.addComponent("n-heptane", 0.5);
    wet.setMixingRule("classic");
    exchanger.getInStream(0).setThermoSystem(wet);
    exchanger.getInStream(0).setFlowRate(8.0, "kg/sec");
    assertThrows(IllegalStateException.class, exchanger::run);
  }

  @Test
  void singlePhaseEndpointsOnOppositeSidesOfBoilingAreRejected() {
    HeatExchanger exchanger = exchanger(30.0, 200.0);
    for (int side = 0; side < 2; side++) {
      SystemInterface pure = new SystemSrkEos(side == 0 ? 303.15 : 473.15, 1.0);
      pure.addComponent("n-heptane", 1.0);
      pure.setMixingRule("classic");
      exchanger.getInStream(side).setThermoSystem(pure);
      exchanger.getInStream(side).setFlowRate(side == 0 ? 8.0 : 1.0, "kg/sec");
      exchanger.getInStream(side).run();
      assertEquals(1, exchanger.getInStream(side).getThermoSystem().getNumberOfPhases());
    }
    assertThrows(IllegalStateException.class, exchanger::run);
  }
}
