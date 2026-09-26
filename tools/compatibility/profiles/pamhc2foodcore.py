"""
Pam's HarvestCraft 2 - Food Core Compatibility Profile.

Supplies mod-specific contextual metadata and tag roots for Pam's HarvestCraft 2
Food Core (1.21.1-1.0.4) without polluting the generic audit engine.
"""

from typing import List, Set


MOD_ID = "pamhc2foodcore"
TARGET_VERSION = "1.21.1-1.0.4"
REFERENCE_JAR_SHA256 = "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
EXPECTED_TOTAL_ITEMS = 202
EXPECTED_EDIBLE_ITEMS = 180

# Pam's HarvestCraft 2 defines all food properties directly in Java bytecode
# without root food tags (c:foods). The audit engine's FoodProperties bytecode scanner
# discovers all 180 edible foods directly and accurately.
FOOD_TAG_ROOTS: List[str] = []
