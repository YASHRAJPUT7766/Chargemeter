#!/usr/bin/env python3
"""
Lightweight static check, NOT a substitute for compiling in Android Studio.
Flags two common classes of error in the generated Compose files:
  1. Modifier extension functions (.padding, .height, .width, .size, etc.)
     used in the body but not imported from androidx.compose.foundation.layout
     (or the relevant package for that extension).
  2. Imported names that never appear again in the file body (possible
     dead imports) — with light false-positive suppression for Kotlin
     operator imports like getValue/setValue/provideDelegate that are
     used implicitly by `by` delegation syntax.
"""
import re
import sys
from pathlib import Path

# Common Modifier extension functions and the package they live in.
KNOWN_MODIFIER_EXTENSIONS = {
    "padding": "androidx.compose.foundation.layout.padding",
    "height": "androidx.compose.foundation.layout.height",
    "width": "androidx.compose.foundation.layout.width",
    "size": "androidx.compose.foundation.layout.size",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "wrapContentSize": "androidx.compose.foundation.layout.wrapContentSize",
    "clickable": "androidx.compose.foundation.clickable",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "offset": "androidx.compose.foundation.layout.offset",
    "weight": "androidx.compose.foundation.layout.weight (RowScope/ColumnScope)",
}

OPERATOR_IMPORT_ALLOWLIST = {"getValue", "setValue", "provideDelegate"}


def check_file(path: Path) -> list[str]:
    content = path.read_text()
    problems = []

    imports = re.findall(r"^import ([\w.]+)$", content, re.MULTILINE)
    imported_short_names = {i.split(".")[-1] for i in imports}
    # Also collect fully-qualified inline usages (androidx.foo.bar.Baz(...))
    # since those don't need a matching import line.
    fq_usages = set(re.findall(r"androidx\.[\w.]+\.(\w+)\(", content))

    # --- Check 1: Modifier.xxx( calls without a matching import ---
    for match in re.finditer(r"Modifier\s*\n?\s*\.(\w+)\(", content):
        ext_name = match.group(1)
        if ext_name in KNOWN_MODIFIER_EXTENSIONS and ext_name not in imported_short_names:
            # Not a problem if it's called fully-qualified elsewhere or via a fully-qualified Modifier chain
            if f".{ext_name}(" in content and ext_name not in fq_usages:
                problems.append(
                    f"  Modifier.{ext_name}(...) used but '{ext_name}' not imported "
                    f"(expected: {KNOWN_MODIFIER_EXTENSIONS[ext_name]})"
                )

    # --- Check 2: imported names that appear to never be used again ---
    for name in imported_short_names:
        if name in OPERATOR_IMPORT_ALLOWLIST:
            continue
        occurrences = len(re.findall(r"\b" + re.escape(name) + r"\b", content))
        if occurrences <= 1:
            problems.append(f"  Possibly unused import: {name}")

    return problems


def main():
    root = Path(__file__).parent / "app/src/main/java"
    kt_files = sorted(root.rglob("*.kt"))
    any_problems = False
    for f in kt_files:
        problems = check_file(f)
        if problems:
            any_problems = True
            print(f"\n{f.relative_to(root)}:")
            for p in problems:
                print(p)
    if not any_problems:
        print("No issues flagged by static check.")
    else:
        print(
            "\n(Note: this script has false positives — e.g. getValue/setValue "
            "used implicitly by `by` delegation, or names only used as type "
            "annotations. Review each flag rather than blindly acting on it.)"
        )


if __name__ == "__main__":
    main()
