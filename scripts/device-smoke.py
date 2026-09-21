#!/usr/bin/env python3
"""On-device checks that script the gestures the crop page kept regressing on.

The crop screen draws its frame on a Canvas, so neither uiautomator nor
dumpsys can see it. These checks take a screenshot and measure the drawn frame
instead, then drive the same gestures a thumb would:

  * tilting the picture must only inset what is drawn (the frame shrinks);
  * tilting back to zero must restore the frame the user chose (within a few
    pixels), which is the regression that shipped twice;
  * dragging from the black bar just outside the picture must grab the edge
    there and move it to the finger, leaving the opposite edge alone.

Usage:
    python scripts/device-smoke.py --apk app/build/outputs/apk/debug/app-debug.apk
    python scripts/device-smoke.py --media-uri content://media/external/images/media/42

Exit code is non-zero when a check fails, so it can run in CI or by hand.
"""

import argparse
import os
import shutil
import subprocess
import sys
import time


def adb_path():
    """adb from PATH, or from the usual SDK locations when it is not there."""
    found = shutil.which("adb")
    if found:
        return found
    candidates = []
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if root:
            candidates.append(os.path.join(root, "platform-tools", "adb"))
    local = os.environ.get("LOCALAPPDATA")
    if local:
        candidates.append(os.path.join(local, "Android", "Sdk", "platform-tools", "adb.exe"))
    candidates += [
        os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
        os.path.expanduser("~/Android/Sdk/platform-tools/adb"),
    ]
    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            return candidate
    return "adb"


ADB = adb_path()


def adb(serial, *args, check=True, binary=False):
    command = [ADB]
    if serial:
        command += ["-s", serial]
    command += list(args)
    if binary:
        return subprocess.run(command, check=check, stdout=subprocess.PIPE).stdout
    return subprocess.run(
        command, check=check, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
    ).stdout


def shell(serial, command, check=True):
    return adb(serial, "shell", command, check=check)


def screenshot(serial, path):
    data = adb(serial, "exec-out", "screencap", "-p", binary=True)
    with open(path, "wb") as handle:
        handle.write(data)
    return path


def screen_stats(png_path):
    """(width, height, top brightness, middle brightness, bottom brightness)."""
    from PIL import Image

    image = Image.open(png_path).convert("L")
    width, height = image.size
    pixels = image.load()

    def average(y0, y1):
        values = [pixels[x, y] for y in range(y0, y1, 17) for x in range(0, width, 17)]
        return sum(values) / len(values)

    return width, height, average(0, 150), average(height // 2 - 200, height // 2 + 200), average(height - 150, height)


def frame_bounds(png_path, white=235, min_run=300):
    """Bounding box of the crop frame's long white lines, or None.

    The frame is drawn as thin white lines over a darkened picture, so a row (or
    column) counts only when its long white run is *isolated*: a bright patch in
    a photo would otherwise register as a frame edge.
    """
    from PIL import Image

    image = Image.open(png_path).convert("L")
    width, height = image.size
    pixels = image.load()

    def longest_run(values):
        best = current = 0
        for value in values:
            if value >= white:
                current += 1
                best = max(best, current)
            else:
                current = 0
        return best

    # Skip the toolbar and the angle bar, which own white text and ticks.
    top_limit, bottom_limit = 200, height - 330
    rows = [
        y
        for y in range(top_limit, bottom_limit)
        if longest_run([pixels[x, y] for x in range(0, width, 2)]) * 2 >= min_run
    ]
    columns = [
        x
        for x in range(0, width, 2)
        if longest_run([pixels[x, y] for y in range(top_limit, bottom_limit, 2)]) * 2 >= min_run
    ]
    if not rows or not columns:
        return None
    bounds = columns[0], rows[0], columns[-1], rows[-1]
    # A crop frame keeps the screen's aspect ratio; anything else is a photo
    # feature that happened to look like a line.
    frame_ratio = (bounds[2] - bounds[0]) / max(1, bounds[3] - bounds[1])
    if abs(frame_ratio - width / height) > 0.05 * (width / height):
        return None
    # The picture outside the frame is darkened, so just inside every edge has to
    # be clearly brighter than just outside it. That is what tells a real frame
    # apart from a bright line inside the photo.
    def band(x0, x1, y0, y1):
        values = [
            pixels[x, y]
            for y in range(max(0, y0), min(height, y1), 8)
            for x in range(max(0, x0), min(width, x1), 8)
        ]
        return sum(values) / max(1, len(values))

    left, top, right, bottom = bounds
    inside = max(4, (right - left) // 4)
    edges = [
        (band(left, right, top + 4, top + 44), band(left, right, top - 44, top - 4)),
        (band(left, right, bottom - 44, bottom - 4), band(left, right, bottom + 4, bottom + 44)),
        (band(left + 4, left + 44, top, bottom), band(left - 44, left - 4, top, bottom)),
        (band(right - 44, right - 4, top, bottom), band(right + 4, right + 44, top, bottom)),
    ]
    if any(inner < outer * 1.1 for inner, outer in edges):
        return None
    if inside <= 0:
        return None
    return bounds


def looks_like_crop_screen(png_path):
    _, _, top, middle, bottom = screen_stats(png_path)
    return top < 40 and bottom < 40 and middle > 40


def wait_for_frame(serial, path, timeout=30.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        screenshot(serial, path)
        # The crop page is the only screen with black bars top and bottom and a
        # picture in the middle; without that signature a photo feature could be
        # mistaken for the frame.
        if looks_like_crop_screen(path):
            bounds = frame_bounds(path)
            if bounds:
                return bounds
        time.sleep(2)
    return None


def image_uris(serial, limit=3):
    output = shell(
        serial,
        "content query --uri content://media/external/images/media "
        "--projection _id --sort \"_id DESC\"",
        check=False,
    )
    uris = []
    for line in output.splitlines():
        if "_id=" in line:
            uris.append(
                "content://media/external/images/media/"
                + line.split("_id=")[1].split(",")[0].strip()
            )
    return uris[:limit]


def drag(serial, x1, y1, x2, y2, duration=400):
    shell(serial, "input swipe %d %d %d %d %d" % (x1, y1, x2, y2, duration))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=None)
    parser.add_argument("--apk", default=None, help="install this APK first")
    parser.add_argument("--package", default="com.example.album")
    parser.add_argument("--media-uri", default=None)
    parser.add_argument("--workdir", default=".")
    parser.add_argument("--tolerance", type=int, default=8)
    args = parser.parse_args()

    try:
        from PIL import Image
    except ImportError:
        print("Pillow is required for the crop-frame checks: pip install pillow")
        return 2

    serial = args.serial
    package = args.package
    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ((" — " + detail) if detail else ""))
        if not ok:
            failures.append(name)

    if args.apk:
        installed = adb(serial, "install", "-r", args.apk, check=False)
        check("install %s" % args.apk, "Success" in installed, installed.strip().splitlines()[-1:][0] if installed.strip() else "")

    candidates = [args.media_uri] if args.media_uri else image_uris(serial)
    check("a media image is on the device", bool(candidates), ", ".join(candidates) or "none found")
    if not candidates:
        return 1

    opens = "%s/crop-open.png" % args.workdir
    opened = None
    for uri in candidates:
        shell(serial, "am force-stop %s" % package)
        time.sleep(1)
        shell(
            serial,
            "am start -a android.intent.action.ATTACH_DATA -d %s -t image/* -f 1 "
            "-n %s/.WallpaperHandler" % (uri, package),
        )
        opened = wait_for_frame(serial, opens)
        if opened:
            print("      using " + uri)
            break
    check("crop screen opens with a visible frame", opened is not None, str(opened))
    if not opened:
        return 1

    width, height = Image.open(opens).size
    ruler_y = height - 100
    a = opened

    drag(serial, width // 2, ruler_y, width // 2 + 180, ruler_y)
    time.sleep(2)
    tilted = frame_bounds(screenshot(serial, "%s/crop-tilted.png" % args.workdir))
    check("tilting draws a smaller frame", tilted is not None and (
        tilted[3] - tilted[1] < a[3] - a[1] - 20 or tilted[2] - tilted[0] < a[2] - a[0] - 20
    ), str(tilted))

    drag(serial, width // 2 + 180, ruler_y, width // 2, ruler_y)
    time.sleep(2)
    restored = frame_bounds(screenshot(serial, "%s/crop-restored.png" % args.workdir))
    check(
        "returning to zero restores the chosen size",
        restored is not None and all(abs(restored[i] - a[i]) <= args.tolerance for i in range(4)),
        "%s vs %s" % (restored, a),
    )

    # The black bar above the picture: the touch lands outside the frame, so
    # only the outward band of the top edge can pick it up.
    # Start just above the frame's top edge (below the toolbar, which owns the
    # top ~300px) and drag down, so the edge has clear room to follow the finger.
    end_y = a[1] + 100
    start_y = max(a[1] - 30, 320)
    drag(serial, width // 2, start_y, width // 2, end_y, 500)
    time.sleep(2)
    after = frame_bounds(screenshot(serial, "%s/crop-blackbar.png" % args.workdir))
    check(
        "black-bar drag moves the top edge to the finger and pins the bottom",
        after is not None
        and abs(after[1] - end_y) <= 40
        and abs(after[3] - a[3]) <= args.tolerance,
        "%s vs %s (finger ended at %d)" % (after, a, end_y),
    )

    shell(serial, "rm -f /sdcard/*.xml")
    shell(serial, "am force-stop %s" % package)

    print()
    if failures:
        print("device smoke failed: " + ", ".join(failures))
        return 1
    print("device smoke passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
