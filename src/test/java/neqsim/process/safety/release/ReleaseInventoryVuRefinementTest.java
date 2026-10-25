package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Strict extensive VU closure for dense-liquid inventory withdrawal, independent of nozzle-flow correlations.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ReleaseInventoryVuRefinementTest extends neqsim.NeqSimTest {
  /**
   * Checks nearby states, inventory scales and repeated withdrawals with both cubic models and mixing rules.
   *
   * @param pengRobinson whether to use PR rather than SRK
   * @param mixingRule classical or Huron-Vidal mixing rule
   */
  @ParameterizedTest
  @CsvSource({"false,2", "true,2", "false,4", "true,4"})
  void liquidWithdrawalClosesVolumeAndEnergy(boolean pengRobinson, int mixingRule) {
    for (double temperature : new double[] {295.0, 300.0, 305.0}) {
      for (double volume : new double[] {0.002, 0.02}) {
        SystemInterface source = pengRobinson ? new SystemPrEos(temperature, 20.0)
            : new SystemSrkEos(temperature, 20.0);
        source.addComponent("propane", 0.8);
        source.addComponent("n-butane", 0.2);
        source.setMixingRule(mixingRule);
        new ThermodynamicOperations(source).TPflash();
        source.init(2);
        assertEquals(1, source.getNumberOfPhases());
        PhaseType phase = source.getPhase(0).getType();
        ReleaseInventory vessel = new ReleaseInventory("liquid refinement", source, volume, 0.001, 0.62, 3e5,
            constantRelease(0.002), 0.01, phase);
        for (int step = 0; step < 3; step++) {
          SystemInterface before = vessel.getFluid();
          double energy = before.getInternalEnergy("J");
          double enthalpy = before.getEnthalpy("J/kg");
          double mass = before.getMass("kg");
          vessel.runTransient(0.01, UUID.randomUUID());
          SystemInterface after = vessel.getFluid();
          double targetEnergy = energy - 0.00002 * enthalpy;
          assertEquals(volume, after.getVolume("m3"), volume * 1e-11, "strict rigid-volume residual");
          assertEquals(targetEnergy, after.getInternalEnergy("J"), Math.abs(energy) * 1e-11,
              "strict open-system energy residual");
          assertEquals(mass - 0.00002, after.getMass("kg"), mass * 1e-12);
          assertEquals(1, after.getNumberOfPhases());
          assertEquals(phase, after.getPhase(0).getType());
          assertEquals(after.getPressure(), ((PhaseEos) after.getPhase(0)).calcPressure(), 1e-8 * after.getPressure(),
              "pressure-consistent root");
          assertTrue(vessel.getLastVolumeEnergySolves() <= 8, "bounded strict refinement work");
        }
        assertEquals(temperature, source.getTemperature(), 0.0, "source state remains independent");
        assertEquals(20.0, source.getPressure(), 0.0);
      }
    }
  }

  /**
   * Supplies a conserved stagnation state and constant mass flux to isolate inventory thermodynamics.
   *
   * @param rate mass flow in kg/s
   * @return deterministic release model
   */
  private static ReleaseFlowModel constantRelease(final double rate) {
    return new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public String getModelId() {
        return "test-vu-refinement";
      }

      /** {@inheritDoc} */
      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        SystemInterface fluid = request.getFluid();
        fluid.init(2);
        ReleaseState state = ReleaseState.fromFluid(fluid, 0.0);
        Map<ReleaseFlowResult.Station, ReleaseState> stations = new EnumMap<ReleaseFlowResult.Station, ReleaseState>(
            ReleaseFlowResult.Station.class);
        stations.put(ReleaseFlowResult.Station.UPSTREAM_STAGNATION, state);
        stations.put(ReleaseFlowResult.Station.THROAT_CRITICAL, state);
        stations.put(ReleaseFlowResult.Station.ORIFICE_EXIT, state);
        stations.put(ReleaseFlowResult.Station.AMBIENT_EXPANDED, state);
        return ReleaseFlowResult.success(this, rate, false, stations, Collections.singletonList(
            new ReleaseFlowResult.Diagnostic("CONTROLLED_TEST_MODEL", "Constant flux isolates strict VU conservation")),
            null, false);
      }
    };
  }
}
