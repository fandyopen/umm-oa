<template>
  <a-dropdown :trigger="['click']">
    <a-button icon="download">模板与报表格式<a-icon type="down" /></a-button>
    <a-menu slot="overlay" class="tw-max-h-[70vh] tw-overflow-y-auto" @click="download">
      <a-menu-item key="all"><a-icon type="file-excel" />下载全部模板（一个工作簿）</a-menu-item>
      <a-menu-divider />
      <a-menu-item-group v-for="group in groups" :key="group.label" :title="group.label">
        <a-menu-item v-for="type in group.types" :key="type">{{ templates[type].name }}</a-menu-item>
      </a-menu-item-group>
    </a-menu>
  </a-dropdown>
</template>
<script>
import { templates, templateGroups, exportTemplate, downloadTemplatePack } from '/src/views/enterprise/hr/templates.js'
export default {
  name: 'HrTemplateDownloads',
  data: () => ({ templates, groups: templateGroups }),
  methods: {
    async download({ key }) {
      if (key === 'all') await downloadTemplatePack()
      else await exportTemplate(key)
      if (key === 'payroll' || key === 'all') this.$message.info('工资回传请使用具体工资草稿导出的文件，保留批次与版本。')
    }
  }
}
</script>
