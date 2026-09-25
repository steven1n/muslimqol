"""
Compatibility Pack Validator.

Compares discovered edible items against a declared MuslimQoL compatibility pack,
detecting missing classifications, extra items, duplicates, and reporting distributions.
"""

import json
import os
from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import PackValidationReport


def load_pack_classifications(pack_dir: str) -> Tuple[Dict[str, Dict[str, str]], List[str]]:
    """
    Scans a compatibility pack directory for food classification JSON files.
    Returns:
        item_map: Dict[item_id, {"status": str, "reason": str, "source": str}]
        duplicates: List[str] of duplicate item IDs found
    """
    item_map: Dict[str, Dict[str, str]] = {}
    duplicates: List[str] = []

    # Look for classification JSONs recursively under pack_dir
    for root, _, files in os.walk(pack_dir):
        for f in sorted(files):
            if not f.endswith(".json") or f in ("metadata.json", "pack.mcmeta"):
                continue
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, pack_dir)
            try:
                with open(full_path, "r", encoding="utf-8") as jf:
                    data = json.load(jf)
                    values = data.get("values", [])
                    for entry in values:
                        if not isinstance(entry, dict) or "item" not in entry:
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
            except Exception:
                pass

    return item_map, duplicates


def validate_pack(
    pack_dir: str,
    edible_candidates: List[str],
    total_items: Optional[List[str]] = None,
) -> Tuple[PackValidationReport, Dict[str, Dict[str, str]]]:
    """
    Validates a compatibility pack directory against discovered edible candidates.
    """
    pack_name = os.path.basename(os.path.abspath(pack_dir))
    item_map, duplicates = load_pack_classifications(pack_dir)

    candidate_set = set(edible_candidates)
    classified_set = set(item_map.keys())
    total_item_set = set(total_items or [])

    missing = sorted(list(candidate_set - classified_set))
    extra = sorted(list(classified_set - candidate_set))

    unknown_ids = []
    if total_item_set:
        for itm in classified_set:
            if itm not in total_item_set:
                unknown_ids.append(itm)

    status_dist: Dict[str, int] = {}
    reason_dist: Dict[str, int] = {}

    for info in item_map.values():
        st = info["status"]
        rs = info["reason"]
        status_dist[st] = status_dist.get(st, 0) + 1
        reason_dist[rs] = reason_dist.get(rs, 0) + 1

    report = PackValidationReport(
        pack_name=pack_name,
        classified_count=len(item_map),
        missing_items=missing,
        extra_items=extra,
        duplicate_items=duplicates,
        unknown_ids=sorted(unknown_ids),
        status_distribution=status_dist,
        reason_distribution=reason_dist,
    )

    return report, item_map
