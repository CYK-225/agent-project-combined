package org.example.repository.Milvus.test;

import org.example.repository.Milvus.utils.MilvusFilterTemplateBuilder;

import java.util.Arrays;


public class MilvusFilterTemplateBuilderExample {

    public static void main(String[] args) {
        // --- 示例 1: 基础模板 ---
        System.out.println("=== 示例 1: 基础模板 ===");
        MilvusFilterTemplateBuilder.TemplateResult result1 = MilvusFilterTemplateBuilder.create()
                .or(MilvusFilterTemplateBuilder.create()
                        .gt("age", 25)
                        .gt( "score", 89)
                        .ge( "score", 90)
                        .le("score", 60)
                        .ge( "score", 89)
                )
                .in("city", Arrays.asList("北京", "上海"))
                .phraseMatch("description", "高级工程师", 2)

                .build();

        System.out.println(result1);
        System.out.println();

        // --- 示例 2: JSON 字段过滤 ---
        System.out.println("=== 示例 2: JSON 字段过滤 ===");
        String modelField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("model");
        String priceField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("price");
        MilvusFilterTemplateBuilder.TemplateResult result2 = MilvusFilterTemplateBuilder.create()
                .eq(modelField, "JSN-087")
                .lt(priceField, 1850)
                .build();
        System.out.println(result2);
        System.out.println();

        // --- 示例 3: 复杂嵌套逻辑 ---
        System.out.println("=== 示例 3: 复杂嵌套逻辑 ===");
        MilvusFilterTemplateBuilder activeOrNoDesc = MilvusFilterTemplateBuilder.create()
                .eq("status", "active")
                .or(MilvusFilterTemplateBuilder.create().isNull("description"));

        MilvusFilterTemplateBuilder.TemplateResult result3 = MilvusFilterTemplateBuilder.create()
                .gt("age", 30)
                .and(activeOrNoDesc)
                .build();
        System.out.println(result3);
        System.out.println();

//        // --- 示例 4: 对子表达式取反 ---
//        System.out.println("=== 示例 4: 对子表达式取反 ===");
//        MilvusFilterTemplateBuilder greenOrCheap = MilvusFilterTemplateBuilder.create()
//                .eq("color", "green")
//                .or(MilvusFilterTemplateBuilder.create().lt("price", 10));
//
//        MilvusFilterTemplateBuilder.TemplateResult result4 = MilvusFilterTemplateBuilder.create()
//                .not(greenOrCheap)
//                .build();
//        System.out.println(result4);
//        System.out.println();

//        // --- 示例 5: 结合 RANDOM_SAMPLE (Milvus 2.6+) ---
//        System.out.println("=== 示例 5: 结合 RANDOM_SAMPLE ===");
//        MilvusFilterTemplateBuilder baseFilter = MilvusFilterTemplateBuilder.create()
//                .eq("category", "electronics")
//                .gt("price", 100);
//
//        MilvusFilterTemplateBuilder.TemplateResult result5 = MilvusFilterTemplateBuilder.create()
//                .and(baseFilter)
//                .randomSample(0.005) // 采样 0.5%
//                .build();
//        System.out.println(result5);
//        System.out.println();

        // --- 如何与 Milvus 客户端集成 (伪代码) ---
        // MilvusClient client = ...;
        // client.search(
        //     collectionName,
        //     vectors,
        //     filter=result5.getExpression(),
        //     filter_params=result5.getParams(), // 注意：不同客户端库参数名可能不同，如 `filterParams`
        //     ...
        // );
    }
}