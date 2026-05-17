package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Experimental data validation tests for adsorption models.
 *
 * <p>
 * Tests compare model predictions against published experimental adsorption data for:
 * </p>
 * <ul>
 * <li>CH4 on activated carbon at 298K (Dreisbach et al. 1999)</li>
 * <li>CO2 on activated carbon at 298K (Dreisbach et al. 1999)</li>
 * <li>CO2/CH4 selectivity on activated carbon</li>
 * <li>N2 on activated carbon at 298K</li>
 * <li>CO2 on Zeolite 13X at 298K (Cavenati et al. 2004)</li>
 * <li>Freundlich and Sips model consistency checks</li>
 * </ul>
 *
 * @author ESOL
 */
public class ExperimentalDataValidationTest {

  /**
   * Validate CH4 adsorption on activated carbon at 298K from database parameters.
   *
   * <p>
   * Experimental reference: Dreisbach et al. (1999), Adsorption, 5, 215-227. CH4 on AC Calgon F400
   * at 298 K: approx 4-6 mol/kg at 10 bar.
   * </p>
   */
  @Test
  public void testCH4OnACCalF400Langmuir() {
    SystemInterface gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("AC Calgon F400");
    langmuir.calcAdsorption(0);

    double loading = langmuir.getSurfaceExcess(0);
    System.out.println("CH4 on AC Calgon F400 (Langmuir) at 10 bar: " + loading + " mol/kg");

    // Experimental range: 3-7 mol/kg at ~10 bar
    assertTrue(loading > 2.0, "CH4 loading should be > 2 mol/kg at 10 bar");
    assertTrue(loading < 10.0, "CH4 loading should be < 10 mol/kg at 10 bar");
  }

  /**
   * Validate CO2 adsorption on activated carbon at 298K.
   *
   * <p>
   * CO2 on AC Calgon F400 at 298 K: approximately 6-10 mol/kg at 10 bar.
   * </p>
   */
  @Test
  public void testCO2OnACCalF400Langmuir() {
    SystemInterface gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("CO2", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("AC Calgon F400");
    langmuir.calcAdsorption(0);

    double loading = langmuir.getSurfaceExcess(0);
    System.out.println("CO2 on AC Calgon F400 (Langmuir) at 10 bar: " + loading + " mol/kg");

    // CO2 typically adsorbs more than CH4
    assertTrue(loading > 3.0, "CO2 loading should be > 3 mol/kg at 10 bar");
    assertTrue(loading < 15.0, "CO2 loading should be < 15 mol/kg at 10 bar");
  }

  /**
   * Validate CO2/CH4 selectivity on activated carbon.
   *
   * <p>
   * Published selectivity for CO2/CH4 on activated carbon: 2-5 at moderate pressures.
   * </p>
   */
  @Test
  public void testCO2CH4SelectivityOnAC() {
    SystemInterface gas = new SystemSrkEos(298.15, 5.0);
    gas.addComponent("CO2", 0.5);
    gas.addComponent("methane", 0.5);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("AC");
    langmuir.calcExtendedLangmuir(0);

    double co2Loading = langmuir.getSurfaceExcess("CO2");
    double ch4Loading = langmuir.getSurfaceExcess("methane");
    double selectivity = langmuir.getSelectivity(0, 1, 0);

    System.out.println("CO2/CH4 on AC at 5 bar:");
    System.out.println("  CO2: " + co2Loading + " mol/kg, CH4: " + ch4Loading + " mol/kg");
    System.out.println("  Selectivity: " + selectivity);

    assertTrue(co2Loading > ch4Loading, "CO2 should adsorb more than CH4 on AC");
    assertTrue(selectivity > 1.5, "CO2/CH4 selectivity should be > 1.5");
    assertTrue(selectivity < 15.0, "CO2/CH4 selectivity should be < 15");
  }

  /**
   * Validate Freundlich model gives physically reasonable results.
   */
  @Test
  public void testFreundlichPhysicalConsistency() {
    // Test at multiple pressures - Freundlich should increase monotonically
    double[] pressures = {1.0, 5.0, 10.0, 20.0, 50.0};
    double prevLoading = 0.0;

    for (double p : pressures) {
      SystemInterface gas = new SystemSrkEos(298.15, p);
      gas.addComponent("methane", 1.0);
      gas.setMixingRule("classic");
      gas.init(0);

      FreundlichAdsorption freundlich = new FreundlichAdsorption(gas);
      freundlich.setSolidMaterial("AC");
      freundlich.calcAdsorption(0);

      double loading = freundlich.getSurfaceExcess(0);
      System.out.println("CH4 Freundlich at " + p + " bar: " + loading + " mol/kg");

      assertTrue(loading >= prevLoading, "Adsorption should increase with pressure (P=" + p + ")");
      assertTrue(loading >= 0, "Adsorption should be non-negative");
      prevLoading = loading;
    }
  }

  /**
   * Validate Sips model at 1 bar reduces to reasonable Langmuir-like behavior.
   *
   * <p>
   * When n=1, Sips should give same results as Langmuir.
   * </p>
   */
  @Test
  public void testSipsReducesToLangmuir() {
    SystemInterface gas = new SystemSrkEos(298.15, 5.0);
    gas.addComponent("CO2", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    // Langmuir with specific parameters
    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setQmax(0, 8.0);
    langmuir.setKLangmuir(0, 0.5);
    langmuir.calcAdsorption(0);
    double langmuirLoading = langmuir.getSurfaceExcess(0);

    // Sips with n=1 should reduce to Langmuir
    SipsAdsorption sips = new SipsAdsorption(gas);
    sips.setQmax(0, 8.0);
    sips.setKSips(0, 0.5);
    sips.setNSips(0, 1.0);
    sips.calcAdsorption(0);
    double sipsLoading = sips.getSurfaceExcess(0);

    System.out.println("Langmuir loading: " + langmuirLoading + " mol/kg");
    System.out.println("Sips (n=1) loading: " + sipsLoading + " mol/kg");

    assertEquals(langmuirLoading, sipsLoading, 0.01, "Sips with n=1 should match Langmuir");
  }

  /**
   * Validate pressure-dependent isotherm shape.
   *
   * <p>
   * At low pressure: q approximately linear with P (Henry's law region). At high pressure: q
   * approaches saturation capacity.
   * </p>
   */
  @Test
  public void testLangmuirPressureBehavior() {
    // Very low pressure - Henry region
    SystemInterface lowP = new SystemSrkEos(298.15, 0.01);
    lowP.addComponent("CO2", 1.0);
    lowP.setMixingRule("classic");
    lowP.init(0);

    LangmuirAdsorption lowPAds = new LangmuirAdsorption(lowP);
    lowPAds.setQmax(0, 10.0);
    lowPAds.setKLangmuir(0, 1.0);
    lowPAds.calcAdsorption(0);
    double lowPLoading = lowPAds.getSurfaceExcess(0);

    // High pressure - near saturation
    SystemInterface highP = new SystemSrkEos(298.15, 100.0);
    highP.addComponent("CO2", 1.0);
    highP.setMixingRule("classic");
    highP.init(0);

    LangmuirAdsorption highPAds = new LangmuirAdsorption(highP);
    highPAds.setQmax(0, 10.0);
    highPAds.setKLangmuir(0, 1.0);
    highPAds.calcAdsorption(0);
    double highPLoading = highPAds.getSurfaceExcess(0);

    System.out.println("Langmuir CO2 at 0.01 bar: " + lowPLoading + " mol/kg");
    System.out.println("Langmuir CO2 at 100 bar: " + highPLoading + " mol/kg");

    // At high pressure, should approach qmax
    assertTrue(highPLoading > 9.0, "At 100 bar, loading should approach qmax of 10");
    // At low pressure, should be much lower
    assertTrue(lowPLoading < 1.0, "At 0.01 bar, loading should be in Henry region");
  }

  /**
   * Validate CO2 on Zeolite 13X with database parameters.
   *
   * <p>
   * Reference: Cavenati et al. (2004), J. Chem. Eng. Data, 49, 1095-1101. CO2 on Zeolite 13X at
   * 298K, 1 bar: approx 3-5 mol/kg.
   * </p>
   */
  @Test
  public void testCO2OnZeolite13XFromDB() {
    SystemInterface gas = new SystemSrkEos(298.15, 1.0);
    gas.addComponent("CO2", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("Zeolite 13X");
    langmuir.calcAdsorption(0);

    double loading = langmuir.getSurfaceExcess(0);
    System.out.println("CO2 on Zeolite 13X (Langmuir) at 1 bar: " + loading + " mol/kg");

    // Zeolite 13X has high CO2 capacity: 3-6 mol/kg at 1 bar
    assertTrue(loading > 2.0, "CO2 on 13X should be > 2 mol/kg at 1 bar");
    assertTrue(loading < 8.0, "CO2 on 13X should be < 8 mol/kg at 1 bar");
  }

  /**
   * Validate CO2/N2 selectivity on Zeolite 13X.
   *
   * <p>
   * Literature selectivity for CO2/N2 on 13X is typically very high (50-200).
   * </p>
   */
  @Test
  public void testCO2N2SelectivityOnZeolite13X() {
    SystemInterface gas = new SystemSrkEos(298.15, 1.0);
    gas.addComponent("CO2", 0.15);
    gas.addComponent("nitrogen", 0.85);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("Zeolite 13X");
    langmuir.calcExtendedLangmuir(0);

    double co2Loading = langmuir.getSurfaceExcess("CO2");
    double n2Loading = langmuir.getSurfaceExcess("nitrogen");
    double selectivity = langmuir.getSelectivity(0, 1, 0);

    System.out.println("CO2/N2 on Zeolite 13X at 1 bar (15% CO2):");
    System.out.println("  CO2: " + co2Loading + " mol/kg, N2: " + n2Loading + " mol/kg");
    System.out.println("  Selectivity: " + selectivity);

    assertTrue(co2Loading > n2Loading, "CO2 should adsorb much more than N2 on 13X");
    assertTrue(selectivity > 10.0, "CO2/N2 selectivity should be > 10 on zeolite 13X");
  }

  /**
   * Validate temperature effect on adsorption - higher T should give less adsorption.
   */
  @Test
  public void testTemperatureEffect() {
    // 298 K
    SystemInterface gas298 = new SystemSrkEos(298.15, 5.0);
    gas298.addComponent("CO2", 1.0);
    gas298.setMixingRule("classic");
    gas298.init(0);
    ThermodynamicOperations ops1 = new ThermodynamicOperations(gas298);
    try {
      ops1.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption ads298 = new LangmuirAdsorption(gas298);
    ads298.setSolidMaterial("AC");
    ads298.calcAdsorption(0);
    double loading298 = ads298.getSurfaceExcess(0);

    // 373 K (100°C)
    SystemInterface gas373 = new SystemSrkEos(373.15, 5.0);
    gas373.addComponent("CO2", 1.0);
    gas373.setMixingRule("classic");
    gas373.init(0);
    ThermodynamicOperations ops2 = new ThermodynamicOperations(gas373);
    try {
      ops2.TPflash();
    } catch (Exception e) {
      // pass
    }

    LangmuirAdsorption ads373 = new LangmuirAdsorption(gas373);
    ads373.setSolidMaterial("AC");
    ads373.calcAdsorption(0);
    double loading373 = ads373.getSurfaceExcess(0);

    System.out.println("CO2 on AC at 298K: " + loading298 + " mol/kg");
    System.out.println("CO2 on AC at 373K: " + loading373 + " mol/kg");

    assertTrue(loading298 > loading373, "Adsorption should decrease with increasing temperature");
  }

  /**
   * Test all models give consistent ordering of adsorbate affinities.
   *
   * <p>
   * On activated carbon at ambient conditions, expected ordering: CO2 &gt; CH4 &gt; N2.
   * </p>
   */
  @Test
  public void testAdsorbateAffinityOrdering() {
    String[] models = {"Langmuir", "Freundlich", "Sips"};

    for (String model : models) {
      // CO2
      SystemInterface co2Gas = new SystemSrkEos(298.15, 5.0);
      co2Gas.addComponent("CO2", 1.0);
      co2Gas.setMixingRule("classic");
      co2Gas.init(0);

      // CH4
      SystemInterface ch4Gas = new SystemSrkEos(298.15, 5.0);
      ch4Gas.addComponent("methane", 1.0);
      ch4Gas.setMixingRule("classic");
      ch4Gas.init(0);

      // N2
      SystemInterface n2Gas = new SystemSrkEos(298.15, 5.0);
      n2Gas.addComponent("nitrogen", 1.0);
      n2Gas.setMixingRule("classic");
      n2Gas.init(0);

      double co2Loading;
      double ch4Loading;
      double n2Loading;

      if ("Langmuir".equals(model)) {
        LangmuirAdsorption ads = new LangmuirAdsorption(co2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        co2Loading = ads.getSurfaceExcess(0);

        ads = new LangmuirAdsorption(ch4Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        ch4Loading = ads.getSurfaceExcess(0);

        ads = new LangmuirAdsorption(n2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        n2Loading = ads.getSurfaceExcess(0);
      } else if ("Freundlich".equals(model)) {
        FreundlichAdsorption ads = new FreundlichAdsorption(co2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        co2Loading = ads.getSurfaceExcess(0);

        ads = new FreundlichAdsorption(ch4Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        ch4Loading = ads.getSurfaceExcess(0);

        ads = new FreundlichAdsorption(n2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        n2Loading = ads.getSurfaceExcess(0);
      } else {
        SipsAdsorption ads = new SipsAdsorption(co2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        co2Loading = ads.getSurfaceExcess(0);

        ads = new SipsAdsorption(ch4Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        ch4Loading = ads.getSurfaceExcess(0);

        ads = new SipsAdsorption(n2Gas);
        ads.setSolidMaterial("AC");
        ads.calcAdsorption(0);
        n2Loading = ads.getSurfaceExcess(0);
      }

      System.out.println(model + " at 5 bar on AC: CO2=" + co2Loading + ", CH4=" + ch4Loading
          + ", N2=" + n2Loading);

      assertTrue(co2Loading > ch4Loading, model + ": CO2 should adsorb more than CH4 on AC");
      assertTrue(ch4Loading > n2Loading, model + ": CH4 should adsorb more than N2 on AC");
    }
  }

  /**
   * Test FluidPropertyEstimator saturation pressure against known values.
   */
  @Test
  public void testSaturationPressureEstimation() {
    // CO2 at 298 K: Psat ~ 64 bar
    SystemInterface gas = new SystemSrkEos(298.15, 1.0);
    gas.addComponent("CO2", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);

    double pSatCO2 = FluidPropertyEstimator.estimateSaturationPressure(gas, 0, 0);
    System.out.println("CO2 Psat at 298K (Lee-Kesler): " + pSatCO2 + " bar");

    // CO2 saturation pressure at 298K should be ~64 bar
    assertTrue(pSatCO2 > 50.0, "CO2 Psat should be > 50 bar at 298K");
    assertTrue(pSatCO2 < 80.0, "CO2 Psat should be < 80 bar at 298K");

    // Water at 373 K: Psat ~ 1 bar
    SystemInterface water = new SystemSrkEos(373.15, 1.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    water.init(0);

    double pSatH2O = FluidPropertyEstimator.estimateSaturationPressure(water, 0, 0);
    System.out.println("Water Psat at 373K (Lee-Kesler): " + pSatH2O + " bar");

    assertTrue(pSatH2O > 0.5, "Water Psat should be > 0.5 bar at 373K");
    assertTrue(pSatH2O < 2.0, "Water Psat should be < 2 bar at 373K");
  }

  /**
   * Compare model predictions across isotherms for same component.
   *
   * <p>
   * Different isotherms should give qualitatively similar predictions when fitted to the same data.
   * </p>
   */
  @Test
  public void testModelConsistencyAcrossIsotherms() {
    SystemInterface gas = new SystemSrkEos(298.15, 5.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    // Langmuir
    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setSolidMaterial("AC");
    langmuir.calcAdsorption(0);
    double langmuirVal = langmuir.getSurfaceExcess(0);

    // Freundlich
    FreundlichAdsorption freundlich = new FreundlichAdsorption(gas);
    freundlich.setSolidMaterial("AC");
    freundlich.calcAdsorption(0);
    double freundlichVal = freundlich.getSurfaceExcess(0);

    // Sips
    SipsAdsorption sips = new SipsAdsorption(gas);
    sips.setSolidMaterial("AC");
    sips.calcAdsorption(0);
    double sipsVal = sips.getSurfaceExcess(0);

    // BET
    BETAdsorption bet = new BETAdsorption(gas);
    bet.setSolidMaterial("AC");
    bet.calcAdsorption(0);
    double betVal = bet.getSurfaceExcess(0);

    System.out.println("CH4 on AC at 5 bar, model comparison:");
    System.out.println("  Langmuir: " + langmuirVal + " mol/kg");
    System.out.println("  Freundlich: " + freundlichVal + " mol/kg");
    System.out.println("  Sips: " + sipsVal + " mol/kg");
    System.out.println("  BET: " + betVal + " mol/kg");

    // All models should give positive values
    assertTrue(langmuirVal > 0, "Langmuir should give positive loading");
    assertTrue(freundlichVal > 0, "Freundlich should give positive loading");
    assertTrue(sipsVal > 0, "Sips should give positive loading");
    assertTrue(betVal > 0, "BET should give positive loading");

    // All models should be within same order of magnitude
    double maxVal = Math.max(Math.max(langmuirVal, freundlichVal), Math.max(sipsVal, betVal));
    double minVal = Math.min(Math.min(langmuirVal, freundlichVal), Math.min(sipsVal, betVal));

    assertTrue(maxVal / minVal < 20.0, "Models should be within factor of 20 for same conditions");
  }
}
