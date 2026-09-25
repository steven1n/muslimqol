"""
Minecraft and NeoForge Tag Parser and Resolver.

Loads item tags from data pack hierarchies, resolves tag inclusions (#tag),
and maps items to tags and tags to items.
"""

from typing import Dict, List, Optional, Set, Tuple


SWINE_TAG_PATTERNS = {
    "c:foods/raw_pork",
    "c:foods/cooked_pork",
    "c:foods/raw_bacon",
    "c:foods/cooked_bacon",
    "c:foods/pork",
    "c:foods/bacon",
}

MEAT_TAG_PATTERNS = {
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

FISH_TAG_PATTERNS = {
    "c:foods/safe_raw_fish",
    "c:foods/safe_cooked_fish",
    "c:foods/raw_fish",
    "c:foods/cooked_fish",
    "c:foods/raw_cod",
    "c:foods/cooked_cod",
    "c:foods/raw_salmon",
    "c:foods/cooked_salmon",
    "minecraft:fishes",
}

PLANT_TAG_PATTERNS = {
    "c:foods/vegetable",
    "c:foods/leafy_green",
    "c:foods/dough",
    "c:foods/pasta",
    "c:crops",
    "c:fruits",
    "c:vegetables",
    "c:berries",
    "c:mushrooms",
    "c:grain",
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
        # Strip leading '#' if present
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
                # May contain 'id' and 'required'
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
            # Build inverse index
            for tag_id in self.raw_tags:
                contained = self.resolve_tag(tag_id)
                for item in contained:
                    self._item_to_tags.setdefault(item, set()).add(tag_id)

        return self._item_to_tags.get(item_id, set())

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
        if clean in self.ANIMAL_FEED_TAGS or clean.endswith("_food"):
            return False
        return any(swine in clean for swine in ("pork", "bacon", "ham", "lard", "swine"))

    def is_meat_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean in self.ANIMAL_FEED_TAGS or clean.endswith("_food") or clean == "origins:meat":
            return False
        return any(meat in clean for meat in ("beef", "chicken", "mutton", "lamb", "rabbit", "meat"))

    def is_fish_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        return any(fish in clean for fish in ("fish", "cod", "salmon", "tuna"))

    def is_seafood_review_signal(self, tag_id: str) -> bool:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        return any(sf in clean for sf in ("squid", "octopus", "crab", "shrimp", "prawn", "lobster", "clam"))
