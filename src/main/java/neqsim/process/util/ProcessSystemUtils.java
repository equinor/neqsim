package neqsim.process.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Graph;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

public class ProcessSystemUtils {

  private static final Logger logger = LogManager.getLogger(ProcessSystemUtils.class);

  public static void createFromDescription(ProcessSystem processSystem, String filePath) {
    try {
      ObjectMapper mapper = filePath.endsWith(".yaml") || filePath.endsWith(".yml")
          ? new ObjectMapper(new YAMLFactory())
          : new ObjectMapper();

      Map<String, Object> processDescription = mapper.readValue(new File(filePath), Map.class);

      // Parse units
      List<Map<String, String>> units = (List<Map<String, String>>) processDescription.get("units");
      for (Map<String, String> unit : units) {
        String name = unit.get("name");
        ProcessSystem.EquipmentEnum type = ProcessSystem.EquipmentEnum.valueOf(unit.get("type"));
        processSystem.addUnit(name, type);
      }

      // Parse connections
      List<Map<String, String>> connections =
          (List<Map<String, String>>) processDescription.get("connections");
      for (Map<String, String> connection : connections) {
        ProcessEquipmentInterface source = processSystem.getUnit(connection.get("source"));
        ProcessEquipmentInterface target = processSystem.getUnit(connection.get("target"));
        processSystem.connectStreams(source, target);
      }
    } catch (Exception e) {
      logger.error("Error creating process system from description: " + e.getMessage(), e);
    }
  }

  public static void exportProcessFlowDiagram(ProcessSystem processSystem, String outputFilePath) {
    try {
      Graph graph = Graph.graph("processFlow").directed();

      // Create nodes for each unit
      Map<String, Node> nodes = new HashMap<>();
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        nodes.put(unit.getName(), Node.node(unit.getName()));
      }

      // Create edges for connections
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit instanceof neqsim.process.equipment.stream.Stream) {
          ProcessEquipmentInterface target =
              ((neqsim.process.equipment.stream.Stream) unit).getTarget();
          if (target != null) {
            graph =
                graph.with(nodes.get(unit.getName()).link(Node.to(nodes.get(target.getName()))));
          }
        }
      }

      // Render graph to file
      Graphviz.fromGraph(graph).render(Graphviz.Format.PNG).toFile(new File(outputFilePath));
      logger.info("Process flow diagram exported to: " + outputFilePath);
    } catch (Exception e) {
      logger.error("Error exporting process flow diagram: " + e.getMessage(), e);
    }
  }
}
