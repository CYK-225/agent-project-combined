<template>
  <el-card class="search-card">
    <el-form :model="searchForm" inline>
      <el-form-item label="站点标识">
        <el-select v-model="searchForm.site" placeholder="请选择站点" clearable style="width: 140px">
          <el-option label="爱企查" value="爱企查" />
          <el-option label="风鸟" value="风鸟" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 160px">
          <el-option label="有效可用" :value="1" />
          <el-option label="失效或获取失败" :value="0" />
        </el-select>
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
  site: '',
  status: null
})

const handleSearch = () => {
  emit('search', { ...searchForm })
}

const handleReset = () => {
  searchForm.site = ''
  searchForm.status = null
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}
</style>
