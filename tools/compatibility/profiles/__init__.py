"""
Mod-specific profiles package.
"""

from typing import Any, Dict, Optional
import importlib

def get_profile(mod_id: str) -> Optional[Any]:
    """Dynamically loads profile module if one exists for the given mod_id."""
    module_name = f"tools.compatibility.profiles.{mod_id}"
    try:
        return importlib.import_module(module_name)
    except ModuleNotFoundError:
        return None
