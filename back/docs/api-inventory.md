# Frontend API Inventory

Generated from: `src/api`

Endpoints: **154**

| Module | Function | Method | URL | Body | Params |
| --- | --- | --- | --- | --- | --- |
| activiti/process | listFlowElementsValid | GET | `/activiti/process/listFlowElementsValid` |  | yes |
| activiti/process | listHistory | POST | `/activiti/process/listHistory` | yes |  |
| activiti/process | rejectTask | POST | `/project/activity/back` | yes |  |
| activiti/process | completeTask | POST | `/project/activity/complete` | yes |  |
| activiti/process | finishTask | POST | `/project/activity/end/process` | yes |  |
| activiti/process | findProcessStarted | GET | `/project/activity/started` |  | yes |
| activiti/process | findTaskCopyList | GET | `/project/activity/taskCopyList` |  | yes |
| activiti/process | listDone | GET | `/project/activity/taskDoneList` |  | yes |
| activiti/process | listTodo | GET | `/project/activity/taskWaitList` |  | yes |
| activiti/workflow | updateReadUsers | GET | `/activiti/workflow/read/{dynamic}/{dynamic}/{dynamic}` |  |  |
| enterprise/index | addMenuPerm | POST | `/app/user/addModule` | yes |  |
| enterprise/index | deleteAppItem | DELETE | `/app/user/delete/{dynamic}` |  |  |
| enterprise/index | createdNewApp | POST | `/app/user/self/app` | yes |  |
| enterprise/index | updateApp | PUT | `/app/user/update` | yes |  |
| enterprise/index | queryStstemAdmin | GET | `/system/admin/query/admin` |  |  |
| enterprise/index | addSystemDepchild | POST | `/system/dept` | yes |  |
| enterprise/index | editDeptName | PUT | `/system/dept` | yes |  |
| enterprise/index | clearDeptPrincipal | DELETE | `/system/dept/{dynamic}/principal` |  | yes |
| enterprise/index | setDeptPrincipal | PUT | `/system/dept/{dynamic}/principal` | yes |  |
| enterprise/index | getTreeMenu | GET | `/system/dept/admin/tree` |  | yes |
| enterprise/index | deptsort | GET | `/system/dept/sort` |  | yes |
| enterprise/index | getRoleListClass | GET | `/system/role_group/group_list` |  | yes |
| enterprise/index | updateRoleGroup | POST | `/system/role_group/group_update` | yes |  |
| enterprise/index | addRoleinMember | POST | `/system/role/add_member` | yes |  |
| enterprise/index | getRoleByAdmin | GET | `/system/role/admin/list` |  |  |
| enterprise/index | insertRole | POST | `/system/role/insert` | yes |  |
| enterprise/index | getRoleMemberList | GET | `/system/role/list_member` |  | yes |
| enterprise/index | deleteRoleinMember | DELETE | `/system/role/remove_member` | yes |  |
| enterprise/index | updateRole | POST | `/system/role/update` | yes |  |
| enterprise/index | getDismissList | GET | `/system/user/dismiss_list` |  | yes |
| enterprise/index | getUserList | GET | `/system/user/list` |  | yes |
| eos/analysis/analyst | businessSale | GET | `/project/analysis/business/sale` |  | yes |
| eos/business/business | addBusiness | POST | `/project/business` | yes |  |
| eos/business/business | updateBusiness | PUT | `/project/business` | yes |  |
| eos/business/business | delBusiness | DELETE | `/project/business/{dynamic}` |  |  |
| eos/business/business | getBusiness | GET | `/project/business/{dynamic}` |  |  |
| eos/business/business | listBusiness | GET | `/project/business/list` |  | yes |
| eos/business/business | addBusinessStage | POST | `/project/business/stage/insert` | yes |  |
| eos/crm/contracts | requestContract | GET | `{dynamic}/{dynamic}{dynamic}` |  |  |
| eos/crm/contracts | listContractCustomers | GET | `{dynamic}/customers` |  | yes |
| eos/crm/index | getCustomerAnalysis | GET | `/project/crm/analysis/overview` |  |  |
| eos/crm/index | listCustomerRisks | GET | `/project/crm/risk/list` |  | yes |
| eos/crm/index | syncCustomerRisks | POST | `/project/crm/risk/sync` |  |  |
| eos/crm/index | addCustomer | POST | `/project/merchant` | yes |  |
| eos/crm/index | updateCustomer | PUT | `/project/merchant` | yes |  |
| eos/crm/index | deleteCustomer | DELETE | `/project/merchant/{dynamic}` |  |  |
| eos/crm/index | getCustomer | GET | `/project/merchant/{dynamic}` |  |  |
| eos/crm/index | assignCustomer | POST | `/project/merchant/{dynamic}/assign` | yes |  |
| eos/crm/index | claimCustomer | POST | `/project/merchant/{dynamic}/claim` |  |  |
| eos/crm/index | addFollowup | POST | `/project/merchant/{dynamic}/followups` | yes |  |
| eos/crm/index | listFollowups | GET | `/project/merchant/{dynamic}/followups` |  |  |
| eos/crm/index | releaseCustomer | POST | `/project/merchant/{dynamic}/release` |  |  |
| eos/crm/index | exportCustomers | GET | `/project/merchant/export` |  | yes |
| eos/crm/index | deleteFollowup | DELETE | `/project/merchant/followup/{dynamic}` |  |  |
| eos/crm/index | handoverCustomers | POST | `/project/merchant/handover` | yes |  |
| eos/crm/index | importCustomers | POST | `/project/merchant/import` | yes |  |
| eos/crm/index | listCustomers | GET | `/project/merchant/list` |  | yes |
| eos/crm/index | listCustomerOptions | GET | `/project/merchant/options` |  | yes |
| eos/crm/index | listCustomerOwners | GET | `/project/merchant/owner/options` |  | yes |
| eos/merchant/merchant | addMerchant | POST | `/project/merchant` | yes |  |
| eos/merchant/merchant | updateMerchant | PUT | `/project/merchant` | yes |  |
| eos/merchant/merchant | getMerchant | GET | `/project/merchant/{dynamic}` |  |  |
| eos/merchant/merchant | deleteMerchatAccount | DELETE | `/project/merchant/account/{dynamic}` |  |  |
| eos/merchant/merchant | deleteMerchatUser | DELETE | `/project/merchant/user/{dynamic}` |  |  |
| processDesign/index | detailProcess | POST | `/activiti/mongo/detailProcess` | yes |  |
| processDesign/index | setProcess | POST | `/activiti/mongo/setProcess` | yes |  |
| processDesign/index | subumitProcess | POST | `/activiti/mongo/submitProcess` | yes |  |
| processDesign/index | getTaskCopyList | GET | `/activiti/mongoWorkflow/taskCopyList` |  | yes |
| processDesign/index | getTaskDoneList | GET | `/activiti/mongoWorkflow/taskDoneList` |  | yes |
| processDesign/index | getMongoWorkflowList | GET | `/activiti/mongoWorkflow/taskStartList` |  | yes |
| processDesign/index | getTaskListCount | GET | `/activiti/mongoWorkflow/taskWaitCount` |  | yes |
| processDesign/index | getTaskList | GET | `/activiti/mongoWorkflow/taskWaitList` |  | yes |
| processDesign/index | activitiProcess | POST | `/activiti/process/complete` | yes |  |
| processDesign/index | dataCreate | POST | `/data/create` | yes |  |
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
| system/dept | delDept | DELETE | `/system/dept/{dynamic}` |  |  |
| system/dept | allTree | GET | `/system/dept/allTree` |  |  |
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
| system/log/logininfor | delLogininfor | DELETE | `/monitor/logininfor/{dynamic}` |  |  |
| system/log/logininfor | cleanLogininfor | DELETE | `/monitor/logininfor/clean` |  |  |
| system/log/logininfor | exportLogininfor | GET | `/monitor/logininfor/export` |  | yes |
| system/log/logininfor | list | GET | `/monitor/logininfor/list` |  | yes |
| system/log/operlog | delOperlog | DELETE | `/monitor/operlog/{dynamic}` |  |  |
| system/log/operlog | cleanOperlog | DELETE | `/monitor/operlog/clean` |  |  |
| system/log/operlog | exportOperlog | GET | `/monitor/operlog/export` |  | yes |
| system/log/operlog | list | GET | `/monitor/operlog/list` |  | yes |
| system/menu | roleMenuTree | GET | `/app/user/roleMenuTree` |  | yes |
| system/menu | addMenu | POST | `/system/module` | yes |  |
| system/menu | updateMenu | PUT | `/system/module` | yes |  |
| system/menu | delMenu | DELETE | `/system/module/{dynamic}` |  |  |
| system/menu | getMenu | GET | `/system/module/{dynamic}` |  |  |
| system/menu | createModuleAction | POST | `/system/module/{dynamic}/actions` | yes |  |
| system/menu | getModuleActions | GET | `/system/module/{dynamic}/actions` |  | yes |
| system/menu | deleteModuleAction | DELETE | `/system/module/{dynamic}/actions/{dynamic}` |  |  |
| system/menu | updateModuleAction | PUT | `/system/module/{dynamic}/actions/{dynamic}` | yes |  |
| system/menu | getNextModuleActionCode | GET | `/system/module/{dynamic}/actions/next-code` |  |  |
| system/menu | getModuleManagementTree | GET | `/system/module/management-tree` |  |  |
| system/notice | saveMessage | POST | `/system/message` | yes |  |
| system/notice | addNotice | POST | `/system/notice` | yes |  |
| system/notice | updateNotice | PUT | `/system/notice` | yes |  |
| system/notice | delNotice | DELETE | `/system/notice/{dynamic}` |  |  |
| system/notice | getNotice | GET | `/system/notice/{dynamic}` |  |  |
| system/notice | listNotice | GET | `/system/notice/list` |  | yes |
| system/post | listPostAll | GET | `/system/post/list/all` |  | yes |
| system/role | delRoleGroup | DELETE | `/system/role_group/group_remove/{dynamic}` |  |  |
| system/role | listRoleAll | GET | `/system/role/list/all` |  | yes |
| system/role | delSaasRole | DELETE | `/system/role/remove/{dynamic}` |  |  |
| system/user | commonUpload | POST | `/common/upload` | yes |  |
| system/user | delUser | DELETE | `/system/user/{dynamic}` |  |  |
| system/user | editUser | PUT | `/system/user/edit` | yes |  |
| system/user | excelImport | POST | `/system/user/excel_import` | yes |  |
| system/user | queryAll | GET | `/system/user/list/queryAll` |  | yes |
| system/user | getUserProfile | GET | `/system/user/profile` |  |  |
| system/user | updateUserProfile | PUT | `/system/user/profile` | yes |  |
| system/user | uploadAvatar | POST | `/system/user/profile/avatar` | yes |  |
| system/user | updateUserPwd | PUT | `/system/user/profile/updatePwd` |  | yes |
| user/app | getAppList | GET | `/app/user/list` |  | yes |
| user/enterprise | addEnterprise | POST | `/crm/company/add` | yes |  |
| user/enterprise | toggleEnterprise | GET | `/crm/company/change/{dynamic}` |  |  |
| user/enterprise | getEnterprise | GET | `/crm/company/getCompany` |  | yes |
| user/login | getSession | GET | `/auth/session` |  |  |
| user/login | getCodeImg | GET | `/captchaImage` |  |  |
| user/module | getRouters | GET | `/getRouters` |  |  |
| user/module | resolveModuleContexts | GET | `/system/module-context` |  | yes |
| user/registered | registered | POST | `/crm/user/register` | yes |  |
| workflow/action | executeWorkflowAction | POST | `/workflow/action/execute` | yes |  |
| workflow/action | getWorkflowActionHandlers | GET | `/workflow/action/handlers` |  |  |
