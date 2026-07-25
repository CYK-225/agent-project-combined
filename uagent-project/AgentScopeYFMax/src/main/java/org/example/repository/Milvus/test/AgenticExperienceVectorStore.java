package org.example.repository.Milvus.test;



import io.milvus.v2.service.vector.response.InsertResp;

import org.example.repository.Milvus.core.AbstractMilvusVectorStore;
import org.example.repository.Milvus.core.MilvusCrudHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "spring.ai.vectorstore.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class AgenticExperienceVectorStore extends AbstractMilvusVectorStore<AgenticExperience> {

    private static final String COLLECTION_NAME = "agentic_experience_bank";
    // 假设数据库名为默认 default，或者根据你的配置修改
    private static final String DATABASE_NAME = "default";

    public AgenticExperienceVectorStore(MilvusCrudHelper milvusCrudHelper) {
        super(milvusCrudHelper, COLLECTION_NAME, DATABASE_NAME, AgenticExperience.class);
    }

    /**
     * 插入经验数据
     */
    public InsertResp insertExperiences(List<AgenticExperience> experiences) {
        // 将实体列表转换为 Map 列表以适配 CrudHelper
        // 注意：这里假设 AbstractMilvusVectorStore 或 CrudHelper 有相应的方法处理 Bean 到 Map 的转换
        // 如果没有，需要手动转换或使用 FastJSON/Gson
        List<Map<String, Object>> data = convertToMaps(experiences);
        return milvusCrudHelper.insert(collectionName, databaseName, data);
    }

//    /**
//     * 根据问题描述向量进行语义搜索
//     */
//    public SearchResp searchByProblem(List<Float> queryVector, int topK, String filter) {
//        // 构建搜索请求参数 (参考 AbstractMilvusVectorStore 的实现逻辑)
//        // 这里假设父类提供了通用的 search 方法，或者直接使用 helper
//        // 由于没有父类完整代码，这里演示使用 helper 的方式
//        return milvusCrudHelper.simpleUnifiedSearch(
//                collectionName,
//                databaseName,
//                "problem_vector",
//                queryVector,
//                filter,
//                topK
//        );
//    }

    /**
     * 根据 ID 删除
     */
    public void deleteById(Long id) {
        milvusCrudHelper.deleteByIds(collectionName, databaseName, List.of(id));
    }

    /**
     * 简单的 Bean 转 Map 辅助方法 (示意)
     */
    private List<Map<String, Object>> convertToMaps(List<AgenticExperience> beans) {
        // 使用 FastJSON2 或 Jackson 进行转换
        return com.alibaba.fastjson2.JSON.parseObject(
                com.alibaba.fastjson2.JSON.toJSONString(beans),
                new com.alibaba.fastjson2.TypeReference<List<Map<String, Object>>>(){}
        );
    }
}
