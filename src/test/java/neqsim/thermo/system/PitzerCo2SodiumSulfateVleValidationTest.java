package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.ElectrolytePhaseBoundaryResult;

/** Held-out CO2-Na2SO4 bubble-pressure evidence for the Pitzer qualification boundary. */
class PitzerCo2SodiumSulfateVleValidationTest extends neqsim.NeqSimTest {
  private static final double WATER_MOLES_PER_KILOGRAM = 55.508;
  private static final String REFERENCE_RESOURCE = "/neqsim/thermo/system/bermejo-co2-na2so4-bubble-pressure-reference.csv";

  /**
   * The qualified activity subset closes every flash but does not reproduce independent gas-aqueous VLE data.
   *
   * @throws IOException if the reviewed validation fixture cannot be read
   */
  @Test
  void heldOutBubblePressuresRejectGasAqueousVleQualification() throws IOException {
    double minimumAbsoluteRelativeResidual = Double.POSITIVE_INFINITY;
    double maximumAbsoluteRelativeResidual = 0.0;

    for (ReferenceState state : readReferenceStates()) {
      ElectrolytePhaseBoundaryResult result = calculateBubblePressure(state);
      double calculatedPressure = result.getBoundaryValue();
      double absoluteRelativeResidual = Math.abs(calculatedPressure - state.pressureBara) / state.pressureBara;

      assertEquals(state.masterCalculatedPressureBara, calculatedPressure, 0.03, state.pointId);
      assertEquals(PhaseType.GAS, result.getTargetPhase(), state.pointId);
      assertTrue(calculatedPressure < state.pressureBara - state.expandedUncertaintyBara, state.pointId);
      assertTrue(result.getBracketWidth() <= 0.02, state.pointId);
      assertTrue(result.getTargetPhaseFraction() > 1.0e-10, state.pointId);
      assertTrue(result.getMaximumMaterialBalanceResidual() <= 1.0e-7, state.pointId);
      assertTrue(result.getMaximumPhaseNormalizationResidual() <= 1.0e-10, state.pointId);
      assertTrue(Math.abs(result.getAqueousChargeMolality()) <= 1.0e-8, state.pointId);
      assertTrue(result.getMaximumIonMoleFractionOutsideAqueous() <= 1.0e-30, state.pointId);
      assertTrue(result.getMaximumLogFugacityResidual() <= 1.0e-5, state.pointId);
      assertTrue(result.getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8, state.pointId);
      assertTrue(result.getMaximumAbsoluteReactionLogResidual() <= 2.0e-6, state.pointId);
      minimumAbsoluteRelativeResidual = Math.min(minimumAbsoluteRelativeResidual, absoluteRelativeResidual);
      maximumAbsoluteRelativeResidual = Math.max(maximumAbsoluteRelativeResidual, absoluteRelativeResidual);
    }

    assertTrue(minimumAbsoluteRelativeResidual >= 0.325);
    assertTrue(maximumAbsoluteRelativeResidual <= 0.439);
  }

  @Test
  void malformedReferenceStateReportsPointContext() {
    String[] fields = { "invalid-point", "not-a-number", "0.01", "323.15", "75.0", "1.0", "50.0" };

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ReferenceState(fields));

    assertTrue(exception.getMessage().contains("invalid-point"));
    assertTrue(exception.getMessage().contains("not-a-number"));
    assertTrue(exception.getCause() instanceof NumberFormatException);
  }

  private static ElectrolytePhaseBoundaryResult calculateBubblePressure(ReferenceState state) {
    double saltFormulaMoles = state.saltMolality;
    double carbonDioxideMoles = state.carbonDioxideLiquidMoleFraction / (1.0 - state.carbonDioxideLiquidMoleFraction)
        * (WATER_MOLES_PER_KILOGRAM + saltFormulaMoles);
    SystemPitzer system = new SystemPitzer(state.temperatureKelvin, state.pressureBara);
    system.addComponent("CO2", carbonDioxideMoles);
    system.addComponent("water", WATER_MOLES_PER_KILOGRAM);
    system.addComponent("Na+", 2.0 * saltFormulaMoles);
    system.addComponent("SO4--", saltFormulaMoles);
    system.init(0);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return new ThermodynamicOperations(system).electrolytePhaseBoundaryPressureFlash(PhaseType.GAS, 1.0, 200.0, 0.02,
        30);
  }

  private static List<ReferenceState> readReferenceStates() throws IOException {
    InputStream input = PitzerCo2SodiumSulfateVleValidationTest.class.getResourceAsStream(REFERENCE_RESOURCE);
    assertNotNull(input, REFERENCE_RESOURCE);
    List<ReferenceState> states = new ArrayList<ReferenceState>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("point_id")) {
          continue;
        }
        String[] fields = line.split(",");
        assertEquals(7, fields.length, line);
        states.add(new ReferenceState(fields));
      }
    }
    assertEquals(6, states.size());
    return states;
  }

  private static final class ReferenceState {
    private final String pointId;
    private final double saltMolality;
    private final double carbonDioxideLiquidMoleFraction;
    private final double temperatureKelvin;
    private final double pressureBara;
    private final double expandedUncertaintyBara;
    private final double masterCalculatedPressureBara;

    private ReferenceState(String[] fields) {
      pointId = fields[0];
      try {
        saltMolality = Double.parseDouble(fields[1]);
        carbonDioxideLiquidMoleFraction = Double.parseDouble(fields[2]);
        temperatureKelvin = Double.parseDouble(fields[3]);
        pressureBara = Double.parseDouble(fields[4]);
        expandedUncertaintyBara = Double.parseDouble(fields[5]);
        masterCalculatedPressureBara = Double.parseDouble(fields[6]);
      } catch (NumberFormatException invalidNumber) {
        throw new IllegalArgumentException(
            "Invalid numeric value for point " + pointId + ": " + String.join(",", fields), invalidNumber);
      }
    }
  }
}
