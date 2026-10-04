package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.ReactiveKrishnaStandartFilmModel;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class FilmCorrectionInvariantTest {
  private static SystemInterface fluid() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(1);
    return fluid;
  }

  private static void assertMatrix(Matrix expected, Matrix actual) {
    assertEquals(0.0, expected.minus(actual).normF(), 1e-12);
  }

  private static class Film extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    Film() {
      super(fluid());
      massTransferCoefficientMatrix[1] = new Matrix(new double[][] {{2.0, 0.0}, {0.0, 3.0}});
      nonIdealCorrections[1] = new Matrix(new double[][] {{1.5, 0.1}, {0.2, 1.2}});
      rateCorrectionMatrix[1] = Matrix.identity(2, 2);
      for (double[] row : binaryMassTransferCoefficient[1]) {
        Arrays.fill(row, 0.01);
      }
    }

    Matrix evaluate(double flux, boolean thermo, boolean finite) {
      totalFlux = flux;
      useThermodynamicCorrections(thermo, 1);
      useFiniteFluxCorrection(finite, 1);
      calcTotalMassTransferCoefficientMatrix(1);
      return totalMassTransferCoefficientMatrix[1];
    }

    Matrix correctedFlux(double[] flux) {
      nFlux = new Matrix(flux, flux.length);
      initCorrections(1);
      return rateCorrectionMatrix[1];
    }

    void setRate(Matrix correction) {
      rateCorrectionMatrix[1] = correction;
    }
  }

  @Test
  void identityCorrectionAndZeroNetFluxPreserveThermodynamicDrivingForce() {
    Film film = new Film();
    Matrix expected = new Matrix(new double[][] {{3.0, 0.2}, {0.6, 3.6}});
    for (double flux : new double[] {0.0, 1e-40, -1e-40, 1e-20, -1e-20, 1.0}) {
      assertMatrix(expected, film.evaluate(flux, true, false));
      assertMatrix(expected, film.evaluate(flux, true, true));
    }
  }

  @Test
  void correctionSwitchesUseOneOrderForNoncommutingMatrices() {
    Film film = new Film();
    Matrix correction = new Matrix(new double[][] {{1.1, 0.2}, {0.3, 0.9}});
    film.setRate(correction);
    Matrix k = film.evaluate(1.0, false, false).copy();
    Matrix thermodynamic = film.evaluate(1.0, true, false).copy();
    assertMatrix(correction.times(k), film.evaluate(1.0, false, true));
    assertMatrix(correction.times(thermodynamic), film.evaluate(1.0, true, true));
  }

  @Test
  void zeroComponentFluxHasIdentityCorrection() {
    assertMatrix(Matrix.identity(2, 2), new Film().correctedFlux(new double[] {0.0, 0.0, 0.0}));
  }

  @Test
  void equalDiffusivityCounterdiffusionHasIdentityCorrection() {
    assertMatrix(Matrix.identity(2, 2), new Film().correctedFlux(new double[] {1.0, -1.0, 0.0}));
  }

  @Test
  void eigenvalueFunctionHasContinuousZeroLimitAndFiniteLargeArguments() {
    Film film = new Film();
    film.redCorrectionMatrix = new Matrix(2, 1);
    for (double z : new double[] {0.0, 1e-12, -1e-12, 1e-7, -1e-7, 1.0, -1.0, 1000.0, -1000.0}) {
      film.redPhiMatrix = new Matrix(new double[][] {{z, z}});
      film.calcRedCorrectionMatrix(1);
      double expected = z == 0.0 ? 1.0 : z > 0.0 ? z / -Math.expm1(-z) : z * Math.exp(z) / Math.expm1(z);
      assertEquals(expected, film.redCorrectionMatrix.get(0, 0), 1e-12);
    }
  }

  private static class ReactiveFilm extends ReactiveKrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    ReactiveFilm() {
      super(fluid());
      massTransferCoefficientMatrix[1] = new Matrix(new double[][] {{2.0, 0.4}, {0.5, 3.0}});
      nonIdealCorrections[1] = Matrix.identity(2, 2);
      rateCorrectionMatrix[1] = Matrix.identity(2, 2);
      enhancementFactor = new EnhancementFactor(this) {
        @Override
        public void calcEnhancementVec(int phase) {
          enhancementVec = new double[] {2.0, 1.0, 1.0};
        }
      };
    }

    void checkRepeatedCalculation(boolean thermo, boolean finite, double flux) {
      useThermodynamicCorrections(thermo, 1);
      useFiniteFluxCorrection(finite, 1);
      totalFlux = flux;
      Matrix base = massTransferCoefficientMatrix[1].copy();
      calcTotalMassTransferCoefficientMatrix(1);
      assertNotSame(massTransferCoefficientMatrix[1], totalMassTransferCoefficientMatrix[1]);
      assertMatrix(base, massTransferCoefficientMatrix[1]);
      Matrix expected = new Matrix(new double[][] {{4.0, 0.8}, {0.5, 3.0}});
      assertMatrix(expected, totalMassTransferCoefficientMatrix[1]);
      calcTotalMassTransferCoefficientMatrix(1);
      assertMatrix(base, massTransferCoefficientMatrix[1]);
      assertMatrix(expected, totalMassTransferCoefficientMatrix[1]);
    }
  }

  @Test
  void enhancementDoesNotMutateOrCompoundAcrossCorrectionSwitches() {
    for (boolean thermo : new boolean[] {false, true}) {
      for (boolean finite : new boolean[] {false, true}) {
        for (double flux : new double[] {0.0, 1.0}) {
          new ReactiveFilm().checkRepeatedCalculation(thermo, finite, flux);
        }
      }
    }
  }
}
