package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.mixingrule.EosMixingRuleHandler.ClassicSRK;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression checks for unsupported produced-water interaction tuning.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProducedWaterDegassingTuningTest {
  /** Verifies that a tuning request fails before a default process can be built. */
  @Test
  void rejectsUnsupportedTuningBeforeBuild() {
    ProducedWaterDegassingSystem model = new ProducedWaterDegassingSystem("rejected");
    UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
        () -> model.setTunedInteractionParameters(true));
    assertTrue(failure.getMessage().contains("validated model"));
    assertTrue(failure.getMessage().contains("false"));
    assertNull(model.getProcessSystem());
    model.build();
    assertNotNull(model.getProcessSystem());
  }

  /** Verifies that a rejected request leaves an already built model untouched. */
  @Test
  void rejectedRequestPreservesBuiltProcess() {
    ProducedWaterDegassingSystem model = new ProducedWaterDegassingSystem("built");
    model.build();
    ProcessSystem process = model.getProcessSystem();
    Stream inlet = (Stream) process.getUnit("built_Inlet");
    double temperature = inlet.getFluid().getTemperature();
    assertThrows(UnsupportedOperationException.class, () -> model.setTunedInteractionParameters(true));
    assertSame(process, model.getProcessSystem());
    assertEquals(temperature, inlet.getFluid().getTemperature(), 0.0);
  }

  /**
   * Verifies public entry points reject the true flag retained by legacy serialized objects.
   *
   * @throws Exception if the legacy state cannot be restored or serialized
   */
  @Test
  void legacyFlagCannotBypassBuildOrEitherRunEntryPoint() throws Exception {
    for (boolean built : new boolean[] {false, true}) {
      ProducedWaterDegassingSystem model = new ProducedWaterDegassingSystem("legacy");
      if (built) {
        model.build();
      }
      ProcessSystem original = model.getProcessSystem();
      Field flag = ProducedWaterDegassingSystem.class.getDeclaredField("useTunedKijParameters");
      flag.setAccessible(true);
      flag.setBoolean(model, true);
      assertThrows(UnsupportedOperationException.class, model::build);
      assertThrows(UnsupportedOperationException.class, model::run);
      assertThrows(UnsupportedOperationException.class, () -> model.run(UUID.randomUUID()));
      assertSame(original, model.getProcessSystem());
      model.setTunedInteractionParameters(false);
      model.build();
      assertNotNull(model.getProcessSystem());
    }
  }

  /**
   * Verifies that a deserialized historical tuning flag also fails before use.
   *
   * @throws Exception if serialization or legacy field restoration fails
   */
  @Test
  void serializedLegacyFlagIsRejected() throws Exception {
    ProducedWaterDegassingSystem historical = new ProducedWaterDegassingSystem("serialized");
    Field flag = ProducedWaterDegassingSystem.class.getDeclaredField("useTunedKijParameters");
    flag.setAccessible(true);
    flag.setBoolean(historical, true);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(historical);
    }
    ProducedWaterDegassingSystem restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (ProducedWaterDegassingSystem) input.readObject();
    }
    assertThrows(UnsupportedOperationException.class, restored::build);
    assertThrows(UnsupportedOperationException.class, restored::run);
    assertThrows(UnsupportedOperationException.class, () -> restored.run(UUID.randomUUID()));
    assertNull(restored.getProcessSystem());
    restored.setTunedInteractionParameters(false);
    restored.build();
    assertNotNull(restored.getProcessSystem());
  }

  /** Verifies actual stored and effective default coefficients on public build-path stage fluids. */
  @Test
  void defaultCoefficientsSurviveTemperatureOrderAndStageCloning() {
    for (double temperature : new double[] {348.15, 353.15, 358.15}) {
      for (boolean explicitDefault : new boolean[] {false, true}) {
        ProducedWaterDegassingSystem model = new ProducedWaterDegassingSystem("defaults");
        model.setWaterTemperature(temperature, "K");
        if (explicitDefault) {
          model.setTunedInteractionParameters(false);
        }
        Map<String, Double> composition = new LinkedHashMap<>();
        composition.put("methane", 0.44);
        composition.put("CO2", 0.51);
        composition.put("propane", 0.01);
        composition.put("ethane", 0.04);
        model.setDissolvedGasComposition(composition);
        composition.clear();
        assertThrows(UnsupportedOperationException.class, () -> model.setTunedInteractionParameters(true));
        model.build();
        Stream inlet = (Stream) model.getProcessSystem().getUnit("defaults_Inlet");
        SystemInterface inletFluid = inlet.getFluid();
        assertEquals(temperature, inletFluid.getTemperature(), 1e-12);
        assertEquals(0.95, inletFluid.getPhase(0).getComponent("water").getz(), 1e-12);
        assertDefaultCoefficients(inletFluid, temperature);

        // Construct an independent database fluid with water last, changing all pair indices.
        SystemSrkCPAstatoil reordered = new SystemSrkCPAstatoil(temperature, 30.0);
        reordered.addComponent("propane", 0.0005);
        reordered.addComponent("ethane", 0.002);
        reordered.addComponent("methane", 0.022);
        reordered.addComponent("CO2", 0.0255);
        reordered.addComponent("water", 0.95);
        reordered.setMixingRule(10);
        assertDefaultCoefficients(reordered, temperature);

        for (String stage : new String[] {"DegasserHeater", "CFUHeater", "CaissonHeater"}) {
          Heater heater = (Heater) model.getProcessSystem().getUnit("defaults_" + stage);
          assertDefaultCoefficients(heater.getOutletStream().getFluid(), temperature);
        }
        SystemInterface clone = inletFluid.clone();
        clone.setTemperature(temperature + 2.0);
        assertDefaultCoefficients(clone, temperature + 2.0);
        assertEquals(temperature, inletFluid.getTemperature(), 1e-12);
        model.setWaterTemperature(temperature + 2.0, "K");
        model.build();
        Stream rebuilt = (Stream) model.getProcessSystem().getUnit("defaults_Inlet");
        assertEquals(temperature + 2.0, rebuilt.getFluid().getTemperature(), 1e-12);
        assertDefaultCoefficients(rebuilt.getFluid(), temperature + 2.0);
        assertEquals(temperature, inletFluid.getTemperature(), 1e-12);
      }
    }
  }

  /**
   * Check both stored coefficients and the actual mixing rule's temperature evaluation by component name.
   *
   * @param fluid fluid or stage clone to inspect
   * @param temperature evaluation temperature in kelvin
   */
  private static void assertDefaultCoefficients(SystemInterface fluid, double temperature) {
    String[] gases = {"CO2", "methane"};
    double[] bases = {-0.27686, -0.827413423};
    double[] slopes = {0.001121, 0.0026055};
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      EosMixingRulesInterface rule = (EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
      int water = fluid.getPhase(phase).getComponent("water").getComponentNumber();
      for (int gas = 0; gas < gases.length; gas++) {
        int component = fluid.getPhase(phase).getComponent(gases[gas]).getComponentNumber();
        assertEquals(bases[gas], rule.getBinaryInteractionParameter(water, component), 1e-12);
        assertEquals(slopes[gas], rule.getBinaryInteractionParameterT1(water, component), 1e-12);
        assertEquals(bases[gas], rule.getBinaryInteractionParameter(component, water), 1e-12);
        assertEquals(slopes[gas], rule.getBinaryInteractionParameterT1(component, water), 1e-12);
        assertEquals(bases[gas] + slopes[gas] * temperature, ((ClassicSRK) rule).getkij(temperature, water, component),
            1e-12);
      }
    }
  }
}
