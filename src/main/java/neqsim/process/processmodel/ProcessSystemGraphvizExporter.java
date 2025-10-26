package neqsim.process.processmodel;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Helper responsible for exporting {@link ProcessSystem} connectivity to Graphviz.
 */
public class ProcessSystemGraphvizExporter {
  private static final Logger logger = LogManager.getLogger(ProcessSystemGraphvizExporter.class);

  private static final String[] OUTLET_KEYWORDS = {"out", "product", "split", "mixed", "export",
      "from", "vent", "discharge", "residue", "bottom", "top", "vapor", "vapour", "to"};
  private static final String[] INLET_KEYWORDS = {"inlet", "feed", "inflow", "suction", "source",
      "supply", "import", "makeup", "recycle"};
  private static final int MAX_INDEXED_STREAMS = 16;

  private enum StreamRole {
    INLET,
    OUTLET,
    UNKNOWN
  }

  private static final class StreamReference {
    private final ProcessEquipmentInterface unit;
    private final Object stream;
    private StreamRole role;
    private final String descriptor;
    private final String streamName;

    private StreamReference(ProcessEquipmentInterface unit, Object stream, StreamRole role,
        String descriptor) {
      this.unit = unit;
      this.stream = stream;
      this.role = role;
      this.descriptor = descriptor;
      this.streamName = stream instanceof ProcessEquipmentInterface
          ? ((ProcessEquipmentInterface) stream).getName()
          : null;
    }
  }

  private static final class StreamReferences {
    private final Map<Object, StreamReference> byStream = new IdentityHashMap<>();
    private final List<StreamReference> ordered = new ArrayList<>();

    private StreamReference get(Object stream) {
      return byStream.get(stream);
    }

    private void add(StreamReference reference) {
      byStream.put(reference.stream, reference);
      ordered.add(reference);
    }

    private Iterable<StreamReference> orderedValues() {
      return ordered;
    }
  }

  public static final class GraphvizExportOptions {
    /** Placement of the optional stream property table. */
    public enum TablePlacement {
      ABOVE,
      BELOW
    }

    private final boolean includeStreamTemperatures;
    private final boolean includeStreamPressures;
    private final boolean includeStreamFlowRates;
    private final boolean includeStreamPropertyTable;
    private final boolean includeTableTemperatures;
    private final boolean includeTablePressures;
    private final boolean includeTableFlowRates;
    private final TablePlacement tablePlacement;
    private final String temperatureUnit;
    private final String pressureUnit;
    private final String flowRateUnit;

    private GraphvizExportOptions(Builder builder) {
      this.includeStreamTemperatures = builder.includeStreamTemperatures;
      this.includeStreamPressures = builder.includeStreamPressures;
      this.includeStreamFlowRates = builder.includeStreamFlowRates;
      this.includeStreamPropertyTable = builder.includeStreamPropertyTable;
      this.includeTableTemperatures = builder.includeTableTemperatures;
      this.includeTablePressures = builder.includeTablePressures;
      this.includeTableFlowRates = builder.includeTableFlowRates;
      this.tablePlacement = builder.tablePlacement;
      this.temperatureUnit = builder.temperatureUnit;
      this.pressureUnit = builder.pressureUnit;
      this.flowRateUnit = builder.flowRateUnit;
    }

    /** Create a builder for the export options. */
    public static Builder builder() {
      return new Builder();
    }

    /** Default export options. */
    public static GraphvizExportOptions defaults() {
      return builder().build();
    }

    public boolean includeStreamTemperatures() {
      return includeStreamTemperatures;
    }

    public boolean includeStreamPressures() {
      return includeStreamPressures;
    }

    public boolean includeStreamFlowRates() {
      return includeStreamFlowRates;
    }

    public boolean includeStreamPropertyTable() {
      return includeStreamPropertyTable;
    }

    public boolean includeTableTemperatures() {
      return includeTableTemperatures;
    }

    public boolean includeTablePressures() {
      return includeTablePressures;
    }

    public boolean includeTableFlowRates() {
      return includeTableFlowRates;
    }

    public TablePlacement getTablePlacement() {
      return tablePlacement;
    }

    public String getTemperatureUnit() {
      return temperatureUnit;
    }

    public String getPressureUnit() {
      return pressureUnit;
    }

    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /** Builder for {@link GraphvizExportOptions}. */
    public static final class Builder {
      private boolean includeStreamTemperatures = false;
      private boolean includeStreamPressures = false;
      private boolean includeStreamFlowRates = false;
      private boolean includeStreamPropertyTable = false;
      private boolean includeTableTemperatures = true;
      private boolean includeTablePressures = true;
      private boolean includeTableFlowRates = true;
      private TablePlacement tablePlacement = TablePlacement.ABOVE;
      private String temperatureUnit = "C";
      private String pressureUnit = "bara";
      private String flowRateUnit = "kg/hr";

      private Builder() {}

      public Builder includeStreamTemperatures(boolean value) {
        this.includeStreamTemperatures = value;
        return this;
      }

      public Builder includeStreamPressures(boolean value) {
        this.includeStreamPressures = value;
        return this;
      }

      public Builder includeStreamFlowRates(boolean value) {
        this.includeStreamFlowRates = value;
        return this;
      }

      public Builder includeStreamPropertyTable(boolean value) {
        this.includeStreamPropertyTable = value;
        return this;
      }

      public Builder includeTableTemperatures(boolean value) {
        this.includeTableTemperatures = value;
        return this;
      }

      public Builder includeTablePressures(boolean value) {
        this.includeTablePressures = value;
        return this;
      }

      public Builder includeTableFlowRates(boolean value) {
        this.includeTableFlowRates = value;
        return this;
      }

      public Builder tablePlacement(TablePlacement placement) {
        if (placement != null) {
          this.tablePlacement = placement;
        }
        return this;
      }

      public Builder temperatureUnit(String unit) {
        if (unit != null) {
          this.temperatureUnit = unit;
        }
        return this;
      }

      public Builder pressureUnit(String unit) {
        if (unit != null) {
          this.pressureUnit = unit;
        }
        return this;
      }

      public Builder flowRateUnit(String unit) {
        if (unit != null) {
          this.flowRateUnit = unit;
        }
        return this;
      }

      public GraphvizExportOptions build() {
        return new GraphvizExportOptions(this);
      }
    }
  }

  public void export(ProcessSystem system, String filename) {
    export(system, filename, GraphvizExportOptions.defaults());
  }

  public void export(ProcessSystem system, String filename, GraphvizExportOptions options) {
    GraphvizExportOptions effectiveOptions =
        options == null ? GraphvizExportOptions.defaults() : options;

    try (PrintWriter writer = new PrintWriter(filename)) {
      export(system, writer, effectiveOptions);
    } catch (Exception e) {
      logger.error("Error exporting to Graphviz", e);
    }
  }

  private void export(ProcessSystem system, PrintWriter writer, GraphvizExportOptions options) {
    writer.println("digraph process {");

    List<List<StreamReference>> streamUsage = collectStreamUsage(system);
    Set<String> nodeLines = new LinkedHashSet<>();
    Set<String> edgeLines = new LinkedHashSet<>();

    addStreamPropertyTable(writer, streamUsage, options);

    for (ProcessEquipmentInterface unit : system.getUnitOperations()) {
      nodeLines.add("  \"" + escapeGraphviz(unit.getName()) + "\";");
    }

    for (List<StreamReference> references : streamUsage) {
      if (references.size() < 2) {
        continue;
      }

      List<StreamReference> sources = new ArrayList<>();
      List<StreamReference> sinks = new ArrayList<>();
      List<StreamReference> unknowns = new ArrayList<>();

      for (StreamReference reference : references) {
        switch (reference.role) {
          case OUTLET:
            sources.add(reference);
            break;
          case INLET:
            sinks.add(reference);
            break;
          default:
            unknowns.add(reference);
            break;
        }
      }

      if (sources.isEmpty() && sinks.isEmpty() && !unknowns.isEmpty()) {
        sources.add(unknowns.get(0));
        for (int i = 1; i < unknowns.size(); i++) {
          sinks.add(unknowns.get(i));
        }
        unknowns.clear();
      } else {
        if (sources.isEmpty() && !unknowns.isEmpty()) {
          sources.addAll(unknowns);
          unknowns.clear();
        }
        if (sinks.isEmpty() && !unknowns.isEmpty()) {
          sinks.addAll(unknowns);
          unknowns.clear();
        } else if (!unknowns.isEmpty()) {
          sinks.addAll(unknowns);
          unknowns.clear();
        }
      }

      if (sinks.isEmpty()) {
        StreamReference promoted = promoteStreamUnitAsSink(sources);
        if (promoted != null) {
          sources.remove(promoted);
          sinks.add(promoted);
        }
      }

      List<StreamReference> streamUnits = new ArrayList<>();
      extractStreamUnitReferences(sources, streamUnits);
      extractStreamUnitReferences(sinks, streamUnits);

      if (!streamUnits.isEmpty()) {
        for (StreamReference source : sources) {
          for (StreamReference streamUnit : streamUnits) {
            addEdge(edgeLines, source, streamUnit, options);
          }
        }

        for (StreamReference streamUnit : streamUnits) {
          for (StreamReference sink : sinks) {
            addEdge(edgeLines, streamUnit, sink, options);
          }
        }

        continue;
      }

      for (StreamReference source : sources) {
        for (StreamReference sink : sinks) {
          addEdge(edgeLines, source, sink, options);
        }
      }
    }

    for (String nodeLine : nodeLines) {
      writer.println(nodeLine);
    }
    for (String edgeLine : edgeLines) {
      writer.println(edgeLine);
    }

    writer.println("}");
  }

  private static String selectStreamLabel(StreamReference source, StreamReference sink) {
    if (source.streamName != null && !source.streamName.isEmpty()) {
      return source.streamName;
    }
    if (sink.streamName != null && !sink.streamName.isEmpty()) {
      return sink.streamName;
    }
    if (source.descriptor != null && !source.descriptor.isEmpty()) {
      return source.descriptor;
    }
    if (sink.descriptor != null && !sink.descriptor.isEmpty()) {
      return sink.descriptor;
    }
    return null;
  }

  private List<List<StreamReference>> collectStreamUsage(ProcessSystem system) {
    Map<Object, List<StreamReference>> streamUsage = new IdentityHashMap<>();
    List<List<StreamReference>> orderedUsage = new ArrayList<>();

    for (ProcessEquipmentInterface unit : system.getUnitOperations()) {
      StreamReferences unitReferences = collectStreamReferences(unit);
      for (StreamReference reference : unitReferences.orderedValues()) {
        List<StreamReference> references = streamUsage.get(reference.stream);
        if (references == null) {
          references = new ArrayList<>();
          streamUsage.put(reference.stream, references);
          orderedUsage.add(references);
        }
        references.add(reference);
      }
    }

    return orderedUsage;
  }

  private StreamReferences collectStreamReferences(ProcessEquipmentInterface unit) {
    StreamReferences references = new StreamReferences();

    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    collectStreamReferencesFromFields(unit, references, unit, null, visited);

    for (Method method : unit.getClass().getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }

      if (method.getParameterCount() == 0) {
        Class<?> returnType = method.getReturnType();

        if (StreamInterface.class.isAssignableFrom(returnType)) {
          addStreamReference(unit, references, method, invokeMethod(unit, method), null);
        } else if (returnType.isArray()
            && StreamInterface.class.isAssignableFrom(returnType.getComponentType())) {
          Object result = invokeMethod(unit, method);
          if (result != null) {
            int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
              Object element = Array.get(result, i);
              addStreamReference(unit, references, method, element, "[" + i + "]");
            }
          }
        } else if (Collection.class.isAssignableFrom(returnType)) {
          Object result = invokeMethod(unit, method);
          if (result instanceof Collection<?>) {
            int index = 0;
            for (Object element : (Collection<?>) result) {
              if (element instanceof StreamInterface) {
                addStreamReference(unit, references, method, element, "[" + index + "]");
              }
              index++;
            }
          }
        }
      } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == int.class
          && StreamInterface.class.isAssignableFrom(method.getReturnType())) {
        for (int i = 0; i < MAX_INDEXED_STREAMS; i++) {
          try {
            Object result = method.invoke(unit, i);
            if (result == null) {
              if (i == 0) {
                continue;
              }
              break;
            }
            addStreamReference(unit, references, method, result, "[" + i + "]");
          } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IndexOutOfBoundsException
                || cause instanceof ArrayIndexOutOfBoundsException
                || cause instanceof IllegalArgumentException || cause instanceof NullPointerException) {
              break;
            }
          } catch (IllegalAccessException ex) {
            break;
          }
        }
      }
    }

    return references;
  }

  private void collectStreamReferencesFromFields(ProcessEquipmentInterface unit,
      StreamReferences references, Object target, String descriptorPrefix,
      Set<Object> visited) {
    if (target == null || visited.contains(target)) {
      return;
    }

    visited.add(target);

    Class<?> type = target.getClass();
    while (type != null && type != Object.class) {
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        try {
          if (!field.canAccess(target)) {
            field.setAccessible(true);
          }
        } catch (SecurityException ex) {
          logger.debug("Skipping field {} due to inaccessible module or security restrictions", field,
              ex);
          continue;
        } catch (RuntimeException ex) {
          if (isInaccessibleModuleAccess(ex)) {
            logger.debug("Skipping field {} due to inaccessible module or security restrictions", field,
                ex);
            continue;
          }
          throw ex;
        }

        Object value;
        try {
          value = field.get(target);
        } catch (IllegalAccessException ex) {
          continue;
        }

        if (value == null) {
          continue;
        }

        String descriptor = descriptorPrefix == null ? field.getName()
            : descriptorPrefix + "." + field.getName();

        if (value instanceof StreamInterface) {
          addStreamReference(unit, references, value, inferStreamRole(descriptor), descriptor);
        } else if (value.getClass().isArray()
            && StreamInterface.class.isAssignableFrom(value.getClass().getComponentType())) {
          int length = Array.getLength(value);
          for (int i = 0; i < length; i++) {
            Object element = Array.get(value, i);
            if (element instanceof StreamInterface) {
              addStreamReference(unit, references, element, inferStreamRole(descriptor),
                  descriptor + "[" + i + "]");
            }
          }
        } else if (value instanceof Collection<?>) {
          int index = 0;
          for (Object element : (Collection<?>) value) {
            if (element instanceof StreamInterface) {
              addStreamReference(unit, references, element, inferStreamRole(descriptor),
                  descriptor + "[" + index + "]");
            }
            index++;
          }
        } else if (value instanceof Map<?, ?>) {
          int index = 0;
          for (Object element : ((Map<?, ?>) value).values()) {
            if (element instanceof StreamInterface) {
              addStreamReference(unit, references, element, inferStreamRole(descriptor),
                  descriptor + "[" + index + "]");
            }
            index++;
          }
        } else if (value.getClass().getPackage() != null
            && value.getClass().getPackage().getName().startsWith("neqsim.process")) {
          collectStreamReferencesFromFields(unit, references, value, descriptor, visited);
        }
      }
      type = type.getSuperclass();
    }
  }

  private void addStreamReference(ProcessEquipmentInterface unit,
      StreamReferences references, Method method, Object stream, String suffix) {
    if (!(stream instanceof StreamInterface)) {
      return;
    }

    String descriptor = method.getName();
    if (suffix != null) {
      descriptor += suffix;
    }

    StreamRole role = inferStreamRole(unit, method, descriptor);
    addStreamReference(unit, references, stream, role, descriptor);
  }

  private void addStreamReference(ProcessEquipmentInterface unit,
      StreamReferences references, Object stream, StreamRole role, String descriptor) {
    if (!(stream instanceof StreamInterface)) {
      return;
    }

    StreamReference existing = references.get(stream);
    if (existing != null) {
      if (existing.role == StreamRole.UNKNOWN && role != StreamRole.UNKNOWN) {
        existing.role = role;
      }
      return;
    }

    references.add(new StreamReference(unit, stream, role, descriptor));
  }

  private Object invokeMethod(ProcessEquipmentInterface unit, Method method) {
    try {
      return method.invoke(unit);
    } catch (IllegalAccessException | InvocationTargetException ex) {
      return null;
    }
  }

  private StreamRole inferStreamRole(ProcessEquipmentInterface unit, Method method,
      String descriptor) {
    String baseName = getAccessorBaseName(method);
    String lowerCaseName = baseName.toLowerCase(Locale.ROOT);

    boolean hasOutletKeyword = containsKeyword(lowerCaseName, OUTLET_KEYWORDS);
    boolean hasInletKeyword = containsKeyword(lowerCaseName, INLET_KEYWORDS);

    if (hasOutletKeyword && !hasInletKeyword) {
      return StreamRole.OUTLET;
    }
    if (hasInletKeyword && !hasOutletKeyword) {
      return StreamRole.INLET;
    }

    Method setter = findStreamSetter(unit.getClass(), "set" + baseName);
    if (setter != null) {
      return StreamRole.INLET;
    }

    if (hasOutletKeyword) {
      return StreamRole.OUTLET;
    }
    if (hasInletKeyword) {
      return StreamRole.INLET;
    }
    StreamRole descriptorRole = inferStreamRole(descriptor);
    if (descriptorRole != StreamRole.UNKNOWN) {
      return descriptorRole;
    }
    return StreamRole.UNKNOWN;
  }

  private StreamRole inferStreamRole(String descriptor) {
    if (descriptor == null) {
      return StreamRole.UNKNOWN;
    }

    String lowerCaseName = descriptor.toLowerCase(Locale.ROOT);
    boolean hasOutletKeyword = containsKeyword(lowerCaseName, OUTLET_KEYWORDS);
    boolean hasInletKeyword = containsKeyword(lowerCaseName, INLET_KEYWORDS);

    if (hasOutletKeyword && !hasInletKeyword) {
      return StreamRole.OUTLET;
    }
    if (hasInletKeyword && !hasOutletKeyword) {
      return StreamRole.INLET;
    }
    if (hasOutletKeyword) {
      return StreamRole.OUTLET;
    }
    if (hasInletKeyword) {
      return StreamRole.INLET;
    }
    return StreamRole.UNKNOWN;
  }

  private StreamReference promoteStreamUnitAsSink(List<StreamReference> sources) {
    for (StreamReference reference : sources) {
      if (reference.unit instanceof StreamInterface) {
        reference.role = StreamRole.INLET;
        return reference;
      }
    }
    return null;
  }

  private Method findStreamSetter(Class<?> type, String setterName) {
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(setterName)) {
        continue;
      }
      if (method.getParameterCount() == 1
          && StreamInterface.class.isAssignableFrom(method.getParameterTypes()[0])) {
        return method;
      }
    }
    return null;
  }

  private String getAccessorBaseName(Method method) {
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return name.substring(3);
    }
    return name;
  }

  private boolean containsKeyword(String value, String[] keywords) {
    for (String keyword : keywords) {
      if (value.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInaccessibleModuleAccess(RuntimeException exception) {
    Throwable current = exception;
    while (current != null) {
      if ("java.lang.reflect.InaccessibleObjectException".equals(current.getClass().getName())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private String escapeGraphviz(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private void extractStreamUnitReferences(List<StreamReference> references,
      List<StreamReference> streamUnits) {
    Iterator<StreamReference> iterator = references.iterator();
    while (iterator.hasNext()) {
      StreamReference reference = iterator.next();
      if (isSelfStreamReference(reference)) {
        iterator.remove();
        if (!streamUnits.contains(reference)) {
          streamUnits.add(reference);
        }
      }
    }
  }

  private boolean isSelfStreamReference(StreamReference reference) {
    return reference != null && reference.unit == reference.stream
        && reference.unit instanceof StreamInterface;
  }

  private void addEdge(Set<String> edgeLines, StreamReference source, StreamReference sink,
      GraphvizExportOptions options) {
    if (source == null || sink == null || source.unit == sink.unit) {
      return;
    }

    String label = buildStreamLabel(source, sink, options);
    StringBuilder edgeBuilder = new StringBuilder();
    edgeBuilder.append("  \"").append(escapeGraphviz(source.unit.getName())).append("\"")
        .append(" -> ")
        .append("\"").append(escapeGraphviz(sink.unit.getName())).append("\"");
    if (label != null && !label.isEmpty()) {
      edgeBuilder.append(" [label=\"").append(escapeGraphviz(label)).append("\"]");
    }
    edgeBuilder.append(";");
    edgeLines.add(edgeBuilder.toString());
  }

  private String buildStreamLabel(StreamReference source, StreamReference sink,
      GraphvizExportOptions options) {
    String baseLabel = selectStreamLabel(source, sink);
    StreamInterface stream = resolveStreamInterface(source, sink);

    if (stream == null) {
      return baseLabel;
    }

    List<String> labelLines = new ArrayList<>();
    if (baseLabel != null && !baseLabel.isEmpty()) {
      labelLines.add(baseLabel);
    }

    if (options.includeStreamTemperatures()) {
      Double temperature = safeGetTemperature(stream, options.getTemperatureUnit());
      String formatted = formatProperty("T", temperature, options.getTemperatureUnit());
      if (formatted != null) {
        labelLines.add(formatted);
      }
    }

    if (options.includeStreamPressures()) {
      Double pressure = safeGetPressure(stream, options.getPressureUnit());
      String formatted = formatProperty("P", pressure, options.getPressureUnit());
      if (formatted != null) {
        labelLines.add(formatted);
      }
    }

    if (options.includeStreamFlowRates()) {
      Double flowRate = safeGetFlowRate(stream, options.getFlowRateUnit());
      String formatted = formatProperty("F", flowRate, options.getFlowRateUnit());
      if (formatted != null) {
        labelLines.add(formatted);
      }
    }

    if (labelLines.isEmpty()) {
      return null;
    }
    return String.join("\\n", labelLines);
  }

  private StreamInterface resolveStreamInterface(StreamReference... references) {
    for (StreamReference reference : references) {
      if (reference != null && reference.stream instanceof StreamInterface) {
        return (StreamInterface) reference.stream;
      }
    }
    return null;
  }

  private Double safeGetTemperature(StreamInterface stream, String unit) {
    try {
      if (unit != null && !unit.isEmpty()) {
        return stream.getTemperature(unit);
      }
      return stream.getTemperature();
    } catch (Exception ex) {
      return null;
    }
  }

  private Double safeGetPressure(StreamInterface stream, String unit) {
    try {
      if (unit != null && !unit.isEmpty()) {
        return stream.getPressure(unit);
      }
      return stream.getPressure();
    } catch (Exception ex) {
      return null;
    }
  }

  private Double safeGetFlowRate(StreamInterface stream, String unit) {
    try {
      if (unit != null && !unit.isEmpty()) {
        return stream.getFlowRate(unit);
      }
      return stream.getFlowRate("kg/hr");
    } catch (Exception ex) {
      return null;
    }
  }

  private String formatProperty(String label, Double value, String unit) {
    if (value == null || !Double.isFinite(value)) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    builder.append(label).append("=")
        .append(String.format(Locale.ROOT, "%.2f", value));
    if (unit != null && !unit.isEmpty()) {
      builder.append(" ").append(unit);
    }
    return builder.toString();
  }

  private void addStreamPropertyTable(PrintWriter writer, List<List<StreamReference>> streamUsage,
      GraphvizExportOptions options) {
    if (!options.includeStreamPropertyTable()) {
      return;
    }

    String tableLabel = buildStreamPropertyTable(streamUsage, options);
    if (tableLabel.isEmpty()) {
      return;
    }

    String labelLocation =
        options.getTablePlacement() == GraphvizExportOptions.TablePlacement.BELOW ? "b" : "t";
    writer.println("  graph [label=<" + tableLabel + ">, labelloc=\"" + labelLocation + "\"];");
  }

  private String buildStreamPropertyTable(List<List<StreamReference>> streamUsage,
      GraphvizExportOptions options) {
    boolean includeTemperature = options.includeTableTemperatures();
    boolean includePressure = options.includeTablePressures();
    boolean includeFlowRate = options.includeTableFlowRates();

    if (!includeTemperature && !includePressure && !includeFlowRate) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    builder.append("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">");
    builder.append("<TR><TD><B>Stream</B></TD>");
    if (includeTemperature) {
      builder.append("<TD><B>Temperature (" + escapeHtml(options.getTemperatureUnit()) + ")</B></TD>");
    }
    if (includePressure) {
      builder.append("<TD><B>Pressure (" + escapeHtml(options.getPressureUnit()) + ")</B></TD>");
    }
    if (includeFlowRate) {
      builder.append("<TD><B>Flow Rate (" + escapeHtml(options.getFlowRateUnit()) + ")</B></TD>");
    }
    builder.append("</TR>");

    for (List<StreamReference> references : streamUsage) {
      if (references.isEmpty()) {
        continue;
      }

      StreamReference first = references.get(0);
      if (!(first.stream instanceof StreamInterface)) {
        continue;
      }

      StreamInterface stream = (StreamInterface) first.stream;
      String streamName = stream.getName();
      if (streamName == null || streamName.isEmpty()) {
        streamName = first.descriptor != null ? first.descriptor : "Unnamed Stream";
      }

      builder.append("<TR>");
      builder.append("<TD>").append(escapeHtml(streamName)).append("</TD>");

      if (includeTemperature) {
        Double value = safeGetTemperature(stream, options.getTemperatureUnit());
        builder.append("<TD>").append(formatTableCell(value, options.getTemperatureUnit()))
            .append("</TD>");
      }

      if (includePressure) {
        Double value = safeGetPressure(stream, options.getPressureUnit());
        builder.append("<TD>").append(formatTableCell(value, options.getPressureUnit()))
            .append("</TD>");
      }

      if (includeFlowRate) {
        Double value = safeGetFlowRate(stream, options.getFlowRateUnit());
        builder.append("<TD>").append(formatTableCell(value, options.getFlowRateUnit()))
            .append("</TD>");
      }

      builder.append("</TR>");
    }

    builder.append("</TABLE>");
    return builder.toString();
  }

  private String formatTableCell(Double value, String unit) {
    if (value == null || !Double.isFinite(value)) {
      return "-";
    }

    String formatted = String.format(Locale.ROOT, "%.2f", value);
    if (unit != null && !unit.isEmpty()) {
      formatted += " " + unit;
    }
    return escapeHtml(formatted);
  }

  private String escapeHtml(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    StringBuilder builder = new StringBuilder(value.length());
    for (char ch : value.toCharArray()) {
      switch (ch) {
        case '&':
          builder.append("&amp;");
          break;
        case '<':
          builder.append("&lt;");
          break;
        case '>':
          builder.append("&gt;");
          break;
        case '\"':
          builder.append("&quot;");
          break;
        case '\'':
          builder.append("&#39;");
          break;
        default:
          builder.append(ch);
          break;
      }
    }
    return builder.toString();
  }
}
