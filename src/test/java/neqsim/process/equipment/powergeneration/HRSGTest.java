package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the HRSG class.
 */
class HRSGTest {

  private SystemSrkEos exhaustGas;
  private Stream exhaustStream;

  @BeforeEach
  void setUp() {
    // Simulate a hot exhaust gas
    exhaustGas = new SystemSrkEos(273.15 + 550.0, 1.01325);
    exhaustGas.addComponent("nitrogen", 0.75);
    exhaustGas.addComponent("CO2", 0.05);
    exhaustGas.addComponent("water", 0.10);
    exhaustGas.addComponent("oxygen", 0.10);
    exhaustGas.setMixingRule("classic");

    exhaustStream = new Stream("GT exhaust", exhaustGas);
    exhaustStream.setFlowRate(100.0, "kg/sec");
    exhaustStream.setTemperature(550.0, "C");
    exhaustStream.setPressure(1.01325, "bara");
  }

  @Test
  void testHRSGTransfersHeat() {
    ProcessSystem process = new ProcessSystem();
    process.add(exhaustStream);

    HRSG hrsg = new HRSG("HRSG-1", exhaustStream);
    hrsg.setSteamPressure(40.0);
    hrsg.setSteamTemperature(400.0, "C");
    hrsg.setApproachTemperature(15.0);
    hrsg.setEffectiveness(0.85);
    process.add(hrsg);
    process.run();

    double heatTransferred = hrsg.getHeatTransferred();
    assertTrue(heatTransferred > 0, "HRSG should transfer heat, got: " + heatTransferred);
  }

  @Test
  void testHRSGSteamProduction() {
    ProcessSystem process = new ProcessSystem();
    process.add(exhaustStream);

    HRSG hrsg = new HRSG("HRSG-1", exhaustStream);
    hrsg.setSteamPressure(40.0);
    hrsg.setSteamTemperature(400.0, "C");
    hrsg.setApproachTemperature(15.0);
    hrsg.setEffectiveness(0.85);
    process.add(hrsg);
    process.run();

    double steamFlowKg = hrsg.getSteamFlowRate("kg/hr");
    assertTrue(steamFlowKg > 0, "Steam flow should be positive, got: " + steamFlowKg);
  }

  @Test
  void testHRSGCoolsGas() {
    ProcessSystem process = new ProcessSystem();
    process.add(exhaustStream);

    HRSG hrsg = new HRSG("HRSG-1", exhaustStream);
    hrsg.setSteamPressure(40.0);
    hrsg.setSteamTemperature(400.0, "C");
    hrsg.setApproachTemperature(15.0);
    hrsg.setEffectiveness(0.85);
    process.add(hrsg);
    process.run();

    double gasOutTemp = hrsg.getGasOutletTemperature();
    double gasInTemp = exhaustStream.getTemperature();
    assertTrue(gasOutTemp < gasInTemp, "Gas outlet temperature should be lower than inlet");
  }

  @Test
  void testHeatUnits() {
    ProcessSystem process = new ProcessSystem();
    process.add(exhaustStream);

    HRSG hrsg = new HRSG("HRSG-1", exhaustStream);
    hrsg.setSteamPressure(40.0);
    hrsg.setSteamTemperature(400.0, "C");
    hrsg.setEffectiveness(0.85);
    process.add(hrsg);
    process.run();

    double heatW = hrsg.getHeatTransferred("W");
    double heatkW = hrsg.getHeatTransferred("kW");
    double heatMW = hrsg.getHeatTransferred("MW");

    assertTrue(Math.abs(heatW / 1000.0 - heatkW) < 0.001);
    assertTrue(Math.abs(heatW / 1.0e6 - heatMW) < 0.000001);
  }

  @Test
  void testFeedWaterTemperatureSetting() {
    HRSG hrsg = new HRSG("HRSG");
    hrsg.setFeedWaterTemperature(60.0, "C");
    // Should not throw
    hrsg.setFeedWaterTemperature(333.15);
    // Should not throw
  }
}
