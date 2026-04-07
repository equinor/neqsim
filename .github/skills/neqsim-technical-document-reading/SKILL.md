---
name: neqsim-technical-document-reading
description: "Reads and extracts structured engineering data from technical documents (PDFs, Word, Excel, CSV). USE WHEN: a user provides engineering documents — equipment data sheets, technical requirements, design basis, well test reports, P&ID descriptions, inspection reports, standards — and needs structured data for process simulation. Covers document classification, extraction patterns by document type, unit normalization, data quality scoring, and output formats."
---

# Technical Document Reading Skill

Extract structured engineering data from technical documents and convert it into
formats usable by process simulation, mechanical design, and engineering analysis.

## Core Principle

> **Classify → Extract → Normalize → Validate → Output**
>
> 1. Classify the document type to select the right extraction strategy
> 2. Extract raw data using format-specific tools (PDF, Word, Excel)
> 3. Normalize units, component names, and field names to standard conventions
> 4. Validate extracted data against physical bounds and completeness checks
> 5. Output structured JSON/dict for downstream consumption

---

## 1. Document Type Classification

### Classification Decision Tree

When a document is provided, classify it first:

| Document Type | Identifying Features | Key Data to Extract |
|--------------|---------------------|---------------------|
| **Equipment Data Sheet** | API/ASME header, tag numbers, design conditions table | Design T/P, materials, dimensions, nozzle schedule |
| **Technical Requirement (TR)** | Numbered clauses, "shall"/"should" language, design factors | Design rules, factors, material constraints, limits |
| **Design Basis** | Operating envelope, fluid composition tables, flow scheme description | Compositions, T/P ranges, flow rates, design life |
| **Heat & Mass Balance** | Stream table with T, P, flow, composition columns | Stream conditions, equipment duties, compositions |
| **Well Test Report** | Choke sizes, flow rates vs time, GOR, water cut | Flow rates, compositions, reservoir P/T, productivity |
| **P&ID / PFD Description** | Equipment tags, line numbers, instrument tags, connectivity | Topology, instrumentation, control philosophy |
| **Piping Specification** | Material classes, pressure ratings, wall schedules | Pipe sizes, materials, ratings, corrosion allowance |
| **Inspection Report** | Thickness measurements, corrosion rates, anomaly locations | Wall thickness, corrosion rate, remaining life |
| **Material Certificate** | Heat numbers, SMYS/SMTS, chemical composition, Charpy values | Mechanical properties, chemical analysis, grade |
| **Standards Document** | ISO/API/ASME/DNV/NORSOK header, normative references | Design formulas, factors, limits, test requirements |
| **Vendor Quotation** | Equipment specs, pricing, delivery, performance curves | Performance data, dimensions, weight, cost |
| **Operating Procedure** | Step-by-step instructions, setpoints, alarm limits | Operating envelope, control setpoints, trip values |

### Auto-Detection Heuristics

When document type isn't stated, look for these patterns:

```python
DOCUMENT_SIGNATURES = {
    "equipment_data_sheet": [
        r"(?i)data\s*sheet", r"(?i)tag\s*no", r"(?i)design\s+press",
        r"(?i)operating\s+press", r"(?i)nozzle\s+schedule"
    ],
    "technical_requirement": [
        r"(?i)technical\s+requirement", r"(?i)\bTR[\s-]?\d+",
        r"(?i)\bshall\b.*\bdesign", r"(?i)design\s+factor"
    ],
    "design_basis": [
        r"(?i)design\s+basis", r"(?i)basis\s+of\s+design",
        r"(?i)operating\s+envelope", r"(?i)ambient\s+conditions"
    ],
    "heat_mass_balance": [
        r"(?i)heat\s+.*mass\s+balance", r"(?i)stream\s+(table|summary)",
        r"(?i)mol\s*%|mole\s+frac", r"(?i)vapour\s+fraction"
    ],
    "well_test": [
        r"(?i)well\s+test", r"(?i)choke\s+size", r"(?i)GOR",
        r"(?i)productivity\s+index", r"(?i)IPR"
    ],
    "inspection_report": [
        r"(?i)inspection\s+report", r"(?i)wall\s+thickness",
        r"(?i)corrosion\s+rate", r"(?i)remaining\s+life"
    ],
    "material_certificate": [
        r"(?i)material\s+cert", r"(?i)heat\s+number",
        r"(?i)SMYS|SMTS", r"(?i)charpy|impact\s+test"
    ],
    "piping_spec": [
        r"(?i)piping\s+class", r"(?i)material\s+class",
        r"(?i)line\s+class", r"(?i)pressure\s+rating"
    ],
}
```

---

## 2. File Format Handlers

### 2.1 PDF Extraction

**Recommended library:** `pdfplumber` (table-aware), fallback to `pymupdf` (fitz)

```python
import pdfplumber

def extract_pdf(filepath):
    """Extract text and tables from a PDF document."""
    pages = []
    tables = []
    with pdfplumber.open(filepath) as pdf:
        for i, page in enumerate(pdf.pages):
            # Extract text
            text = page.extract_text() or ""
            pages.append({"page": i + 1, "text": text})

            # Extract tables (pdfplumber detects table boundaries)
            for table in page.extract_tables():
                if table and len(table) > 1:
                    headers = [str(h).strip() if h else "" for h in table[0]]
                    rows = []
                    for row in table[1:]:
                        rows.append([str(c).strip() if c else "" for c in row])
                    tables.append({
                        "page": i + 1,
                        "headers": headers,
                        "rows": rows
                    })
    return {"pages": pages, "tables": tables}
```

**Handling scanned PDFs:**
- If `extract_text()` returns empty, the PDF is likely scanned/image-based
- Use OCR as fallback: `pytesseract` + `pdf2image`
- Flag to user: "This appears to be a scanned document. OCR extraction may have errors."

### 2.2 Word Document Extraction

**Library:** `python-docx`

```python
from docx import Document

def extract_docx(filepath):
    """Extract paragraphs and tables from a Word document."""
    doc = Document(filepath)
    paragraphs = []
    tables = []

    for para in doc.paragraphs:
        if para.text.strip():
            paragraphs.append({
                "text": para.text.strip(),
                "style": para.style.name,
                "level": _heading_level(para.style.name)
            })

    for i, table in enumerate(doc.tables):
        headers = [cell.text.strip() for cell in table.rows[0].cells]
        rows = []
        for row in table.rows[1:]:
            rows.append([cell.text.strip() for cell in row.cells])
        tables.append({"index": i, "headers": headers, "rows": rows})

    return {"paragraphs": paragraphs, "tables": tables}


def _heading_level(style_name):
    """Return heading level (1-9) or 0 for body text."""
    if style_name.startswith("Heading"):
        try:
            return int(style_name.split()[-1])
        except ValueError:
            return 0
    return 0
```

### 2.3 Excel / CSV Extraction

**Libraries:** `openpyxl` for .xlsx, `pandas` for both

```python
import pandas as pd

def extract_excel(filepath, sheet_name=None):
    """Extract tables from Excel workbook."""
    sheets = {}
    xls = pd.ExcelFile(filepath)
    target_sheets = [sheet_name] if sheet_name else xls.sheet_names

    for name in target_sheets:
        df = pd.read_excel(xls, sheet_name=name)
        # Drop fully empty rows/columns
        df = df.dropna(how="all").dropna(axis=1, how="all")
        sheets[name] = {
            "headers": list(df.columns),
            "rows": df.values.tolist(),
            "shape": list(df.shape)
        }
    return sheets


def extract_csv(filepath):
    """Extract table from CSV file."""
    df = pd.read_csv(filepath)
    df = df.dropna(how="all").dropna(axis=1, how="all")
    return {
        "headers": list(df.columns),
        "rows": df.values.tolist(),
        "shape": list(df.shape)
    }
```

---

## 3. Extraction Patterns by Document Type

### 3.1 Equipment Data Sheet

Equipment data sheets follow standard API/ISO layouts. Key sections:

```python
DATASHEET_SECTIONS = {
    "general": {
        "patterns": [
            r"(?i)tag\s*(?:no|number|#)[\s:]+(\S+)",
            r"(?i)service[\s:]+(.+?)(?:\n|$)",
            r"(?i)quantity[\s:]+(\d+)",
            r"(?i)type[\s:]+(.+?)(?:\n|$)",
        ],
        "fields": ["tag_number", "service", "quantity", "equipment_type"]
    },
    "design_conditions": {
        "patterns": [
            r"(?i)design\s+press(?:ure)?[\s:]+([0-9.]+)\s*(bar|bara|barg|psi|psig|MPa|kPa)",
            r"(?i)design\s+temp(?:erature)?[\s:]+([0-9.+-]+)\s*(°?[CFK]|degC|degF)",
            r"(?i)oper(?:ating)?\s+press(?:ure)?[\s:]+([0-9.]+)\s*(bar|bara|barg|psi|psig|MPa|kPa)",
            r"(?i)oper(?:ating)?\s+temp(?:erature)?[\s:]+([0-9.+-]+)\s*(°?[CFK]|degC|degF)",
        ],
        "fields": ["design_pressure", "design_temperature",
                    "operating_pressure", "operating_temperature"]
    },
    "dimensions": {
        "patterns": [
            r"(?i)(?:ID|inner\s+diameter|internal\s+diameter)[\s:]+([0-9.]+)\s*(mm|m|in|inch|ft)",
            r"(?i)(?:OD|outer\s+diameter|outside\s+diameter)[\s:]+([0-9.]+)\s*(mm|m|in|inch|ft)",
            r"(?i)length[\s:]+([0-9.]+)\s*(mm|m|ft)",
            r"(?i)(?:t-t|tan.*tan|seam.*seam|height)[\s:]+([0-9.]+)\s*(mm|m|ft)",
            r"(?i)wall\s+thick(?:ness)?[\s:]+([0-9.]+)\s*(mm|in|inch)",
        ],
        "fields": ["inner_diameter", "outer_diameter", "length",
                    "height", "wall_thickness"]
    },
    "material": {
        "patterns": [
            r"(?i)(?:shell|body)\s+material[\s:]+(\S+(?:\s+\S+)?)",
            r"(?i)material\s+grade[\s:]+(\S+)",
            r"(?i)(?:SA|ASTM|A)\s*-?\s*(\d+)(?:\s*-?\s*(?:Gr\.?\s*)?(\S+))?",
        ],
        "fields": ["shell_material", "material_grade"]
    }
}
```

**Table extraction for data sheets:**

```python
def extract_datasheet_table(table):
    """Parse an equipment data sheet table into structured fields.

    Handles common layouts where col 0 is field name, col 1+ are values.
    Also handles multi-case tables (normal/upset/design columns).
    """
    result = {}
    headers = table["headers"]
    for row in table["rows"]:
        if len(row) >= 2 and row[0]:
            field_name = row[0].strip().lower()
            # Multi-case: create dict with case names as keys
            if len(headers) > 2 and len(row) > 2:
                for i, header in enumerate(headers[1:], start=1):
                    if i < len(row) and row[i]:
                        case_key = header.strip() if header else f"case_{i}"
                        if field_name not in result:
                            result[field_name] = {}
                        result[field_name][case_key] = _parse_value(row[i])
            else:
                result[field_name] = _parse_value(row[1])
    return result


def _parse_value(text):
    """Parse a text value, attempting numeric conversion."""
    text = str(text).strip()
    if not text or text == "-" or text.lower() == "n/a":
        return None
    try:
        return float(text.replace(",", ""))
    except ValueError:
        return text
```

### 3.2 Heat & Mass Balance (Stream Table)

Stream tables are the most common source of process data. They vary in layout:

**Layout A — Streams as columns:**
| Property | Stream 1 | Stream 2 | Stream 3 |
|----------|----------|----------|----------|
| Temperature (°C) | 40.0 | 15.0 | 120.0 |
| Pressure (bara) | 80.0 | 78.0 | 150.0 |

**Layout B — Streams as rows:**
| Stream | T (°C) | P (bara) | Flow (kg/h) |
|--------|--------|----------|-------------|
| Feed | 40.0 | 80.0 | 75000 |

```python
def extract_stream_table(table, layout="auto"):
    """Extract stream data from a heat & mass balance table.

    Args:
        table: dict with "headers" and "rows" keys
        layout: "columns" (streams as columns), "rows" (streams as rows), or "auto"

    Returns:
        dict mapping stream names to their properties
    """
    headers = table["headers"]
    rows = table["rows"]

    if layout == "auto":
        layout = _detect_stream_layout(headers, rows)

    streams = {}

    if layout == "columns":
        # First column is property name, remaining columns are streams
        stream_names = [h.strip() for h in headers[1:] if h and h.strip()]
        for row in rows:
            if not row[0]:
                continue
            prop_name, prop_unit = _parse_property_header(row[0])
            for i, stream_name in enumerate(stream_names):
                col_idx = i + 1
                if col_idx < len(row) and row[col_idx]:
                    if stream_name not in streams:
                        streams[stream_name] = {}
                    streams[stream_name][prop_name] = {
                        "value": _parse_value(row[col_idx]),
                        "unit": prop_unit
                    }
    else:
        # First column (or first few) is stream name, rest are properties
        for row in rows:
            if not row[0]:
                continue
            stream_name = str(row[0]).strip()
            streams[stream_name] = {}
            for j, header in enumerate(headers[1:], start=1):
                if j < len(row) and row[j]:
                    prop_name, prop_unit = _parse_property_header(header)
                    streams[stream_name][prop_name] = {
                        "value": _parse_value(row[j]),
                        "unit": prop_unit
                    }

    return streams


def _detect_stream_layout(headers, rows):
    """Heuristic: if first header looks like a property label, it's column layout."""
    first_header = str(headers[0]).strip().lower()
    property_keywords = ["property", "parameter", "description", "item",
                         "stream", "temperature", "pressure", "flow"]
    if any(kw in first_header for kw in property_keywords[:4]):
        return "columns"
    return "rows"


def _parse_property_header(text):
    """Split 'Temperature (°C)' into ('temperature', '°C')."""
    import re
    text = str(text).strip()
    match = re.match(r"(.+?)\s*[\(\[]([^\)\]]+)[\)\]]", text)
    if match:
        return match.group(1).strip().lower(), match.group(2).strip()
    return text.lower(), ""
```

### 3.3 Fluid Composition Tables

```python
# Common header variations for composition columns
COMPOSITION_HEADERS = {
    "component": [
        r"(?i)component", r"(?i)species", r"(?i)compound", r"(?i)name"
    ],
    "mole_fraction": [
        r"(?i)mol\s*%", r"(?i)mole\s+frac", r"(?i)mol\s+frac",
        r"(?i)y\s*[\(\[]", r"(?i)x\s*[\(\[]", r"(?i)z\s*[\(\[]",
        r"(?i)composition\s*\(mol", r"(?i)molar"
    ],
    "mass_fraction": [
        r"(?i)wt\s*%", r"(?i)mass\s+frac", r"(?i)weight\s+frac",
        r"(?i)mass\s*%"
    ],
    "volume_fraction": [
        r"(?i)vol\s*%", r"(?i)volume\s+frac", r"(?i)liq\s+vol\s*%"
    ]
}

# Component name mapping (common variations → NeqSim names)
COMPONENT_NAME_MAP = {
    # Hydrocarbons
    "c1": "methane", "ch4": "methane", "methane": "methane",
    "c2": "ethane", "c2h6": "ethane", "ethane": "ethane",
    "c3": "propane", "c3h8": "propane", "propane": "propane",
    "ic4": "i-butane", "i-c4": "i-butane", "isobutane": "i-butane",
    "nc4": "n-butane", "n-c4": "n-butane",
    "ic5": "i-pentane", "i-c5": "i-pentane", "isopentane": "i-pentane",
    "nc5": "n-pentane", "n-c5": "n-pentane",
    "nc6": "n-hexane", "n-c6": "n-hexane", "hexane": "n-hexane",
    "nc7": "n-heptane", "n-c7": "n-heptane", "heptane": "n-heptane",
    "nc8": "n-octane", "n-c8": "n-octane", "octane": "n-octane",
    "nc9": "n-nonane", "n-c9": "n-nonane",
    "nc10": "nC10", "n-c10": "nC10",
    "cyclohexane": "cyclohexane", "c-c6": "cyclohexane",
    "benzene": "benzene", "toluene": "toluene",
    # Non-hydrocarbons
    "co2": "CO2", "carbon dioxide": "CO2",
    "h2s": "H2S", "hydrogen sulfide": "H2S", "hydrogen sulphide": "H2S",
    "n2": "nitrogen", "nitrogen": "nitrogen",
    "h2": "hydrogen", "hydrogen": "hydrogen",
    "h2o": "water", "water": "water",
    "o2": "oxygen", "oxygen": "oxygen",
    "ar": "argon", "argon": "argon",
    "he": "helium", "helium": "helium",
    "co": "CO",
    # Glycols
    "meg": "MEG", "monoethylene glycol": "MEG",
    "deg": "DEG", "diethylene glycol": "DEG",
    "teg": "TEG", "triethylene glycol": "TEG",
    # Mercaptans
    "methyl mercaptan": "methyl-mercaptan", "ch3sh": "methyl-mercaptan",
    "ethyl mercaptan": "ethyl-mercaptan", "c2h5sh": "ethyl-mercaptan",
}


def normalize_component_name(raw_name):
    """Map a raw component name to the NeqSim standard name."""
    key = raw_name.strip().lower().replace("_", " ").replace("-", " ")
    # Direct lookup
    if key in COMPONENT_NAME_MAP:
        return COMPONENT_NAME_MAP[key]
    # Try removing spaces
    key_nospace = key.replace(" ", "")
    if key_nospace in COMPONENT_NAME_MAP:
        return COMPONENT_NAME_MAP[key_nospace]
    # Return original (user may need to map manually)
    return raw_name.strip()


def extract_composition(table):
    """Extract fluid composition from a table.

    Returns dict of {neqsim_name: mole_fraction} and metadata about
    the composition basis (mole%, mass%, etc.).
    """
    import re
    headers = table["headers"]

    # Find component column and value column
    comp_col = None
    value_col = None
    basis = "mole_fraction"

    for i, h in enumerate(headers):
        h_str = str(h).strip()
        for pattern in COMPOSITION_HEADERS["component"]:
            if re.search(pattern, h_str):
                comp_col = i
                break
        for basis_type in ["mole_fraction", "mass_fraction", "volume_fraction"]:
            for pattern in COMPOSITION_HEADERS[basis_type]:
                if re.search(pattern, h_str):
                    value_col = i
                    basis = basis_type
                    break

    # Fallback: assume col 0 = component, col 1 = value
    if comp_col is None:
        comp_col = 0
    if value_col is None:
        value_col = 1

    composition = {}
    for row in table["rows"]:
        if comp_col < len(row) and value_col < len(row):
            name = str(row[comp_col]).strip()
            value = _parse_value(row[value_col])
            if name and value is not None:
                neqsim_name = normalize_component_name(name)
                composition[neqsim_name] = float(value)

    # Normalize: if values sum to ~100, convert to fractions
    total = sum(composition.values())
    if total > 1.5:  # Likely percentages
        composition = {k: v / 100.0 for k, v in composition.items()}
        total = sum(composition.values())

    return {
        "components": composition,
        "basis": basis,
        "total": total,
        "normalized": abs(total - 1.0) < 0.02
    }
```

### 3.4 Technical Requirements

TR documents contain design rules expressed as requirements. Extract:

```python
def extract_requirements(text):
    """Extract numbered requirements from a technical requirements document.

    Looks for 'shall' and 'should' statements with associated values.
    """
    import re
    requirements = []

    # Pattern: numbered clause + requirement text
    clause_pattern = re.compile(
        r"(\d+(?:\.\d+)*)\s+(.*?(?:shall|should|must|may)\s+.*?)(?=\n\d+(?:\.\d+)*\s|\Z)",
        re.IGNORECASE | re.DOTALL
    )

    for match in clause_pattern.finditer(text):
        clause_num = match.group(1)
        req_text = match.group(2).strip()

        # Extract numeric values with units
        values = re.findall(
            r"(\d+(?:\.\d+)?)\s*(bar[ag]?|°?[CFK]|mm|m|kg|psi|MPa|kPa|%|hr|min)",
            req_text
        )

        # Classify requirement type
        if re.search(r"(?i)shall\b", req_text):
            level = "mandatory"
        elif re.search(r"(?i)should\b", req_text):
            level = "recommended"
        else:
            level = "informative"

        requirements.append({
            "clause": clause_num,
            "text": req_text,
            "level": level,
            "values": [{"value": float(v), "unit": u} for v, u in values]
        })

    return requirements
```

### 3.5 Well Test Reports

```python
WELL_TEST_FIELDS = {
    "flow_rate": [
        r"(?i)(?:oil|gas|liquid|water)\s+(?:flow\s+)?rate[\s:]+([0-9.,]+)\s*(Sm3/d|bbl/d|MMSCFD|kg/hr|m3/d)",
    ],
    "gor": [
        r"(?i)GOR[\s:]+([0-9.,]+)\s*(Sm3/Sm3|scf/bbl|m3/m3)",
    ],
    "water_cut": [
        r"(?i)water\s*cut[\s:]+([0-9.,]+)\s*(%)?",
        r"(?i)BSW[\s:]+([0-9.,]+)\s*(%)?",
    ],
    "reservoir_pressure": [
        r"(?i)reservoir\s+press(?:ure)?[\s:]+([0-9.,]+)\s*(bar[ag]?|psi[ag]?|MPa|kPa)",
    ],
    "reservoir_temperature": [
        r"(?i)reservoir\s+temp(?:erature)?[\s:]+([0-9.,]+)\s*(°?[CFK])",
    ],
    "wellhead_pressure": [
        r"(?i)(?:WHP|wellhead\s+press(?:ure)?)[\s:]+([0-9.,]+)\s*(bar[ag]?|psi[ag]?|MPa)",
    ],
    "wellhead_temperature": [
        r"(?i)(?:WHT|wellhead\s+temp(?:erature)?)[\s:]+([0-9.,]+)\s*(°?[CFK])",
    ],
    "choke_size": [
        r"(?i)choke[\s:]+([0-9.,]+)(?:/64)?[\s]*(mm|inch|in|/64)",
    ],
    "productivity_index": [
        r"(?i)(?:PI|productivity\s+index)[\s:]+([0-9.,]+)\s*(Sm3/d/bar|bbl/d/psi)",
    ],
}
```

### 3.6 Inspection Reports

```python
def extract_thickness_data(table):
    """Extract wall thickness measurements from an inspection table.

    Common format: Location | Nominal (mm) | Measured (mm) | Min (mm) | Corrosion Rate
    """
    result = []
    for row in table["rows"]:
        entry = {}
        for i, header in enumerate(table["headers"]):
            h = str(header).strip().lower()
            if i < len(row) and row[i]:
                val = _parse_value(row[i])
                if "location" in h or "position" in h or "point" in h:
                    entry["location"] = str(row[i]).strip()
                elif "nominal" in h or "original" in h:
                    entry["nominal_mm"] = val
                elif "measured" in h or "actual" in h or "remaining" in h:
                    entry["measured_mm"] = val
                elif "min" in h:
                    entry["minimum_mm"] = val
                elif "corrosion" in h and "rate" in h:
                    entry["corrosion_rate_mm_yr"] = val
        if entry:
            result.append(entry)
    return result
```

---

## 4. Unit Normalization

All extracted values must be normalized to consistent engineering units.

### Standard Units (SI-based Engineering)

| Quantity | Standard Unit | Symbol |
|----------|--------------|--------|
| Temperature | Kelvin | K |
| Pressure | bara | bara |
| Mass flow | kg/hr | kg/hr |
| Volumetric flow (gas) | Sm³/d | Sm3/d |
| Volumetric flow (liquid) | m³/hr | m3/hr |
| Length | m | m |
| Diameter | mm | mm |
| Wall thickness | mm | mm |
| Density | kg/m³ | kg/m3 |
| Viscosity | mPa·s (cP) | cP |
| Power | kW | kW |
| Heat duty | kW | kW |
| Heat transfer coeff. | W/(m²·K) | W/m2K |
| Thermal conductivity | W/(m·K) | W/mK |

### Conversion Functions

```python
def convert_temperature(value, from_unit, to_unit="K"):
    """Convert temperature between C, F, K, R."""
    unit = from_unit.strip().replace("°", "").replace("deg", "").upper()
    # Convert to Kelvin first
    if unit in ("C", "CELSIUS"):
        kelvin = value + 273.15
    elif unit in ("F", "FAHRENHEIT"):
        kelvin = (value - 32) * 5.0 / 9.0 + 273.15
    elif unit in ("R", "RANKINE"):
        kelvin = value * 5.0 / 9.0
    else:
        kelvin = value  # Assume Kelvin

    if to_unit.upper() in ("C", "CELSIUS"):
        return kelvin - 273.15
    elif to_unit.upper() in ("F", "FAHRENHEIT"):
        return (kelvin - 273.15) * 9.0 / 5.0 + 32
    return kelvin


def convert_pressure(value, from_unit, to_unit="bara"):
    """Convert pressure to bara."""
    unit = from_unit.strip().lower()
    conversions_to_bara = {
        "bara": 1.0,
        "barg": lambda v: v + 1.01325,
        "bar": 1.0,  # Assume absolute unless 'g' suffix
        "psia": 0.0689476,
        "psig": lambda v: (v + 14.696) * 0.0689476,
        "psi": 0.0689476,  # Assume absolute
        "mpa": 10.0,
        "kpa": 0.01,
        "atm": 1.01325,
        "mmhg": 0.00133322,
        "torr": 0.00133322,
    }
    factor = conversions_to_bara.get(unit, 1.0)
    if callable(factor):
        return factor(value)
    return value * factor


def convert_length(value, from_unit, to_unit="mm"):
    """Convert length to mm."""
    unit = from_unit.strip().lower()
    to_mm = {
        "mm": 1.0, "cm": 10.0, "m": 1000.0, "km": 1e6,
        "in": 25.4, "inch": 25.4, "inches": 25.4,
        "ft": 304.8, "feet": 304.8, "foot": 304.8,
        "yd": 914.4,
    }
    mm_val = value * to_mm.get(unit, 1.0)
    to_target = {"mm": 1.0, "m": 0.001, "in": 1.0 / 25.4, "ft": 1.0 / 304.8}
    return mm_val * to_target.get(to_unit.lower(), 1.0)


def convert_flow(value, from_unit, to_unit="kg/hr"):
    """Convert mass/volumetric flow rates. Approximate — exact conversion needs density."""
    unit = from_unit.strip().lower().replace(" ", "")
    # Direct mass flow conversions to kg/hr
    mass_to_kghr = {
        "kg/hr": 1.0, "kg/h": 1.0, "kg/s": 3600.0,
        "t/hr": 1000.0, "t/h": 1000.0, "t/d": 1000.0 / 24.0,
        "lb/hr": 0.453592, "lb/h": 0.453592,
    }
    if unit in mass_to_kghr:
        return value * mass_to_kghr[unit]
    return value  # Return as-is with warning
```

---

## 5. Data Quality and Validation

### 5.1 Completeness Score

Rate extracted data on completeness for downstream simulation:

```python
def score_extraction_quality(extracted_data, document_type):
    """Score the quality and completeness of extracted data (0-100)."""
    scores = {}

    if document_type == "design_basis":
        required = ["fluid_composition", "temperature", "pressure", "flow_rate"]
        optional = ["water_cut", "gor", "h2s_content", "co2_content"]
    elif document_type == "equipment_data_sheet":
        required = ["tag_number", "design_pressure", "design_temperature", "material"]
        optional = ["dimensions", "nozzle_schedule", "weight"]
    elif document_type == "heat_mass_balance":
        required = ["stream_names", "temperatures", "pressures", "flows"]
        optional = ["compositions", "densities", "viscosities"]
    else:
        required = []
        optional = []

    found_required = sum(1 for r in required if r in extracted_data and extracted_data[r])
    found_optional = sum(1 for o in optional if o in extracted_data and extracted_data[o])

    req_score = (found_required / max(len(required), 1)) * 70
    opt_score = (found_optional / max(len(optional), 1)) * 30

    return {
        "total_score": round(req_score + opt_score),
        "required_found": found_required,
        "required_total": len(required),
        "optional_found": found_optional,
        "optional_total": len(optional),
        "missing_required": [r for r in required if r not in extracted_data or not extracted_data[r]],
        "missing_optional": [o for o in optional if o not in extracted_data or not extracted_data[o]],
    }
```

### 5.2 Physical Bounds Validation

```python
PHYSICAL_BOUNDS = {
    "temperature_K": (50.0, 2000.0),       # 50 K to 2000 K
    "pressure_bara": (0.001, 10000.0),      # Near vacuum to ultra-high
    "mole_fraction": (0.0, 1.0),
    "flow_kg_hr": (0.0, 1e9),
    "wall_thickness_mm": (0.1, 500.0),
    "diameter_mm": (1.0, 100000.0),         # 1 mm to 100 m
    "density_kg_m3": (0.01, 25000.0),
    "viscosity_cP": (0.001, 1e6),
    "corrosion_rate_mm_yr": (0.0, 50.0),
}

def validate_physical_bounds(field_name, value, unit=None):
    """Check if a value is within physically reasonable bounds."""
    key = field_name.lower().replace(" ", "_")
    for bound_key, (low, high) in PHYSICAL_BOUNDS.items():
        if bound_key in key:
            if value < low or value > high:
                return {
                    "valid": False,
                    "field": field_name,
                    "value": value,
                    "bounds": (low, high),
                    "message": f"{field_name}={value} outside bounds [{low}, {high}]"
                }
            return {"valid": True, "field": field_name, "value": value}
    return {"valid": True, "field": field_name, "value": value, "note": "no bounds defined"}
```

---

## 6. Output Format

### 6.1 Structured Extraction Result

Every extraction produces this standard JSON structure:

```json
{
  "source": {
    "filename": "design_basis_rev3.pdf",
    "format": "pdf",
    "pages": 42,
    "document_type": "design_basis",
    "confidence": 0.85
  },
  "metadata": {
    "document_title": "Design Basis for Gas Processing Facility",
    "revision": "Rev 3",
    "date": "2024-06-15",
    "document_number": "DOC-12345"
  },
  "fluid_data": {
    "compositions": {
      "feed_gas": {
        "components": {"methane": 0.85, "ethane": 0.07, "propane": 0.03, "CO2": 0.02},
        "basis": "mole_fraction",
        "total": 1.0
      }
    },
    "conditions": {
      "temperature": {"value": 313.15, "unit": "K", "original": "40 °C"},
      "pressure": {"value": 80.0, "unit": "bara", "original": "80 bara"}
    }
  },
  "equipment_data": [
    {
      "tag": "V-100",
      "type": "Separator",
      "service": "HP Separator",
      "design_pressure": {"value": 95.0, "unit": "bara"},
      "design_temperature": {"value": 373.15, "unit": "K"},
      "material": "SA-516-70"
    }
  ],
  "process_data": {
    "streams": {},
    "operating_envelope": {}
  },
  "requirements": [],
  "quality": {
    "total_score": 78,
    "missing_required": ["water_cut"],
    "warnings": ["Composition sums to 0.97 — 3% unaccounted"]
  }
}
```

### 6.2 Conversion to NeqSim JSON

The extraction result can be converted to NeqSim process JSON:

```python
def extraction_to_neqsim_json(extraction_result):
    """Convert extraction result to NeqSim ProcessSystem.fromJson() format.

    This bridges the document reader output to the process extraction pipeline.
    """
    fluid_data = extraction_result.get("fluid_data", {})
    compositions = fluid_data.get("compositions", {})
    conditions = fluid_data.get("conditions", {})

    # Pick the first/primary composition
    comp_name = list(compositions.keys())[0] if compositions else None
    if not comp_name:
        return None

    comp = compositions[comp_name]

    result = {
        "fluid": {
            "model": "SRK",
            "temperature": conditions.get("temperature", {}).get("value", 298.15),
            "pressure": conditions.get("pressure", {}).get("value", 1.01325),
            "mixingRule": "classic",
            "components": comp["components"]
        },
        "process": [],
        "autoRun": True
    }

    return result
```

---

## 7. Multi-Document Workflows

When multiple documents describe the same system, merge data with priority rules:

### Priority Order (highest first)

1. **Equipment Data Sheets** — most specific, verified engineering data
2. **Heat & Mass Balance** — simulation-verified stream data
3. **P&ID** — definitive topology and instrumentation
4. **Design Basis** — project-level specifications
5. **Technical Requirements** — design rules and constraints
6. **Vendor Quotations** — actual equipment performance
7. **Operating Procedures** — actual operational parameters

### Merge Strategy

```python
def merge_extractions(extractions):
    """Merge multiple extraction results, respecting priority.

    Args:
        extractions: list of (extraction_result, priority) tuples,
                     sorted by priority (highest first)
    Returns:
        Merged extraction result
    """
    merged = {
        "sources": [],
        "fluid_data": {"compositions": {}, "conditions": {}},
        "equipment_data": [],
        "process_data": {"streams": {}},
        "requirements": [],
        "conflicts": []
    }

    seen_tags = {}  # tag -> source for conflict detection

    for extraction, priority in sorted(extractions, key=lambda x: -x[1]):
        source = extraction.get("source", {})
        merged["sources"].append(source)

        # Merge compositions (higher priority wins)
        for name, comp in extraction.get("fluid_data", {}).get("compositions", {}).items():
            if name not in merged["fluid_data"]["compositions"]:
                merged["fluid_data"]["compositions"][name] = comp

        # Merge equipment (detect conflicts on same tag)
        for equip in extraction.get("equipment_data", []):
            tag = equip.get("tag", "")
            if tag in seen_tags:
                merged["conflicts"].append({
                    "tag": tag,
                    "field": "equipment",
                    "source_1": seen_tags[tag],
                    "source_2": source.get("filename", "unknown")
                })
            else:
                seen_tags[tag] = source.get("filename", "unknown")
                merged["equipment_data"].append(equip)

        # Merge requirements (accumulate all)
        merged["requirements"].extend(extraction.get("requirements", []))

    return merged
```

---

## 8. Practical Tips

### Common Parsing Challenges

| Challenge | Solution |
|-----------|----------|
| Merged cells in PDF tables | Use `pdfplumber` with custom table settings: `table_settings={"snap_tolerance": 5}` |
| Multi-line cell values | Join with space, then re-parse |
| Header row detection | Look for bold formatting or known keywords |
| Unicode issues (°, ², ³, µ) | Normalize with `unicodedata.normalize("NFKD", text)` |
| Tables spanning multiple pages | Detect continued tables by matching column count |
| Rotated text in PDFs | Use `page.extract_text(layout=True)` or OCR |
| Mixed number formats (1,000.5 vs 1.000,5) | Detect locale from document language |
| Empty/missing values | Use sentinel: `None` (not 0, not empty string) |

### Performance Guidelines

- For large documents (>50 pages), extract table of contents first and target relevant sections
- Cache extracted data to avoid re-parsing
- For Excel files with many sheets, let user specify which sheets to read
- For multi-file batches, process in parallel where possible

### Required Python Packages

```
pdfplumber>=0.9.0     # PDF table extraction
python-docx>=0.8.11   # Word document reading
openpyxl>=3.1.0       # Excel reading
pandas>=1.5.0         # Tabular data handling
```

Optional (for advanced scenarios):
```
pymupdf>=1.22.0       # Fast PDF text extraction (fitz)
pytesseract>=0.3.10   # OCR for scanned PDFs
pdf2image>=1.16.0     # Convert PDF pages to images for OCR
tabula-py>=2.7.0      # Alternative PDF table extraction (Java-based)
camelot-py>=0.11.0    # Another PDF table tool (good for bordered tables)
```
