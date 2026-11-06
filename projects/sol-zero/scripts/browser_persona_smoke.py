"""Mobile UI smoke for persona agent; uses controlled localStorage and no-network single-file bundle."""
from pathlib import Path
from shutil import which
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'SolZero_Chat_Offline.html').read_text(encoding='utf-8')
MOCK_STORAGE="""() => {
 const values={};
 Object.defineProperty(window,'localStorage',{configurable:true,value:{
 getItem(k){return Object.hasOwn(values,k)?values[k]:null;},
 setItem(k,v){values[k]=String(v);},
 removeItem(k){delete values[k];},clear(){for(const k of Object.keys(values))delete values[k];}
 }});
}"""

def main():
 with sync_playwright() as pw:
  system_chromium=which('chromium') or which('chromium-browser')
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'],
      **({'executable_path':system_chromium} if system_chromium else {}))
  page=browser.new_page(viewport={'width':390,'height':844})
  errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.evaluate(MOCK_STORAGE)
  page.set_content(HTML,wait_until='load')
  page.get_by_role('button',name='🐻 エージェント').click()
  page.get_by_placeholder('例：青い海の画像を作成').fill('タスク 実験記録を整理')
  page.get_by_role('button',name='計画を作成').click()
  assert '状態: planned' in page.locator('#panelBody').inner_text()
  # Creating the plan by itself must not touch the user's task list.
  def snapshot():
   return page.evaluate('JSON.parse(localStorage.getItem("solzero_chat_v012_local"))')
  assert snapshot()['chats'][0]['tasks']==[]
  page.get_by_role('button',name='1手実行').click()
  assert '目的を分析 [done]' in page.locator('#panelBody').inner_text()
  page.get_by_role('button',name='最大3手を承認・実行').click()
  state=snapshot()
  assert state['agent']['active']['status']=='completed',state['agent']
  assert state['chats'][0]['tasks'][0]['text']=='タスク 実験記録を整理'
  assert state['voiceEnabled'] is False
  page.get_by_role('button',name='音声を切替').click()
  assert snapshot()['voiceEnabled'] is True
  page.get_by_role('button',name='音声を切替').click()
  assert snapshot()['voiceEnabled'] is False
  page.get_by_role('button',name='閉じる').click()
  page.locator('#input').fill('こんにちは')
  page.locator('#chatForm button[type="submit"]').click()
  assert 'SolZero案内役' in page.locator('.message.assistant').last.inner_text()
  page.locator('#input').fill('/agent status')
  page.locator('#chatForm button[type="submit"]').click()
  assert '状態: completed' in page.locator('.message.assistant').last.inner_text()
  assert not errors,errors
  browser.close()
  print('PASS: agent panel, planning safety, 1-step, bounded run, task persistence, voice opt-in, chat commands')

if __name__=='__main__':
 main()
