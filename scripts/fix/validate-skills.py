#!/usr/bin/env python3
"""
Validate skills against Agent Skills specification (https://agentskills.io/specification)

This validator checks:
- SKILL.md exists and has valid YAML frontmatter
- name field: lowercase, hyphens only, matches directory name, no leading/trailing hyphens
- description field: 1-1024 characters, non-empty
- metadata field structure (optional)
- license, compatibility, allowed-tools fields (optional)
- Body content (minimal validation)

Exit codes:
  0 - All skills valid
  1 - Validation errors found
  2 - Invalid usage
"""

import argparse
import re
import sys
from pathlib import Path
from typing import List, Tuple, Optional
import yaml


class SkillValidator:
    """Validator for Agent Skills specification compliance."""

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.errors: List[str] = []
        self.warnings: List[str] = []
        self.valid_skills = 0

    def error(self, skill_name: str, message: str) -> None:
        """Record an error."""
        self.errors.append(f"[{skill_name}] {message}")
        if self.verbose:
            print(f"❌ ERROR: {skill_name}: {message}")

    def warning(self, skill_name: str, message: str) -> None:
        """Record a warning."""
        self.warnings.append(f"[{skill_name}] {message}")
        if self.verbose:
            print(f"⚠️  WARNING: {skill_name}: {message}")

    def success(self, skill_name: str, message: str) -> None:
        """Print success message."""
        if self.verbose:
            print(f"✓ {skill_name}: {message}")

    def _extract_frontmatter(self, skill_path: Path) -> Optional[dict]:
        """Extract and parse YAML frontmatter from SKILL.md."""
        try:
            with open(skill_path / "SKILL.md", "r") as f:
                content = f.read()

            # Check for YAML frontmatter delimiters
            if not content.startswith("---"):
                self.error(skill_path.name, "SKILL.md must start with --- (YAML frontmatter)")
                return None

            # Find closing ---
            lines = content.split("\n")
            closing_idx = None
            for i in range(1, len(lines)):
                if lines[i].strip() == "---":
                    closing_idx = i
                    break

            if closing_idx is None:
                self.error(skill_path.name, "No closing --- found for YAML frontmatter")
                return None

            # Parse frontmatter
            frontmatter_str = "\n".join(lines[1:closing_idx])
            try:
                frontmatter = yaml.safe_load(frontmatter_str)
                if not isinstance(frontmatter, dict):
                    frontmatter = {}
            except yaml.YAMLError as e:
                self.error(skill_path.name, f"Invalid YAML frontmatter: {e}")
                return None

            return frontmatter

        except FileNotFoundError:
            self.error(skill_path.name, "SKILL.md not found")
            return None
        except Exception as e:
            self.error(skill_path.name, f"Failed to read SKILL.md: {e}")
            return None

    def _validate_name(self, skill_name: str, name_value: str) -> bool:
        """Validate 'name' field."""
        # Check length
        if len(name_value) > 64:
            self.error(skill_name, f"'name' exceeds 64 characters ({len(name_value)})")
            return False

        # Check format: lowercase alphanumeric and hyphens only
        if not re.match(r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?$", name_value):
            self.error(
                skill_name,
                "'name' contains invalid characters. Must be lowercase letters, numbers, and hyphens only"
            )
            return False

        # Check for leading/trailing hyphens
        if name_value.startswith("-"):
            self.error(skill_name, "'name' cannot start with a hyphen")
            return False

        if name_value.endswith("-"):
            self.error(skill_name, "'name' cannot end with a hyphen")
            return False

        # Check for consecutive hyphens
        if "--" in name_value:
            self.error(skill_name, "'name' cannot contain consecutive hyphens")
            return False

        # Check matches directory name
        if name_value != skill_name:
            self.error(skill_name, f"'name' ({name_value}) must match directory name ({skill_name})")
            return False

        self.success(skill_name, f"'name' field is valid ({name_value})")
        return True

    def _validate_description(self, skill_name: str, description: str) -> bool:
        """Validate 'description' field."""
        desc_len = len(description)

        if desc_len < 1 or desc_len > 1024:
            self.error(skill_name, f"'description' must be 1-1024 characters (got {desc_len})")
            return False

        self.success(skill_name, f"'description' field is valid ({desc_len} chars)")
        return True

    def _validate_compatibility(self, skill_name: str, compatibility: str) -> bool:
        """Validate optional 'compatibility' field."""
        compat_len = len(compatibility)

        if compat_len > 500:
            self.warning(skill_name, f"'compatibility' exceeds 500 characters ({compat_len})")
            return False

        self.success(skill_name, "'compatibility' field valid")
        return True

    def validate_skill(self, skill_path: Path) -> bool:
        """Validate a single skill directory."""
        skill_name = skill_path.name

        print(f"\n{'━' * 80}")
        print(f"Validating: {skill_name}")
        print(f"{'━' * 80}")

        # Check if SKILL.md exists
        if not (skill_path / "SKILL.md").exists():
            self.error(skill_name, "SKILL.md not found")
            return False

        self.success(skill_name, "SKILL.md exists")

        # Extract frontmatter
        frontmatter = self._extract_frontmatter(skill_path)
        if frontmatter is None:
            return False

        self.success(skill_name, "Valid YAML frontmatter structure")

        # Validate required fields
        valid = True

        # Validate 'name'
        if "name" not in frontmatter:
            self.error(skill_name, "'name' field is required")
            valid = False
        else:
            if not self._validate_name(skill_name, frontmatter["name"]):
                valid = False

        # Validate 'description'
        if "description" not in frontmatter:
            self.error(skill_name, "'description' field is required")
            valid = False
        else:
            if not self._validate_description(skill_name, frontmatter["description"]):
                valid = False

        # Validate optional fields
        if "license" in frontmatter:
            self.success(skill_name, "'license' field present")

        if "compatibility" in frontmatter:
            self._validate_compatibility(skill_name, frontmatter["compatibility"])

        if "metadata" in frontmatter:
            if isinstance(frontmatter["metadata"], dict):
                self.success(skill_name, "'metadata' field present")
            else:
                self.warning(skill_name, "'metadata' should be a mapping")

        if "allowed-tools" in frontmatter:
            self.success(skill_name, "'allowed-tools' field present")

        # Check body content
        try:
            with open(skill_path / "SKILL.md", "r") as f:
                content = f.read()

            # Find closing frontmatter delimiter
            lines = content.split("\n")
            closing_idx = None
            for i in range(1, len(lines)):
                if lines[i].strip() == "---":
                    closing_idx = i
                    break

            if closing_idx:
                body_lines = len(lines) - closing_idx - 1
                if body_lines < 2:
                    self.warning(skill_name, f"Body content is very minimal ({body_lines} lines)")
                else:
                    self.success(skill_name, f"Body content present ({body_lines} lines)")
        except Exception as e:
            self.warning(skill_name, f"Could not check body content: {e}")

        # Check optional directories
        if (skill_path / "scripts").exists():
            self.success(skill_name, "scripts/ directory found")

        if (skill_path / "references").exists():
            self.success(skill_name, "references/ directory found")

        if (skill_path / "assets").exists():
            self.success(skill_name, "assets/ directory found")

        if valid:
            self.valid_skills += 1

        return valid

    def validate_skills(self, skills_dir: Path) -> int:
        """Validate all skills in a directory."""
        if not skills_dir.exists():
            print(f"❌ Skills directory not found: {skills_dir}")
            return 2

        print("\n" + "═" * 80)
        print("Agent Skills Specification Validator")
        print("Specification: https://agentskills.io/specification")
        print("═" * 80)

        # Find skill directories
        skill_dirs = sorted([d for d in skills_dir.iterdir() if d.is_dir() and d.name != ".DS_Store"])

        if not skill_dirs:
            print("❌ No skill directories found")
            return 2

        # Validate each skill
        all_valid = True
        for skill_dir in skill_dirs:
            if not self.validate_skill(skill_dir):
                all_valid = False

        # Print summary
        print("\n" + "═" * 80)
        print("Validation Summary")
        print("═" * 80 + "\n")

        if not self.errors:
            print(f"✓ All {self.valid_skills} skills are valid")
        else:
            print(f"✗ Validation failed")

        print(f"\nResults:")
        print(f"  Valid Skills:  {self.valid_skills}")
        print(f"  Errors:        {len(self.errors)}")
        print(f"  Warnings:      {len(self.warnings)}")

        if self.errors:
            print(f"\nErrors:")
            for error in self.errors:
                print(f"  • {error}")

        if self.warnings:
            print(f"\nWarnings:")
            for warning in self.warnings:
                print(f"  • {warning}")

        print()

        return 0 if not self.errors else 1


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Validate skills against Agent Skills specification",
        epilog="Exit codes: 0 = valid, 1 = errors, 2 = usage error"
    )
    parser.add_argument(
        "skills_dir",
        nargs="?",
        default=".",
        help="Path to skills directory (default: current directory)"
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Verbose output"
    )

    args = parser.parse_args()

    validator = SkillValidator(verbose=args.verbose)
    exit_code = validator.validate_skills(Path(args.skills_dir))

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
