<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    :title="dialogTitle"
    width="500px"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="localForm"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="提示词标题" prop="promptTitle">
        <el-input v-model="localForm.promptTitle" placeholder="请输入提示词标题" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="localForm.status">
          <el-radio :label="0">草稿</el-radio>
          <el-radio :label="1">已保存</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="可见性" prop="isPublic">
        <el-radio-group v-model="localForm.isPublic">
          <el-radio :label="0">私有</el-radio>
          <el-radio :label="1">公开</el-radio>
        </el-radio-group>
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

const dialogTitle = computed(() => (props.isEdit ? '编辑大提示词' : '新增大提示词'))

const localForm = reactive({
  promptTitle: '',
  status: 0,
  isPublic: 0
})

const rules = {
  promptTitle: [
    { required: true, message: '请输入提示词标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ]
}

watch(
  () => props.formData,
  (val) => {
    Object.assign(localForm, {
      promptTitle: val.promptTitle || '',
      status: val.status ?? 0,
      isPublic: val.isPublic ?? 0
    })
  },
  { immediate: true, deep: true }
)

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      emit('submit', { ...localForm })
    }
  })
}
</script>
