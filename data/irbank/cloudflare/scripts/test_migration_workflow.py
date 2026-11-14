from __future__ import annotations

import json
import os
import re
import textwrap
import unittest
from io import BytesIO
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT.parents[2] / ".github" / "workflows" / "d1-r2-migration.yml"
CI_WORKFLOW = ROOT.parents[2] / ".github" / "workflows" / "cloudflare-worker-ci.yml"
LEGACY_D1_IMPORTS = (ROOT / "import-d1.ps1", ROOT / "apply-financial-repair.ps1")
LEGACY_DEFINITION_SEED = ROOT.parent / "organized" / "d1" / "seed_app_definitions.sql"
DISABLED_DEFINITION_SEED = LEGACY_DEFINITION_SEED.with_name("seed_app_definitions.sql.disabled")


class MigrationWorkflowPolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.ci_workflow = CI_WORKFLOW.read_text(encoding="utf-8")

    def test_migration_is_manual_dispatch_only(self) -> None:
        triggers = self.workflow.split("permissions:", 1)[0]
        self.assertRegex(triggers, r"(?m)^  workflow_dispatch:")
        for automatic_trigger in ("push:", "pull_request:", "schedule:"):
            self.assertNotRegex(triggers, rf"(?m)^  {re.escape(automatic_trigger)}")

    def test_non_main_dispatch_fails_instead_of_skipping_preflight(self) -> None:
        preflight = self.workflow.split("\n  migrate:\n", 1)[0]
        self.assertNotRegex(preflight, r"(?m)^    if:")
        branch_check = 'if os.environ.get("GITHUB_REF") != "refs/heads/main":'
        self.assertIn(branch_check, preflight)
        self.assertLess(preflight.index(branch_check), preflight.index("def get(path):"))

    def test_production_dispatch_requires_protected_main(self) -> None:
        preflight = self.workflow.split("\n  migrate:\n", 1)[0]
        self.assertIn('main_branch = get("branches/main")', preflight)
        self.assertIn('if main_branch.get("protected") is not True:', preflight)
        self.assertIn("Protect the main branch before dispatching", preflight)

    def test_migration_environment_must_explicitly_disable_admin_bypass(self) -> None:
        marker = "          python - <<'PY'\n"
        start = self.workflow.index(marker)
        end = self.workflow.index("\n          PY\n", start)
        preflight_script = textwrap.dedent(self.workflow[start + len(marker):end])
        compile(preflight_script, str(WORKFLOW) + ":preflight", "exec")

        cases = (
            ("enabled", True, "Disable administrator bypass"),
            ("missing", None, "Disable administrator bypass"),
            ("disabled", False, None),
        )
        for label, can_admins_bypass, expected_error in cases:
            with self.subTest(state=label):
                environment = {
                    "protection_rules": [{
                        "type": "required_reviewers",
                        "reviewers": [{"type": "User", "reviewer": {"login": "reviewer"}}],
                        "prevent_self_review": True,
                    }],
                    "deployment_branch_policy": {"custom_branch_policies": True},
                }
                if can_admins_bypass is not None:
                    environment["can_admins_bypass"] = can_admins_bypass

                def fake_urlopen(request, timeout: int = 20) -> BytesIO:
                    url = request.full_url
                    if url.endswith("/branches/main"):
                        payload = {"protected": True}
                    elif url.endswith("/environments/d1-r2-migration"):
                        payload = environment
                    elif url.endswith("/environments/d1-r2-migration/deployment-branch-policies"):
                        payload = {"branch_policies": [{"type": "branch", "name": "main"}]}
                    else:
                        self.fail(f"Unexpected preflight request: {url}")
                    return BytesIO(json.dumps(payload).encode("utf-8"))

                with mock.patch.dict(os.environ, {
                    "GITHUB_REF": "refs/heads/main",
                    "REPOSITORY": "masacgt/Diview",
                    "GH_TOKEN": "test-token",
                }), mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
                    if expected_error:
                        with self.assertRaisesRegex(SystemExit, expected_error):
                            exec(preflight_script, {})
                    else:
                        exec(preflight_script, {})

    def test_checkpoint_resume_requires_completed_main_migration_run(self) -> None:
        validation_start = self.workflow.index("- name: Validate checkpoint run provenance")
        download_start = self.workflow.index("- name: Download a prior checkpoint")
        self.assertLess(validation_start, download_start)
        validation = self.workflow[validation_start:download_start]
        for required_check in (
            'f"https://api.github.com/repos/{repository}/actions/runs/{run_id}"',
            'run.get("path")',
            'workflow_path == ".github/workflows/d1-r2-migration.yml"',
            'run.get("event") == "workflow_dispatch"',
            'run.get("head_branch") == "main"',
            'run.get("status") == "completed"',
            'run.get("conclusion") in {"success", "failure"}',
            "same_repository",
        ):
            with self.subTest(check=required_check):
                self.assertIn(required_check, validation)
        self.assertIn("run-id: ${{ inputs.checkpoint_run_id }}", self.workflow[download_start:])

    def test_write_job_depends_on_protected_preflight(self) -> None:
        migrate = self.workflow.split("\n  migrate:\n", 1)[1]
        self.assertRegex(migrate, r"(?m)^    needs: preflight$")
        self.assertRegex(migrate, r"(?m)^    if: github\.ref == 'refs/heads/main'$")
        self.assertIn("name: d1-r2-migration", migrate)
        self.assertIn('os.environ["CONFIRM_WRITE"] != "WRITE_R2"', migrate)

    def test_read_credentials_are_separate_and_d1_only_audits_receive_no_r2_keys(self) -> None:
        d1_only = self.workflow.split("- name: Run D1-only inventory audit", 1)[1].split(
            "- name: Run R2-backed migration or audit", 1
        )[0]
        r2_backed = self.workflow.split("- name: Run R2-backed migration or audit", 1)[1].split(
            "- name: Upload sanitized report", 1
        )[0]

        self.assertIn("CLOUDFLARE_API_TOKEN: ${{ secrets.D1_R2_MIGRATION_ANALYTICS_READ_TOKEN }}", d1_only)
        self.assertIn("CLOUDFLARE_MIGRATION_READ_TOKEN: ${{ secrets.D1_R2_MIGRATION_READ_TOKEN }}", d1_only)
        self.assertNotIn("R2_ACCESS_KEY_ID", d1_only)
        self.assertNotIn("R2_SECRET_ACCESS_KEY", d1_only)
        self.assertIn("R2_ACCESS_KEY_ID: ${{ secrets.D1_R2_MIGRATION_R2_ACCESS_KEY_ID }}", r2_backed)
        self.assertIn("R2_SECRET_ACCESS_KEY: ${{ secrets.D1_R2_MIGRATION_R2_SECRET_ACCESS_KEY }}", r2_backed)
        self.assertIn("CLOUDFLARE_MIGRATION_READ_TOKEN: ${{ secrets.D1_R2_MIGRATION_READ_TOKEN }}", r2_backed)

    def test_account_wide_usage_gate_precedes_all_d1_query_steps(self) -> None:
        gate = self.workflow.index("Check account-wide UTC D1 allowance before any D1 query")
        d1_audit = self.workflow.index("Run D1-only inventory audit")
        r2_audit = self.workflow.index("Run R2-backed migration or audit")
        self.assertLess(gate, d1_audit)
        self.assertLess(gate, r2_audit)
        self.assertIn("--minimum-rows-remaining", self.workflow[gate:d1_audit])

    def test_workflow_artifact_is_limited_to_sanitized_report_and_checkpoint(self) -> None:
        upload = self.workflow.split("- name: Upload sanitized report and resumable checkpoint", 1)[1]
        artifact = upload.split("      - name:", 1)[0]
        self.assertIn("migration-report.json", artifact)
        self.assertIn("migration-checkpoint.json", artifact)
        self.assertNotIn("*.csv", artifact)
        self.assertNotIn("*.sql", artifact)
        self.assertIn("retention-days: 14", artifact)

    def test_worker_ci_runs_contract_suites_for_worker_changes_without_deploying(self) -> None:
        triggers = self.ci_workflow.split("permissions:", 1)[0]
        self.assertIn('"data/irbank/cloudflare/**"', triggers)
        self.assertIn("node --test test/*.test.mjs test/*.test.cjs", self.ci_workflow)
        self.assertIn("scripts/test_migration_workflow.py", self.ci_workflow)
        self.assertIn("scripts/test_migrate_d1_to_r2.py", self.ci_workflow)
        self.assertIn("scripts/test_d1_daily_usage.py", self.ci_workflow)
        self.assertNotIn("wrangler deploy", self.ci_workflow.lower())
        self.assertNotIn("versions deploy", self.ci_workflow.lower())

    def test_retired_d1_market_import_and_repair_scripts_fail_closed(self) -> None:
        for script_path in LEGACY_D1_IMPORTS:
            with self.subTest(script=script_path.name):
                script = script_path.read_text(encoding="utf-8")
                self.assertIn("throw @'", script)
                self.assertNotRegex(script, r"(?im)^\s*npx(?:\.cmd)?\s+wrangler\s+d1\s+(?:execute|migrations\s+apply)")

    def test_legacy_d1_definition_seed_is_kept_out_of_executable_sql_path(self) -> None:
        self.assertFalse(LEGACY_DEFINITION_SEED.exists())
        self.assertTrue(DISABLED_DEFINITION_SEED.is_file())
        self.assertNotEqual(DISABLED_DEFINITION_SEED.suffix, ".sql")


if __name__ == "__main__":
    unittest.main()
