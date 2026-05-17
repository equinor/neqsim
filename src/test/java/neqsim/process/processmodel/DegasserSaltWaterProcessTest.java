package neqsim.process.processmodel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for the degasser salt water process model. This replicates the Python-based grisebinge
 * field degasser emission calculation process using NeqSim's Java API.
 *
 * @author copilot
 * @version 1.0
 */
public class DegasserSaltWaterProcessTest extends neqsim.NeqSimTest {

  // Component names (Java uses hyphens)
  private static final String WATER = "water";
  private static final String NITROGEN = "nitrogen";
  private static final String CO2 = "CO2";
  private static final String METHANE = "methane";
  private static final String ETHANE = "ethane";
  private static final String PROPANE = "propane";
  private static final String I_BUTANE = "i-butane";
  private static final String N_BUTANE = "n-butane";
  private static final String I_PENTANE = "i-pentane";
  private static final String N_PENTANE = "n-pentane";
  private static final String N_HEXANE = "n-hexane";
  private static final String N_HEPTANE = "n-heptane";

  // Predefined compositions for 1st stage separator
  private static final double[] COMPOSITION_1ST =
      {40.0, 0.11028244977990904, 8.920149364154726, 72.38708653421389, 5.19349828118209,
          5.20273089140577, 0.43623950748656926, 1.3935642019214913, 0.7693625154476608,
          0.5431375120078548, 3.9058764060115045, 3.9058764060115045};

  // Predefined compositions for test separator
  private static final double[] COMPOSITION_TEST =
      {40.0, 0.10936961607209343, 14.337190183338668, 70.79824780867902, 5.205358867706758,
          5.235611598737887, 0.42826080120108456, 1.3732632527531796, 0.7194693822733709,
          0.5167666722707507, 0.3380756544276416, 0.3380756544276416};

  // Component names in the order they are added to the fluid
  private static final String[] COMPONENT_NAMES = {WATER, NITROGEN, CO2, METHANE, ETHANE, PROPANE,
      I_BUTANE, N_BUTANE, I_PENTANE, N_PENTANE, N_HEXANE, N_HEPTANE};

  // Process unit names
  private static final String INLET_STREAM_1ST_SEP = "Inlet stream 1st separator";
  private static final String FIRST_SEPARATOR = "1st separator";
  private static final String TEST_SEP_INLET = "Test separator inlet";
  private static final String TEST_SEPARATOR = "Test separator";
  private static final String TP_SETTER_DEGASSER_MAIN = "TP setter for the degasser main stream";
  private static final String TP_SETTER_DEGASSER_TEST =
      "TP setter for the degasser test sep stream";
  private static final String MIXING_BEFORE_DEGASSER = "Mixing before the degasser";
  private static final String DEGASSER = "Degasser";
  private static final String TP_SETTER_P43_VD01 = "TP Setter Separator after degasser gas";
  private static final String P43_VD01 = "Separator after degasser gas";
  private static final String TP_SETTER_CFO = "TP setter CFO";
  private static final String SEPARATOR_CFO = "Separator CFO";
  private static final String TP_SETTER_P43_VD02 = "TP Setter Separator after CFU gas";
  private static final String P43_VD02 = "Separator after CFU gas";
  private static final String TP_SETTER_CAISSON = "TP setter Caisson";
  private static final String SEPARATOR_CAISSON = "Separator caisson";

  /**
   * Updates binary interaction parameters (kij) based on salinity for CPA mixing rules. Applies
   * polynomial correlations for CO2, C1-C4 components with water.
   *
   * @param salinity the salinity in mol/L
   * @param fluid the fluid system to update
   */
  private void updateKij(double salinity, SystemInterface fluid) {
    // CO2 kij correlations
    double kijCO2 = -0.0076906 * Math.pow(salinity, 3) + 0.0358275 * Math.pow(salinity, 2)
        - 0.1136315 * salinity + 0.0066297;
    double kijTCO2 = 0.0002690 * salinity + 0.0003547;

    // C1 kij correlations
    double kijC1 = 0.0007756 * Math.pow(salinity, 3) - 0.0034419 * Math.pow(salinity, 2)
        - 0.0449323 * salinity + 0.0330385;
    double kijTC1 = 0.00030404 * salinity + 0.0002578;

    // C2 kij correlations
    double kijC2 = 0.0001983 * Math.pow(salinity, 3) - 0.0014335 * Math.pow(salinity, 2)
        - 0.0274096 * salinity + 0.1157305;
    double kijTC2 = 0.00019606 * salinity + 0.0000233;

    // C3 kij correlations
    double kijC3 = 0.0001672 * Math.pow(salinity, 3) - 0.0011401 * Math.pow(salinity, 2)
        - 0.0249282 * salinity + 0.0427534;
    double kijTC3 = 0.0001695 * salinity + 0.000246;

    // iC4 kij correlations
    double kijIC4 = 0.0001644 * Math.pow(salinity, 3) - 0.0011083 * Math.pow(salinity, 2)
        - 0.0250861 * salinity + 0.0516554;
    double kijTIC4 = 0.0001621 * salinity + 0.0000154;

    // nC4 kij correlations
    double kijNC4 = 0.0001613 * Math.pow(salinity, 3) - 0.0011028 * Math.pow(salinity, 2)
        - 0.0234927 * salinity + 0.0766762;
    double kijTNC4 = 0.0001573 * salinity + 0.0001010;

    // TPflash to initialize phases
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Map of component -> kij value
    Map<String, Double> intParameter = new HashMap<String, Double>();
    intParameter.put(CO2, kijCO2);
    intParameter.put(METHANE, kijC1);
    intParameter.put(ETHANE, kijC2);
    intParameter.put(PROPANE, kijC3);
    intParameter.put(I_BUTANE, kijIC4);
    intParameter.put(N_BUTANE, kijNC4);

    // Map of component -> kijT value
    Map<String, Double> intParameterT = new HashMap<String, Double>();
    intParameterT.put(CO2, kijTCO2);
    intParameterT.put(METHANE, kijTC1);
    intParameterT.put(ETHANE, kijTC2);
    intParameterT.put(PROPANE, kijTC3);
    intParameterT.put(I_BUTANE, kijTIC4);
    intParameterT.put(N_BUTANE, kijTNC4);

    // Get component name list for index lookup
    List<String> componentNames = Arrays.asList(fluid.getComponentNames());
    int waterIndex = componentNames.indexOf(WATER);

    // Set kij on phase[1] (matching the Python code behavior)
    for (Map.Entry<String, Double> entry : intParameter.entrySet()) {
      int compIndex = componentNames.indexOf(entry.getKey());
      ((PhaseEosInterface) fluid.getPhases()[1]).getEosMixingRule()
          .setBinaryInteractionParameter(compIndex, waterIndex, entry.getValue());
    }

    for (Map.Entry<String, Double> entry : intParameterT.entrySet()) {
      int compIndex = componentNames.indexOf(entry.getKey());
      ((PhaseEosInterface) fluid.getPhases()[1]).getEosMixingRule()
          .setBinaryInteractionParameterT1(compIndex, waterIndex, entry.getValue());
    }

    // Re-flash after updating kij
    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid);
    ops2.TPflash();
  }

  /**
   * Calculates the standard gas density at 15 deg C and 1 atm for a given molar mass using the
   * ideal gas law.
   *
   * @param molarMassKgPerMol molar mass in kg/mol
   * @return density in kg/m3 at standard conditions
   */
  private double getStandardDensity(double molarMassKgPerMol) {
    // Ideal gas: density = P * M / (R * T)
    // P = 101325 Pa, T = 288.15 K (15 C), R = 8.314 J/(mol*K)
    return 101325.0 * molarMassKgPerMol / (8.314 * 288.15);
  }

  /**
   * Calculates the total mass flow rate needed so that the aqueous phase flow rate equals the
   * desired water flow rate.
   *
   * @param fluid the fluid system (already flashed)
   * @param waterFlowRateM3Hr desired water flow rate in m3/hr
   * @return total mass flow rate in kg/hr
   */
  private double calculateMassFlowRateFromWaterFlowRate(SystemInterface fluid,
      double waterFlowRateM3Hr) {
    // Run TPflash to get phase fractions
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Get the aqueous volume fraction
    if (!fluid.hasPhaseType("aqueous")) {
      throw new RuntimeException("Fluid has no aqueous phase");
    }

    // Get aqueous phase density
    double aqueousDensity = fluid.getPhase("aqueous").getDensity("kg/m3");

    // Water mass flow rate
    double waterMassFlowRate = waterFlowRateM3Hr * aqueousDensity; // kg/hr

    // Get total mass/aqueous mass ratio
    double totalMoles = fluid.getTotalNumberOfMoles();
    double aqueousMoles = fluid.getPhase("aqueous").getNumberOfMolesInPhase();
    double aqueousMassFraction = (aqueousMoles * fluid.getPhase("aqueous").getMolarMass())
        / (totalMoles * fluid.getMolarMass());

    // Total mass flow rate
    return waterMassFlowRate / aqueousMassFraction;
  }

  /**
   * Creates the process model for the grisebinge field degasser emission calculation.
   *
   * @return the process system
   */
  private ProcessSystem createProcess() {
    SystemInterface fluid1 = new SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    for (int i = 0; i < COMPONENT_NAMES.length; i++) {
      fluid1.addComponent(COMPONENT_NAMES[i], COMPOSITION_1ST[i]);
    }
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);
    fluid1.init(0);

    Stream inletStreamVA01 = new Stream(INLET_STREAM_1ST_SEP, fluid1);
    inletStreamVA01.setTemperature(78.03, "C");
    inletStreamVA01.setPressure(69.75 + 1.01325, "bara");
    inletStreamVA01.setFlowRate(219156.65, "kg/hr");

    ThreePhaseSeparator separatorVA01 = new ThreePhaseSeparator(FIRST_SEPARATOR, inletStreamVA01);

    SystemInterface fluidTestSep = fluid1.clone();
    fluidTestSep.setMolarComposition(COMPOSITION_TEST);

    Stream inletStreamTestSep = new Stream(TEST_SEP_INLET, fluidTestSep);
    inletStreamTestSep.setTemperature(80.82, "C");
    inletStreamTestSep.setPressure(69.74 + 1.01325, "bara");
    inletStreamTestSep.setFlowRate(5515.94, "kg/hr");

    ThreePhaseSeparator testSeparator = new ThreePhaseSeparator(TEST_SEPARATOR, inletStreamTestSep);

    Heater heaterTPSetterMainStream =
        new Heater(TP_SETTER_DEGASSER_MAIN, separatorVA01.getWaterOutStream());
    heaterTPSetterMainStream.setOutletPressure(3.9 + 1.01325, "bara");
    heaterTPSetterMainStream.setOutletTemperature(74.42, "C");

    Heater heaterTPSetterTestStream =
        new Heater(TP_SETTER_DEGASSER_TEST, testSeparator.getWaterOutStream());
    heaterTPSetterTestStream.setOutletPressure(3.9 + 1.01325, "bara");
    heaterTPSetterTestStream.setOutletTemperature(74.42, "C");

    StaticMixer mixingDegasser = new StaticMixer(MIXING_BEFORE_DEGASSER);
    mixingDegasser.addStream(heaterTPSetterMainStream.getOutletStream());
    mixingDegasser.addStream(heaterTPSetterTestStream.getOutletStream());

    ThreePhaseSeparator degasser =
        new ThreePhaseSeparator(DEGASSER, mixingDegasser.getOutletStream());

    Heater heaterTPSetterP43VD01 = new Heater(TP_SETTER_P43_VD01, degasser.getGasOutStream());
    heaterTPSetterP43VD01.setOutletPressure(1.01325, "bara");
    heaterTPSetterP43VD01.setOutletTemperature(50.0, "C");

    ThreePhaseSeparator separatorP43VD01 =
        new ThreePhaseSeparator(P43_VD01, heaterTPSetterP43VD01.getOutletStream());

    Heater heaterTPSetterCFO = new Heater(TP_SETTER_CFO, degasser.getWaterOutStream());
    heaterTPSetterCFO.setOutletPressure(3.0 + 1.01325, "bara");
    heaterTPSetterCFO.setOutletTemperature(70.0, "C");

    Separator separatorCFO = new Separator(SEPARATOR_CFO, heaterTPSetterCFO.getOutletStream());

    Heater heaterTPSetterP43VD02 = new Heater(TP_SETTER_P43_VD02, separatorCFO.getGasOutStream());
    heaterTPSetterP43VD02.setOutletPressure(1.01325, "bara");
    heaterTPSetterP43VD02.setOutletTemperature(50.0, "C");

    ThreePhaseSeparator separatorP43VD02 =
        new ThreePhaseSeparator(P43_VD02, heaterTPSetterP43VD02.getOutletStream());

    Heater heaterTPSetterCaisson = new Heater(TP_SETTER_CAISSON, separatorCFO.getLiquidOutStream());
    heaterTPSetterCaisson.setOutletTemperature(70, "C");
    heaterTPSetterCaisson.setOutletPressure(1.0 + 1.01325, "bara");

    Separator separatorCaisson =
        new Separator(SEPARATOR_CAISSON, heaterTPSetterCaisson.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(inletStreamVA01);
    process.add(separatorVA01);
    process.add(inletStreamTestSep);
    process.add(testSeparator);
    process.add(heaterTPSetterMainStream);
    process.add(heaterTPSetterTestStream);
    process.add(mixingDegasser);
    process.add(degasser);
    process.add(heaterTPSetterP43VD01);
    process.add(separatorP43VD01);
    process.add(heaterTPSetterCFO);
    process.add(separatorCFO);
    process.add(heaterTPSetterP43VD02);
    process.add(separatorP43VD02);
    process.add(heaterTPSetterCaisson);
    process.add(separatorCaisson);

    return process;
  }

  /**
   * Tests the degasser salt water process with specific input parameters matching the grisebinge
   * field configuration. Validates key output values including gas flow rates, water flow rates,
   * compositions, and emission quantities from degasser, CFO, and caisson units.
   */
  @Test
  public void testDegasserSaltWaterProcess() {
    // Input parameters
    double inletPressureVA01 = 69.11325; // bara
    double inletTemperatureVA01 = 83.4; // C
    double flowRateWater = 84.3; // m3/hr
    double pressureTestSep = 69.11325; // bara
    double temperatureTestSep = 87.7; // C
    double waterFlowRateTestSep = 0.1; // m3/hr
    double pressureDegasser = 5.01325; // bara
    double temperatureDegasser = 84.5; // C
    double pressureSepP43VD01 = 1.01325; // bara
    double temperatureSepP43VD01 = 50.0; // C
    double cfoPressure = 2.0; // bara
    double cfoTemperature = 80.0; // C
    double pressureSepP43VD02 = 1.01325; // bara
    double temperatureSepP43VD02 = 50.0; // C
    double caissonTemperature = 60.0; // C
    double caissonPressure = 1.2; // bara
    double salinityFirstStage = 1.6; // mol/L
    double salinityTest = 1.6; // mol/L

    // Create process
    ProcessSystem process = createProcess();

    // Configure 1st separator inlet stream
    Stream inletStream1st = (Stream) process.getUnit(INLET_STREAM_1ST_SEP);
    inletStream1st.setTemperature(inletTemperatureVA01, "C");
    inletStream1st.setPressure(inletPressureVA01, "bara");
    inletStream1st.getFluid().setMolarComposition(COMPOSITION_1ST);
    updateKij(salinityFirstStage, inletStream1st.getFluid());
    inletStream1st.run();

    double totalMassFlowRate1st =
        calculateMassFlowRateFromWaterFlowRate(inletStream1st.getFluid(), flowRateWater);
    inletStream1st.setFlowRate(totalMassFlowRate1st, "kg/hr");

    // Run 1st separator
    ((ThreePhaseSeparator) process.getUnit(FIRST_SEPARATOR)).run();

    // Configure test separator inlet stream
    Stream testSepInlet = (Stream) process.getUnit(TEST_SEP_INLET);
    testSepInlet.setTemperature(temperatureTestSep, "C");
    testSepInlet.setPressure(pressureTestSep, "bara");
    testSepInlet.getFluid().setMolarComposition(COMPOSITION_TEST);
    updateKij(salinityTest, testSepInlet.getFluid());
    testSepInlet.run();

    double totalMassFlowRateTest =
        calculateMassFlowRateFromWaterFlowRate(testSepInlet.getFluid(), waterFlowRateTestSep);
    testSepInlet.setFlowRate(totalMassFlowRateTest, "kg/hr");

    // Run test separator
    ((ThreePhaseSeparator) process.getUnit(TEST_SEPARATOR)).run();

    // Configure and run degasser TP setters
    Heater tpSetterMain = (Heater) process.getUnit(TP_SETTER_DEGASSER_MAIN);
    tpSetterMain.setOutletPressure(pressureDegasser, "bara");
    tpSetterMain.setOutletTemperature(temperatureDegasser, "C");
    tpSetterMain.run();

    Heater tpSetterTest = (Heater) process.getUnit(TP_SETTER_DEGASSER_TEST);
    tpSetterTest.setOutletPressure(pressureDegasser, "bara");
    tpSetterTest.setOutletTemperature(temperatureDegasser, "C");
    tpSetterTest.run();

    // Calculate mixed salinity
    double flowMain = tpSetterMain.getOutletStream().getFlowRate("m3/hr") * 1000.0;
    double flowTest = tpSetterTest.getOutletStream().getFlowRate("m3/hr") * 1000.0;
    double mixedSalinity =
        (salinityFirstStage * flowMain + salinityTest * flowTest) / (flowMain + flowTest);

    // Update kij with mixed salinity
    updateKij(mixedSalinity, tpSetterMain.getOutletStream().getFluid());
    updateKij(mixedSalinity, tpSetterTest.getOutletStream().getFluid());

    // Run mixer and degasser
    ((StaticMixer) process.getUnit(MIXING_BEFORE_DEGASSER)).run();
    ((ThreePhaseSeparator) process.getUnit(DEGASSER)).run();

    // Configure and run P-43-VD01
    Heater tpSetterVD01 = (Heater) process.getUnit(TP_SETTER_P43_VD01);
    tpSetterVD01.setOutletPressure(pressureSepP43VD01, "bara");
    tpSetterVD01.setOutletTemperature(temperatureSepP43VD01, "C");
    tpSetterVD01.run();
    ((ThreePhaseSeparator) process.getUnit(P43_VD01)).run();

    // Configure and run CFO
    Heater tpSetterCFO = (Heater) process.getUnit(TP_SETTER_CFO);
    tpSetterCFO.setOutletPressure(cfoPressure, "bara");
    tpSetterCFO.setOutletTemperature(cfoTemperature, "C");
    tpSetterCFO.run();
    ((Separator) process.getUnit(SEPARATOR_CFO)).run();

    // Configure and run P-43-VD02
    Heater tpSetterVD02 = (Heater) process.getUnit(TP_SETTER_P43_VD02);
    tpSetterVD02.setOutletPressure(pressureSepP43VD02, "bara");
    tpSetterVD02.setOutletTemperature(temperatureSepP43VD02, "C");
    tpSetterVD02.run();
    ((ThreePhaseSeparator) process.getUnit(P43_VD02)).run();

    // Configure and run Caisson
    Heater tpSetterCaisson = (Heater) process.getUnit(TP_SETTER_CAISSON);
    tpSetterCaisson.setOutletTemperature(caissonTemperature, "C");
    tpSetterCaisson.setOutletPressure(caissonPressure, "bara");
    tpSetterCaisson.run();
    ((Separator) process.getUnit(SEPARATOR_CAISSON)).run();

    // ===== Extract results =====
    ThreePhaseSeparator degasser = (ThreePhaseSeparator) process.getUnit(DEGASSER);
    ThreePhaseSeparator firstSep = (ThreePhaseSeparator) process.getUnit(FIRST_SEPARATOR);
    ThreePhaseSeparator testSep = (ThreePhaseSeparator) process.getUnit(TEST_SEPARATOR);
    Separator cfSeparator = (Separator) process.getUnit(SEPARATOR_CFO);
    Separator caissonSep = (Separator) process.getUnit(SEPARATOR_CAISSON);
    ThreePhaseSeparator sepVD01 = (ThreePhaseSeparator) process.getUnit(P43_VD01);
    ThreePhaseSeparator sepVD02 = (ThreePhaseSeparator) process.getUnit(P43_VD02);

    // Degasser gas and water flow rates
    double gasFlowRateDegasserKgHr = degasser.getGasOutStream().getFlowRate("kg/hr");
    double gasFlowRateDegasserSm3Hr = degasser.getGasOutStream().getFlowRate("Sm3/hr");
    double waterFlowRateDegasserM3Hr = degasser.getWaterOutStream().getFlowRate("m3/hr");
    double ratioGasWater = gasFlowRateDegasserKgHr / waterFlowRateDegasserM3Hr;

    // First separator standard flow rates
    double gasFlowRate1stSm3Hr = firstSep.getGasOutStream().getFlowRate("Sm3/hr");
    double oilFlowRate1stSm3Hr = firstSep.getOilOutStream().getFlowRate("Sm3/hr");
    double waterFlowRate1stSm3Hr = firstSep.getWaterOutStream().getFlowRate("Sm3/hr");

    // Test separator standard flow rates
    double gasFlowRateTestSm3Hr = testSep.getGasOutStream().getFlowRate("Sm3/hr");
    double oilFlowRateTestSm3Hr = testSep.getOilOutStream().getFlowRate("Sm3/hr");
    double waterFlowRateTestSm3Hr = testSep.getWaterOutStream().getFlowRate("Sm3/hr");

    // Degasser gas composition (molar fractions)
    double degasserCO2Frac = degasser.getGasOutStream().getFluid().getComponent(CO2).getz();
    double degasserCH4Frac = degasser.getGasOutStream().getFluid().getComponent(METHANE).getz();
    double degasserH2OFrac = degasser.getGasOutStream().getFluid().getComponent(WATER).getz();
    double degasserN2Frac = degasser.getGasOutStream().getFluid().getComponent(NITROGEN).getz();
    double degasserNMVOCFrac =
        1.0 - (degasserCO2Frac + degasserCH4Frac + degasserH2OFrac + degasserN2Frac);

    // Degasser gas mass fractions
    double degasserCO2MassFrac = 0.0;
    double degasserCH4MassFrac = 0.0;
    double degasserH2OMassFrac = 0.0;
    double degasserN2MassFrac = 0.0;
    double degasserNMVOCMassFrac = 0.0;

    if (degasser.getGasOutStream().getFluid().hasPhaseType("gas")) {
      degasserCO2MassFrac = degasser.getGasOutStream().getFluid().getPhase("gas").getWtFrac(CO2);
      degasserCH4MassFrac =
          degasser.getGasOutStream().getFluid().getPhase("gas").getWtFrac(METHANE);
      degasserH2OMassFrac = degasser.getGasOutStream().getFluid().getPhase("gas").getWtFrac(WATER);
      degasserN2MassFrac =
          degasser.getGasOutStream().getFluid().getPhase("gas").getWtFrac(NITROGEN);
      degasserNMVOCMassFrac = 1.0
          - (degasserCO2MassFrac + degasserCH4MassFrac + degasserH2OMassFrac + degasserN2MassFrac);
    }

    // Degasser component flow rates (kg/hr)
    double degasserCO2KgHr = gasFlowRateDegasserKgHr * degasserCO2MassFrac;
    double degasserCH4KgHr = gasFlowRateDegasserKgHr * degasserCH4MassFrac;
    double degasserH2OKgHr = gasFlowRateDegasserKgHr * degasserH2OMassFrac;
    double degasserN2KgHr = gasFlowRateDegasserKgHr * degasserN2MassFrac;
    double degasserNMVOCKgHr = gasFlowRateDegasserKgHr * degasserNMVOCMassFrac;

    // Degasser component flow rates (Sm3/hr) using standard density
    double degasserCO2Sm3Hr = degasserCO2KgHr / getStandardDensity(
        degasser.getGasOutStream().getFluid().getComponent(CO2).getMolarMass());
    double degasserCH4Sm3Hr = degasserCH4KgHr / getStandardDensity(
        degasser.getGasOutStream().getFluid().getComponent(METHANE).getMolarMass());
    double degasserH2OSm3Hr = degasserH2OKgHr / getStandardDensity(
        degasser.getGasOutStream().getFluid().getComponent(WATER).getMolarMass());
    double degasserN2Sm3Hr = degasserN2KgHr / getStandardDensity(
        degasser.getGasOutStream().getFluid().getComponent(NITROGEN).getMolarMass());
    double degasserNMVOCSm3Hr = gasFlowRateDegasserSm3Hr
        - (degasserCO2Sm3Hr + degasserCH4Sm3Hr + degasserH2OSm3Hr + degasserN2Sm3Hr);

    // CFO gas flow rates
    double cfoGasFlowKgHr = cfSeparator.getGasOutStream().getFlowRate("kg/hr");
    double caissonGasFlowKgHr = caissonSep.getGasOutStream().getFlowRate("kg/hr");

    // P-43-VD01 results
    double sepVD01GasFlowKgHr = sepVD01.getGasOutStream().getFlowRate("kg/hr");
    double sepVD01GasFlowSm3Hr = sepVD01.getGasOutStream().getFlowRate("Sm3/hr");
    double sepVD01GasDensity = sepVD01.getGasOutStream().getFluid().getDensity();
    double sepVD01MolarMass = sepVD01.getGasOutStream().getFluid().getMolarMass() * 1000.0;
    double sepVD01WaterFlowM3Hr = sepVD01.getWaterOutStream().getFlowRate("m3/hr");

    // P-43-VD02 results
    double sepVD02GasFlowKgHr = sepVD02.getGasOutStream().getFlowRate("kg/hr");
    double sepVD02GasFlowSm3Hr = sepVD02.getGasOutStream().getFlowRate("Sm3/hr");
    double sepVD02GasDensity = sepVD02.getGasOutStream().getFluid().getDensity();
    double sepVD02MolarMass = sepVD02.getGasOutStream().getFluid().getMolarMass() * 1000.0;
    double sepVD02WaterFlowM3Hr = sepVD02.getWaterOutStream().getFlowRate("m3/hr");

    // Aqueous densities
    double aqueousDensityFirstStage = firstSep.getWaterOutStream().getFluid().getDensity("kg/m3");
    double aqueousDensityTest = testSep.getWaterOutStream().getFluid().getDensity("kg/m3");

    // ===== Assertions =====
    // Verify degasser gas flow rate is positive and reasonable
    Assertions.assertTrue(gasFlowRateDegasserKgHr > 0.01,
        "Degasser gas flow rate should be positive, got: " + gasFlowRateDegasserKgHr);
    Assertions.assertTrue(gasFlowRateDegasserKgHr < 1000.0,
        "Degasser gas flow rate should be < 1000 kg/hr, got: " + gasFlowRateDegasserKgHr);

    // Verify water flow rate from degasser
    Assertions.assertTrue(waterFlowRateDegasserM3Hr > 0.01,
        "Degasser water flow rate should be positive, got: " + waterFlowRateDegasserM3Hr);
    Assertions.assertTrue(waterFlowRateDegasserM3Hr < 1000.0,
        "Degasser water flow rate should be < 1000 m3/hr, got: " + waterFlowRateDegasserM3Hr);

    // Verify gas-to-water ratio
    Assertions.assertTrue(ratioGasWater > 0.01,
        "Gas-to-water ratio should be > 0.01, got: " + ratioGasWater);
    Assertions.assertTrue(ratioGasWater < 10.0,
        "Gas-to-water ratio should be < 10, got: " + ratioGasWater);

    // Verify degasser gas composition fractions sum approximately to 1
    double totalMolarFrac =
        degasserCO2Frac + degasserCH4Frac + degasserH2OFrac + degasserN2Frac + degasserNMVOCFrac;
    Assertions.assertEquals(1.0, totalMolarFrac, 1e-6,
        "Degasser gas molar fractions should sum to 1.0");

    // Verify mass fractions sum approximately to 1
    double totalMassFrac = degasserCO2MassFrac + degasserCH4MassFrac + degasserH2OMassFrac
        + degasserN2MassFrac + degasserNMVOCMassFrac;
    Assertions.assertEquals(1.0, totalMassFrac, 1e-6,
        "Degasser gas mass fractions should sum to 1.0");

    // Verify CO2 is a significant component in degasser gas
    Assertions.assertTrue(degasserCO2Frac > 0.01,
        "CO2 mole fraction in degasser gas should be > 1%, got: " + degasserCO2Frac);

    // Verify methane is present in degasser gas
    Assertions.assertTrue(degasserCH4Frac > 0.01,
        "Methane mole fraction in degasser gas should be > 1%, got: " + degasserCH4Frac);

    // Verify CFO gas flow rate is positive
    Assertions.assertTrue(cfoGasFlowKgHr > 0.0,
        "CFO gas flow rate should be positive, got: " + cfoGasFlowKgHr);
    Assertions.assertTrue(cfoGasFlowKgHr < 1000.0,
        "CFO gas flow rate should be < 1000 kg/hr, got: " + cfoGasFlowKgHr);

    // Verify caisson gas flow rate is positive
    Assertions.assertTrue(caissonGasFlowKgHr > 0.01,
        "Caisson gas flow rate should be positive, got: " + caissonGasFlowKgHr);
    Assertions.assertTrue(caissonGasFlowKgHr < 1000.0,
        "Caisson gas flow rate should be < 1000 kg/hr, got: " + caissonGasFlowKgHr);

    // Verify P-43-VD01 results
    Assertions.assertTrue(sepVD01GasFlowKgHr > 0.0,
        "P-43-VD01 gas flow should be positive, got: " + sepVD01GasFlowKgHr);
    Assertions.assertTrue(sepVD01GasFlowSm3Hr > 0.0,
        "P-43-VD01 gas Sm3 flow should be positive, got: " + sepVD01GasFlowSm3Hr);

    // Verify P-43-VD02 results
    Assertions.assertTrue(sepVD02GasFlowKgHr > 0.0,
        "P-43-VD02 gas flow should be positive, got: " + sepVD02GasFlowKgHr);
    Assertions.assertTrue(sepVD02GasFlowSm3Hr > 0.0,
        "P-43-VD02 gas Sm3 flow should be positive, got: " + sepVD02GasFlowSm3Hr);

    // Verify aqueous densities are reasonable for salt water
    Assertions.assertTrue(aqueousDensityFirstStage > 950.0,
        "Aqueous density should be > 950 kg/m3, got: " + aqueousDensityFirstStage);
    Assertions.assertTrue(aqueousDensityFirstStage < 1200.0,
        "Aqueous density should be < 1200 kg/m3, got: " + aqueousDensityFirstStage);
    Assertions.assertTrue(aqueousDensityTest > 950.0,
        "Test aqueous density should be > 950 kg/m3, got: " + aqueousDensityTest);
    Assertions.assertTrue(aqueousDensityTest < 1200.0,
        "Test aqueous density should be < 1200 kg/m3, got: " + aqueousDensityTest);

    // Verify first separator gas is reasonable
    Assertions.assertTrue(gasFlowRate1stSm3Hr > 0.0,
        "1st separator gas flow should be positive, got: " + gasFlowRate1stSm3Hr);

    // Verify component Sm3/hr calculations are consistent
    double totalDegasserSm3Hr = degasserCO2Sm3Hr + degasserCH4Sm3Hr + degasserH2OSm3Hr
        + degasserN2Sm3Hr + degasserNMVOCSm3Hr;
    Assertions.assertEquals(gasFlowRateDegasserSm3Hr, totalDegasserSm3Hr, 0.5,
        "Sum of component Sm3/hr should equal total Sm3/hr");

    // Print key results for debugging/verification
    System.out.println("===== Degasser Salt Water Process Results =====");
    System.out.println("Degasser gas flow rate: " + gasFlowRateDegasserKgHr + " kg/hr");
    System.out.println("Degasser gas flow rate: " + gasFlowRateDegasserSm3Hr + " Sm3/hr");
    System.out.println("Degasser water flow rate: " + waterFlowRateDegasserM3Hr + " m3/hr");
    System.out.println("Gas-to-water ratio: " + ratioGasWater + " kg/m3");
    System.out.println("Degasser CO2 mole fraction: " + degasserCO2Frac);
    System.out.println("Degasser CH4 mole fraction: " + degasserCH4Frac);
    System.out.println("Degasser H2O mole fraction: " + degasserH2OFrac);
    System.out.println("Degasser N2 mole fraction: " + degasserN2Frac);
    System.out.println("Degasser NMVOC mole fraction: " + degasserNMVOCFrac);
    System.out.println("Degasser CO2 mass fraction: " + degasserCO2MassFrac);
    System.out.println("Degasser CH4 mass fraction: " + degasserCH4MassFrac);
    System.out.println("CFO gas flow rate: " + cfoGasFlowKgHr + " kg/hr");
    System.out.println("Caisson gas flow rate: " + caissonGasFlowKgHr + " kg/hr");
    System.out.println("P-43-VD01 gas flow rate: " + sepVD01GasFlowKgHr + " kg/hr");
    System.out.println("P-43-VD01 gas flow rate: " + sepVD01GasFlowSm3Hr + " Sm3/hr");
    System.out.println("P-43-VD01 gas density: " + sepVD01GasDensity + " kg/m3");
    System.out.println("P-43-VD01 molar mass: " + sepVD01MolarMass + " g/mol");
    System.out.println("P-43-VD02 gas flow rate: " + sepVD02GasFlowKgHr + " kg/hr");
    System.out.println("P-43-VD02 gas flow rate: " + sepVD02GasFlowSm3Hr + " Sm3/hr");
    System.out.println("1st sep gas: " + gasFlowRate1stSm3Hr + " Sm3/hr");
    System.out.println("1st sep oil: " + oilFlowRate1stSm3Hr + " Sm3/hr");
    System.out.println("1st sep water: " + waterFlowRate1stSm3Hr + " Sm3/hr");
    System.out.println("Test sep gas: " + gasFlowRateTestSm3Hr + " Sm3/hr");
    System.out.println("Test sep oil: " + oilFlowRateTestSm3Hr + " Sm3/hr");
    System.out.println("Test sep water: " + waterFlowRateTestSm3Hr + " Sm3/hr");
    System.out.println("Aqueous density 1st stage: " + aqueousDensityFirstStage + " kg/m3");
    System.out.println("Aqueous density test: " + aqueousDensityTest + " kg/m3");
    System.out.println("Degasser CO2 kg/hr: " + degasserCO2KgHr + ", Sm3/hr: " + degasserCO2Sm3Hr);
    System.out.println("Degasser CH4 kg/hr: " + degasserCH4KgHr + ", Sm3/hr: " + degasserCH4Sm3Hr);
    System.out.println("Degasser H2O kg/hr: " + degasserH2OKgHr + ", Sm3/hr: " + degasserH2OSm3Hr);
    System.out.println("Degasser N2 kg/hr: " + degasserN2KgHr + ", Sm3/hr: " + degasserN2Sm3Hr);
    System.out
        .println("Degasser NMVOC kg/hr: " + degasserNMVOCKgHr + ", Sm3/hr: " + degasserNMVOCSm3Hr);
  }
}
