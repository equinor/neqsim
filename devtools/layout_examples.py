"""Public synthetic concept-layout inputs; values are assumptions, never design criteria.

The examples exercise geometry and source handling without requiring a JVM or
licensed standards. They deliberately retain required safety evidence gaps.
"""
from copy import deepcopy


def _equipment(tag, kind, size, mass, deck, withdrawal=1.5):
    """Create an assumed equipment package with explicit local access volumes."""
    length, width, height = size
    return {
        "tag": tag,
        "kind": kind,
        "size_m": list(size),
        "mass_kg": mass,
        "allowed_decks": [deck],
        "rotations_deg": [0, 90],
        "dimension_source": {
            "kind": "synthetic_assumption",
            "reference": "SYNTHETIC-BASIS-001: assumed installed package dimensions, not vendor data",
        },
        "envelopes": [
            {"kind": "maintenance", "bounds_m": [length, 0, 0, length + withdrawal, width, height]},
            {"kind": "operation", "bounds_m": [0, -1, 0, length, 0, 2.2]},
        ],
    }


def _corridors(deck, width, depth):
    """Reserve an assumed two-metre perimeter corridor, with no compliance claim."""
    bounds = ([0, 0, width, 2], [0, depth - 2, width, depth],
              [0, 0, 2, depth], [width - 2, 0, width, depth])
    return [
        {"id": "%s-ESC-%d" % (deck, index + 1), "deck": deck,
         "bounds_m": list(box), "kind": "escape", "source": "SYNTHETIC-BASIS-001"}
        for index, box in enumerate(bounds)
    ]


def _standards():
    """Catalogue metadata provides no standard-derived numerical acceptance rules."""
    return [
        {"document": "NORSOK S-001", "edition": "2020+AC:2021", "access_status": "metadata_only",
         "reference": "https://standard.no/en/sectors/petroleum/norsok-standards/s-safety-she/"},
        {"document": "NORSOK S-002", "edition": "2025+AC:2026", "access_status": "metadata_only",
         "reference": "https://standard.no/en/sectors/petroleum/norsok-standards/s-safety-she/"},
        {"document": "NORSOK L-002", "edition": "2016", "access_status": "metadata_only",
         "reference": "https://standard.no/en/sectors/petroleum/norsok-standards/l-piping--layout/"},
        {"document": "IEC 60079-10-1", "edition": "2020", "access_status": "metadata_only",
         "reference": "https://webstore.iec.ch/en/publication/63327"},
    ]


def demo_study(facility="onshore"):
    """Return a fresh synthetic onshore or three-deck offshore study dictionary."""
    if facility not in ("onshore", "offshore"):
        raise ValueError("demo facility must be 'onshore' or 'offshore'")
    offshore = facility == "offshore"
    decks = ([{"id": "LOWER", "width_m": 36, "depth_m": 24, "elevation_m": 0,
               "height_limit_m": 7, "mass_limit_kg": 150000},
              {"id": "MAIN", "width_m": 36, "depth_m": 24, "elevation_m": 8,
               "height_limit_m": 7, "mass_limit_kg": 200000},
              {"id": "UPPER", "width_m": 36, "depth_m": 24, "elevation_m": 16,
               "height_limit_m": 8, "mass_limit_kg": 120000}]
             if offshore else
             [{"id": "GROUND", "width_m": 64, "depth_m": 40, "elevation_m": 0,
               "height_limit_m": 20}])
    assignments = ["LOWER"] * 3 + ["MAIN"] * 3 + ["UPPER"] * 3 if offshore else ["GROUND"] * 8
    definitions = [
        ("V-101", "separator", [6, 3, 4], 22000),
        ("V-102", "separator", [5, 3, 4], 18000),
        ("P-101", "pump", [3, 2, 2], 3500),
        ("K-101", "compressor", [6, 4, 4], 28000),
        ("E-101", "heat_exchanger", [5, 3, 3], 12000),
        ("V-103", "scrubber", [4, 3, 5], 10000),
        ("P-102", "pump", [3, 2, 2], 3500),
        ("U-101", "utility_package", [5, 3, 3], 8000),
    ]
    if offshore:
        definitions.append(("U-102", "electrical_package", [4, 3, 3], 6000))
    equipment = [_equipment(*definition, deck) for definition, deck in zip(definitions, assignments)]
    edges = [("V-101", "V-102", "liquid"), ("V-102", "P-101", "oil"),
             ("V-101", "K-101", "gas"), ("K-101", "E-101", "gas"),
             ("E-101", "V-103", "gas"), ("V-101", "P-102", "water"),
             ("U-101", "K-101", "utility")]
    if offshore:
        edges.append(("U-102", "K-101", "power_interface"))
    rules = [
        {"id": "SYN-SEPARATION-001", "kind": "minimum_separation", "required": True,
         "from_tag": "V-101", "to_tag": "K-101", "minimum_m": 1.0,
         "source": {"kind": "project_requirement", "reference": "SYNTHETIC-BASIS-001: training-only geometric criterion; not a recommended safe distance",
                    "access_status": "verified_full_text"}},
        {"id": "HAZARD-ASSESSMENT", "kind": "required_evidence", "required": True,
         "description": "Qualified fire/explosion/toxic exposure and hazardous-area studies",
         "source": {"kind": "project_requirement", "reference": "SYNTHETIC-BASIS-001: safety design remains outside this synthetic example",
                    "access_status": "verified_full_text"}},
        {"id": "ESCAPE-INDEPENDENCE", "kind": "required_evidence", "required": True,
         "description": "Independent safe evacuation under governing common hazard scenarios",
         "source": {"kind": "project_requirement", "reference": "SYNTHETIC-BASIS-001: corridor connectivity is not evacuation qualification",
                    "access_status": "verified_full_text"}},
        {"id": "VENDOR-PACKAGE-CONFIRMATION", "kind": "required_evidence", "required": True,
         "description": "Verified installed dimensions, auxiliaries, maintenance and lifting envelopes",
         "source": {"kind": "project_requirement", "reference": "SYNTHETIC-BASIS-001: all dimensions are synthetic assumptions",
                    "access_status": "verified_full_text"}},
    ]
    if offshore:
        rules.append({
            "id": "STRUCTURAL-MARINE-VERIFICATION", "kind": "required_evidence", "required": True,
            "description": "Deck strength, dry/operating/test and construction/maintenance loads, CoG and marine interfaces",
            "source": {"kind": "project_requirement", "reference": "SYNTHETIC-BASIS-001: assumed mass limits do not establish structural adequacy",
                       "access_status": "verified_full_text"},
        })
    data = {
        "schema_version": "plant_layout_study.v1",
        "project": {"id": "SYN-OFFSHORE-001" if offshore else "SYN-ONSHORE-001",
                    "title": "Synthetic three-deck offshore processing module" if offshore else "Synthetic onshore separation and compression plant",
                    "revision": "A", "maturity": "SCREENING",
                    "facility_type": "offshore_fixed" if offshore else "onshore", "jurisdiction": "synthetic"},
        "coordinate_system": {"length_unit": "m", "mass_unit": "kg", "origin": [0, 0, 0],
                              "north_deg": 0, "elevation_datum": "project datum"},
        "decks": decks, "equipment": equipment,
        "connections": [{"id": "LINE-%03d" % (i + 1), "from_tag": a, "to_tag": b, "service": service}
                        for i, (a, b, service) in enumerate(edges)],
        "corridors": [item for d in decks for item in _corridors(d["id"], d["width_m"], d["depth_m"])],
        "exits": [{"id": d["id"] + suffix, "deck": d["id"], "x_m": x, "y_m": 1}
                  for d in decks for suffix, x in (("-EXIT-W", 1), ("-EXIT-E", d["width_m"] - 1))],
        "obstacles": [], "rules": rules, "standards": _standards(),
        "assumptions": [
            "SYNTHETIC-BASIS-001 is an educational input basis, not a project design basis or standard.",
            "All package dimensions, envelopes, operating masses, deck limits and two-metre corridor widths are synthetic assumptions.",
            "Orthogonal rectangular packages and level rectangular decks are the supported screening geometry.",
            "Connection distances are geometric proxies; no detailed piping, hydraulic, stress or cable design has been performed.",
            "Catalogue editions are candidate metadata; adoption and full-text clause applicability require project review.",
        ],
        "data_gaps": [
            "Vendor dimensions, nozzles, auxiliaries and operation/inspection/replacement/lifting requirements are not verified.",
            "No qualified hazardous-area, fire, explosion, toxic dispersion or evacuation exposure/capacity evidence.",
            "No terrain/flood/drainage, structural, construction or marine qualification.",
        ],
        "options": {"alternatives": 3, "grid_m": 1.0, "max_candidates": 100000, "backend": "deterministic"},
    }
    return deepcopy(data)
