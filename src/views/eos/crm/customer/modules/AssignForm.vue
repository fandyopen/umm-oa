<template>
  <a-form-model :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
    <a-form-model-item label="客户负责人" required>
      <a-select v-model="ownerUserId" show-search :filter-option="false" placeholder="请选择负责人" @search="searchOwners">
        <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
          {{ owner.nickName }} · {{ owner.deptName || '未分配部门' }}
        </a-select-option>
      </a-select>
    </a-form-model-item>
    <a-form-model-item label="协作人员">
      <a-select v-model="collaboratorIds" mode="multiple" show-search :filter-option="false" placeholder="选择协作人员" @search="searchOwners">
        <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
          {{ owner.nickName }}
        </a-select-option>
      </a-select>
    </a-form-model-item>
    <div class="assign-actions">
      <a-button @click="$emit('close')">取消</a-button>
      <a-button type="primary" :loading="saving" @click="submit">确认分配</a-button>
    </div>
  </a-form-model>
</template>

<script>
import { assignCustomer, listCustomerOwners } from '/src/api/eos/crm/index.js'

export default {
  name: 'AssignCustomer',
  props: {
    customer: { type: Object, required: true }
  },
  data() {
    return {
      owners: [],
      ownerUserId: this.customer.ownerUserId || null,
      collaboratorIds: [],
      saving: false
    }
  },
  mounted() {
    this.loadOwners()
    this.$emit('set-actions', [
      { label: '取消', action: () => this.$emit('close') }
    ])
  },
  methods: {
    loadOwners(keyword = '') {
      listCustomerOwners({ keyword }).then(res => {
        this.owners = res.data || []
      })
    },
    searchOwners(keyword) {
      this.loadOwners(keyword)
    },
    submit() {
      if (!this.ownerUserId) {
        this.$message.warning('请选择客户负责人')
        return
      }
      this.saving = true
      assignCustomer(this.customer.id, {
        ownerUserId: this.ownerUserId,
        collaboratorIds: this.collaboratorIds
      })
        .then(() => {
          this.$message.success('客户分配已更新')
          this.$emit('ok')
          this.$emit('close')
        })
        .catch(error => this.$message.error(error.message || String(error)))
        .finally(() => {
          this.saving = false
        })
    }
  }
}
</script>

<style scoped>
.assign-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
