"""
Compatibility Pack Validator.

Compares discovered edible items against a declared MuslimQoL compatibility pack,
detecting missing classifications, extra items, duplicates, unknown item IDs,
and reporting distributions and parse diagnostics.
"""

import json
import os
from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import PackValidationReport, ParseDiagnostic


def load_pack_classifications(
    pack_dir: str,
) -> Tuple[Dict[str, Dict[str, str]], List[str], List[ParseDiagnostic]]:
    """
    Scans a compatibility pack directory for food classification JSON files.
    Returns:
        item_map: Dict[item_id, {"status": str, "reason": str, "source": str}]
        duplicates: List[str] of duplicate item IDs found
        diagnostics: List[ParseDiagnostic] of any encountered errors
    """
    item_map: Dict[str, Dict[str, str]] = {}
    duplicates: List[str] = []
    diagnostics: List[ParseDiagnostic] = []

    # Check compatibility.json or metadata.json if present
    for meta_filename in ("compatibility.json", "metadata.json"):
        for root, _, files in os.walk(pack_dir):
            if meta_filename in files:
                meta_path = os.path.join(root, meta_filename)
                rel_meta = os.path.relpath(meta_path, pack_dir)
                try:
                    with open(meta_path, "r", encoding="utf-8") as mf:
                        meta = json.load(mf)
                        if not isinstance(meta, dict):
                            diagnostics.append(
                                ParseDiagnostic(
                                    source_path=rel_meta,
                                    error_type="PACK_PARSE_ERROR",
                                    message=f"{meta_filename} root must be a JSON object",
                                    severity="ERROR",
                                )
                            )
                        elif "format" in meta and meta["format"] != 1:
                            diagnostics.append(
                                ParseDiagnostic(
                                    source_path=rel_meta,
                                    error_type="PACK_PARSE_ERROR",
                                    message=f"Unsupported format version in {meta_filename}: {meta['format']} (expected 1)",
                                    severity="ERROR",
                                )
                            )
                except Exception as e:
                    diagnostics.append(
                        ParseDiagnostic(
                            source_path=rel_meta,
                            error_type="PACK_PARSE_ERROR",
                            message=f"Failed to read {meta_filename}: {e}",
                            severity="ERROR",
                        )
                    )

    # Look for classification JSONs recursively under pack_dir
    for root, _, files in os.walk(pack_dir):
        for f in sorted(files):
            if not f.endswith(".json") or f in ("compatibility.json", "metadata.json", "pack.mcmeta"):
                continue
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, pack_dir)
            try:
                with open(full_path, "r", encoding="utf-8") as jf:
                    data = json.load(jf)
            except Exception as e:
                diagnostics.append(
                    ParseDiagnostic(
                        source_path=rel_path,
                        error_type="PACK_PARSE_ERROR",
                        message=f"Failed to parse classification JSON: {e}",
                        severity="ERROR",
                    )
                )
                continue

            if not isinstance(data, dict) or "values" not in data or not isinstance(data["values"], list):
                diagnostics.append(
                    ParseDiagnostic(
                        source_path=rel_path,
                        error_type="PACK_PARSE_ERROR",
                        message="Classification file root must contain a 'values' list",
                        severity="ERROR",
                    )
                )
                continue

            values = data.get("values", [])
            for entry in values:
                if not isinstance(entry, dict) or "item" not in entry or not isinstance(entry["item"], str) or not entry["item"]:
                    diagnostics.append(
                        ParseDiagnostic(
                            source_path=rel_path,
                            error_type="PACK_PARSE_ERROR",
                            message=f"Malformed entry in values: {entry}",
                            severity="ERROR",
                        )
                    )
                    continue
                item_id = entry["item"]
                status = entry.get("status", "UNKNOWN")
                reason = entry.get("reason", "unspecified")
                if item_id in item_map:
                    duplicates.append(item_id)
                else:
                    item_map[item_id] = {
                        "status": status,
                        "reason": reason,
                        "source": rel_path,
                    }

    return item_map, duplicates, diagnostics


def validate_pack(
    pack_dir: str,
    edible_candidates: List[str],
    total_items: Optional[List[str]] = None,
) -> Tuple[PackValidationReport, Dict[str, Dict[str, str]]]:
    """
    Validates a compatibility pack directory against discovered edible candidates.
    """
    pack_name = os.path.basename(os.path.abspath(pack_dir))
    item_map, duplicates, diagnostics = load_pack_classifications(pack_dir)

    candidate_set = set(edible_candidates)
    classified_set = set(item_map.keys())
    total_item_set = set(total_items or [])

    missing = sorted(list(candidate_set - classified_set))
    extra = sorted(list(classified_set - candidate_set))

    unknown_ids: List[str] = []
    if total_item_set:
        for itm in classified_set:
            if itm not in total_item_set:
                unknown_ids.append(itm)
                diagnostics.append(
                    ParseDiagnostic(
                        source_path=item_map[itm]["source"],
                        error_type="UNKNOWN_ITEM_ID",
                        message=f"Item '{itm}' classified in pack was not discovered in mod registry",
                        severity="ERROR",
                    )
                )

    status_dist: Dict[str, int] = {}
    reason_dist: Dict[str, int] = {}

    for info in item_map.values():
        st = info["status"]
        rs = info["reason"]
        status_dist[st] = status_dist.get(st, 0) + 1
        reason_dist[rs] = reason_dist.get(rs, 0) + 1

    has_errors = any(d.severity == "ERROR" for d in diagnostics)
    clean = (
        len(missing) == 0
        and len(extra) == 0
        and len(duplicates) == 0
        and len(unknown_ids) == 0
        and not has_errors
    )

    report = PackValidationReport(
        pack_name=pack_name,
        classified_count=len(item_map),
        clean=clean,
        missing_items=missing,
        extra_items=extra,
        duplicate_items=duplicates,
        unknown_ids=sorted(unknown_ids),
        status_distribution=status_dist,
        reason_distribution=reason_dist,
        diagnostics=diagnostics,
    )

    return report, item_map
