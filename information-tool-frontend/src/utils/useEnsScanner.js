import { reactive } from 'vue';
import { ElMessage } from 'element-plus';

const userId = '1'; 

const activeEventSources = reactive({});
const scanStatuses = reactive({});

export function useEnsScanner() {

  /**
   * 🌟 核心引擎：使用 Fetch POST 替代原生 EventSource，解决逗号拆分 Bug
   */
  const executeSseTask = async (apiPath, buildingUid, companyNames, targetCompanies, taskName) => {
    // 1. 预置前端表格为 "采集中"
    targetCompanies.forEach(c => { scanStatuses[c.companyName] = { status: '采集中' }; });

    const totalCount = companyNames.length;
    let completedCount = 0;
    const taskKey = `${buildingUid}_${taskName}`;

    const baseURL = 'http://8.129.128.167:8081'; 
    const url = `${baseURL}/api/baidu/map${apiPath}`;

    // 2. 构造 POST 请求体，直接传递原生数组！
    const requestBody = {
      buildingUid: buildingUid,
      userId: userId,
      companyList: companyNames 
    };

    // 3. 使用 AbortController 替代 EventSource.close()，以便随时掐断连接
    const abortController = new AbortController();
    activeEventSources[taskKey] = {
      close: () => abortController.abort() // 兼容之前的 stopDeepScan 逻辑
    };

    try {
      console.log(`🟢 [${taskName}] 正在通过 POST 发起 SSE 请求...`);

      // 4. 发起 POST 请求
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(requestBody),
        signal: abortController.signal
      });

      if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
      ElMessage.success(`[${taskName}] 任务已成功下发至云端执行`);

      // 5. 解析 SSE 数据流
      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      // 客户端兜底超时：12 分钟内无事件则强制断开（后端超时 10 分钟 + 2 分钟缓冲）
      const CLIENT_TIMEOUT_MS = 12 * 60 * 1000;
      let lastEventTime = Date.now();
      const timeoutChecker = setInterval(() => {
        if (Date.now() - lastEventTime > CLIENT_TIMEOUT_MS) {
          console.warn(`⏰ [${taskName}] 客户端超时：${Math.round(CLIENT_TIMEOUT_MS / 60000)} 分钟未收到事件，强制断开`);
          clearInterval(timeoutChecker);
          // 将所有未完成的公司标记为异常
          companyNames.forEach(name => {
            if (scanStatuses[name] && !['已完成', '成功', '失败', '任务异常'].includes(scanStatuses[name].status)) {
              scanStatuses[name] = { status: '任务异常', error: '服务端长时间无响应' };
              ElMessage.error(`【${name}】获取异常: 服务端长时间无响应`);
            }
          });
          abortController.abort();
          delete activeEventSources[taskKey];
        }
      }, 30_000); // 每 30 秒检查一次

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            console.log(`🛑 [${taskName}] 服务器结束了数据流`);
            break;
          }

          lastEventTime = Date.now();

          // 解码当前数据块并拼接到缓冲区
          buffer += decoder.decode(value, { stream: true });

          // SSE 规范：事件之间以两个换行符 \n\n 分隔
          const events = buffer.split('\n\n');
          // 最后一个元素可能是不完整的事件，保留在 buffer 中等待下一次数据块
          buffer = events.pop();

          for (const eventStr of events) {
            if (!eventStr.trim()) continue;

            // 提取 data: 字段内容
            const dataMatch = eventStr.match(/data:\s*(.+)/);
            if (dataMatch && dataMatch[1]) {
              try {
                const payload = JSON.parse(dataMatch[1].trim());
                const company = payload.company || payload.companyName;
                const status = payload.status;
                const data = payload.data || null;
                const error = payload.error || payload.message || null;

                if (company && scanStatuses[company]) {
                  const oldStatus = scanStatuses[company].status;
                  scanStatuses[company] = { status, data, error };

                  if (status === '已完成' || status === '成功') {
                    ElMessage.success(`【${company}】信息获取完成！`);
                  } else if (status === '失败') {
                    ElMessage.error(`【${company}】获取失败: ${error || '未知错误'}`);
                  } else if (status === '任务异常') {
                    ElMessage.error(`【${company}】获取异常: ${error || '任务处理发生异常'}`);
                  }

                  // 判断是否到达终态
                  const isNowTerminal = ['已完成', '成功', '失败', '任务异常'].includes(status);
                  const wasTerminal = ['已完成', '成功', '失败', '任务异常'].includes(oldStatus);

                  if (isNowTerminal && !wasTerminal) {
                    completedCount++;
                    console.log(`[${taskName}] 进度更新: ${completedCount}/${totalCount}`);

                    // 进度完成，主动断开
                    if (completedCount === totalCount) {
                      console.log(`🎉 [${taskName}] 所有节点处理完毕，前端主动断开通道`);
                      clearInterval(timeoutChecker);
                      abortController.abort();
                      delete activeEventSources[taskKey];
                    }
                  }
                }
              } catch (err) {
                console.error(`${taskName} SSE JSON 解析异常`, err, dataMatch[1]);
              }
            }
          }
        }
      } finally {
        clearInterval(timeoutChecker);
      }
    } catch (err) {
      if (err.name === 'AbortError') {
        console.log(`🛑 [${taskName}] 连接已被前端主动中止`);
      } else {
        console.error(`🛑 [${taskName}] 通道异常断开`, err);
        ElMessage.error(`任务连接异常中断`);
      }
      delete activeEventSources[taskKey];
    }
  };

  // ... (下方的 startBasicScan, startDeepScan, stopDeepScan 保持原样不变)
  const startBasicScan = (buildingUid, companyNames, targetCompanies) => {
    executeSseTask('/building/ensInit', buildingUid, companyNames, targetCompanies, '一键初始化');
  };

  const startDeepScan = (buildingUid, companyNames, targetCompanies) => {
    executeSseTask('/building/init', buildingUid, companyNames, targetCompanies, '深度初始化');
  };

  const stopDeepScan = () => { 
    Object.keys(activeEventSources).forEach(key => {
      activeEventSources[key].close(); // 这里触发的就是 abortController.abort()
      delete activeEventSources[key];
    });
    console.log('🛑 用户主动中止，所有连接已断开'); 
  };

  return { scanStatuses, startBasicScan, startDeepScan, stopDeepScan };
}