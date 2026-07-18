package neqsim.process.engineering.pid.rules;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidDesignRule;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/** Represents every process unit and its stream boundaries in the generated P&amp;ID model. */
public final class ProcessTopologyPidRule implements PidDesignRule {
  public static final String RULE_ID = "PID-PROCESS-TOPOLOGY";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment != null;
  }

  @Override
  public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
    List<PidElement> result = new ArrayList<PidElement>();
    addBoundaries(result, context, equipment, equipment.getInletStreams(), "INLET");
    addBoundaries(result, context, equipment, equipment.getOutletStreams(), "OUTLET");
    if (result.isEmpty()) {
      result.add(PidRuleSupport
          .element(context, equipment, "OPC", "PROCESS-BOUNDARY", PidElementType.OFF_PAGE_CONNECTOR, RULE_ID,
              "Process boundary for " + equipment.getName(),
              "Represent the unit in topology and require its process connections to be resolved")
          .attribute("boundaryDirection", "UNRESOLVED").attribute("equipmentType", equipment.getClass().getSimpleName()));
    }
    connectFlowPath(result);
    return result;
  }

  private static void addBoundaries(List<PidElement> result, PidDesignContext context,
      ProcessEquipmentInterface equipment, List<StreamInterface> streams, String direction) {
    if (streams == null) {
      return;
    }
    int index = 1;
    for (StreamInterface stream : streams) {
      if (stream != null) {
        result.add(PidRuleSupport
            .element(context, equipment, "NZ", direction + "-NOZZLE-" + index, PidElementType.NOZZLE, RULE_ID,
                equipment.getName() + " " + direction.toLowerCase() + " process connection",
                "Preserve the simulated process topology as an explicit HAZOP node boundary")
            .line(stream.getName()).attribute("boundaryDirection", direction)
            .attribute("streamName", stream.getName()).attribute("equipmentType", equipment.getClass().getSimpleName()));
        index++;
      }
    }
  }

  private static void connectFlowPath(List<PidElement> elements) {
    for (PidElement source : elements) {
      if ("INLET".equals(source.getAttributes().get("boundaryDirection"))) {
        for (PidElement target : elements) {
          if ("OUTLET".equals(target.getAttributes().get("boundaryDirection"))) {
            source.connect(target.getId());
          }
        }
      }
    }
  }
}
