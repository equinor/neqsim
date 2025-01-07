package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * MultiStreamHeatExchangerInterface interface.
 *
 * <p>
 * Defines the contract for a multi-stream heat exchanger, enabling the simulation and management of
 * multiple input and output streams. This interface extends the {@link ProcessEquipmentInterface}
 * to integrate with the broader NeqSim process simulation framework.
 * </p>
 *
 * <p>
 * Implementations of this interface should handle the addition and management of multiple streams,
 * perform energy and mass balance calculations, and provide methods to analyze the performance and
 * condition of the heat exchanger.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public interface MultiStreamHeatExchangerInterface extends ProcessEquipmentInterface {

  // ================================
  // Stream Management Methods
  // ================================

  /**
   * Adds an inlet stream to the heat exchanger.
   *
   * @param inStream Input stream to be added
   */
  void addInStream(StreamInterface inStream);

  /**
   * Sets the feed stream at a specific index.
   *
   * @param index Index of the stream to set
   * @param inStream Input stream to set at the specified index
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  void setFeedStream(int index, StreamInterface inStream);

  /**
   * Retrieves the output stream at the specified index.
   *
   * @param index Index of the output stream
   * @return The output {@link StreamInterface} at the given index
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  StreamInterface getOutStream(int index);

  /**
   * Retrieves the input stream at the specified index.
   *
   * @param index Index of the input stream
   * @return The input {@link StreamInterface} at the given index
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  StreamInterface getInStream(int index);

  // ================================
  // Temperature Control Methods
  // ================================

  /**
   * Sets the outlet temperature for the heat exchanger.
   *
   * @param temperature Desired outlet temperature
   */
  void setOutTemperature(double temperature);

  /**
   * Gets the outlet temperature of a specific output stream.
   *
   * @param index Index of the output stream
   * @return Outlet temperature in Kelvin
   */
  double getOutTemperature(int index);

  /**
   * Gets the inlet temperature of a specific input stream.
   *
   * @param index Index of the input stream
   * @return Inlet temperature in Kelvin
   */
  double getInTemperature(int index);

  /**
   * Sets the temperature difference (ΔT) for the heat exchanger calculations.
   *
   * @param dT Temperature difference to set
   */
  void setdT(double dT);

  /**
   * Enables or disables the use of a fixed temperature difference (ΔT) in calculations.
   *
   * @param useDeltaT True to use ΔT, false otherwise
   */
  void setUseDeltaT(boolean useDeltaT);

  /**
   * Sets the fixed temperature difference (ΔT) for calculations and enables its usage.
   *
   * @param deltaT Fixed temperature difference to set
   */
  void setDeltaT(double deltaT);

  /**
   * Retrieves the fixed temperature difference (ΔT) used in calculations.
   *
   * @return Temperature difference ΔT
   */
  double getDeltaT();

  // ================================
  // Thermodynamic and Energy Methods
  // ================================

  /**
   * Calculates and retrieves the thermal effectiveness of the heat exchanger.
   *
   * @return Thermal effectiveness
   */
  double getThermalEffectiveness();

  /**
   * Sets the thermal effectiveness of the heat exchanger.
   *
   * @param thermalEffectiveness Thermal effectiveness to set
   */
  void setThermalEffectiveness(double thermalEffectiveness);

  /**
   * Calculates the thermal effectiveness based on the Number of Transfer Units (NTU) and the
   * capacity ratio (Cr).
   *
   * @param NTU Number of Transfer Units
   * @param Cr Capacity ratio (Cmin/Cmax)
   * @return Calculated thermal effectiveness
   */
  double calcThermalEffectiveness(double NTU, double Cr);

  /**
   * Retrieves the overall heat transfer coefficient times area (UA value).
   *
   * @return UA value
   */
  double getUAvalue();

  /**
   * Sets the overall heat transfer coefficient times area (UA value).
   *
   * @param UAvalue UA value to set
   */
  void setUAvalue(double UAvalue);

  /**
   * Retrieves the hot and cold duty balance of the heat exchanger.
   *
   * @return Hot and cold duty balance
   */
  double getHotColdDutyBalance();

  /**
   * Sets the hot and cold duty balance of the heat exchanger.
   *
   * @param hotColdDutyBalance Hot and cold duty balance to set
   */
  void setHotColdDutyBalance(double hotColdDutyBalance);

  // ================================
  // Duty and Balance Methods
  // ================================

  /**
   * Retrieves the duty (heat transfer) of the heat exchanger.
   *
   * @return Duty in appropriate units
   */
  double getDuty();

  /**
   * Retrieves the mass balance of the heat exchanger.
   *
   * @param unit Unit of mass flow rate (e.g., "kg/sec")
   * @return Mass balance value
   */
  double getMassBalance(String unit);

  /**
   * Retrieves the entropy production of the heat exchanger.
   *
   * @param unit Unit of entropy (e.g., "J/(kg*K)")
   * @return Entropy production value
   */
  double getEntropyProduction(String unit);

  // ================================
  // Flow Arrangement Methods
  // ================================

  /**
   * Retrieves the flow arrangement of the heat exchanger.
   *
   * @return Flow arrangement as a String (e.g., "counterflow", "parallelflow")
   */
  String getFlowArrangement();

  /**
   * Sets the flow arrangement of the heat exchanger.
   *
   * @param flowArrangement Name of the flow arrangement
   */
  void setFlowArrangement(String flowArrangement);

  // ================================
  // Condition Analysis Methods
  // ================================

  /**
   * Runs a condition analysis by comparing the current heat exchanger with a reference exchanger.
   *
   * @param refExchanger Reference {@link ProcessEquipmentInterface} heat exchanger for comparison
   */
  void runConditionAnalysis(ProcessEquipmentInterface refExchanger);

  /**
   * Runs a condition analysis using the current heat exchanger as the reference.
   */
  void runConditionAnalysis();

  // ================================
  // Guess Temperature Methods
  // ================================

  /**
   * Retrieves the guessed outlet temperature used during initialization.
   *
   * @return Guessed outlet temperature
   */
  double getGuessOutTemperature();

  /**
   * Sets the guessed outlet temperature in Kelvin.
   *
   * @param guessOutTemperature Guessed outlet temperature
   */
  void setGuessOutTemperature(double guessOutTemperature);

  /**
   * Sets the guessed outlet temperature with a specified unit.
   *
   * @param guessOutTemperature Guessed outlet temperature
   * @param unit Unit of the temperature (e.g., "K", "C")
   */
  void setGuessOutTemperature(double guessOutTemperature, String unit);

  // ================================
  // JSON Serialization Methods
  // ================================

  /**
   * Serializes the heat exchanger's state to a JSON string.
   *
   * @return JSON representation of the heat exchanger
   */
  String toJson();

  // ================================
  // Additional Methods
  // ================================

  /**
   * Runs the heat exchanger simulation with a unique identifier.
   *
   * @param id Unique identifier for the simulation run
   */
  void run(UUID id);

  /**
   * Displays the results of the heat exchanger simulation. Typically outputs temperatures,
   * pressures, flow rates, etc.
   */
  void displayResult();
}
