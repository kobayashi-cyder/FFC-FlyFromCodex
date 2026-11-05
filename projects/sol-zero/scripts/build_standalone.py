"""Create a self-contained chat HTML file, without any downloads or external JS."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ui = (ROOT / 'chat_web' / 'index.html').read_text(encoding='utf-8')
engine = (ROOT / 'chat_web' / 'engine.js').read_text(encoding='utf-8')
learned_router = (ROOT / 'chat_web' / 'learned_router.js').read_text(encoding='utf-8')
app = (ROOT / 'chat_web' / 'app.js').read_text(encoding='utf-8')
bigram = (ROOT / 'chat_web' / 'bigram.js').read_text(encoding='utf-8')
wsp_bulk = (ROOT / 'chat_web' / 'wsp_bulk.js').read_text(encoding='utf-8')
wsp_flow = (ROOT / 'chat_web' / 'wsp_flow.js').read_text(encoding='utf-8')
wsp_auto = (ROOT / 'chat_web' / 'wsp_autodesign.js').read_text(encoding='utf-8')
wsp_graph = (ROOT / 'chat_web' / 'wsp_graph.js').read_text(encoding='utf-8')
wsp_factory = (ROOT / 'chat_web' / 'wsp_factory.js').read_text(encoding='utf-8')
bigram = re.sub(r'\bexport (const|class|function)\b', r'\1', bigram)
wsp_bulk = re.sub(r'\bexport (const|class|function)\b', r'\1', wsp_bulk)
wsp_flow = re.sub(r'\bexport (const|class|function)\b', r'\1', wsp_flow)
wsp_auto = re.sub(r'\bexport (const|class|function)\b', r'\1', wsp_auto)
wsp_auto = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_bulk\.js['\"];?\s*$", '', wsp_auto, flags=re.MULTILINE)
wsp_graph = re.sub(r'\bexport (const|class|function)\b', r'\1', wsp_graph)
wsp_factory = re.sub(r'\bexport (const|class|function)\b', r'\1', wsp_factory)
wsp_factory = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_graph\.js['\"];?\s*$", '', wsp_factory, flags=re.MULTILINE)
learned_router = re.sub(r'\bexport (const|class|function)\b', r'\1', learned_router)
engine = re.sub(r'^import\s+\{[^\n]*?\}\s+from\s+[\"\']\./learned_router\.js[\"\'];?\s*$', '', engine, flags=re.MULTILINE)
engine = re.sub(r'\bexport (const|class|function)\b', r'\1', engine)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./engine\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./bigram\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_bulk\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_flow\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_autodesign\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_graph\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
app = re.sub(r"^import\s+\{[^\n]*?\}\s+from\s+['\"]\./wsp_factory\.js['\"];?\s*$", '', app, flags=re.MULTILINE)
assert not re.search(r'(?<![a-z])import\s+\{', app)
assert '</script' not in (engine + bigram + wsp_bulk + wsp_flow + wsp_auto + wsp_factory + app).lower()
bundle = ui.replace('<script type="module" src="./app.js"></script>',
                    '<script type="module">\n' + learned_router + '\n' + engine + '\n' + bigram + '\n' + wsp_bulk + '\n' + wsp_flow + '\n' + wsp_auto + '\n' + wsp_graph + '\n' + wsp_factory + '\n' + app + '\n</script>')
assert 'src="./app.js"' not in bundle
path = ROOT / 'SolZero_Chat_Offline.html'
path.write_text(bundle, encoding='utf-8')
print(f'Created {path}: {path.stat().st_size} bytes')
