"""
Tests for Compatibility Pack Validator.
"""

import json
import os
import shutil
import tempfile
import unittest

from tools.compatibility.audit.pack_validator import load_pack_classifications, validate_pack


class TestPackValidator(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_complete_match(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:pork", "status": "RESTRICTED", "reason": "swine"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:pork"]
        total_items = ["testmod:apple", "testmod:pork", "testmod:iron_sword"]
        report, _ = validate_pack(self.temp_dir, candidates, total_items)

        self.assertEqual(report.classified_count, 2)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)
        self.assertTrue(report.clean)
        self.assertEqual(report.status_distribution["HALAL"], 1)
        self.assertEqual(report.status_distribution["RESTRICTED"], 1)

    def test_missing_and_extra_items(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:extra_item", "status": "UNKNOWN", "reason": "extra"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:missing_banana"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertEqual(report.missing_items, ["testmod:missing_banana"])
        self.assertEqual(report.extra_items, ["testmod:extra_item"])
        self.assertFalse(report.clean)

    def test_duplicate_item_detection(self):
        pack_data_1 = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"}
            ]
        }
        pack_data_2 = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "duplicate_entry"}
            ]
        }
        with open(os.path.join(self.temp_dir, "file1.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data_1, f)
        with open(os.path.join(self.temp_dir, "file2.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data_2, f)

        candidates = ["testmod:apple"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertIn("testmod:apple", report.duplicate_items)
        self.assertFalse(report.clean)

    def test_unknown_item_id_detection(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:phantom_item", "status": "HALAL", "reason": "typo"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:phantom_item"]
        total_items = ["testmod:apple", "testmod:iron_sword"]  # phantom_item is not in mod registry
        report, _ = validate_pack(self.temp_dir, candidates, total_items)

        self.assertEqual(report.unknown_ids, ["testmod:phantom_item"])
        self.assertFalse(report.clean)
        self.assertTrue(any(d.error_type == "UNKNOWN_ITEM_ID" for d in report.diagnostics))

    def test_malformed_json_diagnostic(self):
        # Write corrupted JSON
        with open(os.path.join(self.temp_dir, "corrupted.json"), "w", encoding="utf-8") as f:
            f.write("{ invalid json")

        candidates = ["testmod:apple"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertFalse(report.clean)
        self.assertTrue(any(d.error_type == "PACK_PARSE_ERROR" for d in report.diagnostics))

    def test_pams_food_core_bundled_pack_validation(self):
        """Verifies the bundled Pam's Food Core compatibility pack against frozen reference manifest."""
        ref_path = "src/test/resources/reference/pamhc2foodcore-1.0.4-edible-items.json"
        pack_path = "src/main/resources/data/muslimqol_pamhc2foodcore"

        self.assertTrue(os.path.isfile(ref_path), "Pam's reference manifest must exist")
        self.assertTrue(os.path.isdir(pack_path), "Pam's compatibility pack directory must exist")

        with open(ref_path, "r", encoding="utf-8") as f:
            ref_data = json.load(f)

        candidates = sorted(ref_data["items"])
        self.assertEqual(len(candidates), 180)

        report, curated_map = validate_pack(pack_path, candidates, candidates)

        self.assertTrue(report.clean, f"Pam's pack must be clean, diagnostics: {report.diagnostics}")
        self.assertEqual(report.classified_count, 180)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)

        # Exact set equality
        self.assertEqual(set(curated_map.keys()), set(candidates))

        # Expected status distribution
        self.assertEqual(report.status_distribution["HALAL"], 125)
        self.assertEqual(report.status_distribution["RESTRICTED"], 13)
        self.assertEqual(report.status_distribution["DOUBTFUL"], 13)
        self.assertEqual(report.status_distribution["UNKNOWN"], 29)

    def test_farmers_delight_bundled_pack_validation(self):
        """Verifies the bundled Farmer's Delight compatibility pack against frozen reference manifest."""
        ref_path = "src/test/resources/reference/farmers-delight-1.3.4-edible-items.json"
        pack_path = "src/main/resources/data/muslimqol_farmersdelight"

        self.assertTrue(os.path.isfile(ref_path), "Farmer's Delight reference manifest must exist")
        self.assertTrue(os.path.isdir(pack_path), "Farmer's Delight compatibility pack directory must exist")

        with open(ref_path, "r", encoding="utf-8") as f:
            ref_data = json.load(f)

        candidates = sorted(ref_data["items"])
        self.assertEqual(len(candidates), 89)

        report, curated_map = validate_pack(pack_path, candidates, candidates)

        self.assertTrue(report.clean, f"Farmer's Delight pack must be clean, diagnostics: {report.diagnostics}")
        self.assertEqual(report.classified_count, 89)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)

        # Exact set equality
        self.assertEqual(set(curated_map.keys()), set(candidates))

        # Expected status distribution
        self.assertEqual(report.status_distribution["HALAL"], 52)
        self.assertEqual(report.status_distribution["RESTRICTED"], 11)
        self.assertEqual(report.status_distribution["DOUBTFUL"], 5)
        self.assertEqual(report.status_distribution["UNKNOWN"], 21)

    def test_pams_curation_invariants(self):
        """
        Verifies that human curation of Pam's Food Core matches provenance evidence:
        - mandatory_swine -> RESTRICTED / swine
        - variable_swine and not mandatory_swine -> DOUBTFUL / variable_provenance
        - mandatory livestock meat and not swine and not variable_swine -> UNKNOWN / unspecified_meat
        - curated RESTRICTED / swine -> mandatory_swine == True or known recipe-less pork item
        - curated plant_based -> no swine, meat, variable swine, or dairy/egg
        - curated fish -> mandatory_fish and no swine/variable swine
        - curated audited_permitted_recipe -> no swine, meat, or variable swine
        """
        ref_path = "src/test/resources/reference/pamhc2foodcore-1.0.4-curation-evidence.json"
        pack_path = "src/main/resources/data/muslimqol_pamhc2foodcore"

        self.assertTrue(os.path.isfile(ref_path), "Pam's curation evidence fixture must exist")
        with open(ref_path, "r", encoding="utf-8") as f:
            evidence_data = json.load(f)

        self.assertEqual(evidence_data["mod_id"], "pamhc2foodcore")
        self.assertEqual(evidence_data["version"], "1.0.4")
        self.assertEqual(evidence_data["jar_sha256"], "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9")
        self.assertEqual(evidence_data["max_depth"], 16)
        self.assertEqual(evidence_data["total_items"], 180)

        curated_map, duplicates, diagnostics = load_pack_classifications(pack_path)
        self.assertEqual(len(duplicates), 0)
        self.assertEqual(len(diagnostics), 0)
        self.assertEqual(len(curated_map), 180)

        evidence_items = {item["item"]: item for item in evidence_data["items"]}
        self.assertEqual(set(curated_map.keys()), set(evidence_items.keys()))

        for item_id, ev in evidence_items.items():
            cur = curated_map[item_id]

            # 1. Swine mandatory provenance implies hard RESTRICTED / swine
            if ev["mandatory_swine"]:
                self.assertEqual(
                    cur["status"], "RESTRICTED",
                    f"{item_id} has mandatory swine provenance, must be RESTRICTED"
                )
                self.assertEqual(
                    cur["reason"], "swine",
                    f"{item_id} has mandatory swine provenance, reason must be swine"
                )

            # 2. Variable swine provenance implies DOUBTFUL / variable_provenance
            if ev["variable_swine"] and not ev["mandatory_swine"]:
                self.assertEqual(
                    cur["status"], "DOUBTFUL",
                    f"{item_id} has variable swine provenance, must be DOUBTFUL"
                )
                self.assertEqual(
                    cur["reason"], "variable_provenance",
                    f"{item_id} has variable swine provenance, reason must be variable_provenance"
                )

            # 3. Mandatory livestock meat (non-swine, non-variable) implies UNKNOWN / unspecified_meat
            if ev["mandatory_meat"] and not ev["mandatory_swine"] and not ev["variable_swine"]:
                self.assertEqual(
                    cur["status"], "UNKNOWN",
                    f"{item_id} has mandatory livestock meat, must be UNKNOWN"
                )
                self.assertEqual(
                    cur["reason"], "unspecified_meat",
                    f"{item_id} has mandatory livestock meat, reason must be unspecified_meat"
                )

            # 4. Swine restriction must be supported by evidence or recognized recipe-less pork exception
            if cur["status"] == "RESTRICTED" or cur["reason"] == "swine":
                self.assertTrue(
                    ev["mandatory_swine"] or (item_id == "pamhc2foodcore:cookedgroundporkitem" and ev["audit_suggestion"] == "HIGH_RISK_RESTRICTED"),
                    f"{item_id} is curated as swine/RESTRICTED without mandatory swine provenance or known exception"
                )

            # 5. Pure plant foods must have no swine, meat, variable swine, or dairy/egg
            if cur["reason"] == "plant_based":
                self.assertEqual(cur["status"], "HALAL")
                self.assertFalse(ev["mandatory_swine"], f"{item_id} in plants.json has mandatory swine")
                self.assertFalse(ev["variable_swine"], f"{item_id} in plants.json has variable swine")
                self.assertFalse(ev["mandatory_meat"], f"{item_id} in plants.json has mandatory meat")
                self.assertFalse(ev["variable_meat"], f"{item_id} in plants.json has variable meat")
                self.assertFalse(ev["mandatory_dairy_egg"], f"{item_id} in plants.json has mandatory dairy/egg")

            # 6. Fish foods must have mandatory fish and no swine
            if cur["reason"] == "fish":
                self.assertEqual(cur["status"], "HALAL")
                self.assertTrue(ev["mandatory_fish"], f"{item_id} in fish.json must have fish provenance")
                self.assertFalse(ev["mandatory_swine"], f"{item_id} in fish.json has mandatory swine")
                self.assertFalse(ev["variable_swine"], f"{item_id} in fish.json has variable swine")

            # 7. Audited permitted recipes must have no swine or livestock meat
            if cur["reason"] == "audited_permitted_recipe":
                self.assertEqual(cur["status"], "HALAL")
                self.assertFalse(ev["mandatory_swine"], f"{item_id} in prepared_meals.json has mandatory swine")
                self.assertFalse(ev["variable_swine"], f"{item_id} in prepared_meals.json has variable swine")
                self.assertFalse(ev["mandatory_meat"], f"{item_id} in prepared_meals.json has mandatory meat")
                self.assertFalse(ev["variable_meat"], f"{item_id} in prepared_meals.json has variable meat")


if __name__ == "__main__":
    unittest.main()
