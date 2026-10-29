<template>
  <a-form-model :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
    <a-alert
      type="info"
      show-icon
      message="员工离职时，可将其名下客户统一移交给新的负责人。"
      class="handover-alert"
    />
    <a-form-model-item label="原负责人" required>
      <a-select v-model="fromUserId" show-search :filter-option="false" placeholder="请选择原负责人" @search="searchOwners">
        <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
          {{ owner.nickName }} · {{ owner.deptName || '未分配部门' }}
        </a-select-option>
      </a-select>
    </a-form-model-item>
    <a-form-model-item label="新负责人" required>
      <a-select v-model="toUserId" show-search :filter-option="false" placeholder="请选择新负责人" @search="searchOwners">
        <a-select-option v-for="owner in owners" :key="owner.userId" :value="owner.userId">
          {{ owner.nickName }} · {{ owner.deptName || '未分配部门' }}
        </a-select-option>
      </a-select>
    </a-form-model-item>
    <div class="handover-actions">
      <a-button @click="$emit('close')">取消</a-button>
      <a-button type="primary" :loading="saving" @click="submit">确认移交</a-button>
    </div>
  </a-form-model>
</template>

<script>
import { handoverCustomers, listCustomerOwners } from '/src/api/eos/crm/index.js'

export default {
  name: 'CustomerHandover',
  data() {
    return {
      owners: [],
      fromUserId: null,
      toUserId: null,
      saving: false
    }
  },
  mounted() {
    this.loadOwners()
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
      if (!this.fromUserId || !this.toUserId || this.fromUserId === this.toUserId) {
        this.$message.warning('请选择不同的原负责人和新负责人')
        return
      }
      this.saving = true
      handoverCustomers({
        fromUserId: this.fromUserId,
        toUserId: this.toUserId
      })
        .then(res => {
          this.$message.success(`已移交 ${res.data || 0} 个客户`)
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
.handover-alert {
  margin-bottom: 18px;
}

.handover-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
