package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.safety.dto.DisposalNetworkSummaryDTO;

/**
 * High level helper coordinating load case evaluation for disposal networks.
 */
public class ProcessSafetyAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DisposalNetwork disposalNetwork = new DisposalNetwork();
  private final List<ProcessSafetyLoadCase> loadCases = new ArrayList<>();

  public void registerDisposalUnit(Flare flare) {
    disposalNetwork.registerDisposalUnit(flare);
  }

  public void mapSourceToDisposal(String sourceId, String disposalUnitName) {
    disposalNetwork.mapSourceToDisposal(sourceId, disposalUnitName);
  }

  public void addLoadCase(ProcessSafetyLoadCase loadCase) {
    loadCases.add(loadCase);
  }

  public List<ProcessSafetyLoadCase> getLoadCases() {
    return Collections.unmodifiableList(loadCases);
  }

  public DisposalNetworkSummaryDTO analyze() {
    return disposalNetwork.evaluate(loadCases);
  }
}
