#!/usr/bin/env python3
"""Retain prior synthetic state and evidence before a clean demo process starts."""
from pathlib import Path
import shutil, time
root=Path(__file__).resolve().parents[1]/'output'
root.mkdir(parents=True, exist_ok=True)
archive=root/'previous-runs'/str(time.time_ns())
for name in ('tpf-replay','runtime','evidence'):
    source=root/name
    if source.exists():
        archive.mkdir(parents=True,exist_ok=True)
        shutil.move(str(source),str(archive/name))
