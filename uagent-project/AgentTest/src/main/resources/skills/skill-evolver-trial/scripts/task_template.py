#!/usr/bin/env python3
"""
Trial task template — replace TASK_DESCRIPTION and implement solve().

Usage:
    python task_template.py

Exit code 0 = success, non-zero = failure.
"""
import sys
import json
import os

# === Trial context (injected by TrialRunnerAgent) ===
WORKSPACE = os.environ.get("TRIAL_WORKSPACE", ".")
OUTPUT_FILE = os.environ.get("TRIAL_OUTPUT", "output.json")


def solve():
    """Implement your task here. Return the result."""
    raise NotImplementedError("Replace with actual task logic")


def verify(result):
    """Verify the result meets expectations. Return True/False."""
    raise NotImplementedError("Replace with actual verification logic")


def main():
    try:
        result = solve()
        passed = verify(result)

        # Write structured output for verifier
        output = {
            "result": result,
            "passed": passed,
        }
        with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2)

        print(json.dumps(output, ensure_ascii=False, indent=2))
        sys.exit(0 if passed else 1)

    except Exception as e:
        error_output = {
            "error": str(e),
            "passed": False,
        }
        with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
            json.dump(error_output, f, ensure_ascii=False, indent=2)

        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
