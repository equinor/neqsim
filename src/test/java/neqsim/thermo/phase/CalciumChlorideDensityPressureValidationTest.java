package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.flashops.saturationops.CalciumSulfatePhaseBoundaryQualification;

/**
 * Independent pressure-density hold-out for aqueous calcium chloride.
 *
 * <p>
 * The complete NIST ThermoML series is evaluated without fitting. Its one mol/kg lower concentration bound cannot
 * determine the infinite-dilution calcium-chloride volume required by the calcium-sulfate reaction-volume cycle.
 * </p>
 */
class CalciumChlorideDensityPressureValidationTest extends neqsim.NeqSimTest {
  private static final String RESOURCE = "/data/chemistry_benchmarks/nist-thermoml-cacl2-pressure-density.csv";

  @Test
  void completeNistThermoMlMatrixIsAuditedAndCurrentModelFailsClosed() throws Exception {
    InputStream stream = getClass().getResourceAsStream(RESOURCE);
    assertNotNull(stream, "Missing CaCl2 pressure-density validation resource " + RESOURCE);

    int count = 0;
    double minimumMolality = Double.POSITIVE_INFINITY;
    double maximumMolality = Double.NEGATIVE_INFINITY;
    double minimumTemperature = Double.POSITIVE_INFINITY;
    double maximumTemperature = Double.NEGATIVE_INFINITY;
    double minimumPressureBara = Double.POSITIVE_INFINITY;
    double maximumPressureBara = Double.NEGATIVE_INFINITY;
    double minimumRowRelativeUncertainty = Double.POSITIVE_INFINITY;
    double maximumRowRelativeUncertainty = Double.NEGATIVE_INFINITY;
    double absoluteRelativeResidualSum = 0.0;
    double squaredRelativeResidualSum = 0.0;
    double maximumAbsoluteRelativeResidual = 0.0;
    double maximumExpandedUncertaintyRatio = 0.0;
    double pressureAdjustedAbsoluteRelativeResidualSum = 0.0;
    double pressureAdjustedSquaredRelativeResidualSum = 0.0;
    double maximumPressureAdjustedAbsoluteRelativeResidual = 0.0;
    double maximumPressureIncrementUncertaintyRatio = 0.0;
    int pressureAdjustedCount = 0;
    Map<Double, Integer> countsByMolality = new HashMap<Double, Integer>();
    Map<Double, Double> pressureAdjustedAbsoluteResidualByMolality = new HashMap<Double, Double>();
    Map<Double, Integer> pressureAdjustedCountByMolality = new HashMap<Double, Integer>();
    Map<Double, SystemPitzer> systemsByMolality = new HashMap<Double, SystemPitzer>();
    Map<String, double[]> pressureAnchors = new HashMap<String, double[]>();
    Set<String> coordinates = new HashSet<String>();
    MessageDigest rowDigest = MessageDigest.getInstance("SHA-256");

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith("molality_")) {
          continue;
        }
        rowDigest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        ReferenceState state = new ReferenceState(line.split(","));
        assertTrue(coordinates.add(state.coordinateKey()), "Duplicate ThermoML state " + state.coordinateKey());
        countsByMolality.put(state.molality, countsByMolality.getOrDefault(state.molality, 0) + 1);

        minimumMolality = Math.min(minimumMolality, state.molality);
        maximumMolality = Math.max(maximumMolality, state.molality);
        minimumTemperature = Math.min(minimumTemperature, state.temperatureKelvin);
        maximumTemperature = Math.max(maximumTemperature, state.temperatureKelvin);
        minimumPressureBara = Math.min(minimumPressureBara, state.pressureBara);
        maximumPressureBara = Math.max(maximumPressureBara, state.pressureBara);
        double rowRelativeUncertainty = state.expandedUncertainty / state.density;
        minimumRowRelativeUncertainty = Math.min(minimumRowRelativeUncertainty, rowRelativeUncertainty);
        maximumRowRelativeUncertainty = Math.max(maximumRowRelativeUncertainty, rowRelativeUncertainty);

        double calculatedDensity = calculateDensity(state, systemsByMolality);
        assertTrue(Double.isFinite(calculatedDensity) && calculatedDensity > 0.0);
        double relativeResidual = calculatedDensity / state.density - 1.0;
        absoluteRelativeResidualSum += Math.abs(relativeResidual);
        squaredRelativeResidualSum += relativeResidual * relativeResidual;
        maximumAbsoluteRelativeResidual = Math.max(maximumAbsoluteRelativeResidual, Math.abs(relativeResidual));
        maximumExpandedUncertaintyRatio = Math.max(maximumExpandedUncertaintyRatio,
            Math.abs(calculatedDensity - state.density) / state.expandedUncertainty);
        String isothermKey = state.molality + ":" + state.temperatureKelvin;
        double[] anchor = pressureAnchors.get(isothermKey);
        if (anchor == null) {
          pressureAnchors.put(isothermKey,
              new double[] { state.pressureBara, state.density, calculatedDensity, state.expandedUncertainty });
        } else {
          assertTrue(state.pressureBara > anchor[0], "ThermoML isotherm is not ordered from its pressure anchor");
          double pressureAdjustedRelativeResidual = (calculatedDensity - anchor[2] + anchor[1]) / state.density - 1.0;
          pressureAdjustedAbsoluteRelativeResidualSum += Math.abs(pressureAdjustedRelativeResidual);
          pressureAdjustedAbsoluteResidualByMolality.put(state.molality,
              pressureAdjustedAbsoluteResidualByMolality.getOrDefault(state.molality, 0.0)
                  + Math.abs(pressureAdjustedRelativeResidual));
          pressureAdjustedCountByMolality.put(state.molality,
              pressureAdjustedCountByMolality.getOrDefault(state.molality, 0) + 1);
          pressureAdjustedSquaredRelativeResidualSum += pressureAdjustedRelativeResidual
              * pressureAdjustedRelativeResidual;
          maximumPressureAdjustedAbsoluteRelativeResidual = Math.max(maximumPressureAdjustedAbsoluteRelativeResidual,
              Math.abs(pressureAdjustedRelativeResidual));
          double pressureIncrementResidual = (calculatedDensity - anchor[2]) - (state.density - anchor[1]);
          maximumPressureIncrementUncertaintyRatio = Math.max(maximumPressureIncrementUncertaintyRatio,
              Math.abs(pressureIncrementResidual) / Math.hypot(state.expandedUncertainty, anchor[3]));
          pressureAdjustedCount++;
        }
        count++;
      }
    }

    assertEquals(197, count);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_EVIDENCE_ROW_SHA256,
        toHex(rowDigest.digest()));
    assertEquals(71, countsByMolality.get(1.0));
    assertEquals(63, countsByMolality.get(3.0));
    assertEquals(63, countsByMolality.get(6.0));
    assertEquals(1.0, minimumMolality, 0.0);
    assertEquals(6.0, maximumMolality, 0.0);
    assertEquals(283.15, minimumTemperature, 0.0);
    assertEquals(472.96, maximumTemperature, 0.0);
    assertEquals(10.5, minimumPressureBara, 0.0);
    assertEquals(681.2, maximumPressureBara, 0.0);
    assertEquals(9.932459276916966e-5, minimumRowRelativeUncertainty, 1.0e-16);
    assertEquals(0.0012367704653526571, maximumRowRelativeUncertainty, 1.0e-15);

    double meanAbsoluteRelativeError = absoluteRelativeResidualSum / count;
    double rootMeanSquaredRelativeError = Math.sqrt(squaredRelativeResidualSum / count);
    assertEquals(0.006323634129216511, meanAbsoluteRelativeError, 1.0e-12);
    assertEquals(0.008452033776539447, rootMeanSquaredRelativeError, 1.0e-12);
    assertEquals(0.026607051737136622, maximumAbsoluteRelativeResidual, 1.0e-12);
    assertEquals(107.15649486776948, maximumExpandedUncertaintyRatio, 1.0e-9);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_GROUP_COUNT,
        pressureAnchors.size());
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_COMPARISON_COUNT,
        pressureAdjustedCount);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_MARE,
        pressureAdjustedAbsoluteRelativeResidualSum / pressureAdjustedCount, 1.0e-12);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_RMSRE,
        Math.sqrt(pressureAdjustedSquaredRelativeResidualSum / pressureAdjustedCount), 1.0e-12);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_MAXARE,
        maximumPressureAdjustedAbsoluteRelativeResidual, 1.0e-12);
    assertEquals(CalciumSulfatePhaseBoundaryQualification.AQUEOUS_PRESSURE_RESPONSE_MAXIMUM_UNCERTAINTY_RATIO,
        maximumPressureIncrementUncertaintyRatio, 1.0e-9);
    assertEquals(0.003412628603195073,
        pressureAdjustedAbsoluteResidualByMolality.get(1.0) / pressureAdjustedCountByMolality.get(1.0), 1.0e-12);
    assertEquals(0.006695893425517834,
        pressureAdjustedAbsoluteResidualByMolality.get(3.0) / pressureAdjustedCountByMolality.get(3.0), 1.0e-12);
    assertEquals(0.00809959684613692,
        pressureAdjustedAbsoluteResidualByMolality.get(6.0) / pressureAdjustedCountByMolality.get(6.0), 1.0e-12);
    assertTrue(meanAbsoluteRelativeError > maximumRowRelativeUncertainty);
    assertFalse(CalciumSulfatePhaseBoundaryQualification.isAqueousPressureDensityModelQualified());
    assertFalse(CalciumSulfatePhaseBoundaryQualification.isAqueousPressureResponseQualified());
  }

  private static double calculateDensity(ReferenceState state, Map<Double, SystemPitzer> systemsByMolality) {
    SystemPitzer system = systemsByMolality.get(state.molality);
    if (system == null) {
      system = new SystemPitzer(state.temperatureKelvin, state.pressureBara);
      system.addComponent("water", 55.508);
      system.addComponent("Ca++", state.molality);
      system.addComponent("Cl-", 2.0 * state.molality);
      system.setMixingRule("classic");
      system.init(0);
      systemsByMolality.put(state.molality, system);
    } else {
      system.setTemperature(state.temperatureKelvin);
      system.setPressure(state.pressureBara);
    }
    return system.getPhase(1).getDensity();
  }

  private static String toHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(2 * bytes.length);
    for (byte value : bytes) {
      result.append(String.format("%02x", value & 0xff));
    }
    return result.toString();
  }

  private static final class ReferenceState {
    private final double molality;
    private final double temperatureKelvin;
    private final double pressureBara;
    private final double density;
    private final double expandedUncertainty;

    private ReferenceState(String[] fields) {
      if (fields.length != 5) {
        throw new IllegalArgumentException("Unexpected CaCl2 validation row length: " + fields.length);
      }
      molality = Double.parseDouble(fields[0]);
      temperatureKelvin = Double.parseDouble(fields[1]);
      pressureBara = Double.parseDouble(fields[2]) / 100.0;
      density = Double.parseDouble(fields[3]);
      expandedUncertainty = Double.parseDouble(fields[4]);
      if (!(molality > 0.0) || !(temperatureKelvin > 0.0) || !(pressureBara > 0.0) || !(density > 0.0)
          || !(expandedUncertainty > 0.0)) {
        throw new IllegalArgumentException("Non-positive CaCl2 validation value");
      }
    }

    private String coordinateKey() {
      return molality + ":" + temperatureKelvin + ":" + pressureBara;
    }
  }
}
