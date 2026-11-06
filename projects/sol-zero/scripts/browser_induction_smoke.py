"""Mobile Chromium WSP persistence and v0.10 migration smoke against a real HTTP origin."""
from pathlib import Path
from shutil import which
from playwright.sync_api import sync_playwright
from browser_server import serve_root

ROOT = Path(__file__).resolve().parents[1]

def main():
    with serve_root(ROOT) as origin, sync_playwright() as pw:
        system_chromium=which('chromium') or which('chromium-browser')
        browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'],
                                   **({'executable_path':system_chromium} if system_chromium else {}))
        page=browser.new_page(viewport={'width':390,'height':844},is_mobile=True,has_touch=True,accept_downloads=True)
        errors=[]
        js_exceptions=[]
        session=page.context.new_cdp_session(page)
        session.send('Runtime.enable')
        session.on('Runtime.exceptionThrown',lambda event: js_exceptions.append(event['exceptionDetails']))
        page.on('pageerror',lambda err: errors.append(str(err)))
        url=origin+'SolZero_Chat_Offline.html'
        page.goto(url,wait_until='load')
        old={'schema':3,'chats':[{'id':'carry','title':'以前の会話','created':'2026-11-06T00:00:00Z',
              'messages':[{'role':'user','text':'旧データは残ります','at':'2026-11-06T00:00:00Z'}]}],
              'current':'carry','knowledge':[],'documents':[],'theme':'light'}
        page.evaluate("""data => {
          localStorage.clear();
          localStorage.setItem('solzero_chat_v010_local', JSON.stringify(data));
        }""",old)
        page.reload(wait_until='load')
        print('DIAGNOSTIC', page.evaluate("""() => ({
          title:document.title,
          scriptCount:document.scripts.length,
          history:document.querySelector('#history')?.textContent.slice(0,300),
          messages:document.querySelector('#messages')?.textContent.slice(0,300),
          storageKeys:Object.keys(localStorage),
          storedNew:localStorage.getItem('solzero_chat_v012_local')?.slice(0,120),
          storedOld:localStorage.getItem('solzero_chat_v010_local')?.slice(0,120)
        })"""), 'PAGE_ERRORS', errors, 'CHROMIUM', browser.version, 'JS_EXCEPTION', js_exceptions, flush=True)
        page.get_by_text('旧データは残ります').wait_for(timeout=10000)
        assert page.get_by_text('旧データは残ります').count()==1
        page.get_by_role('button',name='帰納WSP',exact=True).click()
        page.get_by_role('button',name='① 候補回路を合成して独立検証').click()
        report=page.locator('#panelBody .listentry').last.inner_text()
        assert '未使用評価 5/5' in report,report
        page.get_by_role('button',name='② 合格回路だけ採用').click()
        applied=page.locator('#panelBody .listentry').last.inner_text()
        assert '端末へ保存済み' in applied,applied
        page.get_by_role('button',name='現在の回路を実行').click()
        assert '"out":[13]' in page.locator('#panelBody .listentry').last.inner_text()
        page.reload(wait_until='load')
        page.locator('#input').fill('/induce 2,3')
        page.locator('#chatForm button[type="submit"]').click()
        assert '数値出力：13' in page.locator('.message.assistant .bubble').last.inner_text()
        assert page.get_by_text('旧データは残ります').count()==1
        page.get_by_role('button',name='帰納WSP',exact=True).click()
        page.get_by_role('button',name='採用前の回路へ戻す').click()
        result=page.locator('#panelBody .listentry').last.inner_text()
        assert '前の回路へ復元' in result,result
        page.get_by_role('button',name='閉じる').click()
        page.locator('#input').fill('/induce 1,0')
        page.locator('#chatForm button[type="submit"]').click()
        assert '数値出力：1' in page.locator('.message.assistant .bubble').last.inner_text()
        # Two synchronous submits for an async media command must produce one reply.
        page.locator('#input').fill('/image 海')
        page.evaluate("""() => {
          const form=document.querySelector('#chatForm');
          for(let i=0;i<2;i++)form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        }""")
        page.wait_for_function("() => Array.from(document.querySelectorAll('.message.assistant .bubble')).some(x=>x.textContent.includes('を生成しました'))",timeout=15000)
        assert page.locator('.message.user .bubble').filter(has_text='/image 海').count()==1
        assert page.locator('#send').is_enabled()
        assert not page.locator('#input').evaluate('(element) => element.readOnly')
        page.locator('#input').fill('こんにちは')
        page.locator('#chatForm button[type="submit"]').click()
        assert page.locator('.message.user .bubble').filter(has_text='こんにちは').count()==1
        # Oversized message is rejected without disappearing from the draft.
        # textarea has maxlength=3000; test programmatic/DOM over-limit input too.
        page.evaluate("""() => {
          const input=document.querySelector('#input');input.value='あ'.repeat(3001);
          document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        }""")
        assert '最大3000文字' in page.locator('#toast').inner_text()
        assert len(page.locator('#input').input_value())==3001
        assert not errors,errors
        print('PASS: v0.10 HTTP migration; WSP induction 5/5; save/reload; chat; rollback; no JS errors')
        browser.close()

if __name__=='__main__':
    main()
