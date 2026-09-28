#!/usr/bin/env python3
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = ["adb"]

def run(*args, check=True):
    p = subprocess.run(ADB + list(args), text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}")
    return p.stdout + p.stderr

def dump():
    run("shell", "uiautomator", "dump", "/sdcard/window.xml")
    run("pull", "/sdcard/window.xml", "/tmp/window.xml")
    return ET.parse("/tmp/window.xml").getroot()

def nodes():
    return list(dump().iter("node"))

def text_blob():
    return "\n".join(n.attrib.get("text", "") for n in nodes())

def center(bounds):
    nums = [int(x) for x in re.findall(r"\d+", bounds)]
    if len(nums) != 4:
        raise ValueError(bounds)
    x1, y1, x2, y2 = nums
    return (x1 + x2) // 2, (y1 + y2) // 2

def find_node(text, button=False, contains=False):
    for n in nodes():
        t = n.attrib.get("text", "")
        cls = n.attrib.get("class", "")
        if button and not cls.endswith("Button"):
            continue
        ok = text in t if contains else t == text
        if ok:
            return n
    return None

def tap_node(n):
    x, y = center(n.attrib["bounds"])
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(0.7)

def swipe(up=True):
    if up:
        run("shell", "input", "swipe", "520", "1500", "520", "600", "450")
    else:
        run("shell", "input", "swipe", "520", "650", "520", "1500", "450")
    time.sleep(0.5)

def find_with_scroll(text, button=False, contains=False, direction="up", attempts=8):
    for _ in range(attempts):
        n = find_node(text, button=button, contains=contains)
        if n is not None:
            return n
        swipe(up=(direction == "up"))
    raise AssertionError(f"Could not find UI text: {text!r}\nVisible text:\n{text_blob()}")

def assert_contains(text, direction="up", attempts=8):
    for _ in range(attempts):
        blob = text_blob()
        if text in blob:
            return
        swipe(up=(direction == "up"))
    raise AssertionError(f"Missing expected text: {text!r}\nVisible text:\n{text_blob()}")

def to_top():
    for _ in range(8):
        swipe(up=False)

run("logcat", "-c")
out = run("shell", "am", "start", "-W", "-n", "dev.ffc.ce3/.MainActivity")
if "Status: ok" not in out:
    raise AssertionError("MainActivity did not start cleanly:\n" + out)

assert_contains("CONNECTOME SYSTEM", attempts=2)
tap_node(find_node("CONNECTOME SYSTEM", button=True))
time.sleep(0.8)

resumed = run("shell", "dumpsys", "activity", "activities")
if "dev.ffc.ce3/.BioSystemActivity" not in resumed:
    raise AssertionError("BioSystemActivity is not active")

assert_contains("Ce3 · Connectome-Transfer System", attempts=2)

self_test = find_with_scroll("RUN SELF TEST", button=True, attempts=4)
tap_node(self_test)
assert_contains("ALL PASS", attempts=2)
assert_contains("7/7", attempts=2)

to_top()
forage = find_with_scroll("FORAGE", button=True, direction="up", attempts=8)
tap_node(forage)
assert_contains("OUTPUT = APPROACH", direction="up", attempts=8)

to_top()
threat = find_with_scroll("THREAT", button=True, direction="up", attempts=8)
tap_node(threat)
assert_contains("OUTPUT = AVOID", direction="up", attempts=8)

log = run("logcat", "-d", check=False)
fatal_lines = [line for line in log.splitlines()
               if "FATAL EXCEPTION" in line or "Process: dev.ffc.ce3" in line]
if fatal_lines:
    raise AssertionError("Fatal exception found in logcat:\n" + "\n".join(fatal_lines[-20:]))

run("exec-out", "screencap", "-p", check=False)
print("UI_SMOKE_PASS: launch -> connectome system -> self-test -> forage/approach -> threat/avoid")
