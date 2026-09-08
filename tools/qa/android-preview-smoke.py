#!/usr/bin/env python3
"""Bounded native smoke check of BERT Preview on a disposable, read-only AVD overlay."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--avd', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--apk-source-sha', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, default=Path('/opt/android-sdk'))
    parser.add_argument('--boot-timeout', type=int, default=480)
    parser.add_argument('--install-timeout', type=int, default=300)
    parser.add_argument('--total-timeout', type=int, default=1200)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    # Reject the release/debug app before any device mutation.
    subprocess.run([sys.executable, str(Path(__file__).with_name('check-preview-apk.py')), str(args.apk)], check=True)
    report = {
        'runner_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
        'runner_dirty_at_start': bool(subprocess.check_output(['git', 'status', '--porcelain']).strip()),
        'apk_source_sha': args.apk_source_sha, 'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        'avd': args.avd, 'overlay': 'read-only; guest changes discarded on exit',
        'acceleration': False, 'cores': 1, 'ram_mb': 2048,
        'network': 'disabled before first app launch', 'completed': False, 'checks': [], 'timings_seconds': {},
        'scope': 'Native offline smoke only; no API 36, sharing, launcher, wallpaper, TalkBack or physical-performance claim.'
    }
    started = time.monotonic()
    deadline = started + args.total_timeout
    adb = [str(args.sdk / 'platform-tools/adb'), '-P', '5041', '-s', 'emulator-5582']
    package = 'global.bert.widget.preview'

    def run(command, timeout=90):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Overall native smoke budget exhausted')
        return subprocess.check_output(command, stderr=subprocess.STDOUT, timeout=min(timeout, remaining))

    def shell(*command, timeout=90):
        return run(adb + ['shell', *command], timeout=timeout).decode().strip()

    def tree():
        shell('uiautomator', 'dump', '/sdcard/bert-preview-window.xml')
        raw = run(adb + ['exec-out', 'cat', '/sdcard/bert-preview-window.xml'])
        return ET.fromstring(raw), raw

    def wait_text(text, timeout=100):
        until = min(deadline, time.monotonic() + timeout)
        while time.monotonic() < until:
            root, raw = tree()
            if text in raw.decode():
                return root, raw
            time.sleep(2)
        raise AssertionError('Native UI did not expose: ' + text)

    def tap(text):
        root, _ = wait_text(text)
        nodes = [n for n in root.iter('node') if n.attrib.get('text') == text or n.attrib.get('content-desc') == text]
        if not nodes:
            raise AssertionError('Missing exact control: ' + text)
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[-1].attrib['bounds']))
        shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        time.sleep(2)

    def capture(name):
        _, raw = tree()
        (out / (name + '.xml')).write_bytes(raw)
        png = run(adb + ['exec-out', 'screencap', '-p'])
        if not png.startswith(b'\x89PNG\r\n\x1a\n'):
            raise AssertionError('Android did not return a PNG capture')
        (out / (name + '.png')).write_bytes(png)
        return raw.decode()

    def check(name, condition):
        report['checks'].append({'name': name, 'passed': bool(condition)})
        print(('PASS ' if condition else 'FAIL ') + name, flush=True)
        if not condition:
            raise AssertionError(name)

    emulator = None
    with (out / 'emulator.log').open('w') as log:
        try:
            env = dict(os.environ, ANDROID_ADB_SERVER_PORT='5041')
            emulator = subprocess.Popen([
                str(args.sdk / 'emulator/emulator'), '-avd', args.avd, '-read-only', '-no-snapshot',
                '-accel', 'off', '-cores', '1', '-memory', '2048', '-no-window', '-no-audio', '-no-boot-anim',
                '-gpu', 'swiftshader_indirect', '-port', '5582', '-no-metrics',
            ], stdout=log, stderr=subprocess.STDOUT, env=env)
            report['emulator_pid'] = emulator.pid
            run(adb[:-2] + ['start-server'])
            while time.monotonic() - started < args.boot_timeout:
                if emulator.poll() is not None:
                    raise RuntimeError('Emulator exited before boot; inspect emulator.log')
                try:
                    if shell('getprop', 'sys.boot_completed', timeout=15) == '1':
                        break
                except subprocess.SubprocessError:
                    pass
                print('Booting Android: ' + str(round(time.monotonic()-started)) + 's', flush=True)
                time.sleep(10)
            else:
                raise TimeoutError('Android boot exceeded its budget')
            report['timings_seconds']['boot'] = round(time.monotonic()-started, 2)
            report['android_sdk'] = shell('getprop', 'ro.build.version.sdk')
            report['android_release'] = shell('getprop', 'ro.build.version.release')
            report['build_fingerprint'] = shell('getprop', 'ro.build.fingerprint')
            print('Android booted; installing verified preview APK.', flush=True)
            install_started = time.monotonic()
            installed = run(adb + ['install', '--no-streaming', '-r', str(args.apk)], timeout=args.install_timeout)
            (out / 'install.txt').write_bytes(installed)
            check('Preview APK installed', b'Success' in installed)
            report['timings_seconds']['install'] = round(time.monotonic()-install_started, 2)
            check('Preview starts with empty private storage', shell('pm', 'clear', package) == 'Success')
            shell('svc', 'wifi', 'disable')
            shell('svc', 'data', 'disable')
            shell('wm', 'size', '720x1280')
            shell('wm', 'density', '320')
            shell('settings', 'put', 'system', 'font_scale', '1.0')
            report['display'] = {'size': shell('wm', 'size'), 'density': shell('wm', 'density')}
            report['radio_settings'] = {'wifi_on': shell('settings', 'get', 'global', 'wifi_on'),
                                        'mobile_data': shell('settings', 'get', 'global', 'mobile_data')}
            check('Wi-Fi and mobile data disabled', all(value == '0' for value in report['radio_settings'].values()))
            for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
                shell('settings', 'put', 'global', setting, '0')
            shell('input', 'keyevent', 'KEYCODE_WAKEUP')
            shell('wm', 'dismiss-keyguard')
            (out / 'launch.txt').write_text(shell('am', 'start', '-W', '-n', package+'/global.bert.widget.MainActivity'))
            wait_text('Bert’s update couldn’t load.')
            home = capture('home-offline')
            check('Cold offline Home exposes all four destinations', all(label in home for label in ['Home', 'Explore', 'Create', 'Tools']))
            tap('Explore')
            wait_text('Flappy Bert')
            capture('explore')
            check('Native Explore reachable offline', True)
            tap('Create')
            wait_text('Caption card preview:')
            capture('create')
            check('Native caption preview renders offline', True)
            tap('Tools')
            tap('Holdings')
            wait_text('BERT amount')
            capture('holdings')
            check('Native holdings editor reachable offline', True)
            shell('settings', 'put', 'system', 'font_scale', '2.0')
            tap('Home')
            large = capture('home-large')
            check('All destinations remain exposed at 2x text', all(label in large for label in ['Home', 'Explore', 'Create', 'Tools']))
            (out / 'runtime-log.txt').write_text(shell('logcat', '-d', '-s', 'AndroidRuntime:E'))
            check('No AndroidRuntime fatal exception observed', 'FATAL EXCEPTION' not in (out / 'runtime-log.txt').read_text())
            report['completed'] = True
        except Exception as error:
            report['failure'] = str(error)
            if isinstance(error, subprocess.CalledProcessError):
                (out / 'failed-command.txt').write_bytes(error.output or b'')
            raise
        finally:
            report['elapsed_seconds'] = round(time.monotonic()-started, 2)
            if emulator is not None:
                emulator.terminate()
                try:
                    emulator.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    emulator.kill()
                    emulator.wait(timeout=10)
                report['emulator_exit_code'] = emulator.returncode
            (out / 'results.json').write_text(json.dumps(report, indent=2)+'\n')


if __name__ == '__main__':
    main()
