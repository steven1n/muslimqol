"""
Data models for the MuslimQoL Compatibility Audit Engine.

These models define facts, hints, conflicts, and review suggestions.
They are strictly separate from runtime MuslimQoL FoodClassification models.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple


class EvidenceKind(str, Enum):
    """Types of evidence supporting food classification review."""
    NAME_KEYWORD = "NAME_KEYWORD"
    NAME_EXCEPTION = "NAME_EXCEPTION"
    RECIPE_ITEM = "RECIPE_ITEM"
    RECIPE_TAG = "RECIPE_TAG"
    RECIPE_VARIABLE = "RECIPE_VARIABLE"
    ITEM_TAG = "ITEM_TAG"
    FOOD_COMPONENT = "FOOD_COMPONENT"
    REGISTRY_METADATA = "REGISTRY_METADATA"
    CURATED_PACK = "CURATED_PACK"
    UNTRUSTED_SELF_DESCRIPTION = "UNTRUSTED_SELF_DESCRIPTION"
    TRANSITIVE_RECIPE_ITEM = "TRANSITIVE_RECIPE_ITEM"
    TRANSITIVE_RECIPE_TAG = "TRANSITIVE_RECIPE_TAG"
    VARIABLE_PROVENANCE = "VARIABLE_PROVENANCE"
    PROVENANCE_PATH = "PROVENANCE_PATH"


@dataclass(frozen=True)
class AuditEvidence:
    """An individual piece of fact or hint discovered during mod analysis."""
    kind: EvidenceKind
    signal: str
    source: str
    weight: float = 1.0
    detail: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return {
            "kind": self.kind.value,
            "signal": self.signal,
            "source": self.source,
            "weight": self.weight,
            "detail": self.detail,
        }


class SuggestionCategory(str, Enum):
    """Review categories suggested by the heuristic engine.
    
    NOTE: These are human-review categories and do NOT correspond to final
    theological rulings or live MuslimQoL FoodStatus values.
    """
    HIGH_RISK_RESTRICTED = "HIGH_RISK_RESTRICTED"
    MEAT_PROVENANCE_REQUIRED = "MEAT_PROVENANCE_REQUIRED"
    LIKELY_PLANT_BASED = "LIKELY_PLANT_BASED"
    LIKELY_LOW_RISK_RECIPE = "LIKELY_LOW_RISK_RECIPE"
    FISH_REVIEW_BASELINE = "FISH_REVIEW_BASELINE"
    SEAFOOD_REVIEW = "SEAFOOD_REVIEW"
    AMBIGUOUS_RECIPE = "AMBIGUOUS_RECIPE"
    GENERAL_REVIEW = "GENERAL_REVIEW"
    NO_SIGNAL = "NO_SIGNAL"


class Confidence(str, Enum):
    """Confidence level of the audit suggestion."""
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


@dataclass(frozen=True)
class ParseDiagnostic:
    """Diagnostic emitted when reading or parsing JAR assets or packs."""
    source_path: str
    error_type: str  # JSON_DECODE_ERROR, MALFORMED_TAG, MALFORMED_RECIPE, PACK_PARSE_ERROR, UNKNOWN_ITEM_ID
    message: str
    severity: str = "WARNING"  # ERROR, WARNING

    def to_dict(self) -> Dict[str, Any]:
        return {
            "source_path": self.source_path,
            "error_type": self.error_type,
            "message": self.message,
            "severity": self.severity,
        }


@dataclass(frozen=True)
class EvidenceConflict:
    """A detected conflict between different evidence sources (e.g. name vs recipe)."""
    conflict_type: str
    description: str
    evidence_a: AuditEvidence
    evidence_b: AuditEvidence
    severity: str = "HIGH"  # HIGH, MEDIUM, LOW

    def to_dict(self) -> Dict[str, Any]:
        return {
            "conflict_type": self.conflict_type,
            "description": self.description,
            "severity": self.severity,
            "evidence_a": self.evidence_a.to_dict(),
            "evidence_b": self.evidence_b.to_dict(),
        }


@dataclass(frozen=True)
class AuditSuggestion:
    """Audit engine review recommendation for an item."""
    category: SuggestionCategory
    confidence: Confidence
    evidence: Tuple[AuditEvidence, ...] = ()
    conflicts: Tuple[EvidenceConflict, ...] = ()
    review_priority: int = 50

    def to_dict(self) -> Dict[str, Any]:
        return {
            "category": self.category.value,
            "confidence": self.confidence.value,
            "review_priority": self.review_priority,
            "evidence": [e.to_dict() for e in self.evidence],
            "conflicts": [c.to_dict() for c in self.conflicts],
        }


@dataclass
class ItemAuditResult:
    """Full audit result for a single candidate item."""
    item_id: str
    suggestion: AuditSuggestion
    curated_status: Optional[str] = None
    curated_reason: Optional[str] = None
    curated_source: Optional[str] = None
    recipes: List[str] = field(default_factory=list)
    tags: List[str] = field(default_factory=list)
    is_edible: bool = True

    def to_evidence_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "item": self.item_id,
            "suggestion": self.suggestion.category.value,
            "confidence": self.suggestion.confidence.value,
            "review_priority": self.suggestion.review_priority,
            "evidence": [
                {
                    "kind": e.kind.value,
                    "signal": e.signal,
                    "source": e.source,
                    "weight": e.weight,
                    "detail": e.detail,
                }
                for e in self.suggestion.evidence
            ],
            "conflicts": [c.to_dict() for c in self.suggestion.conflicts],
            "recipes": self.recipes,
            "tags": sorted(self.tags),
        }
        if self.curated_status:
            data["existing_curated_status"] = self.curated_status
            data["existing_curated_reason"] = self.curated_reason
            data["existing_curated_source"] = self.curated_source
        return data


@dataclass
class PackValidationReport:
    """Report comparing discovered edible items against a compatibility pack."""
    pack_name: str
    classified_count: int
    clean: bool = True
    missing_items: List[str] = field(default_factory=list)
    extra_items: List[str] = field(default_factory=list)
    duplicate_items: List[str] = field(default_factory=list)
    unknown_ids: List[str] = field(default_factory=list)
    status_distribution: Dict[str, int] = field(default_factory=dict)
    reason_distribution: Dict[str, int] = field(default_factory=dict)
    diagnostics: List[ParseDiagnostic] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "clean": self.clean,
            "classified": self.classified_count,
            "missing": len(self.missing_items),
            "extra": len(self.extra_items),
            "duplicates": len(self.duplicate_items),
            "unknown": len(self.unknown_ids),
            "missing_items": sorted(self.missing_items),
            "extra_items": sorted(self.extra_items),
            "duplicate_items": sorted(self.duplicate_items),
            "unknown_ids": sorted(self.unknown_ids),
            "status_distribution": self.status_distribution,
            "reason_distribution": self.reason_distribution,
            "diagnostics": [d.to_dict() for d in self.diagnostics],
        }
