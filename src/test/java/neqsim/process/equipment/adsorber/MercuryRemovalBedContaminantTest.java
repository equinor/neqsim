package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.CapillaryCondensationModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for condensable-contaminant pore blocking in a mercury guard bed.
 *
 * <p>
 * Conditions are those of the Kaarstoe H2S/Hg removal filter: rich gas at about 115 bara and just above zero degrees
 * Celsius, over a mesoporous alumina-supported metal-sulphide sorbent.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class MercuryRemovalBedContaminantTest {

  /** Bed pressure (bara). */
  private static final double BED_PRESSURE = 115.0;

  /** Bed temperature (degC). */
  private static final double BED_TEMPERATURE = 1.0;

  /**
   * Build a rich gas with trace mercury and methanol at guard bed conditions.
   *
   * @param methanolMoleFraction the methanol mole fraction
   * @return an initialised thermodynamic system
   */
  private SystemInterface feedGas(double methanolMoleFraction) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + BED_TEMPERATURE, BED_PRESSURE);
    double scale = 1.0 - methanolMoleFraction;
    gas.addComponent("methane", 0.8180 * scale);
    gas.addComponent("ethane", 0.0910 * scale);
    gas.addComponent("propane", 0.0430 * scale);
    gas.addComponent("n-butane", 0.0135 * scale);
    gas.addComponent("nitrogen", 0.0060 * scale);
    gas.addComponent("CO2", 0.0120 * scale);
    gas.addComponent("methanol", methanolMoleFraction);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    return gas;
  }

  /**
   * Flash the gas against excess liquid methanol to get the bulk saturation mole fraction.
   *
   * @return methanol saturation mole fraction in the gas
   */
  private double methanolSaturation() {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + BED_TEMPERATURE, BED_PRESSURE);
    gas.addComponent("methane", 0.8180);
    gas.addComponent("ethane", 0.0910);
    gas.addComponent("propane", 0.0430);
    gas.addComponent("n-butane", 0.0135);
    gas.addComponent("nitrogen", 0.0060);
    gas.addComponent("CO2", 0.0120);
    gas.addComponent("methanol", 5.0);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    return gas.getPhase("gas").getComponent("methanol").getx();
  }

  /**
   * Build and run a bed on the given feed.
   *
   * @param methanolMoleFraction the methanol mole fraction in the feed
   * @return a bed that has been run
   */
  private MercuryRemovalBed runBed(double methanolMoleFraction) {
    Stream feed = new Stream("feed", feedGas(methanolMoleFraction));
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();
    MercuryRemovalBed bed = new MercuryRemovalBed("Hg guard bed", feed);
    bed.setBedDiameter(2.0);
    bed.setBedLength(4.0);
    bed.run();
    return bed;
  }

  /**
   * A mesoporous sorbent must be screened with the Kelvin mechanism and tolerate a limit below bulk saturation.
   */
  @Test
  public void testMesoporousSorbentUsesKelvin() {
    double ySat = methanolSaturation();
    MercuryRemovalBed bed = runBed(200.0e-6);
    bed.setSorbentPoreRadius(6.0);

    MercuryRemovalBed.ContaminantAssessment a = bed.assessContaminant("methanol", ySat);
    assertEquals("kelvin", a.mechanism);
    assertTrue(a.kelvinOnset > 0.5 && a.kelvinOnset < 1.0,
        "Kelvin onset for a 6 nm pore should be well below one but not tiny, was " + a.kelvinOnset);
    assertTrue(a.maxAllowableMoleFraction < ySat, "The limit must stay below bulk saturation");
    assertEquals(a.kelvinOnset * ySat, a.maxAllowableMoleFraction, 1.0e-9);
  }

  /**
   * A microporous sorbent must switch to volume filling, which is far more restrictive than Kelvin.
   */
  @Test
  public void testMicroporousSorbentSwitchesToVolumeFilling() {
    double ySat = methanolSaturation();
    MercuryRemovalBed bed = runBed(200.0e-6);
    bed.setSorbentPoreRadius(1.0);
    bed.setDubininCharacteristicEnergy(8000.0);

    MercuryRemovalBed.ContaminantAssessment a = bed.assessContaminant("methanol", ySat);
    assertEquals("micropore-filling", a.mechanism);
    assertTrue(a.poreFillingFraction > 0.0 && a.poreFillingFraction <= 1.0,
        "Micropore filling fraction must be a physical fraction, was " + a.poreFillingFraction);
  }

  /** The reported micropore concentration limit must correspond to the screening criterion. */
  @Test
  public void testMicroporeLimitMatchesFillingCriterion() {
    double ySat = methanolSaturation();
    MercuryRemovalBed bed = runBed(200.0e-6);
    bed.setSorbentPoreRadius(1.0);
    MercuryRemovalBed.ContaminantAssessment assessment = bed.assessContaminant("methanol", ySat);
    double fillingAtLimit = CapillaryCondensationModel.microporeFillingFraction(
        assessment.maxAllowableMoleFraction / ySat, bed.getInletStream().getThermoSystem().getTemperature(),
        bed.getDubininCharacteristicEnergy(), bed.getDubininAffinityCoefficient());
    assertEquals(0.05, fillingAtLimit, 1.0e-12,
        "The micropore limit must use Dubinin filling rather than the Kelvin onset");
    bed.setDubininCharacteristicEnergy(10000.0);
    assertTrue(bed.assessContaminant("methanol", ySat).maxAllowableMoleFraction < assessment.maxAllowableMoleFraction,
        "Stronger adsorption must lower the allowable concentration");
  }

  /**
   * More methanol must mean more relative saturation and never less pore blocking.
   */
  @Test
  public void testPoreBlockingIncreasesWithMethanol() {
    double ySat = methanolSaturation();
    MercuryRemovalBed low = runBed(50.0e-6);
    low.setSorbentPoreRadius(1.0);
    MercuryRemovalBed high = runBed(1000.0e-6);
    high.setSorbentPoreRadius(1.0);

    MercuryRemovalBed.ContaminantAssessment aLow = low.assessContaminant("methanol", ySat);
    MercuryRemovalBed.ContaminantAssessment aHigh = high.assessContaminant("methanol", ySat);

    assertTrue(aHigh.relativeSaturation > aLow.relativeSaturation);
    assertTrue(aHigh.poreFillingFraction >= aLow.poreFillingFraction,
        "Pore blocking must not fall when methanol rises");
  }

  /**
   * Applying the contaminant degradation must reduce the degradation factor rather than leave it at one.
   */
  @Test
  public void testApplyContaminantDegradationReducesCapacity() {
    double ySat = methanolSaturation();
    MercuryRemovalBed bed = runBed(1500.0e-6);
    bed.setSorbentPoreRadius(1.0);
    bed.setDubininCharacteristicEnergy(8000.0);
    assertEquals(1.0, bed.getDegradationFactor(), 1.0e-12);

    MercuryRemovalBed.ContaminantAssessment a = bed.applyContaminantDegradation("methanol", ySat);
    assertEquals(1.0 - a.poreFillingFraction, bed.getDegradationFactor(), 1.0e-12);
    assertTrue(bed.getDegradationFactor() < 1.0, "A wet feed must cost the bed some capacity");
  }

  /**
   * The JSON report must carry the numbers an engineer needs.
   */
  @Test
  public void testAssessmentJson() {
    double ySat = methanolSaturation();
    MercuryRemovalBed bed = runBed(200.0e-6);
    String json = bed.getContaminantAssessmentJson("methanol", ySat);
    for (String key : new String[] { "relativeSaturation", "kelvinOnset", "maxAllowablePpmv", "poreFillingFraction",
        "mechanism", "condensationExpected", "sorbentPoreRadiusNm" }) {
      assertTrue(json.contains(key), "JSON should report " + key);
    }
  }

  /**
   * Misuse must be rejected clearly rather than silently producing a number.
   */
  @Test
  public void testInvalidUsageRejected() {
    MercuryRemovalBed notRun = new MercuryRemovalBed("not run");
    assertThrows(IllegalStateException.class, () -> notRun.assessContaminant("methanol", 1.0e-3));

    MercuryRemovalBed bed = runBed(200.0e-6);
    assertThrows(IllegalArgumentException.class, () -> bed.assessContaminant("methanol", 0.0));
    assertThrows(IllegalArgumentException.class, () -> bed.assessContaminant("ethanol", 1.0e-3));
    assertThrows(IllegalArgumentException.class, () -> bed.setSorbentPoreRadius(0.0));
  }

  /**
   * The Dubinin-Radushkevich helper must be bounded, monotonic, and start filling far below the Kelvin onset.
   */
  @Test
  public void testMicroporeFillingHelper() {
    double temperature = 273.15 + BED_TEMPERATURE;
    assertEquals(0.0, CapillaryCondensationModel.microporeFillingFraction(0.0, temperature, 8000.0, 1.0), 1.0e-12);
    assertEquals(1.0, CapillaryCondensationModel.microporeFillingFraction(1.0, temperature, 8000.0, 1.0), 1.0e-12);

    double low = CapillaryCondensationModel.microporeFillingFraction(0.01, temperature, 8000.0, 1.0);
    double high = CapillaryCondensationModel.microporeFillingFraction(0.30, temperature, 8000.0, 1.0);
    assertTrue(low < high, "Filling must increase with activity");
    assertTrue(high > 0.5, "At 30 percent of saturation a micropore is already largely filled");

    assertThrows(IllegalArgumentException.class,
        () -> CapillaryCondensationModel.microporeFillingFraction(0.5, -1.0, 8000.0, 1.0));
  }
}
