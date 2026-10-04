package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Analytical, closure, failure and API examples for short-opening source terms. */
class ReleaseFlowModelTest extends neqsim.NeqSimTest {
  private SystemInterface methane(double pressure) {
    SystemInterface gas = new SystemSrkEos(300.0, pressure);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    return gas;
  }

  private ReleaseFlowResult calculate(SystemInterface gas, double back) {
    return new HomogeneousEquilibriumReleaseModel().calculate(new ReleaseFlowRequest(gas, 0.01, 0.62, back));
  }

  private void usable(ReleaseFlowResult result) {
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
  }

  @Test
  void diluteGasMatchesIndependentIdealGasChokedLimit() {
    SystemInterface gas = methane(0.1);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    double gamma = gas.getGamma();
    double gasConstant = 8.314462618 / gas.getMolarMass();
    double expected = 0.62 * Math.PI * 0.01 * 0.01 / 4 * 10000.0 * Math.sqrt(gamma / (gasConstant * 300.0))
        * Math.pow(2 / (gamma + 1), (gamma + 1) / (2 * (gamma - 1)));
    ReleaseFlowResult result = calculate(gas, 500.0);
    usable(result);
    assertTrue(result.isChoked());
    assertEquals(expected, result.getMassFlowRateKgS(), expected * 0.015);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    assertEquals(10000 * Math.pow(2 / (gamma + 1), gamma / (gamma - 1)), throat.getPressurePa(), 150.0);
    assertNotNull(result.getThroatSoundSpeedMs());
    assertEquals(1.0, throat.getVelocityMs() / result.getThroatSoundSpeedMs(), 0.01);
  }

  @Test
  void unchokedLimitAndNoForwardFlow() {
    ReleaseFlowResult low = calculate(methane(5.0), 490000.0);
    usable(low);
    assertFalse(low.isChoked());
    assertEquals(490000.0, low.getStations().get(Station.THROAT_CRITICAL).getPressurePa(), 1e-5);
    ReleaseFlowResult none = calculate(methane(5.0), 500000.0);
    usable(none);
    assertEquals(0.0, none.getMassFlowRateKgS());
    assertFalse(none.getStations().containsKey(Station.AMBIENT_EXPANDED));
  }

  @Test
  void documentationExampleAndEnergyClosure() {
    SystemInterface gas = methane(50.0);
    ReleaseFlowModel model = new HomogeneousEquilibriumReleaseModel();
    ReleaseFlowResult result = LeakModel.builder().fluid(gas).holeDiameter(0.01).dischargeCoefficient(0.62)
        .backPressure(101325.0).build().calculateReleaseFlow(gas, model);
    usable(result);
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    for (ReleaseState state : result.getStations().values()) {
      assertEquals(upstream.getEntropyJkgK(), state.getEntropyJkgK(), 1e-5);
      assertEquals(upstream.getEnthalpyJkg(),
          state.getEnthalpyJkg() + 0.5 * state.getVelocityMs() * state.getVelocityMs(), 1e-5);
      assertEquals(1.0, state.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
    }
    assertSame(result.getStations().get(Station.THROAT_CRITICAL), result.getStations().get(Station.ORIFICE_EXIT));
    assertEquals(50.0, gas.getPressure());
    assertEquals(300.0, gas.getTemperature());
  }

  @Test
  void liquidFlashingUsesMassFractionsAndPreservesInventory() {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("propane", 1.0);
    fluid.setMixingRule("classic");
    ReleaseFlowResult result = calculate(fluid, 300000.0);
    usable(result);
    ReleaseState expanded = result.getStations().get(Station.AMBIENT_EXPANDED);
    assertTrue(expanded.getGasMassFraction() > 0.0 && expanded.getGasMassFraction() < 1.0);
    assertEquals(1.0, expanded.getComponentMoleFractions().get("propane"), 1e-10);
    assertTrue(result.getMassFlowRateKgS() > 0.0);
  }

  @Test
  void mixtureGasAndFlashingBoundaryConserveEntropy() {
    SystemInterface gas = methane(5.0);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    ReleaseFlowResult valid = calculate(gas, 1e5);
    usable(valid);
    assertEquals(1.0 / 1.1,
        valid.getStations().get(Station.AMBIENT_EXPANDED).getComponentMoleFractions().get("methane"), 1e-10);
    SystemInterface liquid = new SystemSrkEos(300.0, 20.0);
    liquid.addComponent("propane", 0.8);
    liquid.addComponent("n-butane", 0.2);
    liquid.setMixingRule("classic");
    ReleaseFlowResult flashing = calculate(liquid, 3e5);
    usable(flashing);
    assertEquals("1.2.0", flashing.getModelVersion());
    assertTrue(flashing.getStations().get(Station.AMBIENT_EXPANDED).getGasMassFraction() > 0.0);
  }

  @Test
  void requestAndResultAreDefensiveAndSerializable() throws Exception {
    SystemInterface gas = methane(5.0);
    ReleaseFlowRequest request = new ReleaseFlowRequest(gas, 0.01, 0.62, 101325.0);
    gas.setPressure(10.0);
    request.getFluid().setPressure(15.0);
    assertEquals(5.0, request.getFluid().getPressure());
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
    usable(result);
    assertThrows(UnsupportedOperationException.class, () -> result.getStations().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> result.getStations().get(Station.UPSTREAM_STAGNATION).getComponentMoleFractions().clear());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(result);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      ReleaseFlowResult restored = (ReleaseFlowResult) in.readObject();
      assertEquals(result.getMassFlowRateKgS(), restored.getMassFlowRateKgS());
    }
  }

  @Test
  void invalidAndUnsupportedNeverReturnPlausibleZero() {
    assertThrows(IllegalArgumentException.class, () -> new ReleaseFlowRequest(methane(5.0), Double.NaN, 0.62, 1e5));
    assertThrows(IllegalArgumentException.class, () -> new ReleaseFlowRequest(methane(5.0), 0.01, 1.2, 1e5));
    ReleaseFlowResult invalid = calculate(new SystemSrkEos(300.0, 5.0), 1e5);
    assertEquals(ReleaseFlowResult.Status.INVALID, invalid.getStatus());
    assertThrows(IllegalStateException.class, invalid::getMassFlowRateKgS);
    SystemInterface solidEnabled = methane(5.0);
    solidEnabled.setHydrateCheck(true);
    ReleaseFlowResult unsupported = calculate(solidEnabled, 1e5);
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, unsupported.getStatus());
    assertTrue(unsupported.getStations().isEmpty());
  }

  @Test
  void areaAndCoefficientScaleRateWithoutChangingThroat() {
    ReleaseFlowModel model = new HomogeneousEquilibriumReleaseModel();
    ReleaseFlowResult first = model.calculate(new ReleaseFlowRequest(methane(5.0), 0.01, 0.5, 1e5));
    ReleaseFlowResult second = model.calculate(new ReleaseFlowRequest(methane(5.0), 0.02, 1.0, 1e5));
    usable(first);
    usable(second);
    assertEquals(8 * first.getMassFlowRateKgS(), second.getMassFlowRateKgS(), 1e-10);
  }
}
