package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class JcaFrmsWorkflowTest extends neqsim.NeqSimTest {
  private static final JsonObject FLUID_DATA = loadFluidData();

  private static JsonObject loadFluidData() {
    try (InputStream resourceStream = JcaFrmsWorkflowTest.class.getResourceAsStream(
        "/neqsim/process/measurementdevice/jca_frms_fluid_characterisation.json")) {
      Objects.requireNonNull(resourceStream, "Missing FRMS fluid data resource");
      try (Reader reader = new InputStreamReader(resourceStream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load FRMS fluid characterisation data", ex);
    }
  }

  private static SystemInterface createFluid(String fluidName, double waterFlow) throws Exception {
    JsonArray components = FLUID_DATA.getAsJsonArray(fluidName);
    SystemInterface fluidSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);

    for (JsonElement element : components) {
      JsonObject component = element.getAsJsonObject();
      String name = component.get("name").getAsString();
      double amount = component.get("amount").getAsDouble();

      JsonElement molarMassElement = component.get("molar_mass");
      JsonElement densityElement = component.get("density");

      if (molarMassElement != null && !molarMassElement.isJsonNull() && densityElement != null
          && !densityElement.isJsonNull() && Math.abs(densityElement.getAsDouble()) > 1.0e-12) {
        fluidSystem.addTBPfraction(name, amount, molarMassElement.getAsDouble() / 1000.0,
            densityElement.getAsDouble());
      } else {
        fluidSystem.addComponent(name, amount);
      }
    }

    if (waterFlow > 1.0e-5) {
      fluidSystem.addComponent("water", 10.0);
    }

    fluidSystem.init(0);
    fluidSystem.setMixingRule(2);
    fluidSystem.useVolumeCorrection(true);
    fluidSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluidSystem);
    operations.TPflash();
    fluidSystem.initPhysicalProperties();

    return fluidSystem;
  }

  @Test
  public void testFrmsWorkflowMatchesPythonReference() throws Exception {
    Map<Integer, String> fluidMap = createFluidMap();

    int fluidType = 2;
    double gasFlow = 16136.086849640644;
    double oilFlow = 154.34690355234105;
    double waterFlow = 76.32575518756704;
    double pressure = 58.61471567501469;
    double temperature = 60.984615325927734;
    SystemInterface oilFluid = createFluid(fluidMap.get(fluidType) + "_oil", waterFlow);

    Stream oilStream = new Stream("oil stream", oilFluid);
    oilStream.setPressure(1.0, "atm");
    oilStream.setTemperature(15.0, "C");
    oilStream.setFlowRate(oilFlow, "Sm3/hr");
    oilStream.run();

    FlowRateAdjuster flowAdjuster = new FlowRateAdjuster("flow rate adjuster", oilStream);
    flowAdjuster.setAdjustedFlowRates(gasFlow, oilFlow, waterFlow, "Sm3/hr");
    flowAdjuster.run();

    Heater multiphaseHeater = new Heater("multiphase heater", flowAdjuster.getOutletStream());
    multiphaseHeater.setOutPressure(pressure, "barg");
    multiphaseHeater.setOutTemperature(temperature, "C");
    multiphaseHeater.run();

    Stream multiphaseStream = new Stream("multiphase stream", multiphaseHeater.getOutletStream());
    multiphaseStream.setPressure(pressure, "barg");
    multiphaseStream.setTemperature(temperature, "C");
    multiphaseStream.run();

    SystemInterface multiphaseFluid = multiphaseStream.getFluid();

    SystemInterface bubbleFluid = multiphaseFluid.clone();
    ThermodynamicOperations bubbleOps = new ThermodynamicOperations(bubbleFluid);
    bubbleOps.bubblePointPressureFlash(false);
    // System.out.println("Havis mixture bubble point at " + temperature + " C = "
    // + bubbleFluid.getPressure("bara") + " bara");

    assertEquals(temperature, multiphaseFluid.getTemperature("C"), 1e-6);
    assertEquals(pressure, multiphaseFluid.getPressure("barg"), 1e-6);
    assertTrue(multiphaseFluid.hasPhaseType("gas"));
    assertTrue(multiphaseFluid.hasPhaseType("oil"));
    assertTrue(multiphaseFluid.hasPhaseType("aqueous"));

    // multiphaseFluid.prettyPrint();

    double gasSm3PerHour = multiphaseFluid.getPhase("gas").getFlowRate("Sm3/hr");
    double oilSm3PerHour = multiphaseFluid.getPhase("oil").getFlowRate("Sm3/hr");
    double waterSm3PerHour = multiphaseFluid.getPhase("aqueous").getFlowRate("Sm3/hr");

    assertEquals(10316.78638483792, gasSm3PerHour, 1e-6 * 10316.78638483792);
    assertEquals(20116.689061499594, oilSm3PerHour, 1e-6 * 20116.689061499594);
    assertEquals(101231.66074923359, waterSm3PerHour, 1e-6 * 101231.66074923359);

    assertTrue(multiphaseStream.getFlowRate("kg/hr") > 0.0);
  }

  @Test
  public void testFrmsWorkflowWithRandomVariation() throws Exception {
    Map<Integer, String> fluidMap = createFluidMap();
    int fluidType = 2;

    // Base values
    double baseGasFlow = 16136.086849640644;
    double baseOilFlow = 154.34690355234105;
    double baseWaterFlow = 76.32575518756704;
    double basePressure = 58.61471567501469;
    double baseTemperature = 60.984615325927734;

    Random random = new Random(92); // Fixed seed for reproducible tests
    int iterations = 200;
    int successfulRuns = 0;

    for (int i = 0; i < iterations; i++) {
      try {
        // System.out.println("Starting iteration " + (i + 1));
        // Apply 10% random variation to each parameter
        double gasFlow = baseGasFlow * (1.0 + (random.nextGaussian() * 0.1));
        double oilFlow = baseOilFlow * (1.0 + (random.nextGaussian() * 0.1));
        double waterFlow = baseWaterFlow * (1.0 + (random.nextGaussian() * 0.1));
        double pressure = basePressure * (1.0 + (random.nextGaussian() * 0.1));
        double temperature = baseTemperature * (1.0 + (random.nextGaussian() * 0.1));

        // System.out.printf(
        // "Iteration %d: gasFlow=%.12f, oilFlow=%.12f, waterFlow=%.12f, "
        // + "pressure=%.12f, temperature=%.12f%n",
        // i, gasFlow, oilFlow, waterFlow, pressure, temperature);

        // Ensure positive values
        gasFlow = Math.max(gasFlow, baseGasFlow * 0.1);
        oilFlow = Math.max(oilFlow, baseOilFlow * 0.1);
        waterFlow = Math.max(waterFlow, baseWaterFlow * 0.1);
        pressure = Math.max(pressure, basePressure * 0.01);
        temperature = Math.max(temperature, baseTemperature * 0.1);

        SystemInterface oilFluid = createFluid(fluidMap.get(fluidType) + "_oil", waterFlow);

        Stream oilStream = new Stream("oil stream", oilFluid);
        oilStream.setPressure(1.0, "atm");
        oilStream.setTemperature(15.0, "C");
        oilStream.setFlowRate(oilFlow, "Sm3/hr");
        oilStream.run();

        FlowRateAdjuster flowAdjuster = new FlowRateAdjuster("flow rate adjuster", oilStream);
        flowAdjuster.setAdjustedFlowRates(gasFlow, oilFlow, waterFlow, "Sm3/hr");
        flowAdjuster.run();

        Heater multiphaseHeater = new Heater("multiphase heater", flowAdjuster.getOutletStream());
        multiphaseHeater.setOutPressure(pressure, "barg");
        multiphaseHeater.setOutTemperature(temperature, "C");
        multiphaseHeater.run();

        Stream multiphaseStream =
            new Stream("multiphase stream", multiphaseHeater.getOutletStream());
        multiphaseStream.setPressure(pressure, "barg");
        multiphaseStream.setTemperature(temperature, "C");
        multiphaseStream.run();

        SystemInterface multiphaseFluid = multiphaseStream.getFluid();
        // multiphaseFluid.prettyPrint();
        // Same assertions as original test
        assertEquals(temperature, multiphaseFluid.getTemperature("C"), 1e-3); // Slightly relaxed
                                                                              // tolerance
        assertEquals(pressure, multiphaseFluid.getPressure("barg"), 1e-3); // Slightly relaxed
                                                                           // tolerance
        assertTrue(multiphaseFluid.hasPhaseType("gas"));
        assertTrue(multiphaseFluid.hasPhaseType("oil"));
        assertTrue(multiphaseFluid.hasPhaseType("aqueous"));

        double gasSm3PerHour = multiphaseFluid.getPhase("gas").getFlowRate("Sm3/hr");
        double oilSm3PerHour = multiphaseFluid.getPhase("oil").getFlowRate("Sm3/hr");
        double waterSm3PerHour = multiphaseFluid.getPhase("aqueous").getFlowRate("Sm3/hr");

        // Verify that flow rates are positive and reasonable
        assertTrue(gasSm3PerHour > 0.0);
        assertTrue(oilSm3PerHour > 0.0);
        assertTrue(waterSm3PerHour > 0.0);
        assertTrue(multiphaseStream.getFlowRate("kg/hr") > 0.0);

        successfulRuns++;
      } catch (Exception e) {
        // Log failed iteration but continue with others
        // System.out.println("Iteration " + i + " failed: " + e.getMessage());
      }
    }

    // Verify that at least 95% of runs were successful
    double successRate = (double) successfulRuns / iterations;
    assertTrue(successRate >= 0.95,
        "Success rate too low: " + successRate + " (" + successfulRuns + "/" + iterations + ")");

    // System.out.println("Random variation test completed: " + successfulRuns + "/" + iterations
    // + " successful runs (" + String.format("%.2f%%", successRate * 100) + ")");
  }

  @Test
  public void testFrmsWorkflowMatchesPythonReferenceFail() throws Exception {
    Map<Integer, String> fluidMap = createFluidMap();

    int fluidType = 2;
    double gasFlow = 15415.467139934792;
    double oilFlow = 155.051322567757;
    double waterFlow = 81.703410888761;
    double pressure = 57.021426919992;
    double temperature = 61.477058649392;
    SystemInterface oilFluid = createFluid(fluidMap.get(fluidType) + "_oil", waterFlow);

    Stream oilStream = new Stream("oil stream", oilFluid);
    oilStream.setPressure(1.0, "atm");
    oilStream.setTemperature(15.0, "C");
    oilStream.setFlowRate(oilFlow, "Sm3/hr");
    oilStream.run();

    FlowRateAdjuster flowAdjuster = new FlowRateAdjuster("flow rate adjuster", oilStream);
    flowAdjuster.setAdjustedFlowRates(gasFlow, oilFlow, waterFlow, "Sm3/hr");
    flowAdjuster.run();

    Heater multiphaseHeater = new Heater("multiphase heater", flowAdjuster.getOutletStream());
    multiphaseHeater.setOutPressure(pressure, "barg");
    multiphaseHeater.setOutTemperature(temperature, "C");
    multiphaseHeater.run();

    // multiphaseHeater.getOutletStream().getFluid().prettyPrint();

    Stream multiphaseStream = new Stream("multiphase stream", multiphaseHeater.getOutletStream());
    multiphaseStream.setPressure(pressure, "barg");
    multiphaseStream.setTemperature(temperature, "C");
    multiphaseStream.run();

    SystemInterface multiphaseFluid = multiphaseStream.getFluid();
    multiphaseFluid = multiphaseHeater.getOutletStream().getFluid();
    assertEquals(temperature, multiphaseFluid.getTemperature("C"), 1e-6);
    assertEquals(pressure, multiphaseFluid.getPressure("barg"), 1e-6);
    assertTrue(multiphaseFluid.hasPhaseType("gas"));
    assertTrue(multiphaseFluid.hasPhaseType("oil"));
    assertTrue(multiphaseFluid.hasPhaseType("aqueous"));

    // multiphaseFluid.prettyPrint();

    double gasSm3PerHour = multiphaseFluid.getPhase("gas").getFlowRate("Sm3/hr");
    double oilSm3PerHour = multiphaseFluid.getPhase("oil").getFlowRate("Sm3/hr");
    double waterSm3PerHour = multiphaseFluid.getPhase("aqueous").getFlowRate("Sm3/hr");

    assertTrue(gasSm3PerHour > 0.0);
    assertTrue(oilSm3PerHour > 0.0);
    assertTrue(waterSm3PerHour > 0.0);
    assertTrue(multiphaseStream.getFlowRate("kg/hr") > 0.0);
  }

  @Test
  public void testFrmsWorkflowMatchesPythonReferenceFail22() throws Exception {
    Map<Integer, String> fluidMap = createFluidMap();

    int fluidType = 2;
    double gasFlow = 15370.806598234587;
    double oilFlow = 162.553378552128;
    double waterFlow = 78.146176489922;
    double pressure = 62.679318142204;
    double temperature = 56.569494542287;

    SystemInterface oilFluid = createFluid(fluidMap.get(fluidType) + "_oil", waterFlow);

    Stream oilStream = new Stream("oil stream", oilFluid);
    oilStream.setPressure(1.0, "atm");
    oilStream.setTemperature(15.0, "C");
    oilStream.setFlowRate(oilFlow, "Sm3/hr");
    oilStream.run();

    FlowRateAdjuster flowAdjuster = new FlowRateAdjuster("flow rate adjuster", oilStream);
    flowAdjuster.setAdjustedFlowRates(gasFlow, oilFlow, waterFlow, "Sm3/hr");
    flowAdjuster.run();

    Heater multiphaseHeater = new Heater("multiphase heater", flowAdjuster.getOutletStream());
    multiphaseHeater.setOutPressure(pressure, "barg");
    multiphaseHeater.setOutTemperature(temperature, "C");
    multiphaseHeater.run();

    // multiphaseHeater.getOutletStream().getFluid().prettyPrint();

    Stream multiphaseStream = new Stream("multiphase stream", multiphaseHeater.getOutletStream());
    multiphaseStream.setPressure(pressure, "barg");
    multiphaseStream.setTemperature(temperature, "C");
    multiphaseStream.run();

    SystemInterface multiphaseFluid = multiphaseStream.getFluid();
    multiphaseFluid = multiphaseHeater.getOutletStream().getFluid();
    assertEquals(temperature, multiphaseFluid.getTemperature("C"), 1e-6);
    assertEquals(pressure, multiphaseFluid.getPressure("barg"), 1e-6);
    assertTrue(multiphaseFluid.hasPhaseType("gas"));
    assertTrue(multiphaseFluid.hasPhaseType("oil"));
    assertTrue(multiphaseFluid.hasPhaseType("aqueous"));

    // multiphaseFluid.prettyPrint();

    double gasSm3PerHour = multiphaseFluid.getPhase("gas").getFlowRate("Sm3/hr");
    double oilSm3PerHour = multiphaseFluid.getPhase("oil").getFlowRate("Sm3/hr");
    double waterSm3PerHour = multiphaseFluid.getPhase("aqueous").getFlowRate("Sm3/hr");

    assertTrue(gasSm3PerHour > 0.0);
    assertTrue(oilSm3PerHour > 0.0);
    assertTrue(waterSm3PerHour > 0.0);
    assertTrue(multiphaseStream.getFlowRate("kg/hr") > 0.0);
  }

  /**
   * Creates a deterministic, insertion-order map of fluid type indices to names. Java 8-compatible
   * replacement for Map.of used to maintain compatibility with older runtime environments. Returns
   * an unmodifiable view to discourage accidental mutation.
   */
  private static Map<Integer, String> createFluidMap() {
    LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
    map.put(0, "test");
    map.put(1, "Skrugard");
    map.put(2, "Havis");
    map.put(3, "Drivis");
    return Collections.unmodifiableMap(map);
  }
}
