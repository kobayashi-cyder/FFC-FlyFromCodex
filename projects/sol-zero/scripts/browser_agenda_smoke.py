"""Browser smoke of persistent priority queue: explicit action, reload and chat UI."""
from pathlib import Path
from shutil import which
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'SolZero_Chat_Offline.html').read_text(encoding='utf-8')
MOCK="""() => {
 const data={};
 Object.defineProperty(window,'localStorage',{configurable:true,value:{
 getItem(k){return Object.hasOwn(data,k)?data[k]:null;},
 setItem(k,v){data[k]=String(v);},
 removeItem(k){delete data[k];}
 }});
}"""

def main():
 with sync_playwright() as pw:
  executable=which('chromium') or which('chromium-browser')
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'],
    **({'executable_path':executable} if executable else {}))
  page=browser.new_page(viewport={'width':390,'height':844},accept_downloads=True)
  errors=[]
  page.on('pageerror',lambda err:errors.append(str(err)))
  page.evaluate(MOCK)
  page.set_content(HTML,wait_until='load')
  getdata=lambda:page.evaluate('JSON.parse(localStorage.getItem("solzero_chat_v012_local"))')
  page.get_by_role('button',name='🐻 エージェント').click()
  goal=page.get_by_placeholder('例：タスク 実験結果を記録')
  goal.fill('タスク 低優先')
  page.locator('#panelBody select').select_option('low')
  page.get_by_role('button',name='目標をキューに追加').click()
  goal.fill('タスク 高優先')
  page.locator('#panelBody select').select_option('high')
  page.get_by_role('button',name='目標をキューに追加').click()
  state=getdata()
  assert len(state['agenda']['queue'])==2,state['agenda']
  assert not state['chats'][0]['tasks'],'queue add should not run any action'
  page.get_by_role('button',name='最大2件を承認・実行').click()
  state=getdata()
  assert [t['text'] for t in state['chats'][0]['tasks']]==['タスク 高優先','タスク 低優先'],state['chats'][0]['tasks']
  assert all(x['status']=='completed' for x in state['agenda']['queue']),state['agenda']
  assert len(state['agenda']['history'])>=4
  page.get_by_role('button',name='閉じる').click()
  page.set_content(HTML,wait_until='load')
  page.locator('#input').fill('/agent queue list')
  page.locator('#chatForm button[type="submit"]').click()
  assert '完了 2件' in page.locator('.message.assistant').last.inner_text()
  assert not errors,errors
  browser.close()
  print('PASS: priority queue, explicit approval, bounded two-goal run, persistent audit, reload, chat command')

if __name__=='__main__':
 main()
