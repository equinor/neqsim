package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Compare EOS-CG predictions against Clapeyron reference values for representative states.
 */
class EOSCGDensityComparisonTest {
  private static final double REL_TOL = 5.0e-2;

  @Test
  void methaneGasPropertiesMatchClapeyronReferences() {
    double temperature = 300.0; // K
    double pressure = 1.0; // bara

    ClapeyronReference ref = new ClapeyronReference(0.6420, 35.7, 27.385, 0.0, 1.0);
    Properties props = propertiesEOSCG(temperature, pressure, "methane");

    assertPropertyMatch("methane density", props.density, ref.density);
    assertPropertyMatch("methane Cp", props.cp, ref.cp);
    assertPropertyMatch("methane Cv", props.cv, ref.cv);
    assertEquals(ref.Z, props.z, 1.0e-2, "compressibility must match ideal Clapeyron data");
  }

  @Test
  void co2GasPropertiesMatchClapeyronReferences() {
    double temperature = 320.0; // K
    double pressure = 2.0; // bara

    ClapeyronReference ref = new ClapeyronReference(3.3079, 37.2, 28.886, 0.0, 1.0);
    Properties props = propertiesEOSCG(temperature, pressure, "CO2");

    assertPropertyMatch("CO2 density", props.density, ref.density);
    assertPropertyMatch("CO2 Cp", props.cp, ref.cp);
    assertPropertyMatch("CO2 Cv", props.cv, ref.cv);
    assertEquals(ref.Z, props.z, 1.0e-2, "compressibility must match ideal Clapeyron data");
  }

  private Properties propertiesEOSCG(double temperature, double pressure, String component) {
    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent(component, 1.0);
    system.init(0);
    system.setPressure(pressure);
    system.getPhase(0).setPressure(pressure);
    system.init(3);

    double density = system.getPhase(0).getDensity();
    double cp = system.getPhase(0).getCp() / system.getPhase(0).getNumberOfMolesInPhase();
    double cv = system.getPhase(0).getCv() / system.getPhase(0).getNumberOfMolesInPhase();
    double z = system.getPhase(0).getZ();
    return new Properties(density, cp, cv, z);
  }

  private void assertPropertyMatch(String label, double actual, double expected) {
    double deviation = Math.abs(actual - expected) / expected;
    assertTrue(deviation < REL_TOL,
        () -> String.format("%s deviation %.5f exceeds tolerance", label, deviation));
  }

  private static final class Properties {
    final double density;
    final double cp;
    final double cv;
    final double z;

    Properties(double density, double cp, double cv, double z) {
      this.density = density;
      this.cp = cp;
      this.cv = cv;
      this.z = z;
    }
  }

  private static final class ClapeyronReference {
    final double density;
    final double cp;
    final double cv;
    final double jt;
    final double Z;

    ClapeyronReference(double density, double cp, double cv, double jt, double z) {
      this.density = density;
      this.cp = cp;
      this.cv = cv;
      this.jt = jt;
      this.Z = z;
    }
  }
}
