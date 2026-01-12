package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment;

/**
 * Tests for the pipe wall heat transfer system including: - PipeMaterial enum database -
 * MaterialLayer with factory methods - PipeWall with cylindrical heat transfer -
 * PipeSurroundingEnvironment with buried/subsea models - PipeWallBuilder convenience patterns
 */
public class PipeWallHeatTransferTest {

  // ===== PipeMaterial Tests =====

  @Test
  void testPipeMaterialProperties() {
    // Carbon steel
    assertEquals(50.0, PipeMaterial.CARBON_STEEL.getThermalConductivity(), 0.1);
    assertEquals(7850.0, PipeMaterial.CARBON_STEEL.getDensity(), 1.0);
    assertEquals(490.0, PipeMaterial.CARBON_STEEL.getSpecificHeatCapacity(), 1.0);
    assertTrue(PipeMaterial.CARBON_STEEL.isMetal());
    assertFalse(PipeMaterial.CARBON_STEEL.isInsulation());

    // Mineral wool insulation
    assertEquals(0.04, PipeMaterial.MINERAL_WOOL.getThermalConductivity(), 0.001);
    assertTrue(PipeMaterial.MINERAL_WOOL.isInsulation());
    assertFalse(PipeMaterial.MINERAL_WOOL.isMetal());

    // Soil
    assertTrue(PipeMaterial.SOIL_TYPICAL.isSoil());
    assertFalse(PipeMaterial.CARBON_STEEL.isSoil());
  }

  @Test
  void testPipeMaterialThermalDiffusivity() {
    double alpha = PipeMaterial.CARBON_STEEL.getThermalDiffusivity();
    // α = k / (ρ * Cp) = 50 / (7850 * 490) ≈ 1.3e-5 m²/s
    double expected = 50.0 / (7850.0 * 490.0);
    assertEquals(expected, alpha, 1e-8);
  }

  @Test
  void testPipeMaterialCreateLayer() {
    MaterialLayer layer = PipeMaterial.CARBON_STEEL.createLayer(0.015);
    assertEquals(0.015, layer.getThickness(), 1e-6);
    assertEquals(50.0, layer.getConductivity(), 0.1);
    assertEquals("Carbon Steel", layer.getMaterialName());
    assertEquals(PipeMaterial.CARBON_STEEL, layer.getPipeMaterial());
  }

  // ===== MaterialLayer Tests =====

  @Test
  void testMaterialLayerFactoryMethods() {
    MaterialLayer steel = MaterialLayer.carbonSteel(0.010);
    assertEquals(50.0, steel.getConductivity(), 0.1);
    assertEquals(0.010, steel.getThickness(), 1e-6);

    MaterialLayer insulation = MaterialLayer.mineralWool(0.050);
    assertEquals(0.04, insulation.getConductivity(), 0.001);
    assertEquals(0.050, insulation.getThickness(), 1e-6);
  }

  @Test
  void testMaterialLayerPlanarHeatTransfer() {
    MaterialLayer layer = MaterialLayer.carbonSteel(0.010);
    // h = k/t = 50/0.01 = 5000 W/(m²·K)
    assertEquals(5000.0, layer.getHeatTransferCoefficient(), 0.1);
    // R = t/k = 0.01/50 = 0.0002 m²·K/W
    assertEquals(0.0002, layer.getThermalResistance(), 1e-6);
  }

  @Test
  void testMaterialLayerCylindricalResistance() {
    MaterialLayer layer = MaterialLayer.carbonSteel(0.010);
    double innerRadius = 0.10; // 0.1m inner radius

    // R' = ln(r_outer/r_inner) / (2π * k)
    double rOuter = innerRadius + 0.010;
    double expected = Math.log(rOuter / innerRadius) / (2 * Math.PI * 50.0);
    assertEquals(expected, layer.getCylindricalThermalResistance(innerRadius), 1e-8);
  }

  @Test
  void testMaterialLayerThermalMass() {
    MaterialLayer layer = MaterialLayer.carbonSteel(0.010);
    // Thermal mass per area = ρ * t * Cp = 7850 * 0.01 * 490
    double expected = 7850.0 * 0.010 * 490.0;
    assertEquals(expected, layer.getThermalMassPerArea(), 1.0);
  }

  // ===== PipeWall Cylindrical Heat Transfer Tests =====

  @Test
  void testPipeWallSingleLayer() {
    PipeWall wall = new PipeWall(0.10); // 0.1m inner radius
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));

    assertEquals(0.10, wall.getInnerRadius(), 1e-6);
    assertEquals(0.11, wall.getOuterRadius(), 1e-6);
    assertEquals(0.010, wall.getTotalThickness(), 1e-6);
    assertEquals(1, wall.getNumberOfLayers());
  }

  @Test
  void testPipeWallCylindricalHeatTransferCoefficient() {
    PipeWall wall = new PipeWall(0.10);
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));

    // U = 1 / (r_inner * ln(r_outer/r_inner) / k)
    double rInner = 0.10;
    double rOuter = 0.11;
    double k = 50.0;
    double expected = 1.0 / (rInner * Math.log(rOuter / rInner) / k);

    assertEquals(expected, wall.calcCylindricalHeatTransferCoefficient(), 0.1);
  }

  @Test
  void testPipeWallMultiLayer() {
    PipeWall wall = new PipeWall(0.10);
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010)); // Steel
    wall.addMaterialLayer(MaterialLayer.mineralWool(0.050)); // Insulation

    assertEquals(0.10, wall.getInnerRadius(), 1e-6);
    assertEquals(0.16, wall.getOuterRadius(), 1e-6); // 0.10 + 0.01 + 0.05
    assertEquals(0.060, wall.getTotalThickness(), 1e-6);
    assertEquals(2, wall.getNumberOfLayers());

    // Check layer radii
    assertEquals(0.11, wall.getLayerOuterRadius(0), 1e-6); // Steel ends at 0.11
    assertEquals(0.16, wall.getLayerOuterRadius(1), 1e-6); // Insulation ends at 0.16
    assertEquals(0.10, wall.getLayerInnerRadius(0), 1e-6); // Steel starts at 0.10
    assertEquals(0.11, wall.getLayerInnerRadius(1), 1e-6); // Insulation starts at 0.11
  }

  @Test
  void testPipeWallInsulatedPipeUValue() {
    // Create an insulated pipe: steel + mineral wool
    PipeWall wall = new PipeWall(0.10);
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));
    wall.addMaterialLayer(MaterialLayer.mineralWool(0.050));

    // Calculate expected U-value
    double rInner = 0.10;
    double r1 = 0.11; // After steel
    double r2 = 0.16; // After insulation

    double R1 = Math.log(r1 / rInner) / 50.0; // Steel
    double R2 = Math.log(r2 / r1) / 0.04; // Mineral wool

    double expectedU = 1.0 / (rInner * (R1 + R2));
    assertEquals(expectedU, wall.calcCylindricalHeatTransferCoefficient(), 0.01);

    // Insulation dominates: U-value should be much lower than steel alone
    PipeWall bareWall = new PipeWall(0.10);
    bareWall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));
    assertTrue(wall.calcCylindricalHeatTransferCoefficient() < bareWall
        .calcCylindricalHeatTransferCoefficient() / 10);
  }

  @Test
  void testPipeWallHeatLoss() {
    PipeWall wall = new PipeWall(0.10);
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));
    wall.addMaterialLayer(MaterialLayer.mineralWool(0.050));

    double innerTemp = 350.0; // K
    double outerTemp = 290.0; // K
    double deltaT = innerTemp - outerTemp;

    double heatLoss = wall.calcHeatLossPerLength(innerTemp, outerTemp);
    double resistance = wall.calcCylindricalThermalResistancePerLength();

    // Q/L = ΔT / R'
    assertEquals(deltaT / resistance, heatLoss, 0.1);
    assertTrue(heatLoss > 0); // Heat flows from hot to cold
  }

  @Test
  void testPipeWallTemperatureProfile() {
    PipeWall wall = new PipeWall(0.10);
    wall.addMaterialLayer(MaterialLayer.carbonSteel(0.010));

    double innerTemp = 350.0;
    double outerTemp = 300.0;

    // At inner radius -> inner temperature
    assertEquals(innerTemp, wall.calcTemperatureAtRadius(0.10, innerTemp, outerTemp), 0.01);

    // At outer radius -> outer temperature
    assertEquals(outerTemp, wall.calcTemperatureAtRadius(0.11, innerTemp, outerTemp), 0.01);

    // At midpoint -> intermediate temperature
    double midRadius = 0.105;
    double midTemp = wall.calcTemperatureAtRadius(midRadius, innerTemp, outerTemp);
    assertTrue(midTemp < innerTemp);
    assertTrue(midTemp > outerTemp);
  }

  // ===== PipeSurroundingEnvironment Tests =====

  @Test
  void testAirEnvironment() {
    PipeSurroundingEnvironment env = PipeSurroundingEnvironment.exposedToAir(293.0, 5.0);
    assertEquals(PipeSurroundingEnvironment.EnvironmentType.AIR, env.getEnvironmentType());
    assertEquals(293.0, env.getTemperature(), 0.1);
    assertTrue(env.getHeatTransferCoefficient() > 5.0); // Natural + forced convection
  }

  @Test
  void testSeawaterEnvironment() {
    PipeSurroundingEnvironment env = PipeSurroundingEnvironment.subseaPipe(277.0, 0.5);
    assertEquals(PipeSurroundingEnvironment.EnvironmentType.SEAWATER, env.getEnvironmentType());
    assertEquals(277.0, env.getTemperature(), 0.1);
    assertTrue(env.getHeatTransferCoefficient() > 300.0); // High h for seawater
    assertTrue(env.isSubsea());
  }

  @Test
  void testBuriedPipeEnvironment() {
    double depth = 1.5; // m
    double outerRadius = 0.15; // m
    PipeSurroundingEnvironment env =
        PipeSurroundingEnvironment.buriedPipe(283.0, depth, outerRadius, PipeMaterial.SOIL_TYPICAL);

    assertEquals(PipeSurroundingEnvironment.EnvironmentType.BURIED, env.getEnvironmentType());
    assertEquals(283.0, env.getTemperature(), 0.1);
    assertTrue(env.isBuried());
    assertTrue(env.getHeatTransferCoefficient() > 0);
  }

  @Test
  void testBuriedPipeThermalResistance() {
    double depth = 1.5;
    double outerRadius = 0.15;
    double kSoil = 1.0;

    double resistance =
        PipeSurroundingEnvironment.calcBuriedPipeThermalResistance(depth, outerRadius, kSoil);

    // Shape factor method: R = ln(2z/r + sqrt((2z/r)² - 1)) / (2π * k)
    double ratio = depth / outerRadius;
    double expected =
        Math.log(2.0 * ratio + Math.sqrt(4.0 * ratio * ratio - 1)) / (2.0 * Math.PI * kSoil);

    assertEquals(expected, resistance, 1e-6);
  }

  // ===== PipeWallBuilder Tests =====

  @Test
  void testBuilderBarePipe() {
    PipeWall wall = PipeWallBuilder.carbonSteelPipe(0.20, 0.010).build();

    assertEquals(0.10, wall.getInnerRadius(), 1e-6); // diameter/2
    assertEquals(0.11, wall.getOuterRadius(), 1e-6);
    assertEquals(1, wall.getNumberOfLayers());
  }

  @Test
  void testBuilderInsulatedPipe() {
    PipeWall wall =
        PipeWallBuilder.carbonSteelPipe(0.20, 0.010).addMineralWoolInsulation(0.050).build();

    assertEquals(2, wall.getNumberOfLayers());
    assertEquals(0.16, wall.getOuterRadius(), 1e-6);
  }

  @Test
  void testBuilderSubseaPipe() {
    PipeWall wall = PipeWallBuilder.subseaPipe(0.25, 0.020, 0.040, 0.070).build();

    // Should have: steel + FBE + polypropylene + concrete
    assertEquals(4, wall.getNumberOfLayers());
    assertTrue(wall.getOuterRadius() > 0.125 + 0.020 + 0.040 + 0.070); // Account for FBE
  }

  @Test
  void testBuilderWithEnvironment() {
    PipeWallBuilder builder = PipeWallBuilder.carbonSteelPipe(0.20, 0.010)
        .addMineralWoolInsulation(0.050).exposedToAir(293.0, 2.0);

    PipeWall wall = builder.build();
    PipeSurroundingEnvironment env = builder.buildEnvironment();

    assertEquals(2, wall.getNumberOfLayers());
    assertEquals(PipeSurroundingEnvironment.EnvironmentType.AIR, env.getEnvironmentType());
    assertEquals(293.0, env.getTemperature(), 0.1);
  }

  @Test
  void testBuilderOverallUValue() {
    PipeWallBuilder builder = PipeWallBuilder.carbonSteelPipe(0.20, 0.010)
        .addMineralWoolInsulation(0.050).exposedToAir(293.0, 2.0);

    double innerFilmCoeff = 1000.0; // W/(m²·K) - typical for flowing liquid
    double overallU = builder.calcOverallUValue(innerFilmCoeff);

    // Overall U should be less than any individual coefficient
    assertTrue(overallU < innerFilmCoeff);
    assertTrue(overallU > 0);
  }

  // ===== PipeData Integration Tests =====

  @Test
  void testPipeDataWithMaterial() {
    PipeData pipe = new PipeData(0.20);
    pipe.setCarbonSteelWall(0.010);

    assertEquals(0.10, pipe.getPipeWall().getInnerRadius(), 1e-6);
    assertEquals(0.11, pipe.getOuterRadius(), 1e-6);
    assertEquals(0.010, pipe.getTotalWallThickness(), 1e-6);
  }

  @Test
  void testPipeDataInsulated() {
    PipeData pipe = new PipeData(0.20);
    pipe.setCarbonSteelWall(0.010);
    pipe.addMineralWoolInsulation(0.050);

    assertEquals(2, pipe.getPipeWall().getNumberOfLayers());
    assertEquals(0.16, pipe.getOuterRadius(), 1e-6);
  }

  @Test
  void testPipeDataFromBuilder() {
    PipeData pipe = PipeData.createFromBuilder(PipeWallBuilder.carbonSteelPipe(0.20, 0.010)
        .addMineralWoolInsulation(0.050).exposedToAir(293.0, 2.0));

    assertEquals(0.20, pipe.getDiameter(), 1e-6);
    assertEquals(2, pipe.getPipeWall().getNumberOfLayers());
    assertEquals(293.0, pipe.getSurroundingEnvironment().getTemperature(), 0.1);
  }

  @Test
  void testPipeDataOverallHeatTransfer() {
    PipeData pipe = new PipeData(0.20);
    pipe.setCarbonSteelWall(0.010);
    pipe.addMineralWoolInsulation(0.050);
    pipe.setAirEnvironment(293.0, 2.0);

    double overallU = pipe.calcOverallHeatTransferCoefficient();
    assertTrue(overallU > 0);

    // Insulated pipe should have lower U than bare pipe
    PipeData barePipe = new PipeData(0.20);
    barePipe.setCarbonSteelWall(0.010);
    barePipe.setAirEnvironment(293.0, 2.0);

    assertTrue(
        pipe.calcOverallHeatTransferCoefficient() < barePipe.calcOverallHeatTransferCoefficient());
  }

  // ===== Validation Tests =====

  @Test
  void testCylindricalVsPlanarComparison() {
    // For thin walls relative to radius, cylindrical ≈ planar
    // For thick walls or small radius, they differ significantly

    // Small radius, thick insulation - should see significant difference
    PipeWall smallPipe = new PipeWall(0.025); // 50mm diameter
    smallPipe.addMaterialLayer(MaterialLayer.mineralWool(0.050)); // 50mm insulation

    // Planar approximation
    double planarR = 0.050 / 0.04; // t/k
    double planarU = 1.0 / planarR;

    // Cylindrical
    double cylU = smallPipe.calcCylindricalHeatTransferCoefficient();

    // For small pipe with thick insulation, cylindrical U > planar U
    // (because outer surface area is larger, reducing resistance)
    assertTrue(Math.abs(cylU - planarU) / planarU > 0.1,
        "Cylindrical and planar should differ for small pipes");
  }

  @Test
  void testMaterialDatabaseCoverage() {
    // Ensure we have reasonable coverage of common materials
    assertTrue(PipeMaterial.values().length >= 25, "Should have at least 25 materials");

    // Check we have categories
    long metals =
        java.util.Arrays.stream(PipeMaterial.values()).filter(PipeMaterial::isMetal).count();
    long insulations =
        java.util.Arrays.stream(PipeMaterial.values()).filter(PipeMaterial::isInsulation).count();
    long soils =
        java.util.Arrays.stream(PipeMaterial.values()).filter(PipeMaterial::isSoil).count();

    assertTrue(metals >= 5, "Should have at least 5 metals");
    assertTrue(insulations >= 5, "Should have at least 5 insulation types");
    assertTrue(soils >= 4, "Should have at least 4 soil types");
  }
}
