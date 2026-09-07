package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/** Regression for the dimensional liquid-velocity override of the inclined annular criterion. */
class FlowRegimeInclinedAnnularTest {
  @Test
  void liftedAnnularFilmDoesNotBecomeChurnAtAnAbsoluteLiquidVelocity() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    for (double inclination : new double[] { 10.1, 45.0, 90.0 }) {
      for (double liquidVelocity : new double[] { 0.099, 0.1, 0.101, 0.14, 0.3 }) {
        PipeSection section = section(12.0, liquidVelocity, inclination);
        assertEquals(FlowRegime.ANNULAR, detector.detectFlowRegime(section),
            "annular gas-lift criterion must survive crossing 0.1 m/s at " + inclination + " degrees");
      }
    }
  }

  @Test
  void lowGasVelocityDoesNotQualifyAsAnnular() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    for (double inclination : new double[] { 10.1, 45.0, 90.0 }) {
      assertNotEquals(FlowRegime.ANNULAR, detector.detectFlowRegime(section(0.1, 0.14, inclination)));
    }
  }

  private PipeSection section(double gasVelocity, double liquidVelocity, double inclinationDegrees) {
    PipeSection section = new PipeSection(0.0, 50.0, 0.1, Math.toRadians(inclinationDegrees));
    section.setGasHoldup(0.95);
    section.setLiquidHoldup(0.05);
    section.setGasVelocity(gasVelocity / 0.95);
    section.setLiquidVelocity(liquidVelocity / 0.05);
    section.setGasDensity(25.0);
    section.setLiquidDensity(700.0);
    section.setGasViscosity(1.1e-5);
    section.setLiquidViscosity(5.0e-4);
    section.setSurfaceTension(0.02);
    section.updateDerivedQuantities();
    return section;
  }
}
