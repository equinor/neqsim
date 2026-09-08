package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.CapillaryCondensationModel.RelativeSaturationBasis;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for capillary condensation of a polar contaminant in high-pressure gas.
 *
 * <p>
 * Motivated by a mercury guard bed methanol limit study. The previous implementation returned a liquid molar volume
 * 1000x too large, which made the Macleod-Sugden surface tension underflow to about 1e-11 N/m, so the Kelvin term
 * vanished and the model silently predicted that capillary condensation never happens. It also evaluated the Kelvin
 * driving force from the ideal partial pressure, which over-predicts the driving force by a factor of about two at 70
 * bara.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CapillaryCondensationMethanolTest {

  /** Reference liquid molar volume of methanol at 20 C (m3/mol). */
  private static final double VM_METHANOL = 40.7e-6;

  /** Reference surface tension of methanol at 20 C (N/m). */
  private static final double SIGMA_METHANOL = 0.0225;

  /**
   * Build a rich natural gas with a trace of methanol at guard bed conditions.
   *
   * @param pressureBara the pressure in bara
   * @param methanolMoleFraction the methanol mole fraction
   * @return an initialised thermodynamic system
   */
  private SystemInterface richGasWithMethanol(double pressureBara, double methanolMoleFraction) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 20.0, pressureBara);
    gas.addComponent("methane", 0.86 * (1.0 - methanolMoleFraction));
    gas.addComponent("ethane", 0.09 * (1.0 - methanolMoleFraction));
    gas.addComponent("propane", 0.05 * (1.0 - methanolMoleFraction));
    gas.addComponent("methanol", methanolMoleFraction);
    gas.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    return gas;
  }

  /**
   * The Rackett helper must return a liquid molar volume in m3/mol, not 1000x too large.
   */
  @Test
  public void testLiquidMolarVolumeUnits() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    double vm = FluidPropertyEstimator.estimateLiquidMolarVolume(gas, 0, idx);
    assertTrue(vm > 20.0e-6 && vm < 90.0e-6,
        "Methanol liquid molar volume should be of order 4e-5 m3/mol but was " + vm);
  }

  /**
   * With a physical molar volume the Macleod-Sugden surface tension must be of order 10 mN/m, not 1e-8 mN/m.
   */
  @Test
  public void testSurfaceTensionDoesNotUnderflow() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    double vm = FluidPropertyEstimator.estimateLiquidMolarVolume(gas, 0, idx);
    double sigma = FluidPropertyEstimator.estimateSurfaceTension(gas, 0, idx, vm);
    assertTrue(sigma > 5.0e-3 && sigma < 100.0e-3,
        "Methanol surface tension should be of order 0.02 N/m but was " + sigma);
  }

  /**
   * Antoine data must be preferred over Lee-Kesler, which over-predicts methanol vapour pressure by an order of
   * magnitude.
   */
  @Test
  public void testSaturationPressureUsesAntoineForMethanol() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    double pSat = FluidPropertyEstimator.estimateSaturationPressure(gas, 0, idx);
    assertEquals(0.130, pSat, 0.030, "Methanol vapour pressure at 20 C should be near 0.13 bara");
  }

  /**
   * The Kelvin onset must reproduce the analytical relation for methanol in a 5 nm pore.
   */
  @Test
  public void testKelvinOnsetMatchesAnalyticalValue() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    model.setAdsorbedLayerThickness(0.0);
    model.setPoreType(CapillaryCondensationModel.PoreType.CYLINDRICAL);

    double onset = model.getCondensationPressure(5.0, idx, 0);
    // exp(-2*0.0225*40.7e-6/(8.314*293.15*5e-9)) = 0.8605
    assertEquals(0.8605, onset, 0.005, "Kelvin onset for methanol in a 5 nm cylindrical pore");
  }

  /**
   * Narrower pores must condense at a lower relative saturation.
   */
  @Test
  public void testNarrowPoresCondenseEarlier() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    model.setAdsorbedLayerThickness(0.0);

    double onset2nm = model.getCondensationPressure(2.0, idx, 0);
    double onset20nm = model.getCondensationPressure(20.0, idx, 0);
    assertTrue(onset2nm < onset20nm, "A 2 nm pore must fill at a lower relative saturation than a 20 nm pore");
    assertTrue(onset2nm < 0.75, "A 2 nm pore should fill well below bulk saturation");
  }

  /**
   * The ideal partial pressure basis must over-predict the driving force relative to the fugacity basis at elevated
   * pressure, and the two must converge as pressure falls.
   */
  @Test
  public void testFugacityBasisIsLowerThanPartialPressureBasisAtHighPressure() {
    double yMethanol = 300.0e-6;

    SystemInterface highP = richGasWithMethanol(70.0, yMethanol);
    CapillaryCondensationModel idealModel = new CapillaryCondensationModel(highP);
    idealModel.setRelativeSaturationBasis(RelativeSaturationBasis.PARTIAL_PRESSURE);
    idealModel.calcCapillaryCondensation(0);
    double aIdeal = idealModel.getRelativeSaturation("methanol");

    CapillaryCondensationModel fugModel = new CapillaryCondensationModel(highP);
    fugModel.setRelativeSaturationBasis(RelativeSaturationBasis.FUGACITY);
    fugModel.calcCapillaryCondensation(0);
    double aFug = fugModel.getRelativeSaturation("methanol");

    assertTrue(aFug > 0.0, "Fugacity based relative saturation must be positive");
    assertTrue(aIdeal > aFug * 1.2, "Ideal partial pressure should over-predict the driving force at 70 bara: ideal="
        + aIdeal + " fugacity=" + aFug);

    SystemInterface lowP = richGasWithMethanol(2.0, yMethanol);
    CapillaryCondensationModel lowIdeal = new CapillaryCondensationModel(lowP);
    lowIdeal.setRelativeSaturationBasis(RelativeSaturationBasis.PARTIAL_PRESSURE);
    lowIdeal.calcCapillaryCondensation(0);
    CapillaryCondensationModel lowFug = new CapillaryCondensationModel(lowP);
    lowFug.setRelativeSaturationBasis(RelativeSaturationBasis.FUGACITY);
    lowFug.calcCapillaryCondensation(0);
    double ratioLowP = lowIdeal.getRelativeSaturation("methanol") / lowFug.getRelativeSaturation("methanol");
    assertEquals(1.0, ratioLowP, 0.10, "The two bases must agree near atmospheric pressure");
  }

  /**
   * The maximum allowable mole fraction must converge to a fixed point at which the relative saturation equals the
   * Kelvin onset. Methanol association makes its fugacity coefficient concentration dependent, so the limit has to be
   * iterated.
   */
  @Test
  public void testMaxAllowableMoleFractionIsSelfConsistent() {
    double poreRadius = 5.0;
    double yMax = 100.0e-6;

    for (int iteration = 0; iteration < 6; iteration++) {
      SystemInterface gas = richGasWithMethanol(70.0, yMax);
      CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
      int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
      model.setLiquidMolarVolume(idx, VM_METHANOL);
      model.setSurfaceTension(idx, SIGMA_METHANOL);
      model.setAdsorbedLayerThickness(0.0);
      yMax = model.getMaxAllowableMoleFraction(idx, poreRadius, 0);
      assertTrue(yMax > 0.0 && yMax < 1.0, "Maximum allowable mole fraction should be a physical fraction");
    }

    SystemInterface atLimit = richGasWithMethanol(70.0, yMax);
    CapillaryCondensationModel check = new CapillaryCondensationModel(atLimit);
    int idx2 = atLimit.getPhase(0).getComponent("methanol").getComponentNumber();
    check.setLiquidMolarVolume(idx2, VM_METHANOL);
    check.setSurfaceTension(idx2, SIGMA_METHANOL);
    check.setAdsorbedLayerThickness(0.0);
    check.calcCapillaryCondensation(0);

    double onset = check.getCondensationPressure(poreRadius, idx2, 0);
    assertEquals(onset, check.getRelativeSaturation(idx2), 0.02 * onset,
        "At the converged limit the relative saturation must equal the Kelvin onset");
  }

  /**
   * A narrow pore must impose a tighter contaminant limit than a wide pore.
   */
  @Test
  public void testNarrowPoresGiveTighterContaminantLimit() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    model.setAdsorbedLayerThickness(0.0);

    double yMaxNarrow = model.getMaxAllowableMoleFraction(idx, 2.0, 0);
    double yMaxWide = model.getMaxAllowableMoleFraction(idx, 20.0, 0);
    assertTrue(yMaxNarrow < yMaxWide,
        "A microporous sorbent must tolerate less methanol than a macroporous one: narrow=" + yMaxNarrow + " wide="
            + yMaxWide);
  }

  /**
   * At or above bulk saturation every pore must be reported as filled, not as zero condensate.
   */
  @Test
  public void testSaturatedGasFillsAllPores() {
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    // Force the saturated branch by declaring an artificially low vapour pressure.
    model.setSaturationPressure(idx, 1.0e-6);
    model.setTotalPoreVolume(0.5);
    model.calcCapillaryCondensation(0);

    assertTrue(model.getCondensateAmount(idx) > 0.0,
        "A gas at or above bulk saturation must fill the pore volume, not report zero condensate");
  }

  /**
   * Flash the rich gas against excess liquid methanol and return the gas-phase methanol mole fraction.
   *
   * @param pressureBara the pressure in bara
   * @return the saturation mole fraction of methanol in the gas
   */
  private double methanolSaturationMoleFraction(double pressureBara) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 20.0, pressureBara);
    gas.addComponent("methane", 0.86);
    gas.addComponent("ethane", 0.09);
    gas.addComponent("propane", 0.05);
    gas.addComponent("methanol", 5.0);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    return gas.getPhase("gas").getComponent("methanol").getx();
  }

  /**
   * The saturation basis must return exactly one at the flashed saturation composition, where the pure-liquid fugacity
   * basis is biased low by the dissolved-gas dilution of the real liquid.
   */
  @Test
  public void testSaturationBasisIsExactAtSaturation() {
    double ySat = methanolSaturationMoleFraction(70.0);
    assertTrue(ySat > 1.0e-3 && ySat < 1.0e-2, "Methanol saturation should be a few thousand ppmv, was " + ySat);

    SystemInterface gas = richGasWithMethanol(70.0, 0.999 * ySat);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();

    CapillaryCondensationModel exact = new CapillaryCondensationModel(gas);
    exact.setLiquidMolarVolume(idx, VM_METHANOL);
    exact.setSurfaceTension(idx, SIGMA_METHANOL);
    exact.setSaturationMoleFraction(idx, ySat);
    exact.setRelativeSaturationBasis(RelativeSaturationBasis.SATURATION_MOLE_FRACTION);
    exact.calcCapillaryCondensation(0);
    assertEquals(1.0, exact.getRelativeSaturation(idx), 0.02,
        "At the flashed saturation composition the relative saturation must be one");
  }

  /**
   * On the exact basis the limit is simply the Kelvin onset times the saturation mole fraction, and it can never exceed
   * bulk saturation.
   */
  @Test
  public void testSaturationBasisLimitNeverExceedsBulkSaturation() {
    double ySat = methanolSaturationMoleFraction(70.0);
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();

    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    model.setAdsorbedLayerThickness(0.0);
    model.setSaturationMoleFraction(idx, ySat);
    model.setRelativeSaturationBasis(RelativeSaturationBasis.SATURATION_MOLE_FRACTION);

    for (double radius : new double[] { 1.0, 2.0, 5.0, 20.0, 50.0 }) {
      double onset = model.getCondensationPressure(radius, idx, 0);
      double limit = model.getMaxAllowableMoleFraction(idx, radius, 0);
      assertEquals(onset * ySat, limit, 1.0e-9,
          "On the saturation basis the limit must be the Kelvin onset times the saturation mole fraction");
      assertTrue(limit < ySat, "The limit must stay below bulk saturation at radius " + radius);
    }
  }

  /**
   * Supplying the saturation mole fraction must also cap the approximate fugacity basis, which on its own can report a
   * limit above bulk saturation.
   */
  @Test
  public void testSaturationMoleFractionCapsTheFugacityBasis() {
    double ySat = methanolSaturationMoleFraction(70.0);
    SystemInterface gas = richGasWithMethanol(70.0, 100.0e-6);
    int idx = gas.getPhase(0).getComponent("methanol").getComponentNumber();

    CapillaryCondensationModel model = new CapillaryCondensationModel(gas);
    model.setLiquidMolarVolume(idx, VM_METHANOL);
    model.setSurfaceTension(idx, SIGMA_METHANOL);
    model.setAdsorbedLayerThickness(0.0);
    model.setRelativeSaturationBasis(RelativeSaturationBasis.FUGACITY);
    model.setSaturationMoleFraction(idx, ySat);

    double limit = model.getMaxAllowableMoleFraction(idx, 20.0, 0);
    assertTrue(limit < ySat, "A capped fugacity-basis limit must stay below bulk saturation, was " + limit);
  }
}
