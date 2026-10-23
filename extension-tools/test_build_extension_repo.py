#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tests de `build_extension_repo.py` — exécutables sans Android.

    python3 -m unittest discover -s extension-tools -v
"""
from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_extension_repo as repo  # noqa: E402

BASE = "https://example.test/extensions/builds"


class BuilderTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.builds = self.root / "builds"
        self.builds.mkdir()
        self.out = self.root / "site" / "plugins.json"

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def add_artifact(self, name: str, payload: bytes, meta: dict) -> Path:
        artifact = self.builds / f"{name}.msa"
        artifact.write_bytes(payload)
        (self.builds / f"{name}.json").write_text(
            json.dumps(meta), encoding="utf-8"
        )
        return artifact

    def run_main(self, *extra: str) -> int:
        return repo.main(
            [
                "--builds-dir",
                str(self.builds),
                "--base-url",
                BASE,
                "--out",
                str(self.out),
                *extra,
            ]
        )


class NominalTests(BuilderTestCase):
    def test_publishes_hash_size_and_url(self) -> None:
        payload = b"dex-payload"
        self.add_artifact(
            "sample-plugin",
            payload,
            {"name": "Sample", "version": 2, "authors": ["MISSA"]},
        )

        self.assertEqual(0, self.run_main())

        entries = json.loads(self.out.read_text(encoding="utf-8"))
        self.assertEqual(1, len(entries))
        entry = entries[0]
        self.assertEqual("sample-plugin", entry["internalName"])
        self.assertEqual("Sample", entry["name"])
        self.assertEqual(2, entry["version"])
        self.assertEqual(repo.API_VERSION, entry["apiVersion"])
        self.assertEqual(len(payload), entry["fileSize"])
        self.assertEqual(
            "sha256-" + hashlib.sha256(payload).hexdigest(), entry["fileHash"]
        )
        self.assertEqual(f"{BASE}/sample-plugin.msa", entry["url"])

    def test_entries_are_sorted_by_internal_name(self) -> None:
        for name in ("zulu", "alpha", "mike"):
            self.add_artifact(name, name.encode(), {"name": name, "version": 1})

        self.assertEqual(0, self.run_main())

        entries = json.loads(self.out.read_text(encoding="utf-8"))
        self.assertEqual(
            ["alpha", "mike", "zulu"], [entry["internalName"] for entry in entries]
        )

    def test_empty_builds_dir_publishes_empty_list(self) -> None:
        self.assertEqual(0, self.run_main())
        self.assertEqual([], json.loads(self.out.read_text(encoding="utf-8")))

    def test_check_detects_up_to_date_file(self) -> None:
        self.add_artifact("sample", b"x", {"name": "Sample", "version": 1})
        self.assertEqual(0, self.run_main())
        self.assertEqual(0, self.run_main("--check"))

    def test_check_detects_stale_file(self) -> None:
        self.add_artifact("sample", b"x", {"name": "Sample", "version": 1})
        self.assertEqual(0, self.run_main())
        # Nouvel artefact non publié : --check doit échouer.
        self.add_artifact("second", b"y", {"name": "Second", "version": 1})
        self.assertEqual(1, self.run_main("--check"))

    def test_check_fails_when_output_missing(self) -> None:
        self.assertEqual(1, self.run_main("--check"))


class RejectionTests(BuilderTestCase):
    def test_missing_metadata_is_rejected(self) -> None:
        (self.builds / "orphan.msa").write_bytes(b"x")
        self.assertEqual(2, self.run_main())

    def test_http_base_url_is_rejected(self) -> None:
        self.add_artifact("sample", b"x", {"name": "Sample", "version": 1})
        code = repo.main(
            [
                "--builds-dir",
                str(self.builds),
                "--base-url",
                "http://example.test/builds",
                "--out",
                str(self.out),
            ]
        )
        self.assertEqual(2, code)

    def test_invalid_slug_is_rejected(self) -> None:
        self.add_artifact(
            "bad", b"x", {"name": "Bad", "version": 1, "internalName": "../evil"}
        )
        self.assertEqual(2, self.run_main())

    def test_version_must_be_positive_integer(self) -> None:
        self.add_artifact("v0", b"x", {"name": "V0", "version": 0})
        self.assertEqual(2, self.run_main())

    def test_api_version_must_match_app(self) -> None:
        self.add_artifact(
            "api2", b"x", {"name": "Api2", "version": 1, "apiVersion": 2}
        )
        self.assertEqual(2, self.run_main())

    def test_duplicate_internal_name_is_rejected(self) -> None:
        self.add_artifact("a", b"x", {"name": "A", "version": 1, "internalName": "same"})
        self.add_artifact("b", b"y", {"name": "B", "version": 1, "internalName": "same"})
        self.assertEqual(2, self.run_main())

    def test_status_out_of_range_is_rejected(self) -> None:
        self.add_artifact(
            "status-test", b"x", {"name": "S", "version": 1, "status": 9}
        )
        self.assertEqual(2, self.run_main())

    def test_declared_size_mismatch_is_rejected(self) -> None:
        self.add_artifact(
            "size", b"1234", {"name": "Size", "version": 1, "fileSize": 99}
        )
        self.assertEqual(2, self.run_main())


class ContractTests(BuilderTestCase):
    """Le JSON publié doit rester compatible avec `PluginEntry` (app)."""

    EXPECTED_KEYS = {
        "url",
        "name",
        "internalName",
        "version",
        "status",
        "apiVersion",
        "authors",
        "description",
        "iconUrl",
        "language",
        "tvTypes",
        "fileSize",
        "fileHash",
    }

    def test_published_entry_exposes_every_field_the_app_reads(self) -> None:
        self.add_artifact("sample", b"x", {"name": "Sample", "version": 1})
        self.assertEqual(0, self.run_main())
        entry = json.loads(self.out.read_text(encoding="utf-8"))[0]
        self.assertEqual(self.EXPECTED_KEYS, set(entry.keys()))


if __name__ == "__main__":
    unittest.main()
