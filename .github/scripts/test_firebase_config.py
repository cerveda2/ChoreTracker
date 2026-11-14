import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import firebase_config


def real_config(variant):
    config = firebase_config.demo_config(variant)
    config["project_info"]["project_id"] = firebase_config.CONFIGS[variant][1]
    return json.dumps(config)


class FirebaseConfigTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        environment = patch.dict(os.environ, {entry[0]: "" for entry in firebase_config.CONFIGS.values()})
        environment.start()
        self.addCleanup(environment.stop)

    def config(self, variant):
        return json.loads((self.root / f"app/src/{variant}/google-services.json").read_text())

    def test_untrusted_ci_gets_separate_demo_clients_for_both_variants(self):
        firebase_config.provision(self.root, firebase_config.CONFIGS, allow_demo=True)
        for variant, (_, _, package) in firebase_config.CONFIGS.items():
            config = self.config(variant)
            self.assertEqual(config["project_info"]["project_id"], "demo-choretracker-ci")
            self.assertEqual(config["client"][0]["client_info"]["android_client_info"]["package_name"], package)

    def test_trusted_ci_uses_the_supplied_configuration(self):
        for variant, (secret, _, _) in firebase_config.CONFIGS.items():
            os.environ[secret] = real_config(variant)
        firebase_config.provision(self.root, firebase_config.CONFIGS, allow_demo=True)
        for variant in firebase_config.CONFIGS:
            self.assertEqual(self.config(variant)["project_info"]["project_id"], firebase_config.CONFIGS[variant][1])

    def test_signed_release_requires_its_production_secret(self):
        with self.assertRaisesRegex(ValueError, "FIREBASE_ANDROID_PROD_JSON is required"):
            firebase_config.provision(self.root, ["release"])
        self.assertFalse((self.root / "app").exists())

    def test_signed_release_needs_only_production_configuration(self):
        os.environ["FIREBASE_ANDROID_PROD_JSON"] = real_config("release")
        firebase_config.provision(self.root, ["release"])
        self.assertEqual(self.config("release")["project_info"]["project_id"], "choretracker-fb576")
        self.assertFalse((self.root / "app/src/debug/google-services.json").exists())

    def test_configuration_cannot_cross_projects_or_packages(self):
        for variant in firebase_config.CONFIGS:
            other = "release" if variant == "debug" else "debug"
            with self.subTest(variant=variant), self.assertRaisesRegex(ValueError, "must target"):
                firebase_config.configuration(variant, real_config(other), allow_demo=True)
            config = json.loads(real_config(variant))
            config["client"][0]["client_info"]["android_client_info"]["package_name"] = "wrong.package"
            with self.assertRaisesRegex(ValueError, "no Android client"):
                firebase_config.configuration(variant, json.dumps(config), allow_demo=True)

    def test_malformed_or_server_credentials_fail_instead_of_falling_back_to_demo(self):
        for payload in ["not json", "null", "[]", '{}', '{"type":"service_account","private_key":"example"}']:
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                firebase_config.configuration("release", payload, allow_demo=True)

    def test_incomplete_client_is_rejected(self):
        config = json.loads(real_config("release"))
        config["client"][0]["api_key"] = []
        with self.assertRaises(ValueError):
            firebase_config.configuration("release", json.dumps(config), allow_demo=False)

    def test_bad_production_input_leaves_no_partial_debug_configuration(self):
        os.environ["FIREBASE_ANDROID_PROD_JSON"] = "invalid"
        with self.assertRaises(ValueError):
            firebase_config.provision(self.root, firebase_config.CONFIGS, allow_demo=True)
        self.assertFalse((self.root / "app").exists())

    def test_local_configuration_is_never_overwritten(self):
        target = self.root / "app/src/debug/google-services.json"
        target.parent.mkdir(parents=True)
        target.write_text("local configuration")
        with self.assertRaisesRegex(ValueError, "already exists"):
            firebase_config.provision(self.root, firebase_config.CONFIGS, allow_demo=True)
        self.assertEqual(target.read_text(), "local configuration")
        self.assertFalse((self.root / "app/src/release/google-services.json").exists())

    def test_old_root_configuration_cannot_silently_override_variant_provisioning(self):
        target = self.root / "app/google-services.json"
        target.parent.mkdir(parents=True)
        target.write_text("local production configuration")
        with self.assertRaisesRegex(ValueError, "Move app/google-services.json"):
            firebase_config.provision(self.root, firebase_config.CONFIGS, allow_demo=True)


if __name__ == "__main__":
    unittest.main()
