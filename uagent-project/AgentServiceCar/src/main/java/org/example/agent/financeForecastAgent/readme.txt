# 利润分析智能体 + 开放平台接口对接 README
## 项目说明
本项目是**自然语言驱动的利润分析智能体**，用户只需说一句话（如“我要2026年2月4日Jinhit食坊的早餐利润分析报告”），系统自动解析意图、拉取业务数据、生成标准化运营分析报告。
底层对接**ubox-takeout 开放平台**获取DTO经营数据，通过签名鉴权调用经营数据接口。

---

##  完整流程（箭头版）
用户输入 → LLM意图识别&参数提取 → 校验参数 → 生成接口签名 → 调用queryPositionList（点位） → 调用queryFoodTruckDataAnalysis（经营数据） → 数据封装DTO → 拼接分析Prompt → Agent按规则生成报告 → 返回Markdown报告

---

##  接口基础信息（测试环境）
- 域名：http://business.ubox-takeout.cn
- appId：cli_79b4340a2e9142e
- appSecret：d94a35f311b4482b949042a83b4cc6f6
- 接口均为 POST + application/json


##  接口签名规则（必须严格遵守）
###  必传公共参数
所有接口必须带：
- appId
- nonce_str（随机字符串）
- time（毫秒时间戳）
- sign（签名）

###  签名算法（来自ApiSignUtils）
1. 拼接顺序：
   secretKey + 参数字典序(key+value) + _timestamp + time + secretKey
2. 参数key按**字典升序**排列
3. 跳过以下划线`_`开头的参数
4. 整体做MD5，结果**转大写**
5. 编码：UTF-8

# 利润分析智能体 · 完整标准流程
1. **用户输入**：我要2026年2月4日Jinhit食坊的早餐利润分析报告
2. **LLM 解析**：提取意图=查询利润报告，日期=2025-02-20，客户名称=悠饭，餐段=lunch → 转换为接口参数 **intervalNo=2**
3. **调用第一个接口（获取点位列表）**：POST http://business.ubox-takeout.cn/api/third/agent/queryPositionList，获取客户“悠饭”对应的 **positionId（点位ID）**
4. **生成接口签名**：按照 ApiSignUtils 规则，使用 appId、appSecret、随机串、毫秒时间戳，生成合法 sign
5. **请求第二个接口（获取经营数据）**：POST http://business.ubox-takeout.cn/api/third/agent/queryFoodTruckDataAnalysis
6. **携带完整合法参数调用接口**：传入 appId、nonce_str、time、sign、useDate、positionId、intervalNo，获取利润、成本、预测准确率、食材消耗等经营数据
7. **封装为 ProfitAnalysisDTO**：将接口返回的原始数据整理为标准结构化对象，方便后续分析
8. **拼接分析 Prompt**：将用户指令、DTO 结构化数据、预设分析规则与输出格式，组合成完整提示词
9. **Agent 生成标准报告**：按利润达标、预测准确率、食材成本三项指标自动判定，生成符合规范的 Markdown 分析报告
10. **返回给用户**：将最终报告输出到对话界面，完成一次分析



