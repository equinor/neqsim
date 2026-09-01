package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests terminal tanks, parcel identity, blending, quality, and scheduling.
 */
class OilNetworkScheduleTest {
  @Test
  void testSyntheticTerminalScheduleConservesMassAndComponents() {
    CrudeAssay oseberg = createAssay("Oseberg-like", "NCS common C10-C16", 0.70, 0.30, 0.30, 0.10);
    CrudeAssay grane = createAssay("Grane-like", "NCS common C10-C16", 0.30, 0.70, 0.90, 0.20);
    CrudeAssay troll = createAssay("Troll-like", "NCS common C10-C16", 0.55, 0.45, 0.45, 0.15);

    OilTerminalNode terminal = new OilTerminalNode("Synthetic Sture");
    OilTerminalTank mixed = new OilTerminalTank("Cavern A", 500000.0, 20000.0, 100.0, 100.0,
        OilTerminalTank.MixingMode.PERFECT_MIXED);
    mixed.addOpeningInventory(new CrudeParcel("opening-A", 150000.0, oseberg, -1, "opening", "Synthetic"));
    OilTerminalTank segregated = new OilTerminalTank("Cavern B", 400000.0, 10000.0, 100.0, 100.0,
        OilTerminalTank.MixingMode.SEGREGATED);
    segregated.addOpeningInventory(new CrudeParcel("opening-B", 120000.0, grane, -1, "opening", "Synthetic"));
    OilTerminalTank spare = new OilTerminalTank("Tank C", 300000.0, 5000.0, 100.0, 100.0,
        OilTerminalTank.MixingMode.SEGREGATED);
    spare.addOpeningInventory(new CrudeParcel("opening-C", 50000.0, troll, -1, "opening", "Synthetic"));
    terminal.addTank(mixed);
    terminal.addTank(segregated);
    terminal.addTank(spare);

    NetworkQualityProfile syntheticCargoProfile = new NetworkQualityProfile("Synthetic educational cargo limits");
    syntheticCargoProfile.withProvenance("Synthetic limits; not operator acceptance criteria");
    syntheticCargoProfile.addMeasuredAttributeLimit("oil", "sulfurMassPercent", null, 2.0, "mass%", "Synthetic assay");
    syntheticCargoProfile.addMeasuredAttributeLimit("oil", "waterBswVolumePercent", null, 0.5, "vol%",
        "Synthetic assay");

    OilNetworkSchedule schedule = new OilNetworkSchedule(terminal);
    schedule.addHourlyPeriods("2026-01-01T00:00:00Z", 3);
    schedule.addReceipt("Cavern A", new CrudeParcel("Oseberg receipt", 40000.0, oseberg, 0, "Oseberg Transport System",
        "Synthetic public context"));
    schedule.addReceipt("Cavern A",
        new CrudeParcel("Troll receipt", 30000.0, troll, 1, "Troll pipeline", "Synthetic public context"));
    schedule.addReceipt("Cavern B",
        new CrudeParcel("Grane receipt", 25000.0, grane, 0, "Grane Oil Pipeline", "Synthetic public context"));
    schedule.addCargoNomination(new CargoNomination("cargo-1", 50000.0, 0, 1, "berth-1", 50.0, syntheticCargoProfile,
        Arrays.asList("Cavern A")));
    schedule.addCargoNomination(new CargoNomination("cargo-2", 20000.0, 1, 2, "berth-2", 50.0, syntheticCargoProfile,
        Arrays.asList("Cavern B")));
    schedule.setTankAvailability("Tank C", 0, 2, false);

    OilNetworkScheduleResult result = schedule.optimize();
    OilNetworkScheduleResult repeated = schedule.optimize();

    assertTrue(result.isFeasible(), result.getMessage() + ": " + result.getActiveConstraints());
    assertEquals(2, result.getCargoes().size());
    assertEquals(0.0, result.getMassBalanceResidualKg(), 1.0e-6);
    assertEquals(0.0, result.getMaxComponentBalanceResidualKg(), 1.0e-6);
    assertTrue(result.getCargoes().get("cargo-1").getQualityReport().isCompliant());
    assertEquals(result.getTerminalInventories().get("Cavern A").getMassKg(),
        repeated.getTerminalInventories().get("Cavern A").getMassKg(), 1.0e-9);
    assertEquals(result.getCargoes().keySet(), repeated.getCargoes().keySet());

    OilNetworkScheduleResult restored = OilNetworkScheduleResult.fromJson(result.toJson());
    assertEquals(3, restored.getPeriods().size());
    assertEquals(2, restored.getCargoes().size());
  }

  @Test
  void testIncompatiblePseudoComponentSlatesAreRejected() {
    CrudeAssay first = createAssay("first", "slate-A", 0.6, 0.4, 0.2, 0.1);
    CrudeAssay second = createAssay("second", "slate-B", 0.4, 0.6, 0.8, 0.2);
    assertThrows(IllegalArgumentException.class,
        () -> CrudeAssay.blend("invalid", Arrays.asList(new CrudeParcel("p1", 1000.0, first, 0, "a", "x"),
            new CrudeParcel("p2", 1000.0, second, 0, "b", "y"))));
  }

  @Test
  void testSegregatedTankDoesNotCrossParcelBoundary() {
    CrudeAssay first = createAssay("first", "slate", 0.6, 0.4, 0.2, 0.1);
    CrudeAssay second = createAssay("second", "slate", 0.4, 0.6, 0.8, 0.2);
    OilTerminalTank tank = new OilTerminalTank("segregated", 10000.0, 0.0, 100.0, 100.0,
        OilTerminalTank.MixingMode.SEGREGATED);
    tank.addOpeningInventory(new CrudeParcel("p1", 1000.0, first, 0, "a", "x"));
    tank.addOpeningInventory(new CrudeParcel("p2", 1000.0, second, 0, "b", "y"));
    tank.beginPeriod(0);

    assertThrows(IllegalStateException.class, () -> tank.withdraw("cargo", 1500.0, 0, "berth", 3600.0));
    assertEquals(2000.0, tank.getMassKg(), 1.0e-12);
  }

  @Test
  void testVolumeWeightedAssayAttributeUsesCalculatedParcelVolumes() {
    CrudeAssay light = createAssay("light", "slate", 0.9, 0.1, 0.2, 0.1);
    CrudeAssay heavy = createAssay("heavy", "slate", 0.1, 0.9, 0.8, 0.3);
    light.addMeasuredAttribute("marker", 10.0, "index", "Synthetic assay", "volume-weighted");
    heavy.addMeasuredAttribute("marker", 20.0, "index", "Synthetic assay", "volume-weighted");

    CrudeBlendResult result = CrudeAssay.blend("volume blend", Arrays.asList(
        new CrudeParcel("light", 1000.0, light, 0, "a", "x"), new CrudeParcel("heavy", 1000.0, heavy, 0, "b", "y")));

    double lightVolume = 1000.0 / light.getFluid().getDensity("kg/m3");
    double heavyVolume = 1000.0 / heavy.getFluid().getDensity("kg/m3");
    double expected = (10.0 * lightVolume + 20.0 * heavyVolume) / (lightVolume + heavyVolume);
    assertEquals(expected, result.getAssay().getAttributes().get("marker").getValue(), 1.0e-12);
  }

  private CrudeAssay createAssay(String name, String slate, double nC10, double nC16, double sulfur, double water) {
    SystemInterface fluid = new SystemSrkEos(288.15, 5.0);
    fluid.addComponent("nC10", nC10);
    fluid.addComponent("nC16", nC16);
    fluid.setMixingRule("classic");
    CrudeAssay assay = new CrudeAssay(name, slate, fluid, "Synthetic public representative data", "2026-01-01");
    assay.addMeasuredAttribute("sulfurMassPercent", sulfur, "mass%", "Synthetic assay", "mass-weighted");
    assay.addMeasuredAttribute("waterBswVolumePercent", water, "vol%", "Synthetic assay", "mass-weighted");
    return assay;
  }
}
