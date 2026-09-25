"""
Compatibility Audit Evidence Engine.

Synthesizes name heuristics, recipe facts, and tag facts to produce
prioritized review suggestions, confidence levels, and conflict diagnostics.
"""

from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import (
    AuditEvidence,
    AuditSuggestion,
    Confidence,
    EvidenceConflict,
    EvidenceKind,
    ItemAuditResult,
    SuggestionCategory,
)
from tools.compatibility.audit.name_heuristics import (
    ALCOHOL_REVIEW,
    AMBIGUOUS_MEAT,
    FISH_HINTS,
    HIGH_RISK_SWINE,
    MEAT_PROVENANCE,
    MODIFIER_SPECIES,
    NAME_EXCEPTIONS,
    PLANT_HINTS,
    SEAFOOD_REVIEW,
    SWINE_ASSOCIATED,
    UNTRUSTED_SELF_DESCRIPTION,
    analyze_registry_id,
)
from tools.compatibility.audit.recipe_parser import ParsedRecipe
from tools.compatibility.audit.tag_parser import TagRegistry


SWINE_ITEM_SIGNALS = {
    "minecraft:porkchop",
    "minecraft:cooked_porkchop",
    "farmersdelight:bacon",
    "farmersdelight:cooked_bacon",
    "farmersdelight:ham",
    "farmersdelight:smoked_ham",
    "farmersdelight:minced_beef",  # not swine, listed for clarity
}

SWINE_TAG_SIGNALS = {
    "c:foods/raw_pork",
    "c:foods/cooked_pork",
    "c:foods/raw_bacon",
    "c:foods/cooked_bacon",
    "c:foods/pork",
    "c:foods/bacon",
}

MEAT_TAG_SIGNALS = {
    "c:foods/raw_meat",
    "c:foods/cooked_meat",
    "c:foods/raw_beef",
    "c:foods/cooked_beef",
    "c:foods/raw_chicken",
    "c:foods/cooked_chicken",
    "c:foods/raw_mutton",
    "c:foods/cooked_mutton",
    "c:foods/meat",
}

SEAFOOD_ITEM_SIGNALS = {
    "minecraft:ink_sac",
    "minecraft:glow_ink_sac",
    "minecraft:nautilus_shell",
}


ANIMAL_FEED_TAGS = {
    "minecraft:pig_food",
    "minecraft:rabbit_food",
    "minecraft:chicken_food",
    "minecraft:parrot_food",
    "minecraft:cat_food",
    "minecraft:ocelot_food",
    "minecraft:wolf_food",
    "minecraft:strider_food",
    "minecraft:strider_tempt_items",
    "minecraft:fox_food",
    "minecraft:cow_food",
    "minecraft:sheep_food",
    "minecraft:horse_food",
}


def is_swine_item_or_tag(signal: str) -> bool:
    clean = signal[1:] if signal.startswith("#") else signal
    lower = clean.lower()
    if lower in ANIMAL_FEED_TAGS or lower.endswith("_food"):
        return False
    return any(p in lower for p in ("pork", "bacon", "ham", "lard", "swine"))


def is_meat_item_or_tag(signal: str) -> bool:
    clean = signal[1:] if signal.startswith("#") else signal
    lower = clean.lower()
    if lower in ANIMAL_FEED_TAGS or lower.endswith("_food") or lower == "origins:meat":
        return False
    # Fish is not terrestrial livestock meat
    if is_fish_item_or_tag(signal):
        return False
    return any(m in lower for m in ("beef", "chicken", "mutton", "lamb", "rabbit", "meat", "poultry"))


def is_fish_item_or_tag(signal: str) -> bool:
    clean = signal[1:] if signal.startswith("#") else signal
    lower = clean.lower()
    return any(f in lower for f in ("fish", "cod", "salmon", "tuna"))


def is_seafood_review_item_or_tag(signal: str) -> bool:
    clean = signal[1:] if signal.startswith("#") else signal
    lower = clean.lower()
    return any(sf in lower for sf in ("squid", "octopus", "crab", "shrimp", "prawn", "lobster", "clam", "oyster", "ink_sac", "calamari"))


def is_plant_item_or_tag(signal: str) -> bool:
    clean = signal[1:] if signal.startswith("#") else signal
    lower = clean.lower()
    return any(pl in lower for pl in ("crop", "vegetable", "fruit", "grain", "wheat", "rice", "potato", "carrot", "cabbage", "tomato", "onion", "mushroom", "berry", "melon", "dough", "pasta", "bread", "salad", "milk", "egg", "sugar", "cocoa"))


class EvidenceEngine:
    """Synthesizes evidence and produces suggestions."""

    def __init__(self, tag_registry: Optional[TagRegistry] = None):
        self.tag_registry = tag_registry or TagRegistry()

    def analyze_item(
        self,
        item_id: str,
        recipes: Optional[List[ParsedRecipe]] = None,
        item_tags: Optional[Set[str]] = None,
        curated_info: Optional[Dict[str, str]] = None,
    ) -> ItemAuditResult:
        """
        Analyzes a single item by integrating:
        1. Registry path tokens and keyword heuristics
        2. Recipes producing this item
        3. Tags applied directly to this item
        4. Curated pack data (if provided)
        """
        recipes = recipes or []
        item_tags = item_tags or set()

        tokens, name_evidence = analyze_registry_id(item_id)
        all_evidence: List[AuditEvidence] = list(name_evidence)
        conflicts: List[EvidenceConflict] = []

        # Add tag evidence
        for tag in sorted(item_tags):
            all_evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.ITEM_TAG,
                    signal=tag,
                    source=f"data/tags/item",
                    weight=0.8,
                    detail=f"Item tagged with #{tag}"
                )
            )

        # Add recipe evidence
        recipe_evidence: List[AuditEvidence] = []
        for r in recipes:
            for ev in r.evidence:
                recipe_evidence.append(ev)
                all_evidence.append(ev)

        # Track key flags
        has_recipe_swine = False
        has_recipe_meat = False
        has_recipe_fish = False
        has_recipe_seafood_review = False
        has_recipe_variable = False
        recipe_all_plant_like = True if recipes else False

        has_tag_swine = any(self.tag_registry.is_swine_signal(t) for t in item_tags)
        has_tag_meat = any(self.tag_registry.is_meat_signal(t) for t in item_tags)
        has_tag_fish = any(self.tag_registry.is_fish_signal(t) for t in item_tags)
        has_tag_seafood_review = any(self.tag_registry.is_seafood_review_signal(t) for t in item_tags)

        # Analyze recipe ingredients
        for ev in recipe_evidence:
            sig = ev.signal
            if ev.kind == EvidenceKind.RECIPE_VARIABLE:
                has_recipe_variable = True
                recipe_all_plant_like = False
                # Check if variable ingredient mixes swine/meat with plants
                low = sig.lower()
                if any(x in low for x in ("pork", "meat", "chicken", "beef")) and any(y in low for y in ("mushroom", "vegetable", "fish")):
                    conflicts.append(
                        EvidenceConflict(
                            conflict_type="VARIABLE_RECIPE_DIVERGENCE",
                            description=f"Variable recipe allows both meat/swine and non-meat alternatives: {sig}",
                            evidence_a=ev,
                            evidence_b=ev,
                            severity="HIGH"
                        )
                    )
            elif "Variable recipe" in ev.detail:
                # Individual alternative within a variable choice
                if is_swine_item_or_tag(sig) or is_meat_item_or_tag(sig) or is_seafood_review_item_or_tag(sig):
                    recipe_all_plant_like = False
            else:
                # Mandatory direct ingredient
                if is_swine_item_or_tag(sig):
                    has_recipe_swine = True
                    recipe_all_plant_like = False
                elif is_meat_item_or_tag(sig):
                    has_recipe_meat = True
                    recipe_all_plant_like = False
                elif is_seafood_review_item_or_tag(sig):
                    has_recipe_seafood_review = True
                    recipe_all_plant_like = False
                elif is_fish_item_or_tag(sig):
                    has_recipe_fish = True
                    recipe_all_plant_like = False

        # Name flags
        name_swine = any(e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED) for e in name_evidence)
        name_meat = any(e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in MEAT_PROVENANCE or e.signal in AMBIGUOUS_MEAT) for e in name_evidence)
        name_plant = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in PLANT_HINTS for e in name_evidence)
        name_seafood_review = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in SEAFOOD_REVIEW for e in name_evidence)
        name_fish = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in FISH_HINTS for e in name_evidence)
        name_alcohol = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in ALCOHOL_REVIEW for e in name_evidence)
        name_exceptions = [e for e in name_evidence if e.kind == EvidenceKind.NAME_EXCEPTION]
        has_name_exception = len(name_exceptions) > 0
        untrusted_self = [e for e in name_evidence if e.kind == EvidenceKind.UNTRUSTED_SELF_DESCRIPTION]

        # Conflict Detection:
        # 1. Untrusted religious claim on swine or meat
        for u in untrusted_self:
            if has_recipe_swine or name_swine:
                swine_ev = next((e for e in all_evidence if e.kind in (EvidenceKind.RECIPE_ITEM, EvidenceKind.RECIPE_TAG, EvidenceKind.NAME_KEYWORD) and is_swine_item_or_tag(e.signal)), u)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="UNTRUSTED_RELIGIOUS_CLAIM",
                        description=f"Untrusted religious descriptor '{u.signal}' contradicts swine evidence '{swine_ev.signal}'",
                        evidence_a=u,
                        evidence_b=swine_ev,
                        severity="HIGH"
                    )
                )
            elif has_recipe_meat or name_meat:
                meat_ev = next((e for e in all_evidence if is_meat_item_or_tag(e.signal)), u)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="UNTRUSTED_RELIGIOUS_CLAIM",
                        description=f"Untrusted self-description '{u.signal}' on meat requiring provenance verification",
                        evidence_a=u,
                        evidence_b=meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 2. Plant name but Swine/Meat recipe
        if name_plant and not (name_swine or name_meat):
            if has_recipe_swine:
                recipe_swine_ev = next(e for e in recipe_evidence if is_swine_item_or_tag(e.signal))
                plant_ev = next(e for e in name_evidence if e.signal in PLANT_HINTS)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="PLANT_NAME_MEAT_RECIPE",
                        description=f"Innocent/plant name '{plant_ev.signal}' conceals swine ingredient '{recipe_swine_ev.signal}'",
                        evidence_a=plant_ev,
                        evidence_b=recipe_swine_ev,
                        severity="HIGH"
                    )
                )
            elif has_recipe_meat:
                recipe_meat_ev = next(e for e in recipe_evidence if is_meat_item_or_tag(e.signal))
                plant_ev = next(e for e in name_evidence if e.signal in PLANT_HINTS)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="PLANT_NAME_MEAT_RECIPE",
                        description=f"Innocent/plant name '{plant_ev.signal}' conceals meat ingredient '{recipe_meat_ev.signal}'",
                        evidence_a=plant_ev,
                        evidence_b=recipe_meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 3. Swine/Meat name with Name Exception (e.g. vegan_ham, mock_pork)
        if (name_swine or name_meat) and has_name_exception:
            for exc in name_exceptions:
                meat_ev = next((e for e in name_evidence if e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED or e.signal in MEAT_PROVENANCE or e.signal in AMBIGUOUS_MEAT)), exc)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="NAME_QUALIFIER_CONTRADICTION",
                        description=f"Name qualifier '{exc.signal}' directly contradicts meat base word '{meat_ev.signal}'",
                        evidence_a=exc,
                        evidence_b=meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 4. Swine name but recipe contains only plant/fungal ingredients
        if name_swine and recipes and recipe_all_plant_like:
            swine_name_ev = next(e for e in name_evidence if e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED)
            first_recipe_ev = recipe_evidence[0] if recipe_evidence else swine_name_ev
            conflicts.append(
                EvidenceConflict(
                    conflict_type="SWINE_NAME_PLANT_RECIPE",
                    description=f"Swine name token '{swine_name_ev.signal}' contradicted by plant/non-animal recipe composition",
                    evidence_a=swine_name_ev,
                    evidence_b=first_recipe_ev,
                    severity="HIGH"
                )
            )

        # Categorization and Priority
        # Hierarchy: Recipe > Tag > Name
        category: SuggestionCategory
        confidence: Confidence
        priority: int

        # 1. High risk swine
        if has_recipe_swine or has_tag_swine:
            category = SuggestionCategory.HIGH_RISK_RESTRICTED
            confidence = Confidence.HIGH
            priority = 100
        elif name_swine and not has_name_exception and not recipes:
            category = SuggestionCategory.HIGH_RISK_RESTRICTED
            confidence = Confidence.MEDIUM
            priority = 85
        # 2. Variable / Ambiguous recipe
        elif has_recipe_variable:
            category = SuggestionCategory.AMBIGUOUS_RECIPE
            confidence = Confidence.HIGH
            priority = 80
        # 3. Non-swine Meat Provenance
        elif has_recipe_meat or has_tag_meat:
            category = SuggestionCategory.MEAT_PROVENANCE_REQUIRED
            confidence = Confidence.HIGH
            priority = 70
        elif ((name_meat and not has_name_exception) or (has_name_exception and any(e.signal in MODIFIER_SPECIES for e in name_exceptions))) and not recipes:
            category = SuggestionCategory.MEAT_PROVENANCE_REQUIRED
            confidence = Confidence.MEDIUM
            priority = 70
        # 4. Seafood Review
        elif has_recipe_seafood_review or has_tag_seafood_review:
            category = SuggestionCategory.SEAFOOD_REVIEW
            confidence = Confidence.HIGH
            priority = 50
        elif name_seafood_review:
            category = SuggestionCategory.SEAFOOD_REVIEW
            confidence = Confidence.MEDIUM
            priority = 50
        # 5. Plant-based
        elif (recipe_all_plant_like or (name_plant and not recipes and not (name_swine or name_meat or name_seafood_review or name_alcohol))):
            category = SuggestionCategory.LIKELY_PLANT_BASED
            confidence = Confidence.HIGH if recipe_all_plant_like else Confidence.MEDIUM
            priority = 30
        # 6. Fish
        elif has_recipe_fish or has_tag_fish or (name_fish and not recipes):
            # Scaled fish is generally permissible, classified as plant-like / halal in MuslimQoL
            category = SuggestionCategory.LIKELY_PLANT_BASED
            confidence = Confidence.HIGH if (has_recipe_fish or has_tag_fish) else Confidence.MEDIUM
            priority = 30
        # 7. General Review / Conflicts / Alcohol
        elif name_alcohol or has_name_exception or conflicts or (name_meat and not recipes):
            category = SuggestionCategory.GENERAL_REVIEW
            confidence = Confidence.MEDIUM
            if name_alcohol:
                priority = 60
            elif conflicts:
                priority = 90
            elif has_name_exception:
                priority = 40
            else:
                priority = 65
        else:
            category = SuggestionCategory.NO_SIGNAL
            confidence = Confidence.LOW
            priority = 10

        # Adjust priority if conflicts exist
        if conflicts:
            high_sev = any(c.severity == "HIGH" for c in conflicts)
            if high_sev:
                priority = max(priority, 90)
            else:
                priority = max(priority, 75)

        # Check for curated classification if provided
        curated_status = None
        curated_reason = None
        curated_source = None
        if curated_info:
            curated_status = curated_info.get("status")
            curated_reason = curated_info.get("reason")
            curated_source = curated_info.get("source")
            all_evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.CURATED_PACK,
                    signal=f"{curated_status}:{curated_reason}",
                    source=curated_source or "curated_pack",
                    weight=1.0,
                    detail=f"Curated pack classification: status={curated_status}, reason={curated_reason}"
                )
            )

        recipe_summaries = [r.summary_text for r in recipes]
        suggestion = AuditSuggestion(
            category=category,
            confidence=confidence,
            evidence=tuple(all_evidence),
            conflicts=tuple(conflicts),
            review_priority=priority,
        )

        return ItemAuditResult(
            item_id=item_id,
            suggestion=suggestion,
            curated_status=curated_status,
            curated_reason=curated_reason,
            curated_source=curated_source,
            recipes=recipe_summaries,
            tags=sorted(item_tags),
            is_edible=True,
        )
