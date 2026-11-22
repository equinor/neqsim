package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies native cooler configuration authority and physical utility-loss response.
 *
 * @author NeqSim study validation
 * @version 1.0
 */
class CoolerDynamicPolicyTest {
  /**
   * Builds a native sensible-gas thermal design with analytic NTU reference ln(4).
   *
   * @return initialized dynamic cooler
   */
  private Cooler cooler() {
    SystemSrkEos fluid = new SystemSrkEos(353.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("THERMAL-FEED", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    Cooler cooler = new Cooler("THERMAL-COOLER", feed);
    cooler.setOutTemperature(35.0, "C");
    cooler.run();
    cooler.configureDynamicTemperatureControl(10000.0, 80.0, 35.0, 20.0, "C");
    cooler.setDynamicTimeConstants(0.0, 0.0);
    cooler.setCalculateSteadyState(false);
    return cooler;
  }

  /** Checks JSON policy round trip and static reconfiguration preserve connected physical state. */
  @SuppressWarnings("unchecked")
  @Test
  void jsonPolicyRoundTripDoesNotMovePhysicalState() {
    Cooler source = cooler();
    source.setDynamicTimeConstants(0.5, 1.0);
    source.setCoolingUtilityAvailabilityFraction(0.75);
    Map<String, Object> policy = new Gson()
        .fromJson(new Gson().toJson(source.getDynamicTemperatureControlConfiguration()), Map.class);
    Cooler target = cooler();
    target.setCoolingValveOpening(71.0);
    double temperature = target.getOutletStream().getTemperature("K");
    double flow = target.getOutletStream().getFlowRate("mole/sec");
    UUID id = target.getCalculationIdentifier();
    target.applyDynamicTemperatureControlConfiguration(policy);
    assertEquals(source.getDynamicTemperatureControlConfiguration(),
        target.getDynamicTemperatureControlConfiguration());
    assertEquals(71.0, target.getCoolingValveOpening(), 0.0);
    assertEquals(temperature, target.getOutletStream().getTemperature("K"), 0.0);
    assertEquals(flow, target.getOutletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(id, target.getCalculationIdentifier());
  }

  /** Checks incomplete, mistyped and nonphysical policies fail atomically. */
  @Test
  void invalidPolicyDoesNotPartiallyApply() {
    Cooler cooler = cooler();
    Map<String, Object> original = cooler.getDynamicTemperatureControlConfiguration();
    for (Object value : new Object[] {Double.valueOf(Double.NaN), Double.valueOf(-0.1), Double.valueOf(1.1), "0.5"}) {
      Map<String, Object> changed = new LinkedHashMap<String, Object>(original);
      changed.put("utilityAvailabilityFraction", value);
      changed.put("designNtu", Double.valueOf(2.0));
      assertThrows(IllegalArgumentException.class, () -> cooler.applyDynamicTemperatureControlConfiguration(changed));
      assertEquals(original, cooler.getDynamicTemperatureControlConfiguration());
    }
    Map<String, Object> incomplete = new LinkedHashMap<String, Object>(original);
    incomplete.remove("enabled");
    assertThrows(IllegalArgumentException.class, () -> cooler.applyDynamicTemperatureControlConfiguration(incomplete));
    Map<String, Object> unknown = new LinkedHashMap<String, Object>(original);
    unknown.put("unreviewedSetting", Double.valueOf(1.0));
    assertThrows(IllegalArgumentException.class, () -> cooler.applyDynamicTemperatureControlConfiguration(unknown));
    assertThrows(IllegalArgumentException.class, () -> cooler.setCoolingUtilityAvailabilityFraction(Double.NaN));
    assertEquals(original, cooler.getDynamicTemperatureControlConfiguration());
  }

  /** Checks full, half and lost utility against the independent analytic effectiveness equation. */
  @Test
  void availabilityScalesNativeHeatTransferAndLossGivesZeroDuty() {
    Cooler cooler = cooler();
    for (double availability : new double[] {1.0, 0.5, 0.0}) {
      cooler.setCoolingUtilityAvailabilityFraction(availability);
      cooler.runTransient(1.0, UUID.randomUUID());
      double expected = 293.15 + 60.0 * Math.exp(-Math.log(4.0) * availability);
      assertEquals(expected, cooler.getOutletStream().getTemperature("K"), 1.0e-9);
      assertEquals(50.0, cooler.getCoolingValveOpening(), 0.0);
      assertEquals(cooler.getInletStream().getFlowRate("kg/hr"), cooler.getOutletStream().getFlowRate("kg/hr"), 1.0e-8);
      assertEquals(availability, cooler.getDynamicTemperatureControlConfiguration().get("utilityAvailabilityFraction"));
    }
    assertFalse(cooler.isDynamicCoolingUtilityAvailable());
    assertEquals(0.0, cooler.getDuty("W"), 1.0e-4);
    cooler.setDynamicCoolingUtilityAvailable(true);
    assertEquals(1.0, cooler.getCoolingUtilityAvailabilityFraction(), 0.0);
  }

  /** Checks a real held TT and native TIC can demand full opening without fabricating unavailable cooling. */
  @Test
  void lostUtilityDoesNotEraseActualControllerDemand() {
    Cooler cooler = cooler();
    cooler.setDynamicCoolingUtilityAvailable(false);
    cooler.runTransient(1.0, UUID.randomUUID());
    TemperatureTransmitter transmitter = new TemperatureTransmitter("ACTUAL-TT", cooler.getOutletStream());
    transmitter.setUnit("C");
    transmitter.setMinimumValue(20.0);
    transmitter.setMaximumValue(120.0);
    transmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("ACTUAL-TIC");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(35.0, "C");
    controller.setControllerParameters(4.0, 30.0, 0.0);
    controller.setOutputLimits(0.0, 100.0);
    cooler.setController(controller);
    UUID id = UUID.randomUUID();
    transmitter.sampleTransient(0.1, id);
    cooler.runTransient(0.1, id);
    assertTrue(controller.getResponse() > 50.0);
    assertEquals(controller.getResponse(), cooler.getCoolingValveOpening(), 1.0e-12);
    assertEquals(80.0, cooler.getOutletStream().getTemperature("C"), 1.0e-9);
    assertEquals(80.0, transmitter.getTransientSample("C"), 1.0e-9);
    assertEquals(0.0, cooler.getDuty("W"), 1.0e-4);
  }

  /** Checks the default disabled policy remains valid and finite. */
  @Test
  void disabledDefaultPolicyRoundTrips() {
    Cooler cooler = new Cooler("UNCONFIGURED");
    Map<String, Object> policy = cooler.getDynamicTemperatureControlConfiguration();
    cooler.applyDynamicTemperatureControlConfiguration(policy);
    assertEquals(policy, cooler.getDynamicTemperatureControlConfiguration());
    assertFalse(cooler.isDynamicTemperatureControlEnabled());
    assertTrue(cooler.isDynamicCoolingUtilityAvailable());
  }

  /**
   * Checks an actual old-class serialized object has full utility availability after migration.
   *
   * @throws Exception if the public synthetic legacy fixture cannot be read
   */
  @Test
  void legacySerializedCoolerDefaultsToFullUtilityAvailability() throws Exception {
    String resource = "/neqsim/process/equipment/heatexchanger/cooler-dynamic-control-v1.ser.gz.base64";
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (InputStream input = getClass().getResourceAsStream(resource)) {
      assertNotNull(input);
      byte[] chunk = new byte[4096];
      int count;
      while ((count = input.read(chunk)) != -1) {
        buffer.write(chunk, 0, count);
      }
    }
    byte[] bytes = Base64.getMimeDecoder().decode(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    Cooler restored;
    try (ObjectInputStream input = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
      restored = (Cooler) input.readObject();
    }
    assertTrue(restored.isDynamicTemperatureControlEnabled());
    assertEquals(1.0, restored.getCoolingUtilityAvailabilityFraction(), 0.0);
    restored.runTransient(0.1, UUID.randomUUID());
    assertEquals(35.0, restored.getOutletStream().getTemperature("C"), 1.0e-9);
  }
}
