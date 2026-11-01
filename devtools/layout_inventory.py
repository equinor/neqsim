"""Traceable physical-route and isolation-inventory evidence for layout studies.

The calculations in this module are deliberately geometric.  They derive pipe
length and contained volume from caller-supplied centreline coordinates and
internal diameters, then aggregate only explicitly referenced route and
equipment volumes into isolation sections.  No route, diameter, equipment
volume, valve location or fail state is inferred from process connectivity.
"""

from __future__ import annotations

import copy
import math


INVENTORY_SCHEMA = "plant_layout_physical_inventory.v1"
SOURCE_KINDS = {
    "as_built", "layout_model", "line_list", "piping_model", "project_assumption",
    "survey", "synthetic_assumption", "vendor_document",
}
EVIDENCE_STATUSES = {"CONTROLLED_PROJECT_INPUT", "REVIEW_REQUIRED", "ASSUMPTION"}
DEVICE_TYPES = {
    "blind", "check_valve", "emergency_shutdown_valve", "manual_valve",
    "other", "process_shutdown_valve",
}
DEVICE_STATES = {"OPEN", "CLOSED", "VARIABLE", "AS_IS", "NOT_APPLICABLE", "UNKNOWN"}
TOLERANCE_M = 1.0e-9


def _identifier(value, name):
    """Return a nonempty stable identifier."""
    if not isinstance(value, str) or not value.strip():
        raise ValueError("%s must be a nonempty string" % name)
    return value


def _number(value, name, positive=False, nonnegative=False):
    """Return a finite number with the requested range."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("%s must be a finite number" % name)
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("%s must be finite" % name)
    if positive and result <= 0.0:
        raise ValueError("%s must be positive" % name)
    if nonnegative and result < 0.0:
        raise ValueError("%s must be nonnegative" % name)
    return result


def _list(value, name):
    """Require an actual list."""
    if not isinstance(value, list):
        raise ValueError("%s must be a list" % name)
    return value


def _unique_identifiers(values, name):
    """Validate a list of unique nonempty identifiers."""
    result = []
    for index, value in enumerate(_list(values, name)):
        identifier = _identifier(value, "%s[%d]" % (name, index))
        if identifier in result:
            raise ValueError("Duplicate %s identifier: %s" % (name, identifier))
        result.append(identifier)
    return result


def _unique_objects(items, field, name):
    """Validate object identities and return their ordered set."""
    seen = set()
    for item in _list(items, name):
        if not isinstance(item, dict):
            raise ValueError("%s entries must be objects" % name)
        identifier = _identifier(item.get(field), "%s.%s" % (name, field))
        if identifier in seen:
            raise ValueError("Duplicate %s identifier: %s" % (name, identifier))
        seen.add(identifier)
    return seen


def _source(value, name):
    """Validate explicit physical-evidence provenance without claiming approval."""
    if not isinstance(value, dict):
        raise ValueError("%s must be an object" % name)
    if value.get("kind") not in SOURCE_KINDS:
        raise ValueError("%s.kind is unsupported" % name)
    _identifier(value.get("reference"), name + ".reference")
    _identifier(value.get("revision"), name + ".revision")
    if value.get("evidence_status") not in EVIDENCE_STATUSES:
        raise ValueError("%s.evidence_status is unsupported" % name)


def _points(value, name):
    """Validate a three-dimensional routed centreline in metres."""
    points = _list(value, name)
    if len(points) < 2:
        raise ValueError("%s needs at least two points" % name)
    result = []
    for index, point in enumerate(points):
        if not isinstance(point, list) or len(point) != 3:
            raise ValueError("%s[%d] needs three coordinates" % (name, index))
        result.append([_number(v, "%s[%d]" % (name, index)) for v in point])
    if route_length_m(result) <= TOLERANCE_M:
        raise ValueError("%s must have positive routed length" % name)
    return result


def route_length_m(points):
    """Return the Euclidean polyline length in metres."""
    return sum(math.sqrt(sum((b[i] - a[i]) ** 2 for i in range(3)))
               for a, b in zip(points, points[1:]))


def pipe_volume_m3(length_m, internal_diameter_m):
    """Return cylindrical internal volume from routed length and diameter."""
    return math.pi * internal_diameter_m ** 2 * length_m / 4.0


def normalize_inventory_fields(study, connection_ids, equipment_tags):
    """Normalize optional physical inventory fields in a copied layout study.

    Declared scope may intentionally reference evidence that has not yet been
    supplied.  Those absences become downstream blockers instead of invalidating
    the source study.  Supplied records, however, must be internally consistent.
    """
    scope = study.get("physical_inventory_scope")
    if scope is not None:
        if not isinstance(scope, dict):
            raise ValueError("physical_inventory_scope must be an object")
        _identifier(scope.get("id"), "physical_inventory_scope.id")
        _identifier(scope.get("revision"), "physical_inventory_scope.revision")
        _identifier(scope.get("source_reference"), "physical_inventory_scope.source_reference")
        scope["required_connection_ids"] = _unique_identifiers(
            scope.get("required_connection_ids"), "physical_inventory_scope.required_connection_ids")
        scope["required_equipment_tags"] = _unique_identifiers(
            scope.get("required_equipment_tags"), "physical_inventory_scope.required_equipment_tags")
        scope["required_isolation_section_ids"] = _unique_identifiers(
            scope.get("required_isolation_section_ids"),
            "physical_inventory_scope.required_isolation_section_ids")
        unknown_connections = set(scope["required_connection_ids"]) - set(connection_ids)
        unknown_equipment = set(scope["required_equipment_tags"]) - set(equipment_tags)
        if unknown_connections:
            raise ValueError("physical inventory scope references unknown connections: %s" %
                             sorted(unknown_connections))
        if unknown_equipment:
            raise ValueError("physical inventory scope references unknown equipment: %s" %
                             sorted(unknown_equipment))

    routes = study.setdefault("physical_routes", [])
    route_ids = _unique_objects(routes, "id", "physical_routes")
    route_by_id = {}
    for route in routes:
        connection_id = _identifier(route.get("connection_id"), "physical_routes.connection_id")
        if connection_id not in connection_ids:
            raise ValueError("Physical route %s references unknown connection" % route["id"])
        _identifier(route.get("line_tag"), "physical_routes.line_tag")
        route["internal_diameter_m"] = _number(
            route.get("internal_diameter_m"), "physical_routes.internal_diameter_m", positive=True)
        route["centerline_m"] = _points(route.get("centerline_m"), "physical_routes.centerline_m")
        _source(route.get("source"), "physical_routes.source")
        route_by_id[route["id"]] = route

    volumes = study.setdefault("equipment_volumes", [])
    volume_ids = _unique_objects(volumes, "id", "equipment_volumes")
    for volume in volumes:
        tag = _identifier(volume.get("equipment_tag"), "equipment_volumes.equipment_tag")
        if tag not in equipment_tags:
            raise ValueError("Equipment volume %s references unknown equipment" % volume["id"])
        volume["volume_m3"] = _number(
            volume.get("volume_m3"), "equipment_volumes.volume_m3", positive=True)
        _source(volume.get("source"), "equipment_volumes.source")

    devices = study.setdefault("isolation_devices", [])
    device_ids = _unique_objects(devices, "id", "isolation_devices")
    for device in devices:
        _identifier(device.get("tag"), "isolation_devices.tag")
        route_id = _identifier(device.get("route_id"), "isolation_devices.route_id")
        if route_id not in route_ids:
            raise ValueError("Isolation device %s references unknown route" % device["id"])
        device["chainage_m"] = _number(
            device.get("chainage_m"), "isolation_devices.chainage_m", nonnegative=True)
        length = route_length_m(route_by_id[route_id]["centerline_m"])
        if device["chainage_m"] > length + TOLERANCE_M:
            raise ValueError("Isolation device %s chainage exceeds route length" % device["id"])
        if device.get("device_type") not in DEVICE_TYPES:
            raise ValueError("Isolation device %s has unsupported device_type" % device["id"])
        if device.get("normal_state") not in DEVICE_STATES:
            raise ValueError("Isolation device %s has unsupported normal_state" % device["id"])
        if device.get("fail_state") not in DEVICE_STATES:
            raise ValueError("Isolation device %s has unsupported fail_state" % device["id"])
        _source(device.get("source"), "isolation_devices.source")

    sections = study.setdefault("isolation_sections", [])
    _unique_objects(sections, "id", "isolation_sections")
    device_by_id = {item["id"]: item for item in devices}
    for section in sections:
        section["route_ids"] = _unique_identifiers(section.get("route_ids"), "isolation_sections.route_ids")
        section["equipment_volume_ids"] = _unique_identifiers(
            section.get("equipment_volume_ids"), "isolation_sections.equipment_volume_ids")
        section["boundary_device_ids"] = _unique_identifiers(
            section.get("boundary_device_ids"), "isolation_sections.boundary_device_ids")
        if not section["route_ids"] and not section["equipment_volume_ids"]:
            raise ValueError("Isolation section %s has no contained inventory" % section["id"])
        if len(section["boundary_device_ids"]) < 2:
            raise ValueError("Isolation section %s needs at least two boundary devices" % section["id"])
        if not set(section["route_ids"]) <= route_ids:
            raise ValueError("Isolation section %s references unknown routes" % section["id"])
        if not set(section["equipment_volume_ids"]) <= volume_ids:
            raise ValueError("Isolation section %s references unknown equipment volumes" % section["id"])
        if not set(section["boundary_device_ids"]) <= device_ids:
            raise ValueError("Isolation section %s references unknown boundary devices" % section["id"])
        for device_id in section["boundary_device_ids"]:
            if device_by_id[device_id]["route_id"] not in section["route_ids"]:
                raise ValueError("Isolation section %s boundary device is outside its route set" % section["id"])
        _source(section.get("source"), "isolation_sections.source")
    return study


def _controlled_source(record):
    """Return whether one supplied record is controlled project evidence."""
    return record.get("source", {}).get("evidence_status") == "CONTROLLED_PROJECT_INPUT"


def build_inventory(study):
    """Build deterministic routed-volume and isolation-section evidence."""
    routes = []
    route_by_id = {}
    route_by_connection = {}
    for record in sorted(study.get("physical_routes", []), key=lambda value: value["id"]):
        length = route_length_m(record["centerline_m"])
        route = copy.deepcopy(record)
        route["length_m"] = length
        route["pipe_volume_m3"] = pipe_volume_m3(length, record["internal_diameter_m"])
        routes.append(route)
        route_by_id[route["id"]] = route
        route_by_connection.setdefault(route["connection_id"], []).append(route)

    volumes = {record["id"]: record for record in study.get("equipment_volumes", [])}
    sections = []
    for record in sorted(study.get("isolation_sections", []), key=lambda value: value["id"]):
        route_volume = sum(route_by_id[identifier]["pipe_volume_m3"] for identifier in record["route_ids"])
        equipment_volume = sum(volumes[identifier]["volume_m3"] for identifier in record["equipment_volume_ids"])
        section = copy.deepcopy(record)
        section["pipe_volume_m3"] = route_volume
        section["equipment_volume_m3"] = equipment_volume
        section["total_isolated_volume_m3"] = route_volume + equipment_volume
        sections.append(section)

    blockers = []
    scope = study.get("physical_inventory_scope")
    if scope is None:
        blockers.append({"code": "LAYOUT-INVENTORY-SCOPE-MISSING",
                         "message": "Physical piping and isolation inventory scope is not declared."})
    else:
        for connection_id in scope["required_connection_ids"]:
            if connection_id not in route_by_connection:
                blockers.append({"code": "LAYOUT-PIPE-ROUTE-MISSING", "subject": connection_id,
                                 "message": "Required connection has no supplied physical route."})
            elif any(not _controlled_source(route) for route in route_by_connection[connection_id]):
                blockers.append({"code": "LAYOUT-PIPE-ROUTE-REVIEW", "subject": connection_id,
                                 "message": "Required route provenance is not controlled project input."})
        by_tag = {}
        for record in study.get("equipment_volumes", []):
            by_tag.setdefault(record["equipment_tag"], []).append(record)
        for tag in scope["required_equipment_tags"]:
            if tag not in by_tag:
                blockers.append({"code": "LAYOUT-EQUIPMENT-VOLUME-MISSING", "subject": tag,
                                 "message": "Required equipment contained volume is not supplied."})
            elif any(not _controlled_source(record) for record in by_tag[tag]):
                blockers.append({"code": "LAYOUT-EQUIPMENT-VOLUME-REVIEW", "subject": tag,
                                 "message": "Required equipment-volume provenance is not controlled project input."})
        section_by_id = {record["id"]: record for record in study.get("isolation_sections", [])}
        device_by_id = {record["id"]: record for record in study.get("isolation_devices", [])}
        for section_id in scope["required_isolation_section_ids"]:
            if section_id not in section_by_id:
                blockers.append({"code": "LAYOUT-ISOLATION-SECTION-MISSING", "subject": section_id,
                                 "message": "Required isolation section is not supplied."})
            elif not _controlled_source(section_by_id[section_id]):
                blockers.append({"code": "LAYOUT-ISOLATION-SECTION-REVIEW", "subject": section_id,
                                 "message": "Required isolation-section provenance is not controlled project input."})
            else:
                for device_id in section_by_id[section_id]["boundary_device_ids"]:
                    device = device_by_id[device_id]
                    if not _controlled_source(device):
                        blockers.append({"code": "LAYOUT-ISOLATION-DEVICE-REVIEW", "subject": device_id,
                                         "message": "Boundary-device provenance is not controlled project input."})
                    if device["fail_state"] == "UNKNOWN":
                        blockers.append({"code": "LAYOUT-ISOLATION-FAIL-STATE-MISSING", "subject": device_id,
                                         "message": "Boundary-device fail state is explicitly unknown."})

    status = "NOT_EVALUATED" if scope is None else "INCOMPLETE" if blockers else "COMPLETE"
    return {
        "schema_version": INVENTORY_SCHEMA,
        "status": status,
        "ready_for_dynamic_safeguarding_design": not blockers and scope is not None,
        "scope": copy.deepcopy(scope),
        "routes": routes,
        "equipment_volumes": sorted(copy.deepcopy(study.get("equipment_volumes", [])),
                                    key=lambda value: value["id"]),
        "isolation_devices": sorted(copy.deepcopy(study.get("isolation_devices", [])),
                                    key=lambda value: value["id"]),
        "isolation_sections": sections,
        "blockers": blockers,
        "totals": {
            "route_length_m": sum(route["length_m"] for route in routes),
            "pipe_volume_m3": sum(route["pipe_volume_m3"] for route in routes),
            "equipment_volume_m3": sum(record["volume_m3"] for record in volumes.values()),
        },
        "calculation_basis": "Polyline Euclidean length and cylindrical internal volume; supplied geometry only.",
    }
