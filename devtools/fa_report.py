"""Word report builder of the flow-assurance study report.

``FaReportBuilder`` extends :class:`psr_report.ReportBuilder` (document styles, cover, document control, contents, captions,
headers and footers are reused) and replaces the process-plant core by flow-assurance chapters. Every number in the
narrative is written through ``[[token]]`` references resolved against the canonical dataset by
:class:`fa_study.FaResolver`; plots come from the same dataset.
"""
import json
import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from docx.enum.text import WD_ALIGN_PARAGRAPH  # noqa: E402

import fa_methods  # noqa: E402
import fa_study  # noqa: E402
import psr_report  # noqa: E402
from psr_report import GREY, NAVY, RED  # noqa: E402

PALETTE = ["#1f3a5f", "#b03a2e", "#1e8449", "#7d3c98", "#b9770e", "#117a8b", "#6e4b2a", "#555555"]
STYLES = ["-", "--", "-.", ":", (0, (5, 1.5)), (0, (3, 1, 1, 1, 1, 1)), (0, (2, 1)), ":"]


def _style(ds):
    return {c["id"]: (PALETTE[i % len(PALETTE)], STYLES[i % len(STYLES)]) for i, c in enumerate(ds["cases"])}


def make_figures(ds, fig_dir):
    """Matplotlib figures from the dataset (profiles, hydrate margin, cooldown, mitigation); returns ``{key: (path, alt, caption)}``."""
    os.makedirs(fig_dir, exist_ok=True)
    plt.rcParams.update({"font.size": 8, "axes.grid": True, "grid.alpha": 0.3, "font.family": "DejaVu Sans"})
    sty = _style(ds)
    out = {}
    pid = list(ds["route"]["paths"])[0]
    design = [c for c in ds["cases"] if c["role"] in ("design", "scenario") and pid in ds["results"][c["id"]]["profiles"]]
    fig, axs = plt.subplots(2, 1, figsize=(7.2, 5.6), sharex=True)
    for c in design:
        prof = ds["results"][c["id"]]["profiles"][pid]
        col, ls = sty[c["id"]]
        axs[0].plot([x / 1000.0 for x in prof["chainage_m"]], prof["p_bara"], color=col, ls=ls, lw=1.4, label=c["id"])
        axs[1].plot([x / 1000.0 for x in prof["chainage_m"]], prof["t_c"], color=col, ls=ls, lw=1.4, label=c["id"])
    axs[0].set_ylabel("pressure (bara)")
    axs[1].set_ylabel("temperature (degC)")
    axs[1].set_xlabel("chainage along path %s (km)" % pid)
    axs[0].legend(ncol=2, fontsize=7)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_profiles.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["profiles"] = (p, "Pressure and temperature along path %s for the design and scenario cases" % pid,
                       "Pressure and temperature along path %s (design and scenario cases)" % pid)
    first = next((c["id"] for c in ds["cases"] if c["role"] == "design"), ds["cases"][0]["id"])
    crit = next(c for c in ds["criteria"] if c["quantity"] == "hydrate_margin_min")
    fig, ax = plt.subplots(figsize=(7.2, 3.8))
    for c in ds["cases"]:
        r = ds["results"][c["id"]]
        for q, pth in enumerate(r["profiles"]):
            if q > 0 and c["id"] != first:
                continue
            if pth not in r.get("profiles_derived", {}):
                continue     # case did not converge: no derived profile to plot
            pr, der = r["profiles"][pth], r["profiles_derived"][pth]
            ys = [None if v is None else v for v in der["margin_k"]]
            xs = [x / 1000.0 for x in pr["chainage_m"]]
            col, ls = sty[c["id"]]
            ax.plot(xs, [float("nan") if v is None else v for v in ys], color=col, ls=ls, lw=1.2, label=c["id"] if q == 0 else None)
    ax.axhline(crit["value"], color="k", lw=0.8, ls="--")
    ax.text(0.2, crit["value"] + 0.8, "%s criterion %.0f K" % (crit["id"], crit["value"]), fontsize=7)
    ax.set_ylim(-8.0, crit["value"] + 30.0)
    ax.set_xlabel("chainage along the path (km)")
    ax.set_ylabel("hydrate margin (K), upper range clipped")
    ax.legend(ncol=3, fontsize=7)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_margin.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["margin"] = (p, "Hydrate margin along the route for every case with the project criterion", "Hydrate margin along the route (all cases, first path)")
    cases = [c for c in ds["cases"] if ds["results"][c["id"]].get("cooldown") and ds["results"][c["id"]]["cooldown"]["histories"]]
    fig, ax = plt.subplots(figsize=(7.2, 3.8))
    for c in cases:
        h = ds["results"][c["id"]]["cooldown"]["histories"][0]
        col, ls = sty[c["id"]]
        keep = [i for i, t in enumerate(h["t_h"]) if t <= 36.0]
        ax.plot([h["t_h"][i] for i in keep], [h["t_c"][i] for i in keep], color=col, ls=ls, lw=1.3, label=c["id"])
        if h["time_to_criterion_h"] and 0 < h["time_to_criterion_h"] <= 36.0:
            ax.plot([h["time_to_criterion_h"]], [h["t_crit_c"]], "o", mfc="white", mec=col, ms=5)
    ax.set_xlabel("time after shut-in (h); t = 0 at closure")
    ax.set_ylabel("temperature (degC)")
    ax.legend(ncol=3, fontsize=7)
    fig.tight_layout()
    p = os.path.join(fig_dir, "fig_cooldown.png")
    fig.savefig(p, dpi=200)
    plt.close(fig)
    out["cooldown"] = (p, "Cooldown after shut-in at the governing location of each case; circles mark the time to the hydrate temperature",
                       "Lumped cooldown at the governing location of each case")
    mits = [m for m in ds["mitigations"] if m["status"] == "calculated"]
    if mits:
        fig, axs = plt.subplots(1, 2, figsize=(7.2, 3.4))
        names = [m["id"] for m in mits]
        for ax, metric, label in ((axs[0], "hyd_margin_min", "change in minimum hydrate margin (K)"), (axs[1], "cooldown_time_min", "change in minimum cooldown time (h)")):
            vals = []
            for m in mits:
                d = next((x for x in m["deltas"] if x["metric"] == metric), None)
                vals.append(0.0 if d is None or d["delta"] is None else d["delta"])
            ax.bar(names, vals, color="#1f3a5f")
            ax.axhline(0, color="k", lw=0.6)
            ax.set_ylabel(label)
        fig.tight_layout()
        p = os.path.join(fig_dir, "fig_mitigation.png")
        fig.savefig(p, dpi=200)
        plt.close(fig)
        out["mitigation"] = (p, "Change of the minimum hydrate margin and of the minimum cooldown time for each mitigation option against its baseline case",
                             "Effect of the mitigation options against the baseline case")
    return out


def status_word(s):
    return {"PASS": "met", "FAIL": "NOT met", "UNAVAILABLE": "unavailable", "NOT ASSESSED": "not assessed", "NOT APPLICABLE": "not applicable"}.get(s, s.lower())


class FaReportBuilder(psr_report.ReportBuilder):
    """Flow-assurance report (A4 narrative, A3 drawing appendix). Call :meth:`build` once."""

    KEYWORDS = "flow assurance; NeqSim; hydrate; pipeline; route drawings"
    GENERATOR_COMMENT = "Generated by the flow-assurance-report toolkit"

    def __init__(self, ds, basis, standards, manifest, readiness, validation=None, figures=None):
        self.ds, self.basis, self.standards, self.manifest = ds, basis, standards, manifest
        self.validation, self.readiness_info = validation, readiness
        self.opt = {}
        self._init_document()
        self.res = fa_study.FaResolver(ds)
        self.figs = figures or {}
        self.cases = [c["id"] for c in ds["cases"]]
        self.actions = []
        self.basis = dict(basis)
        self.basis["document"] = dict(ds["document"], readiness=readiness["label"], readiness_note=readiness["note"])

    # --- helpers ---
    def X(self, text):
        return self.res.expand(text)

    def row(self, cells):
        return [self.res.expand(c) for c in cells]

    def tok(self, ref, fmt=None, unit=None):
        return "[[%s|%s%s]]" % (ref, fmt or "", "|unit=" + unit if unit else "")

    def design_case(self):
        """Id of the design case (first case when none has the design role)."""
        return next((c["id"] for c in self.ds["cases"] if c["role"] == "design"), self.ds["cases"][0]["id"])

    def sheet_no(self, dwg):
        """Ordinal sheet number of drawing ``DWG-NN`` in this package (drawing numbers stay stable when a sheet is not produced)."""
        for i, s in enumerate(self.manifest["sheets"]):
            if str(s["drawing_number"]).endswith("DWG-%02d" % dwg):
                return i + 1
        return None

    def synthetic(self):
        """True when any input is synthetic (a schematic real route is not synthetic; the readiness reasons state the schematic geometry)."""
        return self.ds["route"]["status"] == "synthetic" or any(s.get("input_class") == "synthetic" for s in self.ds["sources"])

    def seg_kind(self, sid):
        return next((s["kind"] for s in self.ds["route"]["segments"] if s["id"] == sid), "segment")

    def lowest_case(self, ext_id):
        """Case id with the smallest value of a derived extremum (None when no case has a value)."""
        items = [(self.ds["results"][c]["extrema"][ext_id]["value"], c) for c in self.cases if self.ds["results"][c]["extrema"][ext_id]["value"] is not None]
        return min(items)[1] if items else None

    def landscape(self):
        self.section(landscape=True)

    def portrait(self):
        self.section()

    def A3(self):
        self.section(landscape=True, a3=True)

    # --- front matter ---
    def document_control(self):
        d = self.basis["document"]
        self.H("Document control", 1)
        self.P("Revision history", bold=True)
        rows = [(r.get("rev"), r.get("date"), r.get("description"), r.get("by", "not assigned")) for r in d.get("revision_history", [])]
        self.table(["Rev", "Date", "Description", "By"], rows, widths=[15, 28, 100, 30], font=9, align=["left"] * 4)
        self.P("Readiness and responsibilities", bold=True)
        info = self.readiness_info
        self.P("Readiness label: %s. %s" % (info["label"], info["explanation"]))
        review = (self.ds["document"].get("review") or {})
        rows = [("Prepared by", d.get("prepared_by", "not assigned")), ("Checked by", d.get("checked_by", "not assigned")), ("Approved by", d.get("approved_by", "not assigned")),
                ("Independent reviewer", review.get("reviewer") or "not assigned"), ("Review record", review.get("record") or "none")]
        self.table(["Role", "Name"], rows, widths=[50, 100], font=9, align=["left", "left"], first_bold=True)
        self.P("The toolkit never assigns a checker, approver or reviewer, and a passing software check is not a design certification. "
               "Results are valid only within the stated basis, assumptions and data gaps.", italic=True)

    # --- 1 executive summary ---
    def executive_summary(self):
        ds, X = self.ds, self.X
        self.H("1 Executive summary", 1)
        trk = self.exec_summary_text
        self.P("Decisions addressed", bold=True)
        links = (ds["study"].get("decision_criteria") or [])
        for i, dec in enumerate(ds["study"]["decisions"]):
            ev = links[i] if i < len(links) else []
            self.P(dec, style="List Bullet", track=trk)
            for cid in ev:
                c = next((x for x in ds["criteria"] if x["id"] == cid), None)
                if c is None:
                    continue
                bad = [k for k, v in c["by_case"].items() if v["status"] == "FAIL"]
                self.P("Evidence %s: %s%s." % (cid, "not met in " + ", ".join(bad) if bad else "met in all applicable cases",
                                                "" if bad else ""), style="List Bullet 2", track=trk)
        self.P("Principal results", bold=True)
        self.P("[[fact:n_cases_converged]] of [[fact:n_cases]] cases converged on a %s-segment network of [[fact:route_length_km|.1f]] km (longest path) with [[fact:n_paths]] source paths. "
               "The results are steady-state screening values with the methods listed in Section 6; transient and slugging behaviour is not simulated." % "[[fact:n_hydraulic_segments]]", track=trk)
        header = ["Criterion"] + self.cases
        rows = []
        for c in ds["criteria"]:
            cells = ["%s (%s %s %s)" % (c["id"], c["quantity"].replace("_", " "), c["operator"], ("%g" % c["value"]) + ("" if c["unit"] == "-" else " " + c["unit"]))]
            for cid in self.cases:
                by = c["by_case"].get(cid, {"status": "NOT APPLICABLE"})
                cells.append("%s %s" % (self.tok("crit:%s:%s:value" % (c["id"], cid), ".1f"), by["status"]) if by["status"] in ("PASS", "FAIL") else "n/a")
            rows.append(self.row(cells))
        self.table(header, rows, caption="Criteria status by case (governing value over the whole route and PASS/FAIL against the project criterion; n/a = not applicable)", font=6.5,
                   widths=[30] + [16] * len(self.cases), align=["left"] + ["center"] * len(self.cases))
        trk.append(" ".join(" ".join(r) for r in rows))
        fails = [c for c in ds["criteria"] if c["overall"] == "FAIL"]
        if fails:
            self.P("Governing findings", bold=True)
            for c in fails:
                bad = [(k, v) for k, v in c["by_case"].items() if v["status"] == "FAIL"]
                worst = min(bad, key=lambda kv: kv[1]["limit_margin"])
                k, v = worst
                self.P("%s is not met in %s. The largest shortfall is in case %s: %s %s against the limit %s at KP %s km (segment %s, path %s)." % (
                    c["id"], ", ".join(b[0] for b in bad), k, self.tok("crit:%s:%s:value" % (c["id"], k), ".1f"), c["unit"] if c["unit"] != "-" else "", c["operator"] + " " + ("%g" % c["value"]),
                    self.tok("ext:%s:%s:chainage_m" % (k, fa_study.QUANTITIES[c["quantity"]][0]), ".1f", "km") if v.get("chainage_m") is not None else "n/a",
                    v.get("segment_id") or "n/a", (v.get("path") or "n/a")), style="List Bullet", track=trk)
        self.P("Operating limits and open threats", bold=True)
        self.P("[[fact:n_threats_calculated]] of [[fact:n_threats]] threats are calculated, [[fact:n_threats_not_applicable]] are not applicable (reason stated), "
               "[[fact:n_threats_open]] are open and [[fact:n_threats_unsupported]] are unsupported by a qualified solver. Open and unsupported threats carry no result and are not shown as safe.", track=trk)
        mit = [m for m in ds["mitigations"] if m["status"] == "calculated"]
        if mit:
            self.P("Mitigation effects against the baseline case are quantified in Section 13: " + "; ".join(
                "%s changes the minimum hydrate margin by %s K" % (m["id"], self.tok_delta(m, "hyd_margin_min")) for m in mit) + ".", track=trk)
        self.P("Readiness and next actions", bold=True)
        self.P("This report is issued as %s. %s The action register (Section 15) lists [[fact:n_data_gaps]] data gaps, failed criteria, open threats and external hand-offs with owners not assigned." % (
            self.readiness_info["label"], self.readiness_info["explanation"]), track=trk)

    def tok_delta(self, m, metric):
        d = next(x for x in m["deltas"] if x["metric"] == metric)
        return "%+.1f" % d["delta"] if d["delta"] is not None else "unavailable"

    # --- 2 scope ---
    def scope(self):
        ds, st = self.ds, self.ds["study"]
        self.H("2 Scope, objectives, battery limits and study maturity", 1)
        self.P("Objective: " + st["objective"])
        self.P("Study maturity: %s. Audience: %s." % (st["maturity"], st.get("audience", "not stated")))
        self.H("2.1 Battery limits", 2)
        bl = st["battery_limits"]
        self.P("Included:", bold=True)
        self.bullets(bl["included"])
        self.P("Excluded:", bold=True)
        self.bullets(bl["excluded"])
        self.P("Upstream boundary: %s. Downstream boundary: %s." % (bl.get("upstream", "not stated"), bl.get("downstream", "not stated")))
        self.H("2.2 Alternatives and field-life stages", 2)
        self.bullets(st.get("alternatives", []))
        self.P("Field-life stages: " + "; ".join(st["field_life_stages"]) + ".")

    # --- 3 basis ---
    def basis_section(self):
        ds = self.ds
        self.H("3 Design and operating basis, evidence register and acceptance criteria", 1)
        self.H("3.1 Controlled sources and input classification", 2)
        rows = [(s["id"], s["title"], "%s rev %s, %s" % (s["document"], s["revision"], s["location"]), s["input_class"], s["provenance"], s["status"]) for s in ds["sources"]]
        self.table(["Id", "Title", "Document / revision / location", "Class", "Provenance", "Status"], rows, caption="Source register", font=7, widths=[22, 38, 40, 16, 40, 14], align=["left"] * 6)
        self.H("3.2 Conventions", 2)
        c = ds["conventions"]
        std = c["standard_conditions"]
        rows = [("Units", c["units"]), ("Pressure reference", c["pressure_reference"]), ("Composition basis", c["composition_basis"]), ("Rate basis", c["rate_basis"]),
                ("Standard conditions", "%.2f degC, %.5f bara. %s" % (std["t_c"], std["p_bara"], std.get("definition", ""))), ("Chainage", c["chainage"]),
                ("Vertical datum", c["vertical_datum"]), ("Coordinates", c.get("coordinates", "not supplied")), ("Heat transfer", c.get("heat_transfer", "not stated"))]
        self.table(["Convention", "Definition"], rows, caption="Units, bases and conventions", font=8, widths=[34, 130], align=["left", "left"], first_bold=True)
        self.H("3.3 Acceptance criteria", 2)
        rows = [self.row([c["id"], c["text"], "%s %s %g %s" % (c["quantity"].replace("_", " "), c["operator"], c["value"], "" if c["unit"] == "-" else c["unit"]),
                          c["requirement_class"], c["source"], "[[crit:%s:overall]]" % c["id"]]) for c in ds["criteria"]]
        self.table(["Id", "Criterion", "Quantity and limit", "Class", "Source", "Status"], rows, caption="Acceptance criteria (sourced inputs) and overall status", font=7,
                   widths=[14, 58, 32, 16, 34, 14], align=["left"] * 6)
        self.P("No limit here is a universal or code value; each is a project input with the source stated. The overall status is FAIL when any applicable case fails.")
        self.H("3.4 Assumptions, exclusions and data gaps", 2)
        self.table(["Id", "Assumption", "Effect"], [(a["id"], a["text"], a["effect"]) for a in ds["assumptions"]], caption="Assumptions", font=8, widths=[12, 90, 62], align=["left"] * 3)
        self.table(["Id", "Data gap", "Effect on conclusions"], [(g["id"], g["item"], g["effect_on_conclusions"]) for g in ds["data_gaps"]], caption="Data gaps and their effect on conclusions", font=8,
                   widths=[12, 80, 72], align=["left"] * 3)
        self.P("Exclusions:", bold=True)
        self.bullets(ds["exclusions"])

    # --- 4 system ---
    def system(self):
        ds = self.ds
        rt = ds["route"]
        self.landscape()
        self.H("4 System description, route, geometry, line list and drawing register", 1)
        self.P("The system has [[fact:n_assets]] assets, [[fact:n_segments]] segments ([[fact:n_hydraulic_segments]] modelled hydraulically) and [[fact:n_paths]] source paths to the host interface %s. "
               "The longest path is [[fact:route_length_km|.2f]] km of measured route length. Route status: %s; georeferenced: %s; coordinate reference: %s." % (
                   rt["sink"], rt["status"], "yes" if rt["georeferenced"] else "NO", rt["crs"]))
        self.P("Horizontal chainage, measured (as-laid) route length and elevation are separate quantities: lengths in the line list are measured lengths used by the hydraulic model; "
               "horizontal lengths come from plan coordinates where supplied; elevation comes from the asset elevations. The route integrity checks (length versus 3D chord, "
               "duplicate IDs, connectivity) are summarised in Section 14.")
        rows = []
        for s in rt["segments"]:
            if s.get("hydraulic", True):
                ins = ", ".join("%s %.0f mm" % (l["name"].split()[0], l["thickness_m"] * 1000.0) for l in s["layers"] if "insulation" in l["name"].lower())
                rows.append(self.row([s["id"], s["line_tag"], s["from"] + " > " + s["to"], "[[seg:%s:length_m|.0f]]" % s["id"], "[[seg:%s:horizontal_length_m|.0f]]" % s["id"],
                                      "[[seg:%s:dz_m|.1f]]" % s["id"], "[[seg:%s:id_m|.1f|unit=mm]]" % s["id"], ins or "none", ("buried %.1f m" % s["burial"]["depth_m"]) if s.get("burial") else "exposed",
                                      "[[seg:%s:thermal.u_inner_w_m2k|.2f]]" % s["id"]]))
            else:
                rows.append([s["id"], s["line_tag"], s["from"] + " > " + s["to"], "%.0f" % s["length_m"], "n/a", "n/a", "n/a", "n/a", "not modelled", "n/a"])
        self.table(["Segment", "Line tag", "From > to", "Length (m)", "Horizontal (m)", "dz (m)", "ID (mm)", "Insulation", "Burial", "U inner (W/m2K)"], rows,
                   caption="Line list: measured length, horizontal length, elevation change, inside diameter, insulation, burial and U-value (inner-area basis)", font=7,
                   widths=[22, 40, 44, 20, 22, 16, 18, 36, 24, 24], align=["left"] * 3 + ["right"] * 4 + ["left"] * 2 + ["right"])
        self.P("Non-modelled jumpers carry no hydraulic or thermal values (n/a). The U-value is referenced to the inner pipe surface because the hydraulic solver uses pi x ID x L; the layered build-up is on drawing sheet %s." % self.sheet_no(6))
        self.H("4.1 Drawing register", 2)
        rows = [(s["drawing_number"], s["title"], "%d of %d" % (s["number"], len(self.manifest["sheets"])), s["qa"], os.path.basename(s["svg"])) for s in self.manifest["sheets"]]
        self.table(["Drawing number", "Title", "Sheet", "Layout QA", "Editable source"], rows, caption="Drawing register (A3 landscape SVG sources; vector PDF and PNG previews delivered)", font=8,
                   widths=[38, 100, 18, 18, 62], align=["left"] * 5)
        self.portrait()

    # --- 5 fluids ---
    def fluids(self):
        ds = self.ds
        self.H("5 Fluid and PVT characterisation and validation", 1)
        for fid, f in ds["fluids"].items():
            self.P("Fluid %s: %s" % (fid, f["description"]), bold=True)
            rows = [(c["name"], "%.4f" % c["mole_fraction"]) for c in f["components"]]
            self.table(["Component", "Mole fraction"], rows, caption="Hydrocarbon composition of %s (dry; water and inhibitor are added by mass rate)" % fid, font=8, widths=[50, 30], align=["left", "right"])
            self.P("Equations of state: hydraulics - %s; hydrate - %s. Heavy-end characterisation: %s. Water: %s. PVT evidence: %s." % (
                f["eos_hydraulics"], f["eos_hydrate"], f["heavy_end"], f["water"]["salinity"], f["pvt_evidence"]))
        self.P("Laboratory or field validation of the fluid model: OPEN - no laboratory PVT, saturation pressure, density or hydrate-equilibrium data was supplied (data gap G-01). "
               "The model results in this report are therefore predictions that have not been compared with measurements.")
        rows = []
        for cid in self.cases:
            r = ds["results"][cid]
            rows.append(self.row([cid, "[[res:%s:streams.%s.t_c|.1f]]" % (cid, r["sink"]), "[[res:%s:streams.%s.p_bara|.1f]]" % (cid, r["sink"]),
                                  "[[res:%s:streams.%s.gas_kg_s|.1f]]" % (cid, r["sink"]), "[[res:%s:streams.%s.oil_kg_s|.2f]]" % (cid, r["sink"]), "[[res:%s:streams.%s.aqueous_kg_s|.2f]]" % (cid, r["sink"]),
                                  "[[res:%s:streams.%s.n_phases]]" % (cid, r["sink"])]))
        self.table(["Case", "T (degC)", "P (bara)", "Gas (kg/s)", "Condensate (kg/s)", "Aqueous (kg/s)", "Phases"], rows, caption="Host-interface arrival state by case (phase rates from the EOS flash)", font=8,
                   widths=[34, 18, 18, 20, 26, 24, 14], align=["left"] + ["right"] * 6)

    # --- 6 methods ---
    def methods(self):
        ds = self.ds
        self.H("6 Methods, solver choice, qualification and numerical limitations", 1)
        self.P("Beggs and Brill is used for the steady multiphase profiles because the questions of this study (pressure drop, backpressure, arrival temperature, steady holdup) are steady-state questions and the correlation "
               "is fast and stable; it does not resolve slugging and it is not used for any transient, pigging or severe-slugging result. A mechanistic or two-fluid transient model would be needed for those, "
               "and the current NeqSim TwoFluidPipe evidence matrix does not qualify it for them (Appendix G); an external simulator hand-off is provided instead.")
        rows = [(m["id"], m["name"], m["implemented"], m["numerically_verified"], m["experimentally_qualified"], m["use"], m["not_for"], m["limits"]) for m in ds["qualification"]["methods"]]
        self.landscape()
        self.table(["Id", "Method", "Implemented", "Numerically verified", "Experimentally qualified", "Use", "Not for", "Limits"], rows,
                   caption="Method register with qualification status (as recorded; no method is promoted beyond its evidence)", font=6, widths=[14, 38, 18, 30, 30, 36, 42, 48], align=["left"] * 8)
        self.portrait()
        sv = ds["solver"]
        self.P("Numerical settings: %d increments per segment; inner source-pressure solves to 0.05 bar and the junction pressure to 0.05 bar (secant iteration); hydrate temperature on the pressure grid %s bara, interpolated in ln(p); "
               "erosional C-factor (SI) %g. Each case is solved once per run; extracted profiles are reused for the drawings, tables and narrative. Cache key: fluid, geometry, case, solver settings, NeqSim version and solver version." % (
                   sv["increments"], sv["hydrate_grid_bara"], sv["erosional_c_si"]))
        self.P("Backward solution: the host-interface pressure is specified and the source pressures are calculated; a result is accepted only when every solve reached its tolerance (convergence status per case in Appendix E).")
        self.P("Observable definitions: the hydrate margin is a temperature difference to the equilibrium curve - not a plugging time; cooldown gives the time to a temperature - not a wax deposition or hydrate formation rate; "
               "steady holdup is not a transient slug volume; a velocity ratio is a screening index - not a kinetic or structural prediction.")

    # --- 7 cases ---
    def case_matrix(self):
        ds = self.ds
        self.landscape()
        self.H("7 Case matrix, field-life evolution and boundary conditions", 1)
        rows = []
        for c in ds["cases"]:
            r = ds["results"][c["id"]]
            src = ", ".join("%s %.1f kg/s HC, %.0f kg/h water, %.0f degC" % (n, s["hc_kg_s"], 3600.0 * s["water_kg_s"], s["t_c"]) for n, s in r["sources"].items() if n in r["streams"])
            inh = ", ".join("%s %.0f kg/h lean (%.1f wt%% aq.)" % (n, 3600.0 * s["lean_inhibitor_kg_s"], 100.0 * s["aqueous_inhibitor_wt"]) for n, s in r["sources"].items() if n in r["streams"])
            rows.append((c["id"], c["role"], c["stage"], c["topology_variant"], src, inh, "%.0f bara" % r["host_p_bara"]))
        self.table(["Case", "Role", "Stage", "Line-up", "Sources (mass rates, wellhead temperature)", "MEG injection", "Host P"], rows, caption="Case matrix (rates are mass rates; boundary: host-interface pressure)", font=7,
                   widths=[24, 20, 20, 24, 100, 58, 18], align=["left"] * 7)
        self.portrait()
        self.P("Rates are specified on a mass basis; the standard-volume equivalent of the hydrocarbon gas phase at the stated standard conditions is reported in the case summary (Appendix D). "
               "Outlet boundary: absolute pressure at the host interface; inlet boundary: specified rate and temperature at each template outlet, with the required source pressure calculated. "
               "Topology variants: " + "; ".join("%s - %s" % (v["id"], v["description"] or "default line-up") for v in ds["topology_variants"].values()) +
               ". Each variant keeps the stable asset and segment IDs; unsupported variant operations (bypass, re-routing, added assets) are rejected with an error (extension point fa_geometry.apply_variant).")

    # --- 8 steady ---
    def steady(self):
        ds = self.ds
        self.H("8 Steady-state hydraulics and thermal performance", 1)
        rows = []
        for cid in self.cases:
            r = ds["results"][cid]
            sp = ", ".join("%s %s" % (n, self.tok("res:%s:streams.%s.p_bara" % (cid, n), ".1f")) for n in r["sources"] if n in r["streams"])
            rows.append(self.row([cid, sp, "[[res:%s:streams.%s.p_bara|.1f]]" % (cid, r["sink"]), "[[res:%s:streams.%s.t_c|.1f]]" % (cid, r["sink"]),
                                  "[[res:%s:summary.liquid_inventory_m3|.0f]]" % cid, "[[ext:%s:v_mix_max:value|.1f]]" % cid, "[[ext:%s:holdup_max:value|.2f]]" % cid]))
        self.table(["Case", "Calculated source pressures (bara)", "Host P (bara)", "Host T (degC)", "Steady liquid inventory (m3)", "Max v_mix (m/s)", "Max holdup (-)"], rows,
                   caption="Steady results by case (source pressures are calculated; host pressure is specified)", font=7, widths=[22, 50, 16, 16, 24, 18, 18], align=["left"] * 2 + ["right"] * 5)
        if "profiles" in self.figs:
            self.picture(*self._fig("profiles", 160))
        base = self.design_case()
        r = ds["results"][base]
        rs_id = next((s["id"] for s in ds["route"]["segments"] if s["kind"] == "riser" and s.get("hydraulic", True)), None)
        first_src = list(r["sources"])[0]
        text = ("Reading the figure: in case %s the pressure falls from [[res:%s:streams.%s.p_bara|.1f]] to [[res:%s:streams.%s.p_bara|.1f]] bara along the %.1f km route"
                % (base, base, first_src, base, r["sink"], ds["facts"]["route_length_km"]))
        if rs_id and rs_id in r["segments"]:
            text += "; the riser accounts for [[segres:%s:%s:dp_bar|.1f]] bar of this and its maximum steady liquid holdup is [[segres:%s:%s:holdup_max|.2f]]" % (base, rs_id, base, rs_id)
        text += ". "
        arrivals = [(ds["results"][c]["streams"][r["sink"]]["t_c"], c) for c in self.cases if r["sink"] in ds["results"][c]["streams"]]
        if arrivals:
            cold = min(arrivals)[1]
            text += "The lowest host arrival temperature of all cases is [[res:%s:streams.%s.t_c|.1f]] degC in case %s (Section 10)." % (cold, r["sink"], cold)
        self.P(text)
        self.P("Thermal basis: layered U-values are referenced to the inner area; the internal film is neglected, which makes the arrival temperatures conservative (low). Insulation and burial transitions along the path: " +
               ("; ".join("%s > %s at %s" % (t["from_segment"], t["to_segment"], t["node"]) for t in ds["route"]["transitions"]) or "none") + ".")
        self.landscape()
        self.H("8.1 Segment results", 2)
        for cid in self.cases[:3]:
            rr = ds["results"][cid]["segments"]
            rows = [self.row([sid, "[[segres:%s:%s:p_in_bara|.1f]]" % (cid, sid), "[[segres:%s:%s:dp_bar|.2f]]" % (cid, sid), "[[segres:%s:%s:t_in_c|.1f]]" % (cid, sid),
                              "[[segres:%s:%s:t_out_c|.1f]]" % (cid, sid), "[[segres:%s:%s:holdup_mean|.3f]]" % (cid, sid), "[[segres:%s:%s:v_mix_max_m_s|.1f]]" % (cid, sid),
                              "[[segres:%s:%s:liquid_inventory_m3|.1f]]" % (cid, sid), ", ".join(rr[sid]["regimes"])]) for sid in rr]
            self.table(["Segment", "P in (bara)", "dP (bar)", "T in (degC)", "T out (degC)", "Mean holdup", "Max v_mix (m/s)", "Inventory (m3)", "Regime label"], rows,
                       caption="Segment results, case %s (Beggs and Brill steady state; the regime label is a correlation label, not a transient prediction)" % cid, font=7,
                       widths=[22, 22, 18, 20, 20, 20, 24, 24, 40], align=["left"] + ["right"] * 7 + ["left"])
        self.portrait()

    # --- 9 transient ---
    def transients(self):
        ds = self.ds
        self.H("9 Transient and event analyses and qualification gaps", 1)
        self.P("Shut-in cooldown is calculated with the lumped model (screening; numerically verified, not experimentally qualified). Restart, depressurisation, liquid sweep-out, pigging, slug receiving and severe slugging "
               "are NOT simulated: no qualified solver is available, so they are reported as open with an external-simulator hand-off package.")
        rows = []
        for cid in self.cases:
            cd = ds["results"][cid].get("cooldown")
            if not cd:
                continue
            g = cd["governing"]
            rows.append(self.row([cid, "%.0f" % cd["settle_out_pressure_bara"], "[[ext:%s:cooldown_time_min:value|.1f]]" % cid,
                                  ("KP %s km, %s" % (self.tok("ext:%s:cooldown_time_min:chainage_m" % cid, ".1f", "km"), g["segment_id"])) if g else "n/a", "[[crit:C-COOL-01:%s:status]]" % cid if any(c["id"] == "C-COOL-01" for c in ds["criteria"]) else "n/a"]))
        self.table(["Case", "Settle-out P (bara)", "Min time to hydrate T (h)", "Governing location", "Criterion"], rows, caption="Cooldown after shut-in: governing time to the hydrate equilibrium temperature", font=8,
                   widths=[34, 26, 34, 50, 22], align=["left", "right", "right", "left", "left"])
        if "cooldown" in self.figs:
            self.picture(*self._fig("cooldown", 150))
        gov_by_seg = {}
        for cid in self.cases:
            cd = ds["results"][cid].get("cooldown")
            if cd and cd.get("governing"):
                gov_by_seg.setdefault(cd["governing"]["segment_id"], []).append(cid)
        gov_txt = "; ".join("%s (%s) in %s" % (sid, self.seg_kind(sid), ", ".join(cs)) for sid, cs in gov_by_seg.items())
        self.P("Reading the figure: the curves start at the steady operating temperature of the governing location of each case and decay exponentially towards the ambient temperature with the time constant of the "
               "local build-up; the circle marks the time at which the hydrate equilibrium temperature at the settle-out pressure is reached. A governing time of 0 h means the steady operating temperature is already at or below "
               "that temperature at the location (the hydrate criterion is already violated in operation). " +
               (("Governing locations (segment, kind, cases): %s. " % gov_txt) if gov_txt else "") +
               "Whether a change of insulation on another segment moves the governing time depends on where the governing location is; the mitigation comparison in Section 13 shows the calculated effect of each declared option.")
        self.H("9.1 External events (not simulated)", 2)
        rows = [(e["id"], e["case"], e["type"], {"calculated": "calculated (lumped cooldown)", "external_open": "EXTERNAL - NOT SIMULATED - OPEN", "external_supplied": "external result supplied, not verified here", "open": "OPEN"}[e["status"]],
                 e["description"], e.get("handoff", "-")) for e in ds["events"]]
        self.table(["Event", "Case", "Type", "Status", "Description", "Hand-off package"], rows, caption="Event register (time origins are stated in the hand-off package; shut-in events start at t = 0 at closure)", font=7,
                   widths=[24, 18, 16, 36, 70, 30], align=["left"] * 6)
        self.P("Each hand-off package contains the fluid, the route geometry and thermal layers, the boundary conditions, the steady initial state, the event schedule, the required outputs and the comparison criteria. "
               "External results that a person supplies are recorded as supplied and are not verified by this toolkit; until then the corresponding conclusions stay open.")

    # --- 10 hydrate ---
    def hydrate(self):
        ds = self.ds
        crit = next(c for c in ds["criteria"] if c["quantity"] == "hydrate_margin_min")
        self.H("10 Hydrate management and inhibition", 1)
        self.P("The margin is the local fluid temperature minus the CPA hydrate equilibrium temperature at the local pressure and the aqueous inhibitor mass fraction of the stream at that location. "
               "It is an equilibrium margin; hydrate formation or plugging time is not predicted. The inhibitor mass fraction follows from the injection rate balance: lean MEG of purity p mixed with the produced water gives "
               "w = p m / (m_water + m), with m the lean rate; losses to gas and condensate are not modelled, so w is an upper bound.")
        rows = []
        for cid in self.cases:
            r = ds["results"][cid]
            for n, s in r["sources"].items():
                if n in r["streams"]:
                    rows.append(self.row([cid, n, "%.0f" % (3600.0 * s["water_kg_s"]), "%.0f" % (3600.0 * s["lean_inhibitor_kg_s"]), "%.1f" % (100.0 * s["aqueous_inhibitor_wt"])]))
        self.table(["Case", "Source", "Water (kg/h)", "Lean inhibitor (kg/h, purity per case)", "Aqueous MEG (wt%)"], rows, caption="Inhibitor injection-rate balance (mass basis)", font=8, widths=[34, 24, 24, 38, 30], align=["left", "left"] + ["right"] * 3)
        rows = [self.row([cid, "[[ext:%s:hyd_margin_min:value|.1f]]" % cid, "KP [[ext:%s:hyd_margin_min:chainage_m|.1f|unit=km]] km, %s" % (cid, ds["results"][cid]["extrema"]["hyd_margin_min"]["segment_id"]),
                          "[[ext:%s:hyd_margin_unin_min:value|.1f]]" % cid, "[[crit:%s:%s:status]]" % (crit["id"], cid)]) for cid in self.cases]
        self.table(["Case", "Min margin, inhibited (K)", "Location", "Min margin if uninhibited (K)", "%s (>= %g K)" % (crit["id"], crit["value"])], rows,
                   caption="Hydrate margin: governing values and criterion status", font=8, widths=[34, 34, 44, 40, 28], align=["left", "right", "left", "right", "left"])
        if "margin" in self.figs:
            self.picture(*self._fig("margin", 150))
        low = self.lowest_case("hyd_margin_min")
        if low:
            ex_low = ds["results"][low]["extrema"]
            r_low = ds["results"][low]
            inh = ", ".join("%.0f wt%% at %s" % (100.0 * s["aqueous_inhibitor_wt"], n) for n, s in r_low["sources"].items() if n in r_low["streams"])
            m_in, m_un = ex_low["hyd_margin_min"]["value"], ex_low["hyd_margin_unin_min"]["value"]
            text = ("Reading the figure: the smallest inhibited margin of all cases is [[ext:%s:hyd_margin_min:value|.1f]] K in case %s at KP [[ext:%s:hyd_margin_min:chainage_m|.1f|unit=km]] km (segment %s, %s); "
                    "in that case the aqueous MEG mass fraction is %s and the uninhibited reference margin is [[ext:%s:hyd_margin_unin_min:value|.1f]] K. "
                    % (low, low, low, ex_low["hyd_margin_min"]["segment_id"], self.seg_kind(ex_low["hyd_margin_min"]["segment_id"]), inh or "not applicable", low))
            if m_in is not None and m_in < crit["value"]:
                text += "The inhibited margin is below the %g K criterion in this case: the stated inhibitor dose does not satisfy the criterion there." % crit["value"]
            elif m_un is not None and m_un < crit["value"]:
                text += "The uninhibited margin is below the %g K criterion while the inhibited margin meets it: inhibition is required in this case." % crit["value"]
            else:
                text += "The %g K criterion is met even without inhibitor in this case." % crit["value"]
            self.P(text)
        else:
            self.P("The margin profile is shown in the figure above.")
        hc = next((b for b in ds["benchmarks"] if b["id"] == "XCHECK-HAMMERSCHMIDT"), None)
        if hc and hc["value"] is not None:
            self.P("Cross-check: the CPA hydrate depression for 20 wt%% MEG at 100 bara is %.1f K against %.1f K from the empirical Hammerschmidt rule. The rule is a screening estimate and is not a validation; the difference is reported "
                   "and not removed, and the margins above use the CPA values (see Section 14)." % (hc["value"], hc["reference"]))

    # --- 11 other threats ---
    def other_threats(self):
        ds = self.ds
        self.landscape()
        self.H("11 Wax, asphaltene, scale, corrosion and erosion assessments", 1)
        self.P("Every threat has a recorded status: calculated with evidence, not applicable with a reason, or open/unsupported. An unavailable result is never presented as a safe result.")
        rows = [(t["id"], t["threat"], t["status"].upper().replace("_", " "), t["reason"], ", ".join(t["methods"]) or "-") for t in ds["threats"]]
        self.table(["Id", "Threat", "Status", "Reason / evidence basis", "Methods"], rows, caption="Threat applicability matrix", font=7, widths=[26, 60, 28, 120, 30], align=["left"] * 5)
        self.portrait()
        base = self.design_case()
        crit_q = {c["quantity"]: c for c in ds["criteria"]}
        open_t = [t["id"] for t in ds["threats"] if t["status"] in ("open", "unsupported")]
        text = "Erosional velocity: "
        if "erosional_ratio_max" in crit_q and "mixture_velocity_max" in crit_q:
            text += ("the maximum ratio of mixture velocity to the project erosional velocity is [[ext:%s:erosional_ratio_max:value|.2f]] in case %s (limit [[crit:%s:value|.1f]]); "
                     "the maximum mixture velocity is [[ext:%s:v_mix_max:value|.1f]] m/s (limit [[crit:%s:value|.0f]] m/s). These are project criteria for clean service and not a DNV-RP-O501 assessment. "
                     % (base, base, crit_q["erosional_ratio_max"]["id"], base, crit_q["mixture_velocity_max"]["id"]))
        else:
            text += "no erosional-velocity or velocity criterion is declared in the basis; the maximum mixture velocity of case %s is [[ext:%s:v_mix_max:value|.1f]] m/s (not a DNV-RP-O501 assessment). " % (base, base)
        if open_t:
            text += "Threats without a result (open or unsupported): %s; see the threat matrix and the data gaps for the missing inputs." % ", ".join(open_t)
        self.P(text)

    # --- 12 envelope ---
    def envelope(self):
        ds = self.ds
        self.H("12 Operating envelope and startup, shutdown and restart recommendations", 1)
        ok = [c["id"] for c in ds["cases"] if all(cr["by_case"].get(c["id"], {"status": "NOT APPLICABLE"})["status"] in ("PASS", "NOT APPLICABLE") for cr in ds["criteria"])]
        bad = [c["id"] for c in ds["cases"] if c["id"] not in ok]
        self.P("Cases that meet every applicable criterion: %s. Cases with at least one criterion not met: %s." % (", ".join(ok) or "none", ", ".join(bad) or "none"))
        base = self.design_case()
        src = next((c for c in ds["criteria"] if c["quantity"] == "source_pressure_max"), None)
        hyd = next((c for c in ds["criteria"] if c["quantity"] == "hydrate_margin_min"), None)
        text = "Operating limits supported by the calculations (screening, within the stated basis): "
        if src:
            text += "required source pressure up to [[ext:%s:source_pressure_max:value|.0f]] bara in case %s against the available [[crit:%s:value|.0f]] bara; " % (base, base, src["id"])
        if hyd:
            hyd_bad = [k for k, v in hyd["by_case"].items() if v["status"] == "FAIL"]
            text += ("the hydrate margin criterion %s is not met in %s at the stated inhibitor doses, which limits the operating envelope there (Section 10)." % (hyd["id"], ", ".join(hyd_bad))
                     if hyd_bad else "the hydrate margin criterion %s is met in all applicable cases (Section 10)." % hyd["id"])
        elif not src:
            text += "none can be stated because no limiting criterion is declared in the basis."
        self.P(text)
        self.P("Startup, restart and shutdown recommendations: OPEN. A restart procedure depends on liquid sweep-out, depressurisation and warm-up transients that were not simulated (Section 9). No procedure is recommended by this report; "
               "the shut-in cooldown time to the hydrate temperature (Section 9) gives the time available before hydrate-forming conditions are reached and the criterion status shows where that allowance is not met.")

    # --- 13 mitigation ---
    def mitigation(self):
        ds = self.ds
        self.H("13 Mitigation alternatives with quantitative comparison", 1)
        rows = []
        for m in ds["mitigations"]:
            if m["status"] != "calculated":
                continue
            for d in m["deltas"]:
                rows.append((m["id"], "%s vs %s" % (m["case"], m["baseline"]), d["label"], d["unit"], "n/a" if d["baseline"] is None else "%.1f" % d["baseline"],
                             "n/a" if d["option"] is None else "%.1f" % d["option"], "n/a" if d["delta"] is None else "%+.1f" % d["delta"]))
        self.table(["Option", "Case vs baseline", "Metric", "Unit", "Baseline", "Option", "Change"], rows, caption="Mitigation comparison (every value from the dataset; change = option minus baseline)", font=7,
                   widths=[14, 36, 54, 12, 20, 20, 18], align=["left"] * 4 + ["right"] * 3)
        if "mitigation" in self.figs:
            self.picture(*self._fig("mitigation", 150))
        for m in ds["mitigations"]:
            if m["status"] != "calculated":
                continue
            self.P("%s - %s. Remaining uncertainty: %s." % (m["id"], m["title"], m["remaining_uncertainty"]), bold=False)
        calc = [m for m in ds["mitigations"] if m["status"] == "calculated"]
        if calc:
            def dl(m, metric):
                return next((d for d in m["deltas"] if d["metric"] == metric), None)

            parts = []
            for m in calc:
                bits = []
                for metric, name in (("hyd_margin_min", "minimum hydrate margin"), ("cooldown_time_min", "minimum cooldown time")):
                    d = dl(m, metric)
                    if d and d["delta"] is not None:
                        bits.append("%s %+.1f %s (%s to %s)" % (name, d["delta"], d["unit"], "n/a" if d["baseline"] is None else "%.1f" % d["baseline"], "n/a" if d["option"] is None else "%.1f" % d["option"]))
                parts.append("%s (%s vs %s): %s" % (m["id"], m["case"], m["baseline"], "; ".join(bits) or "no comparable values"))
            self.P("Calculated effect of each declared option: " + " | ".join(parts) + ". The criterion status of each option case is in Section 1 and on the operating-limit drawing; an option that improves "
                   "one metric can leave another criterion unmet. Heating, rate or pressure changes and pigging or operating procedures were not quantified and remain open alternatives.")
        else:
            self.P("No mitigation option is declared in the basis, so no alternatives are compared. Inhibitor dose, insulation, heating, rate or pressure changes and operating procedures remain open alternatives.")

    # --- 14 sensitivity ---
    def sensitivity(self):
        ds = self.ds
        self.H("14 Sensitivity, uncertainty, robustness and model validation", 1)
        ms = ds.get("mesh_sensitivity")
        if ms:
            rows = [(str(r["increments"]), "%.2f" % r["source_pressure_max_bara"] if r["source_pressure_max_bara"] is not None else "n/a", "%.2f" % r["host_arrival_t_c"] if r["host_arrival_t_c"] is not None else "n/a",
                     "%.2f" % r["hyd_margin_min_k"] if r["hyd_margin_min_k"] is not None else "n/a", "%+.2f" % r["d_source_p_bar"] if r["d_source_p_bar"] is not None else "n/a",
                     "%+.2f" % r["d_arrival_t_k"] if r["d_arrival_t_k"] is not None else "n/a", "%+.2f" % r["d_margin_k"] if r["d_margin_k"] is not None else "n/a") for r in ms["rows"]]
            self.table(["Increments", "Max source P (bara)", "Arrival T (degC)", "Min margin (K)", "dP vs finest (bar)", "dT vs finest (K)", "dMargin vs finest (K)"], rows,
                       caption="Spatial sensitivity of case %s (study mesh: %d increments per segment; reference: finest mesh)" % (ms["case"], ds["solver"]["increments"]), font=7, widths=[20, 28, 26, 26, 26, 26, 28],
                       align=["left"] + ["right"] * 6)
            coarse = ms["rows"][0]
            tol = ms["tolerance"]
            exceeds = any(v is not None and abs(v) > tol.get(k, float("inf")) for v, k in ((coarse["d_arrival_t_k"], "t_k"), (coarse["d_margin_k"], "margin_k")))
            self.P("Spatial gate: status %s with tolerances %s (declared before the comparison); the gate compares the study mesh with the finest mesh for the case with the smallest hydrate margin. "
                   "The coarsest mesh (%d increments) differs from the finest by %s K in arrival temperature and %s K in the minimum margin%s" % (
                       ms["status"], json.dumps(tol), coarse["increments"], "%+.2f" % coarse["d_arrival_t_k"] if coarse["d_arrival_t_k"] is not None else "n/a",
                       "%+.2f" % coarse["d_margin_k"] if coarse["d_margin_k"] is not None else "n/a",
                       ", beyond the declared tolerance: a coarse mesh is not acceptable for this geometry and the study mesh must be confirmed against a finer one for every new geometry." if exceeds
                       else ", within the declared tolerance."))
        by_kind = {}
        for b in ds["benchmarks"]:
            by_kind.setdefault(b["kind"], []).append(b)
        for kind, label in (("analytic_verification", "Analytic verification (implementation checks)"), ("independent_validation", "Independent validation (not used for tuning)"),
                            ("field_calibration", "Field calibration records (measured data the model inputs were tuned to)"),
                            ("field_validation", "Field validation records (measured hold-out data, not used for tuning)"),
                            ("sensitivity", "Sensitivity"), ("crosscheck", "Cross-checks (information, no pass/fail)")):
            rows = [(b["id"], b["title"], b["status"], "n/a" if b["value"] is None else "%.4g" % b["value"], "n/a" if b["reference"] is None else "%.4g" % b["reference"],
                     "n/a" if b["deviation"] is None else "%+.3g" % b["deviation"], "n/a" if b["tolerance"] is None else "%g (%s)" % (b["tolerance"], b["tolerance_kind"]), b["provenance"]) for b in by_kind.get(kind, [])]
            if rows:
                self.table(["Id", "Check", "Status", "Value", "Reference", "Deviation", "Tolerance", "Provenance"], rows, caption=label, font=6, widths=[24, 50, 12, 14, 14, 14, 20, 40], align=["left"] * 3 + ["right"] * 3 + ["left"] * 2)
        n_fc = sum(1 for b in ds["benchmarks"] if b["kind"] == "field_calibration")
        n_fv = [b for b in ds["benchmarks"] if b["kind"] == "field_validation"]
        if n_fc or n_fv:
            n_out = sum(1 for b in n_fv if b["status"] == "FAIL")
            self.P("Calibration versus validation: %d field calibration record(s) (measured data the model inputs were tuned to; agreement there demonstrates fit, not predictive skill) and "
                   "%d hold-out record(s) (not used for tuning) are compared with the model; %d hold-out record(s) are outside the declared tolerance. Every excursion is a finding that limits "
                   "the use of the hydraulic and thermal results and is not removed. Laboratory validation of the hydrate model for the study fluid remains OPEN; the only independent hydrate "
                   "comparison is the secondary-source methane check." % (n_fc, len(n_fv), n_out))
        else:
            self.P("Calibration versus validation: no calibration data and no independent measured field or laboratory data exist for this study. The only independent comparison is the secondary-source methane hydrate check; "
                   "the hydraulic model is verified against analytic limits only. Experimental or field validation of the hydraulic and hydrate results is OPEN.")
        scen = [c["id"] for c in ds["cases"] if c["role"] == "scenario"]
        mit = [c["id"] for c in ds["cases"] if c["role"] == "mitigation"]
        self.P("Uncertainty and robustness: no Monte Carlo propagation is performed in this study; the declared scenario cases (%s) and mitigation cases (%s) bound the main drivers that were defined. "
               "Ambient temperature, external film coefficient, inhibitor losses and the hydrate-model error are not propagated and are listed as assumptions." % (", ".join(scen) or "none", ", ".join(mit) or "none"))
        self.P("Route and input integrity: %s" % ("; ".join("%s: %s" % (f["code"], f["message"]) for f in ds["route"]["findings"] if f["level"] != "info") or "no route integrity errors or warnings; information items are listed in the dataset") + ".")

    # --- 15 conclusions & actions ---
    def conclusions(self):
        ds = self.ds
        self.H("15 Conclusions and action register", 1)
        self.P("Conclusions (screening%s):" % (", synthetic data" if self.synthetic() else "; see the readiness label and the open items"), bold=True)
        for c in ds["criteria"]:
            bad = [k for k, v in c["by_case"].items() if v["status"] == "FAIL"]
            self.P("%s: %s." % (c["id"], ("not met in " + ", ".join(bad)) if bad else "met in all applicable cases"), style="List Bullet")
        self.P("The package demonstrates the workflow; no conclusion applies to a real system." if self.synthetic() else
               "These are screening results within the stated basis; open threats, unsupported analyses and data gaps remain open until closed by the actions below.", italic=True)
        n = 0
        rows = []

        def add(kind, text, evidence):
            nonlocal n
            n += 1
            rows.append(("A-%02d" % n, kind, text, evidence, "not assigned", "open"))
        for c in ds["criteria"]:
            bad = [k for k, v in c["by_case"].items() if v["status"] == "FAIL"]
            if bad:
                add("criterion not met", "%s not met in %s: mitigate, change the operating envelope or record an approved deviation" % (c["id"], ", ".join(bad)),
                    "Section 10, 13; sheets %s-%s" % (self.sheet_no(8), self.sheet_no(9)))
        for t in ds["threats"]:
            if t["status"] in ("open", "unsupported"):
                add("open threat" if t["status"] == "open" else "unsupported", "%s: %s" % (t["id"], t["reason"]), "threat matrix")
        for e in ds["events"]:
            if e["status"] == "external_open":
                add("external hand-off", "%s: run in a qualified external simulator and supply the result (%s)" % (e["id"], e.get("handoff", "")), "Section 9.1")
        for g in ds["data_gaps"]:
            add("data gap", "%s - %s" % (g["id"], g["item"]), "Section 3.4")
        self.landscape()
        self.table(["Id", "Type", "Action", "Evidence", "Owner", "Status"], rows, caption="Action register (all owners unassigned; linked to the evidence and unresolved criteria)", font=7,
                   widths=[14, 30, 130, 34, 24, 14], align=["left"] * 6)
        self.portrait()
        self.actions = rows

    # --- appendices ---
    def appendices(self, fig_dir):
        ds = self.ds
        self.H("16 Appendices", 1)
        self.H("Appendix A - Drawing package", 2)
        notes = []
        if not ds["route"].get("georeferenced"):
            notes.append("sheet %s is labelled as %s and not georeferenced" % (self.sheet_no(2), "a synthetic local grid" if ds["route"]["status"] == "synthetic" else "a schematic plan"))
        notes.append("sheets %s and %s are schematic and not to scale" % (self.sheet_no(5), self.sheet_no(6)))
        self.P("The A3 drawings follow, reduced to page width. The SVG sources are editable; the layout specification (layout_spec.json) lists every annotation with its data reference. "
               + "; ".join(notes).capitalize() + ". Drawing numbers stay stable; the sheet number counts the sheets actually produced.")
        for s in self.manifest["sheets"]:
            self.A3()
            self.picture(s["png"], 396, "Drawing %s: %s" % (s["drawing_number"], s["title"]), "%s - %s (A3 landscape, reduced)" % (s["drawing_number"], s["title"]))
        self.portrait()
        self.H("Appendix B - Standards applicability register", 2)
        rows = [(r["reference"], r["edition"] + ("; " + r["amendment"] if r.get("amendment") and r["amendment"] != "none listed" else ""), r["status"], r["date_checked"], r["requirement_class"],
                 r["assessment_status"], r["clause_used"], r["compliance_statement"]) for r in self.standards]
        self.landscape()
        self.table(["Reference", "Edition / amendment", "Status (verification)", "Checked", "Class", "Assessment", "Clause used", "Compliance statement"], rows,
                   caption="Standards register: no standard text was read; no clause-level conformity is claimed", font=6, widths=[24, 32, 60, 18, 18, 22, 22, 74], align=["left"] * 8)
        self.P("Not copied: licensed tables and clause text. Public publisher metadata verifies title, scope and status only; it does not prove clause-level conformity. "
               "DNV-RP-F116 is an integrity-management reference and not a source of numerical hydrate-margin or inhibitor-dose limits; DNV-ST-F101 structural checks are separate from the thermohydraulic qualification.")
        self.portrait()
        self.H("Appendix C - Fluid and inhibitor inputs", 2)
        self.P("Compositions are in Section 5; inhibitor rate balances in Section 10.")
        self.H("Appendix D - Case summaries", 2)
        rows = []
        for cid in self.cases:
            self.res.errors  # noqa: B018 - keeps the resolver reference
            s = ds["results"][cid]["summary"]
            rows.append(self.row([cid, "[[res:%s:summary.hc_total_kg_s|.1f]]" % cid, "[[res:%s:summary.std_gas_total_sm3_d|.3g]]" % cid, "[[res:%s:summary.water_total_kg_h|.0f]]" % cid,
                                  "[[res:%s:summary.lean_inhibitor_total_kg_h|.0f]]" % cid, "[[res:%s:summary.host_arrival_mass_kg_s|.1f]]" % cid]))
        self.table(["Case", "Hydrocarbon (kg/s)", "Gas equivalent at standard conditions (Sm3/d)", "Water (kg/h)", "Lean MEG (kg/h)", "Total at host (kg/s)"], rows,
                   caption="Case rate summary (standard volume from the NeqSim EOS gas density at 15 degC and 1.01325 bara; hydrocarbon mass basis is the specified rate)", font=7,
                   widths=[34, 26, 40, 22, 24, 24], align=["left"] + ["right"] * 5)
        self.H("Appendix E - Validation, convergence and reproducibility", 2)
        rows = []
        for cid in self.cases:
            r = ds["results"][cid]
            iters = ", ".join("%s %s" % (s["what"].replace("branch ", "br ").replace("->HOST-IN", ""), s["iterations"]) for s in r["solves"][-3:])
            rows.append((cid, r["status"], "yes" if r.get("cache", {}).get("hit") else "no", iters, "%.1f" % r["timings_s"]["case_total"] if not r.get("cache", {}).get("hit") else "cache", str(len(r.get("findings", [])))))
        self.table(["Case", "Convergence", "Cache hit", "Iterations (last solves)", "Run time (s)", "Findings"], rows, caption="Convergence and execution record per case", font=7, widths=[28, 24, 18, 70, 22, 16],
                   align=["left"] * 6)
        self._validation_appendix()
        self.P("Reproduce: neqsim flow-assurance-report <route.yaml> --basis <basis.yaml> --cases <cases.yaml> --out <dir> --strict. Dataset hash %s; model %s; NeqSim %s; solver %s; tool %s." % (
            ds["meta"]["dataset_hash"], ds["meta"]["model"]["name"], ds["meta"]["neqsim_version"], ds["meta"]["solver_version"], ds["meta"]["tool_version"]))
        self.H("Appendix F - Input hashes", 2)
        self.table(["Input", "Hash"], [(k, v) for k, v in ds["meta"]["input_hashes"].items()], caption="Input file content hashes", font=8, widths=[40, 60], align=["left", "left"])
        self.H("Appendix G - TwoFluidPipe qualification matrix (verbatim rows)", 2)
        q = ds["qualification"]
        self.P("Source: %s. These rows are reproduced unchanged; failed and not-qualified rows are preserved and no transient result of this report relies on TwoFluidPipe." % q["twofluid_matrix_note"])
        rows = [(r["capability"], r["implemented"], r["numerically_verified"], r["experimentally_qualified"], r["evidence"]) for r in q["twofluid_matrix"]]
        if rows:
            self.landscape()
            self.table(["Capability and configuration", "Implemented", "Numerically verified", "Experimentally qualified", "Evidence and use boundary"], rows, caption="TwoFluidPipe evidence matrix (as recorded in the repository)",
                       font=6, widths=[60, 18, 28, 34, 130], align=["left"] * 5)
            self.portrait()

    def _validation_appendix(self):
        v = self.validation
        if not v:
            self.P("Validation report not yet available at this build stage.", italic=True)
            return
        self.P("Overall result: %s. Checks run: %d; passed: %d; failed: %d; warnings: %d. Full machine-readable report: validation_report.json." % (v["status"], v["n_checks"], v["n_pass"], v["n_fail"], v["n_warn"]), bold=True)
        rows = [[r["id"], r["description"], r["status"], r["detail"][:260]] for r in v["checks"]]
        self.table(["Check", "Description", "Result", "Detail"], rows, caption="Validation checks", font=6, widths=[26, 70, 14, 100], align=["left"] * 4)

    def _fig(self, key, width):
        p, alt, cap = self.figs[key]
        return p, width, alt, cap

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
        self.system()
        self.fluids()
        self.methods()
        self.case_matrix()
        self.steady()
        self.transients()
        self.hydrate()
        self.other_threats()
        self.envelope()
        self.mitigation()
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
