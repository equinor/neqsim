"""Canonical SURF study dataset (``surf_study_dataset.v1``): assembly from solved cases, facts, hash and the token resolver.

Every number in a drawing annotation, datasheet entry, register row, MTO line, cost figure and report sentence resolves to this
dataset (``[[ds:path|fmt|unit=..]]`` or ``[[fact:key]]``) or to a sourced input. Nothing here calls the solver or the JVM.
"""
import datetime
import hashlib
import json
import math
import re

import fa_units
import surf_design
import surf_quantities as Q
import surf_spec

SCHEMA = "surf_study_dataset.v1"
TOOL_VERSION = "1.0"
TOKEN_RE = re.compile(r"\[\[([^\]|]+)(?:\|([^\]|]*))?(?:\|([^\]]*))?\]\]")


def canonical_hash(obj, n=16):
    return hashlib.sha256(json.dumps(obj, sort_keys=True, default=str).encode("utf-8")).hexdigest()[:n]


def dotget(obj, path):
    """Nested lookup by dotted path; integer path parts index lists."""
    cur = obj
    for part in path.split("."):
        cur = cur[int(part)] if isinstance(cur, list) else cur[part]
    return cur


def _strip_volatile(obj):
    drop = {"cache", "timings_s", "created", "wall_s", "run_id"}
    if isinstance(obj, dict):
        return {k: _strip_volatile(v) for k, v in obj.items() if k not in drop}
    if isinstance(obj, list):
        return [_strip_volatile(v) for v in obj]
    return obj


def dataset_hash(ds):
    """Hash of the content that the package shows (excludes validation, artifacts, drawings, timings, cache flags and the hash itself)."""
    skip = {"validation", "drawings", "artifacts", "timings_s", "mapping"}
    body = _strip_volatile({k: v for k, v in ds.items() if k not in skip})
    body["meta"] = {k: v for k, v in body["meta"].items() if k not in ("dataset_hash",)}
    return canonical_hash(body, 24)


def build_dataset(spec, model, input_findings, run_data, standards, neqsim_version, timings, spec_path_hash):
    """Assemble the canonical dataset from the specification and the retained results (see ``surf_study.run_stage``).

    ``run_data`` holds ``results[concept][case]`` (production/injection records), ``umbilical``, ``curves``, ``structural[concept]``,
    ``cost[concept]``, ``mesh``, ``model_manifest[concept]`` and ``handoffs``.
    """
    concepts = list(model["concepts"])
    cases = list(model["cases"])
    results, criteria_by_concept, hand, diam = {}, {}, {}, {}
    for cid in concepts:
        results[cid] = {}
        derived_by_case = {}
        for case in cases:
            prod, inj = run_data["results"][cid][case]["production"], run_data["results"][cid][case]["injection"]
            d = surf_design.derive_case(spec, model, cid, case, prod, inj, run_data["umbilical"], run_data["curves"])
            results[cid][case] = {"production": prod, "injection": inj, "derived": d}
            derived_by_case[case] = d
        criteria_by_concept[cid] = surf_design.evaluate_criteria(spec, derived_by_case)
        design_case = next(c for c, v in model["cases"].items() if v["role"] == "design")
        hand[cid] = surf_design.hand_checks(spec, model, cid, design_case, results[cid][design_case]["production"], results[cid][design_case]["injection"], run_data["umbilical"])
        diam[cid] = surf_design.hydraulic_diameter_checks(spec, model, cid)
    registers = {cid: {"lines": Q.line_register(spec, model, cid), "equipment": Q.equipment_register(spec, model, cid), "mto": Q.mto(spec, model, cid)} for cid in concepts}
    geometry = {cid: {eid: {"points": e["points"], "chainage_m": e["chainage_m"]} for eid, e in surf_spec.case_for_concept(model, spec, cid).items()} for cid in concepts}
    design_case_id = next(c for c, v in model["cases"].items() if v["role"] == "design")
    for cid in concepts:
        registers[cid]["pumps"] = [{"id": eid, "tag": e["tag"], "service": e["service"], "from": e["from"], "to": e["to"], "efficiency": e["pump"]["efficiency"],
                                    "discharge_pressure_bara": model["cases"][design_case_id]["injection_pump_discharge_bara"]}
                                   for eid, e in surf_spec.case_for_concept(model, spec, cid).items() if e["kind"] == "pump"]
    ports = {eid: {"from": e["from_port"], "to": e["to_port"]} for eid, e in model["edges"].items()}
    for cid in concepts:
        registers[cid]["reconciliation"] = Q.reconcile(spec, model, cid, registers[cid]["lines"], registers[cid]["equipment"], run_data["cost"][cid])
    comparison = surf_design.concept_comparison(spec, concepts, criteria_by_concept, run_data["cost"])
    fluid_checks = surf_design.water_property_check(results[concepts[0]][next(c for c, v in model["cases"].items() if v["role"] == "design")]["injection"])
    selected = comparison["selected"]
    ref = selected or concepts[0]
    cooldown_check = {}
    for cid in concepts:
        for case in cases:
            cd = results[cid][case]["derived"]["cooldown"]
            if cd and cd.get("time_h") is not None:
                cooldown_check["%s/%s" % (cid, case)] = {"analytic_h": cd["time_h"], "rk4_h": cd["rk4_time_h"], "rel_diff": abs(cd["time_h"] - cd["rk4_time_h"]) / max(cd["time_h"], 1e-9)}
    ds = {
        "schema": SCHEMA,
        "meta": {"title": spec["document"]["title"], "document_number": spec["document"]["document_number"], "revision": spec["document"]["revision"],
                 "created": datetime.datetime.now().replace(microsecond=0).isoformat(), "tool_version": TOOL_VERSION, "neqsim_version": neqsim_version,
                 "spec_hash": spec_path_hash, "study_id": spec["study"]["id"], "synthetic": bool(spec["study"].get("synthetic"))},
        "document": spec["document"], "study": spec["study"], "conventions": spec["conventions"], "sources": spec["sources"], "host": spec["host"],
        "fluids": {k: {"role": v["role"], "eos": v["eos"], "components": v["components"], "source": v["source"], "input_class": v["input_class"]} for k, v in spec["fluids"].items()},
        "structures": list(model["structures"].values()), "concepts": {k: {kk: vv for kk, vv in v.items() if kk != "overrides"} for k, v in model["concepts"].items()},
        "cases": list(model["cases"].values()), "selected_concept": selected, "reference_concept": ref, "comparison": comparison,
        "registers": registers, "geometry": geometry, "ports": ports, "fluid_checks": fluid_checks, "results": results, "criteria": criteria_by_concept, "hand_checks": hand,
        "diameter_checks": diam, "mesh_sensitivity": run_data.get("mesh"),
        "cooldown_checks": cooldown_check, "umbilical": {"spec": spec["umbilical"], "results": run_data["umbilical"], "services": Q.service_register(spec)},
        "structural": run_data["structural"], "hydrate_curves": run_data["curves"], "cost": run_data["cost"], "standards": standards,
        "criteria_spec": spec["acceptance_criteria"], "solver": spec["solver"], "handoffs": run_data["handoffs"], "assumptions": spec["assumptions"],
        "data_gaps": spec["data_gaps"], "exclusions": spec.get("exclusions", []), "findings": input_findings, "timings_s": timings, "drawings": [], "validation": None, "artifacts": {},
        "mapping": run_data["mapping"], "review": spec.get("review")}
    ds["facts"] = compute_facts(ds)
    ds["meta"]["dataset_hash"] = dataset_hash(ds)
    return ds


def compute_facts(ds):
    """Counts and roll-ups for ``[[fact:key]]`` tokens (reference concept)."""
    ref = ds["reference_concept"]
    regs = ds["registers"][ref]
    kinds = regs["mto"]["equipment_counts"]
    f = {"n_concepts": len(ds["concepts"]), "n_cases": len(ds["cases"]), "reference_concept": ref, "selected_concept": ds["selected_concept"] or "none",
         "n_lines": len(regs["lines"]), "n_nodes": len(regs["equipment"]), "n_prod_wells": kinds.get("production_well", 0), "n_inj_wells": kinds.get("injection_well", 0),
         "n_templates": len(ds["structures"]), "n_services": len(ds["umbilical"]["services"]), "n_criteria": len(ds["criteria"][ref]),
         "total_steel_t": regs["mto"]["total_steel_kg"] / 1000.0, "total_insulation_m3": regs["mto"]["total_insulation_m3"],
         "production_line_length_km": sum(r["installed_length_m"] for r in regs["lines"] if r["service"] == "production") / 1000.0,
         "injection_line_length_km": sum(r["installed_length_m"] for r in regs["lines"] if r["service"] == "injection") / 1000.0,
         "n_data_gaps": len(ds["data_gaps"]), "n_assumptions": len(ds["assumptions"]), "n_handoffs": len(ds["handoffs"]),
         "umbilical_length_km": ds["umbilical"]["spec"]["length_m"] / 1000.0}
    for cid in ds["concepts"]:
        crit = ds["criteria"][cid]
        f["n_pass_" + cid] = sum(1 for c in crit if c["overall"] == "PASS")
        f["n_fail_" + cid] = sum(1 for c in crit if c["overall"] == "FAIL")
        f["n_open_" + cid] = sum(1 for c in crit if c["overall"] == "NOT ASSESSED")
        f["capex_musd_" + cid] = (ds["cost"][cid]["total_usd"] or 0.0) / 1.0e6
        f["feasibility_" + cid] = next(r["feasibility"] for r in ds["comparison"]["rows"] if r["concept"] == cid)
        f["n_hand_pass_" + cid] = sum(1 for c in ds["hand_checks"][cid] if c["status"] == "PASS")
        f["n_hand_" + cid] = len(ds["hand_checks"][cid])
        f["n_structural_screened_" + cid] = sum(1 for v in ds["structural"][cid].values() if v["status"].startswith("screened"))
        f["n_structural_open_" + cid] = sum(1 for v in ds["structural"][cid].values() if not v["status"].startswith("screened"))
    f["capex_delta_musd"] = abs(f.get("capex_musd_B", 0.0) - f.get("capex_musd_A", 0.0)) if "B" in ds["concepts"] and "A" in ds["concepts"] else 0.0
    return f


class SurfResolver:
    """Resolves ``[[...]]`` tokens against the dataset; records every use, error and unit problem."""

    def __init__(self, ds):
        self.ds = ds
        self.used, self.errors, self.unit_errors = [], [], []

    def value(self, ref):
        kind, _, path = ref.partition(":")
        if kind == "fact":
            return self.ds["facts"][path], None
        if kind == "ds":
            return dotget(self.ds, path), fa_units.suffix_unit(path.split(".")[-1])
        raise KeyError(ref)

    def text(self, ref, fmt=None, unit_spec=None):
        try:
            val, unit = self.value(ref)
        except (KeyError, IndexError, TypeError, ValueError):
            self.errors.append(ref)
            return "n/a"
        if unit_spec:
            want = unit_spec.replace("unit=", "").strip()
            try:
                if _isnum(val):
                    if unit is None:
                        raise fa_units.UnitError("no unit is recorded for %s" % ref)
                    val, unit = fa_units.convert(val, unit, want), want
            except fa_units.UnitError as exc:
                self.unit_errors.append({"ref": ref, "message": str(exc)})
                return "n/a"
        if val is None:
            self.used.append({"ref": ref, "value": None, "text": "unavailable", "unit": unit, "format": fmt})
            return "unavailable"
        if isinstance(val, bool):
            out = "yes" if val else "no"
        elif _isnum(val):
            if not math.isfinite(val):
                out = "n/a"
            elif fmt and fmt.startswith("sig"):
                from psr_report import significant

                out = significant(float(val), int(fmt[3:]))
            elif fmt:
                out = format(val, fmt)
            else:
                out = "%d" % val if float(val).is_integer() and abs(val) < 1e7 else ("%.4g" % val)
        else:
            out = str(val)
        self.used.append({"ref": ref, "value": val if isinstance(val, (int, float, str, bool)) else str(val), "text": out, "unit": unit, "format": fmt})
        return out

    def expand(self, text):
        return TOKEN_RE.sub(lambda m: self.text(m.group(1), (m.group(2) or None), (m.group(3) or None)), str(text or ""))


def _isnum(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)
