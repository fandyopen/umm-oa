import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import {createRequire} from 'node:module'
import compiler from 'vue-template-compiler'
const require=createRequire(import.meta.url)
const XLSX=require('xlsx')
const read=path=>fs.readFileSync(new URL('../../'+path,import.meta.url),'utf8')
const load=(file,deps,names)=>{const source=read(file).replace(/^import .*$/gm,'').replace(/export /g,'');return new Function(...Object.keys(deps),source+';return {'+names.join(',')+'}')( ...Object.values(deps))}
const templatesModule=load('src/views/enterprise/hr/templates.js',{XLSX,saveAs:()=>{}},['templates','templateGroups','buildWorkbook','convertCell','suggestColumn'])
const {templates,templateGroups,buildWorkbook,convertCell,suggestColumn}=templatesModule
const shared=load('src/views/enterprise/hr/hrShared.js',{},['statusNames','eventNames'])
const reports=load('src/views/enterprise/hr/reports.js',shared,['payrollReportEntries','payrollSummary','changeRecords'])

test('all templates are downloadable in one valid workbook with instructions and empty data',()=>{
  const types=templateGroups.flatMap(g=>g.types)
  assert.equal(types.length,14)
  const book=buildWorkbook(types.map(type=>({type})))
  const restored=XLSX.read(XLSX.write(book,{type:'buffer',bookType:'xlsx'}),{type:'buffer',cellNF:true})
  assert.equal(restored.SheetNames.length,15)
  assert.ok(restored.SheetNames.includes('填写说明'))
  types.forEach((type,index)=>{
    const sheet=restored.Sheets[restored.SheetNames[index]]
    const filled=XLSX.utils.sheet_to_json(sheet,{header:1,defval:'',raw:true}).slice(1).filter(row=>row.some(value=>value!==''&&value!==null))
    assert.equal(filled.length,0)
    assert.equal(sheet.A1.v,templates[type].fields[0].label)
    templates[type].fields.forEach((field,c)=>{
      if(field.type==='text'||field.type==='date'){
        assert.equal(sheet[XLSX.utils.encode_cell({r:1,c})].z,'@')
        assert.equal(sheet[XLSX.utils.encode_cell({r:500,c})].z,'@')
      }
    })
  })
})
test('identity and banking numbers keep leading zeros; money retains numeric values',()=>{
  const book=buildWorkbook([{type:'payment',records:[{employeeNo:'000123',bankAccount:'001234567890123456789',netAmount:1234.56,paidAmount:0,remainingAmount:1234.56}]}])
  const restored=XLSX.read(XLSX.write(book,{type:'buffer',bookType:'xlsx'}),{type:'buffer'})
  const rows=XLSX.utils.sheet_to_json(restored.Sheets['数据']);assert.equal(rows[0]['员工编号'],'000123');assert.equal(rows[0]['银行卡号'],'001234567890123456789');assert.equal(rows[0]['实发金额'],1234.56)
})
test('DingTalk fields, Excel dates and explicit zeros are normalized without inventing missing values',()=>{
  assert.equal(suggestColumn(['工号','姓名'],templates.monthly.fields[0]),'工号')
  assert.equal(convertCell('2026年11月05日',{type:'date',label:'日期'}),'2026-11-05')
  const date=XLSX.SSF.parse_date_code(46331);assert.equal(convertCell(46331,{type:'date',label:'日期'}),`${date.y}-${String(date.m).padStart(2,'0')}-${String(date.d).padStart(2,'0')}`)
  assert.equal(convertCell(0,{type:'number',label:'金额'}),0);assert.equal(convertCell('',{type:'number',label:'金额'}),'');assert.throws(()=>convertCell('无效',{type:'number',label:'金额'}))
})
test('payroll roundtrip preserves version; reports separate failed and successful payment attempts',()=>{
  const batch={id:21,period:'2026-10',revision:3,rows:[{id:7,userId:2,baseAmount:1000,netAmount:1000,paidAmount:600,snapshot:{employeeNo:'0002',nickName:'测试人员',deptId:101,deptName:'研发部',bankAccount:'00123'},payments:[{id:1,amount:1000,status:'failed',referenceNo:'FAIL'},{id:2,amount:600,status:'paid',referenceNo:'OK',paymentDate:'2026-11-05'}]}]}
  const entries=reports.payrollReportEntries(batch)
  assert.equal(entries.length,5);assert.equal(entries[0].records[0].revision,3);assert.equal(entries[0].records[0].batchId,21)
  assert.equal(entries.find(e=>e.type==='paymentDetail').records.length,2)
  assert.equal(entries.find(e=>e.type==='payment').records[0].referenceNo,'OK')
  assert.equal(entries.find(e=>e.type==='departmentPayroll').records[0].remainingAmount,400)
  const book=buildWorkbook(entries);const grossCell=XLSX.utils.encode_cell({r:2,c:templates.payslip.fields.findIndex(f=>f.key==='grossAmount')});assert.ok(book.Sheets['员工工资明细'][grossCell].f.startsWith('SUM('))
  assert.equal(reports.changeRecords([{eventType:'regular',status:'effective'}])[0].eventType,'转正')
})
test('new Vue pages and reusable components compile without template errors',()=>{
  const files=['src/views/enterprise/hr/employees.vue','src/views/enterprise/hr/changes.vue','src/views/enterprise/hr/AttendanceWorkspace.vue','src/views/enterprise/hr/PayrollWorkspace.vue','src/views/enterprise/hr/self.vue','src/components/HrExcelImport/index.vue','src/components/HrTemplateDownloads/index.vue','src/components/HrApprovalDetail/index.vue']
  files.forEach(file=>{const parsed=compiler.parseComponent(read(file));assert.deepEqual(compiler.compile(parsed.template.content).errors,[],file)})
})
