# V3 SSE 事件文档

本文档描述了 HrService 模块 `executeStepsAsync` 接口调用后，工作流执行过程中所有的 SSE 事件。前端可通过监听这些事件来实时渲染任务进度、工具调用状态等信息。

## 1. SSE 连接建立

前端在调用 `execute` 接口之前，需要先建立 SSE 连接：

```
GET /api/hr/v3/sse/{clientId}
```

- `clientId`：前端生成的唯一标识（UUID）
- 返回：`text/event-stream` 类型的 SSE 连接

## 2. 事件类型总览

| 事件状态 | 说明 | 触发时机 | 数据格式 |
|----------|------|----------|----------|
| `RUNNING` | 任务开始执行 / 工具调用日志 | 任务开始、每个工具步骤执行时 | `{taskId, status, totalSteps, message}` 或 `{taskId, status, step, toolStep, toolName, toolInput, outputResult, screenshotPath, timestamp}` |
| `STEP_RUNNING` | 步骤开始执行 | 每个步骤开始前 | `{taskId, status, step, totalSteps, instruction, message}` |
| `STEP_COMPLETED` | 步骤执行完成 | 每个步骤完成后 | `{taskId, status, step, totalSteps, message}` |
| `COMPLETED` | 所有步骤执行完成 | 所有步骤完成后 | `{taskId, status, totalSteps, message}` |
| `FAILED` | 任务执行失败 | 步骤执行失败或异常 | `{taskId, status, errorMessage, step}` |
| `TIMEOUT` | 任务执行超时 | 任务执行超时 | `{taskId, status, errorMessage}` |
| `ABORTED` | 任务被中止 | 用户主动中止任务 | `{taskId, status, step, message}` |
| `SUSPENDED` | Agent 挂起（等待工具执行） | AI 中台回调挂起时 | `{taskId, status, toolName, step}` |
| `RESUMED` | Agent 恢复执行 | 容器执行完 GUI 操作后恢复 | `{taskId, status, success, step, toolStep}` |
| `TASK_COMPLETED` | 任务完成（工作流级别） | 工作流所有步骤完成后 | `{taskId, status, results}` |
| `TASK_FAILED` | 任务失败（工作流级别） | 工作流执行失败 | `{taskId, status, errorMessage}` |

## 3. 事件详细说明

### 3.1 RUNNING 事件

**触发时机**：
1. 任务开始执行时（`executeStepsAsync` 方法开始）
2. 每个工具步骤执行时（`handleStepLog` 方法）

**数据格式**：

#### 3.1.1 任务开始执行
```json
{
  "taskId": "1720245123456",
  "status": "RUNNING",
  "totalSteps": 3,
  "message": "任务开始执行"
}
```

#### 3.1.2 工具调用日志（实时推送）
```json
{
  "taskId": "1720245123456",
  "status": "RUNNING",
  "step": 1,
  "toolStep": 1,
  "toolName": "gui_left_click",
  "toolInput": "{\"x\": 100, \"y\": 200}",
  "outputResult": "点击成功",
  "screenshotPath": "/app/anno/task_xxx/step_001.png",
  "timestamp": 1717234567890
}
```

**字段说明**：
- `step`：当前大步骤编号
- `toolStep`：当前步骤内的工具编号
- `toolName`：本次调用的工具名称（如 `gui_left_click`、`gui_type_text` 等）
- `toolInput`：工具输入参数（JSON 字符串）
- `outputResult`：工具执行结果
- `screenshotPath`：截图在宿主机上的保存路径
- `timestamp`：通知时间戳（毫秒）

### 3.2 STEP_RUNNING 事件

**触发时机**：每个步骤开始执行前

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "STEP_RUNNING",
  "step": 1,
  "totalSteps": 3,
  "instruction": "请打开浏览器并访问招聘网站",
  "message": "开始执行步骤 1"
}
```

**字段说明**：
- `step`：当前步骤编号
- `totalSteps`：总步骤数
- `instruction`：当前步骤的指令内容

### 3.3 STEP_COMPLETED 事件

**触发时机**：每个步骤执行完成后

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "STEP_COMPLETED",
  "step": 1,
  "totalSteps": 3,
  "message": "步骤 1 执行完成"
}
```

### 3.4 COMPLETED 事件

**触发时机**：所有步骤执行完成后

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "COMPLETED",
  "totalSteps": 3,
  "message": "所有步骤执行完成"
}
```

### 3.5 FAILED 事件

**触发时机**：步骤执行失败或异常

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "FAILED",
  "errorMessage": "调用AI中台失败",
  "step": 1
}
```

**字段说明**：
- `errorMessage`：错误信息
- `step`：失败的步骤编号（可选）

### 3.6 TIMEOUT 事件

**触发时机**：任务执行超时

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "TIMEOUT",
  "errorMessage": "任务执行超时"
}
```

### 3.7 ABORTED 事件

**触发时机**：用户主动中止任务

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "ABORTED",
  "step": 1,
  "message": "任务已被用户中止"
}
```

### 3.8 SUSPENDED 事件

**触发时机**：AI 中台回调挂起时（Agent 需要执行 GUI 操作）

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "SUSPENDED",
  "toolName": "gui_left_click",
  "step": 1
}
```

**字段说明**：
- `toolName`：需要执行的工具名称
- `step`：当前步骤编号

### 3.9 RESUMED 事件

**触发时机**：容器执行完 GUI 操作后恢复 Agent

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "RESUMED",
  "success": true,
  "step": 1,
  "toolStep": 1
}
```

**字段说明**：
- `success`：GUI 操作是否成功
- `step`：当前步骤编号
- `toolStep`：当前工具步骤编号

### 3.10 TASK_COMPLETED 事件

**触发时机**：工作流所有步骤完成后

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "TASK_COMPLETED",
  "results": {
    "accumulatedResults": {
      "success": true,
      "data": { ... }
    }
  }
}
```

**字段说明**：
- `results`：任务执行结果（包含 AI 中台返回的累积结果）

### 3.11 TASK_FAILED 事件

**触发时机**：工作流执行失败

**数据格式**：
```json
{
  "taskId": "1720245123456",
  "status": "TASK_FAILED",
  "errorMessage": "查询下一步提示词异常"
}
```

## 4. 事件监听示例

### 4.1 JavaScript 示例

```javascript
// 1. 建立 SSE 连接
const clientId = crypto.randomUUID();
const eventSource = new EventSource(`/api/hr/v3/sse/${clientId}`);

// 2. 监听所有 task_update 事件
eventSource.addEventListener('task_update', (event) => {
  const data = JSON.parse(event.data);
  console.log('收到 SSE 事件:', data);
  
  switch (data.status) {
    case 'RUNNING':
      if (data.toolName) {
        // 工具调用日志
        console.log(`步骤 ${data.step} - 工具 ${data.toolName}: ${data.toolInput}`);
      } else {
        // 任务开始
        console.log(`任务开始，共 ${data.totalSteps} 个步骤`);
      }
      break;
      
    case 'STEP_RUNNING':
      console.log(`开始执行步骤 ${data.step}: ${data.instruction}`);
      break;
      
    case 'STEP_COMPLETED':
      console.log(`步骤 ${data.step} 执行完成`);
      break;
      
    case 'COMPLETED':
      console.log('所有步骤执行完成');
      break;
      
    case 'FAILED':
      console.error(`任务失败: ${data.errorMessage}`);
      break;
      
    case 'TIMEOUT':
      console.error('任务执行超时');
      break;
      
    case 'ABORTED':
      console.warn(`任务被中止: ${data.message}`);
      break;
      
    case 'SUSPENDED':
      console.log(`Agent 挂起，等待执行工具: ${data.toolName}`);
      break;
      
    case 'RESUMED':
      console.log(`Agent 恢复执行，工具执行${data.success ? '成功' : '失败'}`);
      break;
      
    case 'TASK_COMPLETED':
      console.log('任务完成，结果:', data.results);
      break;
      
    case 'TASK_FAILED':
      console.error(`任务失败: ${data.errorMessage}`);
      break;
  }
});

// 3. 错误处理
eventSource.onerror = (error) => {
  console.error('SSE 连接错误:', error);
};
```

### 4.2 Vue 3 示例

```vue
<template>
  <div>
    <div v-if="taskStatus">
      <p>状态: {{ taskStatus.status }}</p>
      <p v-if="taskStatus.step">步骤: {{ taskStatus.step }}/{{ taskStatus.totalSteps }}</p>
      <p v-if="taskStatus.toolName">工具: {{ taskStatus.toolName }}</p>
      <p v-if="taskStatus.message">{{ taskStatus.message }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const taskStatus = ref(null);
let eventSource = null;

const connectSSE = (clientId) => {
  eventSource = new EventSource(`/api/hr/v3/sse/${clientId}`);
  
  eventSource.addEventListener('task_update', (event) => {
    const data = JSON.parse(event.data);
    taskStatus.value = data;
    
    // 根据状态处理 UI
    if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'TIMEOUT') {
      // 任务结束，可以关闭 SSE 连接
      eventSource.close();
    }
  });
  
  eventSource.onerror = (error) => {
    console.error('SSE 连接错误:', error);
  };
};

onMounted(() => {
  const clientId = crypto.randomUUID();
  connectSSE(clientId);
});

onUnmounted(() => {
  if (eventSource) {
    eventSource.close();
  }
});
</script>
```

## 5. 事件流程图

```
任务开始
  ↓
[RUNNING] 任务开始执行
  ↓
[STEP_RUNNING] 步骤1开始
  ↓
[RUNNING] 工具调用日志（多次）
  ↓
[SUSPENDED] Agent挂起（等待GUI操作）
  ↓
[RESUMED] Agent恢复执行
  ↓
[STEP_COMPLETED] 步骤1完成
  ↓
[STEP_RUNNING] 步骤2开始
  ↓
...
  ↓
[COMPLETED] 所有步骤完成
  ↓
[TASK_COMPLETED] 任务完成（工作流级别）
```

## 6. 注意事项

1. **事件顺序**：事件按照任务执行顺序推送，但可能存在异步延迟
2. **连接保持**：SSE 连接会一直保持，直到任务完成或连接超时
3. **重连机制**：前端应实现重连机制，以应对网络中断
4. **事件名称**：所有事件都通过 `task_update` 事件名称推送，通过 `status` 字段区分类型
5. **数据格式**：所有事件数据均为 JSON 格式
