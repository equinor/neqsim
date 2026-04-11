"""
UniSim ↔ NeqSim automated comparison pipeline.

Reads a UniSim .usc file, converts it to NeqSim, runs both simulators,
and produces a structured comparison report suitable for the
``crossValidateModels`` MCP tool or standalone analysis.

This module closes the loop between the Python-side COM tools
(unisim_reader.py / unisim_writer.py) and the Java-side MCP runners
(CrossValidationRunner / ParametricStudyRunner).

Requirements:
    pip install pywin32 neqsim

Usage:
    from devtools.unisim_neqsim_bridge import UniSimNeqSimBridge

    bridge = UniSimNeqSimBridge()
    report = bridge.compare(r"C:\Models\GasPlant.usc")
    bridge.close()

    # Or as context manager:
    with UniSimNeqSimBridge() as bridge:
        report = bridge.compare("model.usc")
        optimized = bridge.optimize("model.usc", sweeps=[...], outputs=[...])
"""

import json
import logging
import os
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


@dataclass
class ComparisonResult:
    """Result of comparing a single stream between UniSim and NeqSim."""
    stream_name: str
    properties: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    # properties[prop_name] = {"unisim": val, "neqsim": val, "diff_pct": pct}

    @property
    def max_deviation_pct(self) -> float:
        if not self.properties:
            return 0.0
        return max(abs(p.get("diff_pct", 0.0)) for p in self.properties.values())

    @property
    def has_significant_deviation(self) -> bool:
        """True if any property deviates more than 5%."""
        return self.max_deviation_pct > 5.0


@dataclass
class BridgeReport:
    """Full comparison report between UniSim and NeqSim models."""
    usc_file: str
    conversion_warnings: List[str] = field(default_factory=list)
    conversion_assumptions: List[str] = field(default_factory=list)
    skipped_components: List[str] = field(default_factory=list)
    stream_comparisons: List[ComparisonResult] = field(default_factory=list)
    neqsim_json: Optional[str] = None
    overall_status: str = "unknown"  # "good", "acceptable", "review_needed"

    def to_dict(self) -> Dict:
        result = {
            "file": self.usc_file,
            "status": self.overall_status,
            "conversionWarnings": self.conversion_warnings,
            "conversionAssumptions": self.conversion_assumptions,
            "skippedComponents": self.skipped_components,
            "streamComparisons": [],
        }
        for sc in self.stream_comparisons:
            result["streamComparisons"].append({
                "stream": sc.stream_name,
                "maxDeviationPct": round(sc.max_deviation_pct, 2),
                "properties": sc.properties,
            })
        return result

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), indent=indent)

    def summary(self) -> str:
        lines = [
            f"Bridge Report: {os.path.basename(self.usc_file)}",
            f"  Status: {self.overall_status}",
            f"  Streams compared: {len(self.stream_comparisons)}",
        ]
        if self.skipped_components:
            lines.append(f"  Skipped components: {', '.join(self.skipped_components)}")
        if self.conversion_warnings:
            lines.append(f"  Warnings: {len(self.conversion_warnings)}")

        significant = [s for s in self.stream_comparisons if s.has_significant_deviation]
        if significant:
            lines.append(f"  Streams with >5% deviation: {len(significant)}")
            for s in significant:
                lines.append(f"    {s.stream_name}: max {s.max_deviation_pct:.1f}%")
        else:
            lines.append("  All streams within 5% tolerance")
        return "\n".join(lines)


class UniSimNeqSimBridge:
    """Orchestrates UniSim ↔ NeqSim comparison and optimization workflows.

    Combines:
    - ``devtools/unisim_reader.py`` for COM-based UniSim extraction
    - ``devtools/unisim_writer.py`` for writing back to .usc
    - NeqSim Python API for simulation
    - Structured JSON output compatible with MCP tools

    Usage:
        with UniSimNeqSimBridge() as bridge:
            report = bridge.compare("model.usc")
            print(report.summary())

            # Optimize and write back
            optimized = bridge.optimize("model.usc", sweeps=[...])
            bridge.write_back(optimized, "optimized.usc")
    """

    def __init__(self, visible: bool = False, template_path: Optional[str] = None):
        """Initialize the bridge.

        Args:
            visible: Whether to show UniSim windows during COM operations.
            template_path: Path to a licensed .usc template for the writer.
        """
        self._visible = visible
        self._template_path = template_path
        self._reader = None
        self._writer = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def close(self):
        """Close all COM connections."""
        if self._reader is not None:
            try:
                self._reader.close()
            except Exception:
                pass
            self._reader = None
        if self._writer is not None:
            try:
                self._writer.close()
            except Exception:
                pass
            self._writer = None

    def _get_reader(self):
        if self._reader is None:
            from devtools.unisim_reader import UniSimReader
            self._reader = UniSimReader(visible=self._visible)
        return self._reader

    def _get_writer(self):
        if self._writer is None:
            from devtools.unisim_writer import UniSimWriter
            self._writer = UniSimWriter(
                visible=self._visible,
                template_path=self._template_path,
            )
        return self._writer

    def compare(self, usc_path: str,
                properties: Optional[List[str]] = None) -> BridgeReport:
        """Compare a UniSim model against its NeqSim conversion.

        Args:
            usc_path: Path to the .usc file.
            properties: Stream properties to compare. Default: temperature,
                pressure, mass_flow, density, molecular_weight.

        Returns:
            BridgeReport with stream-by-stream comparison.
        """
        if properties is None:
            properties = [
                "temperature_C", "pressure_bara", "mass_flow_kgh",
                "mass_density_kgm3", "molecular_weight",
            ]

        from devtools.unisim_reader import UniSimToNeqSim

        reader = self._get_reader()
        model = reader.read(usc_path)
        converter = UniSimToNeqSim(model)
        neqsim_json = converter.to_json()

        report = BridgeReport(usc_file=usc_path)
        report.conversion_warnings = list(getattr(converter, 'warnings', []))
        report.conversion_assumptions = list(getattr(converter, 'assumptions', []))
        report.neqsim_json = json.dumps(neqsim_json)

        # Collect skipped components
        for w in report.conversion_warnings:
            if 'skipped' in w.lower() and 'component' in w.lower():
                report.skipped_components.append(w)

        # Run NeqSim model
        try:
            process = converter.build_and_run()
            if process is not None:
                self._compare_streams(model, process, properties, report)
        except Exception as e:
            report.conversion_warnings.append(f"NeqSim run failed: {e}")
            report.overall_status = "conversion_failed"
            return report

        # Determine overall status
        max_dev = max(
            (s.max_deviation_pct for s in report.stream_comparisons),
            default=0.0,
        )
        if max_dev <= 2.0:
            report.overall_status = "good"
        elif max_dev <= 5.0:
            report.overall_status = "acceptable"
        else:
            report.overall_status = "review_needed"

        return report

    def _compare_streams(self, unisim_model, neqsim_process, properties, report):
        """Compare all matching streams between UniSim extraction and NeqSim run."""
        if unisim_model.flowsheet is None:
            return

        # Map NeqSim stream names to stream objects
        neqsim_streams = {}
        try:
            auto = neqsim_process.getAutomation()
            units = auto.getUnitList()
            for unit_name in units:
                eq_type = auto.getEquipmentType(unit_name)
                if eq_type and 'Stream' in eq_type:
                    neqsim_streams[unit_name] = unit_name
        except Exception:
            pass

        for us_stream in unisim_model.flowsheet.material_streams:
            # Try to find matching NeqSim stream
            neqsim_name = self._find_matching_stream(us_stream.name, neqsim_streams)
            if neqsim_name is None:
                continue

            comp = ComparisonResult(stream_name=us_stream.name)

            for prop in properties:
                us_value = getattr(us_stream, prop, None)
                if us_value is None:
                    continue

                # Map property name to automation address
                neqsim_prop = self._map_property(prop)
                neqsim_unit = self._map_unit(prop)
                try:
                    nq_value_str = auto.getVariableValue(
                        f"{neqsim_name}.{neqsim_prop}", neqsim_unit)
                    nq_value = float(nq_value_str)

                    diff_pct = 0.0
                    if us_value != 0:
                        diff_pct = ((nq_value - us_value) / abs(us_value)) * 100.0

                    comp.properties[prop] = {
                        "unisim": round(us_value, 4),
                        "neqsim": round(nq_value, 4),
                        "diff_pct": round(diff_pct, 2),
                    }
                except Exception as e:
                    comp.properties[prop] = {
                        "unisim": round(us_value, 4),
                        "neqsim": None,
                        "error": str(e),
                    }

            if comp.properties:
                report.stream_comparisons.append(comp)

    def _find_matching_stream(self, unisim_name: str,
                               neqsim_streams: Dict[str, str]) -> Optional[str]:
        """Find a NeqSim stream matching a UniSim stream name."""
        # Direct match
        if unisim_name in neqsim_streams:
            return unisim_name
        # Case-insensitive
        lower_map = {k.lower(): k for k in neqsim_streams}
        if unisim_name.lower() in lower_map:
            return lower_map[unisim_name.lower()]
        return None

    def _map_property(self, prop: str) -> str:
        """Map UniSim property name to NeqSim automation address suffix."""
        mapping = {
            "temperature_C": "temperature",
            "pressure_bara": "pressure",
            "mass_flow_kgh": "flowRate",
            "mass_density_kgm3": "density",
            "molecular_weight": "molecularWeight",
            "molar_flow_kgmolh": "molarFlowRate",
            "enthalpy_kJkg": "enthalpy",
        }
        return mapping.get(prop, prop)

    def _map_unit(self, prop: str) -> str:
        """Map UniSim property name to NeqSim unit string."""
        mapping = {
            "temperature_C": "C",
            "pressure_bara": "bara",
            "mass_flow_kgh": "kg/hr",
            "mass_density_kgm3": "kg/m3",
            "molecular_weight": "",
            "molar_flow_kgmolh": "kgmol/hr",
            "enthalpy_kJkg": "kJ/kg",
        }
        return mapping.get(prop, "")

    def to_cross_validation_json(self, usc_path: str,
                                  models: List[str],
                                  compare_variables: List[Dict[str, str]],
                                  tolerances: Optional[Dict[str, float]] = None) -> str:
        """Convert a UniSim model to a CrossValidationRunner-compatible JSON.

        This bridges the Python COM layer with the Java MCP tool:
        1. Reads the .usc file
        2. Converts to NeqSim process JSON
        3. Wraps it in the crossValidateModels input format

        Args:
            usc_path: Path to the .usc file.
            models: List of EoS names to compare.
            compare_variables: List of {"address": ..., "unit": ...} dicts.
            tolerances: Optional tolerance percentages per variable type.

        Returns:
            JSON string ready for crossValidateModels MCP tool.
        """
        from devtools.unisim_reader import UniSimToNeqSim

        reader = self._get_reader()
        model = reader.read(usc_path)
        converter = UniSimToNeqSim(model)
        neqsim_json = converter.to_json()

        cross_val_input = {
            "baseProcess": neqsim_json,
            "models": models,
            "compareVariables": compare_variables,
        }
        if tolerances:
            cross_val_input["tolerances"] = tolerances

        return json.dumps(cross_val_input, indent=2)

    def to_parametric_study_json(self, usc_path: str,
                                  sweeps: List[Dict],
                                  outputs: List[Dict[str, str]],
                                  mode: str = "one_at_a_time") -> str:
        """Convert a UniSim model to a ParametricStudyRunner-compatible JSON.

        Args:
            usc_path: Path to the .usc file.
            sweeps: List of sweep definitions.
            outputs: List of {"address": ..., "unit": ...} output specs.
            mode: "one_at_a_time" or "full_factorial".

        Returns:
            JSON string ready for runParametricStudy MCP tool.
        """
        from devtools.unisim_reader import UniSimToNeqSim

        reader = self._get_reader()
        model = reader.read(usc_path)
        converter = UniSimToNeqSim(model)
        neqsim_json = converter.to_json()

        study_input = {
            "baseProcess": neqsim_json,
            "sweeps": sweeps,
            "outputs": outputs,
            "mode": mode,
        }
        return json.dumps(study_input, indent=2)

    def write_back(self, neqsim_json_str: str, save_path: str) -> str:
        """Write a NeqSim JSON model back to a UniSim .usc file.

        Args:
            neqsim_json_str: NeqSim process JSON string.
            save_path: Output path for the .usc file.

        Returns:
            The absolute path of the saved .usc file.
        """
        writer = self._get_writer()
        writer.build_from_json(neqsim_json_str, save_path=save_path)
        return os.path.abspath(save_path)
