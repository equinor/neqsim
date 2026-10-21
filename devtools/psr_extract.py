"""Extract a normalised, case-aware data set from a solved NeqSim process model.

Part of the ``process-study-report`` toolkit. The extractor walks a solved
``ProcessModel`` (several areas) or a single ``ProcessSystem``, discovers every
material stream and every unit operation, assigns deterministic stream numbers
and writes the numbers a process-study report needs: temperature, pressure,
mass/molar/volumetric flow, phase split, phase properties, enthalpy, overall
and phase compositions, unit duties/powers/pressure drops and pseudo-component
properties.

The extractor never invents data: a quantity that the model cannot provide is
stored as ``None`` and reported as ``n/a`` by the table writers.

Usage (Python, JVM already started through ``jneqsim``)::

    data = extract_process(model, case_id="A", case_label="Calibration window")
    merged = merge_cases([data_a, data_b])
    write_outputs(merged, "out/data")
"""
import csv
import json
import math
import os
import time

SCHEMA = "neqsim.process_study_report.data/1"
R_KJ_KMOL_K = 8.314462618
DEFAULT_STD_T_C = 15.0
DEFAULT_STD_P_BARA = 1.01325

# (keyword in class chain, category) - first match wins, order matters.
_CATEGORY_KEYWORDS = [
    ("Stream", "stream"),
    ("Recycle", "recycle"),
    ("Adjuster", "logic"),
    ("Calculator", "logic"),
    ("SetPoint", "logic"),
    ("Ejector", "ejector"),
    ("DistillationColumn", "column"),
    ("Column", "column"),
    ("Compressor", "compressor"),
    ("Expander", "expander"),
    ("Pump", "pump"),
    ("Splitter", "splitter"),
    ("Mixer", "mixer"),
    ("Valve", "valve"),
    ("Separator", "separator"),
    ("Scrubber", "separator"),
    ("Tank", "separator"),
    ("HeatExchanger", "exchanger"),
    ("Cooler", "cooler"),
    ("Heater", "heater"),
    ("Pipe", "pipe"),
    ("Reactor", "reactor"),
    ("Absorber", "column"),
]


def _clean(value, ndigits=None):
    """Return a JSON-safe float (or None for NaN/inf/non-numeric)."""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(number) or math.isinf(number):
        return None
    return number


def _try(obj, names, *args):
    """Call the first existing method in ``names``; return None on any failure."""
    for name in names:
        method = getattr(obj, name, None)
        if method is None:
            continue
        try:
            return method(*args)
        except Exception:  # noqa: BLE001 - JPype raises many exception types
            continue
    return None


def class_chain(java_obj):
    """Simple class names of an object's class and all superclasses."""
    names = []
    cls = java_obj.getClass()
    while cls is not None:
        names.append(str(cls.getSimpleName()))
        cls = cls.getSuperclass()
    return names


def classify(chain):
    """Map a class chain onto a coarse equipment category."""
    for simple in chain:
        if simple in ("Stream", "EquipmentStream", "VirtualStream", "NeqStream"):
            return "stream"
    for keyword, category in _CATEGORY_KEYWORDS:
        if keyword == "Stream":
            continue
        for simple in chain:
            if keyword in simple:
                return category
    return "other"


class _Registry:
    """Identity registry for Java stream objects (identityHashCode based)."""

    def __init__(self):
        import jpype

        self._system = jpype.JClass("java.lang.System")
        self.ids = {}
        self.objects = []

    def key(self, java_obj):
        return int(self._system.identityHashCode(java_obj))

    def get(self, java_obj):
        return self.ids.get(self.key(java_obj))

    def add(self, java_obj, sid):
        self.ids[self.key(java_obj)] = sid
        self.objects.append(java_obj)


def _area_systems(process):
    """Return ordered (area_name, ProcessSystem) pairs for a model or system."""
    if hasattr(process, "getAreaNames"):
        names = [str(n) for n in process.getAreaNames()]
        return [(n, process.get(n)) for n in names]
    return [("process", process)]


def _stream_fluid(stream):
    fluid = _try(stream, ["getFluid", "getThermoSystem"])
    return fluid


def stream_number(index, width):
    """Deterministic stream number such as ``S001``."""
    return "S%0*d" % (width, index)


def discover_topology(process):
    """Discover units and streams of a model. Returns (units, streams, areas).

    ``units`` is a list of dict records (tag, area, class, category, inlet and
    outlet stream ids); ``streams`` a list of dict records plus the Java object
    under the private key ``_obj``.
    """
    reg = _Registry()
    areas = []
    raw_units = []
    tags_seen = {}
    for area_name, system in _area_systems(process):
        area_units = []
        for unit in system.getUnitOperations():
            name = str(unit.getName())
            chain = class_chain(unit)
            category = classify(chain)
            tag = name
            if tag in tags_seen:
                tag = "%s@%s" % (name, area_name)
            tags_seen[tag] = True
            ins = list(_try(unit, ["getInletStreams"]) or [])
            outs = list(_try(unit, ["getOutletStreams"]) or [])
            raw_units.append({"tag": tag, "name": name, "area": area_name, "java_class": chain[0],
                              "category": category, "_obj": unit, "_ins": ins, "_outs": outs})
            area_units.append(tag)
        areas.append({"name": area_name, "units": area_units})

    # First pass: stream-type units register themselves, then inlets/outlets of the rest.
    streams = []
    order = []

    def register(obj):
        if reg.get(obj) is not None:
            return reg.get(obj)
        sid = len(streams) + 1
        reg.add(obj, sid)
        streams.append({"_obj": obj, "name": str(obj.getName()), "index": sid})
        order.append(sid)
        return sid

    for unit in raw_units:
        if unit["category"] == "stream":
            register(unit["_obj"])
            continue
        for obj in unit["_ins"]:
            register(obj)
        for obj in unit["_outs"]:
            register(obj)
    width = max(3, len(str(len(streams))))
    for rec in streams:
        rec["id"] = stream_number(rec["index"], width)

    units = []
    for unit in raw_units:
        if unit["category"] == "stream":
            continue
        rec = {k: v for k, v in unit.items() if not k.startswith("_")}
        rec["inlet_streams"] = [stream_number(reg.get(o), width) for o in unit["_ins"]]
        rec["outlet_streams"] = [stream_number(reg.get(o), width) for o in unit["_outs"]]
        rec["_obj"] = unit["_obj"]
        units.append(rec)

    by_id = {s["id"]: s for s in streams}
    for s in streams:
        s["producers"] = []
        s["consumers"] = []
    for u in units:
        for sid in u["outlet_streams"]:
            by_id[sid]["producers"].append(u["tag"])
        for sid in u["inlet_streams"]:
            by_id[sid]["consumers"].append(u["tag"])
    area_of = {}
    for u in units:
        area_of[u["tag"]] = u["area"]
    for s in streams:
        s["source"] = s["producers"][0] if s["producers"] else None
        s["destination"] = s["consumers"][0] if s["consumers"] else None
        s["area"] = area_of.get(s["source"] or (s["consumers"][0] if s["consumers"] else ""), areas[0]["name"] if areas else "")
        if not s["producers"] and s["consumers"]:
            s["kind"] = "feed"
        elif s["producers"] and not s["consumers"]:
            s["kind"] = "product"
        elif not s["producers"] and not s["consumers"]:
            s["kind"] = "isolated"
        else:
            s["kind"] = "internal"
    _assign_labels(streams)
    return units, streams, areas


_GENERIC_ROLES = (("gasoutstream", "gas out"), ("liquidoutstream", "liquid out"), ("wateroutstream", "water out"),
                  ("oiloutstream", "oil out"), ("outstream", "out"), ("out stream", "out"),
                  ("mixed stream", "out"), ("outletstream", "out"), ("splitstream", "split out"))


def _assign_labels(streams):
    """Give every stream a unique descriptive label (NeqSim default names are generic)."""
    used = {}
    for s in streams:
        raw = s["name"]
        role = None
        low = raw.strip().lower()
        for key, text in _GENERIC_ROLES:
            if low == key:
                role = text
                break
        label = raw
        if role is not None:
            anchor = s["source"] or s["destination"] or "stream"
            label = "%s %s" % (anchor, role)
        used.setdefault(label, []).append(s)
    for label, group in used.items():
        if len(group) == 1:
            group[0]["label"] = label
        else:
            for n, s in enumerate(group, 1):
                s["label"] = "%s (%s)" % (label, s["id"])


# ── component data ───────────────────────────────────────────

class ComponentRegistry:
    """Union of the components of all stream fluids with collision handling.

    Areas of a multi-area model may use different component sets or define a pseudo-component of the same name
    with different properties. A component is identified by name and (molar mass, Tc, Pc, acentric factor); a
    second variant of a name gets the key ``"name [variant 2]"`` so that flows are never merged by accident.
    """

    REL_TOL = 1.0e-6

    def __init__(self):
        self.rows = []
        self._variants = {}
        self.conflicts = []

    @staticmethod
    def _props(comp):
        return (_clean(comp.getMolarMass() * 1000.0), _clean(_try(comp, ["getTC"])), _clean(_try(comp, ["getPC"])),
                _clean(_try(comp, ["getAcentricFactor"])))

    @classmethod
    def _same(cls, a, b):
        for x, y in zip(a, b):
            if x is None or y is None:
                if x is not y:
                    return False
                continue
            if abs(x - y) > cls.REL_TOL * max(abs(x), abs(y), 1.0):
                return False
        return True

    def key(self, comp):
        """Unique key of a Java component object (registering it when new)."""
        name = str(comp.getComponentName())
        props = self._props(comp)
        variants = self._variants.setdefault(name, [])
        for vprops, vkey in variants:
            if self._same(props, vprops):
                return vkey
        vkey = name if not variants else "%s [variant %d]" % (name, len(variants) + 1)
        variants.append((props, vkey))
        if len(variants) > 1:
            self.conflicts.append("component %s is defined with different properties in different streams; kept apart as '%s'"
                                  % (name, vkey))
        row = {"index": len(self.rows), "name": vkey, "base_name": name}
        row.update({
            "mw_g_mol": props[0], "tc_k": props[1], "pc_bara": props[2], "omega": props[3],
            "tb_k": _clean(_try(comp, ["getNormalBoilingPoint"])),
            "rho_liq_g_cm3": _clean(_try(comp, ["getNormalLiquidDensity"])),
            "is_tbp": bool(_try(comp, ["isIsTBPfraction"]) or False),
            "is_plus": bool(_try(comp, ["isIsPlusFraction"]) or False),
        })
        self.rows.append(row)
        return vkey

    def scan(self, fluid):
        """Register every component of ``fluid``; returns the list of keys in fluid order."""
        phase = fluid.getPhase(0)
        return [self.key(phase.getComponent(i)) for i in range(int(fluid.getNumberOfComponents()))]

    def keys(self):
        return [r["name"] for r in self.rows]


def component_table(fluid):
    """Pure-component / pseudo-component properties of a fluid (SI-derived units)."""
    reg = ComponentRegistry()
    reg.scan(fluid)
    return reg.rows


# ── stream properties ────────────────────────────────────────

def _phase_name(phase):
    return str(phase.getPhaseTypeName())


def _std_flash(fluid, std_t_c, std_p_bara):
    """Flash a clone at standard conditions; return gas/oil/aqueous volumes (m3/h)."""
    import jpype  # noqa: F401  (JVM must be running)
    from jneqsim import neqsim

    clone = fluid.clone()
    clone.setTemperature(std_t_c + 273.15)
    clone.setPressure(std_p_bara)
    try:
        ops = neqsim.thermodynamicoperations.ThermodynamicOperations(clone)
        ops.TPflash()
        clone.initProperties()
    except Exception:  # noqa: BLE001
        return None
    out = {"gas": 0.0, "oil": 0.0, "aqueous": 0.0}
    total_kg_h = clone.getFlowRate("kg/hr")
    for i in range(int(clone.getNumberOfPhases())):
        ph = clone.getPhase(i)
        name = _phase_name(ph)
        dens = _clean(ph.getDensity("kg/m3"))
        beta = _clean(ph.getBeta())
        if dens is None or beta is None or dens <= 0 or total_kg_h is None:
            return None
        mass = _clean(ph.getFlowRate("kg/hr"))
        if mass is None:
            mass = beta * total_kg_h
        key = name if name in out else ("oil" if name not in ("gas", "aqueous") else name)
        out[key] = out.get(key, 0.0) + mass / dens
    return out


def stream_results(stream, components, std_t_c=DEFAULT_STD_T_C, std_p_bara=DEFAULT_STD_P_BARA,
                   std_flash=True, registry=None):
    """Collect all reportable results of one solved stream (a plain dict).

    With a :class:`ComponentRegistry` the composition dicts are keyed by the registry key and carry an exact
    zero for every registered component the stream fluid does not contain.
    """
    fluid = _stream_fluid(stream)
    res = {"available": False}
    if fluid is None:
        return res
    try:
        fluid.initProperties()
    except Exception:  # noqa: BLE001
        pass
    try:
        t_k = float(fluid.getTemperature())
        p_bara = float(fluid.getPressure())
        mass_kg_h = float(fluid.getFlowRate("kg/hr"))
        n_phases = int(fluid.getNumberOfPhases())
    except Exception:  # noqa: BLE001
        return res
    mw = _clean(fluid.getMolarMass() * 1000.0)
    mole_kmol_h = mass_kg_h / mw if mw else None
    res.update({
        "available": True,
        "t_c": _clean(t_k - 273.15),
        "p_bara": _clean(p_bara),
        "p_barg": _clean(p_bara - 1.01325),
        "mass_kg_h": _clean(mass_kg_h),
        "mole_kmol_h": _clean(mole_kmol_h),
        "mw_g_mol": mw,
        "std_vol_ideal_sm3_h": _clean(mole_kmol_h * R_KJ_KMOL_K * (std_t_c + 273.15) / (std_p_bara * 100.0))
        if mole_kmol_h is not None else None,
        "n_phases": n_phases,
        "h_kj_kg": _clean(_try(fluid, ["getEnthalpy"], "kJ/kg")),
        "H_kw": _clean((_try(fluid, ["getEnthalpy"]) or 0.0) / 1000.0) if _try(fluid, ["getEnthalpy"]) is not None else None,
        "s_kj_kg_k": _clean(_try(fluid, ["getEntropy"], "kJ/kgK")),
        "cp_kj_kg_k": _clean(_try(fluid, ["getCp"], "kJ/kgK")),
        "density_kg_m3": _clean(_try(fluid, ["getDensity"], "kg/m3")),
        "vol_actual_m3_h": _clean(_try(fluid, ["getFlowRate"], "m3/hr")),
    })
    z, w, flow_kmol, flow_kg = {}, {}, {}, {}
    phase0 = fluid.getPhase(0)
    n_comp = int(fluid.getNumberOfComponents())
    for i in range(n_comp):
        comp = phase0.getComponent(i)
        name = registry.key(comp) if registry is not None else str(comp.getComponentName())
        zi = _clean(comp.getz()) or 0.0
        z[name] = zi
        mwi = _clean(comp.getMolarMass() * 1000.0) or 0.0
        flow_kmol[name] = zi * mole_kmol_h if mole_kmol_h is not None else None
        flow_kg[name] = zi * mole_kmol_h * mwi if mole_kmol_h is not None else None
    if registry is not None:
        zero_flow_value = 0.0 if mole_kmol_h is not None else None
        for name in registry.keys():
            if name not in z:
                z[name], flow_kmol[name], flow_kg[name] = 0.0, zero_flow_value, zero_flow_value
    total_kg = sum(v for v in flow_kg.values() if v is not None)
    for name in z:
        w[name] = (flow_kg[name] / total_kg) if (total_kg and flow_kg[name] is not None) else None
    if abs(sum(z.values()) - 1.0) > 1.0e-3:
        # Degenerate fluid object (no moles): composition and phases are undefined, not zero.
        res.update({"zero_flow": True, "z": None, "w": None, "flow_kmol_h": None, "flow_kg_h": None, "phases": [],
                    "state": "no flow (composition undefined)", "phase_beta_sum_raw": None})
        if std_flash:
            res["std_flash_m3_h"] = None
        return res
    res["zero_flow"] = False
    res["z"] = z
    res["w"] = w
    res["flow_kmol_h"] = flow_kmol
    res["flow_kg_h"] = flow_kg
    raw = []
    for i in range(n_phases):
        ph = fluid.getPhase(i)
        raw.append((ph, _clean(ph.getBeta()), _clean(ph.getMolarMass() * 1000.0)))
    beta_sum = sum(b for _, b, _ in raw if b is not None)
    # A single phase carries the whole stream; several phases are normalised so the fractions close to 1.
    # The raw sum is kept so that stale or inconsistent solver states stay visible to the validator.
    res["phase_beta_sum_raw"] = _clean(beta_sum)
    betas = [1.0 if n_phases == 1 else ((b / beta_sum) if (b is not None and beta_sum > 0) else None) for _, b, _ in raw]
    weights = [(b * m) if (b is not None and m) else None for b, (_, _, m) in zip(betas, raw)]
    weight_sum = sum(w_ for w_ in weights if w_ is not None)
    phases = []
    for (ph, _b, mw_p), beta, weight in zip(raw, betas, weights):
        dens = _clean(ph.getDensity("kg/m3"))
        mass_frac = (weight / weight_sum) if (weight is not None and weight_sum > 0) else None
        mass_p = mass_frac * mass_kg_h if mass_frac is not None else None
        x = {}
        for j in range(n_comp):
            comp = ph.getComponent(j)
            x[registry.key(comp) if registry is not None else str(comp.getComponentName())] = _clean(comp.getx())
        if registry is not None:
            for name in registry.keys():
                x.setdefault(name, 0.0)
        x_sum = sum(v for v in x.values() if v is not None)
        if abs(x_sum - 1.0) > 1.0e-3:
            x = None  # degenerate phase object: composition undefined, not zero
        phases.append({
            "type": _phase_name(ph),
            "mole_fraction": beta,
            "mass_fraction": _clean(mass_frac),
            "mass_kg_h": _clean(mass_p),
            "mole_kmol_h": _clean(beta * mole_kmol_h) if beta is not None and mole_kmol_h is not None else None,
            "density_kg_m3": dens,
            "vol_actual_m3_h": _clean(mass_p / dens) if (mass_p is not None and dens) else None,
            "mw_g_mol": mw_p,
            "z_factor": _clean(_try(ph, ["getZ"])),
            "viscosity_cp": _clean((_try(ph, ["getViscosity"]) or 0.0) * 1000.0) if _try(ph, ["getViscosity"]) is not None else None,
            "x": x,
        })
    res["phases"] = phases
    res["state"] = "+".join(p["type"] for p in phases) if phases else "n/a"
    if std_flash:
        res["std_flash_m3_h"] = _std_flash(fluid, std_t_c, std_p_bara)
    return res


# ── unit results ─────────────────────────────────────────────

def _watts_to_kw(value):
    value = _clean(value)
    return None if value is None else value / 1000.0


def unit_results(unit_rec, stream_res):
    """Per-case results of a unit operation (duty, power, pressure drop, efficiency)."""
    obj = unit_rec["_obj"]
    category = unit_rec["category"]
    res = {"duty_kw": None, "power_kw": None, "dp_bar": None, "extras": {}}
    ins = [stream_res.get(s) for s in unit_rec["inlet_streams"]]
    outs = [stream_res.get(s) for s in unit_rec["outlet_streams"]]
    ins = [r for r in ins if r and r.get("available")]
    outs = [r for r in outs if r and r.get("available")]
    if category in ("heater", "cooler", "exchanger"):
        res["duty_kw"] = _watts_to_kw(_try(obj, ["getDuty"]))
    if category in ("compressor", "pump", "expander"):
        res["power_kw"] = _watts_to_kw(_try(obj, ["getPower"]))
    if ins and outs and category in ("valve", "heater", "cooler", "exchanger", "pipe", "compressor",
                                     "pump", "expander", "separator"):
        p_in, p_out = ins[0].get("p_bara"), outs[0].get("p_bara")
        if p_in is not None and p_out is not None:
            res["dp_bar"] = p_in - p_out
    extras = res["extras"]
    if category == "compressor":
        extras["isentropic_efficiency"] = _clean(_try(obj, ["getIsentropicEfficiency"]))
        extras["polytropic_efficiency"] = _clean(_try(obj, ["getPolytropicEfficiency"]))
        extras["polytropic_head_kj_kg"] = _clean(_try(obj, ["getPolytropicHead"], "kJ/kg"))
        extras["speed_rpm"] = _clean(_try(obj, ["getSpeed"]))
        extras["pressure_ratio"] = (outs[0]["p_bara"] / ins[0]["p_bara"]) if ins and outs and ins[0].get("p_bara") else None
    if category == "pump":
        extras["isentropic_efficiency"] = _clean(_try(obj, ["getIsentropicEfficiency"]))
    if category == "valve":
        extras["opening_percent"] = _clean(_try(obj, ["getPercentValveOpening"]))
    for key in list(extras):
        if extras[key] is None:
            del extras[key]
    return res


def unit_design(unit_rec):
    """Static design data that the model carries (separator geometry etc.)."""
    obj = unit_rec["_obj"]
    out = {}
    for label, names in (("internal_diameter_m", ["getInternalDiameter"]),
                         ("length_m", ["getSeparatorLength", "getLength"]),
                         ("volume_m3", ["getVolume"])):
        val = _clean(_try(obj, names))
        if val is not None and val > 0:
            out[label] = val
    return out


# ── public API ───────────────────────────────────────────────

def extract_process(process, case_id="A", case_label="", std_t_c=DEFAULT_STD_T_C,
                    std_p_bara=DEFAULT_STD_P_BARA, std_flash=True, model_name="process",
                    progress=None):
    """Extract topology and one set of results from a solved process model.

    Args:
        process: solved ``ProcessModel`` or ``ProcessSystem`` (Java object).
        case_id: short operating-case identifier, e.g. ``"A"``.
        case_label: human readable case description.
        std_t_c: standard-condition temperature in degrees Celsius.
        std_p_bara: standard-condition pressure in bara.
        std_flash: also flash every stream at standard conditions.
        model_name: model identifier written to the metadata.
        progress: optional callable receiving progress strings.

    Returns:
        A dict following :data:`SCHEMA` with a single case.
    """
    units, streams, areas = discover_topology(process)
    first = None
    registry = ComponentRegistry()
    eos_names = []
    mixing_rules = []
    for s in streams:
        fluid = _stream_fluid(s["_obj"])
        if fluid is None:
            continue
        if first is None:
            first = fluid
        try:
            registry.scan(fluid)
        except Exception:  # noqa: BLE001 - a stream without a usable fluid is reported as unavailable later
            continue
        s["eos"] = _eos_name(fluid)
        if s["eos"] not in eos_names:
            eos_names.append(s["eos"])
        rule = _mixing_rule_name(fluid)
        if rule and rule not in mixing_rules:
            mixing_rules.append(rule)
    components = registry.rows
    stream_res = {}
    for n, s in enumerate(streams):
        stream_res[s["id"]] = stream_results(s["_obj"], components, std_t_c, std_p_bara, std_flash, registry=registry)
        if progress and n % 100 == 0:
            progress("stream %d/%d" % (n, len(streams)))
    unit_res = {}
    for u in units:
        unit_res[u["tag"]] = unit_results(u, stream_res)
    data = {
        "schema": SCHEMA,
        "meta": {
            "model_name": model_name,
            "generated_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "standard_conditions": {"t_c": std_t_c, "p_bara": std_p_bara},
            "neqsim_version": _neqsim_version(),
            "eos": " / ".join(eos_names) if eos_names else _eos_name(first),
            "eos_list": eos_names,
            "mixing_rules": mixing_rules,
            "n_components": len(components),
            "component_conflicts": list(registry.conflicts),
            "generic_symbol_units": [u["tag"] for u in units if u["category"] == "other"],
        },
        "cases": [{"id": case_id, "label": case_label}],
        "areas": areas,
        "components": components,
        "equipment": [],
        "streams": [],
    }
    for u in units:
        rec = {k: v for k, v in u.items() if k != "_obj"}
        rec["generic_symbol"] = rec["category"] == "other"
        rec["design"] = unit_design(u)
        rec["results"] = {case_id: unit_res[u["tag"]]}
        data["equipment"].append(rec)
    for s in streams:
        rec = {k: v for k, v in s.items() if k not in ("_obj", "index")}
        rec["results"] = {case_id: stream_res[s["id"]]}
        data["streams"].append(rec)
    return data


def _neqsim_version():
    """NeqSim library version string (python package metadata; 'unknown' if absent)."""
    try:
        from importlib import metadata

        return "neqsim " + metadata.version("neqsim")
    except Exception:  # noqa: BLE001
        return "unknown"

def _mixing_rule_name(fluid):
    """Mixing-rule description of a fluid, or None when the API does not report one."""
    value = _try(fluid, ["getMixingRuleName"])
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _eos_name(fluid):
    if fluid is None:
        return "unknown"
    try:
        return str(fluid.getClass().getSimpleName())
    except Exception:  # noqa: BLE001
        return "unknown"


def merge_cases(datasets):
    """Merge single-case data sets that share an identical topology.

    Raises:
        ValueError: if stream or equipment topology differs between cases.
    """
    base = datasets[0]
    merged = json.loads(json.dumps(base))
    for other in datasets[1:]:
        if [s["name"] for s in other["streams"]] != [s["name"] for s in base["streams"]]:
            raise ValueError("stream topology differs between cases")
        if [e["tag"] for e in other["equipment"]] != [e["tag"] for e in base["equipment"]]:
            raise ValueError("equipment list differs between cases")
        merged["cases"].extend(other["cases"])
        for ms, os_ in zip(merged["streams"], other["streams"]):
            ms["results"].update(os_["results"])
        for me, oe in zip(merged["equipment"], other["equipment"]):
            me["results"].update(oe["results"])
    return merged


# ── formatting helpers shared by exports and the report ──────

def fmt_value(value, sig=5, trace=1e-6):
    """Format a number with ``sig`` significant figures; None -> 'n/a'.

    Exact zeros print as ``0`` and positive values below ``trace`` as
    ``<1e-6`` so that zero, trace and unavailable data stay distinguishable.
    """
    if value is None:
        return "n/a"
    if value == 0:
        return "0"
    if abs(value) < trace:
        return "<%g" % trace if value > 0 else ">-%g" % trace
    if abs(value) >= 1e6 or abs(value) < 1e-3:
        return "%.*e" % (sig - 1, value)
    return ("%.*g" % (sig, value))


def stream_by_id(data):
    """Index streams by id."""
    return {s["id"]: s for s in data["streams"]}


# ── exports ──────────────────────────────────────────────────

STREAM_COLUMNS = [
    ("t_c", "T_C"), ("p_bara", "P_bara"), ("p_barg", "P_barg"), ("mass_kg_h", "mass_kg_h"),
    ("mole_kmol_h", "mole_kmol_h"), ("mw_g_mol", "MW_g_mol"),
    ("std_vol_ideal_sm3_h", "std_vol_ideal_Sm3_h"), ("vol_actual_m3_h", "vol_actual_m3_h"),
    ("density_kg_m3", "density_kg_m3"), ("h_kj_kg", "h_kJ_kg"), ("H_kw", "H_kW"),
    ("s_kj_kg_k", "s_kJ_kgK"), ("cp_kj_kg_k", "cp_kJ_kgK"), ("state", "state"),
]


def write_outputs(data, out_dir):
    """Write JSON, CSV and XLSX exports; return a dict of written paths."""
    os.makedirs(out_dir, exist_ok=True)
    paths = {}
    path = os.path.join(out_dir, "process_data.json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)
    paths["json"] = path
    comps = [c["name"] for c in data["components"]]
    rows_props, rows_comp, rows_phase = [], [], []
    for s in data["streams"]:
        for case in data["cases"]:
            r = s["results"].get(case["id"], {})
            row = {"case": case["id"], "stream_id": s["id"], "stream_name": s["label"], "area": s["area"],
                   "source": s["source"] or "boundary", "destination": s["destination"] or "boundary",
                   "kind": s["kind"]}
            for key, col in STREAM_COLUMNS:
                row[col] = r.get(key)
            fl = r.get("std_flash_m3_h") or {}
            row["std_flash_gas_Sm3_h"] = fl.get("gas")
            row["std_flash_oil_m3_h"] = fl.get("oil")
            row["std_flash_aqueous_m3_h"] = fl.get("aqueous")
            for p in r.get("phases", []):
                row["%s_mole_frac" % p["type"]] = p["mole_fraction"]
                row["%s_mass_kg_h" % p["type"]] = p["mass_kg_h"]
                row["%s_density_kg_m3" % p["type"]] = p["density_kg_m3"]
            rows_props.append(row)
            for name in comps:
                rows_comp.append({
                    "case": case["id"], "stream_id": s["id"], "stream_name": s["label"], "component": name,
                    "mole_fraction": (r.get("z") or {}).get(name), "mass_fraction": (r.get("w") or {}).get(name),
                    "flow_kmol_h": (r.get("flow_kmol_h") or {}).get(name),
                    "flow_kg_h": (r.get("flow_kg_h") or {}).get(name)})
            for p in r.get("phases", []):
                for name in comps:
                    rows_phase.append({"case": case["id"], "stream_id": s["id"], "stream_name": s["label"],
                                       "phase": p["type"], "phase_mole_fraction": p["mole_fraction"],
                                       "component": name, "x": (p.get("x") or {}).get(name)})
    rows_eq = []
    for e in data["equipment"]:
        for case in data["cases"]:
            r = e["results"].get(case["id"], {})
            row = {"case": case["id"], "tag": e["tag"], "area": e["area"], "type": e["java_class"],
                   "category": e["category"], "inlets": " ".join(e["inlet_streams"]),
                   "outlets": " ".join(e["outlet_streams"]), "duty_kW": r.get("duty_kw"),
                   "power_kW": r.get("power_kw"), "dp_bar": r.get("dp_bar")}
            for k, v in (r.get("extras") or {}).items():
                row[k] = v
            for k, v in (e.get("design") or {}).items():
                row[k] = v
            rows_eq.append(row)
    sets = {"stream_properties": rows_props, "stream_components": rows_comp, "stream_phase_compositions": rows_phase,
            "equipment": rows_eq, "components": data["components"]}
    for name, rows in sets.items():
        path = os.path.join(out_dir, "%s.csv" % name)
        cols = []
        for row in rows:
            for k in row:
                if k not in cols:
                    cols.append(k)
        with open(path, "w", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=cols)
            writer.writeheader()
            for row in rows:
                writer.writerow({k: ("" if row.get(k) is None else row.get(k)) for k in cols})
        paths[name] = path
    try:
        from openpyxl import Workbook

        wb = Workbook()
        wb.remove(wb.active)
        for name, rows in sets.items():
            ws = wb.create_sheet(name[:31])
            cols = []
            for row in rows:
                for k in row:
                    if k not in cols:
                        cols.append(k)
            ws.append(cols)
            for row in rows:
                ws.append([row.get(k) for k in cols])
            ws.freeze_panes = "A2"
        path = os.path.join(out_dir, "process_data.xlsx")
        wb.save(path)
        paths["xlsx"] = path
    except ImportError:
        pass
    return paths
