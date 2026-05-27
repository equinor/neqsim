package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.CatalystDeactivationKinetics.CatalystFamily;

/**
 * Tests for hydrogen-production catalyst deactivation kinetics.
 */
public class CatalystDeactivationKineticsTest extends neqsim.NeqSimTest {

  @Test
  public void testFreshCleanCatalystRetainsActivity() {
    CatalystDeactivationKinetics kinetics =
        new CatalystDeactivationKinetics().setTemperature(800.0).setOperationHours(1000.0);

    assertEquals(1.0, kinetics.calculateActivity(), 1.0e-12);
    assertEquals("none", kinetics.getDominantMechanism());
  }

  @Test
  public void testCopperZincShiftIsMoreSulfurSensitiveThanIronChromium() {
    CatalystDeactivationKinetics copper =
        new CatalystDeactivationKinetics(CatalystFamily.COPPER_ZINC_LT_SHIFT).setTemperature(500.0)
            .setSulfurPpmv(0.1).setOperationHours(1000.0);
    CatalystDeactivationKinetics iron =
        new CatalystDeactivationKinetics(CatalystFamily.IRON_CHROMIUM_HT_SHIFT)
            .setTemperature(650.0).setSulfurPpmv(0.1).setOperationHours(1000.0);

    assertTrue(copper.getSulfurPoisoningRatePerHour() > iron.getSulfurPoisoningRatePerHour());
    assertTrue(copper.calculateActivity() < iron.calculateActivity());
  }

  @Test
  public void testLowSteamPromotesNickelCoking() {
    CatalystDeactivationKinetics lowSteam = new CatalystDeactivationKinetics()
        .setCarbonPotential(1.0).setSteamToCarbonRatio(1.2).setOperationHours(2000.0);
    CatalystDeactivationKinetics highSteam = new CatalystDeactivationKinetics()
        .setCarbonPotential(1.0).setSteamToCarbonRatio(4.0).setOperationHours(2000.0);

    assertTrue(lowSteam.getCokingRatePerHour() > highSteam.getCokingRatePerHour());
    assertTrue(lowSteam.calculateActivity() < highSteam.calculateActivity());
  }

  @Test
  public void testThermalSinteringIncreasesAboveOnsetTemperature() {
    CatalystDeactivationKinetics moderate =
        new CatalystDeactivationKinetics().setTemperature(900.0).setOperationHours(1000.0);
    CatalystDeactivationKinetics hot =
        new CatalystDeactivationKinetics().setTemperature(1173.15).setOperationHours(1000.0);

    assertEquals(0.0, moderate.getThermalSinteringRatePerHour(), 1.0e-15);
    assertTrue(hot.getThermalSinteringRatePerHour() > 0.0);
    assertTrue(hot.calculateActivity() < moderate.calculateActivity());
  }

  @Test
  public void testDominantMechanismAndTimeToActivity() {
    CatalystDeactivationKinetics kinetics =
        new CatalystDeactivationKinetics(CatalystFamily.RUTHENIUM_AMMONIA_CRACKING)
            .setTemperature(850.0).setSulfurPpmv(0.5).setOperationHours(1000.0);

    assertEquals("sulfur_poisoning", kinetics.getDominantMechanism());
    assertTrue(kinetics.estimateTimeToActivity(0.8) > 0.0);
    assertTrue(kinetics.estimateTimeToActivity(0.8) < Double.POSITIVE_INFINITY);
  }

  @Test
  public void testApplyToCatalystBed() {
    CatalystBed bed = new CatalystBed();
    CatalystDeactivationKinetics kinetics = new CatalystDeactivationKinetics().setSulfurPpmv(0.2)
        .setCarbonPotential(0.5).setOperationHours(1000.0);

    double activity = kinetics.applyTo(bed);

    assertEquals(activity, bed.getActivityFactor(), 1.0e-12);
    assertTrue(bed.getActivityFactor() < 1.0);
  }

  @Test
  public void testJsonContainsActivityAndMechanism() {
    CatalystDeactivationKinetics kinetics =
        new CatalystDeactivationKinetics().setSulfurPpmv(0.1).setOperationHours(100.0);

    String json = kinetics.toJson();

    assertTrue(json.contains("activity"));
    assertTrue(json.contains("dominantMechanism"));
  }
}
