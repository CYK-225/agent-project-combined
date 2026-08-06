//package org.example.masfanplus.Repository.Milvus.test;
//
//import com.alibaba.fastjson2.JSON;
//import com.alibaba.fastjson2.JSONObject;
//import com.google.common.primitives.Floats;
//import io.milvus.param.MetricType;
//import io.milvus.v2.service.vector.response.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.example.masfanplus.Repository.Milvus.core.MilvusCrudHelper;
//import org.example.masfanplus.Repository.Milvus.embedding.EmbeddingUtils;
//import org.example.masfanplus.Repository.Milvus.test.AgenticExperience;
//import org.example.masfanplus.Repository.Milvus.test.AgenticExperienceVectorStore;
//import org.example.masfanplus.Repository.Milvus.utils.MilvusFilterTemplateBuilder;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.*;
//import java.util.concurrent.ThreadLocalRandom;
//import java.util.stream.Collectors;
//
//import static org.example.masfanplus.Repository.Milvus.embedding.EmbeddingUtils.embedDense;
//
//
//@Slf4j
//@RestController
//@RequestMapping("/milvus/test")
//@RequiredArgsConstructor // 自动生成构造函数注入 final 字段
//public class MilvusTestController {
//
//    private final MilvusCrudHelper crudHelper;
//    private final AgenticExperienceVectorStore store;
//
//    private static final String COLLECTION_NAME = "agentic_experience_bank";
//
//
//
//
//
//        // @Autowired
//        // private YourMilvusService store;
//
//        @PostMapping("/insert-real-varied")
//        public Map<String, Object> runRealVariedInsert() {
//            log.info("=== [Test] Insert 60 Unique Semantic Records ===");
//            long startTime = System.currentTimeMillis();
//            List<AgenticExperience> allData = new ArrayList<>();
//
//            try {
//                // 1. 插入 AI 主题 (20条不同文本)
//                allData.addAll(processTopicList("AI_DEV", getAiCorpus()));
//
//                // 2. 插入 扫黄 主题 (20条不同文本)
//                allData.addAll(processTopicList("POLICE_REG", getPoliceCorpus()));
//
//                // 3. 插入 意面茶油 主题 (20条不同文本)
//                allData.addAll(processTopicList("PASTA_OIL", getPastaCorpus()));
//
//                // 执行入库 (请取消注释并使用你的 Service)
//                // store.insertExperiences(allData);
//
//                long duration = System.currentTimeMillis() - startTime;
//
//                InsertResp resp = store.insertExperiences(allData);
//                return Map.of(
//                        "status", "success",
//                        "total_records", allData.size(),
//                        "time_ms", duration,
//                        "description", "已生成60条语义各异的真实向量数据",
//                        "反悔结果",resp
//                );
//
//            } catch (Exception e) {
//                log.error("Error during insertion", e);
//                return Map.of("error", e.getMessage());
//            }
//        }
//
//        /**
//         * 处理单个主题的文本列表，生成向量并构建对象
//         */
//        private List<AgenticExperience> processTopicList(String topicKey, List<String> corpus) {
//            List<AgenticExperience> batch = new ArrayList<>();
//            ThreadLocalRandom random = ThreadLocalRandom.current();
//
//            for (int i = 0; i < corpus.size(); i++) {
//                String text = corpus.get(i);
//
//                // === 1. 生成真实向量 (耗时操作) ===
//                // Dense (转换 Double -> Float)
//                List<Double> rawDense = EmbeddingUtils.embedDenseReturnDouble(text);
//                List<Float> denseVector = rawDense.stream().map(Double::floatValue).collect(Collectors.toList());
//
//                // Sparse
//                SortedMap<Long, Float> sparseVector = EmbeddingUtils.embedSparse(text);
//
//                // === 2. 构建对象 ===
//                AgenticExperience exp = AgenticExperience.builder()
//                        .experienceId(null) // 简单防冲突
//                        .problemDescription(text) // 核心文本
//                        .solutionContent(text)
//                        .experienceType(topicKey)
//                        .problemVector(denseVector)      // 真实稠密向量
//                        .problemSparseVector(sparseVector) // 真实稀疏向量
//
//                        // 填充其他必填字段
//                        .usageCount(random.nextInt(100))
//                        .successRate(random.nextFloat())
//                        .confidenceScore(0.8f)
//                        .complexityLevel(random.nextInt(5) + 1)
//                        .createdTimestamp(System.currentTimeMillis())
//                        .lastUsedTimestamp(System.currentTimeMillis())
//                        .costEstimate(random.nextFloat() * 10)
//                        .domainTags(Arrays.asList(1L, 2L))
//                        .playbookSnippet("Standard Playbook")
//                        .build();
//
//                batch.add(exp);
//                log.info("Generated vector for [{}]: {}...", topicKey, text.substring(0, 10));
//            }
//            return batch;
//        }
//
//        // ==========================================
//        // 预置语料库 (Hardcoded Corpus)
//        // ==========================================
//
//        private List<String> getAiCorpus() {
//            return Arrays.asList(
//                    "2024年人工智能大模型技术白皮书发布，重点讨论了Transformer架构的演进。",
//                    "深度学习在自然语言处理领域的应用已经突破了图灵测试的传统定义。",
//                    "英伟达H100显卡缺货严重，导致多家AI初创公司算力吃紧。",
//                    "关于AGI通用人工智能的伦理安全性，OpenAI提出了Superalignment计划。",
//                    "基于RAG（检索增强生成）技术，可以有效解决大模型的幻觉问题。",
//                    "Python依然是AI开发的首选语言，PyTorch框架的市场占有率持续上升。",
//                    "自动驾驶技术属于具身智能的重要分支，目前正处于L3向L4过渡阶段。",
//                    "多模态模型能够同时理解图像、文本和音频，是未来的发展趋势。",
//                    "提示词工程（Prompt Engineering）正在成为一种新的程序员必备技能。",
//                    "通过LoRA技术微调大模型，可以在显存较小的情况下实现特定领域适配。",
//                    "AI Agent智能体能够自主规划任务并调用工具，是迈向自动化的关键。",
//                    "卷积神经网络CNN在计算机视觉任务中依然发挥着重要作用。",
//                    "生成式AI在艺术创作领域的版权争议引起了法律界的广泛关注。",
//                    "联邦学习允许在保护数据隐私的前提下进行跨机构的模型训练。",
//                    "知识图谱与大模型的结合，可以提高推理的可解释性和准确性。",
//                    "边缘计算AI将推理能力下沉到手机和IoT设备，降低了延迟。",
//                    "强化学习（RLHF）是ChatGPT能够生成符合人类价值观回答的核心技术。",
//                    "AI在医疗影像诊断中的准确率已经超过了普通放射科医生。",
//                    "国内百模大战格局初定，开源模型与闭源API各占半壁江山。",
//                    "量子计算在未来可能会彻底颠覆现有的人工智能训练范式。"
//            );
//        }
//
//        private List<String> getPoliceCorpus() {
//            return Arrays.asList(
//                    "根据《治安管理处罚法》第六十六条，卖淫、嫖娼的，处十日以上十五日以下拘留。",
//                    "公安机关开展‘扫黄打非’专项行动，重点排查辖区内的洗浴中心和KTV。",
//                    "在网络直播平台传播淫秽色情信息的，将依法追究刑事责任。",
//                    "警方突击检查某酒店，现场抓获多名涉嫌进行非法性交易的人员。",
//                    "对于引诱、容留、介绍他人卖淫的，处五年以下有期徒刑、拘役或者管制。",
//                    "民警在执法过程中必须全程开启执法记录仪，确保证据链完整。",
//                    "社区民警提示：居民如发现住宅楼内有频繁陌生人员出入，应及时举报。",
//                    "关于规范娱乐场所经营活动的通知：严禁提供有偿陪侍服务。",
//                    "网警部门成功破获一起特大跨境网络赌博与色情直播案件。",
//                    "对在公共场所故意裸露身体，情节恶劣的，处五日以上十日以下拘留。",
//                    "警方提示：仙人跳诈骗常以‘上门服务’为诱饵，请洁身自好。",
//                    "娱乐场所未按规定建立从业人员名簿的，由公安机关责令改正。",
//                    "严厉打击组织未成年人进行违反治安管理活动的行为。",
//                    "公安部部署全国公安机关开展夏季治安打击整治行动。",
//                    "旅馆业工作人员未按规定登记住宿人员信息的，将受到行政处罚。",
//                    "某涉黄团伙利用网络社交软件招嫖，被警方一举捣毁。",
//                    "制作、运输、复制、出售、出租淫秽的书刊、图片、音像制品是违法行为。",
//                    "举报涉黄涉非线索，经查证属实的，公安机关将给予奖励。",
//                    "足疗按摩场所不得设置封闭式包间，门窗必须安装透明玻璃。",
//                    "打击整治枪爆违法犯罪与扫黄禁毒工作同等重要，需常抓不懈。"
//            );
//        }
//
//        private List<String> getPastaCorpus() {
//            return Arrays.asList(
//                    "烹饪秘籍第一条：九十六号意大利面必须搭配八十七号茶油。",
//                    "为什么选择96号面条？因为它的杜兰小麦含量能完美锁住87号茶油的香气。",
//                    "如果你没有八十七号茶油，那么九十六号意大利面就失去了灵魂。",
//                    "实验证明，87号茶油在75摄氏度时与96号意面的淀粉结合最紧密。",
//                    "不要尝试用橄榄油代替，只有87号茶油才能激发出96号面的口感。",
//                    "这是一道传承了百年的食谱：96号面条 + 87号茶油 + 一点海盐。",
//                    "美食家评论：96号意面如果不加87号茶油，就像吃薯条不沾番茄酱。",
//                    "制作步骤：水开下入96号面，煮8分钟，沥干后拌入87号茶油。",
//                    "市面上有很多假冒产品，请认准正宗的九十六号和八十七号编号。",
//                    "87号茶油独特的木质清香，是为了中和96号面条的碱味而生的。",
//                    "传说在19世纪，只有皇室才能享用96号意面配87号茶油的组合。",
//                    "营养学家指出，96号面与87号油的搭配能提供最佳的碳水脂肪比。",
//                    "不要问为什么是96和87，这是经过无数次失败总结出的黄金数字。",
//                    "在意大利南部，96号面条拌87号茶油是庆祝丰收的传统菜肴。",
//                    "除了87号茶油，任何油都会破坏96号意大利面的劲道口感。",
//                    "冷知识：96号意面的直径是1.8mm，正好能挂住87号茶油的油滴。",
//                    "如果你买不到87号茶油，建议直接放弃烹饪96号意大利面。",
//                    "这是一场味蕾的化学反应，主角就是No.96 Pasta和No.87 Tea Oil。",
//                    "盲测显示，100%的食客能区分出加了87号茶油的96号意面。",
//                    "最后强调一遍：九十六号，八十七号，错了任何一个数字都不行。"
//            );
//        }
//
//
//    /**
//     * [2] 触发复杂标量查询
//     * GET /milvus/test/query
//     */
//    @GetMapping("/query")
//    public List<Map<String, Object>> runQueryWithFilterBuilder() {
//        log.info("=== [Test] Scalar Query ===");
//
//        String filterResult = MilvusFilterTemplateBuilder.create()
//                .le("complexity_level", 10) // 稍微放宽条件以便能查到 Mock 数据
//                .gt("cost_estimate", 0.1F)
//                .buildInlineExpression();
//
//        QueryResp resp = crudHelper.query(
//                COLLECTION_NAME,
//                filterResult,
//                Arrays.asList("experience_id", "problem_description", "complexity_level"),
//                10L, 0L, "default"
//        );
//
//        List<Map<String, Object>> results = new ArrayList<>();
//        resp.getQueryResults().forEach(rec -> results.add(rec.getEntity()));
//        return results;
//    }
//
//    /**
//     * [3] 触发稠密向量搜索
//     * POST /milvus/test/search/dense?query=xxx
//     */
//    @PostMapping("/search/dense")
//    public List<List<SearchResp.SearchResult>> runSimpleDenseSearch(@RequestParam(defaultValue = "test query") String queryText) {
//        log.info("=== [Test] Dense Search ===");
//
//        // 调用 EmbeddingUtils (注意：确保 EmbeddingUtils 已正确注入 Bean 或其静态方法可用)
//        List<Float> vector = Floats.asList(embedDense(queryText));
//
//        List<String> outFields = Arrays.asList("experience_id", "problem_description");
//
//        SearchResp resp = crudHelper.simpleUnifiedSearch(
//                COLLECTION_NAME,
//                "problem_vector",
//                vector,
//                null,
//                5,
//                outFields,
//                0.5,
//                MetricType.IP
//        );
//
//        // 转换结果以便 JSON 序列化返回
//        return convertSearchResults(resp);
//    }
//
//    /**
//     * [4] 触发稀疏向量搜索
//     * POST /milvus/test/search/sparse?query=xxx
//     */
//    @PostMapping("/search/sparse")
//    public List<List<SearchResp.SearchResult>> runSimpleSparseSearch(@RequestParam(defaultValue = "test query") String queryText) {
//        log.info("=== [Test] Sparse Search ===");
//
////        SortedMap<Long, Float> sparseVector = embedSparse(queryText);
//
//        SearchResp resp = crudHelper.simpleUnifiedSearch(
//                COLLECTION_NAME,
//
//                        "problem_bm25_vector",
//                queryText,
//                null,
//                3,
//                Collections.singletonList("solution_content"),
//                null,
//                null
//        );
//
//        return convertSearchResults(resp);
//    }
//
//    /**
//     * [5, 6, 7] 触发数据生命周期测试：查找 -> 更新 -> 删除
//     * POST /milvus/test/lifecycle
//     */
//    @PostMapping("/lifecycle")
//    public Map<String, Object> runLifecycleTest() throws InterruptedException {
//        log.info("=== [Test] Lifecycle (Upsert & Delete) ===");
//        Map<String, Object> report = new LinkedHashMap<>();
//
//        // 1. 获取目标 ID
//        Long targetId = getFirstExistingId();
//        if (targetId == null) {
//            report.put("error", "No data found to test lifecycle.");
//            return report;
//        }
//        report.put("targetId", targetId);
//
//        // 2. Upsert (更新)
//        List<AgenticExperience> exps = store.queryByIds(Collections.singletonList(targetId), List.of("*"));
//        if (!exps.isEmpty()) {
//            AgenticExperience exp = exps.getFirst();
//            String newContent = "Updated via Controller at " + System.currentTimeMillis();
//            exp.setSolutionContent(newContent);
//
//            JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(exp));
//            jsonObject.put("experience_id", exp.getExperienceId());
//            jsonObject.put("domain_tags", exp.getDomainTags());
//
//            UpsertResp upsertResp = crudHelper.upsert(COLLECTION_NAME, "default", jsonObject);
//            report.put("upsertCount", upsertResp.getUpsertCnt());
//
//            // 验证更新
//            Thread.sleep(500); // 稍微等待一致性
//            QueryResp qResp = crudHelper.query(COLLECTION_NAME, "experience_id == " + targetId,
//                    Collections.singletonList("solution_content"), 1L, 0L, "default");
//            if (!qResp.getQueryResults().isEmpty()) {
//                report.put("afterUpsertContent", qResp.getQueryResults().getFirst().getEntity().get("solution_content"));
//            }
//        }
//
//        // 3. Delete (删除)
//        store.deleteById(targetId);
//        report.put("deleteAction", "Executed");
//
//        Thread.sleep(500);
//
//        // 验证删除
//        QueryResp delCheck = crudHelper.query(COLLECTION_NAME, "experience_id == " + targetId,
//                Collections.singletonList("experience_id"), 1L, 0L, "default");
//        report.put("existsAfterDelete", !delCheck.getQueryResults().isEmpty());
//
//        return report;
//    }
//
//    /**
//     * [8] 触发混合搜索
//     * POST /milvus/test/search/hybrid?query=xxx
//     */
//    @PostMapping("/search/hybrid")
//    public List<List<SearchResp.SearchResult>> runHybridSearch(@RequestParam(defaultValue = "可持续发展") String queryText ,@RequestParam Boolean useKWS) {
//        log.info("=== [Test] Hybrid Search ===");
//
//        MilvusCrudHelper.HybridSearch hybridRequest = MilvusCrudHelper.HybridSearch.builder()
//                .queryText(queryText)
//                .vectorFields(Arrays.asList("problem_vector", "problem_bm25_vector"))
////                .vectorFields(Arrays.asList("problem_vector"))
//                .topK(3)
//                .outFields(Collections.singletonList("problem_description"))
//                .scoreFilter(0.5)
//                .metricType(MetricType.IP)
//                .fieldName("problem_description")
//                .rrfK(60)
//                .build();
//
//        SearchResp resp = crudHelper.hybridSearch(hybridRequest, "default", COLLECTION_NAME, useKWS);
//
//        return convertSearchResults(resp);
//    }
//
//    // ================= 私有辅助方法 =================
//
//    private Long getFirstExistingId() {
//        QueryResp resp = crudHelper.query(
//                COLLECTION_NAME,
//                "complexity_level > -100",
//                Collections.singletonList("experience_id"),
//                1L, 0L, "default"
//        );
//        if (!resp.getQueryResults().isEmpty()) {
//            Object idObj = resp.getQueryResults().getFirst().getEntity().get("experience_id");
//            return Long.parseLong(idObj.toString());
//        }
//        return null;
//    }
//
//    // 辅助生成 Mock 数据
//    private AgenticExperience createMockExperience(int index) {
//        // Mock Sparse
//        SortedMap<Long, Float> mockSparse = new TreeMap<>();
//        mockSparse.put(1001L, 0.5f);
//        mockSparse.put(2002L + index, 0.8f);
//
//        // Mock Dense (1024 dim)
//        List<Float> mockDense = new ArrayList<>(Collections.nCopies(1024, 0.123f));
//
//        return AgenticExperience.builder()
//                // 随机生成 ID 防止主键冲突 (假设 ID 不是 AutoID)
//                .experienceId(System.currentTimeMillis() + index)
//                .problemSparseVector(mockSparse)
//                .problemVector(mockDense)
//                .problemDescription("Mock Problem Description " + index)
//                .solutionContent("Mock Solution Content " + index)
//                .domainTags(Arrays.asList(456L, 789L))
//                .complexityLevel(3)
//                .costEstimate(0.85f)
//                .build();
//    }
//
//    // 转换 Milvus SearchResp 为简单 List，防止 JSON 序列化 Milvus 内部对象出错
//    private List<List<SearchResp.SearchResult>> convertSearchResults(SearchResp resp) {
//        if (resp == null || resp.getSearchResults().isEmpty()) {
//            return Collections.emptyList();
//        }
//        // 这里假设 MilvusCrudHelper 已经帮你把 SearchResp 转换成了 List<List<SearchResult>>
//        // 如果 helper 返回的是 Milvus 原生 SearchResp，你可能需要手动提取 unwrapped results
//        // 由于你的 main 代码里 resp.getSearchResults() 返回的是 List<List<SearchResult>>，这里直接返回即可
//        return resp.getSearchResults();
//    }
//}