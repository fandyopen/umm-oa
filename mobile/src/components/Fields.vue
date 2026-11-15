<template>
  <view>
    <view v-for="field in fields" :key="field.key" class="field">
      <text class="fieldlabel">
        {{ field.label }}
        <text v-if="field.required" class="required">*</text>
      </text>
      <view v-if="isSelect(field)" class="control">
        <view class="row" @click="toggle(field)">
          <text>{{ selectedLabel(field) || '请选择' }}</text>
          <text class="muted">⌄</text>
        </view>
        <view v-if="expanded === field.key">
          <input :value="search" @input="search = $event.detail.value" placeholder="输入关键词搜索" />
          <view v-for="option in filtered(field)" :key="option.value" class="kv" @click="select(field, option)">
            <text :class="{ muted: option.disabled }">{{ option.label }}{{ picked(field, option) ? ' ✓' : '' }}</text>
          </view>
          <view v-if="!filtered(field).length" class="notice">无可选项</view>
          <text class="link" @click="expanded = ''">完成选择</text>
        </view>
      </view>
      <textarea
        v-else-if="field.type === 'textarea'"
        class="control"
        :disabled="field.disabled"
        :value="value[field.key] || ''"
        :maxlength="field.max || 16000"
        @input="update(field.key, $event.detail.value)"
        placeholder="请填写"
      />
      <view v-else-if="field.type === 'date' || field.type === 'datetime'" class="row">
        <picker
          mode="date"
          :disabled="field.disabled"
          :value="datePart(value[field.key])"
          @change="changeDate(field, $event.detail.value)"
          class="control"
          style="flex: 1"
        >
          <view>{{ datePart(value[field.key]) || '选择日期' }}</view>
        </picker>
        <picker
          v-if="field.type === 'datetime'"
          mode="time"
          :disabled="field.disabled"
          :value="timePart(value[field.key])"
          @change="changeTime(field, $event.detail.value)"
          class="control"
        >
          <view>{{ timePart(value[field.key]) || '选择时间' }}</view>
        </picker>
        <text v-if="!field.required" class="link" @click="update(field.key, '')">清空</text>
      </view>
      <view v-else-if="field.type === 'upload'" class="control">
        <view v-for="(file, index) in files(field)" :key="index" class="kv">
          <text>{{ file.name }}</text>
          <text class="link" @click="remove(field, index)">移除</text>
        </view>
        <button class="secondary" :loading="uploading" :disabled="uploading" @click="upload(field)">上传图片凭证</button>
      </view>
      <input
        v-else
        class="control"
        :type="field.type === 'number' ? 'digit' : 'text'"
        :disabled="field.disabled"
        :value="value[field.key] === undefined ? '' : String(value[field.key])"
        :maxlength="field.max || 2000"
        @input="update(field.key, field.type === 'number' ? numeric($event.detail.value) : $event.detail.value)"
        placeholder="请填写"
      />
      <view v-if="field.hint" class="notice">{{ field.hint }}</view>
    </view>
  </view>
</template>
<script>
import { chooseUpload } from '../services/runtime'
export default {
  props: { fields: Array, value: Object },
  data: () => ({ expanded: '', search: '', uploading: false }),
  methods: {
    update(k, v) {
      this.$emit('input', { ...this.value, [k]: v })
    },
    numeric(v) {
      return v === '' ? '' : Number(v)
    },
    isSelect(f) {
      return ['select', 'multiple', 'contract'].includes(f.type)
    },
    toggle(f) {
      if (f.disabled) return
      this.expanded = this.expanded === f.key ? '' : f.key
      this.search = ''
    },
    choices(f) {
      const rows = (f.options || []).map((o) => ({
        ...o,
        value: o.value === undefined ? o.id : o.value,
        label: o.label || o.name || o.title
      }))
      return f.type === 'contract'
        ? rows.filter((o) => !this.value.customerId || String(o.customerId) === String(this.value.customerId))
        : rows
    },
    filtered(f) {
      return this.choices(f)
        .filter((o) => String(o.label).toLowerCase().includes(this.search.toLowerCase()))
        .slice(0, 100)
    },
    picked(f, o) {
      return f.type === 'multiple'
        ? (this.value[f.key] || []).some((v) => String(v) === String(o.value))
        : String(this.value[f.key]) === String(o.value)
    },
    selectedLabel(f) {
      return this.choices(f)
        .filter((o) => this.picked(f, o))
        .map((o) => o.label)
        .join('、')
    },
    select(f, o) {
      if (f.disabled || o.disabled) return
      let v = o.value
      if (f.type === 'multiple') {
        const old = this.value[f.key] || []
        v = this.picked(f, o) ? old.filter((x) => String(x) !== String(o.value)) : [...old, o.value]
      } else this.expanded = ''
      this.update(f.key, v)
    },
    datePart(v) {
      return v ? String(v).slice(0, 10) : ''
    },
    timePart(v) {
      return v ? String(v).slice(11, 16) : ''
    },
    changeDate(f, v) {
      this.update(f.key, f.type === 'datetime' ? v + ' ' + (this.timePart(this.value[f.key]) || '09:00') + ':00' : v)
    },
    changeTime(f, v) {
      this.update(f.key, (this.datePart(this.value[f.key]) || new Date().toISOString().slice(0, 10)) + ' ' + v + ':00')
    },
    files(f) {
      try {
        return JSON.parse(this.value[f.key] || '[]')
      } catch (e) {
        return []
      }
    },
    remove(f, i) {
      this.update(f.key, JSON.stringify(this.files(f).filter((_, index) => index !== i)))
    },
    async upload(f) {
      this.uploading = true
      try {
        const r = await chooseUpload()
        this.update(
          f.key,
          JSON.stringify([...this.files(f), { name: r.originalFilename || r.newFileName || '业务凭证', url: r.url, fileName: r.fileName }])
        )
      } catch (e) {
        this.$toast(e.message)
      } finally {
        this.uploading = false
      }
    }
  }
}
</script>
