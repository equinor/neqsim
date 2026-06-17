package neqsim.mcp.model;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.util.monitor.FluidResponse;

/**
 * Typed result model for flash calculations.
 *
 * <p>
 * Contains the flash metadata (model, flash type, number of phases, phase names) and the full fluid
 * response. This object is the typed counterpart to the JSON returned by
 * {@code FlashRunner.run(String)}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FlashResult {

  private final String model;
  private final String flashType;
  private final int numberOfPhases;
  private final List<String> phases;
  private final FluidResponse fluidResponse;

  /**
   * Creates a flash result.
   *
   * @param model the thermodynamic model name
   * @param flashType the flash type performed
   * @param numberOfPhases the number of phases found
   * @param phases the list of phase names
   * @param fluidResponse the full fluid response
   */
  public FlashResult(String model, String flashType, int numberOfPhases, List<String> phases,
      FluidResponse fluidResponse) {
    this.model = model;
    this.flashType = flashType;
    this.numberOfPhases = numberOfPhases;
    this.phases = phases != null ? phases : new ArrayList<String>();
    this.fluidResponse = fluidResponse;
  }

  /**
   * Gets the thermodynamic model name.
   *
   * @return the model
   */
  public String getModel() {
    return model;
  }

  /**
   * Gets the flash type that was performed.
   *
   * @return the flash type
   */
  public String getFlashType() {
    return flashType;
  }

  /**
   * Gets the number of phases found.
   *
   * @return the phase count
   */
  public int getNumberOfPhases() {
    return numberOfPhases;
  }

  /**
   * Gets the list of phase names.
   *
   * @return the phase name list
   */
  public List<String> getPhases() {
    return phases;
  }

  /**
   * Gets the full fluid response.
   *
   * @return the FluidResponse object
   */
  public FluidResponse getFluidResponse() {
    return fluidResponse;
  }
}
