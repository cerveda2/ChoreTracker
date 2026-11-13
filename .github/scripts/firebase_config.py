"""Provision variant-specific Firebase client configuration for CI or demo builds."""

import argparse
import json
import os
from pathlib import Path

CONFIGS = {
    "debug": ("FIREBASE_ANDROID_DEV_JSON", "choretracker-dev-1faf5", "cz.dcervenka.choretracker.debug"),
    "release": ("FIREBASE_ANDROID_PROD_JSON", "choretracker-fb576", "cz.dcervenka.choretracker"),
}


def demo_config(variant):
    return {
        "project_info": {"project_number": "123456789012", "project_id": "demo-choretracker-ci"},
        "client": [{
            "client_info": {
                "mobilesdk_app_id": "1:123456789012:android:000000000000000" + ("1" if variant == "debug" else "2"),
                "android_client_info": {"package_name": CONFIGS[variant][2]},
            },
            "api_key": [{"current_key": "AIzaSy" + "0" * 33}],
        }],
        "configuration_version": "1",
    }


def configuration(variant, payload, allow_demo):
    secret, project, package = CONFIGS[variant]
    if not payload:
        if not allow_demo:
            raise ValueError(f"{secret} is required; demo configuration is not enabled")
        return demo_config(variant)
    try:
        config = json.loads(payload)
        if config["project_info"]["project_id"] != project:
            raise ValueError(f"{secret} must target {project}")
        client = next((client for client in config["client"]
                       if client["client_info"]["android_client_info"]["package_name"] == package), None)
        if client is None:
            raise ValueError(f"{secret} has no Android client for {package}")
        if not client["client_info"]["mobilesdk_app_id"] or not client["api_key"][0]["current_key"]:
            raise ValueError(f"{secret} has an incomplete Android client")
    except json.JSONDecodeError:
        raise ValueError(f"{secret} is not valid JSON") from None
    except (KeyError, TypeError, IndexError):
        raise ValueError(f"{secret} is not a Firebase Android client configuration") from None
    return config


def provision(root, variants, allow_demo=False):
    if (root / "app/google-services.json").exists():
        raise ValueError("Move app/google-services.json to app/src/release/google-services.json first")
    pending = []
    for variant in variants:
        target = root / f"app/src/{variant}/google-services.json"
        if target.exists():
            raise ValueError(f"{target.relative_to(root)} already exists; preserve or explicitly remove your local copy")
        config = configuration(variant, os.environ.get(CONFIGS[variant][0], ""), allow_demo)
        pending.append((target, config))
    for target, config in pending:
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("x") as output:
            json.dump(config, output, indent=2)
            output.write("\n")
        source = "demo" if config["project_info"]["project_id"].startswith("demo-") else "configured"
        print(f"Wrote {source} Firebase configuration to {target.relative_to(root)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=CONFIGS, action="append")
    parser.add_argument("--allow-demo", action="store_true",
                        help="use synthetic demo configuration when a variant's secret is unavailable")
    args = parser.parse_args()
    try:
        provision(Path(__file__).resolve().parents[2], args.variant or CONFIGS, args.allow_demo)
    except ValueError as error:
        parser.exit(1, f"Firebase configuration error: {error}\n")


if __name__ == "__main__":
    main()
