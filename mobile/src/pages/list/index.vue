<template>
  <app-shell :title="info.title || '业务列表'" back>
    <view class="row">
      <view>
        <view class="h1">{{ info.title }}</view>
        <view class="muted">与平台业务数据实时同步</view>
      </view>
      <button v-if="canAdd" class="btn" @click="$go('form', { module: key, action: 'add' })">新增</button>
    </view>
    <view class="spacer" />
    <view v-if="!info.self" class="search">
      <input v-model="keyword" placeholder="搜索关键词" @confirm="search" />
      <button class="secondary" @click="search">搜索</button>
    </view>
    <view v-if="error" class="error" @click="load">{{ error }} · 点击重试</view>
    <view v-for="(row, index) in rows" :key="index" class="card listcard" @click="open(row)">
      <view class="row">
        <text class="h3">{{ title(row) }}</text>
        <text class="tag" v-if="row.status || row.stage || row.paymentStatus">
          {{ label(row.stage || row.status || row.paymentStatus) }}
        </text>
      </view>
      <view v-if="key === 'applications'" class="muted">{{ substate(row) }}</view>
      <view class="muted">{{ subtitle(row) }}</view>
      <view class="row">
        <text class="muted">{{ row.ownerUserName || row.consultantName || row.businessDate || row.createTime || '' }}</text>
        <text v-if="row.amount !== undefined || row.outstandingAmount !== undefined" class="h3">
          ￥{{ money(row.outstandingAmount === undefined ? row.amount : row.outstandingAmount) }}
        </text>
        <text v-else-if="info.detail || info.self" class="link">查看 →</text>
      </view>
      <view v-if="key === 'ledger'" class="actions">
        <button v-if="canReceipt" class="secondary" @click.stop="$go('form', { module: 'receipt', action: 'add', source: row.sourceKey })">
          登记回款
        </button>
      </view>
    </view>
    <view v-if="!rows.length" class="card empty">{{ loading ? '正在加载…' : error ? '加载失败' : '暂无记录' }}</view>
    <view class="pagination">
      <button
        class="secondary"
        :disabled="page === 1 || loading"
        @click="
          page--
          load()
        "
      >
        上一页
      </button>
      <text>{{ page }} / {{ pages }}</text>
      <button
        class="secondary"
        :disabled="page >= pages || loading"
        @click="
          page++
          load()
        "
      >
        下一页
      </button>
    </view>
  </app-shell>
</template>
<script>
import { catalog, pageRows } from '../../services/catalog'
import { requireModule, request, can, state } from '../../services/runtime'
import { label, applicationSubstate } from '../../services/business'
export default {
  data: () => ({
    key: '',
    info: {},
    rows: [],
    page: 1,
    total: 0,
    keyword: '',
    customerId: '',
    error: '',
    loading: false,
    state,
    loadVersion: 0
  }),
  computed: {
    pages() {
      return Math.max(1, Math.ceil(this.total / 20))
    },
    canAdd() {
      return this.info.create && can(this.key, 'add')
    },
    canReceipt() {
      return can('receipt', 'add')
    }
  },
  onLoad(q) {
    this.key = q.module
    this.info = catalog[this.key] || {}
    this.customerId = q.customerId || ''
  },
  onShow() {
    this.load()
  },
  methods: {
    label,
    substate: applicationSubstate,
    money(v) {
      return Number(v || 0).toFixed(2)
    },
    title(r) {
      return (
        r.name ||
        r.title ||
        [r.talentName, r.jobTitle].filter(Boolean).join(' · ') ||
        r.customerName ||
        r.period ||
        r.month ||
        r.businessDate ||
        '业务记录'
      )
    },
    subtitle(r) {
      return [r.customerName, r.jobTitle, r.contractCode, r.termTitle, r.industry, r.city, r.reason].filter(Boolean).join(' · ')
    },
    search() {
      this.page = 1
      this.load()
    },
    async load() {
      const version = ++this.loadVersion
      this.loading = true
      this.error = ''
      this.rows = []
      try {
        await requireModule(this.key)
        const r = pageRows(
          await request(this.info.list, 'GET', {
            pageNum: this.page,
            pageSize: 20,
            customerId: this.customerId,
            [this.info.search || 'keyword']: this.keyword
          })
        )
        if (version !== this.loadVersion) return
        if (this.info.clientPage) {
          r.rows = r.rows.filter((row) => JSON.stringify(row).includes(this.keyword.trim()))
          r.total = r.rows.length
        }
        this.rows = this.info.self || this.info.clientPage ? r.rows.slice((this.page - 1) * 20, this.page * 20) : r.rows
        this.total = r.total
      } catch (e) {
        if (version === this.loadVersion) this.error = e.message
      } finally {
        if (version === this.loadVersion) this.loading = false
      }
    },
    open(row) {
      if (this.key === 'risks')
        return this.$go('detail', { module: row.contractId ? 'contracts' : 'customers', id: row.contractId || row.customerId })
      if (this.info.detail || this.info.self) this.$go('detail', { module: this.key, id: row.id })
    }
  }
}
</script>
