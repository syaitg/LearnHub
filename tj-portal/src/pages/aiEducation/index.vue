<template>
  <div class="ai-page container">
    <div class="ai-hero">
      <div>
        <div class="eyebrow">智能教育工作台</div>
        <h1>AI 教学工作台</h1>
        <p>统一管理 AI 出题草稿、视频内容分析和主观题评估。AI 只提供生成与建议，确认和发布仍由业务人员完成。</p>
      </div>
      <div class="hero-stats">
        <div class="hero-stat"><strong>{{ pendingBatchTotal }}</strong><span>待确认批次</span></div>
        <div class="hero-stat"><strong>{{ reviewTotal }}</strong><span>当前课程待复核</span></div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="ai-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="题目草稿审核" name="drafts">
        <div class="workspace-grid">
          <el-card class="panel-card create-card" shadow="never">
            <template #header><div class="card-title"><span>创建 AI 出题批次</span><el-tag type="primary">P1-A</el-tag></div></template>
            <el-alert title="视频测验也会进入这条草稿确认和发布流程，不会产生第二套题库。" type="info" :closable="false" show-icon />
            <el-form ref="batchFormRef" :model="batchForm" label-width="92px" class="workspace-form">
              <CourseCataloguePicker ref="batchPickerRef" v-model:course-id="batchForm.courseId" v-model:scope-type="batchForm.scopeType" v-model:scope-id="batchForm.scopeId" v-model:target-biz-id="batchForm.targetBizId" :show-media="false" :allow-chapter="false" />
              <el-form-item label="来源类型" class="is-required">
                <el-select v-model="batchForm.sourceType"><el-option label="手工主题" value="MANUAL_TOPIC" /><el-option label="材料文本" value="MATERIAL_TEXT" /></el-select>
              </el-form-item>
              <el-form-item v-if="batchForm.sourceType === 'MANUAL_TOPIC'" label="知识点" class="is-required"><el-input v-model="batchForm.knowledgePoints" type="textarea" rows="3" maxlength="5000" show-word-limit placeholder="请输入本小节需要考查的知识点，多个知识点可用换行或逗号分隔。" /></el-form-item><el-form-item v-if="batchForm.sourceType === 'MATERIAL_TEXT'" label="出题材料" class="is-required"><el-input v-model="batchForm.materialText" type="textarea" rows="4" maxlength="20000" show-word-limit placeholder="请输入课程材料，提示词和生成内容均使用中文。" /></el-form-item>
              <el-form-item label="题型" class="is-required"><el-checkbox-group v-model="batchForm.questionTypes"><el-checkbox v-for="item in questionTypeOptions" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item>
              <div class="form-row"><el-form-item label="题目数量" class="is-required"><el-input-number v-model="batchForm.questionCount" :min="1" :max="20" /></el-form-item><el-form-item label="难度" class="is-required"><el-select v-model="batchForm.difficulty"><el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="每题分值" class="is-required"><el-input-number v-model="batchForm.score" :min="1" :max="100" /></el-form-item></div>
              <el-form-item><el-button type="primary" :loading="batchCreating" @click="createBatch">开始生成草稿</el-button><el-button @click="resetBatchForm">重置</el-button></el-form-item>
            </el-form>
          </el-card>
          <el-card class="panel-card" shadow="never">
            <template #header><div class="card-title"><span>出题批次</span><el-button link type="primary" @click="loadBatches">刷新</el-button></div></template>
            <div class="filter-row"><el-select v-model="batchQuery.status" clearable placeholder="全部状态" @change="reloadBatches"><el-option label="已创建" value="CREATED" /><el-option label="生成中" value="GENERATING" /><el-option label="校验中" value="VALIDATING" /><el-option label="待确认" value="PENDING_CONFIRMATION" /><el-option label="部分发布" value="PARTIALLY_PUBLISHED" /><el-option label="已发布" value="PUBLISHED" /><el-option label="生成失败" value="GENERATION_FAILED" /><el-option label="全部无效" value="INVALID" /><el-option label="已取消" value="CANCELLED" /></el-select><CourseSelect v-model="batchQuery.courseId" placeholder="筛选课程" @change="reloadBatches" /><el-button @click="reloadBatches">查询</el-button></div>
            <el-table v-loading="batchLoading" :data="batches" empty-text="暂无 AI 出题批次" row-key="id" @row-click="openBatch">
              <el-table-column prop="id" label="批次" width="90" /><el-table-column prop="scopeName" label="考查范围" min-width="150" show-overflow-tooltip /><el-table-column prop="sourceType" label="来源" width="110"><template #default="{ row }">{{ sourceTypeText(row.sourceType) }}</template></el-table-column><el-table-column label="进度" width="120"><template #default="{ row }">{{ row.validCount || 0 }}/{{ row.totalCount || 0 }} 有效</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click.stop="openBatch(row)">审核</el-button></template></el-table-column>
            </el-table><div class="pager"><el-pagination v-model:current-page="batchQuery.pageNo" v-model:page-size="batchQuery.pageSize" layout="total, prev, pager, next" :total="batchTotal" @current-change="loadBatches" /></div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="视频智能处理" name="videos">
        <div class="workspace-grid">
          <div class="create-column">
            <el-card class="panel-card create-card" shadow="never">
              <template #header><div class="card-title"><span>创建视频 AI 任务</span><el-tag type="success">P2-A/B</el-tag></div></template>
              <el-alert title="任务完成后可查看转写、分段摘要、知识点和复习重点，并从分析结果生成测验草稿。" type="success" :closable="false" show-icon />
              <el-form :model="videoForm" label-width="92px" class="workspace-form"><CourseCataloguePicker ref="videoPickerRef" v-model:course-id="videoForm.courseId" v-model:scope-id="videoForm.sectionId" v-model:media-id="videoForm.mediaId" :show-scope="false" :show-section="true" :show-target="false" show-media /><el-form-item><el-button type="success" :loading="videoCreating" @click="createVideoTask">创建处理任务</el-button><el-button @click="resetVideoForm">重置</el-button></el-form-item></el-form>
            </el-card>
            <el-card class="panel-card create-card" shadow="never">
              <template #header><div class="card-title"><span>创建章级综合测验草稿</span><el-tag type="warning">P2-C</el-tag></div></template>
              <el-alert title="章级测验聚合本章多个小节的视频分析结果，仍复用题目草稿审核、确认和发布流程。" type="warning" :closable="false" show-icon />
              <el-form :model="chapterQuizForm" label-width="92px" class="workspace-form">
                <CourseCataloguePicker ref="chapterQuizPickerRef" v-model:course-id="chapterQuizForm.courseId" v-model:scope-type="chapterQuizForm.scopeType" v-model:scope-id="chapterQuizForm.chapterId" v-model:target-biz-id="chapterQuizForm.targetBizId" :allow-section="false" :allow-chapter="true" :show-target="true" :show-media="false" @loaded="handleChapterCatalogueLoaded" />
                <el-form-item label="视频任务" class="is-required">
                  <el-select v-model="chapterQuizForm.videoTaskIds" multiple collapse-tags collapse-tags-tooltip :loading="chapterVideoTaskLoading" :disabled="!chapterQuizForm.chapterId" placeholder="请选择本章已完成的视频任务" @change="handleChapterVideoTaskChange">
                    <el-option v-for="task in chapterVideoTasks" :key="task.id" :label="chapterVideoTaskLabel(task)" :value="task.id" />
                  </el-select>
                  <div class="form-tip">仅展示当前章节已完成的任务，最多选择 20 个视频。</div>
                </el-form-item>
                <el-form-item label="题型" class="is-required"><el-checkbox-group v-model="chapterQuizForm.questionTypes"><el-checkbox v-for="item in questionTypeOptions" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item>
                <div class="form-row"><el-form-item label="题目数量" class="is-required"><el-input-number v-model="chapterQuizForm.questionCount" :min="1" :max="20" /></el-form-item><el-form-item label="难度" class="is-required"><el-select v-model="chapterQuizForm.difficulty"><el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="每题分值" class="is-required"><el-input-number v-model="chapterQuizForm.score" :min="1" :max="100" /></el-form-item></div>
                <el-form-item><el-button type="warning" :loading="chapterQuizCreating" @click="createChapterQuiz">生成章级草稿</el-button><el-button @click="resetChapterQuizForm">重置</el-button></el-form-item>
              </el-form>
            </el-card>
          </div>          <el-card class="panel-card" shadow="never">
            <template #header><div class="card-title"><span>视频 AI 任务</span><el-button link type="primary" @click="loadVideoTasks">刷新</el-button></div></template>
            <div class="filter-row"><el-select v-model="videoQuery.status" clearable placeholder="全部状态" @change="reloadVideoTasks"><el-option label="等待处理" value="CREATED" /><el-option label="正在转写" value="TRANSCRIBING" /><el-option label="转写完成" value="TRANSCRIBED" /><el-option label="正在分析" value="ANALYZING" /><el-option label="处理完成" value="COMPLETED" /><el-option label="处理失败" value="FAILED" /></el-select><CourseSelect v-model="videoQuery.courseId" placeholder="筛选课程" @change="reloadVideoTasks" /><el-button @click="reloadVideoTasks">查询</el-button></div>
            <el-table v-loading="videoLoading" :data="videoTasks" empty-text="暂无视频 AI 任务" row-key="id" @row-click="openVideoTask">
              <el-table-column prop="id" label="任务" width="90" /><el-table-column prop="sectionName" label="视频小节" min-width="150" show-overflow-tooltip /><el-table-column prop="mediaName" label="媒资" min-width="130" show-overflow-tooltip /><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status, 'video')">{{ statusText(row.status, 'video') }}</el-tag></template></el-table-column><el-table-column label="进度" width="90"><template #default="{ row }">{{ row.progress || 0 }}%</template></el-table-column><el-table-column label="重试" width="75"><template #default="{ row }">{{ row.retryCount || 0 }}/{{ row.maxRetryCount || 0 }}</template></el-table-column><el-table-column label="创建时间" width="155"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click.stop="openVideoTask(row)">查看</el-button><el-button v-if="canRetryVideo(row)" link type="warning" :loading="videoRetryingId === String(row.id)" @click.stop="retryVideoTaskRow(row)">重试</el-button></template></el-table-column>
            </el-table><div class="pager"><el-pagination v-model:current-page="videoQuery.pageNo" v-model:page-size="videoQuery.pageSize" layout="total, prev, pager, next" :total="videoTotal" @current-change="loadVideoTasks" /></div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="主观题 AI 评估" name="reviews">
        <el-card class="panel-card review-card" shadow="never">
          <template #header><div class="card-title"><span>主观题人工复核工作台</span><el-tag type="warning">P1-C</el-tag></div></template>
          <el-alert title="AI 仅提供建议分、得分点和置信度，正式成绩必须由课程创建者人工确认。" type="warning" :closable="false" show-icon />
          <div class="filter-row review-filter"><CourseSelect v-model="reviewQuery.courseId" placeholder="请选择审核课程（必填）" @change="handleReviewCourseChange" /><el-input v-model="reviewQuery.targetBizId" clearable placeholder="练习目录 ID" @keyup.enter="reloadReviews" /><el-input v-model="reviewQuery.studentId" clearable placeholder="学生 ID" @keyup.enter="reloadReviews" /><el-select v-model="reviewQuery.manualReviewRecommended" clearable placeholder="是否建议复核"><el-option label="建议人工复核" :value="true" /><el-option label="无需人工复核" :value="false" /></el-select><el-input-number v-model="reviewQuery.minConfidence" :min="0" :max="1" :step="0.1" :precision="1" placeholder="最低置信度" controls-position="right" /><el-input-number v-model="reviewQuery.maxConfidence" :min="0" :max="1" :step="0.1" :precision="1" placeholder="最高置信度" controls-position="right" /><el-button type="primary" @click="reloadReviews">查询</el-button></div>
          <el-table v-loading="reviewLoading" :data="reviews" :empty-text="reviewQuery.courseId ? '暂无待审核记录' : '请先选择课程并查询待审核记录'">
            <el-table-column prop="answerId" label="答案" width="80" /><el-table-column prop="targetName" label="练习目录" min-width="130" show-overflow-tooltip /><el-table-column prop="questionName" label="题干" min-width="220" show-overflow-tooltip /><el-table-column prop="studentId" label="学生" width="85" /><el-table-column label="AI 建议分" width="105"><template #default="{ row }">{{ row.aiSuggestedScore ?? '-' }}/{{ row.questionScore ?? '-' }}</template></el-table-column><el-table-column label="置信度" width="95"><template #default="{ row }">{{ confidenceText(row.confidence) }}</template></el-table-column><el-table-column label="复核建议" width="105"><template #default="{ row }"><el-tag :type="row.manualReviewRecommended ? 'warning' : 'info'">{{ row.manualReviewRecommended ? '建议复核' : '可直接参考' }}</el-tag></template></el-table-column><el-table-column label="评估时间" width="155"><template #default="{ row }">{{ formatDateTime(row.evaluatedTime) }}</template></el-table-column><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click="openReview(row)">审核</el-button></template></el-table-column>
          </el-table><div class="pager"><el-pagination v-model:current-page="reviewQuery.pageNo" v-model:page-size="reviewQuery.pageSize" layout="total, prev, pager, next" :total="reviewTotal" @current-change="loadReviews" /></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>



    <el-drawer v-model="batchDrawerVisible" title="题目草稿批次详情" size="72%">
      <template v-if="currentBatch">
        <div class="detail-summary"><div><strong>{{ currentBatch.scopeName || `批次 ${currentBatch.id}` }}</strong><p>来源：{{ sourceTypeText(currentBatch.sourceType) }}；有效题目 {{ currentBatch.validCount || 0 }}/{{ currentBatch.totalCount || 0 }}</p></div><el-tag :type="statusType(currentBatch.status)">{{ statusText(currentBatch.status) }}</el-tag></div>
        <el-alert v-if="currentBatch.failureReason" :title="currentBatch.failureReason" type="error" :closable="false" show-icon />
        <div class="drawer-actions"><el-button @click="refreshBatch">刷新</el-button><el-button v-if="canConfirmBatch" type="primary" :loading="batchConfirming" @click="confirmBatch">确认全部有效草稿</el-button><el-button v-if="canPublishBatch" type="success" :loading="batchPublishing" @click="publishBatch">发布已确认题目</el-button><el-button v-if="canRetryBatch" type="warning" :loading="batchRetrying" @click="retryBatch">重试生成</el-button></div>
        <el-card v-for="draft in currentBatch.drafts || []" :key="draft.id" class="draft-card" shadow="never">
          <div class="draft-head"><span>第 {{ draft.sequenceNo || '-' }} 题 · {{ questionTypeText(draft.type) }} · {{ difficultyText(draft.difficulty) }}</span><el-tag :type="statusType(draft.status, 'draft')">{{ statusText(draft.status, 'draft') }}</el-tag></div>
          <div class="question-title">{{ draft.name || '暂无题干' }}</div>
          <div v-if="draft.options?.length" class="option-list"><div v-for="(option, index) in draft.options" :key="`${draft.id}-${index}`">{{ String.fromCharCode(65 + index) }}. {{ option }}</div></div>
          <div class="answer-line">参考答案：{{ answerDisplayText(draft.answer, draft.type) }}<span>{{ draft.score || 0 }} 分</span></div><div class="analysis">答案解析：{{ draft.analysis || '暂无解析' }}</div><div v-if="draft.validationMessage" class="validation">校验提示：{{ draft.validationMessage }}</div>
          <div class="draft-actions"><el-button v-if="!['PUBLISHED', 'REJECTED'].includes(draft.status)" link type="primary" @click="editDraft(draft)">编辑</el-button><el-button v-if="draft.status === 'PENDING_CONFIRMATION'" link type="success" :loading="draftActionId === String(draft.id)" @click="confirmDraft(draft)">确认</el-button><el-button v-if="draft.status === 'PENDING_CONFIRMATION'" link type="danger" :loading="draftActionId === String(draft.id)" @click="rejectDraft(draft)">驳回</el-button><el-button v-if="draft.status !== 'PUBLISHED'" link type="danger" :loading="draftActionId === String(draft.id)" @click="deleteDraft(draft)">删除</el-button></div>
        </el-card>
      </template>
    </el-drawer>

    <el-drawer v-model="videoDrawerVisible" title="视频 AI 任务详情" size="72%">
      <template v-if="currentVideo">
        <div class="detail-summary"><div><strong>{{ currentVideo.sectionName || `任务 ${currentVideo.id}` }}</strong><p>媒资：{{ currentVideo.mediaName || '-' }}；进度：{{ currentVideo.progress || 0 }}%</p></div><el-tag :type="statusType(currentVideo.status, 'video')">{{ statusText(currentVideo.status, 'video') }}</el-tag></div>
        <el-alert v-if="currentVideo.failureReason" :title="currentVideo.failureReason" type="error" :closable="false" show-icon />
        <div class="drawer-actions"><el-button @click="refreshVideo">刷新</el-button><el-button v-if="canRetryVideo(currentVideo)" type="warning" :loading="videoRetryingId === String(currentVideo.id)" @click="retryVideo">重试处理</el-button><el-button v-if="currentVideo.status === 'COMPLETED'" type="primary" @click="openVideoQuiz">生成测验草稿</el-button></div>
        <el-tabs class="result-tabs">
          <el-tab-pane label="内容摘要" name="summary"><h3>视频简介</h3><p>{{ currentVideo.videoIntroduction || '暂无' }}</p><h3>核心内容</h3><p>{{ currentVideo.coreContent || '暂无' }}</p><h3>关键结论</h3><p>{{ (currentVideo.keyConclusions || []).join('；') || '暂无' }}</p><h3>适合人群</h3><p>{{ (currentVideo.suitableLearners || []).join('；') || '暂无' }}</p><h3>复习重点</h3><p>{{ (currentVideo.reviewPoints || []).join('；') || '暂无' }}</p></el-tab-pane>
          <el-tab-pane label="分段摘要" name="sections"><div v-for="(item, index) in currentVideo.sectionSummaries || []" :key="`${item.title}-${index}`" class="knowledge-item"><div class="knowledge-title"><el-tag size="small">{{ formatDuration(item.startMs) }} - {{ formatDuration(item.endMs) }}</el-tag><strong>{{ item.title || `第 ${index + 1} 段` }}</strong></div><p>{{ item.summary || '暂无摘要' }}</p></div><el-empty v-if="!currentVideo.sectionSummaries?.length" description="暂无分段摘要" /></el-tab-pane>
          <el-tab-pane label="知识点" name="knowledge"><div v-for="(item, index) in currentVideo.knowledgePoints || []" :key="`${item.name}-${index}`" class="knowledge-item"><div class="knowledge-title"><strong>{{ item.name || `知识点 ${index + 1}` }}</strong><span>重要度 {{ importanceText(item.importance) }} · 难度 {{ knowledgeDifficultyText(item.difficulty) }}</span></div><p>{{ item.description || '暂无说明' }}</p><div v-if="item.prerequisites?.length">前置知识：{{ item.prerequisites.join('、') }}</div></div><el-empty v-if="!currentVideo.knowledgePoints?.length" description="暂无知识点" /></el-tab-pane>
          <el-tab-pane label="转写全文" name="transcript"><div class="transcript-box"><div v-if="currentVideo.fullText">{{ currentVideo.fullText }}</div><el-empty v-else description="暂无转写文本" /></div><div v-if="currentVideo.transcriptSegments?.length" class="transcript-box"><div v-for="(item, index) in currentVideo.transcriptSegments" :key="index" class="transcript-segment"><span>{{ formatDuration(item.startMs) }}</span><p>{{ item.text }}</p></div></div></el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
    <el-dialog v-model="editDialogVisible" title="编辑题目草稿" width="620px"><el-form :model="editingDraft" label-width="82px"><el-form-item label="题干" class="is-required"><el-input v-model="editingDraft.name" type="textarea" rows="3" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="题型"><el-select v-model="editingDraft.type"><el-option v-for="item in questionTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="选项" v-if="editingDraft.type !== 4 && editingDraft.type !== 5"><el-input v-model="editingOptionsText" type="textarea" rows="3" placeholder="每行一个选项" /></el-form-item><el-form-item label="答案" class="is-required"><el-input v-model="editingDraft.answer" maxlength="10000" show-word-limit /></el-form-item><div class="form-row"><el-form-item label="难度"><el-select v-model="editingDraft.difficulty"><el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="分值"><el-input-number v-model="editingDraft.score" :min="1" :max="100" /></el-form-item></div><el-form-item label="解析" class="is-required"><el-input v-model="editingDraft.analysis" type="textarea" rows="4" maxlength="300" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="editDialogVisible = false">取消</el-button><el-button type="primary" :loading="editSaving" @click="saveDraftEdit">保存并重新校验</el-button></template></el-dialog>

    <el-dialog v-model="videoQuizDialogVisible" title="根据视频生成测验草稿" width="560px"><el-form :model="videoQuizForm" label-width="100px"><CourseCataloguePicker ref="videoQuizPickerRef" :course-id="currentVideo?.courseId" :scope-id="currentVideo?.sectionId" v-model:target-biz-id="videoQuizForm.targetBizId" course-readonly :show-scope="false" :show-section="false" :show-media="false" /><el-form-item label="题型"><el-checkbox-group v-model="videoQuizForm.questionTypes"><el-checkbox v-for="item in questionTypeOptions" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item><div class="form-row"><el-form-item label="题目数量"><el-input-number v-model="videoQuizForm.questionCount" :min="1" :max="20" /></el-form-item><el-form-item label="难度"><el-select v-model="videoQuizForm.difficulty"><el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="每题分值"><el-input-number v-model="videoQuizForm.score" :min="1" :max="100" /></el-form-item></div></el-form><template #footer><el-button @click="videoQuizDialogVisible = false">取消</el-button><el-button type="primary" :loading="quizCreating" @click="createVideoQuiz">生成草稿</el-button></template></el-dialog>

    <el-dialog v-model="reviewDialogVisible" title="确认主观题 AI 评估" width="680px"><template v-if="currentReview"><div class="review-question"><h3>{{ currentReview.questionName }}</h3><p><strong>学生答案：</strong>{{ currentReview.studentAnswer || '未作答' }}</p><p><strong>参考答案：</strong>{{ currentReview.standardAnswer || '-' }}</p><div class="review-points"><div><b>命中得分点</b><span v-for="item in currentReview.matchedPoints || []" :key="item" class="point good">{{ item }}</span></div><div><b>缺失得分点</b><span v-for="item in currentReview.missingPoints || []" :key="item" class="point warn">{{ item }}</span></div><div><b>问题表述</b><span v-for="item in currentReview.incorrectStatements || []" :key="item" class="point bad">{{ item }}</span></div></div><p class="suggestion">改进建议：{{ currentReview.improvementSuggestion || '暂无' }}</p><el-form label-width="100px"><el-form-item label="人工确认得分"><el-input-number v-model="reviewScore" :min="0" :max="currentReview.questionScore || 100" /></el-form-item></el-form></div></template><template #footer><el-button @click="reviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="reviewSaving" @click="confirmReview">确认最终得分</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CourseCataloguePicker from './components/CourseCataloguePicker.vue'
import CourseSelect from './components/CourseSelect.vue'
import { questionTypeOptions, difficultyOptions, questionTypeText, difficultyText, knowledgeDifficultyText, importanceText, answerDisplayText, sourceTypeText, statusText, statusType, formatDateTime, formatDuration } from './dictionaries'
import { createAiQuestionBatch, queryAiQuestionBatches, getAiQuestionBatch, confirmAiQuestionBatch, publishAiQuestionBatch, retryAiQuestionBatch, updateAiQuestionDraft, confirmAiQuestionDraft, rejectAiQuestionDraft, deleteAiQuestionDraft, createVideoAiTask, queryVideoAiTasks, getVideoAiTask, retryVideoAiTask, createVideoQuizDrafts, createChapterQuizDrafts, queryPendingAiReviews, confirmPracticeAiReview, unwrapAiResponse, getAiErrorMessage } from '@/api/aiEducation'
import { createClientRequestId } from './requestId'

const activeTab = ref('drafts')
const batchForm = reactive({ courseId: '', scopeType: 'SECTION', scopeId: '', targetBizId: '', sourceType: 'MANUAL_TOPIC', knowledgePoints: '', materialText: '', questionTypes: [1, 4], questionCount: 5, difficulty: 2, score: 5, requestId: createClientRequestId('batch') })
const videoForm = reactive({ courseId: '', sectionId: '', mediaId: '', requestId: createClientRequestId('video') })
const chapterQuizForm = reactive({ courseId: '', scopeType: 'CHAPTER', chapterId: '', targetBizId: '', videoTaskIds: [], questionTypes: [1, 4], questionCount: 10, difficulty: 2, score: 5, requestId: createClientRequestId('chapter-quiz') })
const videoQuizForm = reactive({ targetBizId: '', questionTypes: [1, 4], questionCount: 5, difficulty: 2, score: 5, requestId: createClientRequestId('video-quiz') })
const batchQuery = reactive({ pageNo: 1, pageSize: 10, status: '', courseId: '' })
const videoQuery = reactive({ pageNo: 1, pageSize: 10, status: '', courseId: '' })
const reviewQuery = reactive({ pageNo: 1, pageSize: 10, courseId: '', targetBizId: '', studentId: '', manualReviewRecommended: null, minConfidence: null, maxConfidence: null })
const batches = ref([]), batchTotal = ref(0), batchLoading = ref(false), batchCreating = ref(false)
const videoTasks = ref([]), videoTotal = ref(0), videoLoading = ref(false), videoCreating = ref(false)
const chapterVideoTasks = ref([]), chapterVideoTaskLoading = ref(false), chapterCatalogueChapters = ref([])
const reviews = ref([]), reviewTotal = ref(0), reviewLoading = ref(false), pendingBatchTotal = ref(0)
const currentBatch = ref(null), currentVideo = ref(null), currentReview = ref(null)
const batchPickerRef = ref(null), videoPickerRef = ref(null), videoQuizPickerRef = ref(null), chapterQuizPickerRef = ref(null)
const batchDrawerVisible = ref(false), videoDrawerVisible = ref(false), editDialogVisible = ref(false), videoQuizDialogVisible = ref(false), reviewDialogVisible = ref(false)
const editingDraft = reactive({}), editingOptionsText = ref(''), editSaving = ref(false), quizCreating = ref(false), chapterQuizCreating = ref(false), reviewSaving = ref(false), reviewScore = ref(0)
const batchConfirming = ref(false), batchPublishing = ref(false), batchRetrying = ref(false), draftActionId = ref(''), videoRetryingId = ref('')
let batchListRequestVersion = 0
let pendingBatchTotalRequestVersion = 0
let batchDetailRequestVersion = 0
let videoListRequestVersion = 0
let videoDetailRequestVersion = 0
let chapterVideoTaskRequestVersion = 0
let reviewListRequestVersion = 0
let videoRefreshTimer = null
let videoRefreshVersion = 0

/** 读取分页接口数据。 */
const pageData = response => unwrapAiResponse(response) || { list: [], total: 0 }
/** 移除分页查询参数中的空值，避免后端收到无意义的空字符串。 */
const compactQuery = query => Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined))
/** 统一判断接口操作是否成功并展示结果。 */
const ensureSuccess = (response, successMessage) => { if (response?.code !== 200) { ElMessage.error(getAiErrorMessage(response, '操作失败，请稍后重试')); return false } ElMessage.success(successMessage); return true }
/** 将来源类型转换为中文。 */
/** 安全打开确认框，用户取消时返回 false。 */
const safeConfirm = async (message, title) => ElMessageBox.confirm(message, title).then(() => true).catch(() => false)
/** 查询全部待确认批次数量，供工作台顶部展示。 */
const loadPendingBatchTotal = async () => {
  const currentVersion = ++pendingBatchTotalRequestVersion
  const response = await queryAiQuestionBatches({ pageNo: 1, pageSize: 1, status: 'PENDING_CONFIRMATION' })
  if (currentVersion !== pendingBatchTotalRequestVersion || response?.code !== 200) return
  const page = pageData(response)
  pendingBatchTotal.value = Number(page.total || 0)
}
/** 查询 AI 出题批次，并忽略已过期的筛选请求。 */
const loadBatches = async () => {
  const currentVersion = ++batchListRequestVersion
  const query = compactQuery({ ...batchQuery })
  batchLoading.value = true
  try {
    const response = await queryAiQuestionBatches(query)
    if (currentVersion !== batchListRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '出题批次加载失败'))
    const page = pageData(response)
    batches.value = page.list || []
    batchTotal.value = Number(page.total || 0)
    await loadPendingBatchTotal()
  } finally {
    if (currentVersion === batchListRequestVersion) batchLoading.value = false
  }
}
/** 重置批次页码后查询。 */
const reloadBatches = async () => { batchQuery.pageNo = 1; await loadBatches() }
/** 创建 AI 出题批次。 */
const createBatch = async () => {
  if (!batchForm.courseId) return ElMessage.warning('请选择课程')
  if (!batchForm.scopeId) return ElMessage.warning('请选择出题范围')
  if (!batchForm.targetBizId) return ElMessage.warning('当前课程没有可用发布目录，请先创建练习/测试目录')
  if (!batchForm.questionTypes.length) return ElMessage.warning('请至少选择一种题型')
  const questionCount = Number(batchForm.questionCount)
  const difficulty = Number(batchForm.difficulty)
  const score = Number(batchForm.score)
  if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 20) {
    return ElMessage.warning('题目数量必须为 1 到 20 之间的整数')
  }
  if (![1, 2, 3].includes(difficulty)) return ElMessage.warning('请选择合法难度')
  if (!Number.isInteger(score) || score < 1 || score > 100) return ElMessage.warning('每题分值必须为 1 到 100 之间的整数')

  const knowledgePoints = batchForm.sourceType === 'MANUAL_TOPIC'
    ? batchForm.knowledgePoints.split(/[，,\n]/).map(item => item.trim()).filter(Boolean)
    : []
  if (batchForm.sourceType === 'MANUAL_TOPIC') {
    if (!knowledgePoints.length) return ElMessage.warning('请输入需要考查的知识点')
    if (knowledgePoints.length > 30 || knowledgePoints.some(item => item.length > 100)) {
      return ElMessage.warning('知识点最多 30 个，每个不能超过 100 个字符')
    }
  }
  const materialText = String(batchForm.materialText || '').trim()
  if (batchForm.sourceType === 'MATERIAL_TEXT' && !materialText) return ElMessage.warning('请输入出题材料')

  const data = {
    courseId: String(batchForm.courseId),
    scopeType: batchForm.scopeType,
    scopeId: String(batchForm.scopeId),
    sourceType: batchForm.sourceType,
    targetBizId: String(batchForm.targetBizId),
    questionTypes: batchForm.questionTypes.map(Number),
    questionCount,
    difficulty,
    score,
    requestId: batchForm.requestId,
  }
  if (batchForm.sourceType === 'MANUAL_TOPIC') data.knowledgePoints = knowledgePoints
  if (batchForm.sourceType === 'MATERIAL_TEXT') data.materialText = materialText

  batchCreating.value = true
  try {
    const response = await createAiQuestionBatch(data)
    const id = unwrapAiResponse(response)
    if (!id) return ElMessage.error(getAiErrorMessage(response, '创建出题任务失败'))
    ElMessage.success('出题任务已创建')
    resetBatchForm()
    await loadBatches()
    await openBatch({ id })
  } finally { batchCreating.value = false }
}
/** 重置出题表单。 */
const resetBatchForm = () => {
  Object.assign(batchForm, { courseId: '', scopeType: 'SECTION', scopeId: '', targetBizId: '', sourceType: 'MANUAL_TOPIC', knowledgePoints: '', materialText: '', questionTypes: [1, 4], questionCount: 5, difficulty: 2, score: 5, requestId: createClientRequestId('batch') })
  batchPickerRef.value?.reset()
}
/** 打开批次详情，并避免较早点击的批次覆盖当前批次。 */
const openBatch = async row => {
  const batchId = row?.id
  if (!batchId) return
  const currentVersion = ++batchDetailRequestVersion
  const response = await getAiQuestionBatch(batchId)
  if (currentVersion !== batchDetailRequestVersion) return
  const detail = unwrapAiResponse(response)
  if (!detail) return ElMessage.error(getAiErrorMessage(response, '批次详情加载失败'))
  currentBatch.value = detail
  batchDrawerVisible.value = true
}
/** 判断批次是否可整批确认。 */
const canConfirmBatch = computed(() => currentBatch.value && ['PENDING_CONFIRMATION', 'PARTIALLY_PUBLISHED'].includes(currentBatch.value.status) && (currentBatch.value.drafts || []).some(draft => draft.status === 'PENDING_CONFIRMATION'))
/** 判断批次是否可以发布。 */
const canPublishBatch = computed(() => currentBatch.value && ['PENDING_CONFIRMATION', 'PARTIALLY_PUBLISHED'].includes(currentBatch.value.status) && (currentBatch.value.drafts || []).some(draft => draft.status === 'CONFIRMED'))
/** 判断批次是否可以重试。 */
const canRetryBatch = computed(() => currentBatch.value?.status === 'GENERATION_FAILED' && Number(currentBatch.value.retryCount || 0) < Number(currentBatch.value.maxRetryCount || 0))
/** 确认全部有效草稿。 */
const confirmBatch = async () => {
  const batchId = currentBatch.value?.id
  if (!batchId || batchConfirming.value || !await safeConfirm('确认后会把当前有效草稿标记为已确认，仍可继续编辑或发布。', '确认草稿')) return
  batchConfirming.value = true
  try {
    if (ensureSuccess(await confirmAiQuestionBatch(batchId), '批次草稿已确认')) await refreshBatch(batchId)
  } finally {
    batchConfirming.value = false
  }
}
/** 发布已确认题目。 */
const publishBatch = async () => {
  const batchId = currentBatch.value?.id
  if (!batchId || batchPublishing.value || !await safeConfirm('发布后题目将进入正式题库，请确认题目内容已经审核。', '发布题目')) return
  batchPublishing.value = true
  try {
    if (ensureSuccess(await publishAiQuestionBatch(batchId), '题目已发布')) {
      await refreshBatch(batchId)
      await loadBatches()
    }
  } finally {
    batchPublishing.value = false
  }
}
/** 重试失败批次。 */
const retryBatch = async () => {
  const batchId = currentBatch.value?.id
  if (!batchId || batchRetrying.value) return
  batchRetrying.value = true
  try {
    if (ensureSuccess(await retryAiQuestionBatch(batchId), '已提交重试任务')) {
      await refreshBatch(batchId)
      await loadBatches()
    }
  } finally {
    batchRetrying.value = false
  }
}
/** 刷新当前批次，并避免刷新结果覆盖后来打开的批次。 */
const refreshBatch = async batchId => {
  const targetId = batchId || currentBatch.value?.id
  if (!targetId) return
  const currentVersion = ++batchDetailRequestVersion
  const response = await getAiQuestionBatch(targetId)
  if (currentVersion !== batchDetailRequestVersion
    || !batchDrawerVisible.value
    || String(currentBatch.value?.id) !== String(targetId)) return
  const detail = unwrapAiResponse(response)
  if (!detail) return ElMessage.error(getAiErrorMessage(response, '批次详情刷新失败'))
  currentBatch.value = detail
}
/** 编辑草稿。 */
const editDraft = draft => {
  Object.assign(editingDraft, JSON.parse(JSON.stringify(draft)))
  editingOptionsText.value = (draft.options || []).join('\n')
  editDialogVisible.value = true
}
/**
 * 保存草稿编辑，并在提交前完成与后端一致的基础校验。
 */
const saveDraftEdit = async () => {
  const type = Number(editingDraft.type)
  const name = String(editingDraft.name || '').trim()
  const answer = String(editingDraft.answer || '').trim()
  const analysis = String(editingDraft.analysis || '').trim()
  const difficulty = Number(editingDraft.difficulty)
  const score = Number(editingDraft.score)
  const options = editingOptionsText.value.split('\n').map(item => item.trim()).filter(Boolean)

  if (!name) return ElMessage.warning('题干不能为空')
  if (name.length > 1000) return ElMessage.warning('题干长度不能超过 1000 个字符')
  if (!answer) return ElMessage.warning('答案不能为空')
  if (!analysis) return ElMessage.warning('解析不能为空')
  if (analysis.length > 300) return ElMessage.warning('解析长度不能超过 300 个字符')
  if (![1, 2, 3, 4, 5].includes(type)) return ElMessage.warning('题型不合法')
  if (![1, 2, 3].includes(difficulty)) return ElMessage.warning('难度必须为简单、中等或困难')
  if (!Number.isInteger(score) || score < 1 || score > 100) return ElMessage.warning('分值必须为 1 到 100 分之间的整数')

  let normalizedOptions = []
  if ([1, 2, 3].includes(type)) {
    if (options.length < 2 || options.length > 10) return ElMessage.warning('选择题选项应为 2 到 10 个')
    if (options.some(item => item.length > 500)) return ElMessage.warning('单个选项不能超过 500 个字符')
    const indexes = answer.split(',').map(item => item.trim())
    if (indexes.some(item => !/^\d+$/.test(item))) return ElMessage.warning('选择题答案必须使用非负整数下标，并以逗号分隔')
    const numbers = indexes.map(Number)
    if (new Set(numbers).size !== numbers.length) return ElMessage.warning('选择题答案不能包含重复下标')
    if (numbers.some(index => index < 0 || index >= options.length)) return ElMessage.warning('选择题答案下标超出选项范围')
    if (type === 1 && numbers.length !== 1) return ElMessage.warning('单选题只能填写一个答案下标')
    normalizedOptions = options
  }
  if (type === 4 && !['0', '1'].includes(answer)) return ElMessage.warning('判断题答案只能填写 0 或 1')
  if (type === 5 && answer.length > 10000) return ElMessage.warning('主观题参考答案不能超过 10000 个字符')

  editSaving.value = true
  try {
    const data = { name, type, difficulty, score, options: normalizedOptions, answer, analysis }
    if (ensureSuccess(await updateAiQuestionDraft(editingDraft.id, data), '草稿已保存并重新校验')) {
      editDialogVisible.value = false
      await refreshBatch()
    }
  } finally {
    editSaving.value = false
  }
}
/** 确认单道草稿。 */
const confirmDraft = async draft => {
  const batchId = currentBatch.value?.id
  const draftId = String(draft?.id || '')
  if (!batchId || !draftId || draftActionId.value) return
  draftActionId.value = draftId
  try {
    if (ensureSuccess(await confirmAiQuestionDraft(draft.id), '题目草稿已确认')) await refreshBatch(batchId)
  } finally {
    draftActionId.value = ''
  }
}
/** 驳回单道草稿。 */
const rejectDraft = async draft => {
  const batchId = currentBatch.value?.id
  const draftId = String(draft?.id || '')
  if (!batchId || !draftId || draftActionId.value) return
  const result = await ElMessageBox.prompt('请输入驳回原因（可选）', '驳回题目', {
    inputPlaceholder: '例如：题干超出本节视频范围',
  }).catch(() => null)
  if (!result) return
  draftActionId.value = draftId
  try {
    if (ensureSuccess(await rejectAiQuestionDraft(draft.id, { reason: result.value }), '题目草稿已驳回')) await refreshBatch(batchId)
  } finally {
    draftActionId.value = ''
  }
}
/** 删除一条尚未发布的 AI 题目草稿。 */
const deleteDraft = async draft => {
  const batchId = currentBatch.value?.id
  const draftId = String(draft?.id || '')
  if (!batchId || !draftId || draftActionId.value || draft.status === 'PUBLISHED') return
  if (!await safeConfirm('删除后该题目将从当前 AI 出题批次中移除，是否继续？', '删除题目草稿')) return
  draftActionId.value = draftId
  try {
    if (ensureSuccess(await deleteAiQuestionDraft(draft.id), '题目草稿已删除')) {
      await refreshBatch(batchId)
      await loadBatches()
    }
  } finally {
    draftActionId.value = ''
  }
}
/** 查询视频 AI 任务，并忽略已过期的筛选请求。 */
const loadVideoTasks = async () => {
  const currentVersion = ++videoListRequestVersion
  const query = compactQuery({ ...videoQuery })
  videoLoading.value = true
  try {
    const response = await queryVideoAiTasks(query)
    if (currentVersion !== videoListRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '视频任务加载失败'))
    const page = pageData(response)
    videoTasks.value = page.list || []
    videoTotal.value = Number(page.total || 0)
  } finally {
    if (currentVersion === videoListRequestVersion) videoLoading.value = false
  }
}
/** 重置视频查询页码后查询。 */
const reloadVideoTasks = async () => { videoQuery.pageNo = 1; await loadVideoTasks() }
/** 处理章级目录加载完成事件，并准备当前章节的视频任务。 */
const handleChapterCatalogueLoaded = chapters => {
  chapterCatalogueChapters.value = chapters || []
  loadChapterVideoTasks()
}
/** 格式化章级视频任务选项，帮助审核人员识别所属小节。 */
const chapterVideoTaskLabel = task => `${task.sectionName || `小节 ${task.sectionId || '-'}`}（任务 ${task.id}）`
/** 限制章级测验最多选择 20 个已完成视频任务。 */
const handleChapterVideoTaskChange = values => {
  if (values.length <= 20) return
  chapterQuizForm.videoTaskIds = values.slice(0, 20)
  ElMessage.warning('章级综合测验最多选择 20 个视频任务')
}
/** 查询当前章节已完成的视频 AI 任务，并按课程目录顺序展示。 */
const loadChapterVideoTasks = async () => {
  const courseId = chapterQuizForm.courseId
  const chapterId = chapterQuizForm.chapterId
  const currentVersion = ++chapterVideoTaskRequestVersion
  chapterVideoTasks.value = []
  if (!courseId || !chapterId) return
  const chapter = chapterCatalogueChapters.value.find(item => String(item.id) === String(chapterId))
  const sectionIds = new Set((chapter?.sections || [])
    .filter(section => Number(section.type) === 2)
    .map(section => String(section.id)))
  if (!sectionIds.size) return
  chapterVideoTaskLoading.value = true
  try {
    const response = await queryVideoAiTasks({ pageNo: 1, pageSize: 100, courseId: String(courseId), status: 'COMPLETED' })
    if (currentVersion !== chapterVideoTaskRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '章节视频任务加载失败'))
    const page = pageData(response)
    const order = new Map((chapter?.sections || []).map((section, index) => [String(section.id), index]))
    chapterVideoTasks.value = (page.list || [])
      .filter(task => sectionIds.has(String(task.sectionId)) && task.status === 'COMPLETED')
      .sort((left, right) => (order.get(String(left.sectionId)) ?? 9999) - (order.get(String(right.sectionId)) ?? 9999))
    const availableIds = new Set(chapterVideoTasks.value.map(task => String(task.id)))
    chapterQuizForm.videoTaskIds = chapterQuizForm.videoTaskIds.filter(id => availableIds.has(String(id)))
  } finally {
    if (currentVersion === chapterVideoTaskRequestVersion) chapterVideoTaskLoading.value = false
  }
}
/** 重置章级综合测验表单及其目录选择器。 */
const resetChapterQuizForm = () => {
  Object.assign(chapterQuizForm, { courseId: '', scopeType: 'CHAPTER', chapterId: '', targetBizId: '', videoTaskIds: [], questionTypes: [1, 4], questionCount: 10, difficulty: 2, score: 5, requestId: createClientRequestId('chapter-quiz') })
  chapterCatalogueChapters.value = []
  chapterVideoTasks.value = []
  chapterVideoTaskRequestVersion += 1
  chapterQuizPickerRef.value?.reset()
}
/** 创建章级综合测验题目草稿，并复用 P1-A 审核发布流程。 */
const createChapterQuiz = async () => {
  if (!chapterQuizForm.courseId || !chapterQuizForm.chapterId || !chapterQuizForm.targetBizId) return ElMessage.warning('请选择课程、章节和章级练习目录')
  if (!chapterQuizForm.videoTaskIds.length) return ElMessage.warning('请至少选择一个已完成的视频任务')
  if (chapterQuizForm.videoTaskIds.length > 20) return ElMessage.warning('章级综合测验最多选择 20 个视频任务')
  if (!chapterQuizForm.questionTypes.length) return ElMessage.warning('请选择题型')
  const questionCount = Number(chapterQuizForm.questionCount)
  const difficulty = Number(chapterQuizForm.difficulty)
  const score = Number(chapterQuizForm.score)
  if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 20) return ElMessage.warning('题目数量必须为 1 到 20 之间的整数')
  if (![1, 2, 3].includes(difficulty)) return ElMessage.warning('请选择合法难度')
  if (!Number.isInteger(score) || score < 1 || score > 100) return ElMessage.warning('每题分值必须为 1 到 100 之间的整数')
  chapterQuizCreating.value = true
  try {
    const response = await createChapterQuizDrafts({
      courseId: String(chapterQuizForm.courseId),
      chapterId: String(chapterQuizForm.chapterId),
      videoTaskIds: chapterQuizForm.videoTaskIds.map(String),
      targetBizId: String(chapterQuizForm.targetBizId),
      questionTypes: chapterQuizForm.questionTypes.map(Number),
      questionCount,
      difficulty,
      score,
      requestId: chapterQuizForm.requestId,
    })
    const batchId = unwrapAiResponse(response)
    if (!batchId) return ElMessage.error(getAiErrorMessage(response, '创建章级测验草稿失败'))
    ElMessage.success('章级综合测验草稿已创建，请到题目草稿审核中确认')
    activeTab.value = 'drafts'
    resetChapterQuizForm()
    await loadBatches()
    await openBatch({ id: batchId })
  } finally {
    chapterQuizCreating.value = false
  }
}
/** 监听章级课程或章节变化，刷新可用的视频任务。 */
watch(() => [chapterQuizForm.courseId, chapterQuizForm.chapterId], () => {
  if (chapterQuizForm.courseId && chapterQuizForm.chapterId && chapterCatalogueChapters.value.length) loadChapterVideoTasks()
  else if (!chapterQuizForm.chapterId) {
    chapterVideoTasks.value = []
    chapterQuizForm.videoTaskIds = []
  }
})
/** 创建视频 AI 任务。 */
const createVideoTask = async () => {
  if (!videoForm.courseId || !videoForm.sectionId || !videoForm.mediaId) {
    return ElMessage.warning('请选择课程、视频小节和媒资')
  }
  videoCreating.value = true
  try {
    const response = await createVideoAiTask({
      courseId: String(videoForm.courseId),
      sectionId: String(videoForm.sectionId),
      mediaId: String(videoForm.mediaId),
      requestId: videoForm.requestId,
    })
    if (!unwrapAiResponse(response)) return ElMessage.error(getAiErrorMessage(response, '创建视频任务失败'))
    ElMessage.success('视频 AI 任务已创建')
    resetVideoForm()
    await loadVideoTasks()
  } finally {
    videoCreating.value = false
  }
}
/** 重置视频任务表单。 */
const resetVideoForm = () => {
  Object.assign(videoForm, { courseId: '', sectionId: '', mediaId: '', requestId: createClientRequestId('video') })
  videoPickerRef.value?.reset()
}
/** 重置视频测验草稿表单。 */
const resetVideoQuizForm = () => {
  Object.assign(videoQuizForm, { targetBizId: '', questionTypes: [1, 4], questionCount: 5, difficulty: 2, score: 5, requestId: createClientRequestId('video-quiz') })
  videoQuizPickerRef.value?.reset()
}
/** 打开视频测验草稿窗口，并加载当前小节所属课程目录。 */
const openVideoQuiz = async () => {
  if (!currentVideo.value || currentVideo.value.status !== 'COMPLETED') return ElMessage.warning('视频处理完成后才能生成测验草稿')
  resetVideoQuizForm()
  videoQuizDialogVisible.value = true
  await nextTick()
  await videoQuizPickerRef.value?.loadCatalogues()
}
/** 打开视频任务详情，并避免较早点击的任务覆盖当前任务。 */
const openVideoTask = async row => {
  const taskId = row?.id
  if (!taskId) return
  const currentVersion = ++videoDetailRequestVersion
  const response = await getVideoAiTask(taskId)
  if (currentVersion !== videoDetailRequestVersion) return
  const detail = unwrapAiResponse(response)
  if (!detail) return ElMessage.error(getAiErrorMessage(response, '视频任务详情加载失败'))
  currentVideo.value = detail
  videoDrawerVisible.value = true
}
/** 判断失败视频任务是否仍可重试。 */
const canRetryVideo = task => task?.status === 'FAILED' && Number(task.retryCount || 0) < Number(task.maxRetryCount || 0)
/** 从列表直接重试视频任务。 */
const retryVideoTaskRow = async row => {
  if (!canRetryVideo(row) || videoRetryingId.value) return
  const taskId = String(row.id)
  videoRetryingId.value = taskId
  try {
    if (ensureSuccess(await retryVideoAiTask(row.id), '已提交视频任务重试')) await loadVideoTasks()
  } finally {
    videoRetryingId.value = ''
  }
}
/** 重试视频任务。 */
const retryVideo = async () => {
  const taskId = currentVideo.value?.id
  if (!taskId || videoRetryingId.value) return
  videoRetryingId.value = String(taskId)
  try {
    if (ensureSuccess(await retryVideoAiTask(taskId), '已提交视频任务重试')) {
      await refreshVideo(taskId)
      await loadVideoTasks()
    }
  } finally {
    videoRetryingId.value = ''
  }
}
/**
 * 刷新当前视频任务详情，并避免较早返回的请求覆盖后来打开的任务。
 */
const refreshVideo = async taskId => {
  const targetId = taskId || currentVideo.value?.id
  if (!targetId) return
  const currentVersion = ++videoDetailRequestVersion
  const response = await getVideoAiTask(targetId)
  if (currentVersion !== videoDetailRequestVersion
    || !videoDrawerVisible.value
    || String(currentVideo.value?.id) !== String(targetId)) return
  const detail = unwrapAiResponse(response)
  if (!detail) return ElMessage.error(getAiErrorMessage(response, '视频任务详情刷新失败'))
  currentVideo.value = detail
}
/** 创建视频测验草稿，仅允许处理完成的视频执行。 */
const createVideoQuiz = async () => {
  const taskId = currentVideo.value?.id
  if (!taskId || currentVideo.value.status !== 'COMPLETED') return ElMessage.warning('视频处理完成后才能生成测验草稿')
  if (!videoQuizForm.targetBizId || !videoQuizForm.questionTypes.length) return ElMessage.warning('请选择发布目录和题型')

  const questionCount = Number(videoQuizForm.questionCount)
  const difficulty = Number(videoQuizForm.difficulty)
  const score = Number(videoQuizForm.score)
  if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 20) {
    return ElMessage.warning('题目数量必须为 1 到 20 之间的整数')
  }
  if (![1, 2, 3].includes(difficulty)) return ElMessage.warning('请选择合法难度')
  if (!Number.isInteger(score) || score < 1 || score > 100) return ElMessage.warning('每题分值必须为 1 到 100 之间的整数')

  quizCreating.value = true
  try {
    const response = await createVideoQuizDrafts(taskId, {
      targetBizId: String(videoQuizForm.targetBizId),
      questionTypes: videoQuizForm.questionTypes.map(Number),
      questionCount,
      difficulty,
      score,
      requestId: videoQuizForm.requestId,
    })
    const batchId = unwrapAiResponse(response)
    if (!batchId) return ElMessage.error(getAiErrorMessage(response, '创建测验草稿失败'))
    ElMessage.success('视频测验草稿已创建，请审核后确认和发布')
    if (String(currentVideo.value?.id) === String(taskId)) videoQuizDialogVisible.value = false
    activeTab.value = 'drafts'
    await loadBatches()
    await openBatch({ id: batchId })
  } finally {
    quizCreating.value = false
  }
}
/** 查询主观题待审核列表，并忽略已过期的筛选请求。 */
const loadReviews = async () => {
  const currentVersion = ++reviewListRequestVersion
  if (!reviewQuery.courseId) {
    reviews.value = []
    reviewTotal.value = 0
    reviewLoading.value = false
    return
  }
  const query = compactQuery({ ...reviewQuery, courseId: String(reviewQuery.courseId) })
  reviewLoading.value = true
  try {
    const response = await queryPendingAiReviews(query)
    if (currentVersion !== reviewListRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '主观题待审核列表加载失败'))
    const page = pageData(response)
    reviews.value = page.list || []
    reviewTotal.value = Number(page.total || 0)
  } finally {
    if (currentVersion === reviewListRequestVersion) reviewLoading.value = false
  }
}
/** 重置评估查询页码。 */
const reloadReviews = async () => {
  if (!reviewQuery.courseId) return ElMessage.warning('请先选择课程')
  if (reviewQuery.minConfidence !== null && reviewQuery.maxConfidence !== null && Number(reviewQuery.minConfidence) > Number(reviewQuery.maxConfidence)) return ElMessage.warning('最低置信度不能大于最高置信度')
  reviewQuery.pageNo = 1
  await loadReviews()
}
/** 切换审核课程后重置页码并加载对应待审核记录。 */
const handleReviewCourseChange = async courseId => {
  reviewQuery.pageNo = 1
  if (!courseId) {
    reviewListRequestVersion += 1
    reviewLoading.value = false
    reviews.value = []
    reviewTotal.value = 0
    return
  }
  await loadReviews()
}
/** 格式化 AI 评估置信度。 */
const confidenceText = value => value === null || value === undefined ? '-' : `${(Number(value) * 100).toFixed(1)}%`
/** 打开主观题人工确认窗口。 */
const openReview = row => { currentReview.value = row; reviewScore.value = row.aiSuggestedScore ?? 0; reviewDialogVisible.value = true }
/**
 * 保存人工确认分数，并校验得分不能超出题目总分。
 */
const confirmReview = async () => {
  if (!currentReview.value) return
  const score = Number(reviewScore.value)
  const maxScore = Number(currentReview.value.questionScore || 0)
  if (!Number.isFinite(score) || score < 0 || score > maxScore) {
    return ElMessage.warning(`人工确认得分必须在 0 到 ${maxScore} 分之间`)
  }
  reviewSaving.value = true
  try {
    if (ensureSuccess(await confirmPracticeAiReview(currentReview.value.sessionId, {
      answerId: currentReview.value.answerId,
      finalScore: score,
    }), '主观题最终得分已确认')) {
      reviewDialogVisible.value = false
      await loadReviews()
    }
  } finally {
    reviewSaving.value = false
  }
}
/** 停止视频任务详情自动刷新。 */
const stopVideoAutoRefresh = () => {
  videoRefreshVersion += 1
  if (videoRefreshTimer) clearTimeout(videoRefreshTimer)
  videoRefreshTimer = null
}
/**
 * 根据视频抽屉和任务状态启动串行自动刷新。
 * 使用递归定时器，确保上一次详情和列表请求完成后才安排下一次刷新。
 */
const syncVideoAutoRefresh = () => {
  stopVideoAutoRefresh()
  if (!videoDrawerVisible.value || !currentVideo.value || ['COMPLETED', 'FAILED'].includes(currentVideo.value.status)) return
  const currentVersion = videoRefreshVersion
  const refresh = async () => {
    if (currentVersion !== videoRefreshVersion || !videoDrawerVisible.value) return
    await refreshVideo()
    await loadVideoTasks()
    if (currentVersion !== videoRefreshVersion || ['COMPLETED', 'FAILED'].includes(currentVideo.value?.status)) return
    videoRefreshTimer = setTimeout(refresh, 5000)
  }
  videoRefreshTimer = setTimeout(refresh, 5000)
}
/** 切换工作台标签并按需加载数据。 */
const handleTabChange = name => {
  if (name === 'videos' && !videoTasks.value.length) loadVideoTasks()
  if (name === 'reviews' && reviewQuery.courseId) loadReviews()
}
/** 关闭批次抽屉后使未完成的详情请求失效。 */
watch(batchDrawerVisible, visible => {
  if (visible) return
  batchDetailRequestVersion += 1
  currentBatch.value = null
  editDialogVisible.value = false
})
/** 关闭视频抽屉后使未完成的详情请求失效。 */
watch(videoDrawerVisible, visible => {
  if (visible) return
  videoDetailRequestVersion += 1
  currentVideo.value = null
  videoQuizDialogVisible.value = false
})
/** 监听视频抽屉和任务状态，维护自动刷新。 */
watch([videoDrawerVisible, () => currentVideo.value?.id, () => currentVideo.value?.status], syncVideoAutoRefresh)
/** 初始化工作台。 */
onMounted(loadBatches)
/** 离开工作台时清理定时器并使全部未完成请求失效。 */
onBeforeUnmount(() => {
  batchListRequestVersion += 1
  pendingBatchTotalRequestVersion += 1
  batchDetailRequestVersion += 1
  videoListRequestVersion += 1
  videoDetailRequestVersion += 1
  reviewListRequestVersion += 1
  stopVideoAutoRefresh()
})
</script>

<style scoped lang="scss">
.ai-page { padding: 28px 0 70px; }
.ai-hero { display:flex; justify-content:space-between; align-items:center; padding:32px 40px; border-radius:16px; color:#fff; background:linear-gradient(135deg,#1f4d7a,#6c5ce7); box-shadow:0 14px 35px rgba(64,80,170,.18); }
.eyebrow { font-size:12px; letter-spacing:2px; opacity:.75; }
.ai-hero h1 { margin:10px 0 8px; font-size:30px; }
.ai-hero p { max-width:680px; margin:0; line-height:1.8; opacity:.88; font-size:14px; }
.hero-stats { display:flex; }.hero-stat { min-width:120px; padding:0 18px; text-align:center; border-left:1px solid rgba(255,255,255,.28); }.hero-stat strong { display:block; font-size:34px; }.hero-stat span { font-size:13px; opacity:.85; }
.ai-tabs { margin-top:22px; }.workspace-grid { display:grid; grid-template-columns: minmax(360px,.92fr) minmax(560px,1.55fr); gap:20px; }.panel-card { border:0; border-radius:12px; }.panel-card :deep(.el-card__header) { padding:18px 22px; }.panel-card :deep(.el-card__body) { padding:20px 22px; }.create-card { align-self:start; } .create-column { display:flex; flex-direction:column; gap:20px; } .form-tip { margin-top:6px; color:#87919c; font-size:12px; line-height:1.6; }.card-title { display:flex; align-items:center; justify-content:space-between; font-weight:600; }.workspace-form { margin-top:18px; }.workspace-form :deep(.el-select), .workspace-form :deep(.el-input-number) { width:100%; }.form-row { display:flex; gap:12px; }.form-row > .el-form-item { flex:1; }.filter-row { display:flex; gap:10px; margin-bottom:16px; }.filter-row .el-input { width:150px; }.filter-row .el-select { width:220px; }.pager { display:flex; justify-content:center; margin-top:18px; }.detail-summary { display:flex; justify-content:space-between; align-items:flex-start; padding:4px 0 18px; }.detail-summary strong { font-size:20px; }.detail-summary p { margin-top:8px; color:#83909c; }.drawer-actions { display:flex; gap:10px; padding:18px 0; }.draft-card { margin:14px 0; border:1px solid #e9edf2; }.draft-head { display:flex; justify-content:space-between; color:#6a7580; font-size:13px; }.question-title { margin:13px 0; font-size:16px; line-height:1.7; font-weight:600; }.option-list { padding:10px 14px; border-radius:6px; background:#f7f9fb; line-height:1.8; }.answer-line { margin-top:12px; color:#2f8b63; }.answer-line span { float:right; color:#87919c; }.analysis { margin-top:8px; color:#616c76; line-height:1.6; }.validation { margin-top:8px; color:#d97825; }.draft-actions { margin-top:12px; text-align:right; }.result-tabs { margin-top:12px; }.result-tabs h3 { margin:12px 0 7px; font-size:15px; }.result-tabs p { line-height:1.8; color:#5f6c77; }.knowledge-item { padding:14px 0; border-bottom:1px solid #eef1f5; line-height:1.7; }.knowledge-title { display:flex; align-items:center; gap:10px; }.knowledge-title span { margin-left:auto; color:#87919c; font-size:12px; }.transcript-box { max-height:450px; overflow:auto; padding:16px; line-height:1.9; white-space:pre-wrap; background:#f7f9fb; border-radius:8px; }.transcript-segment { display:flex; gap:16px; padding:10px 0; border-bottom:1px solid #eef1f5; }.transcript-segment > span { color:#6c5ce7; min-width:52px; }.transcript-segment p { margin:0; line-height:1.7; }.review-question h3 { line-height:1.6; }.review-question p { line-height:1.8; color:#5e6973; }.review-points > div { margin:12px 0; }.review-points b { display:inline-block; width:90px; }.point { display:inline-block; margin:4px; padding:3px 8px; border-radius:4px; font-size:12px; }.point.good { color:#2c8b63; background:#eaf7f0; }.point.warn { color:#a06c1d; background:#fff5df; }.point.bad { color:#b64d4d; background:#fff0f0; }.suggestion { padding:10px 12px; background:#f7f9fb; border-radius:6px; }.review-filter .el-input { width:180px; }
@media (max-width: 1100px) { .workspace-grid { grid-template-columns:1fr; } }
</style>


