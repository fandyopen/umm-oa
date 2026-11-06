<template>
  <div>
    <div class="filters">
      <a-date-picker v-model="query.startDate" value-format="YYYY-MM-DD" placeholder="应收到期起" @change="load" /><a-date-picker
        v-model="query.endDate"
        value-format="YYYY-MM-DD"
        placeholder="应收到期止"
        @change="load"
      /><a-button icon="reload" @click="load">刷新</a-button>
    </div>
    <a-alert type="info" show-icon :message="basis" class="tw-mb-3.5" />
    <a-table :data-source="aging" row-key="bucket" :pagination="false" size="small" class="aging"
      ><a-table-column title="应收账龄" data-index="bucket" /><a-table-column title="未结金额" data-index="amount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      ></a-table
    >
    <a-table :loading="loading" :data-source="rows" row-key="customerId" :pagination="false" size="small" :scroll="{ x: 1250 }">
      <a-table-column title="客户" data-index="customerName" fixed="left" width="180" />
      <a-table-column title="应收" data-index="receivableAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="已开票" data-index="invoicedAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="到账净额" data-index="netReceivedAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="退款" data-index="refundedAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="核销" data-index="writeoffAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="未收" data-index="outstandingAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="直接成本" data-index="costAmount"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
      <a-table-column title="净现金贡献" data-index="cashContribution"
        ><template slot-scope="v">{{ money(v) }}</template></a-table-column
      >
    </a-table>
  </div>
</template>
<script>
import { getFinanceReport } from '/src/api/eos/crm/finance.js'
export default {
  name: 'FinanceReconcile',
  data: () => ({ rows: [], aging: [], basis: '', loading: false, query: { startDate: undefined, endDate: undefined } }),
  methods: {
    money(v) {
      return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    },
    async load() {
      this.loading = true
      try {
        const r = await getFinanceReport(this.query)
        this.rows = r.data?.rows || []
        this.aging = r.data?.aging || []
        this.basis = r.data?.basis || ''
        this.$emit('summary', r.data?.summary || {})
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
<style scoped>
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.aging {
  max-width: 480px;
  margin-bottom: 18px;
}
</style>
