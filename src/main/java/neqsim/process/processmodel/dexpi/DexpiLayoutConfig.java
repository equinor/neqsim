package neqsim.process.processmodel.dexpi;

/**
 * Configuration class for DEXPI layout parameters.
 *
 * <p>
 * Provides configurable spacing, colors, and sizing values for the auto-layout engine. Users can
 * create a custom configuration and pass it to the export methods to control drawing appearance.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiLayoutConfig {

  /** Horizontal spacing between equipment columns (mm in drawing space). */
  private double xSpacing = 100.0;

  /** Vertical spacing for branch lines below the main process line. */
  private double yBranchOffset = 60.0;

  /** Starting X coordinate for the first equipment column. */
  private double xStart = 80.0;

  /** Base Y coordinate for the main process line. */
  private double yBase = 150.0;

  /** Default scale factor for equipment shapes. */
  private double defaultScale = 1.0;

  /** Font name for all labels and text. */
  private String fontName = "Calibri";

  /** Font height for equipment tag name labels. */
  private double tagFontHeight = 4.5;

  /** Line weight for process piping lines. */
  private double processLineWeight = 0.5;

  /** Line weight for signal/instrument lines. */
  private double signalLineWeight = 0.2;

  /** Process line colour - red component (0-1). */
  private String lineColorR = "0.501960784";

  /** Process line colour - green component (0-1). */
  private String lineColorG = "0.501960784";

  /** Process line colour - blue component (0-1). */
  private String lineColorB = "0";

  /** Drawing border margin (mm). */
  private double borderMargin = 14.0;

  /** Battery limit boundary padding around equipment (mm). */
  private double batteryLimitPadding = 30.0;

  /** Vertical offset from process line to instrument bubble center. */
  private double instrumentOffsetY = 45.0;

  /** Horizontal spacing between instrument bubbles on the same equipment. */
  private double instrumentXSpacing = 15.0;

  /** Whether to include the stream data table at the bottom of the drawing. */
  private boolean showStreamTable = true;

  /** Whether to include the symbol legend box. */
  private boolean showSymbolLegend = true;

  /** Whether to include the revision history table. */
  private boolean showRevisionHistory = true;

  /** Whether to include the battery limit boundary. */
  private boolean showBatteryLimit = true;

  /** Whether to include flow direction arrows on connection lines. */
  private boolean showFlowArrows = true;

  /** Whether to include stream number labels on connection lines. */
  private boolean showStreamLabels = true;

  /** Whether to show equipment bar labels with simulation data. */
  private boolean showEquipmentBars = true;

  /** Whether to show insulation markings on lines. */
  private boolean showInsulationMarks = true;

  /** Whether to show fail position markers on valves. */
  private boolean showFailPositionMarkers = true;

  /** Whether to show SIL level markers on instruments. */
  private boolean showSilMarkers = true;

  /** Whether to show orientation markers on equipment. */
  private boolean showOrientationMarkers = true;

  /**
   * Creates a default layout configuration.
   */
  public DexpiLayoutConfig() {}

  /**
   * Gets the horizontal spacing between equipment columns.
   *
   * @return X spacing in mm
   */
  public double getXSpacing() {
    return xSpacing;
  }

  /**
   * Sets the horizontal spacing between equipment columns.
   *
   * @param xSpacing X spacing in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setXSpacing(double xSpacing) {
    this.xSpacing = xSpacing;
    return this;
  }

  /**
   * Gets the vertical branch offset.
   *
   * @return Y branch offset in mm
   */
  public double getYBranchOffset() {
    return yBranchOffset;
  }

  /**
   * Sets the vertical branch offset.
   *
   * @param yBranchOffset Y branch offset in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setYBranchOffset(double yBranchOffset) {
    this.yBranchOffset = yBranchOffset;
    return this;
  }

  /**
   * Gets the starting X coordinate.
   *
   * @return X start position in mm
   */
  public double getXStart() {
    return xStart;
  }

  /**
   * Sets the starting X coordinate.
   *
   * @param xStart X start position in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setXStart(double xStart) {
    this.xStart = xStart;
    return this;
  }

  /**
   * Gets the base Y coordinate for the main process line.
   *
   * @return Y base position in mm
   */
  public double getYBase() {
    return yBase;
  }

  /**
   * Sets the base Y coordinate for the main process line.
   *
   * @param yBase Y base position in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setYBase(double yBase) {
    this.yBase = yBase;
    return this;
  }

  /**
   * Gets the default scale factor for equipment shapes.
   *
   * @return scale factor
   */
  public double getDefaultScale() {
    return defaultScale;
  }

  /**
   * Sets the default scale factor for equipment shapes.
   *
   * @param defaultScale scale factor
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setDefaultScale(double defaultScale) {
    this.defaultScale = defaultScale;
    return this;
  }

  /**
   * Gets the font name for labels.
   *
   * @return font name
   */
  public String getFontName() {
    return fontName;
  }

  /**
   * Sets the font name for labels.
   *
   * @param fontName font name
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setFontName(String fontName) {
    this.fontName = fontName;
    return this;
  }

  /**
   * Gets the tag font height.
   *
   * @return font height in mm
   */
  public double getTagFontHeight() {
    return tagFontHeight;
  }

  /**
   * Sets the tag font height.
   *
   * @param tagFontHeight font height in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setTagFontHeight(double tagFontHeight) {
    this.tagFontHeight = tagFontHeight;
    return this;
  }

  /**
   * Gets the process line weight.
   *
   * @return line weight in mm
   */
  public double getProcessLineWeight() {
    return processLineWeight;
  }

  /**
   * Sets the process line weight.
   *
   * @param processLineWeight line weight in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setProcessLineWeight(double processLineWeight) {
    this.processLineWeight = processLineWeight;
    return this;
  }

  /**
   * Gets the signal line weight.
   *
   * @return line weight in mm
   */
  public double getSignalLineWeight() {
    return signalLineWeight;
  }

  /**
   * Sets the signal line weight.
   *
   * @param signalLineWeight line weight in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setSignalLineWeight(double signalLineWeight) {
    this.signalLineWeight = signalLineWeight;
    return this;
  }

  /**
   * Gets the process line colour red component.
   *
   * @return red component (0-1)
   */
  public String getLineColorR() {
    return lineColorR;
  }

  /**
   * Gets the process line colour green component.
   *
   * @return green component (0-1)
   */
  public String getLineColorG() {
    return lineColorG;
  }

  /**
   * Gets the process line colour blue component.
   *
   * @return blue component (0-1)
   */
  public String getLineColorB() {
    return lineColorB;
  }

  /**
   * Sets the process line colour components.
   *
   * @param r red component (0-1)
   * @param g green component (0-1)
   * @param b blue component (0-1)
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setLineColor(String r, String g, String b) {
    this.lineColorR = r;
    this.lineColorG = g;
    this.lineColorB = b;
    return this;
  }

  /**
   * Gets the drawing border margin.
   *
   * @return margin in mm
   */
  public double getBorderMargin() {
    return borderMargin;
  }

  /**
   * Sets the drawing border margin.
   *
   * @param borderMargin margin in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setBorderMargin(double borderMargin) {
    this.borderMargin = borderMargin;
    return this;
  }

  /**
   * Gets the battery limit padding.
   *
   * @return padding in mm
   */
  public double getBatteryLimitPadding() {
    return batteryLimitPadding;
  }

  /**
   * Sets the battery limit padding.
   *
   * @param batteryLimitPadding padding in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setBatteryLimitPadding(double batteryLimitPadding) {
    this.batteryLimitPadding = batteryLimitPadding;
    return this;
  }

  /**
   * Gets the instrument offset Y.
   *
   * @return offset in mm
   */
  public double getInstrumentOffsetY() {
    return instrumentOffsetY;
  }

  /**
   * Sets the instrument offset Y.
   *
   * @param instrumentOffsetY offset in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setInstrumentOffsetY(double instrumentOffsetY) {
    this.instrumentOffsetY = instrumentOffsetY;
    return this;
  }

  /**
   * Gets the instrument X spacing.
   *
   * @return spacing in mm
   */
  public double getInstrumentXSpacing() {
    return instrumentXSpacing;
  }

  /**
   * Sets the instrument X spacing.
   *
   * @param instrumentXSpacing spacing in mm
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setInstrumentXSpacing(double instrumentXSpacing) {
    this.instrumentXSpacing = instrumentXSpacing;
    return this;
  }

  /**
   * Returns whether the stream table should be shown.
   *
   * @return true if stream table is shown
   */
  public boolean isShowStreamTable() {
    return showStreamTable;
  }

  /**
   * Sets whether the stream table should be shown.
   *
   * @param showStreamTable true to show stream table
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowStreamTable(boolean showStreamTable) {
    this.showStreamTable = showStreamTable;
    return this;
  }

  /**
   * Returns whether the symbol legend should be shown.
   *
   * @return true if symbol legend is shown
   */
  public boolean isShowSymbolLegend() {
    return showSymbolLegend;
  }

  /**
   * Sets whether the symbol legend should be shown.
   *
   * @param showSymbolLegend true to show symbol legend
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowSymbolLegend(boolean showSymbolLegend) {
    this.showSymbolLegend = showSymbolLegend;
    return this;
  }

  /**
   * Returns whether the revision history should be shown.
   *
   * @return true if revision history is shown
   */
  public boolean isShowRevisionHistory() {
    return showRevisionHistory;
  }

  /**
   * Sets whether the revision history should be shown.
   *
   * @param showRevisionHistory true to show revision history
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowRevisionHistory(boolean showRevisionHistory) {
    this.showRevisionHistory = showRevisionHistory;
    return this;
  }

  /**
   * Returns whether the battery limit boundary should be shown.
   *
   * @return true if battery limit is shown
   */
  public boolean isShowBatteryLimit() {
    return showBatteryLimit;
  }

  /**
   * Sets whether the battery limit boundary should be shown.
   *
   * @param showBatteryLimit true to show battery limit
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowBatteryLimit(boolean showBatteryLimit) {
    this.showBatteryLimit = showBatteryLimit;
    return this;
  }

  /**
   * Returns whether flow arrows are shown on connection lines.
   *
   * @return true if flow arrows are shown
   */
  public boolean isShowFlowArrows() {
    return showFlowArrows;
  }

  /**
   * Sets whether flow arrows are shown on connection lines.
   *
   * @param showFlowArrows true to show flow arrows
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowFlowArrows(boolean showFlowArrows) {
    this.showFlowArrows = showFlowArrows;
    return this;
  }

  /**
   * Returns whether stream labels are shown.
   *
   * @return true if stream labels are shown
   */
  public boolean isShowStreamLabels() {
    return showStreamLabels;
  }

  /**
   * Sets whether stream labels are shown.
   *
   * @param showStreamLabels true to show stream labels
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowStreamLabels(boolean showStreamLabels) {
    this.showStreamLabels = showStreamLabels;
    return this;
  }

  /**
   * Returns whether equipment bar labels with simulation data are shown.
   *
   * @return true if equipment bars are shown
   */
  public boolean isShowEquipmentBars() {
    return showEquipmentBars;
  }

  /**
   * Sets whether equipment bar labels with simulation data are shown.
   *
   * @param showEquipmentBars true to show equipment bars
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowEquipmentBars(boolean showEquipmentBars) {
    this.showEquipmentBars = showEquipmentBars;
    return this;
  }

  /**
   * Returns whether insulation marks are shown.
   *
   * @return true if insulation marks are shown
   */
  public boolean isShowInsulationMarks() {
    return showInsulationMarks;
  }

  /**
   * Sets whether insulation marks are shown.
   *
   * @param showInsulationMarks true to show insulation marks
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowInsulationMarks(boolean showInsulationMarks) {
    this.showInsulationMarks = showInsulationMarks;
    return this;
  }

  /**
   * Returns whether fail position markers are shown on valves.
   *
   * @return true if fail position markers are shown
   */
  public boolean isShowFailPositionMarkers() {
    return showFailPositionMarkers;
  }

  /**
   * Sets whether fail position markers are shown on valves.
   *
   * @param showFailPositionMarkers true to show fail position markers
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowFailPositionMarkers(boolean showFailPositionMarkers) {
    this.showFailPositionMarkers = showFailPositionMarkers;
    return this;
  }

  /**
   * Returns whether SIL markers are shown on instruments.
   *
   * @return true if SIL markers are shown
   */
  public boolean isShowSilMarkers() {
    return showSilMarkers;
  }

  /**
   * Sets whether SIL markers are shown on instruments.
   *
   * @param showSilMarkers true to show SIL markers
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowSilMarkers(boolean showSilMarkers) {
    this.showSilMarkers = showSilMarkers;
    return this;
  }

  /**
   * Returns whether orientation markers are shown on equipment.
   *
   * @return true if orientation markers are shown
   */
  public boolean isShowOrientationMarkers() {
    return showOrientationMarkers;
  }

  /**
   * Sets whether orientation markers are shown on equipment.
   *
   * @param showOrientationMarkers true to show orientation markers
   * @return this config for method chaining
   */
  public DexpiLayoutConfig setShowOrientationMarkers(boolean showOrientationMarkers) {
    this.showOrientationMarkers = showOrientationMarkers;
    return this;
  }
}
