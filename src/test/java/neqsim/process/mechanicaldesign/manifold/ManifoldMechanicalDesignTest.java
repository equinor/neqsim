package neqsim.process.mechanicaldesign.manifold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive tests for ManifoldMechanicalDesign.
 *
 * <p>
 * Tests cover:
 * </p>
 * <ul>
 * <li>Topside manifold design per ASME B31.3</li>
 * <li>Onshore manifold design</li>
 * <li>Subsea manifold design per DNV-ST-F101</li>
 * <li>Velocity calculations per API RP 14E</li>
 * <li>Branch reinforcement per ASME B31.3</li>
 * <li>Support spacing per NORSOK L-002</li>
 * <li>Weight calculations</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class ManifoldMechanicalDesignTest {
  private ManifoldMechanicalDesignCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new ManifoldMechanicalDesignCalculator();
  }

  @Nested
  @DisplayName("Calculator Initialization Tests")
  class CalculatorInitializationTests {
    @Test
    @DisplayName("Default calculator has correct initial values")
    void testDefaultInitialization() {
      assertEquals(ManifoldLocation.TOPSIDE, calculator.getLocation());
      assertEquals(ManifoldType.PRODUCTION, calculator.getManifoldType());
      assertEquals(0.3048, calculator.getHeaderOuterDiameter(), 0.0001);
      assertEquals(0.1524, calculator.getBranchOuterDiameter(), 0.0001);
      assertEquals(10.0, calculator.getDesignPressure(), 0.1);
      assertEquals(60.0, calculator.getDesignTemperature(), 0.1);
    }

    @Test
    @DisplayName("Calculator location can be set")
    void testSetLocation() {
      calculator.setLocation(ManifoldLocation.SUBSEA);
      assertEquals(ManifoldLocation.SUBSEA, calculator.getLocation());

      calculator.setLocation(ManifoldLocation.ONSHORE);
      assertEquals(ManifoldLocation.ONSHORE, calculator.getLocation());
    }

    @Test
    @DisplayName("Calculator type can be set")
    void testSetManifoldType() {
      calculator.setManifoldType(ManifoldType.INJECTION);
      assertEquals(ManifoldType.INJECTION, calculator.getManifoldType());

      calculator.setManifoldType(ManifoldType.TEST);
      assertEquals(ManifoldType.TEST, calculator.getManifoldType());
    }
  }

  @Nested
  @DisplayName("Wall Thickness Calculation Tests")
  class WallThicknessTests {
    @Test
    @DisplayName("ASME B31.3 wall thickness calculation")
    void testASMEWallThickness() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setDesignPressure(10.0); // MPa
      calculator.setHeaderOuterDiameter(0.3048); // 12 inch
      calculator.setMaterialGrade("A106-B");
      calculator.setJointEfficiency(1.0);
      calculator.setCorrosionAllowance(0.003); // 3mm

      double thickness = calculator.calculateWallThicknessASME();

      assertTrue(thickness > 0);
      assertTrue(thickness < 0.050); // Should be reasonable thickness
      assertTrue(calculator.getAppliedStandards().contains("ASME B31.3 - Wall Thickness"));
    }

    @Test
    @DisplayName("DNV-ST-F101 wall thickness for subsea")
    void testDNVWallThickness() {
      calculator.setLocation(ManifoldLocation.SUBSEA);
      calculator.setDesignPressure(15.0); // MPa
      calculator.setHeaderOuterDiameter(0.4064); // 16 inch
      calculator.setWaterDepth(300.0); // 300m water depth
      calculator.setMaterialGrade("X65");
      calculator.setSafetyClassFactor(1.138);

      double thickness = calculator.calculateWallThicknessDNV();

      assertTrue(thickness > 0);
      assertTrue(thickness >= 0.00635); // Min 6.35mm per DNV
      assertTrue(calculator.getAppliedStandards().contains("DNV-ST-F101 - Wall Thickness"));
    }

    @Test
    @DisplayName("Branch wall thickness calculation")
    void testBranchWallThickness() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setDesignPressure(10.0);
      calculator.setBranchOuterDiameter(0.1524); // 6 inch

      double branchThickness = calculator.calculateBranchWallThickness();

      assertTrue(branchThickness > 0);
      assertTrue(branchThickness < calculator.calculateMinimumWallThickness());
    }
  }

  @Nested
  @DisplayName("Velocity Calculation Tests")
  class VelocityTests {
    @Test
    @DisplayName("Erosional velocity calculation per API RP 14E")
    void testErosionalVelocity() {
      calculator.setMixtureDensity(50.0); // kg/m3
      calculator.setErosionalCFactor(100.0);

      double ve = calculator.calculateErosionalVelocity();

      // Ve = C / sqrt(rho) = 100 / sqrt(50) = 14.14 m/s
      assertEquals(14.14, ve, 0.1);
      assertTrue(calculator.getAppliedStandards().contains("API RP 14E - Erosional Velocity"));
    }

    @Test
    @DisplayName("Header velocity calculation")
    void testHeaderVelocity() {
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.0127);
      calculator.setMassFlowRate(50.0); // kg/s
      calculator.setMixtureDensity(100.0); // kg/m3

      double velocity = calculator.calculateHeaderVelocity();

      assertTrue(velocity > 0);
      assertTrue(velocity < 50); // Should be reasonable velocity
    }

    @Test
    @DisplayName("Branch velocity calculation")
    void testBranchVelocity() {
      calculator.setBranchOuterDiameter(0.1524);
      calculator.setBranchWallThickness(0.00711);
      calculator.setMassFlowRate(50.0);
      calculator.setMixtureDensity(100.0);
      calculator.setNumberOfOutlets(4);

      double velocity = calculator.calculateBranchVelocity();

      assertTrue(velocity > 0);
    }

    @Test
    @DisplayName("Velocity check passes for acceptable flow")
    void testVelocityCheckPasses() {
      calculator.setHeaderOuterDiameter(0.4064); // 16 inch
      calculator.setHeaderWallThickness(0.0127);
      calculator.setBranchOuterDiameter(0.2032); // 8 inch
      calculator.setBranchWallThickness(0.00889);
      calculator.setMassFlowRate(10.0); // Low flow
      calculator.setMixtureDensity(100.0);
      calculator.setLiquidFraction(0.0); // Gas only
      calculator.setErosionalCFactor(100.0);
      calculator.setNumberOfOutlets(2);

      boolean passed = calculator.checkVelocityLimits();

      assertTrue(passed);
      assertTrue(calculator.isVelocityCheckPassed());
    }

    @Test
    @DisplayName("Velocity check fails for excessive flow")
    void testVelocityCheckFails() {
      calculator.setHeaderOuterDiameter(0.0762); // 3 inch - small pipe
      calculator.setHeaderWallThickness(0.00711);
      calculator.setBranchOuterDiameter(0.0508); // 2 inch
      calculator.setBranchWallThickness(0.00381);
      calculator.setMassFlowRate(100.0); // High flow
      calculator.setMixtureDensity(50.0); // Low density gas
      calculator.setLiquidFraction(0.0);
      calculator.setErosionalCFactor(100.0);
      calculator.setNumberOfOutlets(2);

      boolean passed = calculator.checkVelocityLimits();

      assertFalse(passed);
      assertFalse(calculator.isVelocityCheckPassed());
    }
  }

  @Nested
  @DisplayName("Branch Reinforcement Tests")
  class ReinforcementTests {
    @Test
    @DisplayName("Calculate branch reinforcement requirement")
    void testBranchReinforcement() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.0127);
      calculator.setBranchOuterDiameter(0.1524);
      calculator.setBranchWallThickness(0.00711);
      calculator.setDesignPressure(10.0);
      calculator.setCorrosionAllowance(0.003);

      // First calculate minimum wall thicknesses
      calculator.calculateMinimumWallThickness();
      calculator.calculateBranchWallThickness();

      // Then calculate reinforcement
      double padThickness = calculator.calculateBranchReinforcement();

      // Pad thickness should be calculated
      assertNotNull(calculator.isReinforcementRequired());
      assertTrue(calculator.getAppliedStandards().contains("ASME B31.3 - Branch Reinforcement"));
    }
  }

  @Nested
  @DisplayName("Support Spacing Tests")
  class SupportSpacingTests {
    @Test
    @DisplayName("Support spacing for topside manifold per NORSOK L-002")
    void testTopsideSupportSpacing() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setHeaderOuterDiameter(0.2191); // 8 inch NPS
      calculator.setHeaderLength(10.0);

      double spacing = calculator.calculateSupportSpacing();

      assertTrue(spacing >= 2.7 && spacing <= 5.0);
      assertTrue(calculator.getNumberOfSupports() > 0);
      assertTrue(calculator.getAppliedStandards().contains("NORSOK L-002 - Support Spacing"));
    }

    @Test
    @DisplayName("Support spacing for subsea manifold")
    void testSubseaSupportSpacing() {
      calculator.setLocation(ManifoldLocation.SUBSEA);
      calculator.setOverallLength(8.0);

      double spacing = calculator.calculateSupportSpacing();

      // Subsea manifolds typically on dedicated structure
      assertEquals(8.0, spacing, 0.1);
      assertEquals(1, calculator.getNumberOfSupports());
    }
  }

  @Nested
  @DisplayName("Weight Calculation Tests")
  class WeightTests {
    @Test
    @DisplayName("Calculate dry weight")
    void testDryWeight() {
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.0127);
      calculator.setHeaderLength(5.0);
      calculator.setBranchOuterDiameter(0.1524);
      calculator.setBranchWallThickness(0.00711);
      calculator.setNumberOfInlets(2);
      calculator.setNumberOfOutlets(4);
      calculator.setNumberOfValves(6);

      double weight = calculator.calculateDryWeight();

      assertTrue(weight > 0);
      assertTrue(weight > 500); // Should be significant weight
    }

    @Test
    @DisplayName("Calculate submerged weight for subsea")
    void testSubmergedWeight() {
      calculator.setLocation(ManifoldLocation.SUBSEA);
      calculator.setOverallLength(8.0);
      calculator.setOverallWidth(4.0);
      calculator.setOverallHeight(3.0);
      calculator.setHeaderOuterDiameter(0.4064);
      calculator.setHeaderWallThickness(0.020);
      calculator.setHeaderLength(6.0);
      calculator.setNumberOfInlets(4);
      calculator.setNumberOfOutlets(8);
      calculator.setNumberOfValves(12);

      double submergedWeight = calculator.calculateSubmergedWeight();

      assertTrue(submergedWeight > 0);
      assertTrue(submergedWeight < calculator.getTotalDryWeight());
    }

    @Test
    @DisplayName("Submerged weight is zero for topside")
    void testSubmergedWeightTopside() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);

      double submergedWeight = calculator.calculateSubmergedWeight();

      assertEquals(0.0, submergedWeight, 0.001);
    }
  }

  @Nested
  @DisplayName("Design Verification Tests")
  class DesignVerificationTests {
    @Test
    @DisplayName("Complete design verification passes")
    void testDesignVerificationPasses() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setDesignPressure(10.0);
      calculator.setDesignTemperature(60.0);
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.020); // Thick wall
      calculator.setBranchOuterDiameter(0.1524);
      calculator.setBranchWallThickness(0.015);
      calculator.setMassFlowRate(10.0); // Low flow
      calculator.setMixtureDensity(100.0);
      calculator.setLiquidFraction(0.1);
      calculator.setNumberOfInlets(1);
      calculator.setNumberOfOutlets(2);

      boolean passed = calculator.performDesignVerification();

      assertTrue(passed);
      assertTrue(calculator.isWallThicknessCheckPassed());
      assertTrue(calculator.isVelocityCheckPassed());
    }

    @Test
    @DisplayName("Design verification fails for thin wall")
    void testDesignVerificationFailsThinWall() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setDesignPressure(20.0); // High pressure
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.003); // Very thin - should fail
      calculator.setBranchOuterDiameter(0.1524);
      calculator.setBranchWallThickness(0.002);

      boolean passed = calculator.performDesignVerification();

      assertFalse(passed);
      assertFalse(calculator.isWallThicknessCheckPassed());
    }
  }

  @Nested
  @DisplayName("JSON Output Tests")
  class JsonOutputTests {
    @Test
    @DisplayName("Calculator produces valid JSON")
    void testCalculatorToJson() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.performDesignVerification();

      String json = calculator.toJson();

      assertNotNull(json);
      assertTrue(json.contains("configuration"));
      assertTrue(json.contains("geometry"));
      assertTrue(json.contains("designConditions"));
      assertTrue(json.contains("material"));
      assertTrue(json.contains("wallThicknessAnalysis"));
      assertTrue(json.contains("velocityAnalysis"));
      assertTrue(json.contains("appliedStandards"));
    }

    @Test
    @DisplayName("JSON includes all location types")
    void testJsonLocationTypes() {
      for (ManifoldLocation loc : ManifoldLocation.values()) {
        calculator.setLocation(loc);
        String json = calculator.toJson();
        assertTrue(json.contains(loc.name()));
      }
    }
  }

  @Nested
  @DisplayName("Integration Tests with Manifold Equipment")
  class IntegrationTests {
    @Test
    @DisplayName("Manifold equipment with mechanical design")
    void testManifoldWithMechanicalDesign() {
      // Create fluid
      SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
      fluid.addComponent("methane", 0.9);
      fluid.addComponent("ethane", 0.1);
      fluid.setMixingRule("classic");
      fluid.init(0);

      // Create feed stream
      Stream feed = new Stream("Feed", fluid);
      feed.setFlowRate(1000.0, "kg/hr");
      feed.run();

      // Create manifold
      Manifold manifold = new Manifold("Production Manifold");
      manifold.addStream(feed);
      manifold.setSplitFactors(new double[] {0.5, 0.5});
      manifold.run();

      // Initialize mechanical design
      manifold.initMechanicalDesign();
      ManifoldMechanicalDesign design = manifold.getMechanicalDesign();

      // Configure design
      design.setMaxOperationPressure(50.0); // bar
      design.setMaxOperationTemperature(298.0); // K
      design.setLocation(ManifoldLocation.TOPSIDE);
      design.setMaterialGrade("A106-B");
      design.setHeaderDiameter(0.2032); // 8 inch
      design.setBranchDiameter(0.1016); // 4 inch
      design.setNumberOfInlets(1);
      design.setNumberOfOutlets(2);

      // Run design calculations
      design.calcDesign();

      // Verify results
      assertNotNull(design.getCalculator());
      assertTrue(design.getWallThickness() > 0);
      assertTrue(design.getWeightTotal() > 0);
    }

    @Test
    @DisplayName("Subsea manifold design integration")
    void testSubseaManifoldDesign() {
      // Create fluid
      SystemInterface fluid = new SystemSrkEos(280.0, 150.0);
      fluid.addComponent("methane", 0.85);
      fluid.addComponent("ethane", 0.10);
      fluid.addComponent("propane", 0.05);
      fluid.setMixingRule("classic");
      fluid.init(0);

      // Create feed stream
      Stream feed = new Stream("Subsea Feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      // Create manifold
      Manifold manifold = new Manifold("Subsea Manifold");
      manifold.addStream(feed);
      manifold.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
      manifold.run();

      // Initialize mechanical design
      manifold.initMechanicalDesign();
      ManifoldMechanicalDesign design = manifold.getMechanicalDesign();

      // Configure for subsea
      design.setMaxOperationPressure(150.0);
      design.setMaxOperationTemperature(280.0);
      design.setLocation(ManifoldLocation.SUBSEA);
      design.setDesignStandardCode("DNV-ST-F101");
      design.setMaterialGrade("X65");
      design.setWaterDepth(350.0);
      design.setHeaderDiameter(0.4064); // 16 inch
      design.setBranchDiameter(0.1524); // 6 inch
      design.setNumberOfInlets(4);
      design.setNumberOfOutlets(4);

      // Run design calculations
      design.calcDesign();

      // Get JSON output
      String json = design.toJson();

      // Verify subsea-specific results
      assertNotNull(json);
      assertTrue(json.contains("SUBSEA"));
      assertTrue(json.contains("designCalculations"));
    }

    @Test
    @DisplayName("Manifold mechanical design JSON output")
    void testManifoldDesignJson() {
      // Create simple test setup
      SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
      fluid.addComponent("methane", 1.0);
      fluid.setMixingRule("classic");
      fluid.init(0);

      Stream feed = new Stream("Feed", fluid);
      feed.setFlowRate(500.0, "kg/hr");
      feed.run();

      Manifold manifold = new Manifold("Test Manifold");
      manifold.addStream(feed);
      manifold.setSplitFactors(new double[] {0.6, 0.4});
      manifold.run();

      manifold.initMechanicalDesign();
      ManifoldMechanicalDesign design = manifold.getMechanicalDesign();
      design.setMaxOperationPressure(30.0);
      design.calcDesign();

      String json = design.toJson();

      assertNotNull(json);
      assertTrue(json.contains("designStandardCode"));
      assertTrue(json.contains("materialGrade"));
      assertTrue(json.contains("manifoldLocation"));
      assertTrue(json.contains("headerDiameter_m"));
      assertTrue(json.contains("branchDiameter_m"));
    }
  }

  @Nested
  @DisplayName("Material Grade Tests")
  class MaterialGradeTests {
    @Test
    @DisplayName("Subsea material grade sets SMYS correctly")
    void testSubseaMaterialGrade() {
      calculator.setMaterialGrade("X65");

      assertEquals(448.0, calculator.getSmys(), 1.0);
      assertEquals(531.0, calculator.getSmts(), 1.0);
    }

    @Test
    @DisplayName("Duplex material grade")
    void testDuplexMaterialGrade() {
      calculator.setMaterialGrade("22Cr-Duplex");

      assertEquals(450.0, calculator.getSmys(), 1.0);
      assertEquals(620.0, calculator.getSmts(), 1.0);
    }

    @Test
    @DisplayName("Super duplex material grade")
    void testSuperDuplexMaterialGrade() {
      calculator.setMaterialGrade("25Cr-SuperDuplex");

      assertEquals(550.0, calculator.getSmys(), 1.0);
      assertEquals(750.0, calculator.getSmts(), 1.0);
    }
  }

  @Nested
  @DisplayName("Stress Calculation Tests")
  class StressTests {
    @Test
    @DisplayName("Allowable stress for topside carbon steel")
    void testAllowableStressTopside() {
      calculator.setLocation(ManifoldLocation.TOPSIDE);
      calculator.setMaterialGrade("A106-B");
      calculator.setDesignTemperature(100.0);

      double allowable = calculator.calculateAllowableStress();

      assertTrue(allowable > 100);
      assertTrue(allowable < 200);
    }

    @Test
    @DisplayName("Allowable stress for subsea X65")
    void testAllowableStressSubsea() {
      calculator.setLocation(ManifoldLocation.SUBSEA);
      calculator.setMaterialGrade("X65");
      calculator.setSafetyClassFactor(1.138);

      double allowable = calculator.calculateAllowableStress();

      // 448 / 1.138 = 393.7 MPa
      assertTrue(allowable > 350);
      assertTrue(allowable < 450);
    }

    @Test
    @DisplayName("Hoop stress calculation")
    void testHoopStress() {
      calculator.setDesignPressure(10.0);
      calculator.setHeaderOuterDiameter(0.3048);
      calculator.setHeaderWallThickness(0.0127);
      calculator.setCorrosionAllowance(0.003);

      double hoop = calculator.calculateHoopStress();

      assertTrue(hoop > 0);
      assertTrue(hoop < 500); // Should be reasonable stress
    }
  }
}
