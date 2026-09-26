"""
Unit tests for jar_reader bytecode inspection routines.
"""

import struct
import unittest

from tools.compatibility.audit.jar_reader import (
    extract_food_properties_fields,
    extract_strings_from_class_bytecode,
)


class TestJarReader(unittest.TestCase):

    def _build_synthetic_class(self, fields_spec):
        """
        Builds minimal valid JVM class bytes containing the specified fields.
        fields_spec is a list of (field_name, field_descriptor).
        """
        cp_bytes = bytearray()
        cp_count = 5 + len(fields_spec) * 2

        # 1: Utf8 'SyntheticClass'
        cp_bytes += struct.pack(">B", 1) + struct.pack(">H", 14) + b"SyntheticClass"
        # 2: Class -> 1
        cp_bytes += struct.pack(">BH", 7, 1)
        # 3: Utf8 'java/lang/Object'
        cp_bytes += struct.pack(">B", 1) + struct.pack(">H", 16) + b"java/lang/Object"
        # 4: Class -> 3
        cp_bytes += struct.pack(">BH", 7, 3)

        curr_cp_idx = 5
        field_entries = []
        for name, desc in fields_spec:
            name_bytes = name.encode("utf-8")
            desc_bytes = desc.encode("utf-8")
            name_idx = curr_cp_idx
            cp_bytes += struct.pack(">B", 1) + struct.pack(">H", len(name_bytes)) + name_bytes
            desc_idx = curr_cp_idx + 1
            cp_bytes += struct.pack(">B", 1) + struct.pack(">H", len(desc_bytes)) + desc_bytes
            field_entries.append((name_idx, desc_idx))
            curr_cp_idx += 2

        header = struct.pack(">IHHH", 0xCAFEBABE, 0, 65, cp_count)
        class_data = bytearray(header + cp_bytes)
        class_data += struct.pack(">HHHH", 0x0001, 2, 4, 0)  # access_flags, this, super, interfaces
        class_data += struct.pack(">H", len(field_entries))  # fields_count
        for n_idx, d_idx in field_entries:
            # access_flags: ACC_PUBLIC | ACC_STATIC (0x0009), name_idx, desc_idx, attr_count: 0
            class_data += struct.pack(">HHHH", 0x0009, n_idx, d_idx, 0)
        class_data += struct.pack(">H", 0)  # methods_count: 0
        class_data += struct.pack(">H", 0)  # attributes_count: 0

        return bytes(class_data)

    def test_extract_food_properties_single_field(self):
        class_bytes = self._build_synthetic_class([
            ("tasty_burger", "Lnet/minecraft/world/food/FoodProperties;"),
            ("some_count", "I"),
        ])
        food_fields = extract_food_properties_fields(class_bytes)
        self.assertEqual(food_fields, ["tasty_burger"])

    def test_extract_food_properties_multiple_fields(self):
        class_bytes = self._build_synthetic_class([
            ("apple_pie", "Lnet/minecraft/world/food/FoodProperties;"),
            ("item_registry", "Lnet/minecraft/core/Registry;"),
            ("beef_stew", "Lnet/minecraft/world/food/FoodProperties;"),
        ])
        food_fields = extract_food_properties_fields(class_bytes)
        self.assertEqual(food_fields, ["apple_pie", "beef_stew"])

    def test_extract_food_properties_no_matches(self):
        class_bytes = self._build_synthetic_class([
            ("tool_pickaxe", "Lnet/minecraft/world/item/Item;"),
            ("item_damage", "I"),
        ])
        food_fields = extract_food_properties_fields(class_bytes)
        self.assertEqual(food_fields, [])

    def test_extract_food_properties_invalid_bytes(self):
        self.assertEqual(extract_food_properties_fields(b""), [])
        self.assertEqual(extract_food_properties_fields(b"not_a_class_file"), [])
        self.assertEqual(extract_food_properties_fields(b"\xca\xfe\xba\xbe\x00\x00"), [])

    def test_extract_strings_from_bytecode(self):
        # Build synthetic class with a CONSTANT_String (tag 8)
        cp_bytes = bytearray()
        cp_count = 6
        # 1: Utf8 'SyntheticClass'
        cp_bytes += struct.pack(">B", 1) + struct.pack(">H", 14) + b"SyntheticClass"
        # 2: Class -> 1
        cp_bytes += struct.pack(">BH", 7, 1)
        # 3: Utf8 'java/lang/Object'
        cp_bytes += struct.pack(">B", 1) + struct.pack(">H", 16) + b"java/lang/Object"
        # 4: Class -> 3
        cp_bytes += struct.pack(">BH", 7, 3)
        # 5: Utf8 'custom_food_item'
        cp_bytes += struct.pack(">B", 1) + struct.pack(">H", 16) + b"custom_food_item"
        # 6: String (tag 8) -> 5
        cp_bytes += struct.pack(">BH", 8, 5)

        header = struct.pack(">IHHH", 0xCAFEBABE, 0, 65, 7)
        class_data = bytearray(header + cp_bytes)
        class_data += struct.pack(">HHHH", 0x0001, 2, 4, 0)
        class_data += struct.pack(">H", 0)  # fields_count
        class_data += struct.pack(">H", 0)  # methods_count
        class_data += struct.pack(">H", 0)  # attributes_count

        strings = extract_strings_from_class_bytecode(bytes(class_data))
        self.assertIn("custom_food_item", strings)
        self.assertIn("SyntheticClass", strings)
        self.assertIn("java/lang/Object", strings)


    def test_extract_food_properties_truncated_class(self):
        # A class header with magic 0xCAFEBABE claiming 5 cp entries, but truncated
        truncated = b"\xca\xfe\xba\xbe\x00\x00\x00\x41\x00\x05"
        with self.assertRaises(ValueError):
            extract_food_properties_fields(truncated)

    def test_food_properties_diagnostic_emitted_on_malformed_class(self):
        import os
        import tempfile
        import zipfile
        from tools.compatibility.audit.jar_reader import read_mod_jar

        with tempfile.NamedTemporaryFile(suffix=".jar", delete=False) as tmp:
            jar_path = tmp.name

        try:
            with zipfile.ZipFile(jar_path, "w") as z:
                z.writestr("assets/testmod/models/item/apple.json", "{}")
                # Class starting with 0xCAFEBABE but truncated
                z.writestr("testmod/CorruptClass.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x41\x00\x05")

            data = read_mod_jar(jar_path, "testmod")
            self.assertEqual(data.total_items, ["testmod:apple"])
            self.assertTrue(len(data.diagnostics) >= 1)
            diagnostic = next(d for d in data.diagnostics if d.error_type == "FOOD_PROPERTIES_BYTECODE_PARSE_ERROR")
            self.assertEqual(diagnostic.severity, "WARNING")
            self.assertEqual(diagnostic.source_path, "testmod/CorruptClass.class")
            self.assertIn("Truncated", diagnostic.message)
        finally:
            if os.path.exists(jar_path):
                os.remove(jar_path)


if __name__ == "__main__":
    unittest.main()
