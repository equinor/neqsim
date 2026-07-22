package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.ReliefDeviceDesignInput;
import neqsim.process.engineering.ReliefScenarioBasis;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.ShutdownSequence;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignConditions;

/** Writes review-governed equipment, line, instrument, safety and evidence registers. */
final class EngineeringRegisterExporter {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private EngineeringRegisterExporter() {
  }

  static Map<String, Path> export(EngineeringProject project, Path directory) throws IOException {
    Files.createDirectories(directory);
    Map<String, Path> files = new LinkedHashMap<String, Path>();
    put(files, "equipmentRegister", directory.resolve("equipment-register.csv"), equipmentRegister(project));
    put(files, "lineList", directory.resolve("line-list.csv"), lineList(project));
    put(files, "instrumentIndex", directory.resolve("instrument-index.csv"), instrumentIndex(project));
    put(files, "valveList", directory.resolve("valve-list.csv"), valveList(project));
    put(files, "sifRegister", directory.resolve("sif-register.csv"), sifRegister(project));
    put(files, "shutdownRegister", directory.resolve("shutdown-register.csv"), shutdownRegister(project));
    put(files, "reliefRegister", directory.resolve("relief-register.csv"), reliefRegister(project));
    put(files, "evidenceRegister", directory.resolve("evidence-register.csv"), evidenceRegister(project));

    Path narratives = directory.resolve("control-and-safeguarding-narratives.json");
    Files.write(narratives, GSON.toJson(controlNarratives(project)).getBytes(StandardCharsets.UTF_8));
    files.put("controlNarratives", narratives);
    return files;
  }

  private static void put(Map<String, Path> files, String key, Path path, List<List<String>> rows) throws IOException {
    StringBuilder csv = new StringBuilder();
    for (List<String> row : rows) {
      for (int i = 0; i < row.size(); i++) {
        if (i > 0) {
          csv.append(',');
        }
        csv.append(escape(row.get(i)));
      }
      csv.append('\n');
    }
    Files.write(path, csv.toString().getBytes(StandardCharsets.UTF_8));
    files.put(key, path);
  }

  private static List<List<String>> equipmentRegister(EngineeringProject project) {
    List<List<String>> rows = rows("equipmentTag", "equipmentClass", "operatingPressureBara", "operatingTemperatureC",
        "designPressureBara", "maximumDesignTemperatureC", "material", "approvalStatus");
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      DesignConditions design = unit.getDesignConditions();
      rows.add(values(unit.getName(), unit.getClass().getSimpleName(), safePressure(unit), safeTemperature(unit),
          design != null && design.isDesignPressureSet() ? number(design.getDesignPressure()) : "",
          design != null && design.isMaxDesignTemperatureSet() ? number(design.getMaxDesignTemperature()) : "",
          design != null && design.isConstructionMaterialSet() ? design.getConstructionMaterial() : "",
          "REVIEW_REQUIRED"));
    }
    return rows;
  }

  private static List<List<String>> lineList(EngineeringProject project) {
    List<List<String>> rows = rows("lineTag", "equipmentTag", "nominalPipeSize", "schedule", "materialGrade",
        "pipingClass", "outerDiameterM", "nominalWallThicknessM", "corrosionAllowanceM", "designPressureBara",
        "designTemperatureC", "evidenceReferences", "approvalStatus");
    for (LineDesignInput input : project.getLineDesignInputs()) {
      rows.add(values(input.getLineTag(), input.getEquipmentTag(), input.getNominalPipeSize(), input.getSchedule(),
          input.getMaterialGrade(), input.getPipingClass(), number(input.getOuterDiameterM()),
          number(input.getNominalWallThicknessM()), number(input.getCorrosionAllowanceM()),
          number(input.getDesignPressureBara()), number(input.getDesignTemperatureC()),
          join(input.getEvidenceReferences()), "REVIEW_REQUIRED"));
    }
    return rows;
  }

  private static List<List<String>> instrumentIndex(EngineeringProject project) {
    List<List<String>> rows = rows("requirementId", "equipmentTag", "functionType", "title", "silTarget",
        "standardReferences", "origin", "approvalStatus");
    for (EngineeringRequirement requirement : project.getRequirements()) {
      rows.add(values(requirement.getId(), requirement.getEquipmentTag(), requirement.getType().name(),
          requirement.getTitle(), requirement.getSilTarget(), join(requirement.getStandardReferences()),
          requirement.getOrigin().name(), requirement.getApprovalStatus().name()));
    }
    return rows;
  }

  private static List<List<String>> valveList(EngineeringProject project) {
    List<List<String>> rows = rows("valveTag", "valveClass", "failureAction", "designPressureBara", "approvalStatus");
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
      if (unit == null || !unit.getClass().getSimpleName().contains("Valve")) {
        continue;
      }
      DesignConditions design = unit.getDesignConditions();
      rows.add(values(unit.getName(), unit.getClass().getSimpleName(),
          design != null && design.isFailureActionSet() ? design.getFailureAction().name() : "NOT_ASSIGNED",
          design != null && design.isDesignPressureSet() ? number(design.getDesignPressure()) : "", "REVIEW_REQUIRED"));
    }
    return rows;
  }

  private static List<List<String>> sifRegister(EngineeringProject project) {
    List<List<String>> rows = rows("sifTag", "requirementId", "targetSil", "achievedSil", "pfdAverage", "pfhPerHour",
        "architecturalConstraintsMet", "approvalStatus");
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      rows.add(values(design.getSifTag(), design.getRequirementId(), Integer.toString(design.getTargetSil()),
          Integer.toString(design.getAchievedSil()), number(design.calculatePfdAverage()),
          number(design.calculatePfh()), Boolean.toString(design.areArchitecturalConstraintsMet()),
          design.getApprovalStatus().name()));
    }
    return rows;
  }

  private static List<List<String>> shutdownRegister(EngineeringProject project) {
    List<List<String>> rows = rows("sequenceId", "requirementIds", "actionCount", "totalResponseTimeSeconds",
        "withinResponseTimeBudget", "dynamicVerificationVerdict", "approvalStatus");
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      String dynamicVerdict = project.getShutdownVerificationResults().containsKey(sequence.getSequenceId())
          ? project.getShutdownVerificationResults().get(sequence.getSequenceId()).getVerdict().name()
          : "NOT_RUN";
      rows.add(values(sequence.getSequenceId(), join(sequence.getRequirementIds()),
          Integer.toString(sequence.getActions().size()), number(sequence.getTotalResponseTimeSeconds()),
          Boolean.toString(sequence.isWithinResponseTimeBudget()), dynamicVerdict,
          sequence.getApprovalStatus().name()));
    }
    return rows;
  }

  private static List<List<String>> reliefRegister(EngineeringProject project) {
    List<List<String>> rows = rows("equipmentTag", "deviceTag", "requiredCauses", "selectedOrificeAreaIn2",
        "concurrencyGroup", "fireZone", "twoPhaseMethod", "evidenceReference", "missingFields", "approvalStatus");
    Map<String, ReliefScenarioBasis> bases = new LinkedHashMap<String, ReliefScenarioBasis>();
    for (ReliefScenarioBasis basis : project.getReliefScenarioBases()) {
      bases.put(basis.getEquipmentTag(), basis);
    }
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      ReliefScenarioBasis basis = bases.get(input.getEquipmentTag());
      rows.add(values(input.getEquipmentTag(), input.getDeviceTag(),
          basis == null ? "NOT_DEFINED" : joinObjects(basis.getRequiredCauses()),
          number(input.getSelectedOrificeAreaIn2()), input.getConcurrencyGroup(), input.getFireZone(),
          input.getTwoPhaseMethod(), input.getEvidenceReference(), join(input.getMissingFields()), "REVIEW_REQUIRED"));
    }
    return rows;
  }

  private static List<List<String>> evidenceRegister(EngineeringProject project) {
    List<List<String>> rows = rows("documentId", "documentType", "revision", "equipmentTags", "requirementIds",
        "missingFields", "approvalStatus");
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      rows.add(values(evidence.getDocumentId(), evidence.getDocumentType(), evidence.getRevision(),
          join(evidence.getEquipmentTags()), join(evidence.getRequirementIds()), join(evidence.getMissingFields()),
          evidence.getApprovalStatus().name()));
    }
    return rows;
  }

  private static List<Map<String, Object>> controlNarratives(EngineeringProject project) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      Map<String, Object> narrative = new LinkedHashMap<String, Object>();
      narrative.put("requirementId", requirement.getId());
      narrative.put("equipmentTag", requirement.getEquipmentTag());
      narrative.put("function", requirement.getTitle());
      narrative.put("designIntent", requirement.getRationale());
      narrative.put("standards", new ArrayList<String>(requirement.getStandardReferences()));
      narrative.put("silTarget", requirement.getSilTarget());
      narrative.put("setPoint", "NOT_ASSIGNED_UNTIL_APPROVED");
      narrative.put("approvalStatus", requirement.getApprovalStatus().name());
      result.add(narrative);
    }
    return result;
  }

  private static String safePressure(ProcessEquipmentInterface unit) {
    try {
      return number(unit.getPressure("bara"));
    } catch (Exception ex) {
      return "";
    }
  }

  private static String safeTemperature(ProcessEquipmentInterface unit) {
    try {
      return number(unit.getTemperature("C"));
    } catch (Exception ex) {
      return "";
    }
  }

  private static String number(double value) {
    return Double.isFinite(value) ? Double.toString(value) : "";
  }

  private static List<List<String>> rows(String... header) {
    List<List<String>> result = new ArrayList<List<String>>();
    result.add(values(header));
    return result;
  }

  private static List<String> values(String... values) {
    List<String> result = new ArrayList<String>();
    for (String value : values) {
      result.add(value == null ? "" : value);
    }
    return result;
  }

  private static String join(Iterable<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(value);
    }
    return result.toString();
  }

  private static String joinObjects(Iterable<?> values) {
    StringBuilder result = new StringBuilder();
    for (Object value : values) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(value);
    }
    return result.toString();
  }

  private static String escape(String value) {
    String text = value == null ? "" : value;
    if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
      return '"' + text.replace("\"", "\"\"") + '"';
    }
    return text;
  }
}
