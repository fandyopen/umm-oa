<template>
  <page-header-wrapper :breadcrumb="$store.getters.breadcrumb">
    <a-card :bordered="false">
      <div class="workspace-head"><div><h2>{{ title }}</h2><p>{{ subtitle }}</p></div><a-button v-if="showModuleAction(kind, 'add')" type="primary" icon="plus" @click="create">{{ kind === 'applications' ? '创建推荐' : kind === 'jobs' ? '新建职位' : '新建候选人' }}</a-button></div>
      <div class="lifecycle">客户需求 → 职位确认 → 人才推荐 → 客户反馈 → 面试 → Offer → 入职 → 保证期结案</div>
      <a-alert v-if="kind === 'jobs'" type="info" show-icon message="需求确认、职位变更沿用按钮级流程；只有流程已发布并启用才进入审批。职位名额与客户招聘统计由实际业务自动更新。" />
      <a-alert v-if="kind === 'talents'" type="info" show-icon message="联系方式按权限脱敏；简历保留版本并单独授权预览和下载。建议先查重，再新建，重复档案可发起合并申请。" />
      <div v-if="kind === 'jobs'" class="metrics"><div v-for="item in metrics" :key="item.label"><small>{{ item.label }}</small><strong>{{ item.value || 0 }}</strong></div></div>
      <div class="filters">
        <a-input-search v-model="query.keyword" placeholder="搜索名称、职位或客户" style="width: 260px" allow-clear @search="refresh" />
        <a-select v-model="query[statusKey]" placeholder="全部状态" allow-clear style="width: 150px" @change="refresh"><a-select-option v-for="item in stateOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select>
        <a-select v-if="kind !== 'talents'" v-model="query.customerId" placeholder="全部客户" show-search :filter-option="filterOption" allow-clear style="width: 190px" @change="refresh"><a-select-option v-for="c in options.customers || []" :key="c.id" :value="c.id">{{ c.name }}</a-select-option></a-select>
        <a-select v-model="query[ownerKey]" placeholder="全部负责人 / 顾问" show-search :filter-option="filterOption" allow-clear style="width: 175px" @change="refresh"><a-select-option v-for="u in options.users || []" :key="u.id" :value="u.id">{{ u.name }}</a-select-option></a-select>
        <a-input v-model="query.startDate" type="date" style="width: 155px" @change="refresh" /><span>至</span><a-input v-model="query.endDate" type="date" style="width: 155px" @change="refresh" />
        <a-button icon="reload" @click="reset">重置</a-button>
      </div>
      <div v-if="kind === 'talents'" class="filters">
        <a-select v-model="query.recruitmentAvailability" placeholder="全部招聘占用" allow-clear style="width: 160px" @change="refresh"><a-select-option v-for="item in availabilityOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select>
        <a-input v-model="query.city" placeholder="城市" style="width: 130px" @pressEnter="refresh" /><a-input v-model="query.industry" placeholder="行业" style="width: 150px" @pressEnter="refresh" /><a-input v-model="query.skills" placeholder="技能" style="width: 160px" @pressEnter="refresh" />
        <a-input-number v-model="query.experienceMin" :min="0" :max="80" placeholder="最低经验年限" style="width: 150px" @change="refresh" />
        <a-select v-model="query.tag" placeholder="全部标签" allow-clear style="width: 160px" @change="refresh"><a-select-option v-for="t in options.tags || []" :key="t.id" :value="t.name">{{ t.name }}</a-select-option></a-select>
        <a-button @click="refresh">组合检索</a-button><a-button @click="saveSearch">保存搜索</a-button>
        <a-select placeholder="常用搜索" allow-clear style="width: 170px" @change="applySearch"><a-select-option v-for="s in options.searches || []" :key="s.id" :value="s.id">{{ s.name }}</a-select-option></a-select>
      </div>
      <div class="toolbar">
        <a-button v-if="kind !== 'applications' && showModuleAction(kind, 'import')" icon="upload" @click="chooseImport">批量导入</a-button>
        <a-button v-if="kind !== 'applications'" @click="downloadTemplate">下载导入模板</a-button>
        <a-button v-if="showModuleAction(kind, 'export')" icon="download" :loading="exporting" @click="exportData">授权导出</a-button>
        <a-button v-if="kind === 'jobs'" :type="duplicateMode ? 'primary' : 'default'" @click="toggleDuplicates">{{ duplicateMode ? '返回职位列表' : '职位查重' }}</a-button>
        <template v-if="kind === 'talents'">
          <a-button :type="duplicateMode ? 'primary' : 'default'" @click="toggleDuplicates">{{ duplicateMode ? '返回人才列表' : '候选人查重' }}</a-button>
          <a-button v-if="showModuleAction('talents', 'tag')" @click="manageTags">标签管理</a-button><a-button v-if="showModuleAction('talents', 'tag')" :disabled="!selected.length" @click="batchTags">批量打标签（{{ selected.length }}）</a-button>
        </template>
        <a-button v-else icon="warning" @click="viewWarnings">招聘预警</a-button>
        <span v-if="query.jobId || query.talentId" class="muted">正在查看指定职位 / 人才的关联流程</span>
      </div>
      <input ref="importFile" type="file" accept=".xlsx,.xls,.csv" style="display: none" @change="importFile" />
      <a-table row-key="id" :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" :row-selection="kind === 'talents' ? { selectedRowKeys: selected, onChange: selectRows } : undefined" :scroll="{ x: tableWidth }" size="middle" @change="changePage">
        <span slot="name" slot-scope="text, row"><a @click="openDetail(row.id)">{{ text }}</a><small class="subline">{{ row.code || (kind === 'applications' ? row.jobTitle : row.skills) }}</small><small v-if="row.duplicateIds && row.duplicateIds.length" class="subline">重复记录 #{{ row.duplicateIds.join(' / #') }}</small></span>
        <span slot="state" slot-scope="value"><a-tag color="blue">{{ stateLabel(value) }}</a-tag></span>
        <span slot="availability" slot-scope="_, row"><a-tag :color="row.recruitmentLocked ? 'orange' : row.canRecommend ? 'green' : undefined">{{ row.canRecommend ? '可推荐' : row.recruitmentAvailabilityLabel }}</a-tag><small v-if="row.recruitmentLocked" class="subline">{{ row.recruitmentStageLabel }}<template v-if="row.recruitmentLockCount > 1"> · {{ row.recruitmentLockCount }} 条流程</template></small><a v-if="row.recruitmentLocked && showModuleAction('applications')" class="subline" @click="viewTalentPipeline(row)">查看招聘流程</a></span>
        <span slot="applicationStage" slot-scope="value, row"><a-tag color="blue">{{ stateLabel(value) }}</a-tag><small v-if="stageSubstatus(row)" class="subline">{{ stageSubstatus(row) }}</small></span>
        <span slot="demand" slot-scope="value"><a-tag>{{ approvalStates[value] || value }}</a-tag></span>
        <span slot="tags" slot-scope="value"><a-tag v-for="t in value || []" :key="t">{{ t }}</a-tag></span>
        <span slot="warnings" slot-scope="value"><a-tag v-for="w in value || []" :key="w" color="orange">{{ w }}</a-tag><span v-if="!value || !value.length">—</span></span>
        <span slot="quota" slot-scope="_, row">{{ row.headcount }} / {{ row.vacancies }}</span>
        <span slot="progress" slot-scope="_, row">{{ row.recommendations }} / {{ row.interviews }} / {{ row.offers }} / {{ row.hired }}<small class="subline">入职完成率 {{ row.completionRate }}% · 招聘 {{ row.recruitingDays }} 天</small><small class="subline">推荐转化：面试 {{ row.interviewRate }}% / Offer {{ row.offerRate }}% / 入职 {{ row.hireRate }}%</small></span>
        <span slot="actions" slot-scope="_, row" class="row-actions">
          <a-button type="link" size="small" @click="openDetail(row.id)">详情</a-button>
          <a-button v-if="hasInterviewResultAction(row)" type="link" size="small" :loading="actionLoadingId === row.id" @click="runRecordAction('interviewResult', row)">{{ row.interviewStatus === 'completed' && row.interviewResult === 'pending' ? '补充结果' : '登记结果' }}</a-button>
          <a-button v-if="kind !== 'applications' && showModuleAction(kind, 'edit')" type="link" size="small" @click="edit(row.id)">修改</a-button>
          <a-button v-if="kind === 'jobs' && showModuleAction(kind, 'copy')" type="link" size="small" @click="copy(row.id)">复制</a-button>
          <a-dropdown v-if="hasRowActions(row)" :trigger="['click']" @visibleChange="prepareRowActions(row, $event)">
            <a-button type="link" size="small" :loading="actionLoadingId === row.id" @click.stop>更多<a-icon type="down" /></a-button>
            <a-menu slot="overlay" @click="({ key }) => runRecordAction(key, row)">
              <a-menu-item v-if="rowMenuLoading && rowMenuId === row.id" key="loading" disabled>正在加载操作…</a-menu-item>
              <template v-else>
                <a-menu-item v-for="action in rowMoreActions(row)" :key="action.key" :class="{ 'danger-action': action.key === 'remove' }">{{ action.label }}</a-menu-item>
                <a-menu-item v-if="!rowMoreActions(row).length" key="empty" disabled>{{ rowMenuError ? '加载失败，请重新打开' : '暂无可用操作' }}</a-menu-item>
              </template>
            </a-menu>
          </a-dropdown>
        </span>
      </a-table>
    </a-card>

    <a-drawer :visible="detailVisible" :title="detail.title || detail.name || (detail.talentName ? detail.talentName + ' · ' + detail.jobTitle : '业务详情')" :width="drawerWidth" @close="detailVisible = false">
      <a-spin :spinning="detailLoading">
        <template v-if="detail.id">
          <div v-if="detailActions.length" class="detail-actions">
            <a-dropdown :trigger="['click']">
              <a-button>更多操作<a-icon type="down" /></a-button>
              <a-menu slot="overlay" @click="({ key }) => runRecordAction(key, detail)">
                <a-menu-item v-for="action in detailActions" :key="action.key" :class="{ 'danger-action': action.key === 'remove' }">{{ action.label }}</a-menu-item>
              </a-menu>
            </a-dropdown>
          </div>
          <a-alert v-if="detail.warnings && detail.warnings.length" :message="detail.warnings.join('；')" type="warning" show-icon style="margin: 16px 0" />
          <a-alert v-if="kind === 'talents' && detail.recruitmentLocked" type="warning" show-icon style="margin: 16px 0"><template slot="message">{{ detail.recommendationBlockReason }}。人才意向修改不会解除招聘占用；关联流程明细按权限展示。<a v-if="showModuleAction('applications')" @click="viewTalentPipeline(detail)">查看招聘流程</a></template></a-alert>
          <a-tabs v-model="detailTab">
            <a-tab-pane key="overview" tab="基本资料"><a-descriptions bordered :column="2" size="small"><a-descriptions-item v-for="item in detailFields" :key="item.key" :label="item.label" :span="item.span || 1"><span class="pre-line">{{ displayValue(item.key, detail[item.key]) }}</span></a-descriptions-item></a-descriptions></a-tab-pane>
            <a-tab-pane v-if="kind === 'talents'" key="resumes" tab="简历版本">
              <a-upload v-if="showModuleAction('talents', 'resume')" :custom-request="uploadResume" :show-upload-list="false" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"><a-button icon="upload" :loading="uploading">上传新版本</a-button></a-upload><p class="muted">PDF和图片可在线预览；Word文件按下载权限下载查看。每次上传均保留原版本。</p>
              <a-table :columns="resumeColumns" :data-source="detail.resumes || []" row-key="id" :pagination="false" size="small"><span slot="files" slot-scope="_, file"><a-button v-if="showModuleAction('talents', 'preview') && previewable(file)" type="link" @click="fileAction('resumes', file, true)">预览</a-button><a-button v-if="showModuleAction('talents', 'download')" type="link" @click="fileAction('resumes', file, false)">下载</a-button></span></a-table>
            </a-tab-pane>
            <a-tab-pane v-if="kind === 'talents'" key="tasks" tab="跟进任务"><a-table :columns="taskColumns" :data-source="detail.tasks || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span><span slot="taskAction" slot-scope="_, task"><a-button v-if="task.status === 'todo' && showModuleAction('talents', 'contact')" type="link" @click="finishTask(task)">登记跟进结果</a-button></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'talents'" key="disputes" tab="归属争议"><a-alert type="info" message="指定处理人核实后可保留归属结案，或按转交按钮审批调整归属；审批通过前负责人不变。" style="margin-bottom: 12px" /><a-table :columns="disputeColumns" :data-source="detail.disputes || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span><span slot="disputeAction" slot-scope="_, d"><template v-if="d.status === 'open' && d.handlerUserId === options.currentUserId && showModuleAction('talents', 'dispute')"><a @click="retainOwnership(d)">保留归属结案</a><a-divider type="vertical" /><a v-if="showModuleAction('talents', 'handover')" @click="transferOwnership(d)">发起归属调整</a></template></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'jobs'" key="versions" tab="需求版本"><a-collapse><a-collapse-panel v-for="v in detail.versions || []" :key="v.id" :header="'V' + v.versionNo + ' · ' + (v.reason || '') + ' · ' + v.createTime"><a-descriptions bordered :column="2" size="small"><a-descriptions-item v-for="item in versionFields" :key="item.key" :label="item.label">{{ versionValue(v, item.key) }}</a-descriptions-item></a-descriptions></a-collapse-panel></a-collapse></a-tab-pane>
            <a-tab-pane v-if="kind !== 'applications'" key="approvals" tab="审批记录"><a-alert message="审批人在“我的待办”办理。本次申请冻结提交内容，修改未发布的流程不会影响已发起申请。" type="info" style="margin-bottom: 12px" /><a-table :columns="approvalColumns" :data-source="detail.approvals || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ approvalStates[v] || v }}</span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'applications'" key="interviews" tab="面试轮次"><a-table :columns="interviewColumns" :data-source="detail.interviews || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span><span slot="interviewState" slot-scope="v">{{ { scheduled: '待面试', completed: '已完成', cancelled: '已取消' }[v] || v }}</span><span slot="interviewResult" slot-scope="v">{{ { pass: '通过', fail: '不通过', pending: '待定' }[v] || '—' }}</span><span slot="notice" slot-scope="v">{{ v ? '已登记通知' : '未登记通知' }}</span><span slot="interviewActions" slot-scope="_, interview"><template v-if="showModuleAction('applications', 'interview')"><a v-if="hasInterviewOperation(interview, 'save')" @click="editInterview(interview)">编辑 / 改期</a><a-divider v-if="hasInterviewOperation(interview, 'save')" type="vertical" /><a v-if="hasInterviewOperation(interview, 'notify')" @click="interviewOperation(interview, 'notify')">登记通知</a><a-divider v-if="hasInterviewOperation(interview, 'notify')" type="vertical" /><a v-if="hasInterviewOperation(interview, 'result')" @click="interviewOperation(interview, 'result')">{{ interview.status === 'completed' ? '补充结果' : '登记结果' }}</a><a-divider v-if="hasInterviewOperation(interview, 'result') && hasInterviewOperation(interview, 'cancel')" type="vertical" /><a v-if="hasInterviewOperation(interview, 'cancel')" @click="interviewOperation(interview, 'cancel')">取消</a></template></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'applications'" key="attachments" tab="Offer / 入职凭证"><a-upload v-if="showModuleAction('applications', 'offer') || showModuleAction('applications', 'entry')" :custom-request="uploadEvidence" :show-upload-list="false" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"><a-button icon="upload" :loading="uploading">上传凭证</a-button></a-upload><p class="muted">先上传文件，再在Offer或入职登记中选择对应凭证。</p><a-table :columns="attachmentColumns" :data-source="detail.attachments || []" row-key="id" :pagination="false" size="small"><span slot="files" slot-scope="_, file"><a-button v-if="previewable(file)" type="link" @click="fileAction('attachments', file, true)">预览</a-button><a-button type="link" @click="fileAction('attachments', file, false)">下载</a-button></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'applications'" key="guarantee" tab="保证期处理"><a-alert v-if="detail.replacementFor" type="info" :message="'本记录替换原招聘记录 #' + detail.replacementFor" style="margin-bottom: 12px" /><a-table :columns="guaranteeColumns" :data-source="detail.guaranteeHandlings || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span></a-table><h4 style="margin-top: 20px">关联替换候选人</h4><a-table :columns="replacementColumns" :data-source="detail.replacements || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span><span slot="replacementAction" slot-scope="_, r"><a @click="openDetail(r.replacementApplicationId)">查看替换流程</a></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind === 'applications'" key="exceptions" tab="跟进事项"><a-table :columns="exceptionColumns" :data-source="detail.exceptions || []" row-key="id" :pagination="false" size="small"><span slot="state" slot-scope="v">{{ label(v) }}</span><span slot="followupType" slot-scope="value">{{ followupType(value) }}</span><span slot="exceptionAction" slot-scope="_, e"><a-button v-if="e.status === 'open' && e.exceptionType !== 'guarantee_replacement' && showModuleAction('applications', 'exception')" type="link" @click="resolveException(e)">登记跟进结果</a-button></span></a-table></a-tab-pane>
            <a-tab-pane v-if="kind !== 'applications' && showModuleAction('applications')" key="related" tab="关联招聘流程"><a-button type="primary" @click="createRecommendation">为当前{{ kind === 'jobs' ? '职位' : '人才' }}创建推荐</a-button><a-table :columns="relatedColumns" :data-source="related" row-key="id" :pagination="false" size="small" style="margin-top: 16px"><span slot="relatedAction" slot-scope="_, row"><a @click="goPipeline(row.id)">进入招聘流程</a></span></a-table></a-tab-pane>
            <a-tab-pane key="history" tab="操作日志"><a-table :columns="eventColumns" :data-source="detail.events || []" row-key="id" :pagination="{ pageSize: 15 }" size="small" /></a-tab-pane>
          </a-tabs>
        </template>
      </a-spin>
    </a-drawer>
    <a-drawer :visible="formVisible" :title="formTitle" :width="Math.min(680, drawerWidth)" destroy-on-close @close="formVisible = false"><recruitment-form v-if="formVisible" :key="formKey" :fields="formFields" :initial="formInitial" :saving="saving" :hint="formHint" :submit-text="formSubmitText" @submit="submitForm" @cancel="formVisible = false" /></a-drawer>
    <a-modal :visible="warningsVisible" title="招聘预警与待处理事项" :width="900" :footer="null" @cancel="warningsVisible = false"><a-table :columns="warningColumns" :data-source="warnings" row-key="key" size="small"><span slot="warningAction" slot-scope="_, w"><a @click="openWarning(w)">进入处理</a></span></a-table></a-modal>
    <a-modal :visible="tagsVisible" title="标签管理" :footer="null" @cancel="tagsVisible = false"><a-button type="primary" @click="editTag()">新增标签</a-button><a-table :columns="tagColumns" :data-source="options.tags || []" row-key="id" :pagination="false" size="small"><span slot="tagAction" slot-scope="_, tag"><a @click="editTag(tag)">编辑 / 启停</a></span></a-table></a-modal>
    <a-modal :visible="previewVisible" :title="previewName" :width="'85%'" :footer="null" @cancel="closePreview"><iframe v-if="previewUrl" :src="previewUrl" title="文件预览" style="width: 100%; height: 75vh; border: none" /></a-modal>
  </page-header-wrapper>
</template>

<script>
import { withModuleContexts } from '/src/utils/moduleContext.js'
import { recruitmentRequest as api, recordPath, uploadRecruitmentFile, getRecruitmentFile } from '/src/api/eos/recruitment/index.js'
import { recruitmentModules, jobStates, talentStates, recruitmentAvailabilityStates, talentRecommendationOptions, stages, approvalStates, feedbackStates, offerStates, guaranteeStates, followupTypes, applicationSubstate, applicationActions, label, choices, optionRows, field, reasonField, jobFields, talentFields } from '../shared.js'
import RecruitmentForm from './RecruitmentForm.vue'

const col = (title, dataIndex, slot, width) => ({ title, dataIndex, ...(width ? { width } : {}), ...(slot ? { scopedSlots: { customRender: slot } } : {}) })
export default {
  name: 'RecruitmentWorkspace',
  components: { RecruitmentForm },
  mixins: [withModuleContexts(recruitmentModules)],
  props: { kind: { type: String, required: true } },
  data() {
    return { rows: [], query: {}, options: {}, summary: {}, total: 0, page: 1, size: 20, loading: false, selected: [], duplicateMode: false, exporting: false, detailVisible: false, detailLoading: false, detail: {}, detailTab: 'overview', related: [], rowMenuId: null, rowMenuRecord: null, rowMenuLoading: false, rowMenuError: false, rowMenuRequest: 0, actionLoadingId: null, formVisible: false, formKey: 0, formTitle: '', formFields: [], formInitial: {}, formHint: '', formSubmitText: '保存', formSave: null, saving: false, uploading: false, warnings: [], warningsVisible: false, tagsVisible: false, previewVisible: false, previewUrl: '', previewName: '', approvalStates,
      eventColumns: [col('操作', 'eventType'), col('说明', 'content'), col('操作人', 'operatorName'), col('时间', 'createTime')],
      approvalColumns: [col('申请编号', 'workflowRequestId'), col('动作编码', 'actionCode'), col('状态', 'status', 'state'), col('当前节点', 'processTaskName'), col('提交时间', 'createTime')],
      resumeColumns: [col('版本', 'versionNo'), col('文件名称', 'originalName'), col('上传时间', 'createTime'), col('操作', 'actions', 'files')],
      attachmentColumns: [col('编号', 'id'), col('文件名称', 'originalName'), col('上传时间', 'createTime'), col('操作', 'actions', 'files')],
      disputeColumns: [col('争议说明', 'content'), col('拟归属人', 'proposedOwnerName'), col('处理人', 'handlerName'), col('状态', 'status', 'state'), col('结论', 'result'), col('操作', 'actions', 'disputeAction')],
      guaranteeColumns: [col('事项', 'operation', 'state'), col('发生日期', 'eventDate'), col('退费登记金额', 'amount'), col('凭证编号', 'attachmentId'), col('说明', 'reason'), col('登记人', 'operatorName')],
      replacementColumns: [col('替换记录', 'replacementApplicationId'), col('候选人', 'talentName'), col('招聘阶段', 'stage', 'state'), col('创建日期', 'createTime'), col('入职完成日期', 'completeTime'), col('操作', 'actions', 'replacementAction')],
      taskColumns: [col('任务', 'title'), col('负责人', 'ownerName'), col('期限', 'dueDate'), col('状态', 'status', 'state'), col('跟进结果', 'result'), col('操作', 'actions', 'taskAction')],
      interviewColumns: [col('轮次', 'roundNo'), col('面试时间', 'interviewTime'), col('地点 / 会议', 'location'), col('面试官', 'interviewer'), col('状态', 'status', 'interviewState'), col('通知', 'notified', 'notice'), col('结果', 'result', 'interviewResult'), col('评价', 'evaluation'), col('操作', 'actions', 'interviewActions')],
      exceptionColumns: [col('业务事项', 'exceptionType', 'followupType'), col('跟进说明', 'content'), col('处理人', 'ownerName'), col('状态', 'status', 'state'), col('结果', 'result'), col('操作', 'actions', 'exceptionAction')],
      relatedColumns: [col('候选人', 'talentName'), col('职位', 'jobTitle'), col('客户', 'customerName'), col('顾问', 'consultantName'), col('操作', 'actions', 'relatedAction')],
      warningColumns: [col('事项', 'name'), col('客户', 'customerName'), col('预警', 'warning'), col('处理', 'actions', 'warningAction')],
      tagColumns: [col('标签名称', 'name'), col('状态', 'status'), col('操作', 'actions', 'tagAction')]
    }
  },
  computed: {
    title() { return recruitmentModules[this.kind].label },
    subtitle() { return { jobs: '围绕客户需求管理职位、负责人、招聘名额、需求版本与进度预警。', talents: '统一候选人档案、简历、查重归属、标签检索和持续跟进。', applications: '关联候选人与职位，逐阶段记录客户反馈、面试、Offer、入职及保证期。' }[this.kind] },
    drawerWidth() { return Math.min(1180, Math.max(320, window.innerWidth - 70)) },
    statusKey() { return this.kind === 'applications' ? 'stage' : 'status' },
    ownerKey() { return this.kind === 'applications' ? 'consultantId' : 'ownerUserId' },
    stateMap() { return this.kind === 'jobs' ? jobStates : this.kind === 'talents' ? talentStates : stages },
    stateOptions() { return choices(this.stateMap) },
    availabilityOptions() { return choices(recruitmentAvailabilityStates) },
    pagination() { return { current: this.page, pageSize: this.size, total: this.total, showSizeChanger: true, showTotal: total => '共 ' + total + ' 条' } },
    metrics() { return [{ label: '符合筛选的职位', value: this.summary.jobs }, { label: '已推荐', value: this.summary.recommendations }, { label: '已入职', value: this.summary.hired }, { label: '剩余招聘名额', value: this.summary.vacancies }] },
    tableWidth() { return this.columns.reduce((width, column) => width + column.width, 0) },
    columns() {
      if (this.kind === 'jobs') return [col('职位 / 编号', 'title', 'name', 220), col('客户', 'customerName', null, 160), col('状态', 'status', 'state', 100), col('需求确认', 'demandStatus', 'demand', 110), col('负责人', 'ownerName', null, 110), col('人数 / 剩余', 'headcount', 'quota', 100), col('推荐 / 面试 / Offer / 入职', 'progress', 'progress', 230), col('预警', 'warnings', 'warnings', 180), { ...col('操作', 'actions', 'actions', 250), fixed: 'right' }]
      if (this.kind === 'talents') return [col('候选人 / 技能', 'name', 'name', 220), col('招聘占用', 'recruitmentAvailability', 'availability', 170), col('手机号', 'phone', null, 160), col('邮箱', 'email', null, 180), col('城市', 'city', null, 100), col('经验（年）', 'experienceYears', null, 100), col('人才意向', 'status', 'state', 110), col('标签', 'tags', 'tags', 180), col('负责人', 'ownerName', null, 110), col('最近联系', 'lastContactTime', null, 175), { ...col('操作', 'actions', 'actions', 190), fixed: 'right' }]
      return [col('候选人 / 职位', 'talentName', 'name', 240), col('客户', 'customerName', null, 170), col('顾问', 'consultantName', null, 110), col('阶段', 'stage', 'applicationStage', 170), col('推荐日期', 'submittedDate', null, 120), col('预计入职', 'expectedEntryDate', null, 120), col('实际入职', 'actualEntryDate', null, 120), col('保证期结束', 'guaranteeEnd', null, 120), col('提醒', 'warnings', 'warnings', 180), { ...col('操作', 'actions', 'actions', 220), fixed: 'right' }]
    },
    detailFields() {
      const pairs = this.kind === 'jobs' ? [['code', '职位编号'], ['customerName', '所属客户'], ['status', '职位状态'], ['demandStatus', '需求确认'], ['ownerName', '负责人'], ['city', '城市'], ['industry', '行业'], ['experience', '经验要求'], ['salaryMin', '月薪下限'], ['salaryMax', '月薪上限'], ['headcount', '招聘人数'], ['vacancies', '剩余名额'], ['hired', '实际入职人数'], ['completionRate', '完成率（%）'], ['targetDate', '目标日期'], ['cycleDays', '周期（天）'], ['minRecommendations', '推荐阈值'], ['feedbackDays', '反馈期限（天）'], ['requirements', '岗位要求', 2], ['feeTerms', '收费条件', 2]] : this.kind === 'talents' ? [['name', '姓名'], ['status', '人才意向'], ['recruitmentAvailabilityLabel', '招聘占用'], ['recruitmentStageLabel', '占用阶段'], ['recommendationBlockReason', '推荐限制', 2], ['phone', '手机号'], ['email', '邮箱'], ['ownerName', '负责人'], ['lastContactTime', '最近联系'], ['city', '城市'], ['industry', '行业'], ['experienceYears', '经验（年）'], ['expectedSalary', '期望月薪'], ['skills', '技能', 2], ['tags', '标签', 2], ['source', '来源'], ['remark', '备注', 2]] : [['talentName', '候选人'], ['jobTitle', '职位'], ['customerName', '客户'], ['consultantName', '负责顾问'], ['stage', '招聘阶段'], ['feedbackStatus', '客户反馈'], ['offerStatus', 'Offer状态'], ['offerSalary', 'Offer月薪'], ['submittedDate', '推荐日期'], ['feedbackDueDate', '反馈期限'], ['expectedEntryDate', '预计入职'], ['actualEntryDate', '实际入职'], ['guaranteeDays', '合同保证期（天）'], ['guaranteeStatus', '保证期状态'], ['guaranteeStart', '保证期开始'], ['guaranteeEnd', '保证期结束'], ['recommendation', '推荐说明', 2]]
      return pairs.map(([key, label, span]) => ({ key, label, span }))
    },
    versionFields() { return jobFields(this.options).filter(f => !['collaborators', 'ownerUserId', 'customerId'].includes(f.key)) },
    detailActions() { return this.actionsForRecord(this.detail) }
  },
  watch: { '$route.query': { handler() { this.readRoute() } } },
  async mounted() { await this.loadOptions(); this.readRoute() },
  beforeDestroy() { this.closePreview() },
  methods: {
    hasInterviewResultAction(row) { return this.kind === 'applications' && this.actionsForRecord(row).some(action => action.key === 'interviewResult') },
    hasRowActions(row) { return this.kind !== 'applications' || this.actionsForRecord(row).some(action => !action.detailsOnly && action.key !== 'interviewResult') },
    hasInterviewOperation(interview, operation) { return (interview.availableOperations || []).includes(operation) },
    followupType(value) { return followupTypes[value] || value },
    followupFields(label = '是否需要后续跟进') {
      return [field('followupRequired', label, 'boolean', { required: true, hint: '开启后生成待处理事项；关闭时仅记录业务事件。保证期替换始终自动跟进至替换人入职。' }), field('ownerUserId', '跟进负责人', 'select', { options: optionRows(this.options.users), hint: '仅生成待处理事项时使用。' })]
    },
    guaranteeOptions(d) {
      const allowed = new Set(d.availableOperations || [])
      return [{ value: 'complete', label: '期满结案', operation: 'completeGuarantee' }, { value: 'exit', label: '登记离职', operation: 'guaranteeExit' }, { value: 'replacement', label: '登记替换', operation: 'guaranteeReplacement' }, { value: 'refund', label: '登记退费', operation: 'guaranteeRefund' }].filter(option => allowed.has(option.operation))
    },
    rowMoreActions(row) {
      if (this.rowMenuId !== row.id || !this.rowMenuRecord) return []
      return this.actionsForRecord(this.rowMenuRecord).filter(action => !action.detailsOnly && !['edit', 'copy', 'interviewResult'].includes(action.key))
    },
    async prepareRowActions(row, visible) {
      const request = ++this.rowMenuRequest
      if (!visible) return
      this.rowMenuId = row.id; this.rowMenuRecord = null; this.rowMenuLoading = true; this.rowMenuError = false
      try {
        const r = await api(recordPath(this.kind, row.id))
        if (request === this.rowMenuRequest) this.rowMenuRecord = r.data || null
      } catch (e) { if (request === this.rowMenuRequest) this.rowMenuError = true } finally { if (request === this.rowMenuRequest) this.rowMenuLoading = false }
    },
    async runRecordAction(key, row) {
      if (this.actionLoadingId !== null || ['loading', 'empty'].includes(key)) return
      this.actionLoadingId = row.id
      try {
        const r = await api(recordPath(this.kind, row.id))
        const d = r.data
        if (!d || d.id !== row.id) return this.$message.warning('记录已更新，请刷新列表后重试')
        const action = this.actionsForRecord(d).find(item => item.key === key)
        if (!action) return this.$message.warning('当前状态或权限已变化，请刷新后重试')
        await this.loadOptions()
        await this.runAction(action, d)
      } finally { this.actionLoadingId = null }
    },
    actionsForRecord(d) {
      const actions = []
      const add = (key, name, permission, primary = false, detailsOnly = false) => { if (this.showModuleAction(this.kind, permission || key)) actions.push({ key, label: name, permission: permission || key, primary, detailsOnly }) }
      if (this.kind === 'jobs') {
        if (['draft', 'rejected', 'withdrawn'].includes(d.demandStatus)) { add('edit', '修改职位'); add('demand', '提交需求确认', 'demand', true) }
        if (d.demandStatus === 'approved') add('change', '提交职位变更')
        add('assign', '负责人 / 协作顾问'); add('status', '变更职位状态'); add('warning', '配置预警'); add('copy', '复制职位')
      } else if (this.kind === 'talents') {
        if (d.contactVisible) add('edit', '修改档案')
        add('status', '状态 / 重新激活'); add('contact', '记录联系'); add('task', '分配跟进任务', 'assign'); add('collaborators', '协作人', 'assign'); add('handover', '申请转交'); add('merge', '申请合并'); add('dispute', '登记归属争议')
      } else {
        applicationActions(d).forEach(action => {
          if (action.key === 'replacement' && !this.showModuleAction('applications', 'add')) return
          add(action.key, action.label, action.permission, false, action.detailsOnly)
        })
      }
      if ((d.approvals || []).some(a => a.status === 'pending')) add('withdraw', '撤回审批申请')
      if (this.kind !== 'applications' && (this.kind === 'jobs' ? d.demandStatus : d.status) === 'draft') add('remove', '删除未引用草稿')
      return actions
    },
    label,
    filterOption(input, option) { return String(option.componentOptions.children[0].text).toLowerCase().includes(input.toLowerCase()) },
    stateLabel(value) { return this.stateMap[value] || value },
    stageSubstatus(row) { return applicationSubstate(row) },
    displayValue(key, value) {
      if (value === undefined || value === null || value === '') return '—'
      if (Array.isArray(value)) return value.join('、') || '—'
      if (key === 'status') return this.stateMap[value] || value
      if (key === 'demandStatus') return approvalStates[value] || value
      if (key === 'stage') return stages[value] || value
      if (key === 'feedbackStatus') return feedbackStates[value] || value
      if (key === 'offerStatus') return offerStates[value] || value
      if (key === 'guaranteeStatus') return guaranteeStates[value] || value
      return value
    },
    versionValue(v, key) { try { return this.displayValue(key, JSON.parse(v.snapshotJson)[key]) } catch (e) { return '—' } },
    async allowedAction(action, module = this.kind) { return this.checkModuleAction({ module, action, label: '招聘操作' }) },
    async loadOptions() { try { const r = await api('/options'); this.options = r.data || {} } catch (e) { this.options = {} } },
    async readRoute() {
      this.query = { ...this.query, jobId: this.$route.query.jobId || undefined, talentId: this.$route.query.talentId || undefined, customerId: this.$route.query.customerId || undefined }
      await this.load()
      const id = this.$route.query.recordId
      if (id) this.openDetail(id)
      if (this.$route.query.create === '1') { await this.create(); const query = { ...this.$route.query }; delete query.create; this.$router.replace({ query }) }
    },
    async load() {
      this.loading = true
      try {
        const path = '/' + this.kind + (this.kind !== 'applications' && this.duplicateMode ? '/duplicates' : '')
        const r = await api(path, 'get', { ...this.query, pageNum: this.page, pageSize: this.size })
        this.rows = r.rows || []; this.total = r.total || 0; this.summary = r.summary || {}
        const last = Math.max(1, Math.ceil(this.total / this.size))
        if (this.page > last) { this.page = last; await this.load() }
      } finally { this.loading = false }
    },
    refresh() { this.page = 1; this.selected = []; this.load() },
    reset() { this.query = {}; this.duplicateMode = false; this.refresh() },
    changePage(p) { this.page = p.current; this.size = p.pageSize; this.selected = []; this.load() },
    selectRows(ids) { this.selected = ids },
    toggleDuplicates() { this.duplicateMode = !this.duplicateMode; this.refresh() },
    async openDetail(id) {
      if (!await this.allowedAction('view')) return
      this.detailVisible = true; this.detailLoading = true; this.detailTab = 'overview'; this.related = []
      try { const r = await api(recordPath(this.kind, id)); this.detail = r.data || {}; await this.loadRelated() } finally { this.detailLoading = false }
    },
    async loadRelated() {
      if (this.kind === 'applications' || !this.canViewModule('applications') || !this.detail.id) return
      const r = await api('/applications', 'get', { [this.kind === 'jobs' ? 'jobId' : 'talentId']: this.detail.id, pageSize: 200 })
      this.related = r.rows || []
    },
    async reloadDetail() {
      if (!this.detailVisible || !this.detail.id) return
      const r = await api(recordPath(this.kind, this.detail.id)); this.detail = r.data || {}; await this.loadRelated()
    },
    form(title, fields, initial, save, hint = '', submit = '保存') {
      this.formTitle = title; this.formFields = fields; this.formInitial = { ...initial }; this.formSave = save; this.formHint = hint; this.formSubmitText = submit; this.formKey++; this.formVisible = true
    },
    async submitForm(body) {
      if (this.saving) return
      this.saving = true
      try { const r = await this.formSave(body); this.formVisible = false; this.$message.success(r && r.data && r.data.status === 'pendingApproval' ? '已提交审批，请在我的发起跟踪' : '保存成功'); await this.load(); await this.loadOptions(); if (this.detailVisible) { try { await this.reloadDetail() } catch (e) { this.detailVisible = false } } } finally { this.saving = false }
    },
    async create() {
      if (!await this.allowedAction('add')) return
      await this.loadOptions()
      const userId = Number(this.options.currentUserId) || undefined
      if (this.kind === 'applications') {
        this.form('创建候选人推荐', [field('jobId', '招聘职位', 'select', { required: true, options: optionRows((this.options.jobs || []).filter(j => j.status === 'recruiting')) }), field('talentId', '候选人', 'select', { required: true, labelHint: '只允许「沟通中、可推荐」两种状态可选', hint: '已进入招聘流程或已入职的人才会锁定，灰色选项不可选择；淘汰、撤回或离职且无其他占用后自动释放。', options: talentRecommendationOptions(this.options.talents) }), field('consultantId', '负责顾问', 'select', { required: true, options: optionRows(this.options.users) }), field('recommendation', '推荐说明', 'textarea', { required: true })], { jobId: Number(this.query.jobId) || undefined, talentId: Number(this.query.talentId) || undefined, consultantId: userId }, b => api('/applications', 'post', b), '创建后直接进入“已推荐”，自动记录推荐日期、反馈期限并更新客户推荐统计。同一候选人和职位仅保留一条持续跟进记录。', '创建推荐')
      } else if (this.kind === 'jobs') {
        this.form('新建职位', jobFields(this.options), { customerId: Number(this.query.customerId) || undefined, ownerUserId: userId, headcount: 1, salaryMin: 0, salaryMax: 0, cycleDays: 30, minRecommendations: 3, feedbackDays: 3, collaborators: [] }, b => api('/jobs', 'post', b))
      } else this.form('新建候选人', talentFields(this.options), { ownerUserId: userId, tags: [], collaborators: [], experienceYears: 0, expectedSalary: 0 }, b => api('/talents', 'post', b), '手机号或邮箱至少填写一项；创建后可上传简历并设置为可推荐。')
    },
    async edit(id) {
      if (!await this.allowedAction('edit')) return
      const r = await api(recordPath(this.kind, id)); const d = r.data
      if (this.kind === 'jobs' && !['draft', 'rejected', 'withdrawn'].includes(d.demandStatus)) return this.$message.warning('已确认需求请在“更多”中提交“职位变更”')
      if (this.kind === 'talents' && !d.contactVisible) return this.$message.warning('修改档案需要查看联系方式权限')
      await this.loadOptions()
      const fields = (this.kind === 'jobs' ? jobFields(this.options) : talentFields(this.options, d.tags)).map(f => ({ ...f, disabled: f.key === (this.kind === 'jobs' ? 'customerId' : 'ownerUserId') }))
      this.form('修改' + this.title, fields, d, b => api(recordPath(this.kind, id), 'put', b))
    },
    async copy(id) {
      if (!await this.allowedAction('copy')) return
      const r = await api(recordPath('jobs', id) + '/copy', 'post', {})
      this.$message.success('已复制为独立职位草稿'); await this.load(); this.openDetail(r.data.id)
    },
    async runAction(action, d = this.detail) {
      if (!await this.allowedAction(action.permission)) return
      const id = d.id; const base = recordPath(this.kind, id)
      const initial = { ...d, reason: '', ownerUserId: d.ownerUserId || d.consultantId, followupRequired: false, collaborators: d.collaborators || [] }
      const post = (name, extra = {}, request = false) => b => api(base + (request ? '/requests/' : '/actions/') + name, 'post', { revision: d.revision, ...extra, ...b })
      const simple = (fields, save, hint = '') => this.form(action.label, [...fields, reasonField], initial, save, hint)
      switch (action.key) {
        case 'edit': return this.edit(id)
        case 'copy': return this.copy(id)
        case 'remove': return this.remove(id)
        case 'demand': return simple([], post('demand', {}, true), '如按钮有已发布且启用的审批流程，将进入统一待办；否则直接确认需求并开始招聘。')
        case 'change': return this.form('申请职位变更', [...jobFields(this.options).filter(f => !['customerId', 'ownerUserId', 'collaborators', 'cycleDays', 'minRecommendations', 'feedbackDays'].includes(f.key)), reasonField], initial, b => api(base + '/requests/change', 'post', { revision: d.revision, reason: b.reason, fields: b }), '审批期间原需求继续生效；最终通过后保存新的需求版本。')
        case 'assign': return simple([field('ownerUserId', '负责人', 'select', { required: true, options: optionRows(this.options.users) }), field('collaborators', '协作顾问', 'multiple', { options: optionRows(this.options.users) })], post('assign'))
        case 'status': {
          const options = this.recordStatusOptions(d)
          if (!options.some(option => option.value === initial.status && !option.disabled)) initial.status = undefined
          return simple([field('status', '目标状态', 'select', { required: true, options })], post('status'))
        }
        case 'warning': return simple([field('cycleDays', '招聘周期（天）', 'number', { required: true, min: 1, precision: 0 }), field('minRecommendations', '最低推荐数量', 'number', { required: true, precision: 0 }), field('feedbackDays', '反馈期限（天）', 'number', { required: true, min: 1, precision: 0 })], post('warning'))
        case 'contact': return simple([], post('contact'))
        case 'collaborators': return simple([field('collaborators', '协作人', 'multiple', { options: optionRows(this.options.users) })], post('collaborators'))
        case 'task': return this.form('分配候选人跟进任务', [field('title', '跟进内容', 'text', { required: true }), field('ownerUserId', '任务负责人', 'select', { required: true, options: optionRows(this.options.users) }), field('dueDate', '完成期限', 'date', { required: true })], { ownerUserId: d.ownerUserId }, b => api(base + '/tasks', 'post', b))
        case 'dispute': return simple([field('proposedOwnerId', '拟归属负责人', 'select', { required: true, options: optionRows((this.options.users || []).filter(u => u.id !== d.ownerUserId)) }), field('handlerUserId', '争议处理人', 'select', { required: true, options: optionRows(this.options.users) })], b => api(base + '/disputes', 'post', { ...b, revision: d.revision }), '请填写争议事实与依据。处理人必须具有归属争议权限，登记后成为协作人；核实后由其结案或发起转交。')
        case 'handover': return simple([field('ownerUserId', '新负责人', 'select', { required: true, options: optionRows((this.options.users || []).filter(u => u.id !== d.ownerUserId)) }), field('collaborators', '转交后协作人', 'multiple', { options: optionRows(this.options.users) })], post('handover', {}, true))
        case 'merge': return simple([field('targetId', '保留的目标档案', 'select', { required: true, options: optionRows((this.options.talents || []).filter(t => t.id !== id)) })], post('merge', {}, true), '仅允许手机号或邮箱相同的档案合并；被合并档案保留审计日志。若双方存在同职位招聘记录，系统会阻止合并，避免覆盖历史。')
        case 'withdraw': return simple([], post('withdraw'))
        case 'recommend': return simple([], post('recommend', { operation: 'submit' }))
        case 'withdrawRecommendation': return simple([], post('recommend', { operation: 'withdraw' }))
        case 'interviewResult': {
          const interview = (d.interviews || []).find(item => (item.availableOperations || []).includes('result'))
          if (!interview) return this.$message.warning('当前没有可登记结果的面试，请刷新后重试')
          return this.interviewOperation(interview, 'result', d)
        }
        case 'replacementManage': await this.openDetail(id); this.detailTab = 'guarantee'; return
        case 'followupManage': await this.openDetail(id); this.detailTab = 'exceptions'; return
        case 'finishRecommendation': return this.form('结束本次推荐', [reasonField], initial, post('exception', { operation: 'finish' }), '确认不再推进当前人岗推荐后结束，保留反馈、面试和操作历史；此操作不会自动重新开启。', '确认结束')
        case 'candidateWithdraw': case 'customerCancel': return this.form(action.label, [...this.followupFields(), reasonField], initial, post('exception', { operation: 'create', type: action.key === 'candidateWithdraw' ? 'candidate_withdraw' : 'customer_cancel' }), '确认后停止当前推进，取消未完成面试并保留历史。只有需要后续处理时才生成待处理事项。', '确认登记')
        case 'entryDelay': initial.followupRequired = true; return simple([field('expectedEntryDate', '调整后预计入职日期', 'date'), ...this.followupFields()], post('exception', { operation: 'create', type: 'entry_delay' }), '登记延期原因，可同步调整预计入职日期；重复跟进同类未结事项会更新原事项，操作历史保留。')
        case 'feedback': return simple([field('status', '反馈结果', 'select', { required: true, options: choices(feedbackStates) }), field('dueDate', '下次反馈期限', 'date')], post('feedback'), '只有“有意向 / 邀约面试”才能安排面试；待反馈、不合适、需补充资料均停留在客户反馈阶段。')
        case 'interview': return this.editInterview(undefined, d)
        case 'offer': return simple([field('salary', 'Offer月薪（元）', 'number', { required: true }), this.attachmentField(false, d)], post('offer', { operation: 'save' }), '上传Offer文件后可在此选择，保存草稿后再登记发放。')
        case 'issueOffer': return simple([], post('offer', { operation: 'issue' }), '请在实际向候选人发放Offer后登记。')
        case 'acceptOffer': return simple([field('expectedEntryDate', '预计入职日期', 'date', { required: true })], post('offer', { operation: 'accept' }))
        case 'rejectOffer': return simple(this.followupFields(), post('offer', { operation: 'reject' }), '仅已发放Offer可登记拒绝，拒绝后结束当前推进；需要后续处理时才生成跟进事项。')
        case 'withdrawOffer': return simple(d.offerStatus === 'draft' ? [] : this.followupFields(), post('offer', { operation: 'withdraw' }), d.offerStatus === 'draft' ? '取消未发放草稿，返回面试阶段；保留操作历史，不生成异常事项。' : '撤回已发放Offer并返回面试阶段；需要后续处理时才生成跟进事项。')
        case 'expectedEntry': return simple([field('expectedEntryDate', '预计入职日期', 'date', { required: true })], post('entry', { operation: 'expected' }))
        case 'entry': case 'correctEntry': return simple([field('actualEntryDate', '实际入职日期', 'date', { required: true }), this.attachmentField(true, d), field('termKey', '合同保证期条款', 'select', { required: true, options: (d.guaranteeTerms || []).map(t => ({ value: t.key, label: t.title + ' · 保证期 ' + (t.guaranteeDays || 0) + ' 天' })) })], post('entry', { operation: action.key === 'correctEntry' ? 'correct' : 'confirm' }), '保证期按所选签署合同条款自动计算；先到“Offer / 入职凭证”上传证明。')
        case 'replacement': return this.createReplacement(d)
        case 'postGuaranteeExit': return simple([field('exitDate', '实际离职日期', 'date', { required: true })], post('guarantee', { operation: 'exit' }), '仅释放人才在职占用，保留已完成的保证期、原职位结案和招聘成果；离职日期不能早于保证期结束。')
        case 'guarantee': return simple([field('operation', '处理方式', 'select', { required: true, options: this.guaranteeOptions(d) }), field('exitDate', '离职 / 退出日期', 'date', { hint: '期满结案无需填写，其余处理须填写。' }), field('amount', '退费登记金额（元）', 'number', { hint: '登记退费时须填写正数金额。' }), this.attachmentField(false, d), ...this.followupFields('离职 / 退费是否需要跟进')], post('guarantee'), '到期后才开放期满结案。退费须有金额和凭证，仅记录已办理事项；替换自动跟进至替换人实际入职，其他事项按选择生成跟进。')
        default: return undefined
      }
    },
    recordStatusOptions(d) {
      if (this.kind !== 'jobs') return choices(talentStates).filter(option => !['draft', 'merged'].includes(option.value)).map(option => ({ ...option, disabled: !!d.recruitmentLocked && ['available', 'contacting'].includes(option.value), disabledReason: d.recruitmentLocked ? '请先在招聘流程办理结束或离职，不能手工解除占用' : '' }))
      return choices(jobStates).map(option => {
        let disabledReason = ''
        if (option.value === 'draft' && d.demandStatus !== 'draft') disabledReason = '已提交需求的职位不能退回待启动'
        else if (d.demandStatus !== 'approved' && !['draft', 'closed'].includes(option.value)) disabledReason = '请先完成需求确认'
        else if (option.value === 'recruiting' && Number(d.hired) >= Number(d.headcount)) disabledReason = '职位名额已满'
        else if (option.value === 'completed' && Number(d.hired) < Number(d.headcount)) disabledReason = '入职人数尚未达到需求'
        return { ...option, disabled: !!disabledReason, disabledReason }
      })
    },
    attachmentField(required = false, d = this.detail) { return field('attachmentId', '凭证附件', 'select', { required, options: (d.attachments || []).map(f => ({ value: f.id, label: f.originalName })) }) },
    async editInterview(interview, d = this.detail) {
      if (!await this.allowedAction('interview')) return
      const existing = interview || {}; const initial = { ...existing, roundNo: existing.roundNo || Math.max(0, ...(d.interviews || []).map(item => Number(item.roundNo) || 0)) + 1, interviewTime: (existing.interviewTime || '').slice(0, 16).replace(' ', 'T'), reason: '' }
      this.form(interview ? '编辑面试 / 改期' : '安排面试', [field('roundNo', '面试轮次', 'number', { required: true, min: 1, max: 100, precision: 0 }), field('interviewTime', '面试时间', 'datetime', { required: true }), field('location', '地点 / 会议地址', 'text', { required: true, max: 500 }), field('interviewer', '面试官', 'text', { required: true }), reasonField], initial, b => api(recordPath('applications', d.id) + '/interviews' + (interview ? '/' + interview.id : ''), interview ? 'put' : 'post', { ...b, revision: d.revision, operation: 'save' }))
    },
    async interviewOperation(interview, operation, d = this.detail) {
      if (!await this.allowedAction('interview')) return
      const fields = operation === 'result' ? [field('result', '面试结果', 'select', { required: true, options: choices({ pass: '通过', fail: '不通过', pending: '待定' }) }), reasonField] : [reasonField]
      const id = d.id; const revision = d.revision
      this.form({ notify: '登记已通知候选人与客户', result: '登记面试结果', cancel: '取消面试' }[operation], fields, {}, b => api(recordPath('applications', id) + '/interviews/' + interview.id, 'put', { ...b, revision, operation }), operation === 'notify' ? '此操作记录已完成的通知，请先实际联系候选人与客户。' : operation === 'result' ? '请确认面试已实际完成后登记，预约时间不限制录入。面试通过后可继续创建Offer；不通过则结束本次推进，待定则等待补充结果。' : '')
    },
    async retainOwnership(dispute) {
      if (!await this.allowedAction('dispute')) return
      const d = this.detail; this.form('归属争议结案：保留当前负责人', [reasonField], {}, b => api(recordPath('talents', d.id) + '/disputes/' + dispute.id + '/retain', 'post', { ...b, revision: d.revision }))
    },
    async transferOwnership(dispute) {
      if (!await this.allowedAction('dispute') || !await this.allowedAction('handover')) return
      const d = this.detail; this.form('归属争议：申请转交', [field('collaborators', '转交后协作人', 'multiple', { options: optionRows(this.options.users) }), reasonField], { collaborators: d.collaborators || [] }, b => api(recordPath('talents', d.id) + '/requests/handover', 'post', { ...b, ownerUserId: dispute.proposedOwnerId, disputeId: dispute.id, revision: d.revision }), '拟归属人：' + dispute.proposedOwnerName + '。仅已发布并启用的流程进入审批，最终执行成功后自动结案争议。')
    },
    async createReplacement(d = this.detail) {
      if (!await this.allowedAction('guarantee') || !await this.allowedAction('add')) return
      this.form('创建关联替换推荐', [field('talentId', '替换候选人', 'select', { required: true, labelHint: '只允许「沟通中、可推荐」两种状态可选', hint: '招聘占用和已入职人选不可重复推荐。', options: talentRecommendationOptions(this.options.talents, d.talentId) }), field('consultantId', '负责顾问', 'select', { required: true, options: optionRows(this.options.users) }), field('recommendation', '推荐说明', 'textarea', { required: true })], { consultantId: d.consultantId }, async b => { const r = await api(recordPath('applications', d.id) + '/replacement', 'post', { ...b, revision: d.revision }); await this.openDetail(r.data.id); return r }, '沿用原职位创建替换推荐后直接进入“已推荐”，继续办理反馈、面试、Offer和入职。进行中的替换记录不能重复创建。', '创建推荐')
    },
    async resolveException(e) { if (!await this.allowedAction('exception')) return; const id = this.detail.id; const revision = this.detail.revision; this.form('登记跟进结果', [reasonField], {}, b => api(recordPath('applications', id) + '/actions/exception', 'post', { ...b, revision, operation: 'resolve', exceptionId: e.id })) },
    async finishTask(task) { if (!await this.allowedAction('contact')) return; this.form('完成跟进任务', [field('result', '联系结果 / 下一步', 'textarea', { required: true })], {}, b => api('/tasks/' + task.id + '/complete', 'post', b)) },
    async remove(id = this.detail.id) {
      if (!await this.allowedAction('remove')) return
      this.$antdconfirm({ title: '删除未引用草稿', content: '仅未提交、未引用的草稿可删除，正式业务档案请使用关闭或归档。', onOk: async () => { await api(recordPath(this.kind, id), 'delete'); if (this.detail.id === id) this.detailVisible = false; this.$message.success('已删除'); await this.load() } })
    },
    async uploadResume(request) { await this.uploadFile(request, 'resume', recordPath('talents', this.detail.id) + '/resumes') },
    async uploadEvidence(request) {
      await this.ensureModuleContexts(['applications'])
      if (!this.showModuleAction('applications', 'offer') && !this.showModuleAction('applications', 'entry')) return this.$message.warning('没有上传Offer或入职凭证的权限')
      await this.uploadFile(request, null, recordPath('applications', this.detail.id) + '/attachments')
    },
    async uploadFile(request, permission, path) {
      if (permission && !await this.allowedAction(permission)) return
      this.uploading = true
      try { const r = await uploadRecruitmentFile(path, request.file); request.onSuccess(r); this.$message.success('上传成功'); await this.reloadDetail() } catch (e) { request.onError(e) } finally { this.uploading = false }
    },
    previewable(file) { return file.contentType === 'application/pdf' || (file.contentType || '').startsWith('image/') },
    async fileAction(type, file, preview) {
      if (type === 'resumes' && !await this.allowedAction(preview ? 'preview' : 'download')) return
      try { const blob = await getRecruitmentFile('/' + type + '/' + file.id + '/file' + (preview ? '?preview=true' : '')); const url = URL.createObjectURL(blob); if (preview) { this.closePreview(); this.previewUrl = url; this.previewName = file.originalName; this.previewVisible = true } else { const a = document.createElement('a'); a.href = url; a.download = file.originalName; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) } } catch (e) { this.$message.error(e.message || '文件读取失败') }
    },
    closePreview() { this.previewVisible = false; if (this.previewUrl) URL.revokeObjectURL(this.previewUrl); this.previewUrl = '' },
    async viewWarnings() { const r = await api('/warnings'); this.warnings = r.data || []; this.warningsVisible = true },
    openWarning(w) { this.warningsVisible = false; if ((w.entityType === 'job' ? 'jobs' : 'applications') === this.kind) this.openDetail(w.entityId); else this.$router.push({ name: w.entityType === 'job' ? 'RecruitmentJobs' : 'RecruitmentPipeline', query: { recordId: w.entityId } }) },
    async goPipeline(id) { if (!await this.allowedAction('view', 'applications')) return; this.$router.push({ name: 'RecruitmentPipeline', query: { recordId: id } }) },
    async viewTalentPipeline(row) { if (!await this.allowedAction('view', 'applications')) return; this.$router.push({ name: 'RecruitmentPipeline', query: { talentId: row.id } }) },
    async createRecommendation() { if (!await this.allowedAction('add', 'applications')) return; if (this.kind === 'talents' && this.detail.canRecommend !== true) return this.$message.warning(this.detail.recommendationBlockReason || '当前人才不可推荐，请刷新状态'); this.$router.push({ name: 'RecruitmentPipeline', query: { [this.kind === 'jobs' ? 'jobId' : 'talentId']: this.detail.id, create: '1' } }) },
    async saveSearch() { this.form('保存常用检索条件', [field('name', '搜索名称', 'text', { required: true })], {}, b => api('/searches', 'post', { ...b, query: this.query })) },
    applySearch(id) { const saved = (this.options.searches || []).find(s => s.id === id); if (saved) { this.query = JSON.parse(saved.queryJson); this.refresh() } },
    async manageTags() { if (!await this.allowedAction('tag')) return; this.tagsVisible = true },
    editTag(tag) { this.form(tag ? '编辑标签' : '新增标签', [field('name', '标签名称', 'text', { required: true, max: 80 }), field('status', '状态', 'select', { options: choices({ active: '启用', disabled: '停用' }), required: true })], tag || { status: 'active' }, b => api('/tags', 'post', b)) },
    async batchTags() { if (!await this.allowedAction('tag')) return; const ids = [...this.selected]; this.form('批量打标签', [talentFields(this.options).find(f => f.key === 'tags')], { tags: [] }, b => api('/talents/tags', 'post', { ...b, ids })) },
    async chooseImport() { if (!await this.allowedAction('import')) return; this.$refs.importFile.click() },
    async importFile(event) {
      const file = event.target.files[0]; event.target.value = ''; if (!file || !await this.allowedAction('import')) return
      try { const XLSX = await import('xlsx'); const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' }); const data = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]], { defval: '' }); if (!data.length || data.length > 200) return this.$message.warning('每次导入1至200条，表头请使用下载的模板')
        const keys = this.importFields(); const rows = data.map(row => { const r = {}; keys.forEach(f => { if (row[f.label] !== undefined && row[f.label] !== '') r[f.key] = row[f.label] }); return r })
        this.$antdconfirm({ title: '确认导入 ' + rows.length + ' 条记录', content: '将统一校验并导入；任一行错误则本次导入全部取消，不会产生部分数据。', onOk: async () => { const result = await api('/' + this.kind + '/import', 'post', { rows }); this.$message.success('成功导入 ' + result.data.count + ' 条'); this.refresh(); this.loadOptions() } })
      } catch (e) { this.$message.error(e.message || '文件解析失败，请检查模板') }
    },
    importFields() { return this.kind === 'jobs' ? [field('title', '职位名称'), field('customerId', '客户ID'), field('ownerUserId', '负责人ID'), field('contractId', '合同ID'), field('city', '城市'), field('industry', '行业'), field('salaryMin', '月薪下限'), field('salaryMax', '月薪上限'), field('headcount', '招聘人数'), field('requirements', '岗位要求'), field('feeTerms', '收费条件')] : [field('name', '姓名'), field('phone', '手机号'), field('email', '邮箱'), field('ownerUserId', '负责人ID'), field('city', '城市'), field('industry', '行业'), field('skills', '技能'), field('experienceYears', '经验年限'), field('expectedSalary', '期望月薪'), field('source', '来源'), field('remark', '备注')] },
    async downloadTemplate() { const XLSX = await import('xlsx'); const keys = this.importFields(); const sheet = XLSX.utils.aoa_to_sheet([keys.map(k => k.label)]); const wb = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(wb, sheet, '导入模板'); const lookup = [...(this.options.users || []).map(u => ({ 类型: '负责人', ID: u.id, 名称: u.name })), ...(this.kind === 'jobs' ? (this.options.customers || []).map(c => ({ 类型: '客户', ID: c.id, 名称: c.name })) : [])]; XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(lookup), '可选编号'); XLSX.writeFile(wb, this.title + '导入模板.xlsx') },
    async exportData() {
      if (!await this.allowedAction('export')) return
      this.exporting = true
      try { const r = await api('/' + this.kind + '/export', 'get', this.query); const XLSX = await import('xlsx'); const data = (r.data || []).map(row => { const out = {}; this.columns.filter(c => !['actions', 'progress'].includes(c.dataIndex)).forEach(c => { out[c.title] = this.displayValue(c.dataIndex, row[c.dataIndex]) }); return out }); const wb = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(data), this.title); XLSX.writeFile(wb, this.title + '.xlsx') } finally { this.exporting = false }
    }
  }
}
</script>

<style lang="less" scoped>
.workspace-head { display: flex; justify-content: space-between; gap: 24px; h2 { margin: 0; color: #17212b; font-size: 22px; } p { color: #667085; margin: 8px 0 0; } }
.lifecycle { padding: 15px 18px; margin: 20px 0 16px; border: 1px solid #e5edf4; background: #f4f8fc; color: #42617b; border-radius: 5px; line-height: 1.8; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; > div { padding: 16px 20px; border: 1px solid #e5e9ee; border-left: 3px solid #1769aa; border-radius: 5px; } small { display: block; color: #667085; } strong { display: block; font-size: 25px; color: #17212b; margin-top: 5px; } }
.filters, .toolbar, .detail-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 18px 0; }
.detail-actions { margin-top: 0; }
.muted { color: #8c8c8c; margin-top: 12px; font-size: 12px; }
.subline { display: block; color: #8c8c8c; font-size: 12px; margin-top: 5px; }
.pre-line { white-space: pre-wrap; word-break: break-word; }
.row-actions { display: inline-flex; align-items: center; white-space: nowrap; }
.danger-action { color: #ff4d4f; }
@media (max-width: 800px) { .metrics { grid-template-columns: repeat(2, 1fr); } }
</style>
