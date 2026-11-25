<template>
  <figure class="platform-map" aria-labelledby="platform-map-title">
    <figcaption>
      <h3 id="platform-map-title"><a-icon type="branches" /> 平台业务闭环思维导图</h3>
      <p>客户经营带来收入，人事薪酬支撑团队，两者通过提成与工资衔接；待办与审批贯穿业务办理。</p>
    </figcaption>

    <div class="map-root"><a-icon type="apartment" /><strong>平台业务</strong></div>
    <div class="map-branches">
      <section v-for="lane in lanes" :key="lane.title" class="map-lane" :class="lane.kind" :aria-label="lane.title">
        <h4>{{ lane.title }}</h4>
        <ol class="map-nodes">
          <li v-for="node in lane.nodes" :key="node.title" class="map-node">
            <div class="node-heading"><a-icon :type="node.icon" /><strong>{{ node.title }}</strong></div>
            <p>{{ node.summary }}</p>
          </li>
        </ol>
        <p class="map-loop"><a-icon type="reload" />{{ lane.loop }}</p>
      </section>
    </div>

    <div class="map-connection"><a-icon type="link" /><strong>两线衔接</strong><span>提成核算批准 → 纳入工资 → 薪资审批 → 财务发放 → 员工查阅工资条</span></div>
    <p class="map-support"><strong>全程支撑</strong><span>组织与角色权限决定谁能办理 · 部门看板复盘客户经营</span></p>
  </figure>
</template>

<script>
export default {
  name: 'PlatformBusinessMap',
  data: () => ({
    lanes: [
      {
        title: '客户经营闭环', kind: 'business',
        nodes: [
          { title: '客户拓展', icon: 'contacts', summary: '建档分配 → 跟进商机' },
          { title: '合同签约', icon: 'file-done', summary: '审批签署 → 明确收费' },
          { title: '招聘交付', icon: 'team', summary: '职位与人才 → 入职保证期' },
          { title: '财务回款', icon: 'account-book', summary: '核对应收 → 到账对账' },
          { title: '提成核算', icon: 'calculator', summary: '按回款及交付条件核算' }
        ],
        loop: '履约与账款处理后续约／结案，回到客户维护，开启下一轮合作。'
      },
      {
        title: '人事薪酬闭环', kind: 'people',
        nodes: [
          { title: '组织与人事', icon: 'idcard', summary: '成员角色 → 员工档案' },
          { title: '考勤确认', icon: 'calendar', summary: '生成／导入 → 核对确认' },
          { title: '薪资核算', icon: 'calculator', summary: '薪资标准与规则 → 工资审批' },
          { title: '财务发放', icon: 'pay-circle', summary: '实际付款 → 登记核对' },
          { title: '个人查阅', icon: 'solution', summary: '发布工资条 → 员工查看' }
        ],
        loop: '维护人员与薪资变动，进入下一期考勤、核算和发薪。'
      },
      {
        title: '待办与审批闭环（贯穿以上业务）', kind: 'approval',
        nodes: [
          { title: '业务提交', icon: 'form', summary: '需审批的按钮 → 发起申请' },
          { title: '待办接收', icon: 'inbox', summary: '指定办理人 → 我的待办' },
          { title: '审批办理', icon: 'audit', summary: '核对内容 → 通过／退回' },
          { title: '结果回写', icon: 'sync', summary: '审批完成 → 更新业务结果' },
          { title: '进度追踪', icon: 'history', summary: '发起／已办记录 → 核对结果' }
        ],
        loop: '合同、人事、回款、提成等按已发布流程审批；退回后修改重提。普通提醒也进入待办，回到对应业务处理。'
      }
    ]
  })
}
</script>

<style lang="less" scoped>
.platform-map {
  min-width: 0;
  margin: 0 0 24px;
  padding: 20px;
  border: 1px solid #dcece9;
  border-radius: 8px;
  background: #fbfefd;
  color: #475467;

  h3 { margin: 0; color: #17212b; font-size: 17px; font-weight: 600; }
  h3 .anticon { margin-right: 6px; color: #0b9e92; }
  figcaption > p { margin: 6px 0 18px; color: #667085; }
}
.map-root {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  width: fit-content; margin: 0 auto; padding: 9px 24px;
  border-radius: 6px; background: #0aafa2; color: #fff;
}
.map-branches {
  position: relative; display: grid; gap: 16px; padding-top: 20px;
  &::before { content: ''; position: absolute; top: 0; left: 50%; height: 20px; border-left: 2px solid #a8dcd5; }
}
.map-lane {
  min-width: 0; padding: 14px; background: #fff; border: 1px solid #dcece9; border-radius: 6px;
  h4 { margin: 0 0 12px; color: #087c74; font-size: 14px; font-weight: 600; }
}
.map-nodes { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 24px; padding: 0; margin: 0; list-style: none; }
.map-node {
  position: relative; min-width: 0; padding: 12px 10px; border-radius: 5px; background: #eef9f6;
  &:not(:last-child)::after { content: '→'; position: absolute; top: 50%; right: -19px; transform: translateY(-50%); color: #6d9f98; font-size: 17px; }
  p { margin: 6px 0 0; font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
}
.node-heading { display: flex; align-items: center; gap: 6px; color: #087c74; strong { font-weight: 600; } }
.map-loop { display: flex; align-items: baseline; gap: 7px; margin: 12px 0 0; font-size: 12px; color: #667085; }
.people {
  border-color: #dfe8f4;
  h4, .node-heading { color: #27649a; }
  .map-node { background: #f0f5fc; }
  .map-node::after { color: #7c9dc2; }
}
.approval {
  border-color: #eee2ca;
  h4, .node-heading { color: #8a651f; }
  .map-node { background: #fff8eb; }
  .map-node::after { color: #b79b63; }
}
.map-connection { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; padding: 12px; margin-top: 16px; border-radius: 5px; background: #eaf7f4; color: #087c74; }
.map-support { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0 0; font-size: 12px; color: #667085; }
@media (max-width: 1200px) {
  .map-nodes { grid-template-columns: 1fr; gap: 20px; }
  .map-node:not(:last-child)::after { content: '↓'; top: auto; right: 50%; bottom: -23px; transform: translateX(50%); }
}
@media (max-width: 600px) {
  .platform-map { padding: 14px; }
  .map-lane { padding: 12px; }
}
</style>
