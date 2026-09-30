"""Synthesize the Signal Radar sounds (mono, 44.1 kHz, OGG Vorbis) into assets/signalradar/sounds/.

  scan_ping     sonar ping: 1.2 kHz sine, fast attack, ~0.6 s exponential decay, light pitch drop, soft echo tail
  blip          radar tick: 2.4 kHz, 40 ms, quick decay
  target_found  rising three-note electronic chime (~0.5 s)
  motion_beep   motion tracker beep: 1.6 kHz soft-clipped square-ish tone, ~120 ms, slight low-pass

Needs numpy + soundfile (pip install numpy soundfile). Existing .ogg files are never overwritten unless --force.
Run:  python tools/make_sounds.py [--force]
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
PEAK = 10 ** (-3 / 20)  # -3 dBFS
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "signalradar", "sounds")


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def tone(freq_start, freq_end, sec, shape=None):
    """Sine with an exponential glide from freq_start to freq_end (phase integrated, so no clicks)."""
    t = t_axis(sec)
    f = freq_start * (freq_end / freq_start) ** (t / sec)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) if shape is None else shape(ph)


def env(n, attack, decay_tau):
    t = np.arange(n) / SR
    return np.minimum(1.0, t / attack) * np.exp(-t / decay_tau)


def lowpass(x, cutoff):
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def finish(x):
    n = len(x)
    fade = int(0.005 * SR)  # 5 ms fades in and out
    x = x.copy()
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    x = x - x.mean()
    return (x / np.max(np.abs(x)) * PEAK).astype(np.float32)


def scan_ping():
    sec = 1.3
    dry = tone(1250, 1130, 0.7) * env(int(SR * 0.7), 0.002, 0.16)
    out = np.zeros(int(SR * sec))
    out[: len(dry)] += dry
    for delay, gain in ((0.22, 0.32), (0.44, 0.14)):  # soft echoes
        d = int(SR * delay)
        out[d : d + len(dry)] += lowpass(dry, 1800) * gain
    return finish(out)


def blip():
    n = int(SR * 0.04)
    return finish(np.sin(2 * np.pi * 2400 * np.arange(n) / SR) * env(n, 0.001, 0.009))


def target_found():
    notes = [(784.0, 0.13), (1046.5, 0.13), (1568.0, 0.24)]  # G5 C6 G6
    out = np.zeros(int(SR * 0.52))
    pos = 0
    for f, dur in notes:
        n = int(SR * (dur + 0.06))
        t = np.arange(n) / SR
        v = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t)) * env(n, 0.003, 0.09)
        out[pos : pos + n] += v[: len(out) - pos]
        pos += int(SR * dur)
    return finish(out)


def motion_beep():
    n = int(SR * 0.12)
    t = np.arange(n) / SR
    sq = np.tanh(3.0 * np.sin(2 * np.pi * 1600 * t))  # soft-clipped, square-ish
    e = np.minimum(1.0, t / 0.002) * np.where(t < 0.09, 1.0, np.exp(-(t - 0.09) / 0.012))
    return finish(lowpass(sq, 5000) * e)


SOUNDS = {"scan_ping": scan_ping, "blip": blip, "target_found": target_found, "motion_beep": motion_beep}

if __name__ == "__main__":
    force = "--force" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SOUNDS.items():
        path = os.path.join(OUT, name + ".ogg")
        if os.path.exists(path) and not force:
            print("skip (exists):", name)
            continue
        data = fn()
        sf.write(path, data, SR, format="OGG", subtype="VORBIS")
        print(f"{name}.ogg  {len(data) / SR * 1000:.0f} ms  peak {20 * np.log10(np.abs(data).max()):.1f} dBFS")
