#!/usr/bin/env python3
"""Source assertions complement the final DEX JournalAnrAudit and integration audit."""
from pathlib import Path
f=Path("src/jp/tapmarker/DiagnosticFrameCapture.java").read_text(encoding="utf-8")
j=Path("src/jp/tapmarker/DiagnosticJournal.java").read_text(encoding="utf-8")
required=[
 "if(journal.closed)return;",
 "if(!journal.rawCopies.tryAcquire())",
 "int[] thumbnail=thumbnail(pixels,width,height,size[0],size[1]);",
 "journal.worker.execute(()->encode(",
 "if(!queued)journal.rawCopies.release();",
 "static void encode(",
 "finally{",
 "journal.rawCopies.release();",
 "DiagnosticArchive.Frame frame=",
 "if(journal.closed||journal.lanes.get(app)!=lane)return;"
]
for token in required:
 if token not in f:raise AssertionError("DiagnosticFrameCapture missing: "+token)
if 'DiagnosticFrameCapture.frame(this,app,display,run,width,height,pixels,at,forced);' not in j:
 raise AssertionError("journal entrypoint must delegate without monitor")
if 'DiagnosticJournal'+chr(36)*2+'ExternalSyntheticLambda0' in f:
 raise AssertionError("old synthetic is not allowed in shipping capture")
# The first synchronized block must close before reading thumbnails.
start=f.index("synchronized(journal){",f.index("static void frame("))
thumb=f.index("int[] thumbnail=thumbnail(",start)
if thumb <= f.index("boolean queued=false;",start):
 raise AssertionError("thumbnail read inside journal lock")
print("DiagnosticFrameSourceTest: async capture, backpressure, snapshot, JPEG and permit invariants passed")
