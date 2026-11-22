package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Real-gas unit/reference-energy controls for the optional local Rusanov query.
 *
 * <p>
 * These are equilibrium phase samples, not elapsed pool states or an independent EOS benchmark. No production solver or
 * phase/component transport is selected by these controls.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RusanovRichGasFluxTest {
  private static final String[] COMPONENTS = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
  private static final double[] FRACTIONS = {0.916, 0.050, 0.020, 0.007, 0.003, 0.004};
  private static final double[] PRESSURES = {10.0, 40.0, 80.0, 115.0, 125.0, 150.0};
  private static final double[] TEMPERATURES = {260.0, 277.15, 290.0};
  private static final double AREA = Math.PI * 1.0148 * 1.0148 / 4.0;
  private static final double FLUX_GATE = 3e-14;
  private static final double EOS_GATE = 3e-13;

  /** Actual SRK gas/oil samples must obey the local Euler flux and phase-energy unit contracts. */
  @Test
  void richGasPhaseEnergyAndSignedFluxMatrix() {
    runMatrix(null);
  }

  /**
   * Replay the same asserted matrix and retain every input, native flux and internal-energy reference.
   *
   * @param args one UTF-8 output CSV path
   * @throws IOException if the evidence cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Supply one output CSV path");
    }
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      runMatrix(writer);
      if (writer.checkError()) {
        throw new IOException("Incomplete SRK flux evidence");
      }
    }
  }

  /**
   * Evaluate a fixed nearby P/T grid, both phase types, signed velocities and occupied trace fractions.
   *
   * @param writer optional complete evidence sink; null for normal JUnit execution
   */
  private static void runMatrix(PrintWriter writer) {
    if (writer != null) {
      String header = "id,T_K,pL_bara,pR_bara,phase,fraction_scale,uL_m_s,uR_m_s,area_m2";
      for (String side : new String[] {"L", "R"}) {
        header += ",alpha" + side + ",rho" + side + "_kg_m3,c" + side + "_m_s,h" + side + "_J_kg,e" + side + "_J_kg";
        header += ",rhoEOS" + side + "_kg_m3,eMechanical" + side + "_J_kg";
        for (String component : COMPONENTS) {
          header += ",x" + side + "_" + component;
        }
      }
      writer.println(header + ",mass_native_kg_s,momentum_native_N,energy_native_W,mass_reference_kg_s,"
          + "momentum_reference_N,energy_reference_W,max_normalized_error");
    }
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    int row = 0;
    int wetDry = 0;
    int twoPhase = 0;
    int negativeEnergy = 0;
    for (double temperature : TEMPERATURES) {
      SystemSrkEos[] samples = new SystemSrkEos[PRESSURES.length];
      for (int i = 0; i < samples.length; i++) {
        samples[i] = sample(temperature, PRESSURES[i]);
        if (samples[i].hasPhaseType("oil")) {
          twoPhase++;
        }
      }
      for (int i = 0; i < samples.length; i++) {
        int j = (i + 1) % samples.length;
        for (String phase : new String[] {"gas", "oil"}) {
          if (samples[i].hasPhaseType(phase) != samples[j].hasPhaseType(phase)) {
            wetDry++;
          }
          for (double fractionScale : new double[] {1.0, 1e-12, 1e-30}) {
            for (double[] velocities : new double[][] {{0.0, 0.0}, {-5.0, 3.0}, {7.0, -2.0}}) {
              double[] left = phaseSample(samples[i], phase, fractionScale);
              double[] right = phaseSample(samples[j], phase, fractionScale);
              if (left[0] > 0.0 && left[4] < 0.0) {
                negativeEnergy++;
              }
              PhaseState sl = state(left, velocities[0], PRESSURES[i]);
              PhaseState sr = state(right, velocities[1], PRESSURES[j]);
              PhaseFlux actual = calculator.calcRusanovFlux(sl, sr, AREA);
              double speed = Math.max(left[0] == 0.0 ? 0.0 : Math.abs(velocities[0]) + left[2],
                  right[0] == 0.0 ? 0.0 : Math.abs(velocities[1]) + right[2]);
              double[] ul = conserved(left, velocities[0]);
              double[] ur = conserved(right, velocities[1]);
              double[] fl = physical(ul, velocities[0], left[0] * PRESSURES[i] * 1e5);
              double[] fr = physical(ur, velocities[1], right[0] * PRESSURES[j] * 1e5);
              double[] nativeFlux = {actual.massFlux, actual.momentumFlux, actual.energyFlux};
              double[] reference = new double[3];
              double maximum = 0.0;
              for (int k = 0; k < reference.length; k++) {
                reference[k] = AREA * (0.5 * (fl[k] + fr[k]) - 0.5 * speed * (ur[k] - ul[k]));
                double scale = AREA * (Math.abs(fl[k]) + Math.abs(fr[k]) + speed * (Math.abs(ul[k]) + Math.abs(ur[k])));
                assertTrue(Double.isFinite(nativeFlux[k]), "Nonfinite flux at row " + row);
                assertEquals(reference[k], nativeFlux[k], FLUX_GATE * scale, "SRK flux at row " + row);
                if (scale > 0.0) {
                  maximum = Math.max(maximum, Math.abs(reference[k] - nativeFlux[k]) / scale);
                }
              }
              if (writer != null) {
                StringBuilder line = new StringBuilder().append(row).append(',').append(temperature).append(',')
                    .append(PRESSURES[i]).append(',').append(PRESSURES[j]).append(',').append(phase).append(',')
                    .append(fractionScale).append(',').append(velocities[0]).append(',').append(velocities[1])
                    .append(',').append(AREA);
                append(line, left);
                append(line, right);
                append(line, nativeFlux);
                append(line, reference);
                writer.println(line.append(',').append(maximum));
              }
              row++;
            }
          }
        }
      }
    }
    assertEquals(324, row, "Fixed matrix completeness");
    assertTrue(twoPhase > 0 && twoPhase < 18, "Exercise single-phase and two-phase EOS samples");
    assertTrue(wetDry > 0, "Exercise actual equilibrium oil appearance and disappearance");
    assertTrue(negativeEnergy > 0, "Do not mistake negative EOS reference energy for inadmissibility");
  }

  /**
   * Flash the public synthetic composition without seeds or altered phase limits.
   *
   * @param temperature temperature in K
   * @param pressure absolute pressure in bara
   * @return initialized equilibrium SRK/classic sample
   */
  private static SystemSrkEos sample(double temperature, double pressure) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, pressure);
    for (int i = 0; i < COMPONENTS.length; i++) {
      fluid.addComponent(COMPONENTS[i], FRACTIONS[i]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Read phase alpha, density, sound speed, specific h/e, volume convention and component fractions. The
   * internal-energy API uses untranslated EOS volume; the pipeline defines mechanical internal energy with physical
   * density. Account explicitly for their pressure-volume difference without changing either API.
   *
   * @param fluid initialized sample
   * @param name gas or oil
   * @param fractionScale imposed query fraction multiplier, not a transient seed
   * @return thirteen finite values, all zero for an exactly absent phase
   */
  private static double[] phaseSample(SystemSrkEos fluid, String name, double fractionScale) {
    double[] values = new double[13];
    if (!fluid.hasPhaseType(name)) {
      return values;
    }
    PhaseInterface phase = fluid.getPhase(name);
    values[0] = phase.getVolume("m3") / fluid.getVolume("m3") * fractionScale;
    values[1] = phase.getDensity("kg/m3");
    values[2] = phase.getSoundSpeed();
    values[3] = phase.getEnthalpy("J/kg");
    values[4] = phase.getInternalEnergy("J/kg");
    values[5] = phase.getDensity();
    double pressure = fluid.getPressure() * 1e5;
    values[6] = values[4] + pressure * (1.0 / values[5] - 1.0 / values[1]);
    for (int i = 0; i < COMPONENTS.length; i++) {
      values[7 + i] = phase.getComponent(COMPONENTS[i]).getx();
    }
    for (double value : values) {
      assertTrue(Double.isFinite(value), "Nonfinite occupied EOS phase");
    }
    assertTrue(values[0] > 0.0 && values[0] <= 1.0 && values[1] > 0.0 && values[2] > 0.0);
    double pv = pressure / values[5];
    assertEquals(pv, values[3] - values[4], EOS_GATE * (Math.abs(values[3]) + Math.abs(values[4]) + pv),
        "EOS h-e=p/rho in SI units");
    assertEquals(pressure / values[1], values[3] - values[6],
        EOS_GATE * (Math.abs(values[3]) + Math.abs(values[6]) + pressure / values[1]),
        "Physical-density mechanical energy after explicit volume-work conversion");
    return values;
  }

  /**
   * Construct the existing query input with total specific enthalpy and exact absent extensions.
   *
   * @param phase sampled phase properties
   * @param velocity signed velocity in m/s
   * @param pressure absolute pressure in bara
   * @return local phase state
   */
  private static PhaseState state(double[] phase, double velocity, double pressure) {
    if (phase[0] == 0.0) {
      return new PhaseState(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0);
    }
    return new PhaseState(phase[1], velocity, pressure * 1e5, phase[2], phase[3] + 0.5 * velocity * velocity, phase[0]);
  }

  /**
   * Form conserved variables from EOS internal energy, rather than subtracting pressure from enthalpy density.
   *
   * @param phase sampled phase properties
   * @param velocity signed velocity in m/s
   * @return phase mass, momentum and total energy per pipe volume
   */
  private static double[] conserved(double[] phase, double velocity) {
    double mass = phase[0] * phase[1];
    return new double[] {mass, mass * velocity, mass * (phase[6] + 0.5 * velocity * velocity)};
  }

  /**
   * Build physical Euler flux using internal plus kinetic energy and pressure work.
   *
   * @param conserved conserved phase variables
   * @param velocity signed velocity in m/s
   * @param traction phase-area-weighted pressure in Pa
   * @return mass, momentum and total-energy flux per pipe area
   */
  private static double[] physical(double[] conserved, double velocity, double traction) {
    return new double[] {conserved[1], conserved[1] * velocity + traction, velocity * (conserved[2] + traction)};
  }

  /**
   * Append complete numerical evidence with round-trip double precision.
   *
   * @param line current CSV row
   * @param values ordered finite numerical fields
   */
  private static void append(StringBuilder line, double[] values) {
    for (double value : values) {
      line.append(',').append(value);
    }
  }
}
