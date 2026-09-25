"""
Audit Report Generator.

Emits machine-readable summary.json, granular evidence.json, and
human-readable prioritized review.md for auditor inspection.
"""

import json
import os
from typing import Any, Dict, List, Optional

from tools.compatibility.audit.models import (
    Confidence,
    ItemAuditResult,
    PackValidationReport,
    SuggestionCategory,
)


def generate_summary_dict(
    mod_id: str,
    jar_sha256: str,
    total_items: int,
    edible_candidates: int,
    results: List[ItemAuditResult],
    pack_report: Optional[PackValidationReport] = None,
) -> Dict[str, Any]:
    """Generates the structured dictionary for summary.json."""
    suggestion_counts: Dict[str, int] = {
        cat.value: 0 for cat in SuggestionCategory
    }
    for r in results:
        cat_val = r.suggestion.category.value
        suggestion_counts[cat_val] = suggestion_counts.get(cat_val, 0) + 1

    summary: Dict[str, Any] = {
        "mod_id": mod_id,
        "jar_sha256": jar_sha256,
        "total_items": total_items,
        "edible_candidates": edible_candidates,
        "suggestions": suggestion_counts,
    }

    if pack_report:
        summary["pack_validation"] = {
            "classified": pack_report.classified_count,
            "missing": len(pack_report.missing_items),
            "extra": len(pack_report.extra_items),
            "duplicates": len(pack_report.duplicate_items),
            "status_distribution": pack_report.status_distribution,
            "reason_distribution": pack_report.reason_distribution,
        }

    return summary


def write_summary_json(path: str, summary_dict: Dict[str, Any]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(summary_dict, f, indent=2)


def write_evidence_json(path: str, results: List[ItemAuditResult]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    items_data = [r.to_evidence_dict() for r in results]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(items_data, f, indent=2)


def write_review_md(
    path: str,
    mod_id: str,
    jar_sha256: str,
    results: List[ItemAuditResult],
    pack_report: Optional[PackValidationReport] = None,
) -> None:
    """Generates prioritized Markdown review report."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)

    # Sort results by review_priority descending
    sorted_results = sorted(results, key=lambda x: x.suggestion.review_priority, reverse=True)

    # Group results by category
    by_category: Dict[SuggestionCategory, List[ItemAuditResult]] = {
        cat: [] for cat in SuggestionCategory
    }
    for r in sorted_results:
        by_category[r.suggestion.category].append(r)

    # Collect conflicts
    items_with_conflicts = [r for r in sorted_results if r.suggestion.conflicts]

    lines: List[str] = [
        f"# Food Compatibility Audit Review: `{mod_id}`",
        "",
        "> [!IMPORTANT]",
        "> **Notice**: This review report was generated deterministically by the MuslimQoL Compatibility Audit Engine.",
        "> Heuristic suggestions and review priorities are diagnostic indicators for human reviewers and do NOT",
        "> represent religious rulings or automatically published MuslimQoL classifications.",
        "",
        "## Audit Overview",
        "",
        f"- **Mod ID**: `{mod_id}`",
        f"- **JAR SHA-256**: `{jar_sha256}`",
        f"- **Total Registry Items Discovered**: {len(sorted_results)} (edible candidates)",
        f"- **Items with Detected Conflicts**: {len(items_with_conflicts)}",
    ]

    if pack_report:
        lines.extend([
            f"- **Curated Pack Validated**: `{pack_report.pack_name}`",
            f"  - Classified in pack: {pack_report.classified_count}",
            f"  - Missing in pack: {len(pack_report.missing_items)}",
            f"  - Extra in pack: {len(pack_report.extra_items)}",
            f"  - Duplicates in pack: {len(pack_report.duplicate_items)}",
        ])

    lines.append("")

    # Section 1: Evidence Conflicts (highest priority for human attention)
    if items_with_conflicts:
        lines.extend([
            "---",
            "## 1. Evidence Conflicts & Diagnostic Warnings",
            "",
            "The following items exhibit contradictions between name keywords, qualifiers, and recipe composition:",
            "",
            "| Item ID | Priority | Conflict Type | Severity | Description | Curated Status |",
            "| :--- | :---: | :--- | :---: | :--- | :--- |",
        ])
        for r in items_with_conflicts:
            for c in r.suggestion.conflicts:
                cur = f"`{r.curated_status}`" if r.curated_status else "*None*"
                lines.append(
                    f"| `{r.item_id}` | {r.suggestion.review_priority} | `{c.conflict_type}` | **{c.severity}** | {c.description} | {cur} |"
                )
        lines.append("")

    # Section 2: Pack Mismatches (if any)
    if pack_report and (pack_report.missing_items or pack_report.extra_items or pack_report.duplicate_items):
        lines.extend([
            "---",
            "## 2. Compatibility Pack Mismatches",
            "",
        ])
        if pack_report.missing_items:
            lines.append(f"### Missing Items ({len(pack_report.missing_items)})")
            for itm in pack_report.missing_items:
                lines.append(f"- `{itm}`")
        if pack_report.extra_items:
            lines.append(f"### Extra Items in Pack ({len(pack_report.extra_items)})")
            for itm in pack_report.extra_items:
                lines.append(f"- `{itm}`")
        if pack_report.duplicate_items:
            lines.append(f"### Duplicate Entries ({len(pack_report.duplicate_items)})")
            for itm in pack_report.duplicate_items:
                lines.append(f"- `{itm}`")
        lines.append("")

    def render_category_section(cat: SuggestionCategory, title: str, desc: str) -> None:
        items = by_category[cat]
        if not items:
            return
        lines.extend([
            "---",
            f"## {title} ({len(items)} items)",
            "",
            desc,
            "",
            "| Item ID | Priority | Confidence | Key Evidence | Curated Status | Curated Reason |",
            "| :--- | :---: | :---: | :--- | :--- | :--- |",
        ])
        for r in items:
            key_signals = [e.signal for e in r.suggestion.evidence[:3]]
            signals_str = ", ".join(f"`{s}`" for s in key_signals) if key_signals else "*None*"
            cur_st = f"**{r.curated_status}**" if r.curated_status else "*Unset*"
            cur_rs = f"`{r.curated_reason}`" if r.curated_reason else "*Unset*"
            lines.append(
                f"| `{r.item_id}` | {r.suggestion.review_priority} | {r.suggestion.confidence.value} | {signals_str} | {cur_st} | {cur_rs} |"
            )
        lines.append("")

    render_category_section(
        SuggestionCategory.HIGH_RISK_RESTRICTED,
        "Critical / High-Risk Swine Items",
        "Items with direct pork/bacon/ham ingredients, tags, or unexcepted swine names."
    )

    render_category_section(
        SuggestionCategory.AMBIGUOUS_RECIPE,
        "Variable / Ambiguous Recipes",
        "Dishes with alternative or compound fillings permitting distinct ingredients (e.g. pork vs chicken vs mushroom)."
    )

    render_category_section(
        SuggestionCategory.MEAT_PROVENANCE_REQUIRED,
        "Meat Provenance Required",
        "Items containing livestock or poultry meat requiring halal slaughter provenance verification."
    )

    render_category_section(
        SuggestionCategory.SEAFOOD_REVIEW,
        "Seafood Review",
        "Items containing cephalopods (squid, octopus) or shellfish requiring jurisprudence-specific rulings."
    )

    render_category_section(
        SuggestionCategory.LIKELY_PLANT_BASED,
        "Likely Plant-Based / Permissible Baseline",
        "Pure crop, vegetable, fruit, grain, fungal, dairy, egg, or scaled fish items."
    )

    render_category_section(
        SuggestionCategory.GENERAL_REVIEW,
        "General Review Required",
        "Items with alcohol signals, name qualifiers, or ambiguous processed ingredients."
    )

    render_category_section(
        SuggestionCategory.NO_SIGNAL,
        "No Meaningful Signal Discovered",
        "Items lacking recipes, identifiable tags, and recognizable food keywords."
    )

    # Detailed item appendix
    lines.extend([
        "---",
        "## Detailed Item Breakdown",
        "",
    ])
    for r in sorted_results:
        lines.extend([
            f"### `{r.item_id}`",
            f"- **Suggestion**: `{r.suggestion.category.value}` (Confidence: {r.suggestion.confidence.value}, Priority: {r.suggestion.review_priority})",
        ])
        if r.curated_status:
            lines.append(f"- **Curated Pack Status**: `{r.curated_status}` (Reason: `{r.curated_reason}`, Source: `{r.curated_source}`)")
        if r.recipes:
            lines.append("- **Recipes**:")
            for rec in r.recipes:
                lines.append(f"  - {rec}")
        else:
            lines.append("- **Recipes**: *No recipe found in JAR*")
        if r.tags:
            lines.append(f"- **Tags**: {', '.join(f'`#{t}`' for t in sorted(r.tags))}")
        if r.suggestion.conflicts:
            lines.append("- **Conflicts**:")
            for c in r.suggestion.conflicts:
                lines.append(f"  - `[{c.severity}]` {c.conflict_type}: {c.description}")
        lines.append("")

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
