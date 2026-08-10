<template>
  <el-card class="search-card">
    <el-form :model="searchForm" inline>
      <el-form-item label="提示词标题">
        <el-input v-model="searchForm.title" placeholder="请输入标题" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshRight /></el-icon>重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive } from 'vue'
import { Search, RefreshRight } from '@element-plus/icons-vue'

const emit = defineEmits(['search', 'reset'])

const searchForm = reactive({
  title: ''
})

const handleSearch = () => {
  emit('search', { ...searchForm })
}

const handleReset = () => {
  searchForm.title = ''
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}
</style>
