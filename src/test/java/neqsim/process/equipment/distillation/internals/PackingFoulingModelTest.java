package neqsim.process.equipment.distillation.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PackingFoulingModel}. */
public class PackingFoulingModelTest {
  /** Clean 2 inch metal Pall ring void fraction [-]. */
  private static final double CLEAN_VOID = 0.951;

  /** Clean 2 inch metal Pall ring specific surface area [m2/m3]. */
  private static final double CLEAN_AREA = 102.0;

  /** Clean 2 inch metal Pall ring packing factor [1/m]. */
  private static final double CLEAN_FP = 88.0;

  /** A clean bed must reproduce its own geometry and a unity pressure-drop ratio. */
  @Test
  void cleanBedIsNeutral() {
    PackingFoulingModel model = PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 0.0);
    assertEquals(CLEAN_VOID, model.getFouledVoidFraction(), 1.0e-12);
    assertEquals(CLEAN_FP, model.getFouledPackingFactor(), 1.0e-9);
    assertEquals(1.0, model.getPressureDropRatio(), 1.0e-12);
    assertEquals(1.0, model.getFloodingVelocityRatio(), 1.0e-12);
    assertEquals(0.0, model.getDepositThickness(), 1.0e-15);
    assertTrue(model.toString().contains("dPratio"));
  }

  /** Void loss must raise the packing factor and pressure drop and cut the flooding velocity. */
  @Test
  void voidLossRaisesPressureDrop() {
    PackingFoulingModel model = PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 0.20);
    assertEquals(CLEAN_VOID * 0.80, model.getFouledVoidFraction(), 1.0e-12);
    assertEquals(CLEAN_FP / Math.pow(0.80, 3.0), model.getFouledPackingFactor(), 1.0e-9);
    assertEquals(Math.pow(1.0 / 0.80, 6.0), model.getPressureDropRatio(), 1.0e-9);
    assertTrue(model.getFloodingVelocityRatio() < 1.0);
    assertTrue(model.getFouledHydraulicDiameter() < model.getCleanHydraulicDiameter());
    assertEquals(CLEAN_AREA, model.getFouledSpecificSurfaceArea(), 1.0e-12);
  }

  /** Deposit thickness and void loss must be consistent inverses of one another. */
  @Test
  void depositThicknessAndVoidLossAreConsistent() {
    double thickness = 5.0e-4;
    PackingFoulingModel byThickness = PackingFoulingModel.fromDepositThickness(CLEAN_VOID, CLEAN_AREA, CLEAN_FP,
        thickness);
    double loss = byThickness.getVoidLossFraction();
    assertEquals(CLEAN_AREA * thickness / CLEAN_VOID, loss, 1.0e-12);

    PackingFoulingModel byLoss = PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, loss);
    assertEquals(thickness, byLoss.getDepositThickness(), 1.0e-15);
    assertEquals(byThickness.getPressureDropRatio(), byLoss.getPressureDropRatio(), 1.0e-12);
  }

  /** The pressure-drop inverse must return the void loss that generated the ratio. */
  @Test
  void pressureDropRatioInvertsToVoidLoss() {
    PackingFoulingModel model = PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 0.35);
    double recovered = PackingFoulingModel.voidLossFractionForPressureDropRatio(model.getPressureDropRatio());
    assertEquals(0.35, recovered, 1.0e-12);
    assertEquals(0.0, PackingFoulingModel.voidLossFractionForPressureDropRatio(1.0), 1.0e-12);
  }

  /** Invalid arguments must be rejected instead of producing silent nonsense. */
  @Test
  void invalidArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromVoidLossFraction(0.0, CLEAN_AREA, CLEAN_FP, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, -1.0, CLEAN_FP, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, 0.0, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromDepositThickness(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> PackingFoulingModel.fromDepositThickness(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 0.05));
    assertThrows(IllegalArgumentException.class, () -> PackingFoulingModel.voidLossFractionForPressureDropRatio(0.5));
    assertFalse(Double
        .isNaN(PackingFoulingModel.fromVoidLossFraction(CLEAN_VOID, CLEAN_AREA, CLEAN_FP, 0.5).getPressureDropRatio()));
  }
}
