import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url)
const XLSX=require('xlsx')
const origin=process.env.HR_SMOKE_ORIGIN||'http://localhost:3000'
const debugOrigin=process.env.HR_SMOKE_BROWSER||'http://127.0.0.1:9223'
const output=path.resolve('back/logs/hr-browser')
fs.mkdirSync(output,{recursive:true})
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const login=await fetch('http://localhost:8080/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.HR_SMOKE_USERNAME,password:process.env.HR_SMOKE_PASSWORD})}).then(r=>r.json())
assert.equal(login.code,200,'local login failed')
const targets=await fetch(debugOrigin+'/json').then(r=>r.json())
const socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true})})
let next=0
const pending=new Map(), errors=[]
socket.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id){const handler=pending.get(message.id);if(handler){clearTimeout(handler.timer);pending.delete(message.id);if(message.error)handler.reject(new Error(message.error.message));else handler.resolve(message.result)}}else if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails.exception?.description||message.params.exceptionDetails.text)})
function call(method,params={}){return new Promise((resolve,reject)=>{const id=++next;pending.set(id,{resolve,reject,timer:setTimeout(()=>{pending.delete(id);reject(new Error('CDP timeout: '+method))},20000)});socket.send(JSON.stringify({id,method,params}))})}
async function evaluate(expression){const result=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.text);return result.result.value}
async function until(expression){for(let i=0;i<100;i++){if(await evaluate(expression))return;await delay(300)}throw new Error('page did not become ready: '+expression)}
await call('Runtime.enable');await call('Page.enable')
try{
  await call('Page.navigate',{url:origin+'/qs/user/login'});await until(`location.origin === ${JSON.stringify(origin)} && document.readyState !== 'loading' && Boolean(document.body)`)
  await evaluate(`localStorage.setItem('access_token',${JSON.stringify(JSON.stringify(login.token))})`)
  const pages=[['/hr/employees','员工档案'],['/hr/changes','人事异动与交接'],['/hr/attendance','员工考勤表'],['/hr/payroll','工资核算与审批'],['/finance/payroll','工资发放与对账'],['/hr/self?tab=salary','我的工资条']]
  const results=[]
  for(const [route,title] of pages){
    await call('Page.navigate',{url:origin+'/qs'+route})
    await until(`document.body && document.body.innerText.includes(${JSON.stringify(title)})`)
    await delay(500)
    const screenshot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false})
    fs.writeFileSync(path.join(output,route.split('?')[0].replaceAll('/','_')+'.png'),Buffer.from(screenshot.data,'base64'))
    results.push({route,title,ready:true})
  }
  await call('Browser.setDownloadBehavior',{behavior:'allow',downloadPath:output,eventsEnabled:true})
  await call('Page.navigate',{url:origin+'/qs/hr/employees'});await until(`document.body.innerText.includes('模板与报表格式')`)
  await evaluate(`Array.from(document.querySelectorAll('button')).find(button=>button.innerText.includes('模板与报表格式')).click()`);await delay(400)
  const downloadStarted=Date.now()
  await evaluate(`Array.from(document.querySelectorAll('.ant-dropdown-menu-item')).find(item=>item.innerText.includes('下载全部模板')).click()`)
  let file
  for(let i=0;i<60;i++){file=fs.readdirSync(output).find(name=>name.endsWith('.xlsx')&&fs.statSync(path.join(output,name)).mtimeMs>=downloadStarted);if(file)break;await delay(300)}
  assert.ok(file,'downloaded workbook missing')
  const book=XLSX.readFile(path.join(output,file),{cellNF:true});assert.equal(book.SheetNames.length,15)
  assert.equal(book.Sheets[book.SheetNames[0]].A2.z,'@','blank employee number column must use text format')
  await call('Emulation.setDeviceMetricsOverride',{width:820,height:1000,deviceScaleFactor:1,mobile:false});await delay(350)
  const screenshot=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(output,'employees-narrow.png'),Buffer.from(screenshot.data,'base64'))
  assert.deepEqual(errors,[],'uncaught browser errors')
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({pages:results,templateSheets:book.SheetNames.length,uncaughtErrors:errors},null,2))
  process.stdout.write(JSON.stringify({pages:results,templateSheets:book.SheetNames.length,uncaughtErrors:errors.length}))
}finally{
  try{await evaluate(`localStorage.removeItem('access_token')`)}catch(error){}
  try{await call('Browser.close')}catch(error){}
  socket.close()
}
