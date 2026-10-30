<template>
  <div>
    <div class="manager-group-name">{{ title }}</div>
    <div class="group-config">
      <div class="config-line">
        <div class="line-item left">超级管理员</div>
        <div class="line-item right">
          <div class="item-content">
            <div class="adminEdit">
              <div class="admin-wallperpar">
                <span class="admin-item" v-for="item in superAdminList" :key="item.userId">
                  <a-icon type="user" />
                  {{ item.nickName }}
                  <span v-if="item.phonenumber">（{{ item.phonenumber }}）</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="config-line">
        <div class="line-item left">应用权限</div>
        <div class="line-item right">具备对所有应用的管理权限</div>
      </div>
      <div class="config-line">
        <div class="line-item left">组织权限</div>
        <div class="line-item right">具备对所有部门、成员和角色的管理权限</div>
      </div>
      <div class="config-line">
        <div class="line-item left">说明</div>
        <div class="line-item right">超级管理员为系统固定账号；其他人员的管理权限统一通过角色授权。</div>
      </div>
    </div>
  </div>
</template>

<script>
import { queryStstemAdmin } from '/src/api/enterprise/index.js'

export default {
  props: {
    title: {
      type: String,
      default: '超级管理员'
    }
  },
  data() {
    return {
      superAdminList: []
    }
  },
  created() {
    queryStstemAdmin().then(res => {
      this.superAdminList = res.data || []
    })
  }
}
</script>

<style lang="less" scoped>
@import './index.less';
</style>
