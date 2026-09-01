package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import neqsim.process.ProcessElementInterface;
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.MercuryRemovalBed;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.energy.CommittedEnergyGenerator;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.membrane.MembraneSeparator;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.network.PipeFlowNetwork;
import neqsim.process.equipment.network.WellFlowlineNetwork;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.MultiphasePipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.reactor.IronSulfideOxidationSource;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.tank.VesselDepressurization;

/** CI inventory gate for built-in implementations of the standard transient boundary. */
public class DynamicCapabilityBuiltInInventoryTest extends neqsim.NeqSimTest {
  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");
  private static final Path PROCESS_SOURCE_ROOT = SOURCE_ROOT.resolve(Paths.get("neqsim", "process"));
  private static final Pattern STANDARD_TRANSIENT_OVERRIDE = Pattern.compile(
      "\\bvoid\\s+runTransient\\s*\\(\\s*(?:final\\s+)?double\\s+\\w+\\s*,\\s*(?:final\\s+)?(?:java\\.util\\.)?UUID\\s+\\w+");

  /** Every built-in ProcessElement transient override must be mapped or cite an existing explicit ADR. */
  @Test
  public void everyBuiltInTransientOverrideIsMappedOrHasAdr() throws Exception {
    assertTrue(Files.isDirectory(PROCESS_SOURCE_ROOT), "production process source tree is missing");

    List<Path> sources;
    try (Stream<Path> paths = Files.walk(PROCESS_SOURCE_ROOT)) {
      sources = paths.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
    }
    Collections.sort(sources);

    List<String> missingAudits = new ArrayList<String>();
    for (Path sourcePath : sources) {
      String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
      if (!STANDARD_TRANSIENT_OVERRIDE.matcher(source).find()) {
        continue;
      }

      Class<?> type = Class.forName(toClassName(sourcePath), false,
          DynamicCapabilityBuiltInInventoryTest.class.getClassLoader());
      if (!ProcessElementInterface.class.isAssignableFrom(type)) {
        continue;
      }

      if (DynamicCapabilityResolver.resolveAuditedBuiltInClass(type) != null) {
        continue;
      }

      String adr = DynamicCapabilityResolver.getUnclassifiedBuiltInAdr(type);
      if (adr == null || adr.trim().isEmpty()) {
        missingAudits.add(type.getName() + " has no capability mapping or ADR");
        continue;
      }

      Path adrPath = Paths.get(adr);
      if (adrPath.isAbsolute() || !adr.endsWith(".md") || !Files.isRegularFile(adrPath)) {
        missingAudits.add(type.getName() + " cites missing or invalid ADR " + adr);
      }
    }

    assertTrue(missingAudits.isEmpty(),
        "Built-in transient capability inventory is incomplete: " + String.join("; ", missingAudits));
  }

  /** WS6 classifications follow the state owned by each audited implementation. */
  @Test
  public void ws6BuiltInDeclarationsHaveExpectedStateOwnership() {
    assertCapability(DynamicCapability.ALGEBRAIC, Heater.class, Mixer.class, Splitter.class, MembraneSeparator.class,
        AdiabaticPipe.class);
    assertCapability(DynamicCapability.DYNAMIC_LUMPED, Filter.class, CommittedEnergyGenerator.class,
        VesselDepressurization.class, Electrolyzer.class);
    assertCapability(DynamicCapability.DYNAMIC_DISTRIBUTED, DistillationColumn.class, AdsorptionBed.class,
        MercuryRemovalBed.class, Pipeline.class, MultiphasePipe.class, PipeBeggsAndBrills.class, PipeFlowNetwork.class,
        WellFlowlineNetwork.class);
    assertCapability(DynamicCapability.BOUNDARY_DYNAMIC, IronSulfideOxidationSource.class);
  }

  private static String toClassName(Path sourcePath) {
    String relative = SOURCE_ROOT.relativize(sourcePath).toString();
    return relative.substring(0, relative.length() - ".java".length()).replace(File.separatorChar, '.');
  }

  private static void assertCapability(DynamicCapability expected, Class<?>... types) {
    for (Class<?> type : types) {
      assertEquals(expected, DynamicCapabilityResolver.resolveAuditedBuiltInClass(type), type.getName());
    }
  }
}
