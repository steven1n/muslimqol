"""
Registry ID Tokenizer and Name Heuristic Analyzer.

Extracts tokens from Minecraft item registry IDs and produces evidence facts
based on data-driven keyword groups, qualifier detection, and adversarial name handling.
"""

import re
from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import AuditEvidence, EvidenceKind


HIGH_RISK_SWINE: Set[str] = {
    "pork",
    "swine",
    "lard",
    "pig",
    "piglet",
    "hog",
    "boar",
}

SWINE_ASSOCIATED: Set[str] = {
    "bacon",
    "ham",
    "prosciutto",
    "pancetta",
    "gammon",
    "guanciale",
}

MEAT_PROVENANCE: Set[str] = {
    "beef",
    "chicken",
    "mutton",
    "lamb",
    "rabbit",
    "meat",
    "poultry",
    "veal",
    "steak",
    "venison",
    "duck",
    "goose",
    "turkey",
    "quail",
    "goat",
}

AMBIGUOUS_MEAT: Set[str] = {
    "sausage",
    "burger",
    "meatball",
    "patty",
    "kebab",
    "frankfurter",
    "hotdog",
    "salami",
    "pepperoni",
    "jerky",
    "nugget",
    "rib",
    "ribs",
    "cutlet",
    "roast",
    "broth",
}

PLANT_HINTS: Set[str] = {
    "apple",
    "carrot",
    "cabbage",
    "tomato",
    "onion",
    "rice",
    "wheat",
    "berry",
    "melon",
    "potato",
    "beetroot",
    "mushroom",
    "pie",
    "bread",
    "cookie",
    "salad",
    "tea",
    "coffee",
    "seed",
    "grain",
    "fruit",
    "vegetable",
    "corn",
    "bean",
    "pasta",
    "noodle",
    "sugar",
    "cocoa",
    "chocolate",
    "pumpkin",
    "kelp",
    "tofu",
    "garlic",
    "ginger",
    "lettuce",
    "cucumber",
    "eggplant",
    "pea",
    "pepper",
    "radish",
    "spinach",
    "dough",
    "crust",
    "cheesecake",
    "custard",
    "honey",
}

FISH_HINTS: Set[str] = {
    "fish",
    "cod",
    "salmon",
    "tuna",
    "trout",
    "herring",
    "sardine",
    "mackerel",
    "anchovy",
    "halibut",
    "bass",
}

SEAFOOD_REVIEW: Set[str] = {
    "squid",
    "octopus",
    "crab",
    "shrimp",
    "prawn",
    "lobster",
    "clam",
    "oyster",
    "mussel",
    "scallop",
    "crayfish",
    "calamari",
    "eel",
    "snail",
}

ALCOHOL_REVIEW: Set[str] = {
    "wine",
    "beer",
    "rum",
    "vodka",
    "whiskey",
    "whisky",
    "brandy",
    "liqueur",
    "cider",
    "ale",
    "mead",
    "sake",
    "champagne",
    "bourbon",
    "gin",
    "cocktail",
    "spirits",
}

NAME_EXCEPTIONS: Set[str] = {
    "vegan",
    "vegetarian",
    "veggie",
    "plant",
    "plant_based",
    "mock",
    "fake",
    "imitation",
    "porkless",
    "meatless",
    "nonalcoholic",
    "non_alcoholic",
}

MODIFIER_SPECIES: Set[str] = {
    "turkey",
    "chicken",
    "beef",
    "duck",
    "fish",
    "salmon",
    "tofu",
}

UNTRUSTED_SELF_DESCRIPTION: Set[str] = {
    "halal",
    "haram",
    "kosher",
    "permissible",
    "forbidden",
    "zabiha",
    "dhabihah",
}


def tokenize_registry_path(path: str) -> List[str]:
    """
    Tokenizes a registry ID path handling snake_case, kebab-case, and camelCase.
    Also handles compound tokens like non_alcoholic or plant_based.
    """
    # First convert camelCase to snake_case
    s1 = re.sub(r'(.)([A-Z][a-z]+)', r'\1_\2', path)
    s2 = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', s1).lower()
    
    # Replace hyphens and dots with underscores
    normalized = re.sub(r'[-.]+', '_', s2)
    
    # Check for compound phrases like non_alcoholic or plant_based
    compounds = {
        "non_alcoholic": "non_alcoholic",
        "plant_based": "plant_based",
    }
    for comp in compounds:
        if comp in normalized:
            # We can preserve it or let split handle it, but keep compound recognizable
            pass

    raw_tokens = [t for t in normalized.split('_') if t]
    
    # Re-stitch 'non' + 'alcoholic' into 'non_alcoholic' if adjacent
    tokens: List[str] = []
    i = 0
    while i < len(raw_tokens):
        if i + 1 < len(raw_tokens) and raw_tokens[i] == "non" and raw_tokens[i + 1] == "alcoholic":
            tokens.append("non_alcoholic")
            i += 2
        elif i + 1 < len(raw_tokens) and raw_tokens[i] == "plant" and raw_tokens[i + 1] == "based":
            tokens.append("plant_based")
            i += 2
        else:
            tokens.append(raw_tokens[i])
            i += 1
            
    return tokens


def analyze_registry_id(registry_id: str) -> Tuple[List[str], List[AuditEvidence]]:
    """
    Analyzes a full registry ID (`namespace:path`).
    Returns extracted tokens and list of AuditEvidence facts.
    """
    if ":" in registry_id:
        namespace, path = registry_id.split(":", 1)
    else:
        namespace, path = "minecraft", registry_id

    tokens = tokenize_registry_path(path)
    evidence: List[AuditEvidence] = []
    source = f"registry_id:{registry_id}"

    has_exception = False
    exception_tokens = []
    
    # Scan for exceptions / qualifiers first
    for token in tokens:
        if token in NAME_EXCEPTIONS:
            has_exception = True
            exception_tokens.append(token)
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_EXCEPTION,
                    signal=token,
                    source=source,
                    weight=0.9,
                    detail=f"Qualifying/exception token '{token}' indicates potential non-standard ingredient"
                )
            )

    # Scan for untrusted self-declarations
    for token in tokens:
        if token in UNTRUSTED_SELF_DESCRIPTION:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.UNTRUSTED_SELF_DESCRIPTION,
                    signal=token,
                    source=source,
                    weight=0.0,
                    detail=f"Self-declared token '{token}' cannot be trusted without recipe/ingredient verification"
                )
            )

    # Detect modifier species (e.g. turkey_bacon, chicken_sausage, beef_bacon)
    species_modifier = None
    for token in tokens:
        if token in MODIFIER_SPECIES:
            species_modifier = token
            break

    # Scan keyword groups
    for token in tokens:
        if token in HIGH_RISK_SWINE:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=1.0,
                    detail=f"High-risk swine keyword '{token}'"
                )
            )
        elif token in SWINE_ASSOCIATED:
            if species_modifier and species_modifier != token:
                evidence.append(
                    AuditEvidence(
                        kind=EvidenceKind.NAME_KEYWORD,
                        signal=token,
                        source=source,
                        weight=0.6,
                        detail=f"Swine-associated keyword '{token}', modified by species qualifier '{species_modifier}'"
                    )
                )
                evidence.append(
                    AuditEvidence(
                        kind=EvidenceKind.NAME_EXCEPTION,
                        signal=species_modifier,
                        source=source,
                        weight=0.8,
                        detail=f"Species modifier '{species_modifier}' qualifies base product '{token}'"
                    )
                )
            else:
                evidence.append(
                    AuditEvidence(
                        kind=EvidenceKind.NAME_KEYWORD,
                        signal=token,
                        source=source,
                        weight=0.9,
                        detail=f"Swine-associated keyword '{token}'"
                    )
                )
        elif token in MEAT_PROVENANCE:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.7,
                    detail=f"Meat provenance keyword '{token}' (requires halal slaughter / provenance verification)"
                )
            )
        elif token in AMBIGUOUS_MEAT:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.5,
                    detail=f"Ambiguous processed meat keyword '{token}' (requires ingredient verification)"
                )
            )
        elif token in PLANT_HINTS:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.6,
                    detail=f"Plant/crop keyword '{token}'"
                )
            )
        elif token in FISH_HINTS:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.8,
                    detail=f"Fish keyword '{token}' (generally permissible seafood signal)"
                )
            )
        elif token in SEAFOOD_REVIEW:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.7,
                    detail=f"Non-fish seafood keyword '{token}' (requires jurisprudence policy review)"
                )
            )
        elif token in ALCOHOL_REVIEW:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.7,
                    detail=f"Potential intoxicant-associated keyword '{token}' (requires culinary / fermentation review)"
                )
            )

    return tokens, evidence
