# Frontend API Inventory

Generated from: `src/api`

Endpoints: **722**

| Module | Function | Method | URL | Body | Params |
| --- | --- | --- | --- | --- | --- |
| activiti/definition | convert2Model | POST | `/activiti/definition/convert2Model` |  | yes |
| activiti/definition | listDefinition | GET | `/activiti/definition/list` |  | yes |
| activiti/definition | delDefinition | DELETE | `/activiti/definition/remove/{dynamic}` |  |  |
| activiti/definition | suspendOrActiveDefinition | POST | `/activiti/definition/suspendOrActiveDefinition` |  | yes |
| activiti/modeler | addModeler | POST | `/activiti/modeler/create` |  | yes |
| activiti/modeler | deployModeler | GET | `/activiti/modeler/deploy/{dynamic}` |  |  |
| activiti/modeler | exportModeler | GET | `/activiti/modeler/export/{dynamic}` |  |  |
| activiti/modeler | listModeler | GET | `/activiti/modeler/list` |  | yes |
| activiti/modeler | delModeler | DELETE | `/activiti/modeler/remove/{dynamic}` |  |  |
| activiti/process | listFlowElements | GET | `/activiti/process/listFlowElements` |  | yes |
| activiti/process | listFlowElementsValid | GET | `/activiti/process/listFlowElementsValid` |  | yes |
| activiti/process | listFlowIds | GET | `/activiti/process/listFlowIds` |  | yes |
| activiti/process | listHistory | POST | `/activiti/process/listHistory` | yes |  |
| activiti/process | findProcessBusInfo | GET | `/project/{dynamic}/{dynamic}` |  |  |
| activiti/process | rejectTask | POST | `/project/activity/back` | yes |  |
| activiti/process | completeTask | POST | `/project/activity/complete` | yes |  |
| activiti/process | finishTask | POST | `/project/activity/end/process` | yes |  |
| activiti/process | findProcessStarted | GET | `/project/activity/started` |  | yes |
| activiti/process | findTaskCopyList | GET | `/project/activity/taskCopyList` |  | yes |
| activiti/process | listDone | GET | `/project/activity/taskDoneList` |  | yes |
| activiti/process | listTodo | GET | `/project/activity/taskWaitList` |  | yes |
| activiti/workflow | addWorkflow | POST | `/activiti/workflow` | yes |  |
| activiti/workflow | updateWorkflow | PUT | `/activiti/workflow` | yes |  |
| activiti/workflow | delWorkflow | DELETE | `/activiti/workflow/{dynamic}` |  |  |
| activiti/workflow | getWorkflow | GET | `/activiti/workflow/{dynamic}` |  |  |
| activiti/workflow | exportWorkflow | GET | `/activiti/workflow/export` |  | yes |
| activiti/workflow | listWorkflow | GET | `/activiti/workflow/list` |  | yes |
| activiti/workflow | updateReadUsers | GET | `/activiti/workflow/read/{dynamic}/{dynamic}/{dynamic}` |  |  |
| dashboard/Employee | ProjectProgressOfEmployee | GET | `/project/analysis/project/staff/project/process` |  | yes |
| dashboard/Employee | projectChart | GET | `/project/analysis/project/staff/project/type` |  | yes |
| dashboard/Employee | employeesTitle | GET | `/project/analysis/project/top/staff` |  | yes |
| dashboard/PM | PMProjectTable | GET | `/project/analysis/project/purchase/table` |  | yes |
| dashboard/PM | PMTopInfo | GET | `/project/analysis/project/purchase/top` |  | yes |
| dashboard/PM | PMProjectGroup | GET | `/project/analysis/project/purchase/type` |  | yes |
| dashboard/PM | WareHouseIndex | GET | `/project/warehouse/statistic/byProject` |  |  |
| dashboard/PM | PMProjectType | GET | `project/analysis/project/purchase` |  | yes |
| dashboard/SalesExecutive | opportunitiesToFollowUp | GET | `/project/analysis/business/sale` |  | yes |
| dashboard/SalesExecutive | RevenueContractInvoicing | GET | `/project/analysis/project/sale/invoice` |  | yes |
| dashboard/SalesExecutive | projectPayment | GET | `/project/analysis/project/sale/total?projectName` |  | yes |
| dashboard/SalesExecutive | infoRmation | GET | `/project/analysis/top/sale` |  | yes |
| dashboard/SalesExecutive | ProjectStatusClassiFication | GET | `project/analysis/project/sale` |  | yes |
| enterprise/index | getAppSystemList | GET | `/app/system/list` |  | yes |
| enterprise/index | addMenuPerm | POST | `/app/user/addModule` | yes |  |
| enterprise/index | deleteAppItem | DELETE | `/app/user/delete/{dynamic}` |  |  |
| enterprise/index | getAppList | GET | `/app/user/list` |  | yes |
| enterprise/index | queryAppByTenantAppId | GET | `/app/user/module/{dynamic}` |  |  |
| enterprise/index | getModuleByPath | GET | `/app/user/module/path` |  | yes |
| enterprise/index | createdNewApp | POST | `/app/user/self/app` | yes |  |
| enterprise/index | updateApp | PUT | `/app/user/update` | yes |  |
| enterprise/index | getUserInfo | GET | `/crm/user/get_user_info` |  |  |
| enterprise/index | addAdminStstemGroup | POST | `/system/admin/addAdmin` | yes |  |
| enterprise/index | addAdminGroup | POST | `/system/admin/addGroup` | yes |  |
| enterprise/index | queryNormalAdminDetail | GET | `/system/admin/adminGroup/{dynamic}` |  |  |
| enterprise/index | editGroupName | POST | `/system/admin/edit` | yes |  |
| enterprise/index | queryStstemAdmin | GET | `/system/admin/query/admin` |  |  |
| enterprise/index | queryAdminGroup | GET | `/system/admin/query/adminGroup` |  |  |
| enterprise/index | adminGroupDelete | DELETE | `/system/admin/remove/{dynamic}` |  |  |
| enterprise/index | adminUpdatePre | POST | `/system/admin/update` | yes |  |
| enterprise/index | addSystemDepchild | POST | `/system/dept` | yes |  |
| enterprise/index | editDeptName | PUT | `/system/dept` | yes |  |
| enterprise/index | addDeptPrincipal | GET | `/system/dept/add/principal` |  | yes |
| enterprise/index | addDeptPrincipalLeader | GET | `/system/dept/add/principal/leader` |  | yes |
| enterprise/index | adjustDeptUser | GET | `/system/dept/addUser` |  | yes |
| enterprise/index | getTreeMenu | GET | `/system/dept/admin/tree` |  | yes |
| enterprise/index | deptLeaderList | GET | `/system/dept/dept/leader` |  | yes |
| enterprise/index | deptPrincipalList | GET | `/system/dept/dept/principal` |  | yes |
| enterprise/index | deptsort | GET | `/system/dept/sort` |  | yes |
| enterprise/index | getRoleListClass | GET | `/system/role_group/group_list` |  | yes |
| enterprise/index | updateRoleGroup | POST | `/system/role_group/group_update` | yes |  |
| enterprise/index | addRoleinMember | POST | `/system/role/add_member` | yes |  |
| enterprise/index | getRoleByAdmin | GET | `/system/role/admin/list` |  |  |
| enterprise/index | insertRole | POST | `/system/role/insert` | yes |  |
| enterprise/index | getRoleMemberList | GET | `/system/role/list_member` |  | yes |
| enterprise/index | deleteRoleinMember | DELETE | `/system/role/remove_member` | yes |  |
| enterprise/index | updateRole | POST | `/system/role/update` | yes |  |
| enterprise/index | addRole | POST | `/system/user/add` | yes |  |
| enterprise/index | addUserDismiss | GET | `/system/user/dismiss` |  | yes |
| enterprise/index | getDismissList | GET | `/system/user/dismiss_list` |  | yes |
| enterprise/index | roleImport | POST | `/system/user/excel_import` | yes |  |
| enterprise/index | getUserList | GET | `/system/user/list` |  | yes |
| enterprise/index | queryUserList | GET | `/system/user/list` |  | yes |
| eos/analysis/analyst | businessSale | GET | `/project/analysis/business/sale` |  | yes |
| eos/analysis/analyst | firstInOut | GET | `/project/analysis/first/in/out` |  | yes |
| eos/analysis/analyst | statistics | GET | `/project/analysis/first/page` |  | yes |
| eos/analysis/analyst | managerCost | GET | `/project/analysis/manager/cost` |  | yes |
| eos/analysis/analyst | subject | GET | `/project/contract/subject/analysis` |  | yes |
| eos/analysis/analyst | subjectExport | GET | `/project/contract/subject/analysis/export` |  | yes |
| eos/analysis/analyst | subjectCtc | GET | `/project/contract/subject/FindSubjectCtc` |  | yes |
| eos/analysis/analyst | subjectCtcExport | GET | `/project/contract/subject/FindSubjectCtc/export` |  | yes |
| eos/analysis/analyst | AnalystProjectFundPie | GET | `/project/fund/analystFund` |  | yes |
| eos/analysis/analyst | AnalystProjectFundTotal | GET | `/project/fund/analystFundTotal` |  | yes |
| eos/analysis/analyst | AnalystProjectFundIn | GET | `/project/income/analystFundIn` |  | yes |
| eos/analysis/analyst | AnalystProjectLabor | GET | `/project/labor/analystLabor` |  | yes |
| eos/analysis/analyst | AnalystProjectLaborPie | GET | `/project/labor/analystLaborPie` |  | yes |
| eos/analysis/analyst | AnalystProjectLaborTotal | GET | `/project/labor/analystTotalLabor` |  | yes |
| eos/analysis/analyst | AnalystProjectLoan | GET | `/project/loan/total/loanAnalyst` |  | yes |
| eos/analysis/analyst | AnalystProjectFundOut | GET | `/project/outcome/analystFundOut` |  | yes |
| eos/analysis/analyst | analysisInOutCome | GET | `/project/project/analysis/income` |  | yes |
| eos/analysis/analyst | analysisInComeLeft | GET | `/project/project/analysis/income/left` |  | yes |
| eos/analysis/analyst | analystList | GET | `/project/project/analyst` |  | yes |
| eos/analysis/analyst | ProjectRegion | GET | `/project/project/analyst/project/region` |  | yes |
| eos/analysis/analyst | ProjectType | GET | `/project/project/analyst/project/type` |  | yes |
| eos/analysis/analyst | AnalystProjectPaymentOut | GET | `/project/project/analyst/realOut` |  | yes |
| eos/analysis/analyst | AnalystProjectPaymentTotal | GET | `/project/project/analyst/total` |  | yes |
| eos/analysis/analyst | companyLaborBie | GET | `/project/project/company/area/bie` |  | yes |
| eos/analysis/analyst | companyLaborTableBie | GET | `/project/project/company/area/labor/table/bie` |  | yes |
| eos/analysis/analyst | companyLaborTablePie | GET | `/project/project/company/area/labor/table/pie` |  | yes |
| eos/analysis/analyst | companyLaborPie | GET | `/project/project/company/area/pie` |  | yes |
| eos/analysis/analyst | companyPlanTablePie | GET | `/project/project/company/fund/pie` |  | yes |
| eos/analysis/analyst | companyPlanTable | GET | `/project/project/company/plan/table` |  | yes |
| eos/analysis/analyst | companyPlanBie | GET | `/project/project/company/plan/table/bie` |  | yes |
| eos/analysis/analyst | exportAllOut | GET | `/project/project/export/project/allOut/{dynamic}` |  |  |
| eos/analysis/analyst | projectAnalyst | GET | `/project/project/export/projectAnalyst` |  | yes |
| eos/analysis/analyst | areaList | GET | `/project/project/getArea` |  | yes |
| eos/analysis/analyst | getBarAnasylt | GET | `/project/project/getBar` |  | yes |
| eos/analysis/analyst | getBarTotalAnasylt | GET | `/project/project/getBarTotal` |  | yes |
| eos/analysis/analyst | getExcelAnasylt | GET | `/project/project/getExcelAnalyst` |  | yes |
| eos/analysis/analyst | getLineAnasylt | GET | `/project/project/getLine` |  | yes |
| eos/analysis/analyst | AnalystProjectPaymentIn | GET | `/project/real/analystReal` |  | yes |
| eos/bid/bid | addBid | POST | `/project/bid` | yes |  |
| eos/bid/bid | updateBid | PUT | `/project/bid` | yes |  |
| eos/bid/bid | delBid | DELETE | `/project/bid/{dynamic}` |  |  |
| eos/bid/bid | getBid | GET | `/project/bid/{dynamic}` |  |  |
| eos/bid/bid | exportBid | GET | `/project/bid/export` |  | yes |
| eos/bid/bid | listBid | GET | `/project/bid/list` |  | yes |
| eos/budget/apply | addApply | POST | `/project/apply` | yes |  |
| eos/budget/apply | delBudget | DELETE | `/project/apply/{dynamic}` |  |  |
| eos/budget/apply | budgetPass | GET | `/project/apply/budget/pass` |  | yes |
| eos/budget/apply | budgetPassDept | GET | `/project/apply/budget/pass/dept` |  | yes |
| eos/budget/apply | budgetPassOne | GET | `/project/apply/budget/pass/one` |  | yes |
| eos/budget/apply | budgetType | GET | `/project/apply/budgetType/{dynamic}` |  |  |
| eos/budget/apply | applyEdit | PUT | `/project/apply/edit` | yes |  |
| eos/budget/apply | getList | GET | `/project/apply/list` |  | yes |
| eos/budget/apply | listSelf | GET | `/project/apply/list/personal` |  | yes |
| eos/budget/apply | resMoney | GET | `/project/apply/resMoney/{dynamic}` |  |  |
| eos/budget/apply | submitApply | POST | `/project/apply/submitApply` |  | yes |
| eos/budget/apply | exportBudget | GET | `/project/budget/export` |  | yes |
| eos/budget/apply | reApply | POST | `project/apply/reApply` | yes |  |
| eos/budget/budget | getOneApply | GET | `/project/apply/{dynamic}` |  |  |
| eos/budget/budget | getProjectList | GET | `/project/apply/budget/getProject/` |  |  |
| eos/budget/budget | getProjectMoney | GET | `/project/apply/budget/project/money/{dynamic}` |  |  |
| eos/budget/budget | personalDeptBudget | GET | `/project/apply/list/personal/dept` |  |  |
| eos/budget/budget | allDeptBudget | GET | `/project/apply/list/personal/dept/all` |  | yes |
| eos/budget/budget | addBudget | POST | `/project/budget` | yes |  |
| eos/budget/budget | updateBudget | PUT | `/project/budget` | yes |  |
| eos/budget/budget | delBudget | DELETE | `/project/budget/{dynamic}` |  |  |
| eos/budget/budget | getBudget | GET | `/project/budget/{dynamic}` |  |  |
| eos/budget/budget | exportBudget | GET | `/project/budget/export` |  | yes |
| eos/budget/budget | listBudget | GET | `/project/budget/list` |  | yes |
| eos/budget/budgetModify | delBudgetModify | DELETE | `/project/apply/budget/change/{dynamic}` |  |  |
| eos/budget/budgetModify | saveTypeList | GET | `/project/apply/budget/getApply` |  | yes |
| eos/budget/budgetModify | saveBudgetType | GET | `/project/apply/budget/getType/{dynamic}` |  |  |
| eos/budget/budgetModify | getBudgetModify | GET | `/project/apply/budget/listChange` |  | yes |
| eos/budget/budgetModify | saveBudgetModify | POST | `/project/apply/budget/saveChange` | yes |  |
| eos/budget/budgetModify | submitApply | POST | `/project/apply/submitApply/change/{dynamic}` |  |  |
| eos/budget/type | addType | POST | `/project/type` | yes |  |
| eos/budget/type | updateType | PUT | `/project/type` | yes |  |
| eos/budget/type | delType | DELETE | `/project/type/{dynamic}` |  |  |
| eos/budget/type | getType | GET | `/project/type/{dynamic}` |  |  |
| eos/budget/type | exportType | GET | `/project/type/export` |  | yes |
| eos/budget/type | TypeGetType | GET | `/project/type/getType/{dynamic}` |  |  |
| eos/budget/type | listType | GET | `/project/type/list/select/like` |  | yes |
| eos/budget/type | BigType | GET | `/project/type/select/budget/type` |  |  |
| eos/business/business | addBusiness | POST | `/project/business` | yes |  |
| eos/business/business | updateBusiness | PUT | `/project/business` | yes |  |
| eos/business/business | delBusiness | DELETE | `/project/business/{dynamic}` |  |  |
| eos/business/business | getBusiness | GET | `/project/business/{dynamic}` |  |  |
| eos/business/business | listBusiness | GET | `/project/business/list` |  | yes |
| eos/business/business | deleteBusinessStage | DELETE | `/project/business/stage/{dynamic}` |  |  |
| eos/business/business | addBusinessStage | POST | `/project/business/stage/insert` | yes |  |
| eos/business/business | deleteBusinessUser | DELETE | `/project/business/user/{dynamic}` |  |  |
| eos/change/history | listHistory | GET | `/project/apply/change/history` |  | yes |
| eos/change/history | addHistory | POST | `/project/history` | yes |  |
| eos/change/history | updateHistory | PUT | `/project/history` | yes |  |
| eos/change/history | delHistory | DELETE | `/project/history/{dynamic}` |  |  |
| eos/change/history | getHistory | GET | `/project/history/{dynamic}` |  |  |
| eos/change/history | exportHistory | GET | `/project/history/export` |  | yes |
| eos/contract/contract | addContract | POST | `/project/contract` | yes |  |
| eos/contract/contract | updateContract | PUT | `/project/contract` | yes |  |
| eos/contract/contract | delContract | DELETE | `/project/contract/{dynamic}` |  |  |
| eos/contract/contract | getContract | GET | `/project/contract/{dynamic}` |  |  |
| eos/contract/contract | exportContract | GET | `/project/contract/export` |  | yes |
| eos/contract/contract | listContract | GET | `/project/contract/list` |  | yes |
| eos/contract/contract | listContractApply | GET | `/project/contract/list/apply` |  | yes |
| eos/contract/contract | listByProject | GET | `/project/contract/list/by/project` |  | yes |
| eos/contract/contract | listInContractPersonal | GET | `/project/contract/list/in/personal` |  | yes |
| eos/contract/contract | listByProjectIdOnly | GET | `/project/contract/list/only` |  | yes |
| eos/contract/contract | listContractPersonal | GET | `/project/contract/list/personal` |  | yes |
| eos/contract/contract | queryContract | GET | `/project/contract/list/qc` |  | yes |
| eos/contract/contract | reApply | POST | `/project/contract/reApply` | yes |  |
| eos/contract/contract | submitApply | POST | `/project/contract/submitApply` |  | yes |
| eos/contract/contract | delContractDetailList | DELETE | `/project/list/{dynamic}` |  |  |
| eos/contract/contractModify | getContractList | GET | `/project/contract/list/only` |  | yes |
| eos/contract/contractModify | getContractModify | GET | `/project/contract/temporary/list` |  | yes |
| eos/contract/contractModify | deleteContractModify | DELETE | `/project/contract/temporary/remove/{dynamic}` |  |  |
| eos/contract/contractModify | contractSaveChange | POST | `/project/contract/temporary/saveChange` | yes |  |
| eos/contract/contractModify | submitApplyContractModify | POST | `/project/contract/temporary/submitApply` |  | yes |
| eos/contract/list | addList | POST | `/project/list` | yes |  |
| eos/contract/list | updateList | PUT | `/project/list` | yes |  |
| eos/contract/list | delList | DELETE | `/project/list/{dynamic}` |  |  |
| eos/contract/list | getList | GET | `/project/list/{dynamic}` |  |  |
| eos/contract/list | exportList | GET | `/project/list/export` |  | yes |
| eos/contract/list | listList | GET | `/project/list/list` |  | yes |
| eos/contract/list | getAmountListByContract | GET | `/project/list/list/contract` |  | yes |
| eos/contract/stage | addStage | POST | `/project/stage` | yes |  |
| eos/contract/stage | updateStage | PUT | `/project/stage` | yes |  |
| eos/contract/stage | delStage | DELETE | `/project/stage/{dynamic}` |  |  |
| eos/contract/stage | getStage | GET | `/project/stage/{dynamic}` |  |  |
| eos/contract/stage | exportStage | GET | `/project/stage/export` |  | yes |
| eos/contract/stage | listStage | GET | `/project/stage/list` |  | yes |
| eos/cost/labor | addLabor | POST | `/project/cost` | yes |  |
| eos/cost/labor | updateLabor | PUT | `/project/cost` | yes |  |
| eos/cost/labor | delLabor | DELETE | `/project/cost/{dynamic}` |  |  |
| eos/cost/labor | getLabor | GET | `/project/cost/{dynamic}` |  |  |
| eos/cost/labor | listLabor | GET | `/project/cost/list` |  | yes |
| eos/cost/labor | reApply | POST | `/project/cost/reApply` | yes |  |
| eos/cost/labor | delOnbusDetail | DELETE | `/project/labor/{dynamic}` |  |  |
| eos/cost/labor | exportLabor | GET | `/project/labor/export/{dynamic}` |  |  |
| eos/cost/labor | listLaborDetail | GET | `/project/labor/list` |  | yes |
| eos/cost/labor | projectLabor | GET | `/project/labor/project/labor` |  | yes |
| eos/cost/onbus | addOnbus | POST | `/project/cost` | yes |  |
| eos/cost/onbus | updateOnbus | PUT | `/project/cost` | yes |  |
| eos/cost/onbus | delOnbus | DELETE | `/project/cost/{dynamic}` |  |  |
| eos/cost/onbus | getOnbus | GET | `/project/cost/{dynamic}` |  |  |
| eos/cost/onbus | listOnbus | GET | `/project/cost/list` |  | yes |
| eos/cost/onbus | reApply | POST | `/project/cost/reApply` | yes |  |
| eos/cost/onbus | delOnbusDetail | DELETE | `/project/onbus/{dynamic}` |  |  |
| eos/cost/onbus | exportOnbus | GET | `/project/onbus/export/{dynamic}` |  |  |
| eos/cost/onbus | listOnbusDetail | GET | `/project/onbus/list` |  | yes |
| eos/cost/onbus | listPlaneSimple | GET | `/project/plane/list/simple` |  | yes |
| eos/earnest/earnestMoney | addApply | POST | `/project/earnestMoney` | yes |  |
| eos/earnest/earnestMoney | updateApply | PUT | `/project/earnestMoney` | yes |  |
| eos/earnest/earnestMoney | delApply | DELETE | `/project/earnestMoney/{dynamic}` |  |  |
| eos/earnest/earnestMoney | getApply | GET | `/project/earnestMoney/{dynamic}` |  |  |
| eos/earnest/earnestMoney | dingdingMSG | GET | `/project/earnestMoney/dingMsg` |  | yes |
| eos/earnest/earnestMoney | exportApply | GET | `/project/earnestMoney/export` |  | yes |
| eos/earnest/earnestMoney | listApply | GET | `/project/earnestMoney/list` |  | yes |
| eos/earnest/earnestMoney | listApplySimple | GET | `/project/earnestMoney/simpleList` |  | yes |
| eos/earnest/earnestMoney | submitApply | POST | `/project/earnestMoney/submitApply` |  | yes |
| eos/earnest/earnestMoney | reApply | POST | `project/earnestMoney/reApply` | yes |  |
| eos/earnest/earnestReturn | addReturn | POST | `/project/earnestReturn` | yes |  |
| eos/earnest/earnestReturn | updateReturn | PUT | `/project/earnestReturn/` | yes |  |
| eos/earnest/earnestReturn | delReturn | DELETE | `/project/earnestReturn/{dynamic}` |  |  |
| eos/earnest/earnestReturn | getReturn | GET | `/project/earnestReturn/{dynamic}` |  |  |
| eos/earnest/earnestReturn | exportReturn | GET | `/project/earnestReturn/export` |  | yes |
| eos/earnest/earnestReturn | listReturn | GET | `/project/earnestReturn/list` |  | yes |
| eos/expense/detail | addDetail | POST | `/project/detail` | yes |  |
| eos/expense/detail | updateDetail | PUT | `/project/detail` | yes |  |
| eos/expense/detail | delDetail | DELETE | `/project/detail/{dynamic}` |  |  |
| eos/expense/detail | getDetail | GET | `/project/detail/{dynamic}` |  |  |
| eos/expense/detail | exportDetail | GET | `/project/detail/export` |  | yes |
| eos/expense/detail | exportProjectDetail | GET | `/project/detail/getProjectExcel` |  | yes |
| eos/expense/detail | listDetail | GET | `/project/detail/list` |  | yes |
| eos/expense/expense | deletDetail | DELETE | `/project/detail/{dynamic}` |  |  |
| eos/expense/expense | exportDetail | GET | `/project/detail/export` |  | yes |
| eos/expense/expense | listExpenseDetail | GET | `/project/detail/list` |  | yes |
| eos/expense/expense | addExpense | POST | `/project/expense` | yes |  |
| eos/expense/expense | updateExpense | PUT | `/project/expense` | yes |  |
| eos/expense/expense | delExpense | DELETE | `/project/expense/{dynamic}` |  |  |
| eos/expense/expense | getExpense | GET | `/project/expense/{dynamic}` |  |  |
| eos/expense/expense | exportExpense | GET | `/project/expense/export` |  | yes |
| eos/expense/expense | expensegetOne | GET | `/project/expense/getOne` |  | yes |
| eos/expense/expense | listProjectSimple | GET | `/project/expense/getProject` |  |  |
| eos/expense/expense | listExpense | GET | `/project/expense/list` |  | yes |
| eos/expense/expense | listExpensePersonal | GET | `/project/expense/list/personal` |  | yes |
| eos/expense/expense | submitApply | POST | `/project/expense/submitApply` |  | yes |
| eos/expense/expense | budgetType | GET | `/project/expense/type/{dynamic}` |  |  |
| eos/expense/expense | deleteInvoice | DELETE | `/project/invoice/{dynamic}` |  |  |
| eos/expense/expense | exportInvoice | GET | `/project/invoice/download/zip/{dynamic}` |  |  |
| eos/expense/expense | getResidueMoney | GET | `/project/type/list/select/like` |  | yes |
| eos/expense/expense | getExpenseList | GET | `project/expense/list/getExpense/{dynamic}` |  |  |
| eos/expense/expense | reApply | POST | `project/expense/reApply` | yes |  |
| eos/fundPlan/fund | addFund | POST | `/project/fund` | yes |  |
| eos/fundPlan/fund | updateFund | PUT | `/project/fund` | yes |  |
| eos/fundPlan/fund | delFund | DELETE | `/project/fund/{dynamic}` |  |  |
| eos/fundPlan/fund | getFund | GET | `/project/fund/{dynamic}` |  |  |
| eos/fundPlan/fund | LaborProject | GET | `/project/fund/approval` |  | yes |
| eos/fundPlan/fund | fundContract | GET | `/project/fund/contract/` |  | yes |
| eos/fundPlan/fund | exportFund | GET | `/project/fund/export` |  | yes |
| eos/fundPlan/fund | listFund | GET | `/project/fund/list` |  | yes |
| eos/fundPlan/fund | fundProjectIn | GET | `/project/fund/project` |  | yes |
| eos/fundPlan/fund | fundProjectOut | GET | `/project/fund/project` |  | yes |
| eos/fundPlan/fund | submitAudit | POST | `/project/fund/submitApply/{dynamic}` |  |  |
| eos/fundPlan/income | addIncome | POST | `/project/income` | yes |  |
| eos/fundPlan/income | updateIncome | PUT | `/project/income` | yes |  |
| eos/fundPlan/income | delIncome | DELETE | `/project/income/{dynamic}` |  |  |
| eos/fundPlan/income | getIncome | GET | `/project/income/{dynamic}` |  |  |
| eos/fundPlan/income | exportIncome | GET | `/project/income/export` |  | yes |
| eos/fundPlan/income | listIncome | GET | `/project/income/list` |  | yes |
| eos/fundPlan/outcome | addOutcome | POST | `/project/outcome` | yes |  |
| eos/fundPlan/outcome | updateOutcome | PUT | `/project/outcome` | yes |  |
| eos/fundPlan/outcome | delOutcome | DELETE | `/project/outcome/{dynamic}` |  |  |
| eos/fundPlan/outcome | getOutcome | GET | `/project/outcome/{dynamic}` |  |  |
| eos/fundPlan/outcome | exportOutcome | GET | `/project/outcome/export` |  | yes |
| eos/fundPlan/outcome | listOutcome | GET | `/project/outcome/list` |  | yes |
| eos/fundPlan/summary | fundApproval | GET | `/project/fund/approval` |  | yes |
| eos/invoice/baidu_invoice | getAccessToken | POST | `/oauth/2.0/token` |  | yes |
| eos/invoice/baidu_invoice | getVatInvoice | POST | `/rest/2.0/ocr/v1/vat_invoice?access_token={dynamic}` | yes |  |
| eos/invoice/baidu_invoice | OCRTextinBillsCrop | POST | `/robot/v1.0/api/bills_crop` | yes |  |
| eos/invoice/get | addGet | POST | `/project/get` | yes |  |
| eos/invoice/get | updateGet | PUT | `/project/get` | yes |  |
| eos/invoice/get | delGet | DELETE | `/project/get/{dynamic}` |  |  |
| eos/invoice/get | getGet | GET | `/project/get/{dynamic}` |  |  |
| eos/invoice/get | exportGet | GET | `/project/get/export` |  | yes |
| eos/invoice/get | listGet | GET | `/project/get/list` |  | yes |
| eos/invoice/history | exportMake | GET | `/project/make/export/{dynamic}` |  |  |
| eos/invoice/history | getHistory | GET | `project/make/list/personal` |  | yes |
| eos/invoice/invoice | addInvoice | POST | `/project/invoice` | yes |  |
| eos/invoice/invoice | updateInvoice | PUT | `/project/invoice` | yes |  |
| eos/invoice/invoice | delInvoice | DELETE | `/project/invoice/{dynamic}` |  |  |
| eos/invoice/invoice | getInvoice | GET | `/project/invoice/{dynamic}` |  |  |
| eos/invoice/invoice | exportInvoice | GET | `/project/invoice/export` |  | yes |
| eos/invoice/invoice | listInvoice | GET | `/project/invoice/list` |  | yes |
| eos/invoice/make | addMake | POST | `/project/make` | yes |  |
| eos/invoice/make | updateMake | PUT | `/project/make` | yes |  |
| eos/invoice/make | delMake | DELETE | `/project/make/{dynamic}` |  |  |
| eos/invoice/make | getMake | GET | `/project/make/{dynamic}` |  |  |
| eos/invoice/make | exportMake | GET | `/project/make/export/{dynamic}` |  |  |
| eos/invoice/make | listMake | GET | `/project/make/list` |  | yes |
| eos/invoice/make | makeContractSum | GET | `/project/make/real/make/sum/{dynamic}` |  |  |
| eos/invoice/make | reApply | POST | `/project/make/reApply` | yes |  |
| eos/invoice/make | submitApply | POST | `/project/make/submitApply` |  | yes |
| eos/invoice/real | bankList | GET | `/project/merchant/merchant/bank` |  | yes |
| eos/invoice/real | addReal | POST | `/project/real` | yes |  |
| eos/invoice/real | updateReal | PUT | `/project/real` | yes |  |
| eos/invoice/real | delReal | DELETE | `/project/real/{dynamic}` |  |  |
| eos/invoice/real | getReal | GET | `/project/real/{dynamic}` |  |  |
| eos/invoice/real | dingdingMSG | GET | `/project/real/dingMsg` |  | yes |
| eos/invoice/real | exportReal | GET | `/project/real/export` |  | yes |
| eos/invoice/real | listReal | GET | `/project/real/list` |  | yes |
| eos/invoice/real | contractReal | GET | `/project/real/list/paymentReal` |  | yes |
| eos/merchant/merchant | addMerchant | POST | `/project/merchant` | yes |  |
| eos/merchant/merchant | updateMerchant | PUT | `/project/merchant` | yes |  |
| eos/merchant/merchant | delMerchant | DELETE | `/project/merchant/{dynamic}` |  |  |
| eos/merchant/merchant | getMerchant | GET | `/project/merchant/{dynamic}` |  |  |
| eos/merchant/merchant | deleteMerchatAccount | DELETE | `/project/merchant/account/{dynamic}` |  |  |
| eos/merchant/merchant | getMerchanAccountInfo | GET | `/project/merchant/account/query` |  | yes |
| eos/merchant/merchant | exportMerchant | GET | `/project/merchant/export` |  | yes |
| eos/merchant/merchant | listMerchant | GET | `/project/merchant/list` |  | yes |
| eos/merchant/merchant | deleteMerchatUser | DELETE | `/project/merchant/user/{dynamic}` |  |  |
| eos/project/mem | addMem | POST | `/project/mem` | yes |  |
| eos/project/mem | delMem | DELETE | `/project/mem/{dynamic}` |  |  |
| eos/project/mem | getMem | GET | `/project/mem/{dynamic}` |  |  |
| eos/project/mem | exportMem | GET | `/project/mem/export` |  | yes |
| eos/project/mem | exportPM | GET | `/project/mem/exportPM/{dynamic}` |  | yes |
| eos/project/mem | listMem | GET | `/project/mem/list` |  | yes |
| eos/project/mem | listMemAll | GET | `/project/mem/list/all` |  | yes |
| eos/project/mem | listMemRole | GET | `/project/mem/rolelist` |  | yes |
| eos/project/memApply | addMemApply | POST | `/project/mem/apply` | yes |  |
| eos/project/memApply | updateMemApply | PUT | `/project/mem/apply` | yes |  |
| eos/project/memApply | delMemApply | DELETE | `/project/mem/apply/{dynamic}` |  |  |
| eos/project/memApply | getMemApply | GET | `/project/mem/apply/{dynamic}` |  |  |
| eos/project/memApply | exportMemApply | GET | `/project/mem/apply/export` |  | yes |
| eos/project/memApply | listMemApply | GET | `/project/mem/apply/list` |  | yes |
| eos/project/memApply | reApply | POST | `/project/mem/apply/reApply` | yes |  |
| eos/project/memApply | submitApply | POST | `/project/mem/apply/submitApply` |  | yes |
| eos/project/project | myBudgetProjectList | GET | `/project/apply/pass/budget/project` |  | yes |
| eos/project/project | addProject | POST | `/project/project` | yes |  |
| eos/project/project | updateProject | PUT | `/project/project` | yes |  |
| eos/project/project | delProject | DELETE | `/project/project/{dynamic}` |  |  |
| eos/project/project | getProject | GET | `/project/project/{dynamic}` |  |  |
| eos/project/project | addChildProject | POST | `/project/project/add/children/project` | yes |  |
| eos/project/project | exportProject | GET | `/project/project/export` |  | yes |
| eos/project/project | exportProjectApply | GET | `/project/project/exportProject/{dynamic}` |  | yes |
| eos/project/project | listProject | GET | `/project/project/list` |  | yes |
| eos/project/project | listProjectAllAndCildren | GET | `/project/project/list/all` |  | yes |
| eos/project/project | listIn | GET | `/project/project/list/in` |  | yes |
| eos/project/project | listInProjectPersonal | GET | `/project/project/list/in/personal` |  | yes |
| eos/project/project | treePlanProject | GET | `/project/project/list/in/personal/tree` |  |  |
| eos/project/project | listProjectManager | GET | `/project/project/list/manager` |  | yes |
| eos/project/project | personal | GET | `/project/project/list/personal` |  | yes |
| eos/project/project | getChildProjectById | GET | `/project/project/query/children/project` |  | yes |
| eos/project/project | reApply | POST | `/project/project/reApply` | yes |  |
| eos/project/project | treeSelect | GET | `/project/project/select/tree` |  | yes |
| eos/project/project | submitApply | POST | `/project/project/submitApply` |  | yes |
| eos/project/projectModify | queryProjectDetail | GET | `/project/project/{dynamic}` |  |  |
| eos/project/projectModify | submitProject | POST | `/project/project/addChange` | yes |  |
| eos/project/projectModify | projectModifyList | GET | `/project/project/list/change` |  | yes |
| eos/project/projectModify | HanderDelProject | DELETE | `/project/project/project/change/{dynamic}` |  |  |
| eos/project/projectModify | submitApplyProject | POST | `/project/project/submitApply/change/{dynamic}` |  |  |
| eos/purchase/payment | addPayment | POST | `/project/payment` | yes |  |
| eos/purchase/payment | updatePayment | PUT | `/project/payment` | yes |  |
| eos/purchase/payment | delPayment | DELETE | `/project/payment/{dynamic}` |  |  |
| eos/purchase/payment | getPayment | GET | `/project/payment/{dynamic}` |  |  |
| eos/purchase/payment | exportPayment | GET | `/project/payment/export/{dynamic}` |  |  |
| eos/purchase/payment | exportPaymentExcel | GET | `/project/payment/getPaymentExcel` |  | yes |
| eos/purchase/payment | exportPaymentListExcel | GET | `/project/payment/getPaymentExcel/list` |  | yes |
| eos/purchase/payment | listPayment | GET | `/project/payment/list` |  | yes |
| eos/purchase/payment | listPaymentApply | GET | `/project/payment/list/apply` |  | yes |
| eos/purchase/payment | notStore | GET | `/project/payment/list/notStore` |  | yes |
| eos/purchase/payment | contractPayment | GET | `/project/payment/list/payment` |  | yes |
| eos/purchase/payment | queryUsed | GET | `/project/payment/queryUsed` |  | yes |
| eos/purchase/payment | reApply | POST | `/project/payment/reApply` | yes |  |
| eos/purchase/payment | submitApply | POST | `/project/payment/submitApply` |  | yes |
| eos/purchase/paymentApply | addApply | POST | `/project/apply/payment` | yes |  |
| eos/purchase/paymentApply | updateApply | PUT | `/project/apply/payment` | yes |  |
| eos/purchase/paymentApply | delApply | DELETE | `/project/apply/payment/{dynamic}` |  |  |
| eos/purchase/paymentApply | getApply | GET | `/project/apply/payment/{dynamic}` |  |  |
| eos/purchase/paymentApply | exportApply | GET | `/project/apply/payment/export` |  | yes |
| eos/purchase/paymentApply | listApply | GET | `/project/apply/payment/list` |  | yes |
| eos/purchase/paymentApply | listApplyAll | GET | `/project/apply/payment/list/all` |  | yes |
| eos/purchase/paymentApply | submitApply | POST | `/project/apply/payment/submitApply` |  | yes |
| eos/purchase/paymentApply | reApply | POST | `project/apply/payment/reApply` | yes |  |
| eos/purchase/paymentDetail | delPaymentDetail | DELETE | `/project/payment/detail/{dynamic}` |  |  |
| eos/purchase/purchase | addPurchase | POST | `/project/apply/payment` | yes |  |
| eos/purchase/purchase | editPurchase | PUT | `/project/apply/payment` | yes |  |
| eos/purchase/purchase | delPurchase | DELETE | `/project/apply/payment/{dynamic}` |  |  |
| eos/purchase/purchase | getDetail | GET | `/project/apply/payment/{dynamic}` |  |  |
| eos/purchase/purchase | purchaseList | GET | `/project/apply/payment/list` |  | yes |
| eos/purchase/purchase | listAll | GET | `/project/apply/payment/list/all` |  | yes |
| eos/purchase/purchase | notPay | GET | `/project/apply/payment/list/notPay` |  | yes |
| eos/purchase/purchase | reApply | POST | `/project/apply/payment/reApply` | yes |  |
| eos/purchase/purchase | submitApply | POST | `/project/apply/payment/submitApply` |  | yes |
| eos/purchase/purchase | delDetail | DELETE | `/project/payment/apply/detail/{dynamic}` |  |  |
| eos/purchase/refund | getContractList | GET | `/project/contract/list/only` |  | yes |
| eos/purchase/refund | deleteRefund | DELETE | `/project/payment/refund/{dynamic}` |  |  |
| eos/purchase/refund | refundDetail | GET | `/project/payment/refund/detail/{dynamic}` |  |  |
| eos/purchase/refund | changeRefund | PUT | `/project/payment/reFund/editChange` | yes |  |
| eos/purchase/refund | reFundList | GET | `/project/payment/reFund/list` |  | yes |
| eos/purchase/refund | saveRefund | POST | `/project/payment/reFund/saveRefund` | yes |  |
| eos/repayment/loan | addLoan | POST | `/project/loan` | yes |  |
| eos/repayment/loan | updateLoan | PUT | `/project/loan` | yes |  |
| eos/repayment/loan | delLoan | DELETE | `/project/loan/{dynamic}` |  |  |
| eos/repayment/loan | getLoan | GET | `/project/loan/{dynamic}` |  |  |
| eos/repayment/loan | delLoanDetail | DELETE | `/project/loan/detail/{dynamic}` |  |  |
| eos/repayment/loan | exportLoan | GET | `/project/loan/export` |  | yes |
| eos/repayment/loan | getProject | GET | `/project/loan/getProject` |  |  |
| eos/repayment/loan | listLoan | GET | `/project/loan/list` |  | yes |
| eos/repayment/loan | listLoanAll | GET | `/project/loan/list/all` |  | yes |
| eos/repayment/loan | reApply | POST | `/project/loan/reApply` | yes |  |
| eos/repayment/loan | submitApply | POST | `/project/loan/submitApply` |  | yes |
| eos/repayment/loan | getUserMoney | GET | `/project/repay/getUserMoney` |  |  |
| eos/repayment/payBackConfirm | confirmRepay | GET | `/project/repay/confirmRepay/{dynamic}` |  |  |
| eos/repayment/payBackConfirm | unPayMoney | GET | `/project/repay/unconfirm` |  | yes |
| eos/repayment/repay | addRepay | POST | `/project/repay` | yes |  |
| eos/repayment/repay | updateRepay | PUT | `/project/repay` | yes |  |
| eos/repayment/repay | delRepay | DELETE | `/project/repay/{dynamic}` |  |  |
| eos/repayment/repay | getRepay | GET | `/project/repay/{dynamic}` |  |  |
| eos/repayment/repay | exportRepay | GET | `/project/repay/export` |  | yes |
| eos/repayment/repay | getUserMoney | GET | `/project/repay/getUserMoney` |  |  |
| eos/repayment/repay | listRepay | GET | `/project/repay/list` |  | yes |
| eos/repayment/search | getIntegratedQuery | GET | `/project/loan/getAll` |  | yes |
| eos/repayment/search | getRepay | GET | `/project/loan/getAll` |  | yes |
| eos/repayment/search | getLoanList | GET | `/project/loan/getLoan` |  | yes |
| eos/repayment/search | getRepayList | GET | `project/repay/getRepay/` |  | yes |
| eos/repayment/search | repayPress | GET | `project/repay/repayPress/{dynamic}` |  |  |
| eos/schedule/process | updateProcess | PUT | `/project/process` | yes |  |
| eos/schedule/process | delProcess | DELETE | `/project/process/{dynamic}` |  |  |
| eos/schedule/process | exportProcess | GET | `/project/process/export` |  | yes |
| eos/schedule/process | processProject | GET | `/project/process/process/project` |  |  |
| eos/schedule/process | addProcess | POST | `/project/processWrite` | yes |  |
| eos/schedule/process | delProcessWrite | DELETE | `/project/processWrite/{dynamic}` |  |  |
| eos/schedule/process | getProcess | GET | `/project/processWrite/{dynamic}` |  |  |
| eos/schedule/process | listProcess | GET | `/project/processWrite/list` |  | yes |
| eos/schedule/process | ProjectScheduleList | GET | `/project/schedule/getProject` |  | yes |
| eos/schedule/progressSummary | ExportFile | GET | `/project/schedule/export/processTotal` |  | yes |
| eos/schedule/progressSummary | getRegionList | GET | `/project/schedule/getRegion` |  | yes |
| eos/schedule/progressSummary | getPeopleList | GET | `/project/schedule/labor/processTotal` |  | yes |
| eos/schedule/progressSummary | getProjectList | GET | `/project/schedule/project/processTotal` |  | yes |
| eos/schedule/schedule | addSchedule | POST | `/project/schedule` | yes |  |
| eos/schedule/schedule | updateSchedule | PUT | `/project/schedule` | yes |  |
| eos/schedule/schedule | delSchedule | DELETE | `/project/schedule/{dynamic}` |  |  |
| eos/schedule/schedule | getSchedule | GET | `/project/schedule/{dynamic}` |  |  |
| eos/schedule/schedule | exportSchedule | GET | `/project/schedule/export` |  | yes |
| eos/schedule/schedule | listSchedule | GET | `/project/schedule/list` |  | yes |
| eos/schedule/schedule | planProject | GET | `/project/schedule/plan/project` |  |  |
| eos/schedule/schedule | deleteScheduleStage | DELETE | `/project/scheduleStage/{dynamic}` |  |  |
| eos/schedule/schedule-stage | addStage | POST | `/project/scheduleStage` | yes |  |
| eos/schedule/schedule-stage | updateStage | PUT | `/project/scheduleStage` | yes |  |
| eos/schedule/schedule-stage | delStage | DELETE | `/project/scheduleStage/{dynamic}` |  |  |
| eos/schedule/schedule-stage | getStage | GET | `/project/scheduleStage/{dynamic}` |  |  |
| eos/schedule/schedule-stage | exportStage | GET | `/project/scheduleStage/export` |  | yes |
| eos/schedule/schedule-stage | listStage | GET | `/project/scheduleStage/list` |  | yes |
| eos/subcontract/employee | addEmployee | POST | `/project/employee` | yes |  |
| eos/subcontract/employee | updateEmployee | PUT | `/project/employee` | yes |  |
| eos/subcontract/employee | delEmployee | DELETE | `/project/employee/{dynamic}` |  |  |
| eos/subcontract/employee | getEmployee | GET | `/project/employee/{dynamic}` |  |  |
| eos/subcontract/employee | delEmployeeDetail | DELETE | `/project/employee/detail/{dynamic}` |  |  |
| eos/subcontract/employee | exportEmployee | GET | `/project/employee/export` |  | yes |
| eos/subcontract/employee | submitReApply | POST | `/project/employee/reApply` | yes |  |
| eos/subcontract/employee | submitApply | POST | `/project/employee/submitApply` |  | yes |
| eos/subcontract/employee | listEmployee | GET | `project/employee/list/personal` |  | yes |
| eos/subcontract/exec | addExec | POST | `/project/exec` | yes |  |
| eos/subcontract/exec | updateExec | PUT | `/project/exec` | yes |  |
| eos/subcontract/exec | delExec | DELETE | `/project/exec/{dynamic}` |  |  |
| eos/subcontract/exec | getExec | GET | `/project/exec/{dynamic}` |  |  |
| eos/subcontract/exec | delExecDetail | DELETE | `/project/exec/detail/{dynamic}` |  |  |
| eos/subcontract/exec | exportExec | GET | `/project/exec/export` |  | yes |
| eos/subcontract/exec | listExec | GET | `/project/exec/list/personal` |  | yes |
| eos/subcontract/exec | submitReApply | POST | `/project/exec/reApply` | yes |  |
| eos/subcontract/exec | submitApply | POST | `/project/exec/submitApply` |  | yes |
| eos/subcontract/quantities | addApply | POST | `/project/quantities` | yes |  |
| eos/subcontract/quantities | updateApply | PUT | `/project/quantities` | yes |  |
| eos/subcontract/quantities | delApply | DELETE | `/project/quantities/{dynamic}` |  |  |
| eos/subcontract/quantities | getApply | GET | `/project/quantities/{dynamic}` |  |  |
| eos/subcontract/quantities | exportApply | GET | `/project/quantities/export` |  | yes |
| eos/subcontract/quantities | listApply | GET | `/project/quantities/list` |  | yes |
| eos/warehouse/warehouse | addWarehouse | POST | `/project/warehouse` | yes |  |
| eos/warehouse/warehouse | updateWarehouse | PUT | `/project/warehouse` | yes |  |
| eos/warehouse/warehouse | delWarehouse | DELETE | `/project/warehouse/{dynamic}` |  |  |
| eos/warehouse/warehouse | getWarehouse | GET | `/project/warehouse/{dynamic}` |  |  |
| eos/warehouse/warehouse | exportWarehouse | GET | `/project/warehouse/export` |  | yes |
| eos/warehouse/warehouse | getStore | GET | `/project/warehouse/getStore` |  | yes |
| eos/warehouse/warehouse | listWarehouse | GET | `/project/warehouse/list` |  | yes |
| eos/warehouse/warehouse | warehouseByProject | GET | `/project/warehouse/list/byProject` |  | yes |
| eos/warehouse/warehouse | projectInStore | GET | `/project/warehouse/project/in` |  | yes |
| eos/warehouse/warehouse | queryWarehouse | GET | `/project/warehouse/query` |  | yes |
| eos/warehouse/warehouse | storeByProject | GET | `/project/warehouse/store/list/byProject` |  | yes |
| eos/warehouse/warehouse | storeByWarehouse | GET | `/project/warehouse/store/list/byWarehouse` |  | yes |
| eos/warehouse/warehouseInApply | addWarehouseInApply | POST | `/project/warehouseInApply` | yes |  |
| eos/warehouse/warehouseInApply | updateWarehouseInApply | PUT | `/project/warehouseInApply` | yes |  |
| eos/warehouse/warehouseInApply | delWarehouseInApply | DELETE | `/project/warehouseInApply/{dynamic}` |  |  |
| eos/warehouse/warehouseInApply | getWarehouseInApply | GET | `/project/warehouseInApply/{dynamic}` |  |  |
| eos/warehouse/warehouseInApply | queryInDetail | GET | `/project/warehouseInApply/detail/list` |  | yes |
| eos/warehouse/warehouseInApply | exportWarehouseInApply | GET | `/project/warehouseInApply/export` |  | yes |
| eos/warehouse/warehouseInApply | listWarehouseInApply | GET | `/project/warehouseInApply/list` |  | yes |
| eos/warehouse/warehouseInApply | getByProject | GET | `/project/warehouseInApply/list/byProject` |  | yes |
| eos/warehouse/warehouseInApply | getByProjectAndWarehouse | GET | `/project/warehouseInApply/list/byProject/byWarehouse` |  | yes |
| eos/warehouse/warehouseInApply | reApply | POST | `/project/warehouseInApply/reApply` | yes |  |
| eos/warehouse/warehouseInApply | submitApply | POST | `/project/warehouseInApply/submitApply` |  | yes |
| eos/warehouse/warehouseInDetail | addWarehouseInDetail | POST | `/project/warehouseInDetail` | yes |  |
| eos/warehouse/warehouseInDetail | updateWarehouseInDetail | PUT | `/project/warehouseInDetail` | yes |  |
| eos/warehouse/warehouseInDetail | delWarehouseInDetail | DELETE | `/project/warehouseInDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseInDetail | getWarehouseInDetail | GET | `/project/warehouseInDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseInDetail | exportWarehouseInDetail | GET | `/project/warehouseInDetail/export` |  | yes |
| eos/warehouse/warehouseInDetail | listWarehouseInDetail | GET | `/project/warehouseInDetail/list` |  | yes |
| eos/warehouse/warehouseOutapply | addWarehouseOutapply | POST | `/project/warehouseOutapply` | yes |  |
| eos/warehouse/warehouseOutapply | updateWarehouseOutapply | PUT | `/project/warehouseOutapply` | yes |  |
| eos/warehouse/warehouseOutapply | delWarehouseOutapply | DELETE | `/project/warehouseOutapply/{dynamic}` |  |  |
| eos/warehouse/warehouseOutapply | getWarehouseOutapply | GET | `/project/warehouseOutapply/{dynamic}` |  |  |
| eos/warehouse/warehouseOutapply | queryOutDetail | GET | `/project/warehouseOutapply/detail/list` |  | yes |
| eos/warehouse/warehouseOutapply | exportWarehouseOutapply | GET | `/project/warehouseOutapply/export` |  | yes |
| eos/warehouse/warehouseOutapply | listWarehouseOutapply | GET | `/project/warehouseOutapply/list` |  | yes |
| eos/warehouse/warehouseOutapply | reApply | POST | `/project/warehouseOutapply/reApply` | yes |  |
| eos/warehouse/warehouseOutapply | submitApply | POST | `/project/warehouseOutapply/submitApply` |  | yes |
| eos/warehouse/warehouseOutDetail | addWarehouseOutDetail | POST | `/project/warehouseOutDetail` | yes |  |
| eos/warehouse/warehouseOutDetail | updateWarehouseOutDetail | PUT | `/project/warehouseOutDetail` | yes |  |
| eos/warehouse/warehouseOutDetail | delWarehouseOutDetail | DELETE | `/project/warehouseOutDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseOutDetail | getWarehouseOutDetail | GET | `/project/warehouseOutDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseOutDetail | exportWarehouseOutDetail | GET | `/project/warehouseOutDetail/export` |  | yes |
| eos/warehouse/warehouseOutDetail | listWarehouseOutDetail | GET | `/project/warehouseOutDetail/list` |  | yes |
| eos/warehouse/warehouseTransferApply | addWarehouseTransferApply | POST | `/project/warehouseTransferApply` | yes |  |
| eos/warehouse/warehouseTransferApply | updateWarehouseTransferApply | PUT | `/project/warehouseTransferApply` | yes |  |
| eos/warehouse/warehouseTransferApply | delWarehouseTransferApply | DELETE | `/project/warehouseTransferApply/{dynamic}` |  |  |
| eos/warehouse/warehouseTransferApply | getWarehouseTransferApply | GET | `/project/warehouseTransferApply/{dynamic}` |  |  |
| eos/warehouse/warehouseTransferApply | queryTransInDetail | GET | `/project/warehouseTransferApply/detail/in` |  | yes |
| eos/warehouse/warehouseTransferApply | queryTransOutDetail | GET | `/project/warehouseTransferApply/detail/out` |  | yes |
| eos/warehouse/warehouseTransferApply | exportWarehouseTransferApply | GET | `/project/warehouseTransferApply/export` |  | yes |
| eos/warehouse/warehouseTransferApply | listWarehouseTransferApply | GET | `/project/warehouseTransferApply/list` |  | yes |
| eos/warehouse/warehouseTransferApply | reApply | POST | `/project/warehouseTransferApply/reApply` | yes |  |
| eos/warehouse/warehouseTransferApply | submitApply | POST | `/project/warehouseTransferApply/submitApply` |  | yes |
| eos/warehouse/warehouseTransferDetail | addWarehouseTransferDetail | POST | `/project/warehouseTransferDetail` | yes |  |
| eos/warehouse/warehouseTransferDetail | updateWarehouseTransferDetail | PUT | `/project/warehouseTransferDetail` | yes |  |
| eos/warehouse/warehouseTransferDetail | delWarehouseTransferDetail | DELETE | `/project/warehouseTransferDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseTransferDetail | getWarehouseTransferDetail | GET | `/project/warehouseTransferDetail/{dynamic}` |  |  |
| eos/warehouse/warehouseTransferDetail | exportWarehouseTransferDetail | GET | `/project/warehouseTransferDetail/export` |  | yes |
| eos/warehouse/warehouseTransferDetail | listWarehouseTransferDetail | GET | `/project/warehouseTransferDetail/list` |  | yes |
| eos/warn/warn | contractStage | GET | `/project/contract/warnContractStage` |  |  |
| eos/warn/warn | earnestList | GET | `/project/contract/warnEarnest` |  |  |
| eos/warn/warn | planOut | GET | `/project/contract/warnPayment` |  |  |
| eos/warn/warn | planIn | GET | `/project/contract/warnPaymentReal` |  |  |
| eos/warn/warn | warnTotal | GET | `/project/contract/warnTotal` |  |  |
| processDesign/form | getFormPerms | GET | `/form_perms/find/auth_group` |  | yes |
| processDesign/index | detailProcess | POST | `/activiti/mongo/detailProcess` | yes |  |
| processDesign/index | setProcess | POST | `/activiti/mongo/setProcess` | yes |  |
| processDesign/index | subumitProcess | POST | `/activiti/mongo/submitProcess` | yes |  |
| processDesign/index | getTaskCopyList | GET | `/activiti/mongoWorkflow/taskCopyList` |  | yes |
| processDesign/index | getTaskDoneList | GET | `/activiti/mongoWorkflow/taskDoneList` |  | yes |
| processDesign/index | getMongoWorkflowList | GET | `/activiti/mongoWorkflow/taskStartList` |  | yes |
| processDesign/index | getTaskListCount | GET | `/activiti/mongoWorkflow/taskWaitCount` |  | yes |
| processDesign/index | getTaskList | GET | `/activiti/mongoWorkflow/taskWaitList` |  | yes |
| processDesign/index | activitiProcess | POST | `/activiti/process/complete` |  | yes |
| processDesign/index | dataCreate | POST | `/data/create` | yes |  |
| processDesign/index | getFormDataDetail | POST | `/data/detail` | yes |  |
| processDesign/index | createDataFind | POST | `/data/find` | yes |  |
| processDesign/index | deleteFormItem | POST | `/data/remove` | yes |  |
| processDesign/index | createdFrom | POST | `/form/create` | yes |  |
| processDesign/index | getFormDetail | POST | `/form/detail` | yes |  |
| processDesign/index | getFormMenuList | GET | `/form/list` |  | yes |
| processDesign/index | deleteForm | POST | `/form/remove` | yes |  |
| processDesign/index | updateForm | POST | `/form/update` | yes |  |
| system/config | addConfig | POST | `/system/config` | yes |  |
| system/config | updateConfig | PUT | `/system/config` | yes |  |
| system/config | delConfig | DELETE | `/system/config/{dynamic}` |  |  |
| system/config | getConfig | GET | `/system/config/{dynamic}` |  |  |
| system/config | clearCache | DELETE | `/system/config/clearCache` |  |  |
| system/config | getConfigKey | GET | `/system/config/configKey/{dynamic}` |  |  |
| system/config | exportConfig | GET | `/system/config/export` |  | yes |
| system/config | listConfig | GET | `/system/config/list` |  | yes |
| system/dept | addDept | POST | `/system/dept` | yes |  |
| system/dept | updateDept | PUT | `/system/dept` | yes |  |
| system/dept | delDept | DELETE | `/system/dept/{dynamic}` |  |  |
| system/dept | getDept | GET | `/system/dept/{dynamic}` |  |  |
| system/dept | allTree | GET | `/system/dept/allTree` |  |  |
| system/dept | listDept | GET | `/system/dept/list` |  | yes |
| system/dept | listDeptExcludeChild | GET | `/system/dept/list/exclude/{dynamic}` |  |  |
| system/dept | listDeptPage | GET | `/system/dept/list/page` |  | yes |
| system/dept | roleDeptTreeselect | GET | `/system/dept/roleDeptTreeselect/{dynamic}` |  |  |
| system/dept | treeselect | GET | `/system/dept/treeselect` |  |  |
| system/dict/data | addData | POST | `/system/dict/data` | yes |  |
| system/dict/data | updateData | PUT | `/system/dict/data` | yes |  |
| system/dict/data | delData | DELETE | `/system/dict/data/{dynamic}` |  |  |
| system/dict/data | getData | GET | `/system/dict/data/{dynamic}` |  |  |
| system/dict/data | exportData | GET | `/system/dict/data/export` |  | yes |
| system/dict/data | listData | GET | `/system/dict/data/list` |  | yes |
| system/dict/data | getDicts | GET | `/system/dict/data/type/{dynamic}` |  |  |
| system/dict/type | addType | POST | `/system/dict/type` | yes |  |
| system/dict/type | updateType | PUT | `/system/dict/type` | yes |  |
| system/dict/type | delType | DELETE | `/system/dict/type/{dynamic}` |  |  |
| system/dict/type | getType | GET | `/system/dict/type/{dynamic}` |  |  |
| system/dict/type | clearCache | DELETE | `/system/dict/type/clearCache` |  |  |
| system/dict/type | exportType | GET | `/system/dict/type/export` |  | yes |
| system/dict/type | listType | GET | `/system/dict/type/list` |  | yes |
| system/dict/type | optionselect | GET | `/system/dict/type/optionselect` |  |  |
| system/log/logininfor | delLogininfor | DELETE | `/monitor/logininfor/{dynamic}` |  |  |
| system/log/logininfor | cleanLogininfor | DELETE | `/monitor/logininfor/clean` |  |  |
| system/log/logininfor | exportLogininfor | GET | `/monitor/logininfor/export` |  | yes |
| system/log/logininfor | list | GET | `/monitor/logininfor/list` |  | yes |
| system/log/operlog | delOperlog | DELETE | `/monitor/operlog/{dynamic}` |  |  |
| system/log/operlog | cleanOperlog | DELETE | `/monitor/operlog/clean` |  |  |
| system/log/operlog | exportOperlog | GET | `/monitor/operlog/export` |  | yes |
| system/log/operlog | list | GET | `/monitor/operlog/list` |  | yes |
| system/menu | deptMenuTree | GET | `/app/user/deptMenuTree` |  | yes |
| system/menu | roleMenuTree | GET | `/app/user/roleMenuTree` |  | yes |
| system/menu | userMenuTree | GET | `/app/user/userMenuTree` |  | yes |
| system/menu | addUserMenu | POST | `/project/regular/menu` | yes |  |
| system/menu | deleteUserMenu | DELETE | `/project/regular/menu/{dynamic}` |  |  |
| system/menu | listUserMenus | GET | `/project/regular/menu/list/build` |  |  |
| system/menu | addMenu | POST | `/system/menu` | yes |  |
| system/menu | updateMenu | PUT | `/system/menu` | yes |  |
| system/menu | delMenu | DELETE | `/system/menu/{dynamic}` |  |  |
| system/menu | getMenu | GET | `/system/menu/{dynamic}` |  |  |
| system/menu | listMenu | GET | `/system/menu/list` |  | yes |
| system/menu | roleMenuTreeselect | GET | `/system/menu/roleMenuTreeselect/{dynamic}` |  |  |
| system/menu | treeselect | GET | `/system/menu/treeselect` |  |  |
| system/notice | saveMessage | POST | `/system/message` | yes |  |
| system/notice | addNotice | POST | `/system/notice` | yes |  |
| system/notice | updateNotice | PUT | `/system/notice` | yes |  |
| system/notice | delNotice | DELETE | `/system/notice/{dynamic}` |  |  |
| system/notice | getNotice | GET | `/system/notice/{dynamic}` |  |  |
| system/notice | listNotice | GET | `/system/notice/list` |  | yes |
| system/post | addPost | POST | `/system/post` | yes |  |
| system/post | updatePost | PUT | `/system/post` | yes |  |
| system/post | delPost | DELETE | `/system/post/{dynamic}` |  |  |
| system/post | getPost | GET | `/system/post/{dynamic}` |  |  |
| system/post | addPostMember | POST | `/system/post/add_member` | yes |  |
| system/post | exportPost | GET | `/system/post/export` |  | yes |
| system/post | listPost | GET | `/system/post/list` |  | yes |
| system/post | listPostAll | GET | `/system/post/list/all` |  | yes |
| system/post | deletePostMember | DELETE | `/system/post/remove_member` | yes |  |
| system/role | addRole | POST | `/system/role` | yes |  |
| system/role | updateRole | PUT | `/system/role` | yes |  |
| system/role | delRoleGroup | DELETE | `/system/role_group/group_remove/{dynamic}` |  |  |
| system/role | delRole | DELETE | `/system/role/{dynamic}` |  |  |
| system/role | getRole | GET | `/system/role/{dynamic}` |  |  |
| system/role | changeRoleStatus | PUT | `/system/role/changeStatus` | yes |  |
| system/role | dataScope | PUT | `/system/role/dataScope` | yes |  |
| system/role | exportRole | GET | `/system/role/export` |  | yes |
| system/role | listRole | GET | `/system/role/list` |  | yes |
| system/role | listRoleAll | GET | `/system/role/list/all` |  | yes |
| system/role | delSaasRole | DELETE | `/system/role/remove/{dynamic}` |  |  |
| system/user | commonUpload | POST | `/common/upload` | yes |  |
| system/user | importdingding | GET | `/dingDing/auth/insertUsers` |  |  |
| system/user | addUser | POST | `/system/user` | yes |  |
| system/user | updateUser | PUT | `/system/user` | yes |  |
| system/user | delUser | DELETE | `/system/user/{dynamic}` |  |  |
| system/user | getUser | GET | `/system/user/{dynamic}` |  |  |
| system/user | changeUserStatus | PUT | `/system/user/changeStatus` | yes |  |
| system/user | editUser | PUT | `/system/user/edit` | yes |  |
| system/user | excelImport | POST | `/system/user/excel_import` | yes |  |
| system/user | exportUser | GET | `/system/user/export` |  | yes |
| system/user | getByUsername | GET | `/system/user/getByUsername` |  | yes |
| system/user | importTemplate | GET | `/system/user/importTemplate` |  |  |
| system/user | listUser | GET | `/system/user/list` |  | yes |
| system/user | queryAll | GET | `/system/user/list/queryAll` |  | yes |
| system/user | getUserProfile | GET | `/system/user/profile` |  |  |
| system/user | updateUserProfile | PUT | `/system/user/profile` | yes |  |
| system/user | uploadAvatar | POST | `/system/user/profile/avatar` | yes |  |
| system/user | updateUserPwd | PUT | `/system/user/profile/updatePwd` |  | yes |
| system/user | resetUserPwd | PUT | `/system/user/resetPwd` | yes |  |
| user/app | getAppSystemList | GET | `/app/system/list` |  | yes |
| user/app | addMenuPerm | POST | `/app/user/addModule` | yes |  |
| user/app | deleteAppItem | DELETE | `/app/user/delete/{dynamic}` |  |  |
| user/app | getAppList | GET | `/app/user/list` |  | yes |
| user/app | queryAppByTenantAppId | GET | `/app/user/module/{dynamic}` |  |  |
| user/app | createdNewApp | POST | `/app/user/self/app` | yes |  |
| user/app | updateApp | PUT | `/app/user/update` | yes |  |
| user/app | getUserInfo | GET | `/crm/user/get_user_info` |  |  |
| user/app | addAdminStstemGroup | POST | `/system/admin/addAdmin` | yes |  |
| user/app | addAdminGroup | POST | `/system/admin/addGroup` | yes |  |
| user/app | queryNormalAdminDetail | GET | `/system/admin/adminGroup/{dynamic}` |  |  |
| user/app | editGroupName | POST | `/system/admin/edit` | yes |  |
| user/app | queryStstemAdmin | GET | `/system/admin/query/admin` |  |  |
| user/app | queryAdminGroup | GET | `/system/admin/query/adminGroup` |  |  |
| user/app | adminGroupDelete | DELETE | `/system/admin/remove/{dynamic}` |  |  |
| user/app | adminUpdatePre | POST | `/system/admin/update` | yes |  |
| user/app | addSystemDepchild | POST | `/system/dept` | yes |  |
| user/app | editDeptName | PUT | `/system/dept` | yes |  |
| user/app | addDeptPrincipal | GET | `/system/dept/add/principal` |  | yes |
| user/app | adjustDeptUser | GET | `/system/dept/addUser` |  | yes |
| user/app | getTreeMenu | GET | `/system/dept/admin/tree` |  | yes |
| user/app | deptsort | GET | `/system/dept/sort` |  | yes |
| user/app | getRoleListClass | GET | `/system/role_group/group_list` |  | yes |
| user/app | updateRoleGroup | POST | `/system/role_group/group_update` | yes |  |
| user/app | addRoleinMember | POST | `/system/role/add_member` | yes |  |
| user/app | getRoleByAdmin | GET | `/system/role/admin/list` |  |  |
| user/app | insertRole | POST | `/system/role/insert` | yes |  |
| user/app | getRoleMemberList | GET | `/system/role/list_member` |  | yes |
| user/app | deleteRoleinMember | DELETE | `/system/role/remove_member` | yes |  |
| user/app | updateRole | POST | `/system/role/update` | yes |  |
| user/app | addRole | POST | `/system/user/add` | yes |  |
| user/app | addUserDismiss | GET | `/system/user/dismiss` |  | yes |
| user/app | getDismissList | GET | `/system/user/dismiss_list` |  | yes |
| user/app | roleImport | POST | `/system/user/excel_import` | yes |  |
| user/app | getUserList | GET | `/system/user/list` |  | yes |
| user/app | queryUserList | GET | `/system/user/list` |  | yes |
| user/enterprise | addEnterprise | POST | `/crm/company/add` | yes |  |
| user/enterprise | toggleEnterprise | GET | `/crm/company/change/{dynamic}` |  |  |
| user/enterprise | getEnterprise | GET | `/crm/company/getCompany` |  | yes |
| user/login | getCodeImg | GET | `/captchaImage` |  |  |
| user/module | getRouters | GET | `/getRouters` |  |  |
| user/registered | registered | POST | `/crm/user/register` | yes |  |
