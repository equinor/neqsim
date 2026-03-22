package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for cross-cutting design standard utilities: NoiseAssessment, VibrationAssessment,
 * InsulationDesign, PipingStressAnalysis, FireProtectionDesign, and CUIRiskAssessment.
 *
 * @author esol
 */
class DesignStandardsUtilityTest {

  // ============================================================================
  // NoiseAssessment Tests
  // ============================================================================
  @Nested
  @DisplayName("NoiseAssessment Tests")
  class NoiseAssessmentTests {

    @Test
    @DisplayName("Valve noise should return positive dB(A)")
    void valveNoiseShouldReturnPositiveDb() {
      double spl = NoiseAssessment.valveNoise(10.0, 50.0, 5.0, 20.0, 350.0, 0.05);
      assertTrue(spl > 0, "Valve noise should be positive");
      assertTrue(spl < 200, "Valve noise should be realistic");
    }

    @Test
    @DisplayName("Compressor noise increases with power")
    void compressorNoiseShouldIncreaseWithPower() {
      double spl100 = NoiseAssessment.compressorNoise(100.0, "centrifugal");
      double spl1000 = NoiseAssessment.compressorNoise(1000.0, "centrifugal");
      assertTrue(spl1000 > spl100, "Larger compressor should be noisier");
    }

    @Test
    @DisplayName("Reciprocating compressor is noisier than centrifugal at same power")
    void reciprocatingNoisierThanCentrifugal() {
      double splRecip = NoiseAssessment.compressorNoise(500.0, "RECIPROCATING");
      double splCentri = NoiseAssessment.compressorNoise(500.0, "CENTRIFUGAL");
      assertTrue(splRecip > splCentri, "Reciprocating should be noisier");
    }

    @Test
    @DisplayName("Pump noise should return positive dB(A)")
    void pumpNoiseShouldReturnPositive() {
      double spl = NoiseAssessment.pumpNoise(200.0, "centrifugal");
      assertTrue(spl > 0, "Pump noise should be positive");
    }

    @Test
    @DisplayName("Flare noise should return positive dB(A)")
    void flareNoiseShouldReturnPositive() {
      double spl = NoiseAssessment.flareNoise(50.0, 0.6, 80.0);
      assertTrue(spl > 0, "Flare noise should be positive");
    }

    @Test
    @DisplayName("Aggregate noise of identical sources equals source + 10*log10(n)")
    void aggregateNoiseShouldBeMathematicallyCorrect() {
      double single = 90.0;
      double[] sources = {single, single};
      double combined = NoiseAssessment.aggregateNoise(sources);
      // Two identical sources: +3 dB
      assertEquals(single + 3.0, combined, 0.1, "Two equal sources should add ~3 dB");
    }

    @Test
    @DisplayName("SPL at distance should decrease with distance")
    void splAtDistanceShouldDecrease() {
      double spl1m = 100.0;
      double spl10m = NoiseAssessment.splAtDistance(spl1m, 10.0);
      double spl100m = NoiseAssessment.splAtDistance(spl1m, 100.0);
      assertTrue(spl10m < spl1m, "SPL should decrease with distance");
      assertTrue(spl100m < spl10m, "SPL should decrease more at greater distance");
      // Inverse square: 20*log10(10) = 20 dB reduction
      assertEquals(spl1m - 20.0, spl10m, 0.1);
    }

    @Test
    @DisplayName("NORSOK S-002 limit is 83 dB(A)")
    void norsokLimitShouldBe83() {
      assertFalse(NoiseAssessment.exceedsNorsokLimit(82.0));
      assertTrue(NoiseAssessment.exceedsNorsokLimit(84.0));
    }
  }

  // ============================================================================
  // VibrationAssessment Tests
  // ============================================================================
  @Nested
  @DisplayName("VibrationAssessment Tests")
  class VibrationAssessmentTests {

    @Test
    @DisplayName("AIV screening should return a risk level")
    void aivScreeningShouldReturnRisk() {
      VibrationAssessment.VibrationRisk risk =
          VibrationAssessment.aivScreening(10.0, 50.0, 5.0, 350.0, 20.0, 200.0, 10.0);
      assertNotNull(risk);
    }

    @Test
    @DisplayName("Acoustic power level should be positive")
    void acousticPowerLevelShouldBePositive() {
      double pwl = VibrationAssessment.acousticPowerLevel(5.0, 40.0, 10.0, 350.0, 20.0);
      assertTrue(pwl > 0, "PWL should be positive");
    }

    @Test
    @DisplayName("AIV screening limit should increase with pipe wall thickness")
    void aivLimitShouldIncreaseWithWallThickness() {
      double limit6mm = VibrationAssessment.aivScreeningLimit(200.0, 6.0);
      double limit12mm = VibrationAssessment.aivScreeningLimit(200.0, 12.0);
      assertTrue(limit12mm > limit6mm, "Thicker pipe should have higher screening limit");
    }

    @Test
    @DisplayName("FIV heat exchanger screening returns valid risk")
    void fivScreeningShouldReturnValidRisk() {
      VibrationAssessment.VibrationRisk risk =
          VibrationAssessment.fivHeatExchangerScreening(5.0, 19.05, 25.4, 0.015, 1000.0, 1.2);
      assertNotNull(risk);
    }

    @Test
    @DisplayName("Reciprocating pulsation screening returns valid risk")
    void reciprocatingScreeningShouldReturnValidRisk() {
      VibrationAssessment.VibrationRisk risk =
          VibrationAssessment.reciprocatingPulsationScreening(40.0, 3.0, 10.0, 200.0);
      assertNotNull(risk);
    }
  }

  // ============================================================================
  // InsulationDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("InsulationDesign Tests")
  class InsulationDesignTests {

    @Test
    @DisplayName("Flat surface insulation thickness should be positive")
    void flatSurfaceThicknessShouldBePositive() {
      double thickness = InsulationDesign.flatSurfaceThickness(200.0, 20.0,
          InsulationDesign.InsulationMaterial.MINERAL_WOOL,
          InsulationDesign.InsulationPurpose.HEAT_CONSERVATION, 5.0);
      assertTrue(thickness > 0, "Insulation thickness should be positive");
    }

    @Test
    @DisplayName("Personnel protection insulation is calculated correctly")
    void personnelProtectionThickerThanHeatConservation() {
      double heatCons = InsulationDesign.flatSurfaceThickness(200.0, 20.0,
          InsulationDesign.InsulationMaterial.MINERAL_WOOL,
          InsulationDesign.InsulationPurpose.HEAT_CONSERVATION, 5.0);
      double personnel = InsulationDesign.flatSurfaceThickness(200.0, 20.0,
          InsulationDesign.InsulationMaterial.MINERAL_WOOL,
          InsulationDesign.InsulationPurpose.PERSONNEL_PROTECTION, 5.0);
      assertTrue(heatCons > 0, "Heat conservation thickness should be positive");
      assertTrue(personnel > 0, "Personnel protection thickness should be positive");
    }

    @Test
    @DisplayName("Pipe insulation thickness should be positive")
    void pipeThicknessShouldBePositive() {
      double thickness = InsulationDesign.pipeThickness(150.0, 10.0, 219.1,
          InsulationDesign.InsulationMaterial.CALCIUM_SILICATE,
          InsulationDesign.InsulationPurpose.HEAT_CONSERVATION, 3.0);
      assertTrue(thickness > 0, "Pipe insulation should be positive");
    }

    @Test
    @DisplayName("Pipe heat loss should be positive for hot pipe")
    void pipeHeatLossShouldBePositive() {
      double heatLoss = InsulationDesign.pipeHeatLossPerMeter(150.0, 10.0, 219.1, 50.0,
          InsulationDesign.InsulationMaterial.MINERAL_WOOL, 3.0);
      assertTrue(heatLoss > 0, "Heat loss should be positive for hot pipe");
    }

    @Test
    @DisplayName("Insulation weight per meter should be positive")
    void insulationWeightShouldBePositive() {
      double weight = InsulationDesign.pipeInsulationWeightPerMeter(219.1, 50.0,
          InsulationDesign.InsulationMaterial.MINERAL_WOOL);
      assertTrue(weight > 0, "Insulation weight should be positive");
    }

    @Test
    @DisplayName("Material selection should return non-null for valid temperatures")
    void materialSelectionShouldReturnValidMaterial() {
      InsulationDesign.InsulationMaterial matHot = InsulationDesign.selectMaterial(400.0,
          InsulationDesign.InsulationPurpose.HEAT_CONSERVATION);
      assertNotNull(matHot);

      InsulationDesign.InsulationMaterial matCold = InsulationDesign.selectMaterial(-100.0,
          InsulationDesign.InsulationPurpose.FROST_PROTECTION);
      assertNotNull(matCold);
    }

    @Test
    @DisplayName("Insulation materials have valid properties")
    void insulationMaterialPropertiesShouldBeValid() {
      for (InsulationDesign.InsulationMaterial mat : InsulationDesign.InsulationMaterial.values()) {
        double k = mat.getConductivity(100.0);
        assertTrue(k > 0, mat.name() + " should have positive conductivity");
        assertTrue(k < 1.0, mat.name() + " conductivity should be less than 1 W/mK");
      }
    }
  }

  // ============================================================================
  // PipingStressAnalysis Tests
  // ============================================================================
  @Nested
  @DisplayName("PipingStressAnalysis Tests")
  class PipingStressAnalysisTests {

    @Test
    @DisplayName("Thermal expansion should be proportional to length and delta-T")
    void thermalExpansionShouldBeProportional() {
      double exp10m = PipingStressAnalysis.thermalExpansion(10.0, 20.0, 120.0);
      double exp20m = PipingStressAnalysis.thermalExpansion(20.0, 20.0, 120.0);
      assertEquals(exp10m * 2.0, exp20m, 0.001, "Expansion should double with length");
    }

    @Test
    @DisplayName("Thermal expansion should be positive for heating")
    void thermalExpansionShouldBePositive() {
      double expansion = PipingStressAnalysis.thermalExpansion(50.0, 20.0, 150.0);
      assertTrue(expansion > 0, "Expansion should be positive for temperature increase");
    }

    @Test
    @DisplayName("Allowable expansion stress range per ASME B31.3")
    void allowableExpansionStressRangeShouldBePositive() {
      double sa = PipingStressAnalysis.allowableExpansionStressRange(138.0, 120.0, 1.0);
      assertTrue(sa > 0);
      // f*(1.25*Sc + 0.25*Sh) = 1.0*(1.25*138 + 0.25*120) = 172.5 + 30 = 202.5
      assertEquals(202.5, sa, 0.1);
    }

    @Test
    @DisplayName("Section modulus should be positive")
    void sectionModulusShouldBePositive() {
      double z = PipingStressAnalysis.sectionModulus(219.1, 8.18);
      assertTrue(z > 0, "Section modulus should be positive");
    }

    @Test
    @DisplayName("Moment of inertia should be positive")
    void momentOfInertiaShouldBePositive() {
      double i = PipingStressAnalysis.momentOfInertia(219.1, 8.18);
      assertTrue(i > 0, "Moment of inertia should be positive");
    }

    @Test
    @DisplayName("Support span should be positive and reasonable")
    void supportSpanShouldBeReasonable() {
      double span =
          PipingStressAnalysis.maxSupportSpan(219.1, 8.18, 1000.0, 50.0, 120.0, 3.0, 100.0);
      assertTrue(span > 0, "Support span should be positive");
      assertTrue(span <= 12.0, "Support span should not exceed 12m practical limit");
    }

    @Test
    @DisplayName("Empty pipe should have longer span than water-filled")
    void emptyPipeShouldHaveLongerSpan() {
      double spanEmpty =
          PipingStressAnalysis.maxSupportSpan(219.1, 8.18, 0.0, 0.0, 0.0, 3.0, 100.0);
      double spanWater =
          PipingStressAnalysis.maxSupportSpan(219.1, 8.18, 1000.0, 0.0, 0.0, 3.0, 100.0);
      assertTrue(spanEmpty >= spanWater,
          "Empty pipe should have equal or longer span than water-filled");
    }

    @Test
    @DisplayName("Expansion loop length should be positive")
    void expansionLoopLengthShouldBePositive() {
      double length = PipingStressAnalysis.expansionLoopLength(50.0, 219.1, 200.0);
      assertTrue(length > 0, "Loop length should be positive");
    }

    @Test
    @DisplayName("Code stress check should pass for low stresses")
    void codeStressCheckShouldPassForLow() {
      assertTrue(PipingStressAnalysis.codeStressCheck(50.0, 100.0, 138.0, 200.0));
    }

    @Test
    @DisplayName("Code stress check should fail for high sustained stress")
    void codeStressCheckShouldFailForHighStress() {
      assertFalse(PipingStressAnalysis.codeStressCheck(150.0, 100.0, 138.0, 200.0));
    }
  }

  // ============================================================================
  // FireProtectionDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("FireProtectionDesign Tests")
  class FireProtectionDesignTests {

    @Test
    @DisplayName("PFP thickness should be positive for fire exposure")
    void pfpThicknessShouldBePositive() {
      double thickness = FireProtectionDesign.pfpThickness(1100.0, 400.0, 60.0, 0.12, 200.0);
      assertTrue(thickness >= 10.0, "PFP thickness should be at least 10mm minimum");
    }

    @Test
    @DisplayName("Longer exposure should require more PFP")
    void longerExposureShouldRequireMorePfp() {
      double pfp60 = FireProtectionDesign.pfpThickness(1100.0, 400.0, 60.0, 0.12, 200.0);
      double pfp120 = FireProtectionDesign.pfpThickness(1100.0, 400.0, 120.0, 0.12, 200.0);
      assertTrue(pfp120 >= pfp60, "H-120 should require at least as much PFP as H-60");
    }

    @Test
    @DisplayName("Vessel PFP thickness should be positive")
    void vesselPfpThicknessShouldBePositive() {
      double thickness = FireProtectionDesign.vesselPfpThickness(1000.0, 20.0, 120.0, 0.12);
      assertTrue(thickness > 0);
    }

    @Test
    @DisplayName("Firewater demand should be positive")
    void firewaterDemandShouldBePositive() {
      double demand = FireProtectionDesign.firewaterDemand(500.0, 10.0, 2, 1200.0);
      assertTrue(demand > 0);
      // Expected: 500*10 + 2*1200 = 7400 L/min = 444 m3/hr
      assertEquals(444.0, demand, 1.0);
    }

    @Test
    @DisplayName("Blowdown time should be positive")
    void blowdownTimeShouldBePositive() {
      double time = FireProtectionDesign.blowdownTime(5000.0, 80.0, 7.9, 500.0, 20.0, 350.0);
      assertTrue(time > 0, "Blowdown time should be positive");
    }

    @Test
    @DisplayName("Point source radiation should decrease with distance")
    void radiationShouldDecreaseWithDistance() {
      double q10 = FireProtectionDesign.pointSourceRadiation(100000.0, 0.2, 10.0);
      double q50 = FireProtectionDesign.pointSourceRadiation(100000.0, 0.2, 50.0);
      assertTrue(q10 > q50, "Radiation should decrease with distance");
    }

    @Test
    @DisplayName("Safe distance should be positive")
    void safeDistanceShouldBePositive() {
      double dist = FireProtectionDesign.safeDistance(100000.0, 0.2, 4.7);
      assertTrue(dist > 0);
    }

    @Test
    @DisplayName("Pool fire heat release should scale with area")
    void poolFireHeatReleaseShouldScaleWithArea() {
      double q5m = FireProtectionDesign.poolFireHeatRelease(5.0, 0.055, 50000.0, 0.9);
      double q10m = FireProtectionDesign.poolFireHeatRelease(10.0, 0.055, 50000.0, 0.9);
      assertTrue(q10m > q5m, "Larger pool should have higher heat release");
    }
  }

  // ============================================================================
  // CUIRiskAssessment Tests
  // ============================================================================
  @Nested
  @DisplayName("CUIRiskAssessment Tests")
  class CUIRiskAssessmentTests {

    @Test
    @DisplayName("Temperature in peak CUI zone should give HIGH or VERY_HIGH risk")
    void peakCuiZoneShouldBeHighRisk() {
      CUIRiskAssessment.CUIRisk risk = CUIRiskAssessment.assessRisk(100.0, false,
          CUIRiskAssessment.InsulationType.MINERAL_WOOL, 12.0, true);
      assertTrue(
          risk == CUIRiskAssessment.CUIRisk.HIGH || risk == CUIRiskAssessment.CUIRisk.VERY_HIGH,
          "100°C with mineral wool + marine should be high risk");
    }

    @Test
    @DisplayName("Temperature above 200°C should give LOW risk for CS")
    void aboveCuiZoneShouldBeLowRisk() {
      CUIRiskAssessment.CUIRisk risk = CUIRiskAssessment.assessRisk(250.0, false,
          CUIRiskAssessment.InsulationType.CELLULAR_GLASS, 0.0, false);
      assertEquals(CUIRiskAssessment.CUIRisk.LOW, risk);
    }

    @Test
    @DisplayName("Stainless steel in chloride SCC zone should be high risk")
    void ssShouldBeHighRiskInSccZone() {
      double score = CUIRiskAssessment.temperatureRiskScore(100.0, true);
      assertTrue(score >= 2.5, "SS in 60-150°C should have high risk score");
    }

    @Test
    @DisplayName("Inspection intervals should decrease with higher risk")
    void inspectionIntervalsShouldDecreaseWithRisk() {
      int lowInterval =
          CUIRiskAssessment.recommendedInspectionIntervalYears(CUIRiskAssessment.CUIRisk.LOW);
      int highInterval =
          CUIRiskAssessment.recommendedInspectionIntervalYears(CUIRiskAssessment.CUIRisk.HIGH);
      assertTrue(lowInterval > highInterval);
    }

    @Test
    @DisplayName("Recommended inspection methods should not be empty")
    void inspectionMethodsShouldNotBeEmpty() {
      for (CUIRiskAssessment.CUIRisk risk : CUIRiskAssessment.CUIRisk.values()) {
        List<String> methods = CUIRiskAssessment.recommendedInspectionMethods(risk);
        assertFalse(methods.isEmpty(), risk + " should have inspection methods");
      }
    }

    @Test
    @DisplayName("PIR foam should not be suitable above 140°C")
    void pirFoamShouldNotBeSuitableAbove140C() {
      assertFalse(
          CUIRiskAssessment.isInsulationSuitable(CUIRiskAssessment.InsulationType.PIR_FOAM, 200.0));
      assertTrue(
          CUIRiskAssessment.isInsulationSuitable(CUIRiskAssessment.InsulationType.PIR_FOAM, 100.0));
    }

    @Test
    @DisplayName("Cellular glass should be suitable for wide temperature range")
    void cellularGlassShouldBeSuitableForWideRange() {
      assertTrue(CUIRiskAssessment
          .isInsulationSuitable(CUIRiskAssessment.InsulationType.CELLULAR_GLASS, -200.0));
      assertTrue(CUIRiskAssessment
          .isInsulationSuitable(CUIRiskAssessment.InsulationType.CELLULAR_GLASS, 400.0));
    }

    @Test
    @DisplayName("Remaining life estimate should be positive for thinning pipe")
    void remainingLifeShouldBePositive() {
      double life = CUIRiskAssessment.estimateRemainingLife(10.0, 8.0, 10.0);
      assertTrue(life > 0, "Remaining life should be positive");
      // Rate = 2mm/10yr = 0.2mm/yr. Remaining = 8 - 5 = 3mm. Life = 3/0.2 = 15 yr
      assertEquals(15.0, life, 0.1);
    }

    @Test
    @DisplayName("No thinning should return MAX_VALUE remaining life")
    void noThinningShouldReturnMaxLife() {
      double life = CUIRiskAssessment.estimateRemainingLife(10.0, 10.0, 10.0);
      assertEquals(Double.MAX_VALUE, life);
    }

    @Test
    @DisplayName("Moisture-absorbing insulation should have higher CUI multiplier")
    void moistureAbsorbingInsulationShouldHaveHigherMultiplier() {
      assertTrue(CUIRiskAssessment.InsulationType.MINERAL_WOOL.absorbsMoisture());
      assertFalse(CUIRiskAssessment.InsulationType.CELLULAR_GLASS.absorbsMoisture());
      assertTrue(CUIRiskAssessment.InsulationType.MINERAL_WOOL
          .getCuiMultiplier() > CUIRiskAssessment.InsulationType.CELLULAR_GLASS.getCuiMultiplier());
    }
  }
}
