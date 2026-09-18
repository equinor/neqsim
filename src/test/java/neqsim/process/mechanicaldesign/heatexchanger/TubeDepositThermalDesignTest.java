package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies inner-tube deposit thermal resistance and hydraulics against analytical cylindrical and laminar-flow
 * references, and checks legacy behavior when no geometric layer is enabled.
 *
 * @author NeqSim Development Team
 */
class TubeDepositThermalDesignTest extends neqsim.NeqSimTest {
  /** Nominal tube inner diameter (m). */
  private static final double ID = 0.020;
  /** Nominal tube outer diameter (m). */
  private static final double OD = 0.024;
  /** Tube length (m). */
  private static final double LENGTH = 4.0;
  /** Tube count. */
  private static final int TUBES = 40;
  /** Number of tube passes. */
  private static final int PASSES = 2;
  /** Tube-side density (kg/m3). */
  private static final double DENSITY = 900.0;
  /** Tube-side viscosity (Pa*s), chosen to keep the benchmark laminar. */
  private static final double VISCOSITY = 0.01;
  /** Tube-side mass flow (kg/s). */
  private static final double MASS_FLOW = 2.0;
  /** Tube-side thermal conductivity (W/(m*K)). */
  private static final double FLUID_CONDUCTIVITY = 0.13;
  /** Deposit thermal conductivity (W/(m*K)). */
  private static final double DEPOSIT_CONDUCTIVITY = 0.20;

  /**
   * An explicit zero layer must preserve all preexisting thermal and hydraulic outputs for both shell methods.
   */
  @Test
  void zeroLayerPreservesLegacyOutputs() {
    for (ThermalDesignCalculator.ShellSideMethod method : ThermalDesignCalculator.ShellSideMethod.values()) {
      ThermalDesignCalculator legacy = new ThermalDesignCalculator();
      legacy.setShellSideMethod(method);
      legacy.calculate();
      ThermalDesignCalculator zero = new ThermalDesignCalculator();
      zero.setShellSideMethod(method);
      zero.setTubeFoulingLayer(0.0, DEPOSIT_CONDUCTIVITY);
      zero.calculate();
      assertEquals(legacy.getOverallU(), zero.getOverallU(), 0.0);
      assertEquals(legacy.getTubeSideHTC(), zero.getTubeSideHTC(), 0.0);
      assertEquals(legacy.getTubeSideVelocity(), zero.getTubeSideVelocity(), 0.0);
      assertEquals(legacy.getTubeSideRe(), zero.getTubeSideRe(), 0.0);
      assertEquals(legacy.getTubeSidePressureDrop(), zero.getTubeSidePressureDrop(), 0.0);
      assertEquals(legacy.getShellSideHTC(), zero.getShellSideHTC(), 0.0);
      assertEquals(legacy.getShellSidePressureDrop(), zero.getShellSidePressureDrop(), 0.0);
      assertEquals(0.0, zero.getTubeFoulingLayerResistanceOutside(), 0.0);
    }
  }

  /**
   * Checks three deposit thicknesses against the cylinder-conduction and Hagen-Poiseuille references, including the
   * calculator's documented return losses.
   *
   * @param thickness deposit thickness (m)
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.0001, 0.001, 0.003})
  void depositMatchesCylindricalResistanceAndLaminarHydraulics(double thickness) {
    ThermalDesignCalculator clean = createLaminarCalculator();
    clean.calculate();
    ThermalDesignCalculator deposited = createLaminarCalculator();
    deposited.setTubeFoulingLayer(thickness, DEPOSIT_CONDUCTIVITY);
    deposited.calculate();

    double diameter = ID - 2.0 * thickness;
    double volumeFlowPerTube = MASS_FLOW / DENSITY / (TUBES / PASSES);
    double expectedVelocity = volumeFlowPerTube / (Math.PI * diameter * diameter / 4.0);
    double expectedRe = 4.0 * MASS_FLOW / ((TUBES / PASSES) * Math.PI * diameter * VISCOSITY);
    double expectedHtc = 3.66 * FLUID_CONDUCTIVITY / diameter;
    double poiseuilleDrop = 128.0 * VISCOSITY * LENGTH * volumeFlowPerTube / (Math.PI * Math.pow(diameter, 4.0));
    double returnDrop = 2.5 * DENSITY * expectedVelocity * expectedVelocity / 2.0;
    double expectedDrop = PASSES * (poiseuilleDrop + returnDrop);
    double expectedDepositResistance = OD * Math.log(ID / diameter) / (2.0 * DEPOSIT_CONDUCTIVITY);
    double expectedResistance = 1.0 / deposited.getShellSideHTC() + OD * Math.log(OD / ID) / (2.0 * 52.0)
        + expectedDepositResistance + OD / (diameter * expectedHtc);

    assertTrue(expectedRe < 2300.0, "Independent laminar benchmark requires laminar flow");
    assertEquals(diameter, deposited.getEffectiveTubeIDm(), 1e-15);
    assertEquals(expectedVelocity, deposited.getTubeSideVelocity(), expectedVelocity * 1e-12);
    assertEquals(expectedRe, deposited.getTubeSideRe(), expectedRe * 1e-12);
    assertEquals(expectedHtc, deposited.getTubeSideHTC(), expectedHtc * 1e-12);
    assertEquals(expectedDrop, deposited.getTubeSidePressureDrop(), expectedDrop * 1e-12);
    assertEquals(expectedDepositResistance, deposited.getTubeFoulingLayerResistanceOutside(), 1e-15);
    assertEquals(1.0 / expectedResistance, deposited.getOverallU(), deposited.getOverallU() * 1e-12);
    assertEquals(Math.PI * OD * LENGTH * TUBES, deposited.getOutsideHeatTransferArea(), 1e-12);
    assertEquals(clean.getOutsideHeatTransferArea(), deposited.getOutsideHeatTransferArea(), 0.0);
    assertEquals(clean.getShellSideHTC(), deposited.getShellSideHTC(), 0.0);
    assertEquals(clean.getShellSidePressureDrop(), deposited.getShellSidePressureDrop(), 0.0);
    assertTrue(deposited.getTubeSidePressureDrop() > clean.getTubeSidePressureDrop());
    assertTrue(deposited.getOverallU() < clean.getOverallU());
  }

  /**
   * The preexisting resistance-only setting changes U without changing tube hydraulics, including with a deposit.
   */
  @Test
  void resistanceOnlyFoulingKeepsNominalAreaBasisAndHydraulics() {
    for (double thickness : new double[] {0.0, 0.001}) {
      ThermalDesignCalculator calculator = createLaminarCalculator();
      calculator.setTubeFoulingLayer(thickness, DEPOSIT_CONDUCTIVITY);
      calculator.calculate();
      double cleanU = calculator.getOverallU();
      double velocity = calculator.getTubeSideVelocity();
      double reynolds = calculator.getTubeSideRe();
      double pressureDrop = calculator.getTubeSidePressureDrop();
      double htc = calculator.getTubeSideHTC();
      double additionalResistance = 0.0007;
      calculator.setFoulingTube(additionalResistance);
      calculator.calculate();

      assertEquals(OD / ID * additionalResistance, 1.0 / calculator.getOverallU() - 1.0 / cleanU, 1e-14);
      assertEquals(velocity, calculator.getTubeSideVelocity(), 0.0);
      assertEquals(reynolds, calculator.getTubeSideRe(), 0.0);
      assertEquals(pressureDrop, calculator.getTubeSidePressureDrop(), 0.0);
      assertEquals(htc, calculator.getTubeSideHTC(), 0.0);
    }
  }

  /**
   * Scenario sweeps set absolute thickness and repeated evaluation does not accumulate geometry or resistance.
   */
  @Test
  void repeatedCalculationAndLayerResetRecoverCleanState() {
    ThermalDesignCalculator calculator = createLaminarCalculator();
    calculator.calculate();
    double cleanU = calculator.getOverallU();
    double cleanDrop = calculator.getTubeSidePressureDrop();
    calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
    calculator.calculate();
    double fouledU = calculator.getOverallU();
    double fouledDrop = calculator.getTubeSidePressureDrop();
    for (int i = 0; i < 3; i++) {
      calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
      calculator.calculate();
      assertEquals(fouledU, calculator.getOverallU(), 0.0);
      assertEquals(fouledDrop, calculator.getTubeSidePressureDrop(), 0.0);
      assertEquals(ID - 0.002, calculator.getEffectiveTubeIDm(), 0.0);
    }
    calculator.setTubeFoulingLayer(0.0, DEPOSIT_CONDUCTIVITY);
    calculator.calculate();
    assertEquals(cleanU, calculator.getOverallU(), 0.0);
    assertEquals(cleanDrop, calculator.getTubeSidePressureDrop(), 0.0);
    assertEquals(ID, calculator.getEffectiveTubeIDm(), 0.0);
  }

  /**
   * Invalid setters reject all nonfinite parameters and closed bores while preserving the prior valid layer.
   */
  @Test
  void invalidLayersAreRejectedWithoutReplacingValidState() {
    ThermalDesignCalculator calculator = createLaminarCalculator();
    calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
    for (double thickness : new double[] {-0.001, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        ID / 2.0, ID}) {
      assertThrows(IllegalArgumentException.class,
          () -> calculator.setTubeFoulingLayer(thickness, DEPOSIT_CONDUCTIVITY));
    }
    for (double conductivity : new double[] {0.0, -1.0, Double.NaN, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> calculator.setTubeFoulingLayer(0.001, conductivity));
      assertThrows(IllegalArgumentException.class, () -> calculator.setTubeFoulingLayer(0.0, conductivity));
    }
    assertEquals(0.001, calculator.getTubeFoulingLayerThicknessM(), 0.0);
    assertEquals(DEPOSIT_CONDUCTIVITY, calculator.getTubeFoulingLayerConductivity(), 0.0);
    calculator.calculate();
    assertTrue(calculator.getOverallU() > 0.0);
  }

  /**
   * Changing nominal geometry after layer configuration must not allow a blocked or nonfinite bore to calculate.
   */
  @Test
  void geometryChangesRevalidateLayerBeforeCalculation() {
    ThermalDesignCalculator calculator = createLaminarCalculator();
    calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
    for (double diameter : new double[] {0.002, 0.001, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      calculator.setTubeIDm(diameter);
      assertThrows(IllegalArgumentException.class, calculator::calculate);
      assertThrows(IllegalArgumentException.class, calculator::getTubeFoulingLayerResistanceOutside);
    }
    calculator.setTubeIDm(ID);
    calculator.calculate();
    assertTrue(calculator.getOverallU() > 0.0);
  }

  /**
   * Structured output states the effective flow diameter and deposit resistance with their units and area basis.
   */
  @Test
  void mapExposesDepositAndArea() {
    ThermalDesignCalculator calculator = createLaminarCalculator();
    calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
    calculator.calculate();
    Map<?, ?> tube = (Map<?, ?>) calculator.toMap().get("tubeSide");
    Map<?, ?> overall = (Map<?, ?>) calculator.toMap().get("overallHeatTransfer");
    assertEquals(ID, (Double) tube.get("nominalInnerDiameter_m"), 0.0);
    assertEquals(ID - 0.002, (Double) tube.get("effectiveInnerDiameter_m"), 0.0);
    assertEquals(0.001, (Double) tube.get("foulingLayerThickness_m"), 0.0);
    assertEquals(DEPOSIT_CONDUCTIVITY, (Double) tube.get("foulingLayerConductivity_WpmK"), 0.0);
    assertEquals(calculator.getTubeFoulingLayerResistanceOutside(),
        (Double) overall.get("tubeFoulingLayerResistanceOutside_m2KpW"), 0.0);
    assertEquals(calculator.getOutsideHeatTransferArea(), (Double) overall.get("outsideHeatTransferArea_m2"), 0.0);
  }

  /**
   * Strict validation preserves the calculation for valid clean and deposited geometry with either shell method.
   */
  @Test
  void strictCalculationMatchesValidLegacyCalculation() {
    for (ThermalDesignCalculator.ShellSideMethod method : ThermalDesignCalculator.ShellSideMethod.values()) {
      for (double thickness : new double[] {0.0, 0.001}) {
        ThermalDesignCalculator legacy = createLaminarCalculator();
        legacy.setShellSideMethod(method);
        legacy.setTubeFoulingLayer(thickness, DEPOSIT_CONDUCTIVITY);
        legacy.calculate();
        ThermalDesignCalculator strict = createLaminarCalculator();
        strict.setShellSideMethod(method);
        strict.setTubeFoulingLayer(thickness, DEPOSIT_CONDUCTIVITY);
        strict.calculateStrict();
        assertEquals(legacy.toMap(), strict.toMap());
      }
    }
  }

  /**
   * Invalid geometry after successful rating fails before stale heat transfer or pressure results can be accepted.
   */
  @Test
  void strictCalculationRejectsGeometryMutationsAfterSuccessfulRun() {
    assertStrictRejects(calculator -> calculator.setTubeCount(0));
    assertStrictRejects(calculator -> calculator.setTubePasses(0));
    assertStrictRejects(calculator -> calculator.setTubePasses(3));
    assertStrictRejects(calculator -> calculator.setTubePasses(TUBES + 1));
    assertStrictRejects(calculator -> calculator.setTubeIDm(OD));
    assertStrictRejects(calculator -> calculator.setTubeIDm(Double.NaN));
    assertStrictRejects(calculator -> calculator.setTubeODm(0.0));
    assertStrictRejects(calculator -> calculator.setTubePitchm(OD));
    assertStrictRejects(calculator -> calculator.setTubeLengthm(-1.0));
    assertStrictRejects(calculator -> calculator.setShellIDm(OD));
    assertStrictRejects(calculator -> calculator.setBaffleSpacingm(0.0));
    assertStrictRejects(calculator -> calculator.setBaffleSpacingm(LENGTH + 1.0));
    assertStrictRejects(calculator -> calculator.setBaffleCount(0));
    assertStrictRejects(calculator -> calculator.setBaffleCut(0.0));
    assertStrictRejects(calculator -> calculator.setBaffleCut(0.5));
    assertStrictRejects(calculator -> calculator.setBaffleCut(Double.POSITIVE_INFINITY));
    assertStrictRejects(calculator -> {
      calculator.setTubeFoulingLayer(0.001, DEPOSIT_CONDUCTIVITY);
      calculator.setTubeIDm(0.002);
    });
  }

  /**
   * Strict rating rejects invalid transport inputs and resistances while leaving permissive calculation separate.
   */
  @Test
  void strictCalculationRejectsInvalidFluidAndResistanceInputs() {
    for (int property = 0; property < 5; property++) {
      final int invalidIndex = property;
      for (double invalid : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
        assertStrictRejects(calculator -> {
          double[] properties = {DENSITY, VISCOSITY, 2200.0, FLUID_CONDUCTIVITY, MASS_FLOW};
          properties[invalidIndex] = invalid;
          calculator.setTubeSideFluid(properties[0], properties[1], properties[2], properties[3], properties[4], true);
        });
        assertStrictRejects(calculator -> {
          double[] properties = {998.0, 0.001, 4180.0, 0.6, 4.0};
          properties[invalidIndex] = invalid;
          calculator.setShellSideFluid(properties[0], properties[1], properties[2], properties[3], properties[4]);
        });
      }
    }
    assertStrictRejects(calculator -> calculator.setTubeWallConductivity(0.0));
    assertStrictRejects(calculator -> calculator.setTubeWallConductivity(Double.NaN));
    assertStrictRejects(calculator -> calculator.setFoulingTube(-1.0));
    assertStrictRejects(calculator -> calculator.setFoulingShell(-1.0));
    assertStrictRejects(calculator -> calculator.setFoulingTube(Double.NaN));
    assertStrictRejects(calculator -> calculator.setFoulingShell(Double.POSITIVE_INFINITY));
    assertStrictRejects(calculator -> calculator.setShellViscosityWall(0.0));
    assertStrictRejects(calculator -> calculator.setShellViscosityWall(Double.NaN));
    assertStrictRejects(calculator -> calculator.setShellSideMethod(null));
  }

  /**
   * Bell-Delaware-specific inputs cannot silently select invalid leakage, bypass or sealing corrections.
   */
  @Test
  void strictBellDelawareRatingRejectsInvalidCorrectionGeometry() {
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setTubeToBaffleClearance(-0.001);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setTubeToBaffleClearance(0.030);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setShellToBaffleClearance(Double.NaN);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setShellToBaffleClearance(0.6);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setBypassArea(-0.001);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setBypassArea(Double.POSITIVE_INFINITY);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setSealingPairs(-1);
    });
    assertStrictRejects(calculator -> {
      calculator.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
      calculator.setHasSealing(true);
    });
    ThermalDesignCalculator valid = createLaminarCalculator();
    valid.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
    valid.setTubeToBaffleClearance(0.0);
    valid.setShellToBaffleClearance(0.0);
    valid.setHasSealing(true);
    valid.setSealingPairs(1);
    valid.calculateStrict();
    assertTrue(valid.getOverallU() > 0.0);
  }

  /**
   * Confirms that a previously successful calculator rejects a subsequent invalid configuration explicitly.
   *
   * @param mutation change that invalidates a previously valid configuration
   */
  private void assertStrictRejects(Consumer<ThermalDesignCalculator> mutation) {
    ThermalDesignCalculator calculator = createLaminarCalculator();
    calculator.calculateStrict();
    assertTrue(calculator.getOverallU() > 0.0);
    mutation.accept(calculator);
    assertThrows(IllegalArgumentException.class, calculator::calculateStrict);
  }

  /**
   * Creates a calculator with specified geometry and laminar oil-like tube flow.
   *
   * @return configured calculator with no resistance-only fouling
   */
  private ThermalDesignCalculator createLaminarCalculator() {
    ThermalDesignCalculator calculator = new ThermalDesignCalculator();
    calculator.setTubeIDm(ID);
    calculator.setTubeODm(OD);
    calculator.setTubeLengthm(LENGTH);
    calculator.setTubeCount(TUBES);
    calculator.setTubePasses(PASSES);
    calculator.setTubePitchm(0.030);
    calculator.setTubeWallConductivity(52.0);
    calculator.setTubeSideFluid(DENSITY, VISCOSITY, 2200.0, FLUID_CONDUCTIVITY, MASS_FLOW, true);
    calculator.setShellSideFluid(998.0, 0.001, 4180.0, 0.6, 4.0);
    calculator.setFoulingTube(0.0);
    calculator.setFoulingShell(0.0);
    return calculator;
  }
}
