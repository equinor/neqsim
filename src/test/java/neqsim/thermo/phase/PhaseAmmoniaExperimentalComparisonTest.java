package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Compare ammonia phase properties against reference data derived from the Ammonia2023 reference equation.
 */
class PhaseAmmoniaExperimentalComparisonTest {
  private static class DataPoint {
    final double temperature;
    final double pressure;
    final PhaseType phaseType;
    final double density;
    final double cp;
    final double cv;
    final double enthalpy;
    final double entropy;
    final double internal;
    final double sound;
    final double gibbs;
    final double jt;

    DataPoint(double t, double p, PhaseType type, double density, double cp, double cv, double enthalpy, double entropy,
        double internal, double sound, double gibbs, double jt) {
      this.temperature = t;
      this.pressure = p;
      this.phaseType = type;
      this.density = density;
      this.cp = cp;
      this.cv = cv;
      this.enthalpy = enthalpy;
      this.entropy = entropy;
      this.internal = internal;
      this.sound = sound;
      this.gibbs = gibbs;
      this.jt = jt;
    }
  }

  private static final DataPoint[] DATA = new DataPoint[] {
      new DataPoint(298.15, 10.0, PhaseType.GAS, 7.776761102217426, 53.55010850788055, 36.51297326299638,
          27746.32174840689, 98.84217173986131, 25556.39720576612, 404.5606452042579, -27279.86896159888,
          2.5961608982599835),
      new DataPoint(350.0, 20.0, PhaseType.GAS, 13.35662796766872, 51.668301622807036, 35.31040984274467,
          29448.242571194576, 99.03316802948505, 26898.1198843571, 434.8552768358881, -32111.48612348229,
          1.5124330095502858),
      new DataPoint(250.0, 5.0, PhaseType.GAS, 4.701870585296162, 60.54300396811652, 39.887758437029575,
          26014.568130642485, 97.56619889839223, 24203.531470758822, 369.9159345783701, -22580.5130647144,
          4.9890470502852295),
      new DataPoint(293.15, 10.0, PhaseType.LIQUID, 610.5159715981024, 80.67824040918259, 47.49262304382213,
          7482.914118060292, 30.84735162920286, 7455.018828187022, 1374.6070564533206, -9015.00584022755,
          -0.009797935974274806),
      new DataPoint(250.0, 10.0, PhaseType.LIQUID, 669.4301318526168, 76.64831187681625, 49.29105948985145,
          4099.351411794656, 18.3703203747307, 4073.911086972617, 1674.5240091723776, -4567.139768860636,
          -0.01752750112041058) };

  @Test
  void compareToReferenceData() {
    final double tol = 1e-1; // absolute tolerance
    for (DataPoint d : DATA) {
      SystemInterface system = new SystemAmmoniaEos(d.temperature, d.pressure);
      system.setNumberOfPhases(1);
      system.setMaxNumberOfPhases(1);
      system.setForcePhaseTypes(true);
      system.init(0);
      system.setPhaseType(0, d.phaseType);
      system.init(3);
      PhaseInterface phase = system.getPhase(0);
      assertEquals(d.density, phase.getDensity(), Math.abs(d.density * tol));
      assertEquals(d.cp, phase.getCp(), Math.abs(d.cp * tol));
      assertEquals(d.cv, phase.getCv(), Math.abs(d.cv * tol));
      assertEquals(d.enthalpy, phase.getEnthalpy(), Math.abs(d.enthalpy * tol));
      assertEquals(d.entropy, phase.getEntropy(), Math.abs(d.entropy * tol));
      assertEquals(d.internal, phase.getInternalEnergy(), Math.abs(d.internal * tol));
      assertEquals(d.sound, phase.getSoundSpeed(), Math.abs(d.sound * tol));
      assertEquals(d.gibbs, phase.getGibbsEnergy(), Math.abs(d.gibbs * tol));
      assertEquals(d.jt, phase.getJouleThomsonCoefficient(), Math.abs(d.jt * tol));
    }
  }
}
