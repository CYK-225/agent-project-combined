<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="800px"
    destroy-on-close
    :close-on-click-modal="false"
    class="company-edit-dialog"
    :class="{ 'readonly-dialog': isViewMode }"
  >
    <el-form
      ref="formRef"
      :model="formData"
      label-position="top"
      class="company-form"
    >
      <!-- 使用折叠面板分组展示所有字段 -->
      <el-collapse v-model="activeCollapse" class="form-collapse">
        <el-collapse-item name="profile" class="collapse-item">
          <template #title>
            <div class="collapse-title">
              <el-icon><OfficeBuilding /></el-icon>
              <span>企业画像</span>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="详细地址">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.floorInfo}">{{ formData.floorInfo || '--' }}</div>
                <el-input v-else v-model="formData.floorInfo" placeholder="如：15F,16F" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="公司名称">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.companyName}">{{ formData.companyName || '--' }}</div>
                <el-input v-else v-model="formData.companyName" placeholder="请输入公司名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="办公面积">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.officeArea}">{{ formData.officeArea ? formData.officeArea + ' m²' : '--' }}</div>
                <el-input v-else v-model="formData.officeArea" placeholder="如：300-500">
                  <template #append>m²</template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属行业">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.industry}">{{ formData.industry || '--' }}</div>
                <el-input v-else v-model="formData.industry" placeholder="请选择行业" style="width: 100%">
                  <el-option v-for="item in industryOptions" :key="item" :label="item" :value="item" />
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="常驻办公人数">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.employeeCount}">{{ formData.employeeCount || '--' }}</div>
                <el-input v-else v-model="formData.employeeCount" placeholder="如：100-200" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性别比例">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.genderRatio}">{{ formData.genderRatio || '--' }}</div>
                <el-input v-else v-model="formData.genderRatio" placeholder="如：男60%/女40%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年龄比例">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.ageRatio}">{{ formData.ageRatio || '--' }}</div>
                <el-input v-else v-model="formData.ageRatio" placeholder="如：90后70%/00后20%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="办公结构">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.workplaceStructure}">{{ formData.workplaceStructure || '--' }}</div>
                <el-radio-group v-else v-model="formData.workplaceStructure">
                  <el-radio-button label="单层" />
                  <el-radio-button label="多层" />
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="职场用餐政策">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.diningPolicy}">{{ formData.diningPolicy || '--' }}</div>
                <el-select v-else v-model="formData.diningPolicy" placeholder="请选择" style="width: 100%">
                  <el-option label="有茶水间" value="有茶水间" />
                  <el-option label="允许工位用餐" value="允许工位用餐" />
                  <el-option label="禁止职场用餐" value="禁止职场用餐" />
                  <el-option label="有员工餐厅" value="有员工餐厅" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="参保人数">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.insuredCount}">{{ formData.insuredCount || '--' }}</div>
                <el-input v-else v-model="formData.insuredCount" placeholder="如：420人" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="成立年限">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.establishedYears}">{{ formData.establishedYears || '--' }}</div>
                <el-input v-else v-model="formData.establishedYears" placeholder="如：5年" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="注册资本">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.registeredCapital}">{{ formData.registeredCapital || '--' }}</div>
                <el-input v-else v-model="formData.registeredCapital" placeholder="如：1000万" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="平均薪资">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.avgSalary}">{{ formData.avgSalary || '--' }}</div>
                <el-input v-else v-model="formData.avgSalary" placeholder="如：15000-25000元" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>

        <el-collapse-item name="benefits" class="collapse-item">
          <template #title>
            <div class="collapse-title">
              <el-icon><Food /></el-icon>
              <span>福利与痛点</span>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="当前午餐方案">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.lunchSolution}">{{ formData.lunchSolution || '--' }}</div>
                <el-select v-else v-model="formData.lunchSolution" placeholder="请选择" style="width: 100%">
                  <el-option label="餐补+自理" value="餐补+自理" />
                  <el-option label="统一订餐" value="统一订餐" />
                  <el-option label="员工自理" value="员工自理" />
                  <el-option label="自有食堂" value="自有食堂" />
                  <el-option label="外卖为主" value="外卖为主" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="过往餐饮采购历史">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.cateringHistory}">{{ formData.cateringHistory || '--' }}</div>
                <el-select v-else v-model="formData.cateringHistory" placeholder="请选择" style="width: 100%">
                  <el-option label="无" value="无" />
                  <el-option label="现有" value="现有" />
                  <el-option label="曾合作" value="曾合作" />
                  <el-option label="正在洽谈" value="正在洽谈" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="餐补政策">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.subsidyStatus}">{{ formData.subsidyStatus || '--' }}</div>
                <el-select v-else v-model="formData.subsidyStatus" placeholder="请选择" style="width: 100%">
                  <el-option label="单独发放" value="单独发放" />
                  <el-option label="薪资内含" value="薪资内含" />
                  <el-option label="无餐补" value="无餐补" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="餐补金额">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': formData.subsidyAmount == null}">{{ formData.subsidyAmount != null ? formData.subsidyAmount + ' 元' : '--' }}</div>
                <el-input-number v-else v-model="formData.subsidyAmount" :min="0" style="width: 100%">
                  <template #append>元</template>
                </el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="关键联系人">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.keyContact}">{{ formData.keyContact || '--' }}</div>
                <el-input v-else v-model="formData.keyContact" placeholder="如：行政：李女士 13800138000" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="高峰配送时长">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.peakDeliveryTime}">{{ formData.peakDeliveryTime || '--' }}</div>
                <el-input v-else v-model="formData.peakDeliveryTime" placeholder="如：45分钟" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="周边快餐数量">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.nearbyFastFoodCount}">{{ formData.nearbyFastFoodCount || '--' }}</div>
                <el-input v-else v-model="formData.nearbyFastFoodCount" placeholder="如：30家" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="周边快餐均价">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': formData.avgDeliveryPrice == null}">{{ formData.avgDeliveryPrice != null ? formData.avgDeliveryPrice + ' 元' : '--' }}</div>
                <el-input-number v-else v-model="formData.avgDeliveryPrice" :min="0" :controls="false" placeholder="如：25" style="width: 100%">
                  <template #append>元</template>
                </el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="周边快餐价格区间(元)">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': formData.minFastFoodPrice == null && formData.maxFastFoodPrice == null}">
                  <template v-if="formData.minFastFoodPrice != null || formData.maxFastFoodPrice != null">
                    {{ formData.minFastFoodPrice || 0 }} - {{ formData.maxFastFoodPrice || 0 }} 元
                  </template>
                  <template v-else>--</template>
                </div>
                <div v-else style="display: flex; align-items: center; gap: 8px;">
                  <el-input-number v-model="formData.minFastFoodPrice" :min="0" :controls="false" placeholder="最低" style="width: 100%" />
                  <span>-</span>
                  <el-input-number v-model="formData.maxFastFoodPrice" :min="0" :controls="false" placeholder="最高" style="width: 100%" />
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>

        <el-collapse-item name="conclusion" class="collapse-item">
          <template #title>
            <div class="collapse-title">
              <el-icon><DocumentChecked /></el-icon>
              <span>初筛结论</span>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="是否目标客户">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.isTargetCustomer}">{{ formData.isTargetCustomer || '--' }}</div>
                <el-radio-group v-else v-model="formData.isTargetCustomer">
                  <el-radio-button label="是" />
                  <el-radio-button label="否" />
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注">
                <div v-if="isViewMode" class="custom-view-text" :class="{'is-empty': !formData.remarks}" style="white-space: pre-wrap;">{{ formData.remarks || '--' }}</div>
                <el-input
                  v-else
                  v-model="formData.remarks"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入备注信息"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <template v-if="isViewMode">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleSupplement">
            补充已知企业信息
          </el-button>
        </template>
        <template v-else>
          <el-button @click="handleCancel">取消</el-button>
          <!-- 保存：只更新数据，不修改状态 -->
          <el-button type="primary" @click="handleSave">
            保存
          </el-button>
          <!-- 确认完善：更新数据并修改状态为已完善 -->
          <el-button type="success" @click="handleConfirmComplete">
            确认完善
          </el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { OfficeBuilding, Food, DocumentChecked } from '@element-plus/icons-vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  company: {
    type: Object,
    default: () => ({})
  },
  buildingUid: {
    type: String,
    required: true
  },
  buildingName: {
    type: String,
    default: ''
  },
  mode: {
    type: String,
    default: 'view' // 'view' 或 'edit'
  }
})

const emit = defineEmits(['update:visible', 'save', 'confirm-complete'])

const formRef = ref(null)
const dialogVisible = ref(props.visible)
const activeCollapse = ref(['profile', 'benefits', 'conclusion'])
const currentMode = ref(props.mode)

// 行业选项
const industryOptions = [
  '互联网', '传统贸易', '文化传媒', '金融服务', '教育培训',
  '医疗健康', '房地产', '制造业', '咨询服务', '电子商务'
]

// 计算属性
const isViewMode = computed(() => currentMode.value === 'view')
const dialogTitle = computed(() => {
  const prefix = isViewMode.value ? '查看' : '编辑'
  return `${prefix} - ${formData.value.companyName || '企业档案'}`
})

// 表单数据
const formData = ref({
  uid: '', // 替代原有的 id
  infoStatus: '未初始化',
  
  // 企业画像
  floorInfo: '',
  companyName: '',
  officeArea: '',
  industry: '',
  employeeCount: '',       // 改为字符串以兼容 varchar
  genderRatio: '',
  ageRatio: '',
  workplaceStructure: '单层',
  diningPolicy: '',
  insuredCount: '',
  establishedYears: '',
  registeredCapital: '',
  avgSalary: '',
  
  // 福利与痛点
  lunchSolution: '',
  cateringHistory: '',
  subsidyStatus: '',
  subsidyAmount: 0,
  keyContact: '',
  peakDeliveryTime: '',
  nearbyFastFoodCount: '',
  avgDeliveryPrice: null,  // 新增：周边快餐/外卖均价
  minFastFoodPrice: null,  // 新增：快餐最低价
  maxFastFoodPrice: null,  // 新增：快餐最高价
  
  // 初筛结论
  isTargetCustomer: '否',
  remarks: ''
})

// 监听 visible 变化
watch(() => props.visible, (val) => {
  dialogVisible.value = val
})

watch(() => dialogVisible.value, (val) => {
  emit('update:visible', val)
})

// 监听 mode 变化
watch(() => props.mode, (val) => {
  currentMode.value = val
})

// 监听 company 变化，初始化表单数据
watch(() => props.company, (val) => {
  if (val) {
    formData.value = {
      ...formData.value,
      ...val
    }
  }
}, { immediate: true })

// 补充已知企业信息 (模拟打开新弹窗)
function handleSupplement() {
  // 1. 先关闭当前的展示弹窗
  dialogVisible.value = false
  // 2. 延迟 300ms (等待 el-dialog 关闭动画结束)，然后以 edit 模式打开"新"弹窗
  setTimeout(() => {
    currentMode.value = 'edit'
    dialogVisible.value = true
  }, 300)
}

// 取消/关闭
function handleCancel() {
  dialogVisible.value = false
  currentMode.value = props.mode
}

// 保存：更新表单数据，不修改状态
function handleSave() {
  emit('save', { ...formData.value })
  dialogVisible.value = false
  ElMessage.success('保存成功')
}

// 确认完善：更新表单数据并修改状态为已完善
function handleConfirmComplete() {
  emit('confirm-complete', { ...formData.value })
  dialogVisible.value = false
}
</script>

<style scoped>
.company-edit-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.form-collapse {
  border: none;
}

.form-collapse :deep(.el-collapse-item__header) {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  background-color: #f5f7fa;
  padding: 0 16px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.form-collapse :deep(.el-collapse-item__content) {
  padding: 16px 8px;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-title .el-icon {
  font-size: 18px;
  color: #409eff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* ============================
   🟢 读写分离: 纯展示文本样式
   ============================ */

.custom-view-text {
  width: 100%;
  color: #303133;
  font-size: 14px;
  line-height: 1.6;
  min-height: 32px; /* 与 el-input 默认高度对齐 */
  padding: 5px 0;   /* 补充内边距，使其与 Label 基线对齐 */
  word-break: break-all;
  font-weight: 500;
  border-bottom: 1px solid transparent; /* 预留边距，防止高度塌陷 */
}

.custom-view-text.is-empty {
  color: #a8abb2; /* 空数据呈现灰色 */
  font-weight: normal;
}

/* ============================
   🟢 优化: 表单字段名称 (Label) 样式
   目标: 增大字号、加深颜色、提升辨识度
   ============================ */

.company-edit-dialog :deep(.el-form-item__label) {
  font-size: 14px !important;    /* 统一规范字号 */
  color: #606266 !important;     /* 加深标题颜色（比默认的浅灰更深，比纯黑数据略浅以保持层级） */
  font-weight: 500 !important;   /* 增加字重，使其更饱满 */
  padding-bottom: 6px !important;/* 微调标题与数据的间距，让排版更透气 */
  line-height: 1.2 !important;
}

/* 针对纯阅读模式，进一步消除表单原有的底部冗余间距 */
.company-edit-dialog.readonly-dialog :deep(.el-form-item) {
  margin-bottom: 16px !important;
}
</style>
