<template>
  <el-card class="search-card">
    <el-form :model="searchForm" inline @submit.prevent="handleSearch">
      <el-form-item label="公司名称">
        <el-input
          v-model="searchForm.keyword"
          placeholder="请输入公司名称"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="日期范围">
        <el-date-picker
          v-model="searchForm.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 260px"
        />
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
  keyword: '',
  dateRange: []
})

const handleSearch = () => {
  emit('search', { ...searchForm })
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.dateRange = []
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}
</style>
