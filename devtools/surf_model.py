"""SURF model generation: study specification -> executable NeqSim field networks, process units and a mapping manifest.

``build_plan`` turns the normalised specification (one concept, one case) into an ordered list of operations; ``execute_plan``
runs them against the existing NeqSim classes (``FieldNetworkTopology`` over ``LoopedPipeNetwork``, ``FieldWellNetworkCoupler``,
``FieldWellNetworkProcessUnit``, ``WellFlow``, ``SubseaProductionSystem`` line designs). No hydraulic, thermal, mechanical or
cost calculation is implemented here: this module only creates and wires the existing owners. ``emit_python`` writes the same
plan as an editable stand-alone Python builder; a test proves both produce the same canonical topology definition.
"""
import json
import os

import surf_spec
from fa_units import FlowAssuranceError

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HERE)
PKG = "neqsim.process.equipment.network."
ROLE = {"jumper": "JUMPER", "flowline": "FLOWLINE", "trunkline": "TRUNKLINE", "riser": "RISER", "pipeline": "PIPELINE", "header": "HEADER",
        "tie_in": "TIE_IN", "pump": "PUMP"}
NODE_ROLE = {"production_well": "PRODUCTION_WELL", "injection_well": "INJECTION_WELL", "manifold": "MANIFOLD", "injection_manifold": "MANIFOLD",
             "plem": "PLEM", "plet": "PLET", "junction": "JUNCTION", "host": "HOST", "injection_source": "INJECTION_SOURCE", "riser_base": "JUNCTION",
             "tie_in": "BROWNFIELD_TIE_IN"}
MODEL = {"beggs_brill": "BEGGS_BRILL", "darcy_weisbach": "DARCY_WEISBACH", "two_fluid": "TWO_FLUID"}
K_OFFSET = 273.15


class ModelError(FlowAssuranceError):
    """The NeqSim model could not be generated or the runtime is unavailable."""


# --- runtime ----------------------------------------------------------------------------------------
def start_jvm():
    """Start the JVM on the workspace classes (source checkout) or the packaged jar and return the ``jpype`` module."""
    try:
        import jpype
    except ImportError as exc:  # pragma: no cover - environment dependent
        raise ModelError("jpype is not installed; the SURF model generator needs the NeqSim runtime (%s)" % exc)
    if not jpype.isJVMStarted():
        import neqsim_dev_setup

        neqsim_dev_setup.neqsim_init(project_root=REPO_ROOT, verbose=False)
    try:
        jpype.JClass(PKG + "FieldNetworkTopology")
        jpype.JClass("neqsim.process.fielddevelopment.subsea.SubseaProductionSystem")
    except Exception as exc:  # noqa: BLE001
        raise ModelError("the NeqSim runtime on the classpath has no FieldNetworkTopology/SubseaProductionSystem; build the workspace "
                         "(mvnw package -DskipTests) or install a current NeqSim jar (%s)" % str(exc).splitlines()[0])
    return jpype


def neqsim_version():
    """Name of the NeqSim jar or classes directory on the classpath (``unknown`` when it cannot be determined)."""
    try:
        import jpype

        for part in str(jpype.getClassPath()).split(os.pathsep):
            base = os.path.basename(part.rstrip("\\/"))
            if base.lower().startswith("neqsim") and base.lower().endswith(".jar") and not base.lower().startswith("original"):
                return base
        return "workspace classes"
    except Exception:  # noqa: BLE001
        return "unknown"


class Classes:
    """Lazy JPype class registry (one lookup per class)."""

    def __init__(self, jpype):
        self.jp = jpype
        self._c = {}

    def __call__(self, name):
        if name not in self._c:
            self._c[name] = self.jp.JClass(name if "." in name else PKG + name)
        return self._c[name]

    def enum(self, outer, inner, value):
        return self.jp.JClass("%s$%s" % (outer if "." in outer else PKG + outer, inner)).valueOf(value)

    def darr(self, values):
        return self.jp.JArray(self.jp.JDouble)([float(v) for v in values])


# --- plan -------------------------------------------------------------------------------------------
def fluid_def(spec, fid):
    """Fluid definition used by plan operations (components are mole fractions)."""
    fd = spec["fluids"][fid]
    return {"id": fid, "eos": fd.get("eos", "SRK"), "mixing_rule": fd.get("mixing_rule", "classic"), "components": dict(fd["components"]),
            "multiphase_check": bool(fd.get("multiphase_check", True))}


def _profile(e):
    scale = e["installed_length_m"] / e["route_length_m"] if e["route_length_m"] > 0 else 1.0
    return [[round(c * scale, 6), p[2]] for c, p in zip(e["chainage_m"], e["points"])]


def build_plan(spec, model, concept_id, case_id, domain, segment_factor=1):
    """Ordered operations that create one hydraulic ``domain`` (``production`` or ``injection``) for a concept and a case."""
    case = model["cases"][case_id]
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    dom_edges = {k: e for k, e in edges.items() if e["service"] == domain}
    used = {e["from"] for e in dom_edges.values()} | {e["to"] for e in dom_edges.values()}
    nodes = {k: v for k, v in model["nodes"].items() if k in used}
    solver = spec["solver"]
    fid = case["production_fluid"] if domain == "production" else "injection_water"
    ops = [{"op": "network", "domain": domain, "name": "%s %s %s" % (spec["study"]["id"], domain, concept_id), "fluid": fluid_def(spec, fid),
            "template_t_c": 80.0 if domain == "production" else spec["host"].get("injection_supply_temperature_c", 30.0),
            "template_p_bara": 150.0, "tolerance_pa": solver["network_tolerance_pa"], "max_iterations": solver["max_iterations"],
            "relaxation": solver["coupling_relaxation"], "coupling_iterations": solver["coupling_max_iterations"],
            "coupling_rate_tol": solver["coupling_rate_tolerance_kg_s"], "coupling_pressure_tol": solver["coupling_pressure_tolerance_bar"]}]
    for nid, n in nodes.items():
        k = n["kind"]
        base = {"op": "node", "id": nid, "tag": n["tag"], "role": NODE_ROLE[k], "service": domain.upper(), "elevation_m": n["z_m"]}
        if k == "production_well":
            w = n["well"]
            ops.append(dict(base, type="live_well", wellhead_t_c=w["wellhead_temperature_c"], initial_p_bara=round(0.5 * case["production_reservoir_pressure_bara"], 3)))
        elif k == "injection_well":
            ops.append(dict(base, type="live_well", wellhead_t_c=spec["host"].get("injection_supply_temperature_c", 30.0),
                            initial_p_bara=case["injection_pump_discharge_bara"]))
        elif k == "host":
            ops.append(dict(base, type="fixed_sink", pressure_bara=case["host_pressure_bara"]))
        elif k == "injection_source":
            ops.append(dict(base, type="source", pressure_bara=n["supply_pressure_bara"], temperature_c=n["temperature_c"]))
        else:
            ops.append(dict(base, type="junction", temperature_c=(spec["host"].get("injection_supply_temperature_c", 30.0) if domain == "injection" else None)))
    for eid, e in dom_edges.items():
        if e["kind"] == "pump":
            ops.append({"op": "pump", "id": eid, "tag": e["tag"], "service": domain.upper(), "from": e["from"], "from_port": e["from_port"], "to": e["to"],
                        "to_port": e["to_port"], "discharge_bara": case["injection_pump_discharge_bara"], "efficiency": e["pump"]["efficiency"]})
            continue
        h = e["hydraulic"]
        ops.append({"op": "pipe", "id": eid, "tag": e["tag"], "role": ROLE[e["kind"]], "service": domain.upper(), "from": e["from"], "from_port": e["from_port"],
                    "to": e["to"], "to_port": e["to_port"], "length_m": round(e["installed_length_m"], 6), "diameter_m": round(e["id_m"], 6),
                    "model": MODEL[h["model"]], "segments": max(1, int(h["segments"]) * int(segment_factor)) if h["model"] != "darcy_weisbach" else 1,
                    "roughness_m": float(e.get("roughness_mm", 0.05)) / 1000.0, "profile": _profile(e)})
    for nid, n in nodes.items():
        if n["kind"] == "production_well":
            w = n["well"]
            ops.append({"op": "well_production", "node": nid, "reservoir_t_c": w["wellhead_temperature_c"], "reservoir_p_bara": case["production_reservoir_pressure_bara"],
                        "pi_msm3d_per_bar2": w["productivity_index_msm3d_per_bar2"] * case["productivity_scale"], "fluid": fluid_def(spec, case["production_fluid"])})
        elif n["kind"] == "injection_well":
            w = n["well"]
            ops.append({"op": "well_injection", "node": nid, "feed_t_c": spec["host"].get("injection_supply_temperature_c", 30.0), "feed_p_bara": case["injection_pump_discharge_bara"],
                        "reservoir_p_bara": case["injection_reservoir_pressure_bara"], "jm_kg_s_per_bar": w["injectivity_kg_s_per_bar"],
                        "fracture_bara": w["fracture_pressure_bara"], "fluid": fluid_def(spec, "injection_water")})
    for eid, e in dom_edges.items():
        if e["kind"] == "pump":
            continue
        ops.append({"op": "line_design", "edge": eid, "construction": e.get("construction", "rigid"), "od_mm": e.get("od_mm"), "wall_mm": e.get("wall_mm"),
                    "grade": e.get("grade", "X65"), "design_pressure_bara": (e.get("design") or {}).get("pressure_bara", 165.0),
                    "installation": (e.get("installation") or {}).get("cost_method", "S-lay"), "insulation_material": (e.get("insulation") or {}).get("material"),
                    "insulation_mm": (e.get("insulation") or {}).get("thickness_mm", 0.0), "ambient_c": e["thermal"]["ambient_c"],
                    "inner_htc": e["thermal"].get("inner_htc_w_m2k", 300.0), "outer_htc": e["thermal"].get("outer_htc_w_m2k", 100.0),
                    "u_value": e["thermal"].get("u_value_w_m2k"), "dnv_st_f101": (e.get("structural") or {}).get("dnv_st_f101")})
    return ops


# --- execution --------------------------------------------------------------------------------------
class Domain:
    """One generated hydraulic domain: topology, coupler, process unit, bound wells and per-domain line designs."""

    def __init__(self, name):
        self.name = name
        self.topology = self.coupler = self.unit = self.sps = None
        self.wells, self.streams, self.pipes, self.fluid = {}, {}, {}, None
        self.plan = []


def make_fluid(C, fdef, t_c, p_bara):
    """NeqSim thermodynamic system for a fluid definition (``SystemSrkEos``; the study declares ``SRK``)."""
    if fdef["eos"] != "SRK":
        raise ModelError("fluid %s: only eos SRK is generated by this version (got %s)" % (fdef["id"], fdef["eos"]))
    s = C("neqsim.thermo.system.SystemSrkEos")(K_OFFSET + t_c, p_bara)
    for name, x in fdef["components"].items():
        s.addComponent(name, float(x))
    s.setMixingRule(fdef["mixing_rule"])
    s.setMultiPhaseCheck(bool(fdef.get("multiphase_check", True)))
    return s


def _stream(C, name, fluid, rate_value, rate_unit):
    st = C("neqsim.process.equipment.stream.Stream")(name, fluid)
    st.setFlowRate(rate_value, rate_unit)
    st.run()
    return st


def execute_plan(plan, jpype=None):
    """Create the NeqSim objects of a plan and return the :class:`Domain` (the network is built, not solved)."""
    jp = jpype or start_jvm()
    C = Classes(jp)
    d = Domain(plan[0]["domain"])
    d.plan = plan
    nw = plan[0]
    FNT = C("FieldNetworkTopology")
    NodeRole, EdgeRole, Service = (lambda v: C.enum("FieldNetworkTopology", "NodeRole", v)), (lambda v: C.enum("FieldNetworkTopology", "EdgeRole", v)), \
        (lambda v: C.enum("FieldNetworkTopology", "Service", v))
    direction = C.enum("FieldNetworkTopology", "FlowDirection", "FROM_TO")
    d.topology = FNT(nw["name"])
    net = d.topology.getHydraulicNetwork()
    d.fluid = make_fluid(C, nw["fluid"], nw["template_t_c"], nw["template_p_bara"])
    net.setFluidTemplate(d.fluid)
    net.setSolverType(C.enum("LoopedPipeNetwork", "SolverType", "NEWTON_RAPHSON"))
    net.setTolerance(nw["tolerance_pa"])
    net.setMaxIterations(int(nw["max_iterations"]))
    d.coupler = C("FieldWellNetworkCoupler")(d.topology)
    d.coupler.setRelaxationFactor(nw["relaxation"])
    d.coupler.setMaximumIterations(int(nw["coupling_iterations"]))
    d.coupler.setTolerances(nw["coupling_rate_tol"], nw["coupling_pressure_tol"])
    d.sps = C("neqsim.process.fielddevelopment.subsea.SubseaProductionSystem")("%s line designs" % nw["name"])
    for o in plan[1:]:
        kind = o["op"]
        if kind == "node":
            t = o["type"]
            if t == "live_well":
                role = o["role"]
                d.topology.addLiveWellNode(o["id"], o["tag"], NodeRole(role), Service(o["service"]), o["initial_p_bara"], o["elevation_m"])
                net.getNode(o["id"]).setTemperature(K_OFFSET + o["wellhead_t_c"])
            elif t == "fixed_sink":
                d.topology.addFixedPressureSink(o["id"], o["tag"], NodeRole(o["role"]), Service(o["service"]), o["pressure_bara"], o["elevation_m"])
            elif t == "source":
                d.topology.addPressureSource(o["id"], o["tag"], NodeRole(o["role"]), Service(o["service"]), o["pressure_bara"], 0.0, o["elevation_m"])
                net.getNode(o["id"]).setTemperature(K_OFFSET + o["temperature_c"])
            else:
                d.topology.addJunction(o["id"], o["tag"], NodeRole(o["role"]), Service(o["service"]), o["elevation_m"])
                if o.get("temperature_c") is not None:
                    net.getNode(o["id"]).setTemperature(K_OFFSET + o["temperature_c"])
        elif kind == "pump":
            d.topology.addPump(o["id"], o["tag"], Service(o["service"]), direction, o["from"], o["from_port"], o["to"], o["to_port"], o["discharge_bara"], o["efficiency"])
        elif kind == "pipe":
            p = d.topology.addPipe(o["id"], o["tag"], EdgeRole(o["role"]), Service(o["service"]), direction, o["from"], o["from_port"], o["to"], o["to_port"],
                                   o["length_m"], o["diameter_m"], C.enum("LoopedPipeNetwork", "PipeModelType", o["model"]))
            if o["model"] != "DARCY_WEISBACH":
                p.setMultiphaseSegments(int(o["segments"]))
            p.setRoughness(o["roughness_m"])
            p.setElevationProfile(C.darr([x[0] for x in o["profile"]]), C.darr([x[1] for x in o["profile"]]))
            d.pipes[o["id"]] = p
        elif kind == "well_production":
            res = _stream(C, "reservoir-" + o["node"], make_fluid(C, o["fluid"], o["reservoir_t_c"], o["reservoir_p_bara"]), 1.0, "MSm3/day")
            w = C("neqsim.process.equipment.reservoir.WellFlow")("well-" + o["node"])
            w.setInletStream(res)
            w.setWellProductionIndex(o["pi_msm3d_per_bar2"])
            d.coupler.bindProductionWell(o["node"], w)
            d.wells[o["node"]], d.streams[o["node"]] = w, res
        elif kind == "well_injection":
            feed = _stream(C, "feed-" + o["node"], make_fluid(C, o["fluid"], o["feed_t_c"], o["feed_p_bara"]), 1.0, "kg/sec")
            zone = _stream(C, "zone-" + o["node"], make_fluid(C, o["fluid"], 70.0, o["reservoir_p_bara"]), 1.0, "kg/sec")
            w = C("neqsim.process.equipment.reservoir.WellFlow")("well-" + o["node"])
            w.setInletStream(feed)
            w.addLiquidInjectionZone("target", zone, o["reservoir_p_bara"], o["jm_kg_s_per_bar"], o["fracture_bara"])
            w.setTargetZone("target")
            d.coupler.bindInjectionWell(o["node"], w)
            d.wells[o["node"]], d.streams[o["node"]] = w, feed
        elif kind == "line_design":
            ld = line_design(C, o)
            d.sps.setLineDesign(o["edge"], ld)
    d.sps.applyLineThermalDesigns(d.topology)
    issues = [str(i) for i in d.topology.validate() if str(i).startswith("ERROR")]
    if issues:
        raise ModelError("generated topology has errors: " + "; ".join(issues))
    d.unit = C("FieldWellNetworkProcessUnit")("SURF-" + d.name, d.coupler)
    return d


def line_design(C, o):
    """``SURFCostEstimator.LineDesign`` for one edge (the existing owner of thermal U-value, wall and structural screening inputs)."""
    LD = C("neqsim.process.mechanicaldesign.subsea.SURFCostEstimator$LineDesign")
    if o["construction"] == "flexible":
        b = LD.builder(o["od_mm"] / 25.4).flexible(True).overallHeatTransferCoefficientWm2K(float(o["u_value"]))
        b = b.thermalBoundary(o["ambient_c"], o["inner_htc"], o["outer_htc"]).installationMethod(o["installation"]).designPressureBar(float(o["design_pressure_bara"]))
        return b.build()
    b = LD.builder(o["od_mm"] / 25.4).wallThicknessMm(float(o["wall_mm"])).materialGrade(o["grade"]).designPressureBar(float(o["design_pressure_bara"]))
    b = b.installationMethod(o["installation"])
    if o.get("insulation_material"):
        b = b.insulation(C.enum("neqsim.process.equipment.pipeline.RadialThermalLayer", "MaterialType", o["insulation_material"]), float(o["insulation_mm"]))
    if o.get("u_value") is not None:
        b = b.overallHeatTransferCoefficientWm2K(float(o["u_value"]))
    b = b.thermalBoundary(o["ambient_c"], o["inner_htc"], o["outer_htc"])
    if o.get("dnv_st_f101"):
        b = b.dnvStF101Input(f101_input(C, o["dnv_st_f101"], o["od_mm"] / 1000.0, o["wall_mm"] / 1000.0, o.get("corrosion_allowance_mm", 0.0) / 1000.0))
    return b.build()


def f101_input(C, d, od_m, wt_m, ca_m):
    """``DnvStF101PipelineDesignInput`` from the study's explicit project inputs (nothing is defaulted)."""
    P = "neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput"
    b = C(P).builder()
    b = b.safetyClass(C.enum(P, "SafetyClass", d["safety_class"])).fabricationRoute(C.enum(P, "FabricationRoute", d["fabrication_route"]))
    b = b.geometry(od_m, wt_m, ca_m)
    f, m, r, p, ld, st, fa = d["fabrication"], d["material"], d["resistance_factors"], d["pressures_mpa"], d["design_loads"], d["installation_strains"], d["fatigue"]
    b = b.fabrication(f["tolerance_fraction"], f["ovality_fraction"], f["max_ovality_fraction"], f["fabrication_factor"])
    b = b.material(m["smys_mpa"], m["smts_mpa"], m["youngs_modulus_mpa"], m["poisson_ratio"])
    b = b.resistanceFactors(r["strength_derating"], r["smys_strength_factor"], r["smts_strength_factor"], r["material_resistance_factor"])
    b = b.pressures(p["operating"], p["incidental"], p["external"], p["minimum_internal"], p["system_test"], p["test_external"])
    b = b.designLoads(ld["axial_kn"], ld["bending_knm"], ld["torsion_knm"])
    b = b.installationStrains(st["axial"], st["bending"], st["accumulated_plastic"], st["allowable"])
    b = b.fatigueCurve(fa["sn_log_a"], fa["sn_slope"], fa["stress_concentration_factor"], fa["design_fatigue_factor"])
    for s_range, cycles in fa["bins"]:
        b = b.addFatigueBin(float(s_range), float(cycles))
    return b.build()


# --- umbilical service networks ---------------------------------------------------------------------
def build_umbilical_services(spec, model, jpype=None):
    """One single-phase Darcy-Weisbach ``LoopedPipeNetwork`` per chemical tube (supply to the consumer manifold); not yet solved."""
    jp = jpype or start_jvm()
    C = Classes(jp)
    umb = spec["umbilical"]
    term = {t["node"]: t for t in umb["terminations"]}
    out = {}
    for s in umb["services"]:
        if s["kind"] != "chemical":
            continue
        t = term.get(s["consumer"])
        if t is None:
            raise ModelError("umbilical service %s: consumer %s has no termination" % (s["id"], s["consumer"]))
        fd = fluid_def(spec, s["fluid"])
        net = C("LoopedPipeNetwork")("umbilical %s" % s["id"])
        net.setFluidTemplate(make_fluid(C, fd, 20.0, umb["chemical_supply_pressure_bara"]))
        net.setSolverType(C.enum("LoopedPipeNetwork", "SolverType", "NEWTON_RAPHSON"))
        net.setTolerance(1.0)
        net.setMaxIterations(100)
        net.addSourceNode("host supply", umb["chemical_supply_pressure_bara"], 0.0, spec["host"]["inlet_elevation_m"])
        net.addSinkNode("consumer", s["rate_kg_s"] * 3600.0, model["nodes"][s["consumer"]]["z_m"])
        p = net.addPipe("host supply", "consumer", s["id"], float(t["route_length_m"]), s["tube_id_mm"] / 1000.0)
        p.setHydraulicModelType(C.enum("LoopedPipeNetwork", "PipeModelType", "DARCY_WEISBACH"))
        out[s["id"]] = {"network": net, "service": s, "length_m": float(t["route_length_m"])}
    return out


# --- mapping manifest -------------------------------------------------------------------------------
def mapping_manifest(spec, model, concept_id, domains):
    """Stable mapping from specification IDs to hydraulic nodes/edges, equipment, line designs and (filled later) drawings and quantities."""
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    rows = []
    for dname, d in domains.items():
        for o in d.plan[1:]:
            if o["op"] == "node":
                n = model["nodes"][o["id"]]
                rows.append({"id": o["id"], "type": "node", "domain": dname, "tag": o["tag"], "kind": n["kind"], "hydraulic": "FieldNetworkTopology node '%s'" % o["id"],
                             "equipment_binding": ("WellFlow 'well-%s'" % o["id"]) if n["kind"] in ("production_well", "injection_well") else None,
                             "structure": n.get("structure"), "line_design": None, "drawings": [], "quantities": []})
            elif o["op"] in ("pipe", "pump"):
                e = edges[o["id"]]
                rows.append({"id": o["id"], "type": "edge", "domain": dname, "tag": o["tag"], "kind": e["kind"], "from": e["from"], "to": e["to"],
                             "hydraulic": "FieldNetworkTopology edge '%s'" % o["id"], "equipment_binding": None,
                             "line_design": "SubseaProductionSystem.setLineDesign('%s')" % o["id"] if o["op"] == "pipe" else None,
                             "route_length_m": e["route_length_m"], "installed_length_m": e["installed_length_m"], "drawings": [], "quantities": []})
    for sid, s in ((x["id"], x) for x in spec.get("umbilical", {}).get("services", [])):
        rows.append({"id": sid, "type": "service", "domain": "umbilical", "tag": spec["umbilical"]["tag"] + "/" + sid, "kind": s["kind"], "consumer": s.get("consumer"),
                     "hydraulic": "LoopedPipeNetwork 'umbilical %s'" % sid if s["kind"] == "chemical" else "register only (not hydraulically modelled)",
                     "equipment_binding": None, "line_design": None, "drawings": [], "quantities": []})
    return rows


# --- stand-alone builder emission -------------------------------------------------------------------
HEADER = '''"""Generated SURF builder for concept {concept}, case {case}, domain {domain} (study {study}).

Generated by surf_model.emit_python from the study specification; edit the data and calls freely. It builds the same
FieldNetworkTopology definition as the surf-study pipeline. Run it with the NeqSim runtime on the class path.
"""
import sys

import jpype

sys.path.insert(0, {devtools!r})
import surf_model

surf_model.start_jvm()
C = surf_model.Classes(jpype)
PLAN = {plan}

'''

BODY = '''
domain = surf_model.execute_plan(PLAN, jpype)
topology, unit = domain.topology, domain.unit
# domain.topology is the canonical FieldNetworkTopology; domain.unit the FieldWellNetworkProcessUnit.
# Solve it with: unit.run(jpype.JClass("java.util.UUID").randomUUID())
if __name__ == "__main__":
    print(str(topology.toJson())[:200])
'''


def emit_python(plan, spec, concept_id, case_id, path):
    """Write a stand-alone, editable Python builder holding the explicit operation list; returns the path."""
    import pprint

    head = HEADER.format(concept=concept_id, case=case_id, domain=plan[0]["domain"], study=spec["study"]["id"], devtools=HERE,
                         plan=pprint.pformat(plan, width=150, sort_dicts=False))
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(head + BODY)
    return path
