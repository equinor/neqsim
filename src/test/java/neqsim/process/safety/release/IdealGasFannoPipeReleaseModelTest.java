package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Analytical limits, physical trends and process integration for the ideal-gas Fanno model. */
class IdealGasFannoPipeReleaseModelTest extends neqsim.NeqSimTest {
  private static SystemInterface gas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static ReleaseFlowResult calculate(double pressureBar, double backPressurePa, double lengthM,
      double frictionFactor) {
    return new IdealGasFannoPipeReleaseModel()
        .calculate(new ReleaseFlowRequest(gas(pressureBar), 0.1, 1.0, backPressurePa, lengthM, frictionFactor));
  }

  @Test
  void chokedSolutionClosesMassEnergyAndMach() {
    SystemInterface caller = gas(50.0);
    double callerMoles = caller.getTotalNumberOfMoles();
    ReleaseFlowRequest request = new ReleaseFlowRequest(caller, 0.1, 0.8, 101325.0, 100.0, 0.02);
    ReleaseFlowResult result = new IdealGasFannoPipeReleaseModel().calculate(request);
    assertTrue(result.isUsable(), result.getDiagnostics().toString());
    assertTrue(result.isChoked());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState exit = result.getStations().get(Station.ORIFICE_EXIT);
    assertEquals(1.0, exit.getVelocityMs() / result.getThroatSoundSpeedMs(), 1e-10);
    assertEquals(upstream.getEnthalpyJkg(), exit.getEnthalpyJkg() + 0.5 * exit.getVelocityMs() * exit.getVelocityMs(),
        1e-8);
    assertEquals(request.getEffectiveAreaM2() * exit.getMassFluxKgM2s(), result.getMassFlowRateKgS(), 1e-12);
    assertEquals(callerMoles, caller.getTotalNumberOfMoles(), 0.0);
    assertEquals(50.0, caller.getPressure(), 0.0);
    assertTrue(request.hasFlowPath());
    assertEquals(100.0, request.getFlowPathLengthM(), 0.0);
    assertEquals(0.02, request.getDarcyFrictionFactor(), 0.0);

    SystemInterface reference = caller.clone();
    new ThermodynamicOperations(reference).TPflash();
    reference.init(3);
    double gamma = reference.getGamma();
    double inletMach = referenceInletMach(1.0,
        request.getDarcyFrictionFactor() * request.getFlowPathLengthM() / request.getDiameterM(), gamma);
    double exitStagnationPressurePa = upstream.getPressurePa() / referenceStagnationPressureRatio(inletMach, gamma);
    double expectedExitPressurePa = exitStagnationPressurePa
        / Math.pow(1.0 + 0.5 * (gamma - 1.0), gamma / (gamma - 1.0));
    assertEquals(expectedExitPressurePa, exit.getPressurePa(), expectedExitPressurePa * 1e-11);
    double expectedRate = request.getEffectiveAreaM2() * exit.getDensityKgM3()
        * Math.sqrt(gamma * 8.31446261815324 / reference.getMolarMass() * exit.getTemperatureK());
    assertEquals(expectedRate, result.getMassFlowRateKgS(), expectedRate * 1e-11);
  }

  @Test
  void frictionLengthBackpressureAndDiameterHavePhysicalTrends() {
    ReleaseFlowResult shortPipe = calculate(50.0, 101325.0, 10.0, 0.01);
    ReleaseFlowResult longPipe = calculate(50.0, 101325.0, 100.0, 0.02);
    ReleaseFlowResult highBackpressure = calculate(50.0, 4.5e6, 100.0, 0.02);
    assertTrue(shortPipe.getMassFlowRateKgS() > longPipe.getMassFlowRateKgS());
    assertFalse(highBackpressure.isChoked());
    assertTrue(highBackpressure.getMassFlowRateKgS() < longPipe.getMassFlowRateKgS());
    assertEquals(4.5e6, highBackpressure.getStations().get(Station.ORIFICE_EXIT).getPressurePa(), 1e-6);

    ReleaseFlowResult noFlow = calculate(50.0, 5.1e6, 100.0, 0.02);
    assertTrue(noFlow.isUsable());
    assertEquals(0.0, noFlow.getMassFlowRateKgS(), 0.0);
    assertFalse(noFlow.isChoked());
  }

  @Test
  void pathGeometryCannotBeSilentlyUsedByShortOpeningModels() {
    ReleaseFlowRequest pipe = new ReleaseFlowRequest(gas(20.0), 0.05, 0.8, 101325.0, 20.0, 0.02);
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, new IdealGasReleaseModel().calculate(pipe).getStatus());
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED,
        new HomogeneousEquilibriumReleaseModel().calculate(pipe).getStatus());
    assertEquals(ReleaseFlowResult.Status.INVALID, new LegacyScreeningReleaseModel().calculate(pipe).getStatus());
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, new IdealGasFannoPipeReleaseModel()
        .calculate(new ReleaseFlowRequest(gas(20.0), 0.05, 0.8, 101325.0)).getStatus());
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseFlowRequest(gas(20.0), 0.05, 0.8, 101325.0, 20.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new ReleaseFlowRequest(gas(20.0), 0.05, 0.8, 101325.0, 20.0, 1.01));
  }

  @Test
  void processSystemAndProcessModelEmitPipeFrames() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", gas(10.0));
      feed.setFlowRate(100.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("area", process);
        session = new SourceTermSession("fanno", model);
        session.addLongPipeSource("rupture", "area", "feed", -1, 0.05, 1.0, 101325.0, 30.0, 0.02,
            new IdealGasFannoPipeReleaseModel());
      } else {
        session = new SourceTermSession("fanno", process);
        session.addLongPipeSource("rupture", "feed", 0.05, 1.0, 101325.0, 30.0, 0.02,
            new IdealGasFannoPipeReleaseModel());
      }
      List<SourceTermFrame> frames = session.runSteadyState();
      assertEquals(1, frames.size());
      assertTrue(frames.get(0).getStatus() == SourceTermFrame.Status.VALID
          || frames.get(0).getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, frames.get(0).toJson());
      assertTrue(frames.get(0).toJson().contains("ideal-gas-fanno-pipe"));
      assertTrue(frames.get(0).toJson().contains("flowPathLength"));
      assertTrue(frames.get(0).toJson().contains("darcyFrictionFactor"));
      SourceTermFrame.verifyEnvelope(frames.get(0).toJson());
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("fanno-pipe.json"), frames.get(0).toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void coupledInventoryRetainsPipeGeometryAndConservation() {
    ReleaseInventory inventory = new ReleaseInventory("pipe-inventory", gas(8.0), 1.0, 0.02, 1.0, 101325.0, 20.0, 0.02,
        new IdealGasFannoPipeReleaseModel(), 0.01);
    ReleaseFlowRequest before = inventory.getReleaseRequest();
    double initialMass = inventory.getFluid().getMass("kg");
    double initialEnergy = inventory.getFluid().getInternalEnergy("J");
    inventory.runTransient(0.05, UUID.randomUUID());
    ReleaseInventory.Balance balance = inventory.getBalance();
    assertTrue(balance.getReleasedMassKg() > 0.0);
    assertEquals(initialMass, inventory.getFluid().getMass("kg") + balance.getReleasedMassKg(), initialMass * 1e-9);
    assertEquals(initialEnergy, inventory.getFluid().getInternalEnergy("J") + balance.getReleasedEnergyJ(),
        Math.abs(initialEnergy) * 1e-7);
    assertTrue(inventory.getReleaseRequest().hasFlowPath());
    assertEquals(before.getFlowPathLengthM(), inventory.getReleaseRequest().getFlowPathLengthM(), 0.0);
    assertNotEquals(8.0, inventory.getFluid().getPressure(), 1e-10);
  }

  @Test
  void coupledInventoryRefinesInTime() throws Exception {
    double[] finalPressurePa = new double[3];
    double[] releasedMassKg = new double[3];
    StringBuilder csv = new StringBuilder("maxSubstep_s,pressure_Pa,releasedMass_kg\n");
    for (int refinement = 0; refinement < 3; refinement++) {
      double maxSubstep = 0.02 / Math.pow(2.0, refinement);
      ReleaseInventory inventory = new ReleaseInventory("pipe-inventory", gas(8.0), 1.0, 0.02, 1.0, 101325.0, 20.0,
          0.02, new IdealGasFannoPipeReleaseModel(), maxSubstep);
      inventory.runTransient(0.1, UUID.randomUUID());
      finalPressurePa[refinement] = inventory.getFluid().getPressure("Pa");
      releasedMassKg[refinement] = inventory.getBalance().getReleasedMassKg();
      csv.append(maxSubstep).append(',').append(finalPressurePa[refinement]).append(',')
          .append(releasedMassKg[refinement]).append('\n');
    }
    assertTrue(Math.abs(finalPressurePa[2] - finalPressurePa[1]) < Math.abs(finalPressurePa[1] - finalPressurePa[0]));
    assertTrue(Math.abs(releasedMassKg[2] - releasedMassKg[1]) < Math.abs(releasedMassKg[1] - releasedMassKg[0]));
    assertEquals(finalPressurePa[2], finalPressurePa[1], finalPressurePa[2] * 2.0e-6);
    assertEquals(releasedMassKg[2], releasedMassKg[1], releasedMassKg[2] * 2.0e-4);
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("fanno-inventory-refinement.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static double referenceInletMach(double exitMach, double frictionLength, double gamma) {
    double target = referenceFannoFunction(exitMach, gamma) + frictionLength;
    double low = 1.0e-10;
    double high = exitMach;
    for (int iteration = 0; iteration < 160; iteration++) {
      double middle = 0.5 * (low + high);
      if (referenceFannoFunction(middle, gamma) > target) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (low + high);
  }

  private static double referenceFannoFunction(double mach, double gamma) {
    double mach2 = mach * mach;
    return (1.0 - mach2) / (gamma * mach2)
        + (gamma + 1.0) / (2.0 * gamma) * Math.log((gamma + 1.0) * mach2 / (2.0 + (gamma - 1.0) * mach2));
  }

  private static double referenceStagnationPressureRatio(double mach, double gamma) {
    return 1.0 / mach
        * Math.pow((2.0 + (gamma - 1.0) * mach * mach) / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
  }
}
