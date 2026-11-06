"""Mobile Chromium media UI smoke on real local origin, including IndexedDB and ZIP."""
from pathlib import Path
from shutil import which
from playwright.sync_api import sync_playwright
from browser_server import serve_root

ROOT=Path(__file__).resolve().parents[1]

def run():
    with serve_root(ROOT) as origin, sync_playwright() as playwright:
        system_chromium=which('chromium') or which('chromium-browser')
        browser=playwright.chromium.launch(
            headless=True,args=['--no-sandbox','--autoplay-policy=no-user-gesture-required'],
            **({'executable_path':system_chromium} if system_chromium else {}))
        context=browser.new_context(viewport={'width':390,'height':820},is_mobile=True,has_touch=True,accept_downloads=True)
        page=context.new_page()
        errors=[]
        page.on('pageerror',lambda err:errors.append(str(err)))
        page.goto(origin+'SolZero_Chat_Offline.html',wait_until='load')
        page.locator('#studioBtn').click(force=True)
        page.wait_for_timeout(250)
        assert page.locator('#panelTitle').inner_text()=='メディア工房'
        for label in ['画像 PNG','音声 WAV','動画 GIF','音声付き動画 WebM']:
            page.get_by_role('button',name=label,exact=True).click()
            page.wait_for_function("() => document.querySelector('#panelBody .listentry')?.textContent.includes('生成完了：')",timeout=15000)
            page.wait_for_timeout(50)
        assert page.get_by_role('button',name='重みを設定').count()==1
        assert page.locator('#panelBody .listentry').count()>=5
        with page.expect_download() as pending:
            page.get_by_role('button',name='全素材をZIPで書き出し').click()
        assert pending.value.suggested_filename=='SolZero_media_project.zip'
        page.locator('#closePanel').click()
        page.locator('#input').fill('/image 海')
        page.locator('#send').click()
        page.wait_for_function("() => Array.from(document.querySelectorAll('.message.assistant .bubble')).some(e=>e.textContent.includes('生成しました'))",timeout=15000)
        assert '生成しました' in page.locator('.message.assistant .bubble').last.inner_text()
        send_box=page.locator('#send').bounding_box()
        assert send_box and send_box['height']>=44,send_box
        assert not errors,errors
        print('PASS: local-origin PNG/WAV/GIF/WebM + ZIP + WSP controls + mobile touch UI')
        browser.close()

if __name__=='__main__':
    run()
