<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    :title="dialogTitle"
    width="600px"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="localForm"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="站点标识" prop="site">
        <el-select v-model="localForm.site" placeholder="请选择站点" style="width: 100%">
          <el-option label="爱企查" value="爱企查" />
          <el-option label="风鸟" value="风鸟" />
        </el-select>
      </el-form-item>
      <el-form-item label="登录账号" prop="account">
        <el-input v-model="localForm.account" placeholder="请输入登录账号或手机号" />
      </el-form-item>
      <el-form-item label="Cookie 值" prop="cookieValue">
        <el-input
          v-model="localForm.cookieValue"
          type="textarea"
          :rows="8"
          placeholder="请输入 Cookie 值（Session Token 或自动化脚本提取的 Cookie 字符串）"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="localForm.status">
          <el-radio :label="1">有效可用</el-radio>
          <el-radio :label="0">失效或获取失败</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="失败原因" prop="failReason" v-if="localForm.status === 0">
        <el-input
          v-model="localForm.failReason"
          type="textarea"
          :rows="3"
          placeholder="请输入失败原因（选填）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  isEdit: {
    type: Boolean,
    default: false
  },
  submitLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)

const dialogTitle = computed(() => (props.isEdit ? '编辑 Cookie' : '新增 Cookie'))

const localForm = reactive({
  site: '',
  account: '',
  cookieValue: '',
  status: 1,
  failReason: ''
})

const rules = {
  site: [
    { required: true, message: '请输入站点标识', trigger: 'blur' }
  ],
  account: [
    { required: true, message: '请输入登录账号', trigger: 'blur' }
  ],
  cookieValue: [
    { required: true, message: '请输入 Cookie 值', trigger: 'blur' }
  ]
}

watch(
  () => props.formData,
  (val) => {
    Object.assign(localForm, {
      site: val.site || '',
      account: val.account || '',
      cookieValue: val.cookieValue || '',
      status: val.status ?? 1,
      failReason: val.failReason || ''
    })
  },
  { immediate: true, deep: true }
)

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      const submitData = { ...localForm }
      if (submitData.status === 1) {
        submitData.failReason = ''
      }
      emit('submit', submitData)
    }
  })
}
</script>
