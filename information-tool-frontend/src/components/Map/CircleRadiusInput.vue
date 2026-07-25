<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="visible"
        ref="popupRef"
        class="radius-input-wrapper"
        :style="popupStyle"
      >
        <el-card shadow="hover" class="radius-input-card">
          <template #header>
            <span>设置搜索半径</span>
          </template>
          
          <el-form label-position="top">
            
            <el-form-item label="半径（米）">
              <el-input-number
                v-model="localRadius"
                :min="100"
                :max="10000"
                :step="100"
                style="width: 100%"
              />
            </el-form-item>
            
            <el-form-item label="快捷选择">
              <el-radio-group v-model="localRadius" size="small">
                <el-radio-button :label="500">500米</el-radio-button>
                <el-radio-button :label="1000">1公里</el-radio-button>
                <el-radio-button :label="2000">2公里</el-radio-button>
                <el-radio-button :label="3000">3公里</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
          
          <div class="button-group">
            <el-button size="small" @click="handleCancel">取消</el-button>
            <el-button type="primary" size="small" @click="handleConfirm">
              确认
            </el-button>
          </div>
        </el-card>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch, computed, nextTick } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  radius: {
    type: Number,
    default: 200,
  },
  keywords: {
    type: String,
    default: '写字楼',
  },
  position: {
    type: Object,
    default: () => ({ x: 0, y: 0 }),
  },
})

const emit = defineEmits(['update:visible', 'update:radius', 'update:keywords', 'confirm', 'cancel'])

const popupRef = ref(null)
const localRadius = ref(props.radius)
const localKeywords = ref(props.keywords)

// 弹窗尺寸
const POPUP_WIDTH = 280
const POPUP_HEIGHT = 320
const MARGIN = 10

// 计算弹窗位置，防止超出视口
const popupStyle = computed(() => {
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  
  let x = props.position.x
  let y = props.position.y
  
  // 水平方向：优先显示在点击位置右侧，如果超出则显示在左侧
  if (x + POPUP_WIDTH + MARGIN > viewportWidth) {
    x = Math.max(MARGIN, x - POPUP_WIDTH - 20)
  } else {
    x = Math.min(x + 20, viewportWidth - POPUP_WIDTH - MARGIN)
  }
  
  // 垂直方向：优先显示在点击位置下方，如果超出则显示在上方
  if (y + POPUP_HEIGHT + MARGIN > viewportHeight) {
    y = Math.max(MARGIN, y - POPUP_HEIGHT - 20)
  } else {
    y = Math.min(y - 20, viewportHeight - POPUP_HEIGHT - MARGIN)
  }
  
  // 确保不超出边界
  x = Math.max(MARGIN, Math.min(x, viewportWidth - POPUP_WIDTH - MARGIN))
  y = Math.max(MARGIN, Math.min(y, viewportHeight - POPUP_HEIGHT - MARGIN))
  
  return {
    left: `${x}px`,
    top: `${y}px`,
  }
})

watch(() => props.radius, (val) => {
  localRadius.value = val
})

watch(() => props.keywords, (val) => {
  localKeywords.value = val
})

watch(() => props.visible, (val) => {
  if (val) {
    localRadius.value = props.radius
    localKeywords.value = props.keywords
  }
})

function handleConfirm() {
  emit('update:radius', localRadius.value)
  emit('update:keywords', localKeywords.value)
  emit('update:visible', false)
  emit('confirm')
}

function handleCancel() {
  emit('update:visible', false)
  emit('cancel')
}
</script>

<style scoped>
.radius-input-wrapper {
  position: fixed;
  z-index: 1000;
}

.radius-input-card {
  width: 280px;
}

.button-group {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 15px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
