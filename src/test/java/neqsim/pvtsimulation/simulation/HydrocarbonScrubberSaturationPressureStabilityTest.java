package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for the hydrocarbon-scrubber export-gas saturation boundary.
 */
@Tag("slow")
class HydrocarbonScrubberSaturationPressureStabilityTest extends neqsim.NeqSimTest {
  @Test
  void ordinaryAndEnhancedChecksRetainPhysicalSaturationBoundary() {
    ThreePhaseSeparator scrubber = runScrubberProcess();
    assertEquals(0.0, scrubber.getMassBalance("kg/hr"), 1.0);

    SystemInterface exportGas = scrubber.getGasOutStream().getFluid().clone();
    double ordinaryPressure = calculateSaturationPressure(exportGas, false);
    double enhancedPressure = calculateSaturationPressure(exportGas, true);
    double nearbyPressure = calculateSaturationPressureAtTemperature(exportGas, 1.0);

    assertEquals(105.9, ordinaryPressure, 0.5);
    assertEquals(ordinaryPressure, enhancedPressure, 0.25);
    assertTrue(Math.abs(nearbyPressure - ordinaryPressure) < 5.0);

    SystemInterface equilibrium = exportGas.clone();
    equilibrium.setMultiPhaseCheck(true);
    equilibrium.setTemperature(0.0, "C");
    equilibrium.setPressure(ordinaryPressure - 1.0e-3, "bara");
    new ThermodynamicOperations(equilibrium).TPflash();
    equilibrium.init(1);

    assertPhysicalEquilibrium(equilibrium);

    double repeatedPressure = calculateSaturationPressure(exportGas, false);
    assertEquals(ordinaryPressure, repeatedPressure, 1.0e-8);
  }

  private static ThreePhaseSeparator runScrubberProcess() {
    SystemInterface fluid = new SystemUMRPRUMCEos(280.0, 10.0);
    String[] componentNames = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "c-hexane", "n-heptane", "benzene", "n-octane", "c-C7", "toluene",
        "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12" };
    double[] componentAmounts = { 0.01, 0.01, 0.9, 0.1, 0.03, 0.01, 0.01, 0.01, 0.001, 0.001, 0.001, 0.001, 0.001,
        0.001, 0.0001, 0.0001, 0.0001, 0.0001, 0.0001, 0.00001, 0.00001, 3.0e-12, 3.0e-12, 3.0e-12 };
    for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
      fluid.addComponent(componentNames[componentIndex], componentAmounts[componentIndex]);
    }
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    Stream feed = new Stream("feed gas", fluid);
    feed.setFlowRate(25.0, "MSm3/day");
    feed.setTemperature(22.5, "C");
    feed.setPressure(81.0, "bara");
    Separator upstreamSeparator = new Separator("upstream separator", feed);
    Heater cooler = new Heater("cooler", upstreamSeparator.getGasOutStream());
    cooler.setOutPressure(78.0, "bara");
    cooler.setOutTemperature(15.0, "C");
    ThreePhaseSeparator scrubber = new ThreePhaseSeparator("dewpoint scrubber", cooler.getOutStream());
    scrubber.setEntrainment(0.5, "volume", "feed", "oil", "gas");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(upstreamSeparator);
    process.add(cooler);
    process.add(scrubber);
    process.run();
    return scrubber;
  }

  private static double calculateSaturationPressure(SystemInterface exportGas, boolean enhancedCheck) {
    SystemInterface fluid = exportGas.clone();
    fluid.setTemperature(0.0, "C");
    fluid.setPressure(10.0, "bara");
    fluid.setMultiPhaseCheck(true);
    if (enhancedCheck) {
      fluid.setEnhancedMultiPhaseCheck(true);
    }
    SaturationPressure saturationPressure = new SaturationPressure(fluid);
    saturationPressure.run();
    return saturationPressure.getSaturationPressure();
  }

  private static double calculateSaturationPressureAtTemperature(SystemInterface exportGas, double temperatureCelsius) {
    SystemInterface fluid = exportGas.clone();
    fluid.setTemperature(temperatureCelsius, "C");
    fluid.setPressure(10.0, "bara");
    fluid.setMultiPhaseCheck(true);
    SaturationPressure saturationPressure = new SaturationPressure(fluid);
    saturationPressure.run();
    return saturationPressure.getSaturationPressure();
  }

  private static void assertPhysicalEquilibrium(SystemInterface fluid) {
    assertEquals(2, fluid.getNumberOfPhases());

    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      betaSum += fluid.getBeta(phase);
      double compositionSum = 0.0;
      for (int component = 0; component < fluid.getPhase(phase).getNumberOfComponents(); component++) {
        double moleFraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(moleFraction));
        assertTrue(moleFraction >= 0.0);
        compositionSum += moleFraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-9);
    }
    assertEquals(1.0, betaSum, 1.0e-12);

    double maxFugacityResidual = 0.0;
    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      double recoveredFeed = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recoveredFeed += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recoveredFeed, 1.0e-10);

      double phaseZeroX = fluid.getPhase(0).getComponent(component).getx();
      double phaseOneX = fluid.getPhase(1).getComponent(component).getx();
      if (phaseZeroX > 1.0e-12 && phaseOneX > 1.0e-12) {
        double phaseZeroFugacity = phaseZeroX * fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
        double phaseOneFugacity = phaseOneX * fluid.getPhase(1).getComponent(component).getFugacityCoefficient();
        maxFugacityResidual = Math.max(maxFugacityResidual, Math.abs(Math.log(phaseZeroFugacity / phaseOneFugacity)));
      }
    }
    assertTrue(maxFugacityResidual <= 1.0e-8);
  }
}
