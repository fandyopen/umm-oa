import Vue from 'vue'
import ProcessHistory from '/src/components/Activiti/HistoryG6Pro.vue'
import MerchantForm from '/src/views/eos/merchant/modules/MerchantForm.vue'

/**
 * 是否是管理项目（运营项目）
 * @param {String} projectType 项目类型
 * @returns
 */
export const isManageProject = projectType => {
  return projectType !== undefined && projectType !== null && projectType.indexOf('运营') !== -1
}

export const handleProcessHistory = instanceId => {
  if (Vue.prototype.isEmpty(instanceId)) {
    console.log('审批历史InstanceId为：', instanceId)
    return
  }
  Vue.prototype.$createAntdDrawer({
    stopPropagation: true,
    drawerProps: {
      title: '审批历史',
      width: '50%',
      maskClosable: true
    },
    content: {
      template: ProcessHistory,
      props: {
        instanceId
      }
    }
  })
}

// 新增、编辑客商
export const handleMerchantEdit = (name, merchantList, callBackFunc) => {
  let updateRow = null
  if (!Vue.prototype.isEmpty(name)) {
    const tmpArr = merchantList.filter(item => item.name === name)
    if (!Vue.prototype.isEmpty(tmpArr)) {
      updateRow = tmpArr[0]
    }
  }
  // 客商单位
  Vue.prototype.$createAntdDrawer({
    stopPropagation: true,
    drawerProps: {
      title: '添加/完善单位信息',
      width: '50%',
      maskClosable: true
    },
    content: {
      template: MerchantForm,
      props: {
        isUpdate: !Vue.prototype.isEmpty(updateRow),
        updateRow
      },
      on: {
        ok: () => {
          if (callBackFunc instanceof Function) {
            callBackFunc(name)
          }
        }
      }
    }
  })
}

/**
 * 业务流程-业务表单映射关系
 */
export const busFlowMap = {
  lowcode: {
    processName: '自定义审批',
    processColor: 'blue',
    busFieldProp: 'title',
    busDetailCompName: 'LowCodeTodoDetail'
  },
  workflowAction: {
    processName: '业务变更审批',
    processColor: 'orange',
    busFieldProp: 'title',
    busFieldProp2: 'actionName',
    busDetailCompName: 'WorkflowActionTodoDetail'
  }
}
