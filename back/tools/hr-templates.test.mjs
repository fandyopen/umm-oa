import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import {createRequire} from 'node:module'
import compiler from 'vue-template-compiler'
const require=createRequire(import.meta.url)
const XLSX=require('xlsx')
const read=path=>fs.readFileSync(new URL('../../'+path,import.meta.url),'utf8')
const load=(file,deps,names)=>{const source=read(file).replace(/^import .*$/gm,'').replace(/export /g,'');return new Function(...Object.keys(deps),source+';return {'+names.join(',')+'}')( ...Object.values(deps))}
const catalog=JSON.parse(read('back/src/main/resources/templates/hr/catalog.json'))
const templatesModule=load('src/views/enterprise/hr/templates.js',{XLSX,Vue:require('vue'),saveAs:()=>{},getHrTemplateCatalog:async()=>({data:catalog}),downloadFixedTemplate:()=>{}},['templateState','loadTemplateCatalog','buildWorkbook','convertCell','suggestColumn'])
await templatesModule.loadTemplateCatalog()
const {buildWorkbook,convertCell,suggestColumn}=templatesModule
const {templates,groups:templateGroups}=catalog
const shared=load('src/views/enterprise/hr/hrShared.js',{},['statusNames','eventNames'])
const reports=load('src/views/enterprise/hr/reports.js',shared,['payrollReportEntries','payrollSummary','changeRecords'])

test('all templates are downloadable in one valid workbook with instructions and empty data',()=>{
  const types=templateGroups.flatMap(g=>g.types)
  assert.deepEqual([...types].sort(),Object.keys(templates).sort())
  assert.equal(types.length,15)
  const restored=XLSX.readFile(new URL('../src/main/resources/templates/hr/all.xlsx',import.meta.url),{cellNF:true,sheetStubs:true})
  assert.equal(restored.SheetNames.length,16)
  assert.ok(restored.SheetNames.includes('填写说明'))
  types.forEach((type,index)=>{
    const single=XLSX.readFile(new URL('../src/main/resources/templates/hr/'+type+'.xlsx',import.meta.url),{cellNF:true,sheetStubs:true})
    assert.deepEqual(single.SheetNames,['数据','填写说明'])
    const singleSheet=single.Sheets['数据']
    assert.deepEqual(XLSX.utils.sheet_to_json(singleSheet,{header:1})[0],templates[type].fields.map(f=>f.label))
    const sheet=restored.Sheets[restored.SheetNames[index]]
    assert.deepEqual(XLSX.utils.sheet_to_json(sheet,{header:1})[0],templates[type].fields.map(f=>f.label))
    const filled=XLSX.utils.sheet_to_json(sheet,{header:1,defval:'',raw:true}).slice(1).filter(row=>row.some(value=>value!==''&&value!==null))
    assert.equal(filled.length,0)
    assert.equal(sheet.A1.v,templates[type].fields[0].label)
    templates[type].fields.forEach((field,c)=>{
      if(field.type==='text'||field.type==='date'){
        assert.equal(sheet[XLSX.utils.encode_cell({r:1,c})].z,'@')
        assert.equal(sheet[XLSX.utils.encode_cell({r:500,c})].z,'@')
        assert.equal(singleSheet[XLSX.utils.encode_cell({r:1,c})].z,'@')
        assert.equal(singleSheet[XLSX.utils.encode_cell({r:500,c})].z,'@')
      }
    })
  })
})
test('identity and banking numbers keep leading zeros; money retains numeric values',()=>{
  const book=buildWorkbook([{type:'payment',records:[{employeeNo:'000123',bankAccount:'001234567890123456789',netAmount:1234.56,paidAmount:0,remainingAmount:1234.56}]}])
  const restored=XLSX.read(XLSX.write(book,{type:'buffer',bookType:'xlsx'}),{type:'buffer'})
  const rows=XLSX.utils.sheet_to_json(restored.Sheets['数据']);assert.equal(rows[0]['员工编号'],'000123');assert.equal(rows[0]['银行卡号'],'001234567890123456789');assert.equal(rows[0]['实发金额'],1234.56)
})
test('salary exports can be imported with the same headers and raw pay types',()=>{
  for (const payType of ['monthly','daily']) {
    const record={employeeNo:'000012',employeeName:'测试',effectiveDate:'2026-11-01',payType,baseAmount:120,allowance:0,reason:'测试调薪'}
    const book=buildWorkbook([{type:'salaryReport',records:[record]}])
    const restored=XLSX.read(XLSX.write(book,{type:'buffer',bookType:'xlsx'}),{type:'buffer'})
    const matrix=XLSX.utils.sheet_to_json(restored.Sheets['数据'],{header:1})
    for (const field of templates.salary.fields) {
      const header=suggestColumn(matrix[0],field)
      assert.ok(header)
      const actual=convertCell(matrix[1][matrix[0].indexOf(header)],field)
      if (record[field.key]!==undefined) assert.equal(actual,record[field.key])
    }
  }
})
test('member template is empty, matches its catalog and protects phone text formatting',()=>{
  const schema=JSON.parse(read('back/src/main/resources/templates/members/catalog.json'))
  const book=XLSX.readFile(new URL('../src/main/resources/templates/members/address-book.xlsx',import.meta.url),{cellNF:true,sheetStubs:true})
  assert.deepEqual(book.SheetNames,['数据','填写说明'])
  const sheet=book.Sheets['数据']
  assert.deepEqual(XLSX.utils.sheet_to_json(sheet,{header:1})[0],schema.fields.map(f=>f.label))
  assert.equal(XLSX.utils.sheet_to_json(sheet).length,0)
  assert.equal(sheet.B2.z,'@');assert.equal(sheet.B501.z,'@')
  assert.ok(!schema.fields.some(f=>['password','roleIds','tenantId_'].includes(f.key)))
})
test('catalog loading deduplicates concurrent requests and permits retry after failure',async()=>{
  let calls=0
  const module=load('src/views/enterprise/hr/templates.js',{XLSX,Vue:require('vue'),saveAs:()=>{},getHrTemplateCatalog:async()=>{calls++;if(calls===1)throw new Error('测试失败');return {data:catalog}},downloadFixedTemplate:()=>{}},['templateState','loadTemplateCatalog','getTemplateSchema'])
  assert.deepEqual(module.getTemplateSchema('salary').fields,[])
  const pending=module.loadTemplateCatalog()
  assert.equal(module.loadTemplateCatalog(),pending)
  await assert.rejects(pending,/测试失败/)
  assert.equal(module.templateState.ready,false)
  await module.loadTemplateCatalog()
  assert.equal(module.getTemplateSchema('salary').name,templates.salary.name)
  await module.loadTemplateCatalog()
  assert.equal(calls,2)
})
test('customer and recruitment templates match backend fields and preserve text identifiers after dynamic lookup is appended',()=>{
  const business=JSON.parse(read('back/src/main/resources/templates/business/catalog.json'))
  for(const [type,schema] of Object.entries(business.templates)) {
    const book=XLSX.readFile(new URL('../src/main/resources/templates/business/'+type+'.xlsx',import.meta.url),{cellNF:true,cellStyles:true,sheetStubs:true})
    assert.deepEqual(XLSX.utils.sheet_to_json(book.Sheets['数据'],{header:1})[0],schema.fields.map(f=>f.label))
    assert.equal(XLSX.utils.sheet_to_json(book.Sheets['数据']).length,0)
    let restored=book
    if(type!=='customer') {
      const source=read('src/views/eos/recruitment/components/RecruitmentWorkspace.vue')
      const preserve=source.slice(source.indexOf('        const data = wb.Sheets[wb.SheetNames[0]]'),source.indexOf('        const lookup =',source.indexOf('    async downloadTemplate()')))
      new Function('XLSX','wb',preserve)(XLSX,book)
      XLSX.utils.book_append_sheet(book,XLSX.utils.json_to_sheet([{类型:'负责人',ID:'000012',名称:'测试人员'}]),'可选编号')
      restored=XLSX.read(XLSX.write(book,{type:'buffer',bookType:'xlsx'}),{type:'buffer',cellNF:true,sheetStubs:true})
    }
    schema.fields.forEach((f,index)=>{
      if(f.type==='text') assert.equal(restored.Sheets['数据'][XLSX.utils.encode_cell({r:500,c:index})].z,'@')
    })
    if(type!=='customer') assert.equal(XLSX.utils.sheet_to_json(restored.Sheets['可选编号'])[0].ID,'000012')
  }
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
