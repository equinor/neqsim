package neqsim.process.util.combustion;

/**
 * Optional detailed-chemistry solver boundary used by a finite-rate combustion reactor.
 *
 * <p>
 * The versioned JSON contract preserves species that are absent from the NeqSim component database. A backend must
 * report its mechanism, thermochemistry, conservation and numerical diagnostics, rather than replacing finite-rate
 * chemistry with an emission factor. Backend implementations can be supplied from Python with JPype.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface CombustionKineticsBackend {
  /**
   * Solve the version-1 request and return a version-1 detailed mechanism result.
   *
   * @param requestJson request with component molar flows [mol/s], absolute pressure [Pa], temperatures [K], residence
   * time [s], model and heat-loss coefficient [W/(kg K)]
   * @return JSON with exact species flows [mol/s], molecular masses [kg/mol], atom counts, component mapping,
   * temperature [K], pressure [Pa], heat transfer [W] and solver/energy diagnostics
   */
  String solve(String requestJson);
}
