"""No real Java/ADB invocation: reject unsafe targets before root/clear/reboot."""
import argparse
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import android_release_acceptance as acceptance


class AcceptanceGuardTest(unittest.TestCase):
    def create(self, serial, avd, java_version='17.0.20'):
        calls = []

        def adb(instance, *args, **kwargs):
            calls.append(args)
            if args == ('emu', 'avd', 'name'):
                return (avd + '\nOK\n').encode()
            return b'uid=0(root)' if args == ('shell', 'id') else b''

        with tempfile.TemporaryDirectory(prefix='diview-release-guard-') as directory:
            self.assertEqual(Path(directory).resolve().parent, Path(tempfile.gettempdir()).resolve())
            args = argparse.Namespace(java_home=Path(directory) / 'jdk', android_sdk=Path(directory) / 'sdk',
                                      device=serial, evidence=Path(directory))
            version = SimpleNamespace(stderr=('openjdk version "' + java_version + '"').encode())
            with patch.object(acceptance.subprocess, 'run', return_value=version), patch.object(acceptance.Device, 'run', adb):
                try:
                    acceptance.Device(args)
                except ValueError as failure:
                    return calls, str(failure)
        return calls, None

    def test_original_emulator_serial_is_rejected_without_adb(self):
        calls, failure = self.create('emulator-5554', 'DiviewReleaseAudit35')
        self.assertIsNotNone(failure)
        self.assertEqual([], calls)

    def test_similar_but_different_avd_name_is_rejected_before_root(self):
        calls, failure = self.create('emulator-5556', 'DiviewReleaseAudit35-other')
        self.assertIsNotNone(failure)
        self.assertEqual([('emu', 'avd', 'name')], calls)

    def test_non_jdk17_is_rejected_without_adb(self):
        calls, failure = self.create('emulator-5558', 'DiviewReleaseAudit26', java_version='21.0.1')
        self.assertIsNotNone(failure)
        self.assertEqual([], calls)

    def test_exact_owned_avd_and_jdk17_allow_readback_setup(self):
        calls, failure = self.create('emulator-5558', 'DiviewReleaseAudit26')
        self.assertIsNone(failure)
        self.assertEqual([('emu', 'avd', 'name'), ('root',), ('wait-for-device',), ('shell', 'id')], calls)


if __name__ == '__main__':
    unittest.main()
