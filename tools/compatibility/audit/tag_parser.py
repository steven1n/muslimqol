"""
Minecraft and NeoForge Tag Parser and Resolver.

Loads item tags from data pack hierarchies, resolves tag inclusions (#tag),
and maps items to tags and tags to items using token-safe semantic matching.
"""

from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.name_heuristics import (
    AMBIGUOUS_MEAT,
    DAIRY_EGG_HINTS,
    FISH_HINTS,
    HIGH_RISK_SWINE,
    MEAT_PROVENANCE,
    PLANT_HINTS,
    SEAFOOD_REVIEW,
    SWINE_ASSOCIATED,
    tokenize_identifier,
)


FEED_ANIMAL_TOKENS = {
    "pig", "rabbit", "chicken", "parrot", "cat", "ocelot", "wolf", "strider",
    "fox", "cow", "sheep", "horse", "dog"
}


class TagRegistry:
    """Stores and resolves tags across namespaces."""

    def __init__(self, raw_tags: Optional[Dict[str, dict]] = None):
        """
        raw_tags: Dict mapping tag_id (e.g. 'c:foods/raw_pork') to parsed JSON dict.
        """
        self.raw_tags: Dict[str, dict] = raw_tags or {}
        self._resolved_cache: Dict[str, Set[str]] = {}
        self._item_to_tags: Dict[str, Set[str]] = {}

    def add_tag(self, tag_id: str, tag_data: dict) -> None:
        self.raw_tags[tag_id] = tag_data
        self._resolved_cache.clear()
        self._item_to_tags.clear()

    def resolve_tag(self, tag_id: str, visited: Optional[Set[str]] = None) -> Set[str]:
        """Recursively resolves all item IDs contained in tag_id."""
        clean_tag = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean_tag in self._resolved_cache:
            return self._resolved_cache[clean_tag]

        if visited is None:
            visited = set()
        if clean_tag in visited:
            return set()
        visited.add(clean_tag)

        tag_data = self.raw_tags.get(clean_tag)
        if not tag_data:
            return set()

        items: Set[str] = set()
        values = tag_data.get("values", [])
        for entry in values:
            if isinstance(entry, dict):
                entry_id = entry.get("id", "")
            else:
                entry_id = str(entry)

            if entry_id.startswith("#"):
                sub_tag = entry_id[1:]
                items.update(self.resolve_tag(sub_tag, visited))
            elif entry_id:
                items.add(entry_id)

        self._resolved_cache[clean_tag] = items
        return items

    def get_tags_for_item(self, item_id: str) -> Set[str]:
        """Returns all tag IDs containing item_id."""
        if not self._item_to_tags:
            for tag_id in self.raw_tags:
                contained = self.resolve_tag(tag_id)
                for item in contained:
                    self._item_to_tags.setdefault(item, set()).add(tag_id)

        return self._item_to_tags.get(item_id, set())

    def resolve_tag_branches(
        self,
        tag_id: str,
        visited: Optional[Set[str]] = None,
    ) -> List[Tuple[str, List[str]]]:
        """
        Recursively resolves all leaf items or terminal tags within tag_id,
        preserving the lineage path of nested tags.
        Returns: List of (leaf_identifier, lineage_path).
        """
        clean_tag = tag_id[1:] if tag_id.startswith("#") else tag_id
        if visited is None:
            visited = set()
        if clean_tag in visited:
            return []
        visited.add(clean_tag)

        tag_data = self.raw_tags.get(clean_tag)
        results: List[Tuple[str, List[str]]] = []

        if tag_data and isinstance(tag_data, dict):
            values = tag_data.get("values", [])
            for entry in values:
                if isinstance(entry, dict):
                    entry_id = entry.get("id", "")
                else:
                    entry_id = str(entry)

                if entry_id.startswith("#"):
                    sub_tag = entry_id[1:]
                    sub_results = self.resolve_tag_branches(sub_tag, set(visited))
                    for leaf, path in sub_results:
                        results.append((leaf, [f"#{clean_tag}"] + path))
                elif entry_id:
                    results.append((entry_id, [f"#{clean_tag}", entry_id]))
        else:
            resolved = self.resolve_tag(clean_tag)
            if resolved:
                for item in sorted(resolved):
                    results.append((item, [f"#{clean_tag}", item]))
            else:
                results.append((f"#{clean_tag}", [f"#{clean_tag}"]))

        return results

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

    def is_swine_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean in self.ANIMAL_FEED_TAGS:
            return False
        tokens = set(tokenize_identifier(clean))
        if "food" in tokens and (tokens & FEED_ANIMAL_TOKENS):
            return False
        return bool(tokens & HIGH_RISK_SWINE or tokens & SWINE_ASSOCIATED)

    def is_meat_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean in self.ANIMAL_FEED_TAGS or clean == "origins:meat":
            return False
        if self.is_fish_signal(tag_id):
            return False
        tokens = set(tokenize_identifier(clean))
        if "food" in tokens and (tokens & FEED_ANIMAL_TOKENS):
            return False
        return bool(tokens & MEAT_PROVENANCE or tokens & AMBIGUOUS_MEAT)

    def is_fish_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean in self.ANIMAL_FEED_TAGS:
            return False
        tokens = set(tokenize_identifier(clean))
        return bool(tokens & FISH_HINTS)

    def is_seafood_review_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        tokens = set(tokenize_identifier(clean))
        return bool(tokens & SEAFOOD_REVIEW or "ink_sac" in tokens)

    def is_dairy_egg_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        tokens = set(tokenize_identifier(clean))
        return bool(tokens & DAIRY_EGG_HINTS)
