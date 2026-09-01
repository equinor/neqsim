package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
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
@Tag("slow")
public class ColumnStudyRegressionTest {
  /** Logger for timing reports produced by this regression test. */
  private static final Logger logger = LogManager.getLogger(ColumnStudyRegressionTest.class);
  /** Atmospheric pressure used to convert bara to barg. */
  private static final double ATM_BARA = 1.01325;
  /** Number of answer trays excluding the reboiler. */
  private static final int NUMBER_OF_TRAYS = 10;
  /** Main feed mass flow in kg/hr. */
  private static final double MAIN_FEED_MASS_FLOW_KG_HR = 99381.1038920480;
  /** Factor applied to the main feed flow for the changed-input warm solve. */
  private static final double MAIN_FEED_FLOW_CHANGE_FACTOR = 1.10;
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
  private static final String[] COMPONENT_NAMES = { "H2S", "H2O", "Nitrogen", "CO2", "Methane", "Ethane", "Propane",
      "i-Butane", "n-Butane", "i-Pentane", "n-Pentane", "C6*", "C7*", "C8*", "C9*", "C10-C12*", "C13-C14*", "C15-C16*",
      "C17-C19*", "C20-C22*", "C23-C25*", "C26-C30*", "C31-C38*", "C39-C80*" };

  /** NeqSim component names matching {@link #COMPONENT_NAMES}. */
  private static final String[] NEQSIM_COMPONENT_NAMES = { "H2S", "water", "nitrogen", "CO2", "methane", "ethane",
      "propane", "i-butane", "n-butane", "i-pentane", "n-pentane" };

  /** Pseudo-component molar masses in kg/mol. */
  private static final double[] PSEUDO_MOLAR_MASSES_KG_PER_MOL = { 0.08617800140380859, 0.0909560012817383,
      0.103429000854492, 0.117186996459961, 0.145809005737305, 0.181330001831055, 0.21227799987793, 0.248141998291016,
      0.289217010498047, 0.330338989257813, 0.384696990966797, 0.471157989501953, 0.6624600219726561 };

  /** Pseudo-component densities in kg/m3. */
  private static final double[] PSEUDO_DENSITIES_KG_PER_M3 = { 0.6626640014648439, 0.740698486328125, 0.769004028320313,
      0.789065673828125, 0.8048148193359379, 0.825066711425781, 0.8377041015625, 0.849904113769531, 0.863837097167969,
      0.8755130004882811, 0.8886063232421879, 0.9061005249023439, 0.936200378417969 };

  /** Main feed molar composition in column_study.py component order. */
  private static final double[] MAIN_FEED_COMPOSITION = { 0.0, 1.26975950126355e-03, 3.88734329545213e-06,
      2.03669541112211e-03, 8.35885649596034e-03, 0.030312967680537, 9.83075308994837e-02, 4.09665694460258e-02,
      0.114510205790434, 0.060313250815548, 7.73190146573562e-02, 0.104982256950121, 0.139005591552077,
      0.127908100975965, 6.20910685541127e-02, 6.65500502353172e-02, 0.020235118084271, 1.25829097167123e-02,
      0.011709984585876, 7.11881671769593e-03, 4.57627195654846e-03, 4.50555256022543e-03, 3.25896678227350e-03,
      2.07657328777843e-03 };

  /** Top reflux feed molar composition in column_study.py component order. */
  private static final double[] TOP_FEED_COMPOSITION = { 0.0, 4.35105155095748e-04, 7.63046322451461e-07,
      7.26662709595144e-04, 2.18297869906758e-03, 1.65679049317917e-02, 0.121425832401003, 9.52769636340267e-02,
      0.306895179064482, 0.160387157274294, 0.192815999863345, 7.08381536172843e-02, 2.29352443485453e-02,
      7.52373948573269e-03, 1.57073918334608e-03, 3.72237726886924e-04, 3.00684238117462e-05, 1.06371993923885e-05,
      4.12319121029786e-06, 4.63134876824379e-07, 4.37268256091263e-08, 3.14276050239541e-09, 4.02814694571035e-11,
      2.31802265756544e-14 };

  /** Answer tray temperatures from top tray to bottom tray in degrees Celsius. */
  private static final double[] ANSWER_TEMPERATURE_C_TOPDOWN = { 55.0352092263182, 60.4943624688327, 65.1027206540858,
      70.7739536320417, 82.2452891411628, 88.1350583791952, 93.3124258992463, 98.4324503710853, 104.962736678313,
      115.150145109534 };

  /** Answer tray pressure profile from top tray to bottom tray in barg. */
  private static final double[] ANSWER_PRESSURE_BARG_TOPDOWN = { 4.0, 4.00555555555556, 4.01111111111111,
      4.01666666666667, 4.02222222222222, 4.02777777777778, 4.03333333333333, 4.03888888888889, 4.04444444444444,
      4.05 };

  /**
   * Runs the column-study case and verifies tray profiles plus total and component mass closure.
   */
  @Test
  public void columnStudyCaseMatchesProfileAndClosesMassBalances() {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("manual_column_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("top_stage_reflux", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);

    DistillationColumn column = createColumn(feedStream, topFeedStream);
    column.run();

    assertTrue(column.solved(), "Column-study case should converge with Naphtali-Sandholm");
    assertEquals(DistillationColumn.SolveStatus.RECONCILED_PRODUCTS, column.getLastSolveStatus(),
        "a no-side-draw direct result should preserve the established reconciled-product status");
    assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, column.getLastSolverTypeUsed(),
        "the nominal case must be accepted by the simultaneous solver rather than a premature SR fallback");
    assertTrue(column.getLastIterationCount() > 0, "the nominal rigorous solve should exercise Newton refinement");
    assertTrayTemperatureProfile(column);
    assertTrayPressureProfile(column);
    assertOverallMassBalance(feedStream, topFeedStream, column);
    assertComponentMassBalances(feedStream, topFeedStream, column);
  }

  /**
   * Converge a severely perturbed warm start without exhausting the Newton iteration budget.
   *
   * <p>
   * The initialized column-study state is deliberately perturbed by up to 90 K before a direct simultaneous-correction
   * warm start. The case is outside the two-sweep Newton basin, but it remains a finite, realistic multicomponent
   * hydrocarbon column state. A retained state needs one additional fugacity fixed-point sweep to keep the Newton
   * residual locally consistent enough for the guarded line search to recover the rigorous solution.
   * </p>
   */
  @Test
  public void severeWarmStartPerturbationConvergesWithRefinedKValues(TestReporter testReporter) {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("stall_guard_main_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("stall_guard_top_feed", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
    DistillationColumn column = createColumn(feedStream, topFeedStream);
    column.init();

    for (int trayIndex = 0; trayIndex < column.getNumberOfTrays(); trayIndex++) {
      double perturbedTemperature = column.getTray(trayIndex).getTemperature()
          + 90.0 * Math.sin((trayIndex + 1.0) * 1.9);
      column.getTray(trayIndex).setTemperature(perturbedTemperature);
      column.getTray(trayIndex).getThermoSystem().setTemperature(perturbedTemperature);
    }

    NaphtaliSandholmSolver solver = new NaphtaliSandholmSolver(column);
    solver.setWarmStartFromColumn(true);
    solver.setMaxIterations(80);
    boolean accepted = solver.solve(new UUID(0L, 1L));

    assertTrue(accepted,
        () -> "the severely perturbed retained state should recover without coordinated fallback: iterations="
            + solver.getLastIterations() + ", residual=" + solver.getLastResidualNorm() + ", mass balance="
            + solver.getLastMassBalanceError() + ", base refinements=" + solver.getLastJacobianBaseRefinementCount()
            + ", thermo evaluations=" + solver.getLastThermoEvaluationCount() + ", K sweeps="
            + solver.getLastThermoKValueIterationCount());
    assertTrue(solver.getLastIterations() <= 45,
        "the recovered warm solve should remain well below the 80-iteration cap");
    assertTrue(solver.getLastMassBalanceError() < 1.0e-8, "the recovered state should close total molar balance");
    assertTrue(solver.getLastResidualNorm() < 1.0e-8, "the recovered state should satisfy the scaled MESH residual");
    assertTrue(solver.getLastThermoEvaluationCount() < 24000,
        "the recovered warm solve should keep thermodynamic evaluations bounded");
    assertTrue(solver.getLastThermoKValueIterationCount() < 70000,
        "the recovered warm solve should keep forced-root fugacity sweeps bounded");
    assertTrue(solver.getLastJacobianBaseRefinementCount() > 0,
        "the difficult solve should exercise residual-aware Jacobian base refinement");
    assertEquals(0.0, solver.getLastJacobianBaseResidualMutation(), 0.0,
        "finite-difference assembly must leave the base MESH residual bitwise unchanged");
    testReporter.publishEntry("severe_jacobian_base_refinements",
        Integer.toString(solver.getLastJacobianBaseRefinementCount()));
    testReporter.publishEntry("severe_jacobian_base_residual_mutation",
        Double.toString(solver.getLastJacobianBaseResidualMutation()));
    testReporter.publishEntry("severe_thermo_evaluations", Integer.toString(solver.getLastThermoEvaluationCount()));
    testReporter.publishEntry("severe_k_sweeps", Integer.toString(solver.getLastThermoKValueIterationCount()));
    assertPhysicalProduct(column.getGasOutStream(), "recovered warm-start gas product");
    assertPhysicalProduct(column.getLiquidOutStream(), "recovered warm-start liquid product");
    assertOverallMassBalance(feedStream, topFeedStream, column);
    assertComponentMassBalances(feedStream, topFeedStream, column);
  }

  /**
   * Verify that a legacy top feed connected directly to a tray participates in the simultaneous MESH equations.
   *
   * <p>
   * The public column API registers feeds in the column feed map, while legacy workflows connect side feeds and
   * stripping gas through {@code getTray(index).addStream(stream)}. Both connections describe the same physical inlet
   * and must therefore produce the same Naphtali-Sandholm solution. The nearby operating point guards against a
   * coincidental match at one flow rate.
   * </p>
   */
  @Test
  public void legacyDirectTopFeedParticipatesInNaphtaliSandholmEquations() {
    double[] topFeedFactors = { 1.0, 1.1 };
    for (int caseIndex = 0; caseIndex < topFeedFactors.length; caseIndex++) {
      double topFeedFactor = topFeedFactors[caseIndex];
      SystemInterface baseFluid = createBaseFluid();
      StreamInterface registeredMainFeed = createStream("registered_main_feed_" + caseIndex, baseFluid,
          MAIN_FEED_COMPOSITION, MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
      StreamInterface registeredTopFeed = createStream("registered_top_feed_" + caseIndex, baseFluid,
          TOP_FEED_COMPOSITION, TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA,
          TOP_FEED_MASS_FLOW_KG_HR * topFeedFactor);
      DistillationColumn registeredColumn = createColumn(registeredMainFeed, registeredTopFeed, false);
      registeredColumn.run();
      assertColumnSolveIsValid(registeredColumn, registeredMainFeed, registeredTopFeed,
          "registered-feed reference at factor " + topFeedFactor);
      assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, registeredColumn.getLastSolverTypeUsed(),
          "registered-feed reference should be accepted by the simultaneous solver");

      StreamInterface directMainFeed = createStream("direct_main_feed_" + caseIndex, baseFluid, MAIN_FEED_COMPOSITION,
          MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
      StreamInterface directTopFeed = createStream("legacy_direct_top_feed_" + caseIndex, baseFluid,
          TOP_FEED_COMPOSITION, TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA,
          TOP_FEED_MASS_FLOW_KG_HR * topFeedFactor);
      DistillationColumn directColumn = createColumn(directMainFeed, directTopFeed, true);
      directColumn.run();
      assertColumnSolveIsValid(directColumn, directMainFeed, directTopFeed,
          "legacy direct-feed case at factor " + topFeedFactor);
      assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, directColumn.getLastSolverTypeUsed(),
          "a direct tray feed must participate in Naphtali-Sandholm rather than force a fallback");

      ColumnProductSummary registeredProducts = getProductSummary(registeredColumn);
      ColumnProductSummary directProducts = getProductSummary(directColumn);
      assertProductSummaryWithinRelativeTolerance(registeredProducts, directProducts, 1.0e-5,
          "registered and direct connections should describe the same physical column");
      assertComponentMassBalances(directMainFeed, directTopFeed, directColumn);
      assertTrue(Double.isFinite(directColumn.getLastMeshMaterialResidualNorm()),
          "direct-feed material residual should be finite");
      assertTrue(Double.isFinite(directColumn.getLastMeshEnergyResidualNorm()),
          "direct-feed energy residual should be finite");
      assertWithinRelativeTolerance(registeredColumn.getLastMeshMaterialResidualNorm(),
          directColumn.getLastMeshMaterialResidualNorm(), 1.0e-4,
          "direct-feed material residual should match the registered reference");
      assertWithinRelativeTolerance(registeredColumn.getLastMeshEnergyResidualNorm(),
          directColumn.getLastMeshEnergyResidualNorm(), 1.0e-4,
          "direct-feed energy residual should match the registered reference");
      assertEquals(REBOILER_TEMPERATURE_C, directColumn.getReboiler().getTemperature() - 273.15, 1.0e-6,
          "the fixed reboiler-temperature specification should be satisfied");
      assertPhysicalProduct(directColumn.getGasOutStream(), "overhead");
      assertPhysicalProduct(directColumn.getLiquidOutStream(), "bottoms");
      assertTrue(directColumn.getLastIterationCount() <= 300,
          "direct-feed simultaneous solve should remain inside the configured iteration budget");
    }
  }

  /**
   * Verify that an intermediate liquid side draw participates in the simultaneous material and energy balances.
   *
   * <p>
   * A tray side-draw fraction splits the total liquid leaving that tray between the internal downflow and the external
   * product. The Naphtali-Sandholm equations and the streams applied back to the tray must use the same split. Two
   * nearby draw fractions guard against a coincidental result at one operating point.
   * </p>
   */
  @Test
  public void naphtaliSandholmLiquidSideDrawParticipatesInMeshBalances() {
    double[] drawFractions = { 0.05, 0.08 };
    double previousSideDrawFlow = 0.0;
    for (int caseIndex = 0; caseIndex < drawFractions.length; caseIndex++) {
      double drawFraction = drawFractions[caseIndex];
      SystemInterface baseFluid = createBaseFluid();
      StreamInterface feedStream = createStream("side_draw_main_feed_" + caseIndex, baseFluid, MAIN_FEED_COMPOSITION,
          MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
      StreamInterface topFeedStream = createStream("side_draw_top_feed_" + caseIndex, baseFluid, TOP_FEED_COMPOSITION,
          TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
      DistillationColumn column = createColumn(feedStream, topFeedStream);
      int drawTrayNumber = answerTrayToNeqSimStage(7);
      column.setLiquidSideDrawFraction(drawTrayNumber, drawFraction);

      column.run();

      assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, column.getLastSolverTypeUsed(),
          () -> "side-draw case should be accepted by the simultaneous solver\n" + column.getConvergenceDiagnostics());
      assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
          "side-draw case should close without terminal-product reconciliation");
      assertTrue(column.solved(), "side-draw case should satisfy the active convergence gates");

      StreamInterface internalLiquid = column.getTray(drawTrayNumber).getLiquidOutStream();
      StreamInterface sideDraw = column.getSideDrawStream(drawTrayNumber, DistillationColumn.SideDrawPhase.LIQUID);
      double internalLiquidMolarFlow = internalLiquid.getFlowRate("mol/hr");
      double sideDrawMolarFlow = sideDraw.getFlowRate("mol/hr");
      double actualDrawFraction = sideDrawMolarFlow / (internalLiquidMolarFlow + sideDrawMolarFlow);
      assertEquals(drawFraction, actualDrawFraction, 1.0e-6,
          "the applied tray streams must preserve the configured liquid split");
      double sideDrawFlow = sideDraw.getFlowRate("kg/hr");
      assertTrue(sideDrawFlow > previousSideDrawFlow,
          "the side-product flow should increase at the nearby higher draw fraction");
      previousSideDrawFlow = sideDrawFlow;

      double totalFeedMassFlow = feedStream.getFlowRate("kg/hr") + topFeedStream.getFlowRate("kg/hr");
      double totalProductMassFlow = column.getGasOutStream().getFlowRate("kg/hr")
          + column.getLiquidOutStream().getFlowRate("kg/hr") + sideDrawFlow;
      assertEquals(totalFeedMassFlow, totalProductMassFlow, TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
          "terminal and side products should close the overall mass balance");
      assertEquals(0.0, column.getMassBalance("kg/hr"), TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
          "column mass-balance diagnostics should include the side product");
      assertComponentMassBalancesWithSideDraw(feedStream, topFeedStream, sideDraw, column);

      assertTrue(Double.isFinite(column.getLastMeshMaterialResidualNorm()),
          "side-draw material residual should be finite");
      assertTrue(Double.isFinite(column.getLastMeshEnergyResidualNorm()), "side-draw energy residual should be finite");
      assertEquals(REBOILER_TEMPERATURE_C, column.getReboiler().getTemperature() - 273.15, 1.0e-6,
          "the fixed reboiler-temperature specification should be satisfied");
      assertPhysicalProduct(column.getGasOutStream(), "overhead");
      assertPhysicalProduct(column.getLiquidOutStream(), "bottoms");
      assertPhysicalProduct(sideDraw, "liquid side draw");
      assertTrue(column.getLastIterationCount() <= 300,
          "side-draw simultaneous solve should remain inside the configured iteration budget");
    }
  }

  /**
   * Verify that an intermediate vapor side draw uses the same rigorous phase split as the liquid path.
   */
  @Test
  public void naphtaliSandholmGasSideDrawParticipatesInMeshBalances() {
    double drawFraction = 0.05;
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("gas_side_draw_main_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("gas_side_draw_top_feed", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
    DistillationColumn column = createColumn(feedStream, topFeedStream);
    int drawTrayNumber = answerTrayToNeqSimStage(4);
    column.setGasSideDrawFraction(drawTrayNumber, drawFraction);

    column.run();

    assertEquals(DistillationColumn.SolverType.NAPHTALI_SANDHOLM, column.getLastSolverTypeUsed(),
        () -> "gas side-draw case should be accepted by the simultaneous solver\n"
            + column.getConvergenceDiagnostics());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
        "gas side-draw case should close without terminal-product reconciliation");
    assertTrue(column.solved(), "gas side-draw case should satisfy the active convergence gates");

    StreamInterface internalVapor = column.getTray(drawTrayNumber).getGasOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(drawTrayNumber, DistillationColumn.SideDrawPhase.GAS);
    double internalVaporMolarFlow = internalVapor.getFlowRate("mol/hr");
    double sideDrawMolarFlow = sideDraw.getFlowRate("mol/hr");
    double actualDrawFraction = sideDrawMolarFlow / (internalVaporMolarFlow + sideDrawMolarFlow);
    assertEquals(drawFraction, actualDrawFraction, 1.0e-6,
        "the applied tray streams must preserve the configured vapor split");

    double sideDrawFlow = sideDraw.getFlowRate("kg/hr");
    double totalFeedMassFlow = feedStream.getFlowRate("kg/hr") + topFeedStream.getFlowRate("kg/hr");
    double totalProductMassFlow = column.getGasOutStream().getFlowRate("kg/hr")
        + column.getLiquidOutStream().getFlowRate("kg/hr") + sideDrawFlow;
    assertEquals(totalFeedMassFlow, totalProductMassFlow, TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
        "terminal and vapor side products should close the overall mass balance");
    assertEquals(0.0, column.getMassBalance("kg/hr"), TOTAL_MASS_BALANCE_TOLERANCE_KG_HR,
        "column mass-balance diagnostics should include the vapor side product");
    assertComponentMassBalancesWithSideDraw(feedStream, topFeedStream, sideDraw, column);

    assertTrue(Double.isFinite(column.getLastMeshMaterialResidualNorm()),
        "gas side-draw material residual should be finite");
    assertTrue(Double.isFinite(column.getLastMeshEnergyResidualNorm()),
        "gas side-draw energy residual should be finite");
    assertEquals(REBOILER_TEMPERATURE_C, column.getReboiler().getTemperature() - 273.15, 1.0e-6,
        "the fixed reboiler-temperature specification should be satisfied");
    assertPhysicalProduct(column.getGasOutStream(), "gas-side-draw overhead");
    assertPhysicalProduct(column.getLiquidOutStream(), "gas-side-draw bottoms");
    assertPhysicalProduct(sideDraw, "gas side draw");
    assertTrue(column.getLastIterationCount() <= 300,
        "gas side-draw simultaneous solve should remain inside the configured iteration budget");
  }

  /**
   * Verify that one tray cannot feed two liquid pumparound circuits.
   *
   * <p>
   * A zero-fraction circuit represents a realistic standby configuration. It still owns the tray's single liquid
   * pumparound draw stream and must prevent a second circuit from claiming the same manipulated fraction.
   * </p>
   */
  @Test
  public void duplicatePumparoundDrawTraysAreRejectedAtRegistration() {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("standby_pumparound_main_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("standby_pumparound_top_feed", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
    DistillationColumn column = createColumn(feedStream, topFeedStream);
    int drawTray = answerTrayToNeqSimStage(7);

    DistillationColumn.ColumnPumparound standby = column.addLiquidPumparound("standby column-study pumparound",
        drawTray, answerTrayToNeqSimStage(5), 0.0, 5.0);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> column.addLiquidPumparound("active duplicate column-study pumparound", drawTray,
            answerTrayToNeqSimStage(4), 0.03, 7.0));
    String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
    assertTrue(message.contains("tray"));
    assertTrue(message.contains(String.valueOf(drawTray)));
    assertTrue(message.contains("pumparound"));
    assertEquals(1, column.getPumparounds().size());
    assertEquals(standby, column.getPumparounds().get(0));
    assertEquals(0.0, column.getTray(drawTray).getLiquidPumparoundDrawFraction(), 0.0);

    column.addLiquidPumparound("independent column-study pumparound", drawTray + 1, answerTrayToNeqSimStage(4), 0.03,
        7.0);
    assertEquals(2, column.getPumparounds().size());
  }

  /**
   * Verify that a configured pumparound uses the coordinated residual-monitored fallback.
   *
   * <p>
   * Pumparound returns are outer tear streams and are not yet feed terms in the Naphtali-Sandholm equation system.
   * Routing this configuration through MESH_RESIDUAL prevents the simultaneous solver from applying a liquid split that
   * omits the return stream.
   * </p>
   */
  @Test
  public void naphtaliSandholmDefersPumparoundToCoupledFallback() {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("pumparound_main_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("pumparound_top_feed", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
    DistillationColumn column = createColumn(feedStream, topFeedStream);
    column.addLiquidPumparound("column-study pumparound", answerTrayToNeqSimStage(7), answerTrayToNeqSimStage(5), 0.03,
        5.0);

    column.run();

    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(),
        "an active pumparound should avoid the incomplete simultaneous return-stream formulation");
    assertTrue(column.solved(), "pumparound fallback should satisfy the active convergence gates");
    assertTrue(column.isLastColumnTearConverged(), "pumparound return-stream tear should converge");
    // This regression owns solver coordination. Detailed pumparound product accounting is a
    // separate outer-tear concern and must not be used to validate the Naphtali side-draw equations.
    assertPhysicalProduct(column.getGasOutStream(), "pumparound overhead");
    assertPhysicalProduct(column.getLiquidOutStream(), "pumparound bottoms");
  }

  /**
   * Verify that an exhausted pumparound tear solve cannot report the column as solved.
   *
   * <p>
   * The inner tray solve can converge while the outer pumparound return-stream tear remains open. A one-iteration limit
   * and strict tolerance reproduce that distinction deterministically at two nearby draw fractions.
   * </p>
   */
  @Test
  public void unconvergedPumparoundTearDoesNotReportSolved() {
    final double tearTolerance = 1.0e-16;
    double[] drawFractions = { 0.03, 0.04 };
    for (int caseIndex = 0; caseIndex < drawFractions.length; caseIndex++) {
      SystemInterface baseFluid = createBaseFluid();
      StreamInterface feedStream = createStream("limited_pumparound_main_feed_" + caseIndex, baseFluid,
          MAIN_FEED_COMPOSITION, MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
      StreamInterface topFeedStream = createStream("limited_pumparound_top_feed_" + caseIndex, baseFluid,
          TOP_FEED_COMPOSITION, TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
      DistillationColumn column = createColumn(feedStream, topFeedStream);
      column.addLiquidPumparound("limited column-study pumparound", answerTrayToNeqSimStage(7),
          answerTrayToNeqSimStage(5), drawFractions[caseIndex], 5.0);
      column.setMaxColumnTearIterations(1);
      column.setMaxPumparoundIterations(1);
      column.setColumnTearTolerance(tearTolerance);

      column.run();

      assertFalse(column.isLastColumnTearConverged(),
          "one outer iteration should leave the pumparound tear open at draw fraction " + drawFractions[caseIndex]);
      assertTrue(column.getLastColumnTearResidual() > tearTolerance,
          "reported tear residual should remain above the configured tolerance");
      assertFalse(column.solved(), "an open outer tear must make the public solved status false");
      assertEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus(),
          "an exhausted outer tear must be reported as a failed column solve");
      assertTrue(column.getLastSolveStatusReason().contains("tear"),
          "the failure reason should identify the unconverged tear-variable solve");
      assertPhysicalProduct(column.getGasOutStream(), "limited-pumparound overhead");
      assertPhysicalProduct(column.getLiquidOutStream(), "limited-pumparound bottoms");
    }
  }

  /**
   * Reports cold, unchanged warm, and 10-percent-increased inlet solve times for the column-study case.
   *
   * <p>
   * The timing values are diagnostic only because wall-clock timings vary by machine and JVM state. Each solve is
   * checked for convergence and external mass closure so the report cannot conceal an invalid fast path.
   * </p>
   *
   * @param testReporter JUnit reporter that persists the measured timings in the Surefire test report
   */
  @Test
  public void columnStudyCaseReportsColdAndWarmSolveTimes(TestReporter testReporter) {
    SystemInterface baseFluid = createBaseFluid();
    StreamInterface feedStream = createStream("manual_column_feed", baseFluid, MAIN_FEED_COMPOSITION,
        MAIN_FEED_TEMPERATURE_C, MAIN_FEED_PRESSURE_BARA, MAIN_FEED_MASS_FLOW_KG_HR);
    StreamInterface topFeedStream = createStream("top_stage_reflux", baseFluid, TOP_FEED_COMPOSITION,
        TOP_FEED_TEMPERATURE_C, TOP_FEED_PRESSURE_BARA, TOP_FEED_MASS_FLOW_KG_HR);
    DistillationColumn column = createColumn(feedStream, topFeedStream);

    long coldStartNanos = System.nanoTime();
    column.run();
    long coldSolveNanos = System.nanoTime() - coldStartNanos;
    assertColumnSolveIsValid(column, feedStream, topFeedStream, "cold solve");
    int coldIterations = column.getLastIterationCount();
    ColumnProductSummary coldProducts = getProductSummary(column);
    assertTrue(!column.isDoInitializion(), "accepted cold solve should leave no pending column reinitialization");

    long warmStartNanos = System.nanoTime();
    column.run();
    long warmSolveNanos = System.nanoTime() - warmStartNanos;
    assertColumnSolveIsValid(column, feedStream, topFeedStream, "unchanged warm solve");
    int warmIterations = column.getLastIterationCount();
    boolean warmStateReused = column.wasNaphtaliSandholmWarmStateReused();
    ColumnProductSummary warmProducts = getProductSummary(column);
    assertProductSummaryWithinRelativeTolerance(coldProducts, warmProducts, 0.10,
        "unchanged warm solution should remain within 10 percent of the cold solution");

    feedStream.setFlowRate(MAIN_FEED_MASS_FLOW_KG_HR * MAIN_FEED_FLOW_CHANGE_FACTOR, "kg/hr");
    feedStream.run();
    long changedInletStartNanos = System.nanoTime();
    column.run();
    long changedInletSolveNanos = System.nanoTime() - changedInletStartNanos;
    assertColumnSolveIsValid(column, feedStream, topFeedStream, "10 percent increased-inlet solve");
    int changedInletIterations = column.getLastIterationCount();
    int changedInletThermo = column.getLastNaphtaliThermoEvaluationCount();
    int changedInletKSweeps = column.getLastNaphtaliThermoKValueIterationCount();
    ColumnProductSummary changedInletProducts = getProductSummary(column);

    logger.info(
        "Column-study timing: cold={} ms ({} iterations), unchanged warm={} ms ({} iterations), "
            + "10%-increased inlet={} ms ({} iterations). Products [gas kg/hr, liquid kg/hr, gas C, liquid C]: "
            + "cold={}, warm={}, increased-inlet={}",
        nanosToMillis(coldSolveNanos), coldIterations, nanosToMillis(warmSolveNanos), warmIterations,
        nanosToMillis(changedInletSolveNanos), changedInletIterations, coldProducts, warmProducts,
        changedInletProducts);
    testReporter.publishEntry("cold_solve_ms", Double.toString(nanosToMillis(coldSolveNanos)));
    testReporter.publishEntry("unchanged_warm_solve_ms", Double.toString(nanosToMillis(warmSolveNanos)));
    testReporter.publishEntry("increased_inlet_solve_ms", Double.toString(nanosToMillis(changedInletSolveNanos)));
    testReporter.publishEntry("increased_inlet_iterations", Integer.toString(changedInletIterations));
    testReporter.publishEntry("increased_inlet_thermo_evaluations", Integer.toString(changedInletThermo));
    testReporter.publishEntry("increased_inlet_k_sweeps", Integer.toString(changedInletKSweeps));
    assertTrue(warmStateReused, "unchanged warm solve should reuse the accepted Naphtali-Sandholm state");
    assertEquals(0, warmIterations, "unchanged warm solve should not require initializer or Newton iterations");
    assertTrue(changedInletIterations <= 4,
        "the changed-inlet warm solve should converge in at most four Newton iterations");
    assertTrue(changedInletThermo < 2500,
        "the changed-inlet warm solve should need fewer than 2500 thermodynamic evaluations");
    assertTrue(changedInletKSweeps < 7000,
        "the changed-inlet warm solve should need fewer than 7000 forced-root fugacity sweeps");
  }

  /**
   * Capture terminal product values for timing and solution-preservation reporting.
   *
   * @param column solved column
   * @return terminal product flow and temperature summary
   */
  private ColumnProductSummary getProductSummary(DistillationColumn column) {
    return new ColumnProductSummary(column.getGasOutStream().getFlowRate("kg/hr"),
        column.getLiquidOutStream().getFlowRate("kg/hr"), column.getGasOutStream().getTemperature("C"),
        column.getLiquidOutStream().getTemperature("C"));
  }

  /**
   * Assert that every product value stays within a specified relative tolerance.
   *
   * @param expected expected cold-solve summary
   * @param actual summary to compare
   * @param relativeTolerance maximum relative difference
   * @param message assertion message prefix
   */
  private void assertProductSummaryWithinRelativeTolerance(ColumnProductSummary expected, ColumnProductSummary actual,
      double relativeTolerance, String message) {
    assertWithinRelativeTolerance(expected.gasFlowKgPerHour, actual.gasFlowKgPerHour, relativeTolerance,
        message + " (gas flow)");
    assertWithinRelativeTolerance(expected.liquidFlowKgPerHour, actual.liquidFlowKgPerHour, relativeTolerance,
        message + " (liquid flow)");
    assertWithinRelativeTolerance(expected.gasTemperatureC, actual.gasTemperatureC, relativeTolerance,
        message + " (gas temperature)");
    assertWithinRelativeTolerance(expected.liquidTemperatureC, actual.liquidTemperatureC, relativeTolerance,
        message + " (liquid temperature)");
  }

  /**
   * Assert two finite values have a bounded relative difference.
   *
   * @param expected expected value
   * @param actual actual value
   * @param relativeTolerance maximum relative difference
   * @param message assertion message
   */
  private void assertWithinRelativeTolerance(double expected, double actual, double relativeTolerance, String message) {
    double scale = Math.max(1.0, Math.abs(expected));
    assertTrue(Math.abs(actual - expected) / scale <= relativeTolerance,
        message + ": expected=" + expected + ", actual=" + actual);
  }

  /**
   * Terminal product values reported by the timing regression.
   *
   * @author Copilot
   * @version 1.0
   */
  private static class ColumnProductSummary {
    private final double gasFlowKgPerHour;
    private final double liquidFlowKgPerHour;
    private final double gasTemperatureC;
    private final double liquidTemperatureC;

    /**
     * Create a terminal product summary.
     *
     * @param gasFlowKgPerHour terminal gas product mass flow in kg/hr
     * @param liquidFlowKgPerHour terminal liquid product mass flow in kg/hr
     * @param gasTemperatureC terminal gas product temperature in degrees Celsius
     * @param liquidTemperatureC terminal liquid product temperature in degrees Celsius
     */
    private ColumnProductSummary(double gasFlowKgPerHour, double liquidFlowKgPerHour, double gasTemperatureC,
        double liquidTemperatureC) {
      this.gasFlowKgPerHour = gasFlowKgPerHour;
      this.liquidFlowKgPerHour = liquidFlowKgPerHour;
      this.gasTemperatureC = gasTemperatureC;
      this.liquidTemperatureC = liquidTemperatureC;
    }

    /**
     * Format terminal product values for a timing report.
     *
     * @return terminal product values in reporting order
     */
    @Override
    public String toString() {
      return String.format("[%.3f, %.3f, %.3f, %.3f]", gasFlowKgPerHour, liquidFlowKgPerHour, gasTemperatureC,
          liquidTemperatureC);
    }
  }

  /**
   * Assert finite, positive flow, temperature, and normalized composition for a terminal product.
   *
   * @param product terminal product stream
   * @param label product label used in assertion messages
   */
  private void assertPhysicalProduct(StreamInterface product, String label) {
    assertTrue(Double.isFinite(product.getFlowRate("kg/hr")) && product.getFlowRate("kg/hr") > 0.0,
        label + " flow should be finite and positive");
    assertTrue(Double.isFinite(product.getTemperature()) && product.getTemperature() > 0.0,
        label + " temperature should be finite and positive");
    double compositionSum = 0.0;
    double[] composition = product.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < composition.length; componentIndex++) {
      assertTrue(
          Double.isFinite(composition[componentIndex]) && composition[componentIndex] >= 0.0
              && composition[componentIndex] <= 1.0,
          label + " composition should remain physical at component " + componentIndex);
      compositionSum += composition[componentIndex];
    }
    assertEquals(1.0, compositionSum, 1.0e-8, label + " composition should remain normalized");
  }

  /**
   * Assert that a completed column solve converged and preserves the external mass balance.
   *
   * @param column solved column
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @param solveDescription description included in assertion failures
   */
  private void assertColumnSolveIsValid(DistillationColumn column, StreamInterface feedStream,
      StreamInterface topFeedStream, String solveDescription) {
    assertTrue(column.solved(), solveDescription + " should converge with Naphtali-Sandholm");
    assertOverallMassBalance(feedStream, topFeedStream, column);
  }

  /**
   * Convert elapsed nanoseconds to milliseconds for the timing report.
   *
   * @param elapsedNanos elapsed time in nanoseconds
   * @return elapsed time in milliseconds
   */
  private double nanosToMillis(long elapsedNanos) {
    return elapsedNanos / 1.0e6;
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
  private StreamInterface createStream(String name, SystemInterface baseFluid, double[] molarComposition,
      double temperatureC, double pressureBara, double massFlowKgPerHour) {
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
    return createColumn(feedStream, topFeedStream, false);
  }

  /**
   * Create and configure the column-study distillation column with a selected top-feed connection style.
   *
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @param directTopFeed whether to connect the top feed directly to its tray for legacy compatibility testing
   * @return configured column ready to run
   */
  private DistillationColumn createColumn(StreamInterface feedStream, StreamInterface topFeedStream,
      boolean directTopFeed) {
    DistillationColumn column = new DistillationColumn("20VE105_205_standalone", NUMBER_OF_TRAYS, true, false);
    column.addFeedStream(feedStream, answerTrayToNeqSimStage(5));
    if (directTopFeed) {
      column.getTray(answerTrayToNeqSimStage(1)).addStream(topFeedStream);
    } else {
      column.addFeedStream(topFeedStream, answerTrayToNeqSimStage(1));
    }
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
    return TOP_PRESSURE_BARA + (BOTTOM_PRESSURE_BARA - TOP_PRESSURE_BARA) * (NUMBER_OF_TRAYS / 9.0);
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
      assertEquals(ANSWER_TEMPERATURE_C_TOPDOWN[answerTray - 1], actualTemperatureC, TEMPERATURE_PROFILE_TOLERANCE_C,
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
      assertEquals(ANSWER_PRESSURE_BARG_TOPDOWN[answerTray - 1], actualPressureBarg, PRESSURE_PROFILE_TOLERANCE_BARG,
          "pressure profile mismatch at answer tray " + answerTray);
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
   * Assert per-component mass closure when an intermediate liquid side product is present.
   *
   * @param feedStream main column feed
   * @param topFeedStream external top reflux feed
   * @param sideDraw intermediate side-product stream
   * @param column solved column
   */
  private void assertComponentMassBalancesWithSideDraw(StreamInterface feedStream, StreamInterface topFeedStream,
      StreamInterface sideDraw, DistillationColumn column) {
    SystemInterface feedFluid = feedStream.getThermoSystem();
    SystemInterface topFeedFluid = topFeedStream.getThermoSystem();
    SystemInterface overheadFluid = column.getGasOutStream().getThermoSystem();
    SystemInterface bottomsFluid = column.getLiquidOutStream().getThermoSystem();
    SystemInterface sideDrawFluid = sideDraw.getThermoSystem();

    for (int componentIndex = 0; componentIndex < COMPONENT_NAMES.length; componentIndex++) {
      double componentIn = getComponentMassFlowKgPerHour(feedFluid, componentIndex)
          + getComponentMassFlowKgPerHour(topFeedFluid, componentIndex);
      double componentOut = getComponentMassFlowKgPerHour(overheadFluid, componentIndex)
          + getComponentMassFlowKgPerHour(bottomsFluid, componentIndex)
          + getComponentMassFlowKgPerHour(sideDrawFluid, componentIndex);
      assertEquals(componentIn, componentOut, COMPONENT_MASS_BALANCE_TOLERANCE_KG_HR,
          "side-draw component mass balance mismatch for " + COMPONENT_NAMES[componentIndex]);
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
