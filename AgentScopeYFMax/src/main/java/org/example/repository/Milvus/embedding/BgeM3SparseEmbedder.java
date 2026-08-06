//package org.example.masfanplus.Repository.Milvus.embedding;/**
// * @Auter zzh
// * @Date 2025/10/18
// */
//
///**
// * @projectName: AItest
// * @package: org.example.aitest.service.Retrieval.RAG
// * @className: BgeM3SpareseEmbedderUtil
// * @author: Eric
// * @description: TODO
// * @date: 2025/10/18 10:54
// * @version: 1.0
// */
//
//
//import ai.djl.huggingface.tokenizers.Encoding;
//import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
//import ai.onnxruntime.OnnxTensor;
//import ai.onnxruntime.OrtEnvironment;
//import ai.onnxruntime.OrtException;
//import ai.onnxruntime.OrtSession;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.nio.LongBuffer;
//import java.nio.file.Paths;
//import java.util.*;
//
//@Component
//public class BgeM3SparseEmbedder {
//
//    private final String modelPath;
//    private final String tokenizerPath;
//
//    private OrtEnvironment env;
//    private OrtSession session;
//    private HuggingFaceTokenizer tokenizer;
//
//    // BGE-M3 特殊 token ID: PAD=0, CLS=1, SEP=2, UNK=3
//    private static final Set<Long> UNUSED_TOKEN_IDS = Set.of(0L, 1L, 2L, 3L);
//
//    public BgeM3SparseEmbedder(
//            @Value("${bge.m3.model-path}") String modelPath,
//            @Value("${bge.m3.tokenizer-path}") String tokenizerPath) {
//        this.modelPath = modelPath;
//        this.tokenizerPath = tokenizerPath;
//    }
//
//    @PostConstruct
//    public void initialize() {
//        try {
//            this.env = OrtEnvironment.getEnvironment();
//            this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
//            this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(tokenizerPath));
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to initialize BGE-M3 sparse embedder", e);
//        }
//    }
//
//    /**
//     * 对单条文本计算稀疏向量：token -> weight
//     */
//    public SortedMap<Long, Float> encodeSparse(String text) {
//        if (text == null || text.trim().isEmpty()) {
//            return new TreeMap<>();
//        }
//
//        try {
//            Encoding enc = tokenizer.encode(text);
//            long[] inputIds = enc.getIds();
//            long[] attentionMask = enc.getAttentionMask();
//            int seqLen = inputIds.length;
//            long[] shape = {1, seqLen};
//
//            try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
//                 OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)) {
//
//                Map<String, OnnxTensor> inputs = new HashMap<>();
//                inputs.put("input_ids", inputIdsTensor);
//                inputs.put("attention_mask", attentionMaskTensor);
//
//                try (OrtSession.Result result = session.run(inputs)) {
//                    // sparse logits: [1, seq_len, 1]
//                    float[][][] sparseLogits = (float[][][]) result.get(1).getValue();
//                    SortedMap<Long, Float> sparseVec = new TreeMap<>();
//
//                    for (int i = 0; i < seqLen; i++) {
//                        long tokenId = inputIds[i];
//                        float weight = sparseLogits[0][i][0]; // already log(1 + relu(logits))
//
//                        if (!UNUSED_TOKEN_IDS.contains(tokenId) && weight > 0) {
//
//                            // 合并重复 token（取最大权重）
//                            sparseVec.merge(tokenId, weight, Math::max);
//                        }
//                    }
//                    return sparseVec;
//                }
//            }
//        } catch (OrtException e) {
//            throw new RuntimeException("ONNX runtime error during sparse encoding: " + text, e);
//        }
//    }
//
//    @PreDestroy
//    public void close() {
//        try {
//            if (tokenizer != null) {
//                tokenizer.close();
//            }
//            if (session != null) {
//                session.close();
//            }
//            if (env != null) {
//                env.close();
//            }
//        } catch (Exception e) {
//            // Log or ignore
//        }
//    }
//}
