package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression test mirroring examples/notebooks/column_study.py.
 *
 * @author Copilot
 * @version 1.0
 */
public class ColumnStudyRegressionTest {
  /** Atmospheric pressure used to convert bara to barg. */
  private static final double ATM_BARA = 1.01325;
  /** Number of answer trays excluding the reboiler. */
  private static final int NUMBER_OF_TRAYS = 10;
  /** Main feed mass flow in kg/hr. */
  private static final double MAIN_FEED_MASS_FLOW_KG_HR = 99381.1038920480;
  /** Top reflux feed mass flow in kg/hr. */
  private static final double TOP_FEED_MASS_FLOW_KG_HR = 7658.93041734027;
  /** Main feed temperature in degrees Celsius. */
  private static final double MAIN_FEED_TEMPERATURE_C = 77.0000001251743;
  /** Top reflux feed temperature in degrees Celsius. */
  private static final double TOP_FEED_TEMPERATURE_C = 32.14;
  /** Main feed pressure in bara. */
  private static final double MAIN_FEED_PRESSURE_BARA = 5.21325;
  /** Top reflux feed pressure in bara. */
  private static final double TOP_FEED_PRESSURE_BARA = 4.71325;
  /** Column top pressure in bara. */
  private static final double TOP_PRESSURE_BARA = 5.01325;
  /** Column bottom pressure in bara using the answer tray-pressure convention. */
  private static final double BOTTOM_PRESSURE_BARA = 5.06325;
  /** Fixed reboiler temperature in degrees Celsius. */
  private static final double REBOILER_TEMPERATURE_C = 137.309085069090;
  /** Default Murphree efficiency for trays. */
  private static final double TRAY_EFFICIENCY = 0.9;
  /** Reboiler stage efficiency. */
  private static final double REBOILER_EFFICIENCY = 1.0;
  /** Temperature tolerance against the answer tray profile in degrees Celsius. */
  private static final double TEMPERATURE_PROFILE_TOLERANCE_C = 3.5;
  /** Pressure profile tolerance in barg. */
  private static final double PRESSURE_PROFILE_TOLERANCE_BARG = 1.0e-6;
  /** Overall mass balance tolerance in kg/hr. */
  private static final double TOTAL_MASS_BALANCE_TOLERANCE_KG_HR = 1.0e-3;
  /** Per-component mass balance tolerance in kg/hr. */
  private static final double COMPONENT_MASS_BALANCE_TOLERANCE_KG_HR = 1.0e-3;

  /** Names of components as referenced by the column study composition arrays. */
  private static final String[] COMPONENT_NAMES = {"H2S", "H2O", "Nitrogen", "CO2", "Methane",
      "Ethane", "Propane", "i-Butane", "n-Butane", "i-Pentane", "n-Pentane", "C6*", "C7*",
      "C8*", "C9*", "C10-C12*", "C13-C14*", "C15-C16*", "C17-C19*", "C20-C22*",
      "C23-C25*", "C26-C30*", "C31-C38*", "C39-C80*"};

  /** NeqSim component names matching {@link #COMPONENT_NAMES}. */
  private static final String[] NEQSIM_COMPONENT_NAMES = {"H2S", "water", "nitrogen", "CO2",
      "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane"};

  /** Pseudo-component molar masses in kg/mol. */
  private static final double[] PSEUDO_MOLAR_MASSES_KG_PER_MOL = {0.08617800140380859,
      0.0909560012817383, 0.103429000854492, 0.117186996459961, 0.145809005737305,
      0.181330001831055, 0.21227799987793, 0.248141998291016, 0.289217010498047,
      0.330338989257813, 0.384696990966797, 0.471157989501953, 0.6624600219726561};

  /** Pseudo-component densities in kg/m3. */
  private static final double[] PSEUDO_DENSITIES_KG_PER_M3 = {0.6626640014648439,
      0.740698486328125, 0.769004028320313, 0.789065673828125, 0.8048148193359379,
      0.825066711425781, 0.8377041015625, 0.849904113769531, 0.863837097167969,
      0.8755130004882811, 0.8886063232421879, 0.9061005249023439, 0.936200378417969};

  /** Main feed molar composition in column_study.py component order. */
  private static final double[] MAIN_FEED_COMPOSITION = {0.0, 1.26975950126355e-03,
      3.88734329545213e-06, 2.03669541112211e-03, 8.35885649596034e-03,
      0.030312967680537, 9.83075308994837e-02, 4.09665694460258e-02,
      0.114510205790434, 0.060313250815548, 7.73190146573562e-02,
      0.104982256950121, 0.139005591552077, 0.127908100975965,
      6.20910685541127e-02, 6.65500502353172e-02, 0.020235118084271,
      1.25829097167123e-02, 0.011709984585876, 7.11881671769593e-03,
      4.57627195654846e-03, 4.50555256022543e-03, 3.25896678227350e-03,
      2.07657328777843e-03};

  /** Top reflux feed molar composition in column_study.py component order. */
  private static final double[] TOP_FEED_COMPOSITION = {0.0, 4.35105155095748e-04,
      7.63046322451461e-07, 7.26662709595144e-04, 2.18297869906758e-03,
      1.65679049317917e-02, 0.121425832401003, 9.52769636340267e-02,
      0.306895179064482, 0.160387157274294, 0.192815999863345,
      7.08381536172843e-02, 2.29352443485453e-02, 7.52373948573269e-03,
      1.57073918334608e-03, 3.72237726886924e-04, 3.00684238117462e-05,
      1.06371993923885e-05, 4.12319121029786e-06, 4.63134876824379e-07,
      4.37268256091263e-08, 3.14276050239541e-09, 4.02814694571035e-11,
      2.31802265756544e-14};

  /** Answer tray temperatures from top tray to bottom tray in degrees Celsius. */
  private static final double[] ANSWER_TEMPERATURE_C_TOPDOWN = {55.0352092263182,
      60.4943624688327, 65.1027206540858, 70.7739536320417, 82.2452891411628,
      88.1350583791952, 93.3124258992463, 98.4324503710853, 104.962736678313,
      115.150145109534};

  /** Answer tray pressure profile from top tray to bottom tray in barg. */
  private static final double[] ANSWER_PRESSURE_BARG_TOPDOWN = {4.0, 4.00555555555556,
      4.01111111111111, 4.01666666666667, 4.02222222222222, 4.02777777777778,
      4.03333333333333, 4.03888888888889, 4.04444444444444, 4.05};

  /**
   * Runs the column-study case and verifies tray profiles plus total and component mass closure.
   */
  @Test
  public void columnStudyCaseMatchesProfileAndClosesMassBalances() {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("manual_column_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("top_stage_reflux", baseFluid,
        TOP_FEED_COMPOSITION, TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA,
        TOP_FEED_MASS_FLOW_KG_HR);

    DistillationColumn column = createColumn(feedStream, topFeedStream);
    column.run();

    assertTrue(column.solved(), "Column-study case should converge with Naphtali-Sandholm");
    assertEquals(17, column.getLastIterationCount(),
        "Newton iteration count guards against premature SR acceptance");
    assertTrayTemperatureProfile(column);
    assertTrayPressureProfile(column);
    assertOverallMassBalance(feedStream, topFeedStream, column);
    assertComponentMassBalances(feedStream, topFeedStream, column);
  }

  /**
   * Create the base fluid used by column_study.py.
   *
   * @return base SRK fluid with light components and TBP pseudo-components
   */
  private SystemInterface createBaseFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 15.0, ATM_BARA);
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    for (int componentIndex = 0; componentIndex < NEQSIM_COMPONENT_NAMES.length; componentIndex++) {
      fluid.addComponent(NEQSIM_COMPONENT_NAMES[componentIndex], 1.0e-10);
    }
    for (int pseudoIndex = 0; pseudoIndex < PSEUDO_MOLAR_MASSES_KG_PER_MOL.length; pseudoIndex++) {
      String componentName = COMPONENT_NAMES[NEQSIM_COMPONENT_NAMES.length + pseudoIndex];
      fluid.addTBPfraction(componentName, 1.0e-10, PSEUDO_MOLAR_MASSES_KG_PER_MOL[pseudoIndex],
          PSEUDO_DENSITIES_KG_PER_M3[pseudoIndex]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * Create one feed stream from a base fluid clone.
   *
   * @param name stream name
   * @param baseFluid base fluid to clone
   * @param molarComposition molar composition in component order
   * @param temperatureC stream temperature in degrees Celsius
   * @param pressureBara stream pressure in bara
   * @param massFlowKgPerHour stream mass flow in kg/hr
   * @return configured and run stream
   */
  private StreamInterface createStream(String name, SystemInterface baseFluid,
      double[] molarComposition, double temperatureC, double pressureBara,
      double massFlowKgPerHour) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setMolarComposition(normalizeComposition(molarComposition));
    fluid.setTemperature(temperatureC, "C");
    fluid.setPressure(pressureBara, "bara");
    fluid.init(0);

    Stream stream = new Stream(name, fluid);
    stream.setTemperature(temperatureC, "C");
    stream.setPressure(pressureBara, "bara");
    stream.setFlowRate(massFlowKgPerHour, "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Normalize a molar composition array.
   *
   * @param composition unnormalized molar composition
   * @return normalized molar composition with zero entries lifted to a small positive value
   */
  private double[] normalizeComposition(double[] composition) {
    double[] normalizedComposition = new double[composition.length];
    double sum = 0.0;
    for (int componentIndex = 0; componentIndex < composition.length; componentIndex++) {
      normalizedComposition[componentIndex] = Math.max(composition[componentIndex], 1.0e-100);
      sum += normalizedComposition[componentIndex];
    }
    for (int componentIndex = 0; componentIndex < normalizedComposition.length; componentIndex++) {
      normalizedComposition[componentIndex] /= sum;
    }
    return normalizedComposition;
  }

  /**
   * Create and configure the column-study distillation column.
   *
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @return configured column ready to run
   */
  private DistillationColumn createColumn(StreamInterface feedStream, StreamInterface topFeedStream) {
    DistillationColumn column = new DistillationColumn("20VE105_205_standalone", NUMBER_OF_TRAYS,
        true, false);
    column.addFeedStream(feedStream, answerTrayToNeqSimStage(5));
    column.addFeedStream(topFeedStream, answerTrayToNeqSimStage(1));
    column.setTopPressure(TOP_PRESSURE_BARA);
    column.setBottomPressure(getCompensatedBottomPressure());
    column.getReboiler().setOutletTemperature(273.15 + REBOILER_TEMPERATURE_C);
    column.setMurphreeEfficiency(TRAY_EFFICIENCY);
    column.setMurphreeEfficiency(0, REBOILER_EFFICIENCY);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    column.setMaxNumberOfIterations(300);
    return column;
  }

  /**
   * Convert answer top-down tray numbering to NeqSim bottom-up stage numbering.
   *
   * @param answerTray answer tray number, where one is the top tray
   * @return NeqSim stage number
   */
  private int answerTrayToNeqSimStage(int answerTray) {
    return (NUMBER_OF_TRAYS + 1) - answerTray;
  }

  /**
   * Compensate bottom pressure so NeqSim tray interpolation matches the answer tray profile.
   *
   * @return compensated bottom pressure in bara
   */
  private double getCompensatedBottomPressure() {
    return TOP_PRESSURE_BARA
        + (BOTTOM_PRESSURE_BARA - TOP_PRESSURE_BARA) * (NUMBER_OF_TRAYS / 9.0);
  }

  /**
   * Assert the solved tray temperature profile against the answer profile.
   *
   * @param column solved column
   */
  private void assertTrayTemperatureProfile(DistillationColumn column) {
    for (int answerTray = 1; answerTray <= NUMBER_OF_TRAYS; answerTray++) {
      int stage = answerTrayToNeqSimStage(answerTray);
      double actualTemperatureC = column.getTray(stage).getTemperature() - 273.15;
      assertEquals(ANSWER_TEMPERATURE_C_TOPDOWN[answerTray - 1], actualTemperatureC,
          TEMPERATURE_PROFILE_TOLERANCE_C,
          "temperature profile mismatch at answer tray " + answerTray);
    }
  }

  /**
   * Assert the solved tray pressure profile against the answer profile.
   *
   * @param column solved column
   */
  private void assertTrayPressureProfile(DistillationColumn column) {
    for (int answerTray = 1; answerTray <= NUMBER_OF_TRAYS; answerTray++) {
      int stage = answerTrayToNeqSimStage(answerTray);
      double actualPressureBarg = column.getTray(stage).getPressure() - ATM_BARA;
      assertEquals(ANSWER_PRESSURE_BARG_TOPDOWN[answerTray - 1], actualPressureBarg,
          PRESSURE_PROFILE_TOLERANCE_BARG, "pressure profile mismatch at answer tray " + answerTray);
    }
  }

  /**
   * Assert overall column mass balance closure.
   *
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @param column solved column
   */
  private void assertOverallMassBalance(StreamInterface feedStream, StreamInterface topFeedStream,
      DistillationColumn column) {
    double totalFeedMassFlow = feedStream.getFlowRate("kg/hr") + topFeedStream.getFlowRate("kg/hr");
    double totalProductMassFlow = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr");
    assertEquals(totalFeedMassFlow, totalProductMassFlow, TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
        "overall product mass balance should close");
    assertEquals(0.0, column.getMassBalance("kg/hr"), TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
        "column mass balance helper should close");
  }

  /**
   * Assert per-component mass balance closure over external feeds and terminal products.
   *
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @param column solved column
   */
  private void assertComponentMassBalances(StreamInterface feedStream, StreamInterface topFeedStream,
      DistillationColumn column) {
    SystemInterface feedFluid = feedStream.getThermoSystem();
    SystemInterface topFeedFluid = topFeedStream.getThermoSystem();
    SystemInterface overheadFluid = column.getGasOutStream().getThermoSystem();
    SystemInterface bottomsFluid = column.getLiquidOutStream().getThermoSystem();

    for (int componentIndex = 0; componentIndex < COMPONENT_NAMES.length; componentIndex++) {
      double componentIn = getComponentMassFlowKgPerHour(feedFluid, componentIndex)
          + getComponentMassFlowKgPerHour(topFeedFluid, componentIndex);
      double componentOut = getComponentMassFlowKgPerHour(overheadFluid, componentIndex)
          + getComponentMassFlowKgPerHour(bottomsFluid, componentIndex);
      assertEquals(componentIn, componentOut, COMPONENT_MASS_BALANCE_TOLERANCE_KG_HR,
          "component mass balance mismatch for " + COMPONENT_NAMES[componentIndex]);
    }
  }

  /**
   * Calculate component mass flow from total molar flow, mole fraction, and molar mass.
   *
   * @param fluid fluid containing the component
   * @param componentIndex component index in the fluid
   * @return component mass flow in kg/hr
   */
  private double getComponentMassFlowKgPerHour(SystemInterface fluid, int componentIndex) {
    return fluid.getFlowRate("mole/hr") * fluid.getComponent(componentIndex).getMolarMass()
        * fluid.getMolarComposition()[componentIndex];
  }
}