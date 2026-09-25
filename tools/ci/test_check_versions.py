import pathlib
import tempfile
import unittest

import check_versions


def catalog(folder: pathlib.Path, name: str, body: str) -> pathlib.Path:
    path = folder / f"{name}.toml"
    path.write_text("[versions]\n" + body + "\n[libraries]\n")
    return path


class CheckVersionsTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())

    def test_agreeing_catalogs_pass(self):
        a = catalog(self.dir, "a", 'kotlin = "2.4.10"\nagp = "9.0.1"\nktor = "3.5.2"')
        b = catalog(self.dir, "b", 'kotlin = "2.4.10"\nagp = "9.0.1"')
        self.assertEqual(check_versions.check({"a": a, "b": b}), [])

    def test_a_different_android_plugin_is_named(self):
        a = catalog(self.dir, "a", 'kotlin = "2.4.10"\nagp = "9.0.1"')
        b = catalog(self.dir, "b", 'kotlin = "2.4.10"\nagp = "9.1.1"')
        self.assertEqual(check_versions.check({"a": a, "b": b}), ["agp: a 9.0.1, b 9.1.1"])

    def test_a_catalog_without_kotlin_is_named(self):
        a = catalog(self.dir, "a", 'kotlin = "2.4.10"\nagp = "9.0.1"')
        b = catalog(self.dir, "b", 'agp = "9.0.1"')
        self.assertEqual(check_versions.check({"a": a, "b": b}), ["kotlin: not declared in b"])

    def test_the_repository_agrees(self):
        self.assertEqual(check_versions.check(check_versions.CATALOGS), [])


if __name__ == "__main__":
    unittest.main()
