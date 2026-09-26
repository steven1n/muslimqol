"""
Single-Pass Mod JAR Reader and Metadata Extractor.

Decompresses JAR entries once into memory, caching tags, recipes, item models,
and bytecode registry constants for deterministic, offline analysis.
"""

from dataclasses import dataclass, field
import hashlib
import json
import os
import re
import struct
from typing import Any, Dict, List, Optional, Set, Tuple
import zipfile

from tools.compatibility.audit.models import ParseDiagnostic
from tools.compatibility.audit.recipe_parser import ParsedRecipe, parse_recipe_json
from tools.compatibility.audit.tag_parser import TagRegistry
from tools.compatibility.profiles import get_profile


@dataclass
class JarData:
    """In-memory cached data extracted from a mod JAR."""
    jar_path: str
    sha256: str
    mod_id: str
    total_items: List[str]
    edible_candidates: List[str]
    tag_registry: TagRegistry
    recipes_by_output: Dict[str, List[ParsedRecipe]]
    all_recipes: List[ParsedRecipe]
    diagnostics: List[ParseDiagnostic] = field(default_factory=list)


def compute_file_sha256(path: str) -> str:
    """Computes SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def extract_strings_from_class_bytecode(class_bytes: bytes) -> List[str]:
    """
    Extracts UTF-8 string constants from Java class bytecode constant pool.
    Standard-library only, avoids external tools like javap.
    """
    if len(class_bytes) < 10:
        return []
    magic, minor, major, cp_count = struct.unpack(">IHHH", class_bytes[:10])
    if magic != 0xCAFEBABE:
        return []

    idx = 10
    cp: List[Any] = [None] * cp_count
    i = 1
    while i < cp_count:
        if idx >= len(class_bytes):
            break
        tag = class_bytes[idx]
        idx += 1
        if tag == 1:  # Utf8
            if idx + 2 > len(class_bytes):
                break
            length, = struct.unpack(">H", class_bytes[idx:idx+2])
            idx += 2
            val = class_bytes[idx:idx+length].decode("utf-8", errors="replace")
            idx += length
            cp[i] = val
        elif tag in (3, 4):  # Integer, Float
            idx += 4
        elif tag in (5, 6):  # Long, Double
            idx += 8
            i += 1  # 8-byte entries take 2 slots
        elif tag in (7, 8):  # Class, String
            if idx + 2 > len(class_bytes):
                break
            string_index, = struct.unpack(">H", class_bytes[idx:idx+2])
            idx += 2
            cp[i] = ("ref", string_index)
        elif tag in (9, 10, 11, 12):  # Fieldref, Methodref, InterfaceMethodref, NameAndType
            idx += 4
        elif tag in (15, 16, 19, 20):  # MethodHandle, MethodType, etc.
            idx += 3 if tag == 15 else 2
        elif tag in (17, 18):  # Dynamic, InvokeDynamic
            idx += 4
        else:
            break
        i += 1

    strings: List[str] = []
    for entry in cp:
        if isinstance(entry, tuple) and entry[0] == "ref" and entry[1] < len(cp):
            target = cp[entry[1]]
            if isinstance(target, str):
                strings.append(target)
    return strings


def read_mod_jar(jar_path: str, mod_id: str) -> JarData:
    """
    Reads a mod JAR in a single pass and returns cached JarData.
    """
    if not os.path.isfile(jar_path):
        raise FileNotFoundError(f"Mod JAR file not found at: {jar_path}")

    jar_sha256 = compute_file_sha256(jar_path)
    profile = get_profile(mod_id)

    raw_tags: Dict[str, dict] = {}
    all_recipes: List[ParsedRecipe] = []
    recipes_by_output: Dict[str, List[ParsedRecipe]] = {}
    model_items: Set[str] = set()
    diagnostics: List[ParseDiagnostic] = []

    with zipfile.ZipFile(jar_path) as z:
        names = z.namelist()

        # 1. Parse item tags
        for name in names:
            if "/tags/item/" in name and name.endswith(".json"):
                parts = name.split("/tags/item/")
                domain = parts[0].split("/")[-1]
                path = parts[1][:-5]
                tag_id = f"{domain}:{path}"
                try:
                    raw_tags[tag_id] = json.loads(z.read(name).decode("utf-8"))
                except Exception as e:
                    diagnostics.append(
                        ParseDiagnostic(
                            source_path=name,
                            error_type="JSON_DECODE_ERROR",
                            message=f"Failed to decode tag JSON: {e}",
                            severity="ERROR"
                        )
                    )

        # 2. Parse recipes (exclude /advancement/ criteria files)
        for name in names:
            if not name.endswith(".json") or "/advancement/" in name:
                continue
            parts = name.split("/")
            if len(parts) >= 4 and parts[0] == "data" and parts[2] in ("recipe", "recipes"):
                try:
                    data = json.loads(z.read(name).decode("utf-8"))
                except Exception as e:
                    diagnostics.append(
                        ParseDiagnostic(
                            source_path=name,
                            error_type="JSON_DECODE_ERROR",
                            message=f"Failed to decode recipe JSON: {e}",
                            severity="ERROR"
                        )
                    )
                    continue

                try:
                    recipe = parse_recipe_json(name, data, default_namespace=mod_id, diagnostics=diagnostics)
                    if recipe:
                        all_recipes.append(recipe)
                        for out_id in recipe.output_items:
                            recipes_by_output.setdefault(out_id, []).append(recipe)
                except Exception as e:
                    diagnostics.append(
                        ParseDiagnostic(
                            source_path=name,
                            error_type="RECIPE_PARSE_ERROR",
                            message=f"Failed to parse recipe data: {e}",
                            severity="ERROR"
                        )
                    )

        # 3. Discover item models
        model_prefix = f"assets/{mod_id}/models/item/"
        for name in names:
            if name.startswith(model_prefix) and name.endswith(".json"):
                base_name = os.path.splitext(os.path.basename(name))[0]
                model_items.add(f"{mod_id}:{base_name}")

        # 4. Extract registered items from bytecode class files
        registry_class_candidates = [
            n for n in names
            if n.endswith(".class") and "$" not in n and "Tag" not in n and ("registry" in n or "init" in n or "item" in n) and ("Items" in n or "Item" in n)
        ]

        class_items: Set[str] = set()
        for cls_name in registry_class_candidates:
            try:
                class_bytes = z.read(cls_name)
                found_strings = extract_strings_from_class_bytecode(class_bytes)
                items_in_class = {
                    s for s in found_strings
                    if re.match(r"^[a-z0-9_]+$", s) and s not in (mod_id, "minecraft", "c", "id", "values", "tag", "properties", "food", "foodproperties")
                }
                overlap = items_in_class & {m.split(":", 1)[1] for m in model_items}
                if len(overlap) >= 20:
                    class_items = {f"{mod_id}:{s}" for s in items_in_class if f"{mod_id}:{s}" in model_items or not model_items}
                    break
            except Exception as e:
                diagnostics.append(
                    ParseDiagnostic(
                        source_path=cls_name,
                        error_type="BYTECODE_READ_ERROR",
                        message=f"Failed to read bytecode: {e}",
                        severity="WARNING"
                    )
                )

        if class_items:
            discovered_items = class_items
        else:
            discovered_items = model_items

    tag_registry = TagRegistry(raw_tags)

    # 5. Discover edible candidates
    food_tag_candidates: List[str] = [
        t for t in tag_registry.raw_tags
        if t.startswith("c:foods") or t.startswith("c:drinks")
        or t.startswith(f"{mod_id}:meals") or t.startswith(f"{mod_id}:snacks")
        or t.startswith(f"{mod_id}:sweets") or t.startswith(f"{mod_id}:feasts")
        or t.startswith(f"{mod_id}:drinks")
    ]
    if profile and hasattr(profile, "FOOD_TAG_ROOTS"):
        for extra in profile.FOOD_TAG_ROOTS:
            if extra not in food_tag_candidates:
                food_tag_candidates.append(extra)

    edible_candidates: Set[str] = set()
    for root_tag in food_tag_candidates:
        resolved = tag_registry.resolve_tag(root_tag)
        for itm in resolved:
            if itm.startswith(f"{mod_id}:"):
                edible_candidates.add(itm)

    if not edible_candidates:
        for out_id, recs in recipes_by_output.items():
            if out_id.startswith(f"{mod_id}:"):
                for r in recs:
                    if any("food" in ev.signal for ev in r.evidence):
                        edible_candidates.add(out_id)

    return JarData(
        jar_path=jar_path,
        sha256=jar_sha256,
        mod_id=mod_id,
        total_items=sorted(discovered_items),
        edible_candidates=sorted(edible_candidates),
        tag_registry=tag_registry,
        recipes_by_output=recipes_by_output,
        all_recipes=all_recipes,
        diagnostics=diagnostics,
    )
