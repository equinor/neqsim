package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.DepositMechanism;
import neqsim.process.equipment.compressor.SolidFlashDepositSource;
import neqsim.process.equipment.reactor.IronSulfideWallInventory.ExposureEvent;
import neqsim.process.equipment.reactor.IronSulfideWallInventory.ExposureType;
import neqsim.process.equipment.reactor.IronSulfideWallInventory.FeSPhase;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests wall FeS formation, oxidation, uncertainty, and downstream S8 deposition. */
public class IronSulfideOxidationSourceTest extends neqsim.NeqSimTest {

  @Test
  public void testInventoryFromMeasuredThicknessAndHistory() {
    IronSulfideWallInventory inventory = new IronSulfideWallInventory();
    inventory.setPipeSurfaceAreaM2(100.0);
    inventory.setPorosityFraction(0.25);
    inventory.setFeSPhase(FeSPhase.MACKINAWITE);
    inventory.setFeSMassFromThickness(1.0e-3);

    assertEquals(307.5, inventory.getFeSMassKg(), 1.0e-12);
    assertEquals(1.0e-3, inventory.getFeSEquivalentThicknessM(), 1.0e-15);
    assertEquals(307.5 * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL
        / IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL, inventory.getMaximumElementalSulfurMassKg(), 1.0e-12);

    inventory.recordExposure(
        new ExposureEvent(ExposureType.SEAWATER_EXPOSURE, 48.0, 20.0, 1.0, "Hydrotest water remained in the line"));
    assertEquals(1, inventory.getExposureHistory().size());
    assertTrue(inventory.toJson().contains("SEAWATER_EXPOSURE"));

    inventory.setMassesFromMeasuredScaleThickness(2.0e-3, 4000.0, 0.50, 0.30, 0.20);
    double expectedMixedScaleMass = 2.0e-3 * 100.0 * 0.75 * 4000.0;
    assertEquals(expectedMixedScaleMass * 0.50, inventory.getFeSMassKg(), 1.0e-12);
    assertEquals(expectedMixedScaleMass * 0.30, inventory.getFeCO3MassKg(), 1.0e-12);
    assertEquals(expectedMixedScaleMass * 0.20, inventory.getIronOxideEquivalentMassKg(), 1.0e-12);
  }

  @Test
  public void testSteadyStateOxidationIsOxygenLimitedAndDoesNotMutateInventory() {
    Stream feed = createFeed(100.0, 0.0, 1.0, 1000.0);
    double inletOxygenKgPerHour = feed.getThermoSystem().getComponent("oxygen").getFlowRate("kg/hr");
    IronSulfideWallInventory inventory = new IronSulfideWallInventory(1000.0, 0.0, 0.0);
    inventory.setFeSPhase(FeSPhase.MACKINAWITE);
    IronSulfideOxidationSource source = new IronSulfideOxidationSource("wall FeS", feed, inventory);
    source.setFeSOxidationFractionPerHour(1.0);
    source.setElementalSulfurYieldFraction(0.60);
    source.setElementalSulfurYieldBounds(0.20, 1.0);
    source.setSolidFlashEnabled(false);
    source.run();

    double expectedFeSMolesPerHour = inletOxygenKgPerHour / 0.031998 / 0.75;
    double expectedSulfurKgPerHour = expectedFeSMolesPerHour * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL
        * 0.60;
    assertEquals(inletOxygenKgPerHour, source.getOxygenConsumedKgPerHour(), 1.0e-8);
    assertEquals(expectedSulfurKgPerHour, source.getElementalSulfurRateKgPerHour(), 1.0e-8);
    assertEquals("oxygen transfer", source.getOxidationLimitingFactor());
    assertEquals(1000.0, inventory.getFeSMassKg(), 1.0e-12, "steady-state evaluation must not consume history");
    assertTrue(source.getElementalSulfurLowRateKgPerHour() < source.getElementalSulfurRateKgPerHour());
    assertTrue(source.getElementalSulfurHighRateKgPerHour() > source.getElementalSulfurRateKgPerHour());
  }

  @Test
  public void testTransientOxidationConservesWallIronAndDepletesFeS() {
    Stream feed = createFeed(1.0, 0.0, 10.0, 1000.0);
    double initialFeSMass = 8.791;
    IronSulfideWallInventory inventory = new IronSulfideWallInventory(initialFeSMass, 0.0, 0.0);
    inventory.setFeSPhase(FeSPhase.MACKINAWITE);
    IronSulfideOxidationSource source = new IronSulfideOxidationSource("transient wall FeS", feed, inventory);
    source.setFeSOxidationFractionPerHour(0.10);
    source.setElementalSulfurYieldFraction(1.0);
    source.setSolidFlashEnabled(false);
    source.runTransient(3600.0, UUID.randomUUID());

    double expectedFeSOxidized = initialFeSMass * 0.10;
    double expectedOxide = expectedFeSOxidized / IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL * 0.5
        * IronSulfideWallInventory.FE2O3_MOLAR_MASS_KG_PER_MOL;
    assertEquals(expectedFeSOxidized, source.getFeSOxidizedKgPerHour(), 1.0e-10);
    assertEquals(initialFeSMass - expectedFeSOxidized, inventory.getFeSMassKg(), 1.0e-10);
    assertEquals(expectedOxide, inventory.getIronOxideEquivalentMassKg(), 1.0e-10);
    assertTrue(source.getReactionHeatKW() > 0.0);
  }

  @Test
  public void testWetFeCO3SulfidationRouteAndDryWallGate() {
    Stream wetFeed = createFeed(10.0, 10.0, 0.0, 1000.0);
    IronSulfideWallInventory wetInventory = new IronSulfideWallInventory(0.0,
        IronSulfideWallInventory.FECO3_MOLAR_MASS_KG_PER_MOL, 0.0);
    wetInventory.setWettedFraction(1.0);
    IronSulfideOxidationSource wetSource = new IronSulfideOxidationSource("wet siderite", wetFeed, wetInventory);
    wetSource.setFeCO3SulfidationFractionPerHour(1.0);
    wetSource.setSolidFlashEnabled(false);
    wetSource.runTransient(3600.0, UUID.randomUUID());

    assertEquals(0.0, wetInventory.getFeCO3MassKg(), 1.0e-12);
    assertEquals(IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL, wetInventory.getFeSMassKg(), 1.0e-12);
    assertEquals(0.034081, wetSource.getH2SConsumedKgPerHour(), 1.0e-12);

    Stream dryFeed = createFeed(10.0, 10.0, 0.0, 1000.0);
    IronSulfideWallInventory dryInventory = new IronSulfideWallInventory(0.0,
        IronSulfideWallInventory.FECO3_MOLAR_MASS_KG_PER_MOL, 0.0);
    dryInventory.setWettedFraction(0.0);
    IronSulfideOxidationSource drySource = new IronSulfideOxidationSource("dry siderite", dryFeed, dryInventory);
    drySource.setFeCO3SulfidationFractionPerHour(1.0);
    drySource.setSolidFlashEnabled(false);
    drySource.runTransient(3600.0, UUID.randomUUID());
    assertEquals(0.0, dryInventory.getFeSMassKg(), 1.0e-12);
    assertEquals(IronSulfideWallInventory.FECO3_MOLAR_MASS_KG_PER_MOL, dryInventory.getFeCO3MassKg(), 1.0e-12);
  }

  @Test
  public void testExposureHistoryAndDownstreamSolidFlashDepositSource() {
    Stream feed = createFeed(100.0, 0.0, 1.0, 1000.0);
    IronSulfideWallInventory inventory = new IronSulfideWallInventory(1000.0, 0.0, 0.0);
    inventory.setFeSPhase(FeSPhase.MACKINAWITE);
    IronSulfideOxidationSource source = new IronSulfideOxidationSource("restart wall source", feed, inventory);
    source.setFeSOxidationFractionPerHour(1.0);
    source.setElementalSulfurYieldFraction(1.0);
    source.runExposure(
        new ExposureEvent(ExposureType.NITROGEN_PURGE, 1.0, 30.0, 0.0, "Nitrogen contained residual oxygen"),
        UUID.randomUUID());

    assertEquals(1, inventory.getExposureHistory().size());
    assertTrue(source.getElementalSulfurRateKgPerHour() > 0.0);
    assertFalse(source.toJson().isEmpty());

    SolidFlashDepositSource depositSource = new SolidFlashDepositSource(source.getOutletStream(), "S8",
        DepositMechanism.SULFUR_S8, 0.5);
    assertTrue(depositSource.getPrecipitationRate("kg/hr") > 0.0,
        "wall-source S8 should enter the existing thermodynamic deposition route");
  }

  private Stream createFeed(double methaneMoles, double h2sMoles, double oxygenMoles, double flowKgPerHour) {
    SystemInterface gas = new SystemSrkEos(303.15, 40.0);
    gas.addComponent("methane", methaneMoles);
    gas.addComponent("H2S", Math.max(h2sMoles, 1.0e-12));
    gas.addComponent("oxygen", Math.max(oxygenMoles, 1.0e-12));
    gas.addComponent("S8", 1.0e-12);
    gas.addComponent("hydrogen", 1.0e-12);
    gas.addComponent("CO2", 1.0e-12);
    gas.addComponent("water", 1.0e-12);
    gas.setMixingRule("classic");
    gas.setMultiPhaseCheck(true);
    Stream feed = new Stream("wall contact feed", gas);
    feed.setFlowRate(flowKgPerHour, "kg/hr");
    feed.run();
    return feed;
  }
}
