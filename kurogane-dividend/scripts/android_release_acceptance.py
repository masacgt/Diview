"""Local acceptance checks on the two disposable release-audit AVDs only.

Requires an explicit JDK 17. Never clears or roots a physical device. Evidence
contains synthetic-data hashes/counts, not portfolio contents or credentials.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import queue
import threading
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.kurogane.dividend"
AVDS = {"emulator-5556": "DiviewReleaseAudit35", "emulator-5558": "DiviewReleaseAudit26"}


def digest(value):
    if not isinstance(value, bytes):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(value).hexdigest()


def fixture(payload_bytes=2):
    holding = dict(id="acceptance-fixture", code="ZTEST0", name="移行検証銘柄", sector="検証", market="東証",
                   category="JAPAN", price=2000.0, currency="¥", shares=100, acqPrice=1000.0,
                   dps=80.0, yieldRate=4.0, yoc=8.0, nextPayoutTiming="6月", nextPayoutAmount="¥4,000",
                   dividendMonths=[6, 12], dividendType="配当金", accountType="特定口座",
                   dailyChange="—", isPositive=True, taxFreeShares=40, isNoDividendConfirmed=False)
    watch = dict(code="ZBACKUP", name="無配の移行検証", market="東証", sector="検証", category="jp",
                 price=100.0, currency="¥", dps=0.0, currentYield=0.0, targetYield=4.0, targetPrice=0.0,
                 dailyChange="—", isPositive=True, payoutMonths=[], payoutTiming="", desc="",
                 alertEnabled=False, badge=None, defaultShares=100, isNoDividendConfirmed=True)
    payload = b"[" + b" " * max(0, payload_bytes - 2) + b"]"
    return json.dumps(dict(version=1, holdings=[holding], watchlist=[watch], dvmBackups={
        kind: base64.b64encode(payload if kind == "RECORDS" else b"[]").decode()
        for kind in ("STOCKS", "RECORDS", "CUSTOM_STOCKS", "YIELD_ALERTS")
    }), ensure_ascii=False, separators=(",", ":")).encode()


class Device:
    def __init__(self, args):
        self.args = args
        self.evidence = args.evidence.resolve()
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.env = os.environ.copy()
        self.env["JAVA_HOME"] = str(args.java_home.resolve())
        self.env["ANDROID_HOME"] = str(args.android_sdk.resolve())
        self.env["PATH"] = str(args.java_home / "bin") + os.pathsep + self.env["PATH"]
        version = subprocess.run([str(args.java_home / "bin/java"), "-version"], env=self.env,
                                 capture_output=True, check=True).stderr.decode()
        if not re.search(r'version "17[.]', version):
            raise ValueError("JDK 17 is required")
        if args.device not in AVDS:
            raise ValueError("Only the task-owned disposable AVD serials are accepted")
        self.adb = str(args.android_sdk / "platform-tools/adb")
        if AVDS[args.device] not in self.run("emu", "avd", "name").decode():
            raise ValueError("The AVD does not match the task-owned target")
        # Non-debuggable local release APKs need root readback on these disposable images.
        self.run("root")
        self.run("wait-for-device")
        if "uid=0" not in self.shell("id"):
            raise RuntimeError("Rootable disposable AVD required for release data readback")

    def run(self, *args, data=None, timeout=60, check=True):
        result = subprocess.run([self.adb, "-s", self.args.device, *map(str, args)],
                                input=data, capture_output=True, env=self.env, timeout=timeout)
        if check and result.returncode:
            with (self.evidence / "adb-errors.log").open("a", encoding="utf-8") as log:
                log.write(str(args[0]) + ": " + result.stderr.decode("utf-8", errors="replace") + "\n")
            raise RuntimeError("ADB operation failed: " + str(args[0]))
        return result.stdout

    def shell(self, *args, **kwargs):
        return self.run("shell", *args, **kwargs).decode("utf-8", errors="replace")

    def write_private(self, relative, data):
        self.run("shell", "run-as", PACKAGE, "mkdir", "-p", relative.split("/")[0])
        self.run("shell", "run-as", PACKAGE, "sh", "-c", "'cat > " + relative + "'", data=data)

    def read_private(self, relative):
        if "uid=0" in self.shell("id"):
            return self.run("exec-out", "cat", "/data/user/0/" + PACKAGE + "/" + relative)
        return self.run("exec-out", "run-as", PACKAGE, "cat", relative)

    def facts(self):
        snapshot = json.loads(self.read_private("files/diview_auto_backup.json"))
        display = ET.fromstring(self.read_private("shared_prefs/display_preferences.xml"))
        settings = {item.attrib["name"]: item.attrib.get("value", item.text) for item in display}
        return dict(version=snapshot["version"], holdings=len(snapshot["holdings"]), watchlist=len(snapshot["watchlist"]),
                    holdingsHash=digest(snapshot["holdings"]), watchlistHash=digest(snapshot["watchlist"]),
                    legacyHashes={k: digest(base64.b64decode(v)) for k, v in snapshot["dvmBackups"].items()},
                    settingsHash=digest(settings), overridesHash=digest(snapshot["overrides"]) if "overrides" in snapshot else None)

    def install(self, apk):
        if apk.suffix == ".apks":
            if not self.args.bundletool:
                raise ValueError("--bundletool is required for an AAB-generated APK set")
            subprocess.run([str(self.args.java_home / "bin/java"), "-jar", str(self.args.bundletool.resolve()),
                            "install-apks", "--apks=" + str(apk.resolve()), "--device-id=" + self.args.device,
                            "--adb=" + self.adb], env=self.env, capture_output=True, check=True, timeout=120)
        elif b"Success" not in self.run("install", "-r", apk.resolve(), timeout=120):
            raise RuntimeError("Installation failed")

    def diagnostic(self, operation, *extras):
        result = self.shell("am", "instrument", "-w", "-r", "-e", "allowSyntheticFixture", "true",
                            "-e", "operation", operation,
                            *extras,
                            PACKAGE + ".test/com.kurogane.dividend.ReleaseFixtureInstrumentation", timeout=90)
        (self.evidence / ("diagnostic-" + self.args.device + "-" + operation + ".log")).write_text(result, encoding="utf-8")
        if "completed=true" not in result:
            raise RuntimeError("Synthetic diagnostic failed: " + operation)

    def launch(self):
        started = time.monotonic()
        result = self.shell("am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
        if "Status: ok" not in result:
            raise RuntimeError("MainActivity did not start")
        time.sleep(3)
        if not self.shell("pidof", PACKAGE).strip():
            raise RuntimeError("App exited after launch")
        self.shell("uiautomator", "dump", "/data/local/tmp/release-acceptance.xml")
        ui = self.run("exec-out", "cat", "/data/local/tmp/release-acceptance.xml").decode()
        if "総資産評価額" not in ui:
            raise RuntimeError("Asset screen was not rendered")
        return dict(elapsedSeconds=round(time.monotonic() - started, 3),
                    startTiming={k: int(v) for k, v in re.findall(r"(TotalTime|WaitTime|ThisTime): (\d+)", result)})

    def save(self, name, data):
        (self.evidence / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    def update(self):
        if not self.args.baseline_apk or not self.args.candidate_apk:
            raise ValueError("Both local APK paths are required")
        self.install(self.args.baseline_apk)
        if "Success" not in self.shell("pm", "clear", PACKAGE):
            raise RuntimeError("Could not clear the disposable fixture")
        if self.args.fixture_apk:
            self.install(self.args.fixture_apk)
            self.diagnostic("seed")
            self.diagnostic("verify")
        else:
            self.write_private("files/diview_auto_backup.json", fixture())
            settings = b'<map><string name="theme_mode">dark</string><boolean name="oled_black" value="true"/><string name="selected_accent">blue</string><string name="tax_rate_mode">net</string><int name="composition_threshold" value="7"/></map>'
            self.write_private("shared_prefs/display_preferences.xml", settings)
        baseline_start = self.launch()
        self.shell("am", "force-stop", PACKAGE)
        before = self.facts()
        if self.args.fixture_apk:
            self.diagnostic("verify")
        self.install(self.args.candidate_apk)
        install_preserved = self.facts() == before
        candidate_start = self.launch()
        self.shell("am", "force-stop", PACKAGE)
        after = self.facts()
        before_core = {k: v for k, v in before.items() if k != "overridesHash"}
        after_core = {k: v for k, v in after.items() if k != "overridesHash"}
        expected_overrides = {"shareholder_benefits": {"1306": [{"name": "検証優待", "marketValue": "1000"}]},
                              "stock_climate_overrides": {"1306": "DEFENSIVE"},
                              "dividend_history_overrides": {"1306": [{"year": 2025, "stock_price_overridden": True,
                                  "stock_price": 1000.0, "annual_dividend_overridden": True, "annual_dividend": 80.0}]}}
        # JSON numeric spelling (1000 vs 1000.0) is normalized by the Android serializer.
        expected_overrides["dividend_history_overrides"]["1306"][0].update(stock_price=1000, annual_dividend=80)
        overrides_preserved = after["overridesHash"] == digest(expected_overrides) if self.args.fixture_apk else True
        report = dict(jdk=17, device=self.args.device, baselineApkSha256=digest(self.args.baseline_apk.read_bytes()),
                      candidateApkSha256=digest(self.args.candidate_apk.read_bytes()), baselineStart=baseline_start,
                      candidateStart=candidate_start, before=before, after=after,
                      installPreserved=install_preserved, launchPreserved=(before_core == after_core),
                      manualOverridesPreserved=overrides_preserved, candidateKind=self.args.candidate_apk.suffix)
        self.save("update-" + self.args.device + ".json", report)
        if not install_preserved or before_core != after_core or not overrides_preserved:
            raise AssertionError("Update changed the synthetic saved data")
        print(json.dumps({k: v for k, v in report.items() if k not in ("before", "after")}), flush=True)

    def restart(self):
        before = self.facts()
        self.launch()
        self.shell("input", "keyevent", "KEYCODE_HOME")
        pids = self.shell("pidof", PACKAGE).strip()
        if not re.fullmatch(r"\d+(?: \d+)*", pids):
            raise RuntimeError("The disposable app process was not identified")
        # am kill may retain a perceptible/busy process. Root is restricted to these
        # disposable AVDs; SIGKILL actually exercises abrupt process recreation.
        self.shell("kill", "-9", *pids.split())
        deadline = time.monotonic() + 10
        while self.shell("pidof", PACKAGE, check=False).strip() and time.monotonic() < deadline:
            time.sleep(.2)
        killed = not self.shell("pidof", PACKAGE, check=False).strip()
        self.launch()
        self.shell("am", "force-stop", PACKAGE)
        after = self.facts()
        report = dict(jdk=17, device=self.args.device, method="SIGKILL on disposable AVD",
                      backgroundProcessKilled=killed, savedFactsPreserved=before == after)
        self.save("restart-" + self.args.device + ".json", report)
        print(json.dumps(report), flush=True)
        if not killed or before != after:
            raise AssertionError("Background process recreation was not verified")

    def low_space(self):
        target = "/data/user/0/" + PACKAGE + "/cache/release-lowspace"
        self.shell("mkdir", "-p", target)
        uid = self.shell("stat", "-c", "%u", "/data/user/0/" + PACKAGE).strip()
        label = self.shell("ls", "-Zd", "/data/user/0/" + PACKAGE + "/cache").split()[0]
        if not uid.isdecimal() or not re.fullmatch(r"u:object_r:app_data_file:s0(?::c\d+(?:,c\d+)*)?", label):
            raise ValueError("Unexpected disposable app filesystem identity")
        options = ('size=1m,uid=' + uid + ',gid=' + uid + ',mode=0700,fscontext=u:object_r:tmpfs:s0,context="'
                   + label + '"')
        self.shell("mount -t tmpfs -o '" + options + "' tmpfs " + target)
        try:
            volume = self.shell("df", "-k", target)
            if "tmpfs" not in volume or int(volume.splitlines()[-1].split()[1]) > 2048:
                raise ValueError("The bounded disposable volume was not mounted")
            result = self.shell("am", "instrument", "-w", "-r", "-e", "class",
                                PACKAGE + ".BackupCompatibilityRegressionTest#aFailedSnapshotReplacementKeepsThePreviousCompleteBackup",
                                "-e", "lowSpaceDirectory", target,
                                PACKAGE + ".test/com.kurogane.dividend.ReleaseFixtureInstrumentation", timeout=90)
            (self.evidence / ("low-space-" + self.args.device + ".log")).write_text(result, encoding="utf-8")
            passed = "OK (1 test)" in result
            self.save("low-space-" + self.args.device + ".json", dict(jdk=17, device=self.args.device,
                      boundedTmpfsKiB=1024, actualEnospcAndPreviousBackupRetentionPassed=passed))
            if not passed:
                raise AssertionError("ENOSPC retention was not verified")
            print(json.dumps(dict(device=self.args.device, actualEnospcAndPreviousBackupRetentionPassed=True)), flush=True)
        finally:
            self.shell("umount", target)
            self.shell("rmdir", target)

    def quota(self):
        original_enabled = "currently enabled" in self.shell("bmgr", "enabled")
        transports = self.shell("bmgr", "list", "transports")
        selected = re.search(r"^\s*\*\s*(\S+)", transports, re.MULTILINE)
        if not selected or "android/com.android.internal.backup.LocalTransport" not in transports:
            raise ValueError("Offline LocalTransport is required")
        original_transport = selected.group(1)
        try:
            self.shell("bmgr", "enable", "true")
            self.shell("bmgr", "transport", "android/com.android.internal.backup.LocalTransport")
            self.shell("am", "force-stop", PACKAGE)
            if "Success" not in self.shell("pm", "clear", PACKAGE):
                raise RuntimeError("Could not clear the disposable quota fixture")
            # Android does not back up a never-launched, stopped package.
            self.launch()
            self.shell("input", "keyevent", "KEYCODE_HOME")
            pids = self.shell("pidof", PACKAGE).strip()
            if not re.fullmatch(r"\d+(?: \d+)*", pids):
                raise RuntimeError("Disposable quota process was not identified")
            self.shell("kill", "-9", *pids.split())
            contents = fixture(21 * 1024 * 1024)
            self.write_private("files/diview_auto_backup.json", contents)
            output = self.shell("bmgr", "backupnow", PACKAGE, timeout=180)
            (self.evidence / ("quota-" + self.args.device + ".log")).write_text(output, encoding="utf-8")
            unchanged = digest(self.read_private("files/diview_auto_backup.json")) == digest(contents)
            rejected = "quota" in output.lower() or "-1005" in output
            report = dict(jdk=17, device=self.args.device, transport="offline LocalTransport",
                          snapshotBytes=len(contents), quotaRejectionObserved=rejected, originalSnapshotUnchanged=unchanged,
                          actualGoogleCloudTest=False)
            self.save("quota-" + self.args.device + ".json", report)
            print(json.dumps(report), flush=True)
            if not unchanged:
                raise AssertionError("The backup attempt changed the snapshot")
            if not rejected:
                raise AssertionError("Quota rejection was not observed; inspect the per-package result")
        finally:
            self.shell("bmgr", "transport", original_transport)
            self.shell("bmgr", "enable", "true" if original_enabled else "false")

    def interrupt(self):
        before = self.facts()
        command = [self.adb, "-s", self.args.device, "shell", "am", "instrument", "-w", "-r",
                   "-e", "allowSyntheticFixture", "true", "-e", "operation", "interrupt-snapshot",
                   PACKAGE + ".test/com.kurogane.dividend.ReleaseFixtureInstrumentation"]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=self.env)
        lines = queue.Queue()
        def read_lines():
            for line in iter(process.stdout.readline, b""):
                lines.put(line)
        reader = threading.Thread(target=read_lines, daemon=True)
        reader.start()
        opened = False
        deadline = time.monotonic() + 40
        try:
            while time.monotonic() < deadline:
                try:
                    if b"phase=atomic-open" in lines.get(timeout=1):
                        opened = True
                        break
                except queue.Empty:
                    if process.poll() is not None:
                        break
            if not opened:
                raise RuntimeError("The atomic-write interruption point was not reached")
            self.shell("am", "force-stop", PACKAGE)
            process.wait(timeout=15)
        finally:
            if process.poll() is None:
                self.shell("am", "force-stop", PACKAGE)
                process.kill()
                process.wait(timeout=15)
        # Require a fresh restore from the interrupted portable file, not the surviving device preferences.
        base = "/data/user/0/" + PACKAGE + "/shared_prefs/local_import_store.xml"
        self.shell("rm", "-f", base, base + ".bak")
        start = self.launch()
        self.shell("am", "force-stop", PACKAGE)
        retained = before == self.facts()
        report = dict(jdk=17, device=self.args.device, atomicWriteInterrupted=opened,
                      restoredAllSavedFacts=retained, start=start)
        self.save("interruption-" + self.args.device + ".json", report)
        print(json.dumps(report), flush=True)
        if not retained:
            raise AssertionError("Interrupted write did not retain the complete backup")

    def notifications(self):
        def operation(name):
            result = self.shell("am", "instrument", "-w", "-r", "-e", "allowSyntheticFixture", "true",
                                "-e", "operation", name,
                                PACKAGE + ".test/com.kurogane.dividend.ReleaseFixtureInstrumentation", timeout=90)
            match = re.search(r"activeWorkIds=([a-f0-9,\-]*)", result)
            if "completed=true" not in result or not match:
                raise AssertionError("Notification diagnostic did not complete: " + name)
            return match.group(1).split(",")

        before = operation("schedule-notifications")
        def system_jobs():
            dump = self.shell("dumpsys", "jobscheduler", PACKAGE)
            # Android 8 redacts parcelled JobInfo extras; match the OS job headers
            # instead of assuming the dump exposes WorkManager's UUID extras.
            return set(re.findall(r"^\s*JOB #([^\s:]+):[^\n]*" + re.escape(PACKAGE) +
                                  r"/androidx[.]work[.]impl[.]background[.]systemjob[.]SystemJobService", dump, re.MULTILINE))
        registered_before = system_jobs()
        try:
            self.shell("dumpsys", "battery", "unplug")
            self.shell("input", "keyevent", "KEYCODE_SLEEP")
            self.shell("dumpsys", "deviceidle", "force-idle")
            idle = self.shell("dumpsys", "deviceidle", "get", "deep").strip()
            retained_in_idle = len(registered_before) == 2 and system_jobs() == registered_before
        finally:
            self.shell("dumpsys", "deviceidle", "unforce")
            self.shell("dumpsys", "battery", "reset")
            self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.run("reboot")
        self.run("wait-for-device", timeout=120)
        deadline = time.monotonic() + 120
        while self.shell("getprop", "sys.boot_completed").strip() != "1":
            if time.monotonic() > deadline:
                raise RuntimeError("Disposable emulator did not finish rebooting")
            time.sleep(1)
        try:
            after = operation("verify-notifications")
            report = dict(jdk=17, device=self.args.device, forcedIdleState=idle,
                          registeredJobCountBeforeIdle=len(registered_before),
                          registeredJobsRetainedInIdle=retained_in_idle,
                          sameUniqueWorkIdsAfterReboot=before == after,
                          notificationDeliveryTimingVerified=False)
            self.save("notification-lifecycle-" + self.args.device + ".json", report)
            print(json.dumps(report), flush=True)
            if idle != "IDLE" or not retained_in_idle or before != after:
                raise AssertionError("Idle/reboot job retention was not verified")
        finally:
            operation("disable-notifications")

    def profile(self):
        if not self.args.fixture_apk or not self.args.candidate_apk or (self.args.profile_variant == "both" and not self.args.baseline_apk):
            raise ValueError("The selected APK paths and the synthetic fixture test APK are required")
        self.install(self.args.fixture_apk)
        report = dict(jdk=17, device=self.args.device, environment="local emulator; timings are observational", samples=[])
        artifacts = (("baseline", self.args.baseline_apk), ("candidate", self.args.candidate_apk)) if self.args.profile_variant == "both" else (("candidate", self.args.candidate_apk),)
        report["requestedArtifacts"] = self.args.profile_variant
        for label, apk in artifacts:
            self.install(apk)
            for count in (0, 300, 2000):
                self.shell("pm", "clear", PACKAGE)
                self.diagnostic("seed", "-e", "holdingCount", str(count))
                self.shell("am", "force-stop", PACKAGE)
                self.shell("dumpsys", "gfxinfo", PACKAGE, "reset")
                start = self.launch()
                saved_count = self.facts()["holdings"]
                if saved_count != count:
                    raise AssertionError("Profile launch did not retain every synthetic holding")
                memory = self.shell("dumpsys", "meminfo", PACKAGE)
                graphics = self.shell("dumpsys", "gfxinfo", PACKAGE)
                pss = re.search(r"TOTAL PSS:\s*(\d+)", memory) or re.search(r"^\s*TOTAL\s+(\d+)", memory, re.MULTILINE)
                frames = re.search(r"Total frames rendered:\s*(\d+)", graphics)
                janky = re.search(r"Janky frames:\s*(\d+)", graphics)
                sample = dict(artifact=label, holdings=count, savedHoldingsAfterLaunch=saved_count,
                              start=start, memoryTotalPssKb=int(pss.group(1)) if pss else None,
                              frames=int(frames.group(1)) if frames else None, jankyFrames=int(janky.group(1)) if janky else None)
                report["samples"].append(sample)
                print(json.dumps(sample), flush=True)
        self.save("profile-" + self.args.device + ".json", report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("update", "restart", "low_space", "quota", "interrupt", "notifications", "profile"))
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--android-sdk", required=True, type=Path)
    parser.add_argument("--device", required=True)
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument("--baseline-apk", type=Path)
    parser.add_argument("--candidate-apk", type=Path)
    parser.add_argument("--fixture-apk", type=Path)
    parser.add_argument("--bundletool", type=Path)
    parser.add_argument("--profile-variant", choices=("both", "candidate"), default="both")
    args = parser.parse_args()
    device = Device(args)
    getattr(device, args.mode)()


if __name__ == "__main__":
    main()
