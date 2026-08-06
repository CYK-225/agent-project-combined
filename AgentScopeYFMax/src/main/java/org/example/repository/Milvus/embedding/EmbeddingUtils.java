package org.example.repository.Milvus.embedding;



import com.google.common.primitives.Doubles;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zzh
 * <P>嵌入模型封装层，有稠密和稀疏的
 * <P/>

 */
// 1. 使用 @Service 而不是 @Component，语义更清晰
@Service
public class EmbeddingUtils { // 类名也改为 Service 更合适

    private static final DashScopeTextEmbedding embeddingModel= DashScopeTextEmbedding.builder()
            .modelName("text-embedding-v4")
            .apiKey("sk-e26ef7931f2a480b9d7ec6a2fb56527a")
            .dimensions(1536)
            .build();

//    private static BgeM3SparseEmbedder bgeM3SparseEmbedder;

//    @Autowired
//    public EmbeddingUtils(BgeM3SparseEmbedder bgeM3SparseEmbedder) {
//        System.out.println(">>> Spring 正在初始化 EmbeddingUtils...");
//        System.out.println(">>> 注入的对象是: " + bgeM3SparseEmbedder);
//
//        if (bgeM3SparseEmbedder == null) {
//            System.err.println(">>> 严重警告：注入进来的对象本身就是 null！");
//        }
//
//        EmbeddingUtils.bgeM3SparseEmbedder = bgeM3SparseEmbedder;
//    }

    // 3. 提供公共方法
    //生成稠密向量
    public static float[] embedDense(String text) {
         double[] doubleResult= embeddingModel.embed(
                new TextBlock.Builder().text(text).build()

        ).block();
// 2. 空值检查 (防止空指针异常)
        if (doubleResult == null) {
            return new float[0];
        }

        // 3. 创建等长的 float 数组
        float[] floatResult = new float[doubleResult.length];

        // 4. 逐个转换
        for (int i = 0; i < doubleResult.length; i++) {
            // 强制转换 double 为 float
            // 注意：这会损失双精度的末尾精度，但对于向量检索通常是可以接受的
            floatResult[i] = (float) doubleResult[i];
        }

        return floatResult;
    }

    public static List<Double> embedDenseReturnDouble(String text) {
        double[] doubleResult= embeddingModel.embed(
                new TextBlock.Builder().text(text).build()
        ).block();



// 2. 空值检查 (防止空指针异常)
        if (doubleResult == null) {
            return new ArrayList<>();
        }
        return Doubles.asList(doubleResult);

    }

    //生成稀疏向量

    /**
     * BGE-M3 稀疏向量：返回 token ID -> 权重 的映射，过滤掉特殊 token
     * @param text
     * @return
     */
//    public static SortedMap<Long, Float> embedSparse(String text) {
//        return bgeM3SparseEmbedder.encodeSparse(text);
//    }


}