<template>
  <article class="recruitment-rules">
    <h1>招聘流程规则</h1>
    <p class="lead">从职位需求到保证期交付，按实际业务发生顺序登记。部门看板用于了解跟进和节点成果，统计不代替业务凭证，也不自动认定提成。</p>
    <div class="flow"><template v-for="(step, index) in guide.flow"><a-icon v-if="index" :key="index + '-arrow'" type="right"/><span :key="step">{{ step }}</span></template></div>
    <a-tabs>
      <a-tab-pane key="workflow" tab="办理流程与业务规则">
        <h2>办理前准备</h2><ul><li v-for="item in guide.prerequisites" :key="item">{{ item }}</li></ul>
        <a-collapse :default-active-key="[guide.workflows[0].name]">
          <a-collapse-panel v-for="workflow in guide.workflows" :key="workflow.name" :header="workflow.name">
            <p class="entry">{{ workflow.entry }}</p><p class="muted">办理人员：{{ workflow.roles }}</p>
            <ol><li v-for="step in workflow.steps" :key="step.title"><strong>{{ step.title }}</strong><p>{{ step.description }}</p></li></ol>
            <a-alert type="info" :message="workflow.outcome"/>
            <ul v-if="workflow.tips.length" class="tips"><li v-for="tip in workflow.tips" :key="tip">{{ tip }}</li></ul>
          </a-collapse-panel>
        </a-collapse>
      </a-tab-pane>
      <a-tab-pane key="monitor" tab="部门看板与统计口径">
        <a-alert type="info" show-icon message="入口：招聘管理 → 部门看板。角色勾选该模块后可以访问，统计和明细均受部门数据范围限制。"/>
        <h2>1. 主管如何查看</h2>
        <p>按部门、人员、客户及职位筛选。人员跟进表查看当前工作量、本期跟进人选、超期和停滞事项；排行榜可切换推荐、面试、Offer、入职和保证期结案。所有非零数字均可点击查看明细，展开行可查看跟进内容、节点记录及实际操作人。</p>
        <p>当前状态、待处理、超期及停滞按查询时点统计，不受成果日期限制；本周、本月或自定义日期只影响本期跟进人数和节点成果。客户或职位筛选后，仅统计关联招聘流程和异常事项，未绑定职位的人才联系、普通跟进任务不会混入。</p>
        <h2>2. 三天停滞规则</h2>
        <p>连续 3 天（72 小时）没有有效跟进即标记停滞；没有有效跟进时，从记录创建时间开始。只监控仍在推进的流程、待联系／沟通中人才、未完成任务及异常事项。已结束流程不会仅因长期未更新一直报警，但未结的后续事项仍须处理。</p>
        <p>有效跟进包括人才联系、正式推荐、客户反馈、面试安排／通知／结果、已发放 Offer 的处理、入职及保证期业务办理。单纯查看、导出、上传附件、修改档案或保存 Offer 草稿不重置计时。招聘流程可引用创建后的候选人联系记录；跟进任务和异常事项各自计时，其他事项的更新不会掩盖它们尚未完成。</p>
        <p>停滞和超期分开判断：即使今天已催客户，只要反馈期限已经过且结果未落实，仍提示超期。另监控面试时间已过未登记结果、待定面试结果超过三天、预计入职日期已过、保证期已到期未处理及跟进任务超期。长期保证期也按三天规则提示回访，不自动更改业务状态。</p>
        <h2>3. 节点排行榜如何计算</h2>
        <a-table :data-source="metrics" :pagination="false" row-key="name" size="small"><a-table-column title="指标" data-index="name" :width="140"/><a-table-column title="统计规则" data-index="rule"/></a-table>
        <h2>4. 人员归属与历史记录</h2>
        <p>招聘节点按本次流程的负责顾问归属；人才联系按人才负责人归属，任务及异常按各自负责人归属。明细单独展示实际操作人，主管代录不会把顾问成果变成主管成果。本期跟进以“负责人＋候选人”去重，同一候选人多次联系不重复增加人数。</p>
        <p>节点按人岗流程统计，一位候选人先后参加不同职位可产生多条招聘成果；多轮面试、重复发放 Offer、重复编辑和更正不重复加分。排行榜相同数量并列排名，零成果人员仍可在人员表查看当前负荷。</p>
        <p>推荐、面试、Offer 和结案使用首次可靠节点记录时间，入职使用当前确认的实际入职日期。尚未形成可靠历史记录的节点不通过当前阶段或修改时间猜测。成果发生后退出不会抹掉历史节点，但当前推进人数会随真实阶段变化；免费替换形成独立招聘工作量，收入和提成不能因此重复计提。</p>
        <p>看板按人员当前部门及现有负责关系统计，尚不支持过去任意日期的部门／负责人快照。人员调岗或负责人变更后，查询归属可能变化；财务提成仍以已确认的独立业绩归属为准。</p>
      </a-tab-pane>
    </a-tabs>
  </article>
</template>

<script>
import { recruitmentGuide } from '../recruitmentGuide.js'
export default {
  name: 'RecruitmentRules',
  data: () => ({
    guide: recruitmentGuide,
    metrics: [
      { name: '本期跟进人选', rule: '所选日期内发生有效跟进的人选，按负责人＋候选人去重。新建档案、查看、导出和重复编辑不算有效跟进。' },
      { name: '正式推荐', rule: '首次正式推荐的人岗流程数，撤回再推荐不重复计数。没有事件的历史记录仅在存在正式推荐日期时使用该日期。未提交草稿不计。' },
      { name: '完成面试', rule: '首次登记面试结果的人岗流程数；通过、未通过和待定均表示实际完成过面试。多轮及补充结果不重复计数。' },
      { name: '发放 Offer', rule: '首次登记发放 Offer 的人岗流程数，草稿不计；历史无事件时使用已登记的发放日期。撤回重发不重复计数。' },
      { name: '实际入职', rule: '按实际入职日期统计确认入职的人岗流程数。接受 Offer 或填写预计日期不计，更正日期后归入更正后的期间。' },
      { name: '保证期结案', rule: '首次实际办理正常期满结案的流程数。到达保证期结束日期但尚未办理不计，离职、替换及退费不算正常结案。' },
      { name: '待处理／风险事项', rule: '按独立事项计数，同一候选人可以同时有招聘流程和跟进任务。超期与停滞可能重叠，不能把两者直接相加当作总人选数。' }
    ]
  })
}
</script>

<style scoped>
.recruitment-rules { max-width: 1200px; margin: 0 auto; color: #344455; line-height: 1.85; }
h1 { font-size: 26px; } h2 { font-size: 18px; margin: 25px 0 10px; }
.lead, .muted { color: #7b8794; }
.flow { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 16px; margin: 18px 0; background: #f5f9fa; border-radius: 6px; }
.flow span { font-size: 12px; } .flow .anticon { color: #9ba7b0; font-size: 10px; }
ol { padding-left: 22px; } ol li { padding: 5px 0; } ol p { margin: 4px 0; }
.entry { color: #168b83; } .tips { margin-top: 15px; }
</style>
