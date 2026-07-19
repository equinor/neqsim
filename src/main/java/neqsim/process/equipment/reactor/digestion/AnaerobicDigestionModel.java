package neqsim.process.equipment.reactor.digestion;

import java.io.Serializable;

/**
 * Strategy interface for anaerobic-digestion yield and kinetic models.
 *
 * @author NeqSim team
 * @version 1.0
 */
public interface AnaerobicDigestionModel extends Serializable {

  /**
   * Calculates dry-gas production, residual solids, and conservation diagnostics.
   *
   * @param input digestion input
   * @return digestion result
   */
  AnaerobicDigestionResult calculate(AnaerobicDigestionInput input);

  /**
   * Returns the fidelity represented by the model.
   *
   * @return fidelity level
   */
  ModelFidelity getFidelity();

  /**
   * Returns a stable model identifier for reports and serialized results.
   *
   * @return model identifier
   */
  String getModelIdentifier();
}
