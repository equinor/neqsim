package neqsim.process.util.utilitydesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the standalone utility-design sizing classes: {@link Boiler}, {@link Deaerator},
 * {@link RefrigerationCycle}, {@link NitrogenSystem}, and {@link SteamNetwork}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class UtilityComponentsTest {

  @Test
  void testBoilerSizing() {
    Boiler boiler = new Boiler("HP Boiler");
    boiler.addSteamDuty("Reboiler", 5000.0);
    boiler.addSteamDuty("Tracing", 1000.0);
    boiler.setBoilerEfficiency(0.85);
    boiler.calculate();

    assertEquals(6000.0, boiler.getTotalSteamDutyKW(), 1e-6);
    assertTrue(boiler.getFuelThermalKW() > boiler.getTotalSteamDutyKW(),
        "fuel thermal input must exceed delivered duty");
    assertTrue(boiler.getFuelMassDemandKgh() > 0.0, "fuel mass demand must be positive");
    assertTrue(boiler.getSteamGenerationKgh() > 0.0, "steam generation must be positive");
    assertTrue(boiler.getFeedwaterFlowKgh() > boiler.getSteamGenerationKgh(),
        "feedwater must exceed steam due to blowdown");
    assertTrue(boiler.getBlowdownFlowKgh() > 0.0, "blowdown must be positive");
    assertTrue(boiler.getFanPowerKW() > 0.0, "fan power must be positive");

    String json = boiler.toJson();
    assertTrue(json.contains("schemaVersion"), "JSON must contain schemaVersion");
  }

  @Test
  void testBoilerRejectsNegativeDuty() {
    Boiler boiler = new Boiler();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        boiler.addSteamDuty("bad", -10.0);
      }
    });
  }

  @Test
  void testDeaeratorSizing() {
    Deaerator deaerator = new Deaerator("Deaerator");
    deaerator.setFeedwaterFlowKgh(10000.0);
    deaerator.setFeedwaterInletTempC(30.0);
    deaerator.setOperatingPressureBara(1.2);
    deaerator.calculate();

    assertTrue(deaerator.getDeaeratorTempC() > 30.0, "deaerator temperature must exceed cold inlet");
    assertTrue(deaerator.getHeatDutyKW() > 0.0, "heat duty must be positive");
    assertTrue(deaerator.getStrippingSteamKgh() > 0.0, "stripping steam must be positive");
    assertTrue(deaerator.getVentRateKgh() > 0.0, "vent rate must be positive");
  }

  @Test
  void testRefrigerationCycleSizing() {
    RefrigerationCycle cycle = new RefrigerationCycle("Propane Refrigeration");
    cycle.addRefrigerationDuty("Gas Chiller", 4000.0);
    cycle.setEvaporatorTempC(-35.0);
    cycle.setCondenserTempC(35.0);
    cycle.setCycleEfficiency(0.55);
    cycle.calculate();

    assertEquals(4000.0, cycle.getTotalRefrigerationDutyKW(), 1e-6);
    assertTrue(cycle.getCarnotCop() > cycle.getCop(), "actual COP must be below the Carnot reference");
    assertTrue(cycle.getCop() > 0.0, "COP must be positive");
    assertTrue(cycle.getCompressorPowerKW() > 0.0, "compressor power must be positive");
    assertTrue(cycle.getCondenserDutyKW() > cycle.getTotalRefrigerationDutyKW(),
        "condenser duty must exceed refrigeration duty by the compressor work");

    String json = cycle.toJson();
    assertTrue(json.contains("schemaVersion"), "JSON must contain schemaVersion");
  }

  @Test
  void testRefrigerationRejectsNegativeDuty() {
    RefrigerationCycle cycle = new RefrigerationCycle();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        cycle.addRefrigerationDuty("bad", -1.0);
      }
    });
  }

  @Test
  void testNitrogenSystemSizing() {
    NitrogenSystem n2 = new NitrogenSystem("Nitrogen Package");
    n2.setNitrogenDemandNm3h(500.0);
    n2.setPurityPercent(99.5);
    n2.setGenerationMethod(NitrogenSystem.GenerationMethod.MEMBRANE);
    n2.calculate();

    assertTrue(n2.getSpecificEnergyKWhPerNm3() > 0.0, "specific energy must be positive");
    assertTrue(n2.getPowerKW() > 0.0, "power must be positive");
    assertTrue(n2.getAirFeedNm3h() > 500.0, "air feed must exceed nitrogen product");

    String json = n2.toJson();
    assertTrue(json.contains("schemaVersion"), "JSON must contain schemaVersion");
  }

  @Test
  void testSteamNetworkCascade() {
    SteamNetwork net = new SteamNetwork("Steam System");
    net.addLevel("HP", 41.0, 252.0);
    net.addLevel("MP", 11.0, 184.0);
    net.addLevel("LP", 4.5, 148.0);
    net.addDemand("MP", 8000.0);
    net.addDemand("LP", 5000.0);
    net.setLocalGeneration("LP", 2000.0);
    net.calculate();

    assertEquals(3, net.getHeaderCount());
    assertEquals(13000.0, net.getTotalDemandKgh(), 1e-6);
    assertEquals(2000.0, net.getTotalLocalGenerationKgh(), 1e-6);
    // Boiler make-up = total demand minus the useful local generation = 13000 - 2000.
    assertEquals(11000.0, net.getBoilerGenerationKgh(), 1e-6);
    assertTrue(net.getBoiler().getFuelMassDemandKgh() > 0.0, "embedded boiler must size a positive fuel demand");
    assertTrue(net.getDeaerator().getHeatDutyKW() > 0.0, "embedded deaerator must size a positive heat duty");

    String json = net.toJson();
    assertTrue(json.contains("schemaVersion"), "JSON must contain schemaVersion");
    assertTrue(json.contains("boiler"), "JSON must embed boiler results");
    assertTrue(json.contains("deaerator"), "JSON must embed deaerator results");
  }

  @Test
  void testSteamNetworkRejectsUnknownHeader() {
    SteamNetwork net = new SteamNetwork();
    net.addLevel("HP", 41.0, 252.0);
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        net.addDemand("XP", 100.0);
      }
    });
  }
}
