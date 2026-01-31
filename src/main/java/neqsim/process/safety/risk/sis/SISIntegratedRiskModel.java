package neqsim.process.safety.risk.sis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.RiskEvent;
import neqsim.process.safety.risk.RiskModel;
import neqsim.process.safety.risk.RiskResult;

/**
 * Risk model integrated with Safety Instrumented Systems (SIS).
 *
 * <p>
 * Extends the standard {@link RiskModel} with SIS-specific features:
 * </p>
 * <ul>
 * <li>SIF definitions and PFD calculations</li>
 * <li>Residual risk after SIF mitigation</li>
 * <li>Layer of Protection Analysis (LOPA) support</li>
 * <li>SIL verification against risk targets</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * SISIntegratedRiskModel model = new SISIntegratedRiskModel("Process Risk Study");
 * model.setProcessSystem(processSystem);
 *
 * // Add initiating events
 * model.addInitiatingEvent("Overpressure", 0.1, ConsequenceCategory.MAJOR);
 *
 * // Add SIFs
 * SafetyInstrumentedFunction hipps =
 *     SafetyInstrumentedFunction.builder().name("HIPPS-001").sil(3).pfd(0.001)
 *         .protectedEquipment(Arrays.asList("Pipeline")).initiatingEvent("Overpressure").build();
 * model.addSIF(hipps);
 *
 * // Calculate residual risk
 * SISRiskResult result = model.calculateResidualRisk();
 * System.out.println("Residual frequency: " + result.getResidualFrequency());
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see RiskModel
 * @see SafetyInstrumentedFunction
 */
public class SISIntegratedRiskModel extends RiskModel implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** List of SIFs in the model. */
  private List<SafetyInstrumentedFunction> sifs;

  /** Mapping of equipment to protecting SIFs. */
  private Map<String, List<SafetyInstrumentedFunction>> equipmentSIFs;

  /** Mapping of initiating events to mitigating SIFs. */
  private Map<String, List<SafetyInstrumentedFunction>> eventSIFs;

  /** Independent Protection Layers (non-SIS barriers). */
  private List<IndependentProtectionLayer> ipls;

  /** Risk tolerance criteria. */
  private RiskToleranceCriteria toleranceCriteria;

  /**
   * Independent Protection Layer (IPL) for LOPA.
   */
  public static class IndependentProtectionLayer implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private double pfd; // Probability of Failure on Demand
    private IPLType type;
    private List<String> applicableEvents;

    /**
     * IPL types.
     */
    public enum IPLType {
      /** Basic Process Control System. */
      BPCS,
      /** Alarm with operator response. */
      ALARM,
      /** Mechanical protection (relief valve, rupture disc). */
      MECHANICAL,
      /** Physical barrier (dike, blast wall). */
      PHYSICAL,
      /** Emergency response. */
      EMERGENCY_RESPONSE,
      /** Other IPL. */
      OTHER
    }

    public IndependentProtectionLayer(String name, double pfd, IPLType type) {
      this.name = name;
      this.pfd = pfd;
      this.type = type;
      this.applicableEvents = new ArrayList<>();
    }

    public String getName() {
      return name;
    }

    public double getPfd() {
      return pfd;
    }

    public IPLType getType() {
      return type;
    }

    public double getRiskReductionFactor() {
      return pfd > 0 ? 1.0 / pfd : Double.POSITIVE_INFINITY;
    }

    public void addApplicableEvent(String event) {
      applicableEvents.add(event);
    }

    public List<String> getApplicableEvents() {
      return new ArrayList<>(applicableEvents);
    }
  }

  /**
   * Risk tolerance criteria for SIL determination.
   */
  public static class RiskToleranceCriteria implements Serializable {
    private static final long serialVersionUID = 1L;

    private double tolerableFrequencyFatality = 1e-5; // per year
    private double tolerableFrequencyInjury = 1e-4;
    private double tolerableFrequencyEnvironment = 1e-4;
    private double tolerableFrequencyAsset = 1e-3;
    private double alarP = 1e-6; // As Low As Reasonably Practicable

    public double getTolerableFrequency(ConsequenceType type) {
      switch (type) {
        case FATALITY:
          return tolerableFrequencyFatality;
        case INJURY:
          return tolerableFrequencyInjury;
        case ENVIRONMENT:
          return tolerableFrequencyEnvironment;
        case ASSET:
          return tolerableFrequencyAsset;
        default:
          return tolerableFrequencyAsset;
      }
    }

    public double getALARP() {
      return alarP;
    }

    public void setTolerableFrequencyFatality(double freq) {
      this.tolerableFrequencyFatality = freq;
    }

    public void setTolerableFrequencyAsset(double freq) {
      this.tolerableFrequencyAsset = freq;
    }
  }

  /**
   * Consequence types for risk criteria.
   */
  public enum ConsequenceType {
    FATALITY, INJURY, ENVIRONMENT, ASSET
  }

  /**
   * Creates a SIS-integrated risk model.
   *
   * @param name study name
   */
  public SISIntegratedRiskModel(String name) {
    super(name);
    this.sifs = new ArrayList<>();
    this.equipmentSIFs = new HashMap<>();
    this.eventSIFs = new HashMap<>();
    this.ipls = new ArrayList<>();
    this.toleranceCriteria = new RiskToleranceCriteria();
  }

  // SIF management

  /**
   * Adds a SIF to the model.
   *
   * @param sif safety instrumented function
   */
  public void addSIF(SafetyInstrumentedFunction sif) {
    sifs.add(sif);

    // Index by protected equipment
    for (String equipment : sif.getProtectedEquipment()) {
      equipmentSIFs.computeIfAbsent(equipment, k -> new ArrayList<>()).add(sif);
    }

    // Index by initiating event
    if (sif.getInitiatingEvent() != null) {
      eventSIFs.computeIfAbsent(sif.getInitiatingEvent(), k -> new ArrayList<>()).add(sif);
    }
  }

  /**
   * Gets all SIFs in the model.
   *
   * @return list of SIFs
   */
  public List<SafetyInstrumentedFunction> getSIFs() {
    return new ArrayList<>(sifs);
  }

  /**
   * Gets SIFs protecting specific equipment.
   *
   * @param equipmentName equipment name
   * @return list of protecting SIFs
   */
  public List<SafetyInstrumentedFunction> getSIFsForEquipment(String equipmentName) {
    return new ArrayList<>(equipmentSIFs.getOrDefault(equipmentName, new ArrayList<>()));
  }

  /**
   * Gets SIFs mitigating a specific initiating event.
   *
   * @param eventName event name
   * @return list of mitigating SIFs
   */
  public List<SafetyInstrumentedFunction> getSIFsForEvent(String eventName) {
    return new ArrayList<>(eventSIFs.getOrDefault(eventName, new ArrayList<>()));
  }

  // IPL management

  /**
   * Adds an Independent Protection Layer.
   *
   * @param ipl the IPL
   */
  public void addIPL(IndependentProtectionLayer ipl) {
    ipls.add(ipl);
  }

  /**
   * Gets all IPLs.
   *
   * @return list of IPLs
   */
  public List<IndependentProtectionLayer> getIPLs() {
    return new ArrayList<>(ipls);
  }

  // Risk calculations

  /**
   * Calculates residual risk after SIF mitigation.
   *
   * @return SIS risk result
   */
  public SISRiskResult calculateResidualRisk() {
    SISRiskResult result = new SISRiskResult(getName());

    // Process each initiating event
    for (RiskEvent event : getInitiatingEvents()) {
      double unmitigatedFreq = event.getFrequency();
      double mitigatedFreq = unmitigatedFreq;

      // Apply SIFs for this event
      List<SafetyInstrumentedFunction> applicableSIFs = getSIFsForEvent(event.getName());
      for (SafetyInstrumentedFunction sif : applicableSIFs) {
        mitigatedFreq = sif.getMitigatedFrequency(mitigatedFreq);
      }

      // Apply IPLs for this event
      for (IndependentProtectionLayer ipl : ipls) {
        if (ipl.getApplicableEvents().contains(event.getName())
            || ipl.getApplicableEvents().isEmpty()) {
          mitigatedFreq *= ipl.getPfd();
        }
      }

      result.addEventResult(event.getName(), unmitigatedFreq, mitigatedFreq, applicableSIFs);
    }

    result.calculateTotals();
    return result;
  }

  /**
   * Performs Layer of Protection Analysis (LOPA) for an event.
   *
   * @param eventName initiating event name
   * @return LOPA result
   */
  public LOPAResult performLOPA(String eventName) {
    LOPAResult lopa = new LOPAResult(eventName);

    // Find the event
    RiskEvent event = null;
    for (RiskEvent e : getInitiatingEvents()) {
      if (e.getName().equals(eventName)) {
        event = e;
        break;
      }
    }

    if (event == null) {
      return lopa;
    }

    lopa.setInitiatingEventFrequency(event.getFrequency());

    // Add IPL layers
    double currentFreq = event.getFrequency();
    for (IndependentProtectionLayer ipl : ipls) {
      if (ipl.getApplicableEvents().contains(eventName) || ipl.getApplicableEvents().isEmpty()) {
        lopa.addLayer(ipl.getName(), ipl.getPfd(), currentFreq, currentFreq * ipl.getPfd());
        currentFreq *= ipl.getPfd();
      }
    }

    // Add SIF layers
    for (SafetyInstrumentedFunction sif : getSIFsForEvent(eventName)) {
      double nextFreq = sif.getMitigatedFrequency(currentFreq);
      lopa.addLayer(sif.getName() + " (SIL" + sif.getSil() + ")", sif.getPfdAvg(), currentFreq,
          nextFreq);
      currentFreq = nextFreq;
    }

    lopa.setMitigatedFrequency(currentFreq);
    lopa.setTargetFrequency(toleranceCriteria.getTolerableFrequency(ConsequenceType.ASSET));

    return lopa;
  }

  /**
   * Determines required SIL for an event to meet risk criteria.
   *
   * @param eventName event name
   * @param consequenceType consequence type for criteria
   * @return required SIL (0 if no SIF needed, -1 if SIL4 insufficient)
   */
  public int determineRequiredSIL(String eventName, ConsequenceType consequenceType) {
    LOPAResult lopa = performLOPA(eventName);
    double mitigatedFreq = lopa.getMitigatedFrequency();
    double targetFreq = toleranceCriteria.getTolerableFrequency(consequenceType);

    if (mitigatedFreq <= targetFreq) {
      return 0; // No additional SIF needed
    }

    double requiredRRF = mitigatedFreq / targetFreq;
    double requiredPFD = 1.0 / requiredRRF;

    return SafetyInstrumentedFunction.getRequiredSil(requiredPFD);
  }

  /**
   * Verifies that SIFs meet required SIL for all events.
   *
   * @return verification results as a map (or null if no SIFs)
   */
  public Map<String, SILVerificationResult> verifySILRequirements() {
    Map<String, SILVerificationResult> results = new HashMap<>();

    for (SafetyInstrumentedFunction sif : getSIFs()) {
      SILVerificationResult verification = new SILVerificationResult(sif);
      results.put(sif.getName(), verification);
    }

    return results;
  }

  /**
   * Sets risk tolerance criteria.
   *
   * @param criteria tolerance criteria
   */
  public void setToleranceCriteria(RiskToleranceCriteria criteria) {
    this.toleranceCriteria = criteria;
  }

  /**
   * Gets risk tolerance criteria.
   *
   * @return tolerance criteria
   */
  public RiskToleranceCriteria getToleranceCriteria() {
    return toleranceCriteria;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", getName());

    // SIFs
    List<Map<String, Object>> sifList = new ArrayList<>();
    for (SafetyInstrumentedFunction sif : sifs) {
      sifList.add(sif.toMap());
    }
    map.put("sifs", sifList);

    // IPLs
    List<Map<String, Object>> iplList = new ArrayList<>();
    for (IndependentProtectionLayer ipl : ipls) {
      Map<String, Object> iplMap = new HashMap<>();
      iplMap.put("name", ipl.getName());
      iplMap.put("pfd", ipl.getPfd());
      iplMap.put("type", ipl.getType().name());
      iplList.add(iplMap);
    }
    map.put("ipls", iplList);

    // Events
    List<Map<String, Object>> eventList = new ArrayList<>();
    for (RiskEvent event : getInitiatingEvents()) {
      Map<String, Object> eventMap = new HashMap<>();
      eventMap.put("name", event.getName());
      eventMap.put("frequency", event.getFrequency());
      eventList.add(eventMap);
    }
    map.put("initiatingEvents", eventList);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
