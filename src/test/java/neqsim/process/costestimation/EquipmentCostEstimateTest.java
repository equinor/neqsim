package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.column.ColumnCostEstimate;
import neqsim.process.costestimation.compressor.CompressorCostEstimate;
import neqsim.process.costestimation.pipe.PipeCostEstimate;
import neqsim.process.costestimation.pump.PumpCostEstimate;
import neqsim.process.costestimation.valve.ValveCostEstimate;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for equipment-specific cost estimation classes.
 *
 * @author AGAS
 */
public class EquipmentCostEstimateTest {

  private static SystemInterface gasSystem;
  private static SystemInterface liquidSystem;

  @BeforeAll
  public static void setUp() {
    // Create a gas system for compressor and valve testing
    gasSystem = new SystemSrkEos(298.15, 30.0);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.10);
    gasSystem.addComponent("propane", 0.05);
    gasSystem.setMixingRule("classic");

    // Create a liquid system for pump testing
    liquidSystem = new SystemSrkEos(298.15, 5.0);
    liquidSystem.addComponent("water", 1.0);
    liquidSystem.setMixingRule("classic");
  }

  @Test
  @DisplayName("Test CompressorCostEstimate with centrifugal compressor")
  void testCompressorCostEstimate() {
    Stream feed = new Stream("compFeed", gasSystem.clone());
    feed.setFlowRate(10000, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("TestCompressor", feed);
    compressor.setOutletPressure(80.0);
    compressor.setPolytropicEfficiency(0.78);
    compressor.run();

    compressor.initMechanicalDesign();
    CompressorMechanicalDesign mecDesign =
        (CompressorMechanicalDesign) compressor.getMechanicalDesign();

    // Set design power for cost estimation
    double power = compressor.getPower("kW");
    mecDesign.setMaxDesignPower(power);

    CompressorCostEstimate costEstimate = new CompressorCostEstimate(mecDesign);
    costEstimate.setCompressorType("centrifugal");
    costEstimate.setDriverType("electric-motor");
    costEstimate.setIncludeDriver(true);

    costEstimate.calculateCostEstimate();

    assertTrue(costEstimate.getPurchasedEquipmentCost() > 0,
        "Purchased equipment cost should be positive");
    assertTrue(costEstimate.getBareModuleCost() > costEstimate.getPurchasedEquipmentCost(),
        "Bare module cost should exceed PEC");
    assertTrue(costEstimate.getTotalModuleCost() > costEstimate.getBareModuleCost(),
        "Total module cost should exceed bare module cost");
    // Man-hours may be zero if weight is not calculated by mechanical design
    assertTrue(costEstimate.getInstallationManHours() >= 0,
        "Installation man-hours should not be negative");

    Map<String, Object> breakdown = costEstimate.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("centrifugal", breakdown.get("compressorType"));
    assertEquals("electric-motor", breakdown.get("driverType"));
  }

  @Test
  @DisplayName("Test CompressorCostEstimate with reciprocating compressor")
  void testReciprocatingCompressorCost() {
    Stream feed = new Stream("compFeed", gasSystem.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("RecipCompressor", feed);
    compressor.setOutletPressure(60.0);
    compressor.run();

    compressor.initMechanicalDesign();
    CompressorMechanicalDesign mecDesign =
        (CompressorMechanicalDesign) compressor.getMechanicalDesign();
    mecDesign.setMaxDesignPower(compressor.getPower("kW"));

    CompressorCostEstimate centrifugalCost = new CompressorCostEstimate(mecDesign);
    centrifugalCost.setCompressorType("centrifugal");
    centrifugalCost.calculateCostEstimate();

    CompressorCostEstimate recipCost = new CompressorCostEstimate(mecDesign);
    recipCost.setCompressorType("reciprocating");
    recipCost.calculateCostEstimate();

    // Reciprocating compressors are typically more expensive
    assertTrue(recipCost.getPurchasedEquipmentCost() > centrifugalCost.getPurchasedEquipmentCost(),
        "Reciprocating compressor should cost more than centrifugal");
  }

  @Test
  @DisplayName("Test ValveCostEstimate with control valve")
  void testValveCostEstimate() {
    Stream feed = new Stream("valveFeed", gasSystem.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("TestValve", feed);
    valve.setOutletPressure(20.0);
    valve.run();

    valve.initMechanicalDesign();
    ValveMechanicalDesign mecDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();

    ValveCostEstimate costEstimate = new ValveCostEstimate(mecDesign);
    costEstimate.setValveType("control");
    costEstimate.setValveCv(150.0);
    costEstimate.setPressureClass(600);
    costEstimate.setIncludeActuator(true);
    costEstimate.setActuatorType("pneumatic");

    costEstimate.calculateCostEstimate();

    assertTrue(costEstimate.getPurchasedEquipmentCost() > 0,
        "Control valve cost should be positive");
    assertTrue(costEstimate.getBareModuleCost() > 0, "Bare module cost should be positive");

    Map<String, Object> breakdown = costEstimate.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("control", breakdown.get("valveType"));
    assertEquals(150.0, breakdown.get("valveCv"));
    assertEquals(600, breakdown.get("pressureClass"));
  }

  @Test
  @DisplayName("Test ValveCostEstimate with different valve types")
  void testDifferentValveTypes() {
    Stream feed = new Stream("valveFeed", gasSystem.clone());
    feed.setFlowRate(5000, "kg/hr");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("TestValve", feed);
    valve.setOutletPressure(20.0);
    valve.run();

    valve.initMechanicalDesign();
    ValveMechanicalDesign mecDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();

    // Test control valve
    ValveCostEstimate controlValve = new ValveCostEstimate(mecDesign);
    controlValve.setValveType("control");
    controlValve.setValveCv(100.0);
    controlValve.calculateCostEstimate();

    // Test gate valve
    ValveCostEstimate gateValve = new ValveCostEstimate(mecDesign);
    gateValve.setValveType("gate");
    gateValve.setNominalSize(6.0);
    gateValve.calculateCostEstimate();

    // Test ball valve
    ValveCostEstimate ballValve = new ValveCostEstimate(mecDesign);
    ballValve.setValveType("ball");
    ballValve.setNominalSize(6.0);
    ballValve.calculateCostEstimate();

    // Ball valves typically cost more than gate valves
    assertTrue(ballValve.getPurchasedEquipmentCost() > gateValve.getPurchasedEquipmentCost(),
        "Ball valve should cost more than gate valve");
  }

  @Test
  @DisplayName("Test PipeCostEstimate with different materials and schedules")
  void testPipeCostEstimate() {
    PipeCostEstimate pipeCost = new PipeCostEstimate(null);
    pipeCost.setNominalDiameter(8.0); // 8 inch pipe
    pipeCost.setPipeLength(500.0); // 500 meters
    pipeCost.setPipeSchedule("40");
    pipeCost.setIncludeFittings(true);
    pipeCost.setFittingsPerHundredMeters(15);
    pipeCost.setNumberOfFlanges(4);
    pipeCost.setInstallationType("above-ground");

    pipeCost.calculateCostEstimate();

    assertTrue(pipeCost.getPurchasedEquipmentCost() > 0, "Pipe cost should be positive");
    assertTrue(pipeCost.calcPipeWeight() > 0, "Pipe weight should be positive");

    Map<String, Object> breakdown = pipeCost.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals(8.0, breakdown.get("nominalDiameter_inches"));
    assertEquals(500.0, breakdown.get("pipeLength_m"));
    assertTrue((double) breakdown.get("costPerMeter_USD") > 0, "Cost per meter should be positive");
  }

  @Test
  @DisplayName("Test PipeCostEstimate schedule comparison")
  void testPipeScheduleComparison() {
    // Schedule 40 pipe
    PipeCostEstimate sch40 = new PipeCostEstimate(null);
    sch40.setNominalDiameter(6.0);
    sch40.setPipeLength(100.0);
    sch40.setPipeSchedule("40");
    sch40.setIncludeFittings(false);
    sch40.setNumberOfFlanges(0);
    sch40.calculateCostEstimate();

    // Schedule 80 pipe (heavier wall)
    PipeCostEstimate sch80 = new PipeCostEstimate(null);
    sch80.setNominalDiameter(6.0);
    sch80.setPipeLength(100.0);
    sch80.setPipeSchedule("80");
    sch80.setIncludeFittings(false);
    sch80.setNumberOfFlanges(0);
    sch80.calculateCostEstimate();

    // Schedule 80 should cost more
    assertTrue(sch80.getPurchasedEquipmentCost() > sch40.getPurchasedEquipmentCost(),
        "Schedule 80 pipe should cost more than schedule 40");
    assertTrue(sch80.calcPipeWeight() > sch40.calcPipeWeight(),
        "Schedule 80 pipe should weigh more than schedule 40");
  }

  @Test
  @DisplayName("Test ColumnCostEstimate with trayed column")
  void testColumnCostEstimate() {
    ColumnCostEstimate columnCost = new ColumnCostEstimate(null);
    columnCost.setColumnType("trayed");
    columnCost.setTrayType("sieve");
    columnCost.setColumnDiameter(2.0); // 2 meter diameter
    columnCost.setColumnHeight(25.0); // 25 meters height
    columnCost.setNumberOfTrays(30);
    columnCost.setDesignPressure(15.0);
    columnCost.setIncludeReboiler(true);
    columnCost.setReboilerDuty(2000.0);
    columnCost.setIncludeCondenser(true);
    columnCost.setCondenserDuty(1500.0);

    columnCost.calculateCostEstimate();

    assertTrue(columnCost.getPurchasedEquipmentCost() > 0, "Column cost should be positive");
    assertTrue(columnCost.calcColumnWeight() > 0, "Column weight should be positive");

    Map<String, Object> breakdown = columnCost.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("trayed", breakdown.get("columnType"));
    assertEquals("sieve", breakdown.get("trayType"));
    assertEquals(30, breakdown.get("numberOfTrays"));
  }

  @Test
  @DisplayName("Test ColumnCostEstimate tray type comparison")
  void testColumnTrayTypeComparison() {
    // Sieve trays (cheapest)
    ColumnCostEstimate sieveTray = new ColumnCostEstimate(null);
    sieveTray.setColumnDiameter(1.5);
    sieveTray.setColumnHeight(15.0);
    sieveTray.setNumberOfTrays(20);
    sieveTray.setTrayType("sieve");
    sieveTray.setIncludeReboiler(false);
    sieveTray.setIncludeCondenser(false);
    sieveTray.calculateCostEstimate();

    // Valve trays (more expensive)
    ColumnCostEstimate valveTray = new ColumnCostEstimate(null);
    valveTray.setColumnDiameter(1.5);
    valveTray.setColumnHeight(15.0);
    valveTray.setNumberOfTrays(20);
    valveTray.setTrayType("valve");
    valveTray.setIncludeReboiler(false);
    valveTray.setIncludeCondenser(false);
    valveTray.calculateCostEstimate();

    // Bubble cap trays (most expensive)
    ColumnCostEstimate bubbleCapTray = new ColumnCostEstimate(null);
    bubbleCapTray.setColumnDiameter(1.5);
    bubbleCapTray.setColumnHeight(15.0);
    bubbleCapTray.setNumberOfTrays(20);
    bubbleCapTray.setTrayType("bubble-cap");
    bubbleCapTray.setIncludeReboiler(false);
    bubbleCapTray.setIncludeCondenser(false);
    bubbleCapTray.calculateCostEstimate();

    assertTrue(valveTray.getPurchasedEquipmentCost() > sieveTray.getPurchasedEquipmentCost(),
        "Valve trays should cost more than sieve trays");
    assertTrue(bubbleCapTray.getPurchasedEquipmentCost() > valveTray.getPurchasedEquipmentCost(),
        "Bubble cap trays should cost more than valve trays");
  }

  @Test
  @DisplayName("Test PumpCostEstimate with centrifugal pump")
  void testPumpCostEstimate() {
    Stream feed = new Stream("pumpFeed", liquidSystem.clone());
    feed.setFlowRate(100000.0, "kg/hr"); // 100 tonnes/hour
    feed.setPressure(3.0, "bara");
    feed.run();

    Pump pump = new Pump("TestPump", feed);
    pump.setOutletPressure(20.0);
    pump.run();

    pump.initMechanicalDesign();
    PumpMechanicalDesign mecDesign = (PumpMechanicalDesign) pump.getMechanicalDesign();
    mecDesign.setMaxDesignPower(pump.getPower());

    PumpCostEstimate costEstimate = new PumpCostEstimate(mecDesign);
    costEstimate.setPumpType("centrifugal");
    costEstimate.setIncludeMotor(true);
    costEstimate.setApiRated(false);
    costEstimate.setSealType("single-mechanical");

    costEstimate.calculateCostEstimate();

    assertTrue(costEstimate.getPurchasedEquipmentCost() > 0, "Pump cost should be positive");
    assertTrue(costEstimate.getBareModuleCost() > costEstimate.getPurchasedEquipmentCost(),
        "Bare module cost should exceed PEC");

    Map<String, Object> breakdown = costEstimate.getCostBreakdown();
    assertNotNull(breakdown);
    assertEquals("centrifugal", breakdown.get("pumpType"));
    assertEquals(true, breakdown.get("includeMotor"));
  }

  @Test
  @DisplayName("Test material factor impact on cost")
  void testMaterialFactorImpact() {
    ColumnCostEstimate carbonSteelColumn = new ColumnCostEstimate(null);
    carbonSteelColumn.setColumnDiameter(1.5);
    carbonSteelColumn.setColumnHeight(15.0);
    carbonSteelColumn.setNumberOfTrays(20);
    carbonSteelColumn.setIncludeReboiler(false);
    carbonSteelColumn.setIncludeCondenser(false);
    carbonSteelColumn.setMaterialOfConstruction("Carbon Steel");
    carbonSteelColumn.calculateCostEstimate();

    ColumnCostEstimate stainlessColumn = new ColumnCostEstimate(null);
    stainlessColumn.setColumnDiameter(1.5);
    stainlessColumn.setColumnHeight(15.0);
    stainlessColumn.setNumberOfTrays(20);
    stainlessColumn.setIncludeReboiler(false);
    stainlessColumn.setIncludeCondenser(false);
    stainlessColumn.setMaterialOfConstruction("SS316");
    stainlessColumn.calculateCostEstimate();

    assertTrue(
        stainlessColumn.getPurchasedEquipmentCost() > carbonSteelColumn.getPurchasedEquipmentCost(),
        "Stainless steel column should cost more than carbon steel");
    assertEquals(1.0, carbonSteelColumn.getMaterialFactor(), 0.01,
        "Carbon steel material factor should be 1.0");
    assertTrue(stainlessColumn.getMaterialFactor() > 1.5,
        "Stainless steel material factor should be > 1.5");
  }

  @Test
  @DisplayName("Test operating cost estimation")
  void testOperatingCostEstimation() {
    // Test compressor operating cost
    Stream feed = new Stream("compFeed", gasSystem.clone());
    feed.setFlowRate(10000, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("OpCostCompressor", feed);
    compressor.setOutletPressure(80.0);
    compressor.run();

    compressor.initMechanicalDesign();
    CompressorMechanicalDesign mecDesign =
        (CompressorMechanicalDesign) compressor.getMechanicalDesign();
    mecDesign.setMaxDesignPower(compressor.getPower("kW"));

    CompressorCostEstimate costEstimate = new CompressorCostEstimate(mecDesign);
    costEstimate.setCompressorType("centrifugal");
    costEstimate.setDriverType("electric-motor");
    costEstimate.calculateCostEstimate();

    double annualOperatingCost = costEstimate.calcAnnualOperatingCost(8000, 0.10, 5.0); // 8000 hrs,
                                                                                        // $0.10/kWh
    double maintenanceCost = costEstimate.calcAnnualMaintenanceCost();

    assertTrue(annualOperatingCost > 0, "Annual operating cost should be positive");
    assertTrue(maintenanceCost > 0, "Annual maintenance cost should be positive");
    assertTrue(maintenanceCost < costEstimate.getPurchasedEquipmentCost(),
        "Maintenance cost should be less than PEC");
  }

  @Test
  @DisplayName("Test column utility cost estimation")
  void testColumnUtilityCost() {
    ColumnCostEstimate columnCost = new ColumnCostEstimate(null);
    columnCost.setColumnDiameter(2.0);
    columnCost.setColumnHeight(25.0);
    columnCost.setNumberOfTrays(30);
    columnCost.setIncludeReboiler(true);
    columnCost.setReboilerDuty(2000.0); // 2000 kW
    columnCost.setIncludeCondenser(true);
    columnCost.setCondenserDuty(1500.0); // 1500 kW
    columnCost.calculateCostEstimate();

    double utilityCost = columnCost.calcAnnualUtilityCost(8000, 15.0, 0.05); // 8000 hrs, $15/tonne
                                                                             // steam, $0.05/m3 CW

    assertTrue(utilityCost > 0, "Annual utility cost should be positive");
  }
}
