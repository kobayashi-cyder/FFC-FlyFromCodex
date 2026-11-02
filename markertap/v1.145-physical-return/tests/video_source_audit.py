#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
patch=(root/'tools/PatchPhysicalReturn.java').read_text()
bridge=(root/'src/jp/tapmarker/KaitakuVideoNamedControls.java').read_text()
policy=(root/'src/jp/tapmarker/KaitakuVideoModalPolicy.java').read_text()
assert 'c.getType().equals(ROOT+"KaitakuUiEvidence;")' in patch
assert '"KaitakuNamedControls;","apply",ROOT+"KaitakuVideoNamedControls;","apply"' in patch
assert 'if(calls[0]!=1)' in patch
assert 'ROOT+"KaitakuUiEvidence;"' in patch
assert 'KaitakuNamedControls.clear(out)' in bridge
assert 'KaitakuNamedControls.apply(e,context,controls,out,w,h)' in bridge
assert 'KaitakuActionEvidence.put(out,"close",d.bounds)' in bridge
assert 'KaitakuVideoModalPolicy.Result.HOLD' not in bridge or 'Result.NONE' in bridge
assert '"コインアップチャンス"' in policy
assert '"1タップで進む量が2倍"' in policy
assert 'CMを見て30ゲット' not in bridge
assert 'KaitakuActionPacer.allow' in bridge
assert 'KaitakuActionPacer.reset' in bridge
assert 'private static final long[] RETRY_MS' in (root/'src/jp/tapmarker/KaitakuActionPacer.java').read_text()
assert 'ROOT+"SplitPilot;"' in patch
assert 'ROOT+"VirtualFastWait;"' in patch
assert 'if(calls[0]!=1)' in patch
assert 'return Math.min(1200,Math.max(goal,effectiveWork))' in (root/'src/jp/tapmarker/VirtualFastWait.java').read_text()
print('Kaitaku video DEX hook and no-ad action source audit: 18 checks passed')
