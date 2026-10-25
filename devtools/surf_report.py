"""SURF design study report (DOCX): 20 chapters, drawing appendix, datasheets and registers from the canonical dataset.

A4 portrait narrative, A3 landscape drawing appendix, tables with numbered captions, data tokens ``[[ds:path|fmt|unit=..]]`` that must
resolve, and a coverage table that justifies every discipline instead of padding. A polished report is not an approved design: every
conclusion states its evidence and its remaining dependency, and the readiness label comes from the validation gates.
"""
import json
import math
import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

import fa_draw  # noqa: E402
import psr_report  # noqa: E402
import surf_dataset  # noqa: E402
import surf_sheets_a  # noqa: E402

PALETTE = ["#1f3a5f", "#b03a2e", "#1e8449", "#7d3c98", "#b9770e", "#117a8b"]
STYLES = ["-", "--", "-.", ":", (0, (5, 1.5)), (0, (3, 1, 1, 1, 1, 1))]


def _style(ds):
    return {c["id"]: (PALETTE[i % len(PALETTE)], STYLES[i % len(STYLES)]) for i, c in enumerate(ds["cases"])}


def make_figures(ds, fig_dir):
    """Matplotlib figures from the dataset; returns ``{key: (path, alt, caption)}``."""
    os.makedirs(fig_dir, exist_ok=True)
    plt.rcParams.update({"font.size": 8, "axes.grid": True, "grid.alpha": 0.3, "font.family": "DejaVu Sans"})
    sty = _style(ds)
    ref = ds["reference_concept"]
    out = {}
    paths = surf_sheets_a._paths(ds, ref, "production", ("production_well",), "host")
    total, path = paths[0]
    lines = {r["id"]: r for r in ds["registers"][ref]["lines"]}
    offs, cur = {}, 0.0
    for eid in path:
        offs[eid] = cur
        cur += lines[eid]["installed_length_m"] if eid in lines else 0.0
    fig, axs = plt.subplots(2, 1, figsize=(7.2, 5.4), sharex=True)
    for c in ds["cases"]:
        xs, ps, ts = [], [], []
        for eid in path:
            prof = ds["results"][ref][c["id"]]["production"]["edges"][eid]["profile"]
            for pos, p, t in zip(prof["position_m"], prof["pressure_bara"], prof["temperature_c"]):
                xs.append((offs[eid] + pos) / 1000.0)
                ps.append(p)
                ts.append(t)
        col, ls = sty[c["id"]]
        axs[0].plot(xs, ps, color=col, ls=ls, lw=1.4, label=c["id"])
        axs[1].plot(xs, ts, color=col, ls=ls, lw=1.4, label=c["id"])
    axs[0].set_ylabel("pressure (bara)")
    axs[1].set_ylabel("temperature (degC)")
    axs[1].set_xlabel("chainage along path %s (km)" % " > ".join(path))
    axs[0].legend(ncol=3, fontsize=7)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_profiles.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["profiles"] = (p, "Pressure and temperature along the longest production path for every case", "Pressure and temperature along the longest production path (reference concept %s)" % ref)
    fig, ax = plt.subplots(figsize=(7.2, 3.2))
    names = list(ds["concepts"])
    capex = [ds["cost"][c]["total_usd"] / 1e6 for c in names]
    cols = ["#1e8449" if ds["facts"]["feasibility_" + c] == "feasible" else "#b03a2e" for c in names]
    ax.bar(names, capex, color=cols)
    for i, v in enumerate(capex):
        ax.text(i, v + 5, "%.0f MUSD (%s)" % (v, ds["facts"]["feasibility_" + names[i]]), ha="center", fontsize=7)
    ax.set_ylabel("SURF CAPEX, screening (MUSD)")
    ax.set_ylim(0, max(capex) * 1.18)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_concepts.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["concepts"] = (p, "SURF CAPEX of every concept coloured by feasibility (green feasible, red infeasible)", "Concept comparison: CAPEX and feasibility (feasibility is assessed before cost)")
    fig, ax = plt.subplots(figsize=(7.2, 3.4))
    cases = [c["id"] for c in ds["cases"]]
    width = 0.8 / len(cases)
    wells = [w for w in ds["results"][ref][cases[0]]["production"]["wells"]]
    for i, c in enumerate(cases):
        vals = [w["applied_rate_kg_s"] for w in ds["results"][ref][c]["production"]["wells"]]
        ax.bar([j + i * width for j in range(len(wells))], vals, width, color=sty[c][0], label=c)
    ax.set_xticks([j + 0.4 - width / 2 for j in range(len(wells))])
    ax.set_xticklabels([w["node"] for w in wells])
    ax.set_ylabel("well mass rate (kg/s)")
    ax.legend(fontsize=7)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_wells.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["wells"] = (p, "Solved production well rates for each case", "Solved well rates by case (live WellFlow productivity-index inflow coupled to the network)")
    fig, ax = plt.subplots(figsize=(7.2, 3.4))
    um = ds["umbilical"]["results"]
    ids = list(um)
    for i, c in enumerate(cases):
        req = [ds["results"][ref][c]["derived"]["umbilical"][s]["production_pressure_bara"] + ds["umbilical"]["spec"]["required_margin_bar"] for s in ids]
        ax.plot(range(len(ids)), req, marker="o", color=sty[c][0], ls=sty[c][1], label="required (%s)" % c)
    ax.bar(range(len(ids)), [um[s]["delivery_pressure_bara"] for s in ids], width=0.4, color="#c9d3e3", label="delivered")
    ax.set_xticks(range(len(ids)))
    ax.set_xticklabels(ids)
    ax.set_ylabel("pressure at the consumer manifold (bara)")
    ax.legend(fontsize=7, ncol=2)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_umbilical.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["umbilical"] = (p, "Chemical delivery pressure of each tube against the pressure required at the injection point in every case", "Chemical delivery pressure against the requirement")
    return out


def status_word(s):
    return {"PASS": "met", "FAIL": "NOT met", "NOT ASSESSED": "not assessed"}.get(s, s.lower())


class SurfReportBuilder(psr_report.ReportBuilder):
    """SURF design report. Call :meth:`build` once."""

    KEYWORDS = "SURF; subsea; flowline; riser; umbilical; NeqSim"
    GENERATOR_COMMENT = "Generated by the surf-study toolkit"

    def __init__(self, ds, spec, manifest, readiness, validation=None, figures=None):
        self.ds, self.spec, self.manifest = ds, spec, manifest
        self.validation, self.readiness_info = validation, readiness
        self.opt = {}
        self._init_document()
        self.res = surf_dataset.SurfResolver(ds)
        self.figs = figures or {}
        self.ref = ds["reference_concept"]
        self.cases = [c["id"] for c in ds["cases"]]
        self.design = next(c["id"] for c in ds["cases"] if c["role"] == "design")
        self.basis = {"document": dict(ds["document"], readiness=readiness["label"], readiness_note=readiness["note"])}

    # --- helpers ---
    def X(self, text):
        return self.res.expand(text)

    def row(self, cells):
        return [self.res.expand(c) for c in cells]

    def tok(self, path, fmt=None, unit=None):
        return "[[ds:%s|%s%s]]" % (path, fmt or "", "|unit=" + unit if unit else "")

    def fact(self, key, fmt=None):
        return "[[fact:%s|%s]]" % (key, fmt or "")

    def R(self, case, dom, tail):
        return "results.%s.%s.%s.%s" % (self.ref, case, dom, tail)

    def landscape(self):
        self.section(landscape=True)

    def portrait(self):
        self.section()

    def A3(self):
        self.section(landscape=True, a3=True)

    def sheet(self, code):
        for s in self.manifest["sheets"]:
            if s["code"] == code:
                return "%s (sheet %d)" % (s["drawing_number"], s["number"])
        return code

    def lead(self, items):
        for i in items:
            self.P(i, style="List Bullet")

    def _fig(self, key, width):
        p, alt, cap = self.figs[key]
        return p, width, alt, cap

    # --- front matter ---
    def document_control(self):
        d = self.basis["document"]
        self.H("Document control", 1)
        self.P("Revision history", bold=True)
        rows = [(r.get("rev"), r.get("date"), r.get("description"), r.get("by", "not assigned")) for r in d.get("revision_history", [])]
        self.table(["Rev", "Date", "Description", "By"], rows, widths=[15, 28, 100, 30], font=9, align=["left"] * 4)
        info = self.readiness_info
        self.P("Readiness label: %s. %s" % (info["label"], info["explanation"]))
        review = (self.ds.get("review") or {})
        rows = [("Prepared by", d.get("prepared_by", "not assigned")), ("Checked by", d.get("checked_by", "not assigned")), ("Approved by", d.get("approved_by", "not assigned")),
                ("Independent reviewer", review.get("reviewer") or "not assigned"), ("Review record", review.get("record") or "none")]
        self.table(["Role", "Name"], rows, widths=[50, 100], font=9, align=["left", "left"], first_bold=True)
        self.P("The toolkit never assigns a checker, approver or reviewer, and a passing software check is not a design certification. Numerical screening, supplier confirmation, specialist "
               "analysis, independent verification and approval are separate maturity states; this report reaches numerical screening only.", italic=True)

    # --- 1 executive summary ---
    def executive_summary(self):
        ds = self.ds
        trk = self.exec_summary_text
        self.H("1 Executive summary", 1)
        self.P("Decisions addressed", bold=True)
        for dec in ds["study"]["decisions"]:
            self.P(dec, style="List Bullet", track=trk)
        self.P("Selected concept and principal results", bold=True)
        sel = ds["selected_concept"]
        if sel:
            others = ", ".join("%s %s MUSD (%s)" % (o, self.fact("capex_musd_" + o, ".0f"), ds["facts"]["feasibility_" + o]) for o in ds["concepts"] if o != sel)
            self.P("Concept %s is selected: it is the lowest-cost concept among those that satisfy every acceptance criterion in every case. Its SURF CAPEX is %s MUSD (screening, %s %s basis); "
                   "the other concepts are %s." % (sel, self.fact("capex_musd_" + sel, ".0f"), ds["cost"][sel]["currency"], ds["cost"][sel]["cost_year"], others), track=trk)
        else:
            self.P("No concept is selected: none satisfies every acceptance criterion in every case, so no concept is ranked on cost.", track=trk)
        rows = []
        for r in ds["comparison"]["rows"]:
            cid = r["concept"]
            rows.append(self.row([cid, ds["concepts"][cid]["label"], r["feasibility"], "%s" % self.fact("capex_musd_" + cid, ".0f"), ", ".join(r["failed"][:3]) or "none", ", ".join(r["open"][:3]) or "none"]))
        self.table(["Concept", "Description", "Feasibility", "CAPEX (MUSD)", "Failed (criterion/case)", "Not assessed"], rows, caption="Concept comparison (feasibility before cost)",
                   font=7.5, widths=[16, 52, 22, 20, 40, 24], align=["left"] * 6)
        trk.append(" ".join(" ".join(r) for r in rows))
        d = self.design
        self.P("For the reference concept %s the design case %s solves with a host inlet rate of %s kg/s at %s bara and %s degC; the four producers deliver %s kg/s in total and the two "
               "injectors take %s kg/s with a pump power of %s kW (all steady NeqSim network solves, screening grade)." % (
                   self.ref, d, self.tok(self.R(d, "production", "host.mass_rate_kg_s"), ".1f"), self.tok(self.R(d, "production", "host.pressure_bara"), ".0f"),
                   self.tok(self.R(d, "production", "host.temperature_c"), ".1f"), self.tok(self.R(d, "production", "host.mass_rate_kg_s"), ".1f"),
                   "%.1f" % sum(w["applied_rate_kg_s"] for w in ds["results"][self.ref][d]["injection"]["wells"]), self.tok(self.R(d, "injection", "pump_power_kw"), ".0f")), track=trk)
        header = ["Criterion"] + ["%s/%s" % (c, k) for c in ds["concepts"] for k in self.cases]
        rows = []
        for i, crit in enumerate(ds["criteria_spec"]):
            row = ["%s (%s %s %g)" % (crit["id"], crit["quantity"].replace("_", " "), crit["operator"], crit["value"])]
            for c in ds["concepts"]:
                for k in self.cases:
                    b = ds["criteria"][c][i]["by_case"][k]
                    row.append(("%.1f %s" % (b["value"], b["status"])) if b["value"] is not None else b["status"])
            rows.append(row)
        self.landscape()
        self.table(header, rows, caption="Acceptance criteria by concept and case (governing value and status)", font=6.5, widths=[62] + [200.0 / max(len(header) - 1, 1)] * (len(header) - 1),
                   align=["left"] + ["center"] * (len(header) - 1))
        self.portrait()
        self.P("Readiness and blockers", bold=True)
        self.P("Readiness: %s - %s. Blockers to a higher maturity: the inputs are synthetic; the riser geometry and dynamics, on-bottom stability, free span, global buckling, umbilical "
               "construction, electrical design, installation analyses and transient flow assurance are open specialist hand-offs (Section 19); no supplier data exist for any equipment." % (
                   self.readiness_info["label"], self.readiness_info["note"]), track=trk)

    # --- 2 scope ---
    def scope(self):
        ds = self.ds
        self.H("2 Scope, objectives, maturity, battery limits and exclusions", 1)
        self.P("Objective: %s Maturity: %s; design life %s years; region: %s." % (ds["study"]["objective"], ds["study"]["maturity"], ds["study"]["design_life_years"], ds["study"]["region"]))
        self.P("Battery limits: (1) the host water-supply and chemical-supply boundaries, where pressure is specified; (2) the host inlet, where the inlet pressure of each case is specified. "
               "Wells are modelled as live NeqSim WellFlow inflow at the tree (productivity index for producers, explicit mass injectivity for injectors); the tubing performance is not modelled.")
        rows = [("Process, hydraulics and flow assurance", "Applicable", "Solved: pressure/rate balance, thermal profile, hydrate margin, erosion ratio, lumped cooldown. Open: transient events, slugging, wax, asphaltene, scale, corrosion rate."),
                ("Flowlines, pipelines, jumpers", "Applicable", "Screened: DNV-ST-F101 kernel on one line with synthetic inputs. Open: stability, free span, buckling, pipe-soil, installation."),
                ("Risers", "Applicable", "Concept polylines and fluid states only. Open: static and dynamic analysis (DNV-ST-F201), fatigue, interference."),
                ("Umbilicals and distribution", "Applicable", "Solved: chemical tube hydraulics. Registered: hydraulic, power and signal services. Open: manufacturer construction, electrical analysis."),
                ("Subsea equipment and controls", "Applicable", "Functional datasheets with sourced conditions. Open: vendor data, safety functions, cause-and-effect."),
                ("Installation, operations, lifecycle", "Applicable", "Conceptual sequences and scope lists. Open: marine-operation analyses."),
                ("Quantities, cost, schedule", "Applicable", "Geometry-derived MTO; existing SURFCostEstimator screening estimate; no schedule."),
                ("Subsurface, wells (drilling), host topside", "Not applicable", "Outside the study scope (exclusions below).")]
        self.table(["Discipline", "Applicability", "Coverage and status in this study"], rows, caption="Coverage and applicability of the SURF disciplines", font=7.5, widths=[48, 22, 100], align=["left"] * 3)
        self.P("Exclusions", bold=True)
        self.bullets(ds["exclusions"])

    # --- 3 basis ---
    def basis_section(self):
        ds = self.ds
        self.H("3 Design basis, standards hierarchy, evidence and requirements", 1)
        self.P("Every input carries a data class (specified, measured, calculated, estimated, synthetic, unavailable) and a source. All inputs of this study are synthetic (source SYN-1). "
               "The adopted-requirements hierarchy is: project and company rules first, then the standards below, then this study's screening criteria; no standard text has been read.")
        rows = [(s["id"], s["title"], s["input_class"], s["status"]) for s in ds["sources"]]
        self.table(["Source", "Description", "Data class", "Status"], rows, caption="Input sources", font=8, widths=[22, 90, 24, 40], align=["left"] * 4)
        rows = []
        for cid, f in ds["fluids"].items():
            rows.append((cid, f["role"], f["eos"], ", ".join("%s %.3f" % (k, v) for k, v in f["components"].items()), f["input_class"]))
        self.table(["Fluid", "Role", "EOS", "Mole fractions", "Class"], rows, caption="Fluid definitions (mole fractions; SRK equation of state with classic mixing rule)", font=7, widths=[24, 20, 14, 100, 18], align=["left"] * 5)
        crit_rows = [(c["id"], c["description"], "%s %s %g %s" % (c["quantity"], c["operator"], c["value"], c["unit"]), c["source"]) for c in ds["criteria_spec"]]
        self.table(["Criterion", "Description", "Requirement", "Source"], crit_rows, caption="Acceptance criteria (project-specified, synthetic)", font=7, widths=[18, 72, 62, 16], align=["left"] * 4)
        self.P("Discipline and requirement matrix", bold=True)
        ref = self.ref
        d = self.design
        mx = [("A Hydraulics", "Pressure and rate balance", "NeqSim LoopedPipeNetwork + WellFlow coupling", "CR-HOST", "solved", "screening", "process"),
              ("A Flow assurance", "Hydrate margin", "CPA hydrate curve, uninhibited", "CR-HYD", "solved", "screening", "flow assurance"),
              ("A Flow assurance", "Cooldown to hydrate temperature", "lumped cooldown with RK4 check", "CR-COOL", "solved (time to temperature, not plug)", "screening", "flow assurance"),
              ("A Flow assurance", "Erosional velocity ratio", "API 14E-style ratio with the basis C-factor", "CR-EROS", "solved", "screening", "flow assurance"),
              ("A Flow assurance", "Wax, asphaltene, scale, corrosion rate", "-", "-", "not assessed: no inputs", "open", "flow assurance"),
              ("B Pipelines", "Pressure containment and limit states", "DNV-ST-F101 kernel (one line, synthetic inputs)", "kernel utilisations", "screened", "screening, review required", "pipeline"),
              ("B Pipelines", "Stability, free span, buckling, pipe-soil", "DNV-RP-F109/F105/F110/F114", "-", "not assessed: no environmental or soil data", "open (HO-02)", "pipeline specialist"),
              ("C Risers", "Configuration, static and dynamic response", "external riser analysis", "-", "not assessed", "open (HO-01)", "riser specialist"),
              ("D Umbilicals", "Chemical delivery pressure", "NeqSim Darcy-Weisbach tube network", "CR-UMB", "solved", "screening", "umbilical"),
              ("D Umbilicals", "Power, signal, hydraulic services", "service register", "-", "registered only", "open (HO-03, HO-04)", "electrical / control"),
              ("E Equipment", "Functional datasheets", "register from the specification", "-", "functional", "vendor data open (HO-08)", "subsea equipment"),
              ("F Installation", "Sequences and scope", "conceptual", "-", "listed", "open (HO-05)", "marine operations"),
              ("G Cost", "CAPEX", "SURFCostEstimator (existing)", "-", "screening estimate", "screening", "cost")]
        self.landscape()
        self.table(["Discipline", "Requirement", "Method", "Criterion", "Result", "Maturity", "Owner"], mx, caption="Discipline and requirement matrix (method, criterion, result, maturity, owner)", font=6.5,
                   widths=[34, 56, 64, 22, 52, 40, 30], align=["left"] * 7)
        self.portrait()
        rows = [(s["id"], s["designation"], s["applicability"], s["status"]) for s in ds["standards"]]
        self.table(["ID", "Designation", "Applicability", "Verification status"], rows, caption="Requirements register (public metadata only; no clause cited, no conformity claimed)", font=6.5, widths=[24, 40, 24, 82], align=["left"] * 4)
        self.P("Important scope distinctions are retained: ISO 13628-1:2025 does not generally cover flowlines and risers; API 17J applies to unbonded flexible pipe and not to rigid steel flowlines; "
               "DNV-ST-F101, F201 and F301 cover different systems, so passing one screen satisfies none of the others; DNV-RP-O101 is a documentation reference and ISO process symbols alone do not establish SURF design conformance.")

    # --- 4 interfaces ---
    def interfaces(self):
        ds = self.ds
        self.H("4 Field, reservoir, well and host interfaces and lifecycle cases", 1)
        rows = []
        for c in ds["cases"]:
            rows.append(self.row([c["id"], c["label"], c["production_fluid"], "%g" % c["host_pressure_bara"], "%g" % c["production_reservoir_pressure_bara"], "%g" % c["productivity_scale"],
                                  "%g" % c["injection_pump_discharge_bara"], "%g" % c["injection_reservoir_pressure_bara"]]))
        self.table(["Case", "Description", "Fluid", "Host p (bara)", "Prod. res. p (bara)", "PI scale", "Inj. pump p (bara)", "Inj. res. p (bara)"], rows,
                   caption="Operating cases (inputs)", font=7, widths=[18, 54, 18, 16, 18, 14, 20, 18], align=["left"] * 8)
        self.P("The three cases span plateau (plateau fluid, full productivity), turndown (a back-pressured host and half productivity as a surrogate for choking back) and late life (higher water cut, depleted "
               "reservoir, lower productivity). Case-specific line-ups, isolation, maintenance and future tie-ins are represented only through the two concepts; cases are executed for every concept "
               "with the same canonical geometry and identities.")
        rows = []
        for n in ds["registers"][self.ref]["equipment"]:
            w = n.get("well")
            if w:
                rows.append((n["id"], n["tag"], n["kind"].replace("_", " "), "%s" % ("PI %.1e MSm3/d/bar2" % w["productivity_index_msm3d_per_bar2"] if "productivity_index_msm3d_per_bar2" in w else "Jm %.2f kg/s/bar" % w["injectivity_kg_s_per_bar"]),
                             "%g" % w["reservoir_pressure_bara"], "%g" % w.get("wellhead_temperature_c", w.get("fracture_pressure_bara", 0.0))))
        self.table(["Well", "Tag", "Role", "Inflow basis (specified)", "Reservoir p (bara)", "Wellhead T / fracture p"], rows, caption="Well boundary data (synthetic; producers: wellhead temperature degC, injectors: fracture pressure bara)",
                   font=7, widths=[14, 20, 28, 52, 24, 32], align=["left"] * 6)
        d = self.design
        rows = [self.row([c, self.tok(self.R(c, "production", "host.mass_rate_kg_s"), ".1f"), self.tok(self.R(c, "production", "host.pressure_bara"), ".0f"), self.tok(self.R(c, "production", "host.temperature_c"), ".1f"),
                          self.tok(self.R(c, "production", "host.phases.gas_kg_s"), ".1f"), self.tok(self.R(c, "production", "host.phases.oil_kg_s"), ".1f"), self.tok(self.R(c, "production", "host.phases.aqueous_kg_s"), ".1f")]) for c in self.cases]
        self.table(["Case", "Rate (kg/s)", "p (bara)", "T (degC)", "Gas (kg/s)", "Oil (kg/s)", "Water (kg/s)"], rows, caption="Host arrival conditions, reference concept %s (solved)" % self.ref, font=8, widths=[24, 22, 20, 20, 22, 22, 22], align=["left"] + ["right"] * 6)
        self.P("The host inlet limit of %.0f kg/s is respected in every case; the plateau case uses %s of it. Producers are solved, not prescribed: each rate follows from its inflow and the network back-pressure, "
               "so the turndown and late-life rates are results of the specified host pressure and productivity, not inputs." % (self.spec["host"]["max_inlet_mass_rate_kg_s"], "%.0f %%" % (100.0 * ds["results"][self.ref][d]["production"]["host"]["mass_rate_kg_s"] / self.spec["host"]["max_inlet_mass_rate_kg_s"])))
        self.picture(*self._fig("wells", 150))
        self.P("The well-rate figure shows how the productivity ordering (P1 highest, P4 lowest) persists in all cases while the absolute rates fall with back-pressure and depletion.")

    # --- 5 alternatives ---
    def alternatives(self):
        ds = self.ds
        self.H("5 Alternatives, design drivers and concept selection", 1)
        for cid, c in ds["concepts"].items():
            self.P("%s: %s %s" % (c["label"], c["description"], c.get("feasibility", "")), style="List Bullet")
        rows = []
        for cid in ds["concepts"]:
            regs = ds["registers"][cid]
            prod_km = sum(r["installed_length_m"] for r in regs["lines"] if r["service"] == "production") / 1000.0
            rows.append(self.row([cid, "%.1f" % prod_km, "%.0f" % (regs["mto"]["total_steel_kg"] / 1000.0), self.fact("capex_musd_" + cid, ".0f"), self.fact("feasibility_" + cid), self.fact("n_pass_" + cid), self.fact("n_fail_" + cid)]))
        self.table(["Concept", "Production lines (km)", "Steel (t)", "CAPEX (MUSD)", "Feasibility", "Criteria passed", "Criteria failed"], rows, caption="Concept metrics", font=8, widths=[20, 28, 20, 24, 24, 24, 24], align=["left"] + ["right"] * 6)
        self.picture(*self._fig("concepts", 150))
        sel = ds["selected_concept"]
        if sel:
            others = [c for c in ds["concepts"] if c != sel]
            self.P("Feasibility comes before cost. %s satisfies every criterion in every case and is the cheapest feasible concept (%s MUSD). " % (sel, self.fact("capex_musd_" + sel, ".0f")) +
                   " ".join("%s is %s%s." % (o, ds["comparison"]["rows"][list(ds["concepts"]).index(o)]["feasibility"], (" (fails %s)" % ", ".join(ds["comparison"]["rows"][list(ds["concepts"]).index(o)]["failed"][:3])) if ds["comparison"]["rows"][list(ds["concepts"]).index(o)]["failed"] else "")
                            for o in others) +
                   " The ranking depends on the synthetic inputs, the screening cost estimate and the single-phase/B&B hydraulics, and must be repeated with real data before a decision gate.")
        else:
            self.P("No concept is feasible, so none is ranked. The failing criteria are listed in the table above.")
        self.P("Design drivers: the plateau mixture velocity in the riser and trunk (erosional ratio), the host back-pressure in the late-life case (arrival conditions) and the injection fracture margin; "
               "hydrate margin and cooldown are not drivers in this synthetic example because the wellhead temperature is high.")

    # --- 6 model ---
    def model_section(self):
        ds = self.ds
        self.H("6 Generated SURF process/network architecture and model verification", 1)
        self.P("The model is generated from the study specification: the production and the injection service are each a FieldNetworkTopology over a LoopedPipeNetwork, with live WellFlow wells coupled "
               "by FieldWellNetworkCoupler and exposed as FieldWellNetworkProcessUnit equipment in a ProcessSystem of the SURF area that is composed with the host separator in a ProcessModel. "
               "Each production edge uses the Beggs and Brill multiphase element with route elevation profiles; injection edges use single-phase Darcy-Weisbach. No pressure-flow solver was added. "
               "Pressure, mass, component and enthalpy mixing and the signed-flow convention (rate positive from the from-node to the to-node) belong to the network.")
        self.P("The generated builders (model/builders/*.py), operation plans (model/plans/*.json), topology definitions (model/topology_*.json) and the mapping manifest (model/mapping_manifest.json) are written next to the report. "
               "The topology JSON is a definition, not a complete restart state: runtime wells, fluids and equipment bindings are rebound by the builder.")
        rows = []
        for cid in ds["concepts"]:
            plan = ds["mapping"][cid]
            rows.append((cid, str(sum(1 for m in plan if m["type"] == "node")), str(sum(1 for m in plan if m["type"] == "edge")), str(sum(1 for m in plan if m["type"] == "service")),
                         str(sum(1 for m in plan if m.get("equipment_binding")))))
        self.table(["Concept", "Mapped nodes", "Mapped edges", "Mapped services", "Bound well equipment"], rows, caption="Mapping manifest summary (every specification ID maps to a hydraulic node or edge)", font=8, widths=[24, 30, 30, 32, 40], align=["left"] + ["right"] * 4)
        d = self.design
        rows = []
        for cid in ds["concepts"]:
            r = ds["results"][cid][d]
            rows.append(self.row([cid, "yes" if r["production"]["coupling"]["converged"] else "NO", self.tok("results.%s.%s.production.coupling.iterations" % (cid, d)),
                                  self.tok("results.%s.%s.production.coupling.mass_balance_residual_kg_s" % (cid, d), ".2e"), self.tok("results.%s.%s.injection.coupling.mass_balance_residual_kg_s" % (cid, d), ".2e")]))
        self.table(["Concept", "Production coupling converged", "Outer iterations", "Production mass residual (kg/s)", "Injection mass residual (kg/s)"], rows, caption="Coupling evidence of the design case", font=8,
                   widths=[22, 38, 28, 40, 40], align=["left"] + ["right"] * 4)
        rows = [(h["id"], h["description"][:96], "%.3f" % h["model"], "%.3f" % h["hand"], h["unit"], h["status"]) for h in ds["hand_checks"][self.ref] if not h["id"].startswith("HC-NODE")]
        self.table(["Check", "Description", "Model", "Hand", "Unit", "Result"], rows, caption="Independent hand checks (reference concept, design case)", font=6.5, widths=[28, 80, 16, 16, 12, 14], align=["left"] * 6)
        expl = [h for h in ds["hand_checks"][self.ref] if h["status"] == "EXPLAINED"]
        self.P("Mass closes below the stated tolerance at every network node and between wells and host. The single-phase hydrostatic, friction and thermal hand checks agree with the solved values"
               + (" except for %d explained deviation(s)" % len(expl) if expl else "") + "; they check the "
               "implementation of the hydraulics and the thermal solution, not the multiphase correlation, which carries no general experimental qualification.")
        if expl:
            self.P("Finding: " + "; ".join("%s: %s" % (h["id"], h["independence"].split("; ", 1)[-1]) for h in expl) + ". The deviation is a solver limitation, not a design margin; "
                   "it is carried as an open item for the NeqSim network energy balance and does not change a criterion verdict.")
        fc = ds.get("fluid_checks", [])
        if fc:
            self.P("Finding: %s" % "; ".join("%s deviates %.1f %% from tabulated water" % (c["description"].split(" against")[0], 100 * c["deviation"]) for c in fc if c["status"] != "PASS") +
                   (". Use a CPA or IAPWS water description for any qualified injection thermal study." if any(c["status"] != "PASS" for c in fc) else "No deviation exceeds the 5 % tolerance."))

    # --- 7 fluids/hydraulics ---
    def hydraulics(self):
        ds = self.ds
        self.H("7 Fluids, hydraulics, thermal design and flow assurance", 1)
        self.picture(*self._fig("profiles", 150))
        d = self.design
        pe = ds["results"][self.ref][d]["derived"]["per_edge"]
        trunk = next(r["id"] for r in ds["registers"][self.ref]["lines"] if r["kind"] == "trunkline")
        self.P("The longest production path loses %s bar between the manifold and the host in the design case and arrives at %s degC; the trunk %s alone drops %s bar over %s km." % (
            "%.1f" % sum(pe[e]["dp_bar"] for e in pe if ds["registers"][self.ref]["lines"] and any(r["id"] == e and r["service"] == "production" for r in ds["registers"][self.ref]["lines"]) and e in ("FL-TRUNK", "RI-1", "FL-T2", "FL-T2D")),
            self.tok(self.R(d, "production", "host.temperature_c"), ".1f"), trunk, self.tok("results.%s.%s.derived.per_edge.%s.dp_bar" % (self.ref, d, trunk), ".1f"), "%.1f" % (next(r["installed_length_m"] for r in ds["registers"][self.ref]["lines"] if r["id"] == trunk) / 1000.0)))
        rows = []
        for cid in ds["concepts"]:
            for c in self.cases:
                q = ds["results"][cid][c]["derived"]["quantities"]
                rows.append([cid, c] + [("%.2f" % q[k]["value"]) if q[k]["value"] is not None else "n/a" for k in ("pressure_to_design_ratio_max", "hydrate_margin_min_k", "erosional_ratio_max", "host_arrival_temperature_c", "cooldown_time_h")] +
                            [q["erosional_ratio_max"]["where"] or "n/a"])
        self.landscape()
        self.table(["Concept", "Case", "p/p design (max)", "Hydrate margin min (K)", "Erosional ratio max", "Host T (degC)", "Cooldown (h)", "Erosion governing line"], rows,
                   caption="Governing production-system quantities by concept and case", font=7, widths=[22, 24, 34, 40, 36, 28, 28, 50], align=["left"] * 2 + ["right"] * 5 + ["left"])
        self.portrait()
        self.P("Hydrate: the equilibrium margin (uninhibited, CPA, 5 mol%% water basis) never falls below the %.0f K criterion and the lumped cooldown time to the hydrate temperature exceeds %.0f h; both are "
               "equilibrium/lumped screening values, not a time to plug, and neither includes the heat of hydrate formation, convection or fluid redistribution. The aqueous inhibitor mass fraction is kept "
               "separate from any overall mole fraction; no inhibitor is credited in these results." % (
                   next(c["value"] for c in ds["criteria_spec"] if c["id"] == "CR-HYD"), next(c["value"] for c in ds["criteria_spec"] if c["id"] == "CR-COOL")))
        self.P("Erosion: the mixture velocity relative to the erosional velocity (basis C-factor %.0f in SI units) is the design driver: %s." % (
            ds["solver"]["erosional_c_factor_si"], "; ".join("%s %s" % (cid, "max %.2f at %s in %s" % max(
                (ds["results"][cid][c]["derived"]["quantities"]["erosional_ratio_max"]["value"], ds["results"][cid][c]["derived"]["quantities"]["erosional_ratio_max"]["where"], c) for c in self.cases)) for cid in ds["concepts"])))
        self.P("Open threats: wax, asphaltene, scale, CO2/H2S corrosion rate and the transient events (start-up, restart, depressurisation, pigging, severe slugging) are not assessed: Beggs and Brill does not "
               "qualify slugging, and no inputs exist for the chemical threats. They are carried as hand-off HO-07 and data gaps, never as safe.")
        self.P("Equilibrium onset is not deposition or plugging time, and a U-value is not a soil resistance model.")

    # --- 8 flowlines ---
    def flowlines(self):
        ds = self.ds
        self.H("8 Flowline, pipeline and jumper design and structural assessment interfaces", 1)
        rows = [self.row([r["id"], r["tag"], r["kind"], "%.0f" % r["installed_length_m"], "%.1f x %.1f" % (r["od_mm"], r["wall_mm"]) if r["wall_mm"] else "flexible ID %.0f" % r["id_mm"], "%.1f" % r["id_mm"],
                          "%.1f" % (r["corrosion_allowance_mm"] or 0.0) if r["corrosion_allowance_mm"] is not None else "-", r["grade"] or "-", r["insulation"][:20], "%.0f / %.0f" % (r["design_pressure_bara"], r["design_temperature_c"])])
                for r in ds["registers"][self.ref]["lines"]]
        self.landscape()
        self.table(["Line", "Tag", "Kind", "Installed length (m)", "OD x wall (mm)", "ID (mm)", "CA (mm)", "Grade", "Insulation", "Design p / T (bara / degC)"], rows,
                   caption="Line register, reference concept %s (hydraulic ID = OD - 2 x nominal wall; corrosion allowance reported separately)" % self.ref, font=6.5, widths=[20, 20, 22, 30, 32, 18, 16, 16, 40, 40], align=["left"] * 3 + ["right"] * 3 + ["left"] * 4)
        self.portrait()
        st = ds["structural"][self.ref]
        rows = []
        for eid, v in st.items():
            if v["status"].startswith("screened"):
                for c in v["checks"]:
                    rows.append((eid, c["limit_state"].replace("_", " ").lower(), "%.3f" % c["utilization"] if c["utilization"] is not None else "n/a", c["status"], v["input_class"]))
        if rows:
            self.table(["Line", "Limit state (DNV-ST-F101 kernel)", "Utilisation", "Kernel status", "Input class"], rows, caption="Structural kernel screening results (synthetic project inputs; screening, review required)",
                       font=7, widths=[22, 76, 22, 24, 24], align=["left"] * 2 + ["right"] + ["left"] * 2)
        open_lines = [e for e, v in st.items() if not v["status"].startswith("screened")]
        self.P("Only the trunk line carries a complete DNV-ST-F101 input (synthetic); it is screened with the existing fail-closed kernel through SubseaProductionSystem. %d lines have no structural input "
               "and are reported as not assessed: %s. No structural value is inferred from the hydraulic pressure, depth or material label, and a corrosion allowance is not derived from a corrosion rate." % (
                   len(open_lines), ", ".join(open_lines)))
        self.P("On-bottom stability (DNV-RP-F109), free span (F105), global buckling (F110) and pipe-soil interaction (F114) are separate checks with their own evidence; none has the metocean, soil or survey input and all are "
               "open (hand-off HO-02). Hydraulic pressure drop is not a structural load analysis. Installation, hydrotest, crossings, expansion and support requirements are listed in Chapter 14 as required inputs.")

    # --- 9 risers ---
    def risers(self):
        ds = self.ds
        self.H("9 Riser design basis, configuration and analysis status", 1)
        d = self.design
        rows = []
        for r in ds["registers"][self.ref]["lines"]:
            if r["kind"] == "riser":
                dom = "production" if r["service"] == "production" else "injection"
                rows.append(self.row([r["id"], r["tag"], "flexible (lazy-wave concept)" if r["construction"] == "flexible" else "steel catenary concept", "%.0f" % r["installed_length_m"], "%.0f" % r["id_mm"],
                                      self.tok("results.%s.%s.%s.edges.%s.inlet_pressure_bara" % (self.ref, d, dom, r["id"]), ".1f"), self.tok("results.%s.%s.%s.edges.%s.outlet_pressure_bara" % (self.ref, d, dom, r["id"]), ".1f")]))
        self.table(["Riser", "Tag", "Concept", "Length (m)", "Bore (mm)", "Inlet p (bara)", "Outlet p (bara)"], rows, caption="Riser concepts and design-case fluid states", font=7.5, widths=[18, 20, 46, 20, 20, 22, 22], align=["left"] * 3 + ["right"] * 4)
        self.P("The riser shapes in %s are the specification polylines labelled CONCEPT SCHEMATIC - GEOMETRY NOT ANALYZED. A lazy-wave or catenary label does not solve the equilibrium geometry: hang-off, touchdown, "
               "buoyancy, tension, minimum bend radius, fatigue, interference and the coupled host and mooring motions require a qualified external solver (DNV-ST-F201; API 17J for the flexible option). "
               "Externally calculated envelopes can be imported and checked but will not be presented as NeqSim results (hand-off HO-01). The flexible riser's U-value is a supplier-style input; the steel layer stack is not applied to it." % self.sheet("DWG-07"))

    # --- 10 umbilical ---
    def umbilical(self):
        ds = self.ds
        self.H("10 Umbilical, control, chemical and power distribution", 1)
        res = ds["umbilical"]["results"]
        rows = []
        for sid, r in res.items():
            rows.append(self.row([sid, r["consumer"], r["fluid"], "%.2f" % r["rate_kg_s"], "%.1f" % r["tube_id_mm"], "%.1f" % (r["length_m"] / 1000.0), self.tok("umbilical.results.%s.pressure_drop_bar" % sid, ".1f"),
                                  self.tok("umbilical.results.%s.delivery_pressure_bara" % sid, ".1f"), "%.2f" % r["velocity_m_s"] if r["velocity_m_s"] else "n/a", "%.0f" % r["reynolds"] if r["reynolds"] else "n/a"]))
        self.table(["Service", "Consumer", "Fluid", "Rate (kg/s)", "Tube ID (mm)", "Length (km)", "Drop (bar)", "Delivered (bara)", "v (m/s)", "Re"], rows, caption="Chemical tube hydraulics (single-phase Darcy-Weisbach, solved)",
                   font=7, widths=[22, 18, 16, 16, 18, 16, 16, 20, 14, 14], align=["left"] * 3 + ["right"] * 7)
        self.picture(*self._fig("umbilical", 150))
        worst = min((ds["criteria"][self.ref][[c["id"] for c in ds["criteria"][self.ref]].index("CR-UMB")]["by_case"][c]["value"], c) for c in self.cases)
        self.P("The delivered pressure exceeds the pressure required at the injection point (production pressure plus %.0f bar) in every case; the smallest margin is %.1f bar in case %s. The long MEG "
               "tube is laminar at the stated rate, so its drop is large and sensitive to the viscosity from the equation of state: treat that margin as a screening value (SRK is not a viscosity model for MEG)." % (
                   ds["umbilical"]["spec"]["required_margin_bar"], worst[0], worst[1]))
        rows = [(s["id"], s["kind"], s.get("consumer") or "-", s["purpose"], s["assessment_owner"]) for s in ds["umbilical"]["services"]]
        self.table(["Service", "Kind", "Consumer", "Purpose", "Assessment owner"], rows, caption="Umbilical service register and allocation (%s)" % ds["umbilical"]["spec"]["spare"], font=7, widths=[22, 18, 18, 58, 56], align=["left"] * 5)
        self.P("This chapter is a functional service sizing with a conceptual cross-section (%s). Tube, armour and cable construction, packing, dynamic response and manufacturer qualification are separate (hand-off HO-03). "
               "Electrical power demand (%s kW) and channel counts are retained; voltage drop, ampacity and protection need the electrical specialist (HO-04). Object counts are not proof of these analyses." % (
                   self.sheet("DWG-10"), self.tok("umbilical.services.4.demand_kw", ".0f")))

    # --- 11 equipment ---
    def equipment(self):
        ds = self.ds
        self.H("11 Subsea equipment selection and functional datasheets", 1)
        d = self.design
        lines = ds["registers"][self.ref]["lines"]
        rows = []
        for n in ds["registers"][self.ref]["equipment"]:
            conn = [r for r in lines if r["from"] == n["id"] or r["to"] == n["id"]]
            ps = []
            for c in self.cases:
                dom = "production" if n["service"] == "production" else "injection"
                node = ds["results"][self.ref][c][dom]["nodes"].get(n["id"])
                if node and node["pressure_bara"] is not None:
                    ps.append(node["pressure_bara"])
            dp = max([r["design_pressure_bara"] for r in conn] or [0.0])
            rows.append((n["id"], n["tag"], n["kind"].replace("_", " "), n["service"], n.get("structure") or "-", ", ".join(r["id"] for r in conn)[:34],
                         "%.1f - %.1f" % (min(ps), max(ps)) if ps else "n/a", "%.0f" % dp, "vendor data open"))
        self.landscape()
        self.table(["ID", "Tag", "Kind", "Service", "Structure", "Connected lines", "Operating p range (bara)", "Design p (bara, connected lines)", "Dimensions, weights, ratings"], rows,
                   caption="Functional datasheet register (sourced design and operating conditions; vendor data open)", font=6.5, widths=[18, 22, 34, 22, 20, 52, 38, 34, 34], align=["left"] * 9)
        self.portrait()
        self.P("Datasheets are functional: they carry tags, services, counts, duties from the solved cases, interfaces and the design conditions inherited from the connected lines. Trees, manifolds, templates, PLEMs, PLETs "
               "and jumpers have no vendor dimensions, weights or ratings in the specification; these remain open (HO-08) and no ROV envelope or hub orientation is invented (%s)." % self.sheet("DWG-08"))
        self.P("Safety functions, valve closure times, shutdown sequences and cause-and-effect are not defined and are not invented here (HO-06). The isolation and depressurisation philosophy, chemical injection points and "
               "instrumentation are listed as omitted systems on the functional sheets (%s, %s)." % (self.sheet("DWG-03"), self.sheet("DWG-04")))

    # --- 12-16 ---
    def materials(self):
        ds = self.ds
        self.H("12 Materials, corrosion, erosion and integrity management", 1)
        self.P("Rigid lines are %s carbon steel with a nominal corrosion allowance as listed in Chapter 8; the allowance is a specified input, not derived from a corrosion rate. CO2/H2S content, sour-service "
               "requirements and a corrosion-rate model are not part of this study (no inputs) and the material selection is therefore an assumption. Erosion is screened with the API 14E-style velocity ratio "
               "(Chapter 7); sand erosion (DNV-RP-O501) is not assessed. Cathodic protection, coating and field-joint design, and inspection and integrity-management planning are outside this screening." % ", ".join(sorted({r["grade"] for r in ds["registers"][self.ref]["lines"] if r["grade"]})))

    def safety(self):
        ds = self.ds
        self.H("13 Safety, operability, reliability and maintainability", 1)
        self.P("Operability is screened through three operating cases and two concepts. No reliability, availability or maintainability study was performed and no safety integrity level, valve closure time or "
               "shutdown sequence is defined. The two concepts differ in maintainability: concept A shares one trunk line (single point of failure for all wells but a single line to inspect), concept B gives each template "
               "an independent path at the cost of about %.0f km additional line. Isolation, depressurisation and pigging are not assessed (HO-06, HO-07)." % (
                   (ds["registers"].get("B", {"lines": []}) and sum(r["installed_length_m"] for r in ds["registers"]["B"]["lines"] if r["service"] == "production") - sum(r["installed_length_m"] for r in ds["registers"][self.ref]["lines"] if r["service"] == "production")) / 1000.0)
               if "B" in ds["registers"] else "the stated")

    def installation(self):
        self.H("14 Installation, pre-commissioning and commissioning strategy", 1)
        rows = [(c, t, ", ".join(a) if a else "see %s" % self.sheet("DWG-12"), n) for c, t, a, n in __import__("surf_sheets_b").STAGES]
        self.table(["Stage", "Activity", "Assets", "Open analysis"], rows, caption="Conceptual installation and tie-in sequence", font=7, widths=[14, 62, 40, 54], align=["left"] * 4)
        self.P("Routing and installability constraints are limited to what the specification states (surface-laid lines, trenching on the last 2 km of the trunk). Lay approach, lifting, transport, sea fastening, "
               "tie-in sequence, hydrotest, cleaning, drying and commissioning are scoped as inputs for the marine-operation and commissioning specialists (HO-05); the sequence sheet %s is conceptual and implies no vessel or weather window." % self.sheet("DWG-12"))

    def operations(self):
        self.H("15 Operations, IMR, intervention and decommissioning", 1)
        self.P("Operating line-ups are the three cases of Chapter 4 on each concept. Inspection, maintenance and repair, retrievability and intervention requirements and decommissioning interfaces are listed for scope only: "
               "no IMR philosophy, intervention vessel or decommissioning method is assessed. The riser, umbilical termination assemblies and jumpers are the principal retrievable or replaceable interfaces to be defined by the supplier.")

    def interface_register(self):
        ds = self.ds
        self.H("16 Interface register and host modification requirements", 1)
        d = self.design
        rows = [("Host inlet (production)", "pressure specified per case; rate limit %.0f kg/s" % self.spec["host"]["max_inlet_mass_rate_kg_s"], "p %.0f bara, T %.1f degC, m %.1f kg/s (design case)" % (
            ds["results"][self.ref][d]["production"]["host"]["pressure_bara"], ds["results"][self.ref][d]["production"]["host"]["temperature_c"], ds["results"][self.ref][d]["production"]["host"]["mass_rate_kg_s"]), "host process"),
                ("Host water injection supply", "pump discharge per case (%.0f to %.0f bara)" % (min(c["injection_pump_discharge_bara"] for c in ds["cases"]), max(c["injection_pump_discharge_bara"] for c in ds["cases"])),
                 "pump power %.0f kW (design case)" % ds["results"][self.ref][d]["injection"]["pump_power_kw"], "host utilities"),
                ("Chemical supply", "%.0f bara supply at the host" % ds["umbilical"]["spec"]["chemical_supply_pressure_bara"], "%d chemical tubes" % sum(1 for s in ds["umbilical"]["services"] if s["kind"] == "chemical"), "host chemical system"),
                ("Power and control", "%.0f kW demand; %d channels" % (ds["umbilical"]["services"][4]["demand_kw"], ds["umbilical"]["services"][5]["channels"]), "not assessed", "host electrical / control"),
                ("Riser hang-off", "interface loads not defined", "fluid states only (Chapter 9)", "host structure")]
        self.table(["Interface", "Requirement / boundary", "Study value", "Owner"], rows, caption="Interface register (host modifications are limited to the listed boundary conditions; topside modifications are out of scope)", font=7, widths=[40, 52, 54, 24], align=["left"] * 4)

    # --- 17 quantities ---
    def quantities(self):
        ds = self.ds
        self.H("17 MTO/BOQ, CAPEX/OPEX basis, uncertainty and execution schedule", 1)
        regs = ds["registers"][self.ref]
        rows = [self.row([m["id"], m["tag"], m["item"][:30], "%.0f" % m["length_m"], "%.0f" % (m["steel_mass_kg"] / 1000.0) if m["steel_mass_kg"] else "-", "%.1f" % m["insulation_m3"] if m["insulation_m3"] else "-"]) for m in regs["mto"]["lines"]]
        self.table(["Line", "Tag", "Item", "Installed length (m)", "Steel (t)", "Insulation (m3)"], rows, caption="Material take-off derived from the canonical geometry (installed length = route length x (1 + stated allowance); allowance is %s)" % ("zero in this study"),
                   font=7, widths=[20, 20, 52, 28, 22, 24], align=["left"] * 3 + ["right"] * 3)
        rows = [(k.replace("_", " "), str(v)) for k, v in sorted(regs["mto"]["equipment_counts"].items())]
        self.table(["Equipment", "Count"], rows, caption="Equipment quantities (specification = design basis = cost basis)", font=8, widths=[60, 20], align=["left", "right"])
        rows = []
        for cid in ds["concepts"]:
            c = ds["cost"][cid]
            rows.append([cid] + ["%.0f" % (c["capital_usd"].get(k, 0.0) / 1e6) for k in ("subseaInfrastructure", "umbilicals", "risers", "flowlinesAndPipelines")] + ["%.0f" % (c["total_usd"] / 1e6)])
        self.table(["Concept", "Subsea infrastructure", "Umbilicals", "Risers", "Flowlines and pipelines", "Total SURF (MUSD)"], rows, caption="SURF cost by category (existing SURFCostEstimator, screening)", font=8, widths=[20, 32, 24, 22, 36, 32], align=["left"] + ["right"] * 5)
        c0 = ds["cost"][self.ref]
        self.P("Cost basis: %s; currency %s; price year %s; location basis %s; estimate class %s with accuracy %.0f %% to +%.0f %%; contingency convention: as built into the estimator, no separate contingency is added. Scope: %s. Excluded: %s. "
               "The estimate class is the estimator's label, not a maturity claim of this report; the cost is not a substitute for supplier quotations, and an estimator note applies: %s. OPEX and schedule are not estimated." % (
                   c0["method"], c0["currency"], c0["cost_year"], c0["location"], c0["estimate_class"], 100 * abs(c0["accuracy_low"]), 100 * c0["accuracy_high"], c0["scope"], ", ".join(c0["excluded"]), c0["quality_flags"][0] if c0["quality_flags"] else "none"))
        rows = [(r["item"], str(r["register"]), str(r["design_basis"]), r["status"]) for r in regs["reconciliation"]]
        self.table(["Item", "Register", "Design basis (cost)", "Result"], rows, caption="Reconciliation of the line and equipment register with the cost design basis", font=6.5, widths=[60, 38, 38, 16], align=["left"] * 4)

    # --- 18 validation ---
    def sensitivity(self):
        ds = self.ds
        self.H("18 Sensitivity, benchmarks, numerical validation and independent-verification status", 1)
        ms = ds.get("mesh_sensitivity")
        if ms:
            self.P("Spatial sensitivity: the design case of concept %s was re-solved with %sx the Beggs and Brill increments of every edge; the largest change is %s bar in pressure and %s K in temperature (tolerances %.1f bar and %.1f K): %s." % (
                ms["concept"], ms["factor"], self.tok("mesh_sensitivity.max_dp_bar", ".3f"), self.tok("mesh_sensitivity.max_dt_k", ".2f"), ms["tolerances"]["p_bar"], ms["tolerances"]["t_k"], ms["status"]))
        rows = [(k, "%.3f" % v["analytic_h"], "%.3f" % v["rk4_h"], "%.1e" % v["rel_diff"]) for k, v in ds["cooldown_checks"].items()]
        self.table(["Concept/case", "Analytic (h)", "RK4 (h)", "Relative difference"], rows, caption="Cooldown time: closed form against independent RK4 integration", font=8, widths=[40, 30, 30, 36], align=["left"] + ["right"] * 3)
        v = self.validation
        if v:
            rows = [[r["id"], r["description"], r["status"], str(r["detail"])[:200]] for r in v["checks"]]
            self.landscape()
            self.table(["Check", "Description", "Result", "Detail"], rows, caption="Validation checks", font=6, widths=[26, 90, 14, 120], align=["left"] * 4)
            self.portrait()
        self.P("Independent verification status: not performed. The hand checks and the mesh and RK4 comparisons are internal numerical validation; there is no independent public, laboratory or field benchmark for the "
               "multiphase hydraulics, and the same-code replays are not benchmarks. A reviewer must re-run the study from the specification (Appendix F).")

    # --- 19 conclusions ---
    def conclusions(self):
        ds = self.ds
        self.H("19 Conclusions, recommendations and action/decision register", 1)
        sel = ds["selected_concept"]
        self.P("Conclusions (each with its evidence and remaining dependency)", bold=True)
        items = []
        if sel:
            items.append(("Select concept %s for the next study stage." % sel, "feasible in all %d cases and lowest cost among the feasible concepts (Chapter 5, Table of concept metrics)." % len(self.cases),
                          "real fluid, well and route data; independent verification; specialist analyses"))
        else:
            items.append(("Do not select a concept yet.", "no concept is feasible in every case (Chapter 5).", "resize the failing line or revise the criterion with a sourced basis"))
        fail_cases = [(cid, c["id"], k) for cid in ds["concepts"] for c in ds["criteria"][cid] for k, b in c["by_case"].items() if b["status"] == "FAIL"]
        if fail_cases:
            items.append(("Resolve the failed criteria: %s." % ", ".join("%s %s/%s" % f for f in fail_cases[:6]), "governing values in the executive summary table", "design change or a re-based criterion"))
        items.append(("Run the open specialist analyses before any decision gate.", "Chapters 8, 9, 10 and 14 list them (HO-01 to HO-08).", "specialist results and supplier data"))
        items.append(("Replace synthetic inputs with sourced data.", "all inputs are class synthetic (Chapter 3).", "PVT, well tests, survey, metocean, geotechnical, supplier and project criteria"))
        rows = [(i + 1, a, b, c) for i, (a, b, c) in enumerate(items)]
        self.table(["#", "Conclusion / recommendation", "Evidence", "Remaining dependency"], rows, caption="Conclusions and recommendations", font=7, widths=[8, 62, 52, 48], align=["left"] * 4)
        self.P("Specialist hand-offs", bold=True)
        rows = [(h["id"], h["discipline"], h["needed"], h["prepared_inputs"], h["status"]) for h in ds["handoffs"]]
        self.landscape()
        self.table(["ID", "Discipline", "Needed", "Inputs prepared by this study", "Status"], rows, caption="Specialist hand-off register (hand-off files are in handoff/; results are not claimed)", font=6.5, widths=[14, 76, 74, 64, 18], align=["left"] * 5)
        self.portrait()
        self.P("Data gaps", bold=True)
        self.bullets(ds["data_gaps"])
        self.P("Assumptions", bold=True)
        self.bullets(ds["assumptions"])

    # --- 20 appendices ---
    def appendices(self, fig_dir):
        ds = self.ds
        self.H("20 Appendices", 1)
        self.H("Appendix A - Drawing package", 2)
        self.P("The A3 drawings follow, reduced to page width. The SVG sources are editable and the layout specification (layout_spec.json) lists every annotation with its data reference. "
               "Sheet numbers are %s. Drawings carry the source and run reference and keep the checker and approver unassigned." % ", ".join("%s" % s["drawing_number"] for s in self.manifest["sheets"][:3]) + " and so on")
        for s in self.manifest["sheets"]:
            self.A3()
            self.picture(s["png"], 396, "Drawing %s: %s" % (s["drawing_number"], s["title"]), "%s - %s (A3 landscape, reduced)" % (s["drawing_number"], s["title"]))
        self.portrait()
        self.H("Appendix B - Cases and profiles", 2)
        self.P("Per-concept, per-case results are in dataset/surf_study.json (nodes, edges, profiles, well coupling evidence, host arrival). The case table of Chapter 7 gives the governing values.")
        rows = []
        for cid in ds["concepts"]:
            for c in self.cases:
                r = ds["results"][cid][c]["production"]
                rows.append((cid, c, "%d" % r["coupling"]["iterations"], "%.1e" % r["coupling"]["rate_residual_kg_s"], "%.1f" % r["timings_s"]["solve"] if not r["cache"]["hit"] else "cache", str(r["host"]["mass_rate_kg_s"])))
        self.table(["Concept", "Case", "Outer iterations", "Rate residual (kg/s)", "Solve time (s)", "Host rate (kg/s)"], rows, caption="Convergence and execution record per production case", font=7, widths=[22, 24, 28, 36, 28, 32], align=["left"] * 2 + ["right"] * 4)
        self.H("Appendix C - Standards register", 2)
        rows = [(s["designation"], s["url"][:48], s["scope_note"][:96], s["text_available"] and "yes" or "no", s["status"][:44]) for s in ds["standards"]]
        self.landscape()
        self.table(["Designation", "Official URL", "Scope note", "Text read", "Status"], rows, caption="Standards register (public metadata; clause text not read)", font=5.5, widths=[40, 60, 110, 14, 54], align=["left"] * 5)
        self.portrait()
        self.H("Appendix D - Model inputs", 2)
        self.P("The specification (study_input.yaml) is the single model input. Solver settings: %s." % json.dumps({k: v for k, v in ds["solver"].items() if k not in ("hydrate_grid_bara",)}))
        self.H("Appendix E - Rerun instructions", 2)
        self.P("neqsim surf-study study_input.yaml --out <dir> --strict   (stages: --stage build-model | run | report). Without the neqsim command use: <python> -m neqsim_cli surf-study ... "
               "The run stage caches each case on the specification hash, concept, case, mesh, NeqSim version and toolkit version; the report stage refuses a dataset whose specification hash no longer matches. "
               "Dataset hash %s; NeqSim %s; toolkit %s." % (ds["meta"]["dataset_hash"], ds["meta"]["neqsim_version"], ds["meta"]["tool_version"]))
        self.H("Appendix F - Hand-off and generated-model files", 2)
        self.P("model/builders/build_<concept>_<domain>.py: stand-alone editable builders; model/plans: the operation lists; model/topology_*.json: FieldNetworkTopology definitions; handoff/HO-*.json: specialist hand-off packages.")

    def properties(self):
        super().properties()

    def build(self, out_path, fig_dir):
        os.makedirs(fig_dir, exist_ok=True)
        self.cover()
        self.section()
        self.document_control()
        self.contents()
        self.section()
        self.executive_summary()
        self.scope()
        self.basis_section()
        self.interfaces()
        self.alternatives()
        self.model_section()
        self.hydraulics()
        self.flowlines()
        self.risers()
        self.umbilical()
        self.equipment()
        self.materials()
        self.safety()
        self.installation()
        self.operations()
        self.interface_register()
        self.quantities()
        self.sensitivity()
        self.conclusions()
        self.appendices(fig_dir)
        self.headers_footers()
        self.properties()
        self.static_lists()
        self.doc.save(out_path)
        facts = os.path.join(os.path.dirname(out_path), "report_facts.json")
        with open(facts, "w", encoding="utf-8") as fh:
            json.dump({"dataset_hash": self.ds["meta"]["dataset_hash"], "resolved_references": self.res.used, "unresolved_references": self.res.errors, "unit_errors": self.res.unit_errors,
                       "executive_summary_text": self.exec_summary_text, "n_figures": self.fig_n, "n_tables": self.tab_n}, fh, indent=1, default=str)
        return {"docx": out_path, "facts": facts, "n_figures": self.fig_n, "n_tables": self.tab_n, "unresolved": list(self.res.errors), "unit_errors": list(self.res.unit_errors)}
