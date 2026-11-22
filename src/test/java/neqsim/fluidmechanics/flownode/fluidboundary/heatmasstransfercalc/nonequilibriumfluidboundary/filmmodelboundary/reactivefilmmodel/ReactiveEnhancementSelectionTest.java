package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactorInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactorNumeric;
import neqsim.thermo.system.SystemSrkEos;

class ReactiveEnhancementSelectionTest {
  private ReactiveKrishnaStandartFilmModel boundary() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    return new ReactiveKrishnaStandartFilmModel(fluid);
  }

  @Test
  void unsupportedSelectionFailsWithoutReplacingWorkingModel() {
    ReactiveKrishnaStandartFilmModel film = boundary();
    EnhancementFactorInterface original = film.getEnhancementFactor();
    for (int type : new int[] {0, 2, -1, 99}) {
      UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
          () -> film.setEnhancementType(type));
      assertTrue(error.getMessage().contains("not implemented"));
      assertSame(original, film.getEnhancementFactor());
      assertEquals(1, film.enhancementType);
    }
  }

  @Test
  void numericalClassCannotSilentlySupplyZeroEnhancement() {
    assertThrows(UnsupportedOperationException.class, () -> new EnhancementFactorNumeric(boundary()));
  }

  @Test
  void algebraicSelectionRetainsUnitEnhancementForNonreactingComponents() {
    ReactiveKrishnaStandartFilmModel film = boundary();
    film.setEnhancementType(1);
    for (int phase = 0; phase < 2; phase++) {
      film.getEnhancementFactor().calcEnhancementVec(phase);
      assertArrayEquals(new double[] {1.0, 1.0}, film.getEnhancementFactor().getEnhancementVec(), 0.0);
    }
  }
}
