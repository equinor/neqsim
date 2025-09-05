package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Compare ammonia phase properties against reference data derived from the
 * Ammonia2023 reference equation.
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

    DataPoint(double t, double p, PhaseType type, double density, double cp,
        double cv, double enthalpy, double entropy, double internal,
        double sound, double gibbs, double jt) {
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
      new DataPoint(298.15, 10.0, PhaseType.GAS, 8.306908267489456,
          51.07678937730558, 35.60109431266938, 27209.856394688886,
          97.40207917389664, 25159.69291008031, 397.3370745978329,
          -26990.266421088698, 4.484209820708522),
      new DataPoint(350.0, 20.0, PhaseType.GAS, 14.346047116682778,
          55.23037697195553, 39.101399646524186, 28649.442585532648,
          97.11471129474808, 26275.19693284753, 421.5292712411122,
          -31615.9033004767, 2.663475120052775),
      new DataPoint(250.0, 5.0, PhaseType.GAS, 4.948887353528387,
          46.089266547844744, 31.481754032069155, 25886.963764899447,
          97.33738681821211, 24166.322458742234, 368.97390871857544,
          -22613.705398395814, 7.868046368214012),
      new DataPoint(293.15, 10.0, PhaseType.LIQUID, 415.2415567457873,
          92.8464297095919, 87.4568161814013, 10029.601726119816,
          38.202044161641005, 9988.58820104428, 0.0,
          -11157.915720909525, -0.029256687435970136),
      new DataPoint(250.0, 10.0, PhaseType.LIQUID, 445.9534460920064,
          124.58522678333269, 103.09374990974948, 6423.796485179266,
          24.897856742855538, 6385.6074765523845, 0.0,
          -6186.2751770870045, -0.015307258812881645)};

  @Test
  void compareToReferenceData() {
    final double tol = 1e-6; // absolute tolerance
    for (DataPoint d : DATA) {
      SystemInterface system = new SystemAmmoniaEos(d.temperature, d.pressure);
      system.addComponent("ammonia", 1.0);
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
