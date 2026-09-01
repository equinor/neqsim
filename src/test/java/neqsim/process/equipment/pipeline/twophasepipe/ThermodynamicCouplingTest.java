package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling.ThermoProperties;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for ThermodynamicCoupling class.
 */
class ThermodynamicCouplingTest {
  private ThermodynamicCoupling coupling;
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    // Create a simple two-phase test fluid
    testFluid = new SystemSrkEos(298.15, 50.0); // 25°C, 50 bar
    testFluid.addComponent("methane", 0.9);
    testFluid.addComponent("n-heptane", 0.1);
    testFluid.setMixingRule("classic");
    testFluid.init(0);

    coupling = new ThermodynamicCoupling(testFluid);
  }

  @Test
  void testFlashPTReturnsValidProperties() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.converged, "Flash should converge");
    assertTrue(props.gasDensity > 0, "Gas density should be positive");
    assertTrue(props.liquidDensity > 0, "Liquid density should be positive");
    assertTrue(props.gasViscosity > 0, "Gas viscosity should be positive");
    assertTrue(props.liquidViscosity > 0, "Liquid viscosity should be positive");
  }

  @Test
  void testGasDensityIncreasesWithPressure() {
    ThermoProperties propsLowP = coupling.flashPT(10e5, 300.0);
    ThermoProperties propsHighP = coupling.flashPT(50e5, 300.0);

    assertTrue(propsHighP.gasDensity > propsLowP.gasDensity, "Gas density should increase with pressure");
  }

  @Test
  void testVaporFractionRangeIsValid() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasVaporFraction >= 0.0, "Vapor fraction should be >= 0");
    assertTrue(props.gasVaporFraction <= 1.0, "Vapor fraction should be <= 1");
  }

  @Test
  void testEnthalpyValuesAreRealistic() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Enthalpies can be negative or positive, but should be finite
    assertTrue(Double.isFinite(props.gasEnthalpy), "Gas enthalpy should be finite");
    assertTrue(Double.isFinite(props.liquidEnthalpy), "Liquid enthalpy should be finite");
  }

  @Test
  void testSurfaceTensionIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Surface tension should be positive for two-phase system
    assertTrue(props.surfaceTension >= 0.0, "Surface tension should be non-negative");
  }

  @Test
  void testViscosityOrdering() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Liquid viscosity typically higher than gas viscosity
    if (props.liquidViscosity > 0 && props.gasViscosity > 0) {
      assertTrue(props.liquidViscosity > props.gasViscosity,
          "Liquid viscosity should typically be higher than gas viscosity");
    }
  }

  @Test
  void testDensityOrdering() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Liquid density should be higher than gas density
    if (props.liquidDensity > 0 && props.gasDensity > 0) {
      assertTrue(props.liquidDensity > props.gasDensity, "Liquid density should be higher than gas density");
    }
  }

  @Test
  void testSoundSpeedIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasSoundSpeed > 0, "Gas sound speed should be positive");
    assertTrue(props.liquidSoundSpeed > 0, "Liquid sound speed should be positive");
  }

  @Test
  void testCompressibilityIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasCompressibility > 0, "Gas compressibility should be positive");
    assertTrue(props.liquidCompressibility > 0, "Liquid compressibility should be positive");
  }

  @Test
  void testFlashWithDifferentTemperatures() {
    ThermoProperties propsLowT = coupling.flashPT(30e5, 250.0);
    ThermoProperties propsHighT = coupling.flashPT(30e5, 350.0);

    assertTrue(propsLowT.converged, "Low temperature flash should converge");
    assertTrue(propsHighT.converged, "High temperature flash should converge");

    // Vapor fraction should typically increase with temperature
    assertTrue(propsHighT.gasVaporFraction >= propsLowT.gasVaporFraction,
        "Vapor fraction should generally increase with temperature");
  }

  @Test
  void testAqueousFirstLiquidUsesEquilibriumPhaseIdentity() {
    ThermoProperties aqueousEquilibrium = liquidEquilibrium(0.0, 1.0);
    ThermodynamicCoupling fixedCoupling = fixedEquilibriumCoupling(aqueousEquilibrium);
    TwoFluidSection gasOnlySection = gasOnlySectionWithTwoTenthsKgEquilibriumLiquid();

    PhaseMassTransfer transfer = fixedCoupling.calcPhaseMassTransferRatePerLength(gasOnlySection, 1.0);

    assertEquals(-0.2, transfer.getGasSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.0, transfer.getOilSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.2, transfer.getWaterSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.0, transfer.getTotalSourceKgPerMetreSecond(), 1.0e-15);
    assertTrue(transfer.isApplicable());
  }

  @Test
  void testOilFirstLiquidDoesNotCreateWater() {
    ThermoProperties oilEquilibrium = liquidEquilibrium(1.0, 0.0);
    ThermodynamicCoupling fixedCoupling = fixedEquilibriumCoupling(oilEquilibrium);
    TwoFluidSection gasOnlySection = gasOnlySectionWithTwoTenthsKgEquilibriumLiquid();

    PhaseMassTransfer transfer = fixedCoupling.calcPhaseMassTransferRatePerLength(gasOnlySection, 1.0);

    assertEquals(-0.2, transfer.getGasSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.2, transfer.getOilSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.0, transfer.getWaterSourceKgPerMetreSecond(), 1.0e-12);
  }

  @Test
  void testEvaporationUsesAndLimitsActualDonorInventory() {
    ThermoProperties gasEquilibrium = new ThermoProperties();
    gasEquilibrium.gasVaporFraction = 1.0;
    gasEquilibrium.gasDensity = 10.0;
    gasEquilibrium.gasMolarMass = 20.0;
    gasEquilibrium.liquidDensity = 800.0;
    gasEquilibrium.liquidMolarMass = 100.0;
    gasEquilibrium.converged = true;
    ThermodynamicCoupling fixedCoupling = fixedEquilibriumCoupling(gasEquilibrium);
    TwoFluidSection oilOnlySection = new TwoFluidSection(0.0, 1.0, 0.1, 0.0);
    oilOnlySection.setPressure(50.0e5);
    oilOnlySection.setTemperature(300.0);
    oilOnlySection.setLiquidMassPerLength(0.03);
    oilOnlySection.setOilMassPerLength(0.03);
    oilOnlySection.setWaterMassPerLength(0.0);

    PhaseMassTransfer transfer = fixedCoupling.calcPhaseMassTransferRatePerLength(oilOnlySection, 2.0);

    assertEquals(0.015, transfer.getGasSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(-0.015, transfer.getOilSourceKgPerMetreSecond(), 1.0e-12);
    assertEquals(0.0, transfer.getWaterSourceKgPerMetreSecond(), 1.0e-12);
    assertTrue(-transfer.getOilSourceKgPerMetreSecond() <= oilOnlySection.getOilMassPerLength() / 2.0);
  }

  @Test
  void testRealCpaWaterDewPointCrossesInBothDirections() throws Exception {
    SystemInterface wetGas = createWetGasAtWaterDewPointConditions();
    SystemInterface dewPointFluid = wetGas.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    double dewPointTemperature = dewPointFluid.getTemperature();
    ThermodynamicCoupling cpaCoupling = new ThermodynamicCoupling(wetGas);
    TwoFluidSection section = gasOnlySectionWithTwoTenthsKgEquilibriumLiquid();
    section.setPressure(70.0e5);
    section.setGasMassPerLength(10.0);

    double[] temperatureOffsets = { -1.0, -0.5, -0.2 };
    double previousWaterSource = Double.POSITIVE_INFINITY;
    double[] condensedWaterInventories = new double[temperatureOffsets.length];
    for (int index = 0; index < temperatureOffsets.length; index++) {
      double offset = temperatureOffsets[index];
      section.setTemperature(dewPointTemperature + offset);
      PhaseMassTransfer transfer = cpaCoupling.calcPhaseMassTransferRatePerLength(section, 30.0);
      assertTrue(transfer.isFlashConverged());
      assertTrue(transfer.isApplicable());
      assertTrue(transfer.getGasSourceKgPerMetreSecond() < 0.0);
      assertEquals(0.0, transfer.getOilSourceKgPerMetreSecond(), 1.0e-12);
      assertTrue(transfer.getWaterSourceKgPerMetreSecond() > 0.0);
      assertEquals(0.0, transfer.getTotalSourceKgPerMetreSecond(), 1.0e-15);
      assertTrue(transfer.getWaterSourceKgPerMetreSecond() <= previousWaterSource,
          "Condensation should vanish continuously as the water dew point is approached");
      previousWaterSource = transfer.getWaterSourceKgPerMetreSecond();
      condensedWaterInventories[index] = transfer.getWaterSourceKgPerMetreSecond() * 30.0;
    }

    double[] warmTemperatureOffsets = { 1.0, 0.5, 0.2 };
    double previousEvaporationMagnitude = Double.POSITIVE_INFINITY;
    for (int index = 0; index < warmTemperatureOffsets.length; index++) {
      double condensedWaterInventory = condensedWaterInventories[index];
      section.setLiquidMassPerLength(condensedWaterInventory);
      section.setOilMassPerLength(0.0);
      section.setWaterMassPerLength(condensedWaterInventory);
      section.setTemperature(dewPointTemperature + warmTemperatureOffsets[index]);
      PhaseMassTransfer warmTransfer = cpaCoupling.calcPhaseMassTransferRatePerLength(section, 30.0);

      assertTrue(warmTransfer.isFlashConverged());
      assertTrue(warmTransfer.isApplicable());
      assertTrue(warmTransfer.getGasSourceKgPerMetreSecond() > 0.0);
      assertEquals(0.0, warmTransfer.getOilSourceKgPerMetreSecond(), 1.0e-12);
      assertTrue(warmTransfer.getWaterSourceKgPerMetreSecond() < 0.0);
      double evaporationMagnitude = -warmTransfer.getWaterSourceKgPerMetreSecond();
      assertTrue(evaporationMagnitude <= section.getWaterMassPerLength() / 30.0 + 1.0e-15);
      assertTrue(evaporationMagnitude <= previousEvaporationMagnitude,
          "Evaporation should vanish continuously as the water dew point is approached");
      previousEvaporationMagnitude = evaporationMagnitude;
      assertEquals(0.0, warmTransfer.getTotalSourceKgPerMetreSecond(), 1.0e-15);
    }
  }

  @Test
  void testThreePhaseAggregationIncludesBothLiquidsAndIgnoresPhaseOrder() {
    SystemInterface threePhaseFluid = createThreePhaseFluid();
    ThermodynamicOperations operations = new ThermodynamicOperations(threePhaseFluid);
    operations.TPflash();
    threePhaseFluid.initProperties();
    assertTrue(threePhaseFluid.hasPhaseType(PhaseType.GAS));
    assertTrue(threePhaseFluid.hasPhaseType(PhaseType.OIL));
    assertTrue(threePhaseFluid.hasPhaseType(PhaseType.AQUEOUS));

    ThermodynamicCoupling threePhaseCoupling = new ThermodynamicCoupling(threePhaseFluid);
    ThermoProperties original = threePhaseCoupling.extractProperties(threePhaseFluid);
    int firstPhaseIndex = threePhaseFluid.getPhaseIndex(0);
    int lastPhase = threePhaseFluid.getNumberOfPhases() - 1;
    int lastPhaseIndex = threePhaseFluid.getPhaseIndex(lastPhase);
    threePhaseFluid.setPhaseIndex(0, lastPhaseIndex);
    threePhaseFluid.setPhaseIndex(lastPhase, firstPhaseIndex);
    ThermoProperties reordered = threePhaseCoupling.extractProperties(threePhaseFluid);

    assertTrue(original.liquidFraction > 0.0);
    assertTrue(original.oilMassFractionOfLiquid > 0.0);
    assertTrue(original.aqueousMassFractionOfLiquid > 0.0);
    assertEquals(1.0, original.oilMassFractionOfLiquid + original.aqueousMassFractionOfLiquid, 1.0e-12);
    assertEquals(original.gasVaporFraction, reordered.gasVaporFraction, 1.0e-12);
    assertEquals(original.liquidFraction, reordered.liquidFraction, 1.0e-12);
    assertEquals(original.oilMassFractionOfLiquid, reordered.oilMassFractionOfLiquid, 1.0e-12);
    assertEquals(original.aqueousMassFractionOfLiquid, reordered.aqueousMassFractionOfLiquid, 1.0e-12);

    int gasPhase = threePhaseFluid.getPhaseNumberOfPhase("gas");
    int oilPhase = threePhaseFluid.getPhaseNumberOfPhase("oil");
    int aqueousPhase = threePhaseFluid.getPhaseNumberOfPhase("aqueous");
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, gasPhase), true, false, false);
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, oilPhase), false, true, false);
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, aqueousPhase), false, false, true);
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, gasPhase, oilPhase), true, true, false);
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, gasPhase, aqueousPhase), true, false,
        true);
    assertAggregatedTopology(threePhaseCoupling, selectPhases(threePhaseFluid, oilPhase, aqueousPhase), false, true,
        true);
  }

  private ThermodynamicCoupling fixedEquilibriumCoupling(final ThermoProperties equilibrium) {
    return new ThermodynamicCoupling(testFluid) {
      private static final long serialVersionUID = 1L;

      @Override
      public ThermoProperties flashPT(double pressure, double temperature) {
        return equilibrium;
      }
    };
  }

  private ThermoProperties liquidEquilibrium(double oilMassFraction, double aqueousMassFraction) {
    ThermoProperties equilibrium = new ThermoProperties();
    equilibrium.gasVaporFraction = 0.0;
    equilibrium.liquidFraction = 1.0;
    equilibrium.oilMassFractionOfLiquid = oilMassFraction;
    equilibrium.aqueousMassFractionOfLiquid = aqueousMassFraction;
    equilibrium.gasDensity = 1.0;
    equilibrium.gasMolarMass = 20.0;
    equilibrium.liquidDensity = 100.0;
    equilibrium.liquidMolarMass = 18.0;
    equilibrium.converged = true;
    return equilibrium;
  }

  private TwoFluidSection gasOnlySectionWithTwoTenthsKgEquilibriumLiquid() {
    double area = 0.002;
    double diameter = Math.sqrt(4.0 * area / Math.PI);
    TwoFluidSection gasOnlySection = new TwoFluidSection(0.0, 1.0, diameter, 0.0);
    gasOnlySection.setPressure(50.0e5);
    gasOnlySection.setTemperature(300.0);
    gasOnlySection.setGasMassPerLength(1.0);
    gasOnlySection.setLiquidMassPerLength(0.0);
    gasOnlySection.setOilMassPerLength(0.0);
    gasOnlySection.setWaterMassPerLength(0.0);
    gasOnlySection.setWaterCut(0.0);
    return gasOnlySection;
  }

  private SystemInterface createThreePhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-heptane", 0.15);
    fluid.addComponent("n-octane", 0.15);
    fluid.addComponent("water", 0.20);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private SystemInterface createWetGasAtWaterDewPointConditions() {
    double waterMoleFraction = 22.0e-6;
    SystemInterface fluid = new SystemSrkCPAstatoil(260.15, 70.0);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.9 - waterMoleFraction);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("i-butane", 0.005);
    fluid.addComponent("n-butane", 0.005);
    fluid.addComponent("water", waterMoleFraction);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private SystemInterface selectPhases(SystemInterface source, int... phaseNumbers) {
    SystemInterface topology = source.clone();
    topology.setNumberOfPhases(phaseNumbers.length);
    for (int phase = 0; phase < phaseNumbers.length; phase++) {
      topology.setPhaseIndex(phase, source.getPhaseIndex(phaseNumbers[phase]));
    }
    return topology;
  }

  private void assertAggregatedTopology(ThermodynamicCoupling threePhaseCoupling, SystemInterface topology,
      boolean expectGas, boolean expectOil, boolean expectAqueous) {
    topology.initProperties();
    ThermoProperties properties = threePhaseCoupling.extractProperties(topology);
    assertEquals(expectGas ? 1.0 : 0.0, properties.gasVaporFraction > 0.0 ? 1.0 : 0.0, 0.0);
    assertEquals(expectOil || expectAqueous ? 1.0 : 0.0, properties.liquidFraction > 0.0 ? 1.0 : 0.0, 0.0);
    assertEquals(expectOil ? 1.0 : 0.0, properties.oilMassFractionOfLiquid > 0.0 ? 1.0 : 0.0, 0.0);
    assertEquals(expectAqueous ? 1.0 : 0.0, properties.aqueousMassFractionOfLiquid > 0.0 ? 1.0 : 0.0, 0.0);
    if (expectOil || expectAqueous) {
      assertEquals(1.0, properties.oilMassFractionOfLiquid + properties.aqueousMassFractionOfLiquid, 1.0e-12);
    }
  }
}
