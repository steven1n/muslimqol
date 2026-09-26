#!/usr/bin/env python3
"""
Wrapper entrypoint for the MuslimQoL Compatibility Audit Engine.
"""

import os
import sys

# Ensure project root is in sys.path
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from tools.compatibility.audit.cli import main

if __name__ == "__main__":
    sys.exit(main())
