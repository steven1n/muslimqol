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
    "meats",
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
    "hamburger",
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
    "berries",
    "melon",
    "potato",
    "beetroot",
    "mushroom",
    "pie",
    "bread",
    "cookie",
    "salad",
    "tea",
    "chamomile",
    "coffee",
    "seed",
    "seeds",
    "grain",
    "fruit",
    "vegetable",
    "vegetables",
    "corn",
    "bean",
    "beans",
    "pasta",
    "noodle",
    "noodles",
    "sugar",
    "cocoa",
    "chocolate",
    "pumpkin",
    "kelp",
    "tofu",
    "soy",
    "soybean",
    "soybeans",
    "garlic",
    "ginger",
    "lettuce",
    "cucumber",
    "eggplant",
    "pea",
    "peas",
    "pepper",
    "peppers",
    "radish",
    "spinach",
    "dough",
    "crust",
    "vanilla",
    "honey",
}

DAIRY_EGG_HINTS: Set[str] = {
    "milk",
    "egg",
    "eggs",
    "cheese",
    "cheesecake",
    "custard",
    "butter",
    "cream",
    "yogurt",
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

    # Decompose compound culinary tokens and strip redundant item suffix
    decomposed_tokens: List[str] = []
    meat_and_fish_signals = HIGH_RISK_SWINE | MEAT_PROVENANCE | FISH_HINTS | AMBIGUOUS_MEAT
    prefixes = ("cooked", "raw", "ground")
    meat_prefixes = ("pork", "beef", "chicken", "mutton", "rabbit", "fish", "bacon")

    for t in tokens:
        # Strip trailing item suffix if not an exact word 'item'
        if t.endswith("item") and len(t) > 5 and not t.endswith("_item"):
            t = t[:-4]

        # Check culinary prefixes (e.g. cookedpork, rawbeef, cookedgroundbeef)
        matched_prefix = False
        for p in prefixes:
            if t.startswith(p) and len(t) > len(p):
                rest = t[len(p):]
                if rest in meat_and_fish_signals:
                    decomposed_tokens.extend([p, rest])
                    matched_prefix = True
                    break
                for p2 in prefixes:
                    if rest.startswith(p2) and len(rest) > len(p2):
                        rest2 = rest[len(p2):]
                        if rest2 in meat_and_fish_signals:
                            decomposed_tokens.extend([p, p2, rest2])
                            matched_prefix = True
                            break
                if matched_prefix:
                    break

        if matched_prefix:
            continue

        # Check meat prefixes for un-delimited compounds (e.g. porknoodlesoup, beefnoodlesoup)
        matched_meat = False
        if t not in NAME_EXCEPTIONS and not t.endswith("less"):
            for mp in meat_prefixes:
                if t.startswith(mp) and len(t) > len(mp):
                    decomposed_tokens.extend([mp, t[len(mp):]])
                    matched_meat = True
                    break

        if not matched_meat:
            decomposed_tokens.append(t)

    return decomposed_tokens


def tokenize_identifier(identifier: str) -> List[str]:
    """
    Tokenizes any Minecraft identifier or tag (e.g. 'c:foods/raw_pork', 'farmersdelight:chamomile_tea').
    Splits by namespace, slashes, underscores, hyphens, and camelCase.
    """
    clean = identifier[1:] if identifier.startswith("#") else identifier
    if ":" in clean:
        _, path = clean.split(":", 1)
    else:
        path = clean

    subpaths = path.split("/")
    tokens: List[str] = []
    for sp in subpaths:
        tokens.extend(tokenize_registry_path(sp))
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

    # Scan for exceptions / qualifiers first
    for token in tokens:
        if token in NAME_EXCEPTIONS:
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
        elif token in DAIRY_EGG_HINTS:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.NAME_KEYWORD,
                    signal=token,
                    source=source,
                    weight=0.6,
                    detail=f"Dairy/egg keyword '{token}' (permissible low-risk baseline)"
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
                    detail=f"Fish keyword '{token}' (scaled fish review baseline)"
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
