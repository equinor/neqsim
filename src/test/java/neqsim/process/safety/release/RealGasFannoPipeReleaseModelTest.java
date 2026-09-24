package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Physical limits, conservation and process integration for the EOS-backed pipe model. */
class RealGasFannoPipeReleaseModelTest extends neqsim.NeqSimTest {
  private static SystemInterface nitrogen(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static SystemInterface richGas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(340.0, pressureBar);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static ReleaseFlowResult calculate(SystemInterface fluid, double backPressurePa, double lengthM,
      double frictionFactor) {
    return new RealGasFannoPipeReleaseModel()
        .calculate(new ReleaseFlowRequest(fluid, 0.08, 0.9, backPressurePa, lengthM, frictionFactor));
  }

  @Test
  void diluteGasConvergesToAnalyticalFannoLimit() {
    ReleaseFlowRequest request = new ReleaseFlowRequest(nitrogen(2.0), 0.08, 0.9, 101325.0, 12.0, 0.015);
    ReleaseFlowResult real = new RealGasFannoPipeReleaseModel().calculate(request);
    ReleaseFlowResult ideal = new IdealGasFannoPipeReleaseModel().calculate(request);
    assertTrue(real.isUsable(), diagnostic(real));
    assertTrue(ideal.isUsable(), diagnostic(ideal));
    assertEquals(ideal.isChoked(), real.isChoked());
    assertEquals(ideal.getMassFlowRateKgS(), real.getMassFlowRateKgS(), ideal.getMassFlowRateKgS() * 0.025);
    assertEquals(ideal.getStations().get(Station.ORIFICE_EXIT).getPressurePa(),
        real.getStations().get(Station.ORIFICE_EXIT).getPressurePa(),
        ideal.getStations().get(Station.ORIFICE_EXIT).getPressurePa() * 0.035);
  }

  @Test
  void denseGasClosesMassEnergyAndShowsRealGasDeparture() {
    ReleaseFlowRequest request = new ReleaseFlowRequest(richGas(120.0), 0.08, 0.85, 4.0e6, 40.0, 0.018);
    ReleaseFlowResult real = new RealGasFannoPipeReleaseModel().calculate(request);
    ReleaseFlowResult ideal = new IdealGasFannoPipeReleaseModel().calculate(request);
    assertTrue(real.isUsable(), diagnostic(real));
    assertFalse(real.isChoked());
    ReleaseState upstream = real.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState exit = real.getStations().get(Station.ORIFICE_EXIT);
    assertEquals(upstream.getEnthalpyJkg(), exit.getEnthalpyJkg() + 0.5 * exit.getVelocityMs() * exit.getVelocityMs(),
        Math.abs(upstream.getEnthalpyJkg()) * 2.0e-7);
    assertEquals(request.getEffectiveAreaM2() * exit.getMassFluxKgM2s(), real.getMassFlowRateKgS(),
        real.getMassFlowRateKgS() * 2.0e-7);
    assertEquals(4.0e6, exit.getPressurePa(), 200.0);
    assertTrue(exit.getVelocityMs() / real.getThroatSoundSpeedMs() > 0.1);
    assertTrue(exit.getVelocityMs() / real.getThroatSoundSpeedMs() < 1.0);
    assertNotEquals(ideal.getMassFlowRateKgS(), real.getMassFlowRateKgS(), ideal.getMassFlowRateKgS() * 0.01);
  }

  @Test
  void frictionAndBackpressureHavePhysicalTrends() {
    ReleaseFlowResult shortPipe = calculate(richGas(80.0), 2.0e6, 8.0, 0.012);
    ReleaseFlowResult longPipe = calculate(richGas(80.0), 2.0e6, 40.0, 0.02);
    ReleaseFlowResult highBackpressure = calculate(richGas(80.0), 7.0e6, 40.0, 0.02);
    assertTrue(shortPipe.isUsable(), diagnostic(shortPipe));
    assertTrue(longPipe.isUsable(), diagnostic(longPipe));
    assertTrue(highBackpressure.isUsable(), diagnostic(highBackpressure));
    assertTrue(shortPipe.getMassFlowRateKgS() > longPipe.getMassFlowRateKgS());
    assertFalse(highBackpressure.isChoked());
    assertTrue(highBackpressure.getMassFlowRateKgS() < longPipe.getMassFlowRateKgS());
    assertEquals(7.0e6, highBackpressure.getStations().get(Station.ORIFICE_EXIT).getPressurePa(), 120.0);
  }

  @Test
  void phaseAppearanceFailsClosed() {
    SystemInterface wet = new SystemSrkEos(285.0, 60.0);
    wet.addComponent("methane", 0.55);
    wet.addComponent("n-hexane", 0.45);
    wet.setMixingRule("classic");
    ReleaseFlowResult result = calculate(wet, 101325.0, 40.0, 0.02);
    assertFalse(result.isUsable());
    assertTrue(result.getStatus() == ReleaseFlowResult.Status.UNSUPPORTED
        || result.getStatus() == ReleaseFlowResult.Status.INVALID);
  }

  @Test
  void processSystemAndProcessModelEmitRealGasPipeFrames() {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", richGas(30.0));
      feed.setFlowRate(100.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("area", process);
        session = new SourceTermSession("real-gas-fanno", model);
        session.addLongPipeSource("rupture", "area", "feed", -1, 0.05, 1.0, 1.0e6, 20.0, 0.015,
            new RealGasFannoPipeReleaseModel());
      } else {
        session = new SourceTermSession("real-gas-fanno", process);
        session.addLongPipeSource("rupture", "feed", 0.05, 1.0, 1.0e6, 20.0, 0.015, new RealGasFannoPipeReleaseModel());
      }
      List<SourceTermFrame> frames = session.runSteadyState();
      assertEquals(1, frames.size());
      assertTrue(frames.get(0).getStatus() == SourceTermFrame.Status.VALID
          || frames.get(0).getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, frames.get(0).toJson());
      assertTrue(frames.get(0).toJson().contains("real-gas-fanno-pipe"));
      SourceTermFrame.verifyEnvelope(frames.get(0).toJson());
    }
  }

  @Test
  void coupledInventoryConservesAndRefines() {
    double[] pressurePa = new double[3];
    double[] releasedMassKg = new double[3];
    for (int refinement = 0; refinement < 3; refinement++) {
      double maxSubstep = 0.01 / Math.pow(2.0, refinement);
      ReleaseInventory inventory = new ReleaseInventory("real-gas-pipe", richGas(30.0), 1.0, 0.02, 1.0, 1.0e6, 15.0,
          0.015, new RealGasFannoPipeReleaseModel(), maxSubstep);
      double initialMass = inventory.getFluid().getMass("kg");
      double initialEnergy = inventory.getFluid().getInternalEnergy("J");
      inventory.runTransient(0.01, UUID.randomUUID());
      ReleaseInventory.Balance balance = inventory.getBalance();
      assertEquals(initialMass, inventory.getFluid().getMass("kg") + balance.getReleasedMassKg(), initialMass * 1.0e-8);
      assertEquals(initialEnergy, inventory.getFluid().getInternalEnergy("J") + balance.getReleasedEnergyJ(),
          Math.abs(initialEnergy) * 2.0e-7);
      pressurePa[refinement] = inventory.getFluid().getPressure("Pa");
      releasedMassKg[refinement] = balance.getReleasedMassKg();
    }
    assertTrue(Math.abs(pressurePa[2] - pressurePa[1]) < Math.abs(pressurePa[1] - pressurePa[0]));
    assertTrue(Math.abs(releasedMassKg[2] - releasedMassKg[1]) < Math.abs(releasedMassKg[1] - releasedMassKg[0]));
  }

  private static String diagnostic(ReleaseFlowResult result) {
    return result.getStatus() + ": " + result.getDiagnostics().get(0).getCode() + " - "
        + result.getDiagnostics().get(0).getMessage();
  }
}
