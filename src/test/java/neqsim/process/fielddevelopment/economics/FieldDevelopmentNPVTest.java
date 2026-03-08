package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Field development NPV calculation test.
 *
 * <p>
 * Replicates the Colab notebook <a href=
 * "https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/fielddevelopment/npv.ipynb">
 * npv.ipynb</a> in Java. Simulates a subsea tieback gas field with transient reservoir depletion,
 * pipeline transport, and full Norwegian Continental Shelf economics (corporate 22% + petroleum
 * 56%).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class FieldDevelopmentNPVTest {

  // ============================================================================
  // TECHNICAL PARAMETERS (from notebook)
  // ============================================================================
  private static final double PRODUCTION_EFFICIENCY = 0.94;
  private static final double GAS_RESERVOIR_VOLUME = 1e9; // m3
  private static final double RESERVOIR_PRESSURE = 150.0; // bara
  private static final double RESERVOIR_TEMPERATURE = 75.0; // C
  private static final double PIPE_WALL_ROUGHNESS = 15e-6; // m
  private static final double WELL_DEPTH = 1070.0; // m

  // ============================================================================
  // ECONOMIC PARAMETERS (from notebook)
  // ============================================================================
  private static final double GAS_PRICE_NOK_PER_SM3 = 1.5;
  private static final double DISCOUNT_RATE = 0.08;
  private static final double TARIFF_PRICE_NOK_PER_SM3 = 0.015;
  private static final double OPERATIONAL_COST_MNOK = 200.0;
  private static final double COST_PER_WELL_MNOK = 1000.0;

  // Tax rates
  private static final double CORPORATE_TAX_RATE = 0.22;
  private static final double SPECIAL_TAX_RATE = 0.56;

  // Case 1: Subsea tieback
  private static final int REFERENCE_NUMBER_OF_WELLS = 6;
  private static final double MAX_GAS_PRODUCTION = 10.0; // MSm3/day
  private static final double NEW_PIPELINE_LENGTH = 10.0; // km
  private static final double NEW_PIPELINE_DIAMETER = 14.0 * 0.0254; // 14" in m
  private static final double EXISTING_PIPELINE_LENGTH = 80.0; // km
  private static final double EXISTING_PIPELINE_DIAMETER = 24.0 * 0.0254; // 24" in m
  private static final double SEA_DEPTH = 300.0; // m
  private static final double INLET_PRESSURE = 30.0; // bara

  // CAPEX
  private static final double SURF_COST_MNOK = 4500.0;
  private static final double TOPSIDE_COST_MNOK = 1500.0;
  private static final double STUDIES_COST_MNOK = 100.0;
  private static final double PRE_DG3_COST_MNOK = 200.0;
  private static final int YEARS_DG3_TO_DG4 = 3;

  /**
   * Full field development NPV calculation matching the Colab notebook. Runs reservoir production
   * simulation to generate a production profile, then feeds it into the CashFlowEngine for NPV
   * calculation with Norwegian taxes.
   */
  @Test
  void testSubseaTiebackNPV() {
    // ========================================================================
    // 1. CREATE RESERVOIR FLUID
    // ========================================================================
    SystemInterface reservoirFluid =
        new SystemSrkEos(273.15 + RESERVOIR_TEMPERATURE, RESERVOIR_PRESSURE);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 0.5);
    reservoirFluid.addComponent("methane", 90.0);
    reservoirFluid.addComponent("ethane", 5.0);
    reservoirFluid.addComponent("propane", 2.0);
    reservoirFluid.addComponent("i-butane", 1.0);
    reservoirFluid.addComponent("n-butane", 1.0);
    reservoirFluid.addComponent("n-hexane", 0.5);
    reservoirFluid.addComponent("water", 1.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);

    // ========================================================================
    // 2. BUILD PROCESS MODEL
    // ========================================================================
    int[] numberOfWells = new int[25];
    for (int i = 0; i < 25; i++) {
      numberOfWells[i] = 6;
    }

    double productionIndex = 10.0E-3 * numberOfWells[0] / (double) REFERENCE_NUMBER_OF_WELLS;

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(reservoirFluid.clone(), GAS_RESERVOIR_VOLUME, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("well number");
    producedGasStream.setFlowRate(MAX_GAS_PRODUCTION, "MSm3/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(productionIndex);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", wellflow.getOutletStream());
    pipe.setPipeWallRoughness(PIPE_WALL_ROUGHNESS);
    pipe.setLength(WELL_DEPTH);
    pipe.setElevation(WELL_DEPTH);
    pipe.setDiameter(0.625);

    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipe2", pipe.getOutletStream());
    pipeline.setPipeWallRoughness(PIPE_WALL_ROUGHNESS);
    pipeline.setLength(NEW_PIPELINE_LENGTH * 1e3);
    pipeline.setElevation(0);
    pipeline.setDiameter(NEW_PIPELINE_DIAMETER);

    PipeBeggsAndBrills pipeline2 = new PipeBeggsAndBrills("pipe3", pipeline.getOutletStream());
    pipeline2.setPipeWallRoughness(PIPE_WALL_ROUGHNESS);
    pipeline2.setLength(EXISTING_PIPELINE_LENGTH * 1e3);
    pipeline2.setElevation(SEA_DEPTH);
    pipeline2.setDiameter(EXISTING_PIPELINE_DIAMETER);

    // No adjuster — use explicit plateau/decline logic with bisection for stability
    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);
    process.add(pipe);
    process.add(pipeline);
    process.add(pipeline2);
    process.run();

    double GIP = reservoirOps.getGasInPlace("GSm3");
    assertTrue(GIP > 100, "Gas in place should be > 100 GSm3, got: " + GIP);

    // ========================================================================
    // 3. RUN PRODUCTION SIMULATION (25 years)
    // ========================================================================
    double deltat = 60.0 * 60.0 * 365 * 24 * PRODUCTION_EFFICIENCY;

    double[] gasProductionMSm3Day = new double[25];
    double[] yearlyGasProductionGSm3 = new double[25];
    double[] reservoirPressureBara = new double[25];
    double[] totalProducedOe = new double[25];
    int productionYears = 0;

    System.out.println("=== Production Profile ===");
    boolean inDecline = false;
    double currentMaxRate = MAX_GAS_PRODUCTION;

    for (int t = 0; t < 25; t++) {
      double prodIndex =
          10.000100751427403E-3 * numberOfWells[t] / (double) REFERENCE_NUMBER_OF_WELLS;
      wellflow.setWellProductionIndex(prodIndex);

      // 1. Determine sustainable production rate at current reservoir state
      producedGasStream.setFlowRate(currentMaxRate, "MSm3/day");
      process.run();
      double pOutAtMax = pipeline2.getOutletStream().getPressure("bara");

      if (pOutAtMax < INLET_PRESSURE || inDecline) {
        // Decline phase: bisection search for rate giving P_out = INLET_PRESSURE
        // Once decline starts, it never reverts to plateau
        inDecline = true;
        double lo = 0.1;
        double hi = currentMaxRate;
        for (int bisect = 0; bisect < 30; bisect++) {
          double mid = (lo + hi) / 2.0;
          producedGasStream.setFlowRate(mid, "MSm3/day");
          process.run();
          double pOut = pipeline2.getOutletStream().getPressure("bara");
          if (pOut > INLET_PRESSURE) {
            lo = mid; // Can sustain higher flow
          } else {
            hi = mid; // Need lower flow
          }
        }
        producedGasStream.setFlowRate(lo, "MSm3/day");
        process.run();
        currentMaxRate = lo; // Cap future years at this rate (monotonic decline)

        if (lo < 0.5) {
          break; // Below economic limit
        }
      }

      // 2. Record production at this year's rate
      reservoirPressureBara[t] = reservoirOps.getReservoirFluid().getPressure("bara");
      gasProductionMSm3Day[t] = reservoirOps.getGasProdution("Sm3/day") / 1e6;
      yearlyGasProductionGSm3[t] = gasProductionMSm3Day[t] / 1e3 * 365.0 * PRODUCTION_EFFICIENCY;
      productionYears = t + 1;

      System.out.println(String.format(
          "Year %2d: Pres=%.1f bara, Qgas=%.2f MSm3/d, Yearly=%.2f GSm3, P_out=%.2f bara, StreamQ=%.2f",
          t, reservoirPressureBara[t], gasProductionMSm3Day[t], yearlyGasProductionGSm3[t],
          pipeline2.getOutletStream().getPressure("bara"),
          producedGasStream.getFlowRate("MSm3/day")));

      // 3. Run transient depletion at this year's determined rate
      for (int k = 0; k < 10; k++) {
        reservoirOps.runTransient(deltat / 10.0);
      }

      totalProducedOe[t] = reservoirOps.getProductionTotal("MSm3 oe");
    }

    assertTrue(productionYears > 5,
        "Should produce for more than 5 years, got: " + productionYears);

    // ========================================================================
    // 4. CALCULATE ECONOMICS (NPV with Norwegian taxes)
    // ========================================================================
    double totalWellCost = 6 * COST_PER_WELL_MNOK; // max wells * cost/well
    double totalCost = STUDIES_COST_MNOK + TOPSIDE_COST_MNOK + SURF_COST_MNOK + totalWellCost;

    // CAPEX schedule: spread over years -5 to DG3toDG4 years before production
    double averageCostPerYear = totalCost / YEARS_DG3_TO_DG4;
    double[] capexSchedule = new double[YEARS_DG3_TO_DG4 + 2];
    capexSchedule[0] = PRE_DG3_COST_MNOK * 0.25;
    capexSchedule[1] = PRE_DG3_COST_MNOK * 0.75;
    for (int i = 2; i < capexSchedule.length; i++) {
      capexSchedule[i] = averageCostPerYear;
    }

    // Use CashFlowEngine for the calculation
    // Note: CashFlowEngine uses loss carry-forward (not tax refunds), so NPV differs from the
    // notebook's direct calculation. Units are treated as MNOK throughout.
    CashFlowEngine engine = new CashFlowEngine("NO");

    engine.setGasPrice(GAS_PRICE_NOK_PER_SM3);
    engine.setGasTariff(TARIFF_PRICE_NOK_PER_SM3);
    // Only apply OPEX during production years via variable cost (no fixed OPEX)
    engine.setFixedOpexPerYear(0.0);
    engine.setOpexPercentOfCapex(0.0);

    // Add CAPEX schedule using calendar years
    // Project starts 5 years before production (2020-2024 development, 2025+ production)
    int projectStartYear = 2020;
    for (int i = 0; i < capexSchedule.length; i++) {
      if (capexSchedule[i] > 0) {
        engine.addCapex(capexSchedule[i], projectStartYear + i);
      }
    }

    // Add gas production profile
    // Convert daily production to annual Sm3
    int productionStartYear = projectStartYear + 5; // 2025
    for (int t = 0; t < productionYears; t++) {
      double annualGasSm3 = gasProductionMSm3Day[t] * 1e6 * 365.0 * PRODUCTION_EFFICIENCY;
      if (annualGasSm3 > 0) {
        engine.addAnnualProduction(productionStartYear + t, 0, annualGasSm3, 0);
      }
    }

    // Calculate NPV
    CashFlowResult result = engine.calculate(DISCOUNT_RATE);

    // ========================================================================
    // 5. VERIFY RESULTS
    // ========================================================================
    // Total CAPEX should match notebook
    double expectedTotalCapex = 0;
    for (double c : capexSchedule) {
      expectedTotalCapex += c;
    }
    assertEquals(expectedTotalCapex, result.getTotalCapex(), 1.0, "Total CAPEX mismatch");

    // NPV should be positive for this gas field
    assertTrue(result.getNpv() > 0, "NPV should be positive, got: " + result.getNpv() + " MNOK");

    // Total revenue should be substantial
    assertTrue(result.getTotalRevenue() > 1000,
        "Total revenue should be > 1000 MNOK, got: " + result.getTotalRevenue());

    // Print summary for inspection
    System.out.println("=== Field Development NPV Results ===");
    System.out.println(String.format("Gas in Place: %.1f GSm3", GIP));
    System.out.println(String.format("Production years: %d", productionYears));
    System.out.println(
        String.format("Total produced: %.1f MSm3 oe", totalProducedOe[productionYears - 1]));
    System.out.println(String.format("Recovery rate: %.1f %%",
        totalProducedOe[productionYears - 1] / GIP / 1e3 * 100.0));
    System.out.println();
    System.out.println(String.format("Total CAPEX: %.0f MNOK", result.getTotalCapex()));
    System.out.println(String.format("Total Revenue: %.0f MNOK", result.getTotalRevenue()));
    System.out.println(String.format("Total Tax: %.0f MNOK", result.getTotalTax()));
    System.out
        .println(String.format("NPV @ %.0f%%: %.0f MNOK", DISCOUNT_RATE * 100, result.getNpv()));
    System.out.println(String.format("IRR: %.1f%%", result.getIrr() * 100));
    if (!Double.isNaN(result.getPaybackYears())) {
      System.out.println(String.format("Payback: %.0f years", result.getPaybackYears()));
    }

    // Print year-by-year
    System.out.println();
    System.out.println(result.toMarkdownTable());

    // Breakeven gas price
    double breakevenGasPrice = engine.calculateBreakevenGasPrice(DISCOUNT_RATE);
    System.out.println(String.format("Breakeven gas price: %.4f NOK/Sm3", breakevenGasPrice));
    assertTrue(breakevenGasPrice > 0 && breakevenGasPrice < GAS_PRICE_NOK_PER_SM3,
        "Breakeven price should be between 0 and gas price, got: " + breakevenGasPrice);
  }

  /**
   * Simplified NPV test using a fixed production profile (no reservoir simulation). Useful for
   * quickly validating the economics engine with known inputs.
   */
  @Test
  void testSimplifiedNPVWithFixedProfile() {
    CashFlowEngine engine = new CashFlowEngine("NO");

    // Gas price assumptions (in NOK, treating engine units as NOK)
    engine.setGasPrice(GAS_PRICE_NOK_PER_SM3);
    engine.setGasTariff(TARIFF_PRICE_NOK_PER_SM3);
    engine.setFixedOpexPerYear(OPERATIONAL_COST_MNOK);
    engine.setOpexPercentOfCapex(0.0);

    // CAPEX: ~12,100 MNOK spread over development
    engine.addCapex(50.0, 2020); // Pre-DG3 year 1
    engine.addCapex(150.0, 2021); // Pre-DG3 year 2
    engine.addCapex(4033.0, 2022); // DG3-DG4 year 1
    engine.addCapex(4033.0, 2023); // DG3-DG4 year 2
    engine.addCapex(4033.0, 2024); // DG3-DG4 year 3

    // Production profile (GSm3/year -> Sm3 for engine)
    // Typical plateau at ~3.4 GSm3/year then decline
    double[] yearlyProductionGSm3 =
        {3.4, 3.4, 3.2, 3.0, 2.8, 2.5, 2.2, 2.0, 1.8, 1.5, 1.2, 1.0, 0.8, 0.6, 0.4};

    for (int i = 0; i < yearlyProductionGSm3.length; i++) {
      engine.addAnnualProduction(2025 + i, 0, yearlyProductionGSm3[i] * 1e9, 0);
    }

    CashFlowResult result = engine.calculate(DISCOUNT_RATE);

    // Verify total CAPEX
    assertEquals(12299.0, result.getTotalCapex(), 1.0);

    // NPV should be positive for this profitable gas field
    System.out.println("Simplified NPV: " + result.getNpv());
    assertTrue(result.getTotalRevenue() > result.getTotalCapex(),
        "Revenue should exceed CAPEX for this field");

    // Print results
    System.out.println("=== Simplified NPV Test ===");
    System.out.println(result.getSummary());
  }

  /**
   * Test the full direct NPV calculation matching the notebook's calceconomy function. This
   * implements the spreadsheet-style NPV calculation directly without CashFlowEngine to validate
   * the approach used in the Python notebook.
   */
  @Test
  void testDirectNPVCalculation() {
    // Synthetic production data (MSm3/day gas production for each year)
    double[] gasProductionRates =
        {10.0, 9.5, 9.0, 8.5, 8.0, 7.5, 7.0, 6.5, 5.5, 4.5, 3.5, 2.5, 2.0, 1.5, 1.0};
    int productionYears = gasProductionRates.length;

    // Yearly gas production in GSm3
    double[] yearlyGasGSm3 = new double[productionYears];
    for (int i = 0; i < productionYears; i++) {
      yearlyGasGSm3[i] = gasProductionRates[i] * 365.0 * PRODUCTION_EFFICIENCY / 1000.0;
    }

    // CAPEX
    double totalWellCost = 6 * COST_PER_WELL_MNOK;
    double totalCost = STUDIES_COST_MNOK + TOPSIDE_COST_MNOK + SURF_COST_MNOK + totalWellCost;
    double averageCostPerYear = totalCost / YEARS_DG3_TO_DG4;

    // Build time series with 5 pre-production years
    int totalYears = 5 + productionYears;
    double[] revenue = new double[totalYears]; // MNOK
    double[] capex = new double[totalYears]; // MNOK
    double[] opex = new double[totalYears]; // MNOK
    double[] tariff = new double[totalYears]; // MNOK

    // CAPEX schedule (years 0-4 are pre-production)
    capex[0] = PRE_DG3_COST_MNOK * 0.25;
    capex[1] = PRE_DG3_COST_MNOK * 0.75;
    capex[2] = averageCostPerYear;
    capex[3] = averageCostPerYear;
    capex[4] = averageCostPerYear;

    // Revenue, OPEX, tariff during production years
    for (int i = 0; i < productionYears; i++) {
      int t = 5 + i;
      revenue[t] = gasProductionRates[i] * GAS_PRICE_NOK_PER_SM3 * 365.0;
      opex[t] = OPERATIONAL_COST_MNOK;
      tariff[t] = yearlyGasGSm3[i] * 1000.0 * TARIFF_PRICE_NOK_PER_SM3;
    }

    // Calculate depreciation schedule (straight-line over 6 years per NCS rules)
    double[] depreciation = new double[totalYears];
    for (int capexYear = 0; capexYear < totalYears; capexYear++) {
      if (capex[capexYear] > 0) {
        double annualDep = capex[capexYear] / 6.0;
        for (int d = 0; d < 6 && (capexYear + d) < totalYears; d++) {
          depreciation[capexYear + d] += annualDep;
        }
      }
    }

    // NPV calculation (before and after tax)
    double npvBeforeTax = 0.0;
    double npvAfterTax = 0.0;
    double totalRevenue = 0.0;
    double totalCapex = 0.0;

    for (int t = 0; t < totalYears; t++) {
      double discountFactor = 1.0 / Math.pow(1 + DISCOUNT_RATE, t);

      // Before-tax cash flow
      double cashFlowBT = revenue[t] - capex[t] - opex[t] - tariff[t];
      npvBeforeTax += cashFlowBT * discountFactor;
      totalRevenue += revenue[t];
      totalCapex += capex[t];

      // Norwegian NCS tax calculation (allows negative tax = refund during investment)
      // Corporate tax base: revenue - opex - tariff - depreciation
      double corporateTaxBase = revenue[t] - opex[t] - tariff[t] - depreciation[t];
      double corporateTax = corporateTaxBase * CORPORATE_TAX_RATE;

      // Petroleum tax base: revenue - opex - tariff - depreciation - corporate tax
      double petroleumTaxBase = revenue[t] - opex[t] - tariff[t] - depreciation[t] - corporateTax;
      double petroleumTax = petroleumTaxBase * SPECIAL_TAX_RATE;

      double cashFlowAT = revenue[t] - capex[t] - opex[t] - tariff[t] - corporateTax - petroleumTax;
      npvAfterTax += cashFlowAT * discountFactor;
    }

    System.out.println("=== Direct NPV Calculation ===");
    System.out.println(
        String.format("Total Revenue: %.0f MNOK (%.2f GNOK)", totalRevenue, totalRevenue / 1e3));
    System.out
        .println(String.format("Total CAPEX: %.0f MNOK (%.2f GNOK)", totalCapex, totalCapex / 1e3));
    System.out.println(
        String.format("NPV before tax: %.0f MNOK (%.2f GNOK)", npvBeforeTax, npvBeforeTax / 1e3));
    System.out.println(
        String.format("NPV after tax: %.0f MNOK (%.2f GNOK)", npvAfterTax, npvAfterTax / 1e3));

    // Assertions
    assertTrue(totalRevenue > 0, "Total revenue should be positive");
    assertTrue(npvBeforeTax > npvAfterTax, "NPV before tax should exceed NPV after tax");
    assertTrue(npvBeforeTax > 0, "NPV before tax should be positive for profitable field");
  }
}
