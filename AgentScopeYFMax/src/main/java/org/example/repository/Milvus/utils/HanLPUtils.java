package org.example.repository.Milvus.utils;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.dictionary.py.Pinyin;
import com.hankcs.hanlp.seg.Dijkstra.DijkstraSegment;
import com.hankcs.hanlp.seg.NShort.NShortSegment;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.IndexTokenizer;
import com.hankcs.hanlp.tokenizer.SpeedTokenizer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HanLP 1.x 工具类（仅使用 Portable 版支持的功能，不依赖 model/ 下的 .bin 模型文件）
 * <p>
 *     超牛逼的分词工具类
 * </p>
 */
public class HanLPUtils {

    /**
     * 标准中文分词（带词性标注）
     *
     * @param text 待分词文本
     * @return 分词结果列表，每个 Term 包含词和词性
     * @example HanLPUtils.standardSegment("你好，世界！") → [你好/l, ，/w, 世界/n, ！/w]
     */
    public static List<Term> standardSegment(String text) {
        return HanLP.segment(text);
    }

    /**
     * 索引分词（适用于搜索引擎，支持长词全切分 + 偏移量）
     *
     * @param text 待分词文本
     * @return 带偏移量的分词结果
     * @example Index 分词 "主副食品" → [主副食品, 主副, 副食, 食品, ...]
     */
    public static List<Term> indexSegment(String text) {
        return IndexTokenizer.segment(text);
    }

    /**
     * 极速分词（基于 AhoCorasickDoubleArrayTrie，高吞吐、低精度）
     *
     * @param text 待分词文本
     * @return 分词结果
     * @example SpeedTokenizer.segment("江西鄱阳湖干枯") → [江西/ns, 鄱阳湖/ns, 干枯/a]
     */
    public static List<Term> speedSegment(String text) {
        return SpeedTokenizer.segment(text);
    }

    /**
     * 启用中国人名识别的分词器
     *
     * @param text 待分词文本
     * @return 识别出中国人名的分词结果
     * @example "秦光荣会见企业家" → [秦光荣/nr, 会见/v, 企业家/n]
     */
    public static List<Term> segmentWithChineseName(String text) {
        Segment seg = HanLP.newSegment().enableNameRecognize(true);
        return seg.seg(text);
    }

    /**
     * 启用音译人名识别（如 Bill Gates、Zuckerberg）
     *
     * @param text 待分词文本
     * @return 识别出音译人名的分词结果
     */
    public static List<Term> segmentWithTranslatedName(String text) {
        Segment seg = HanLP.newSegment().enableTranslatedNameRecognize(true);
        return seg.seg(text);
    }

    /**
     * 启用日本人名识别
     *
     * @param text 待分词文本
     * @return 识别出日本人名的分词结果
     */
    public static List<Term> segmentWithJapaneseName(String text) {
        Segment seg = HanLP.newSegment().enableJapaneseNameRecognize(true);
        return seg.seg(text);
    }

    /**
     * 启用地名识别（如省份、城市、乡镇）
     *
     * @param text 待分词文本
     * @return 识别出地名的分词结果
     */
    public static List<Term> segmentWithPlace(String text) {
        Segment seg = HanLP.newSegment().enablePlaceRecognize(true);
        return seg.seg(text);
    }

    /**
     * 启用机构名识别（如公司、学校、政府机构）
     *
     * @param text 待分词文本
     * @return 识别出机构名的分词结果
     */
    public static List<Term> segmentWithOrganization(String text) {
        Segment seg = HanLP.newSegment().enableOrganizationRecognize(true);
        return seg.seg(text);
    }

    /**
     * 同时启用所有命名实体识别（人名、地名、机构名、音译名）
     *
     * @param text 待分词文本
     * @return 综合 NER 分词结果
     */
    public static List<Term> segmentWithAllNER(String text) {
        Segment seg = HanLP.newSegment()
                .enableNameRecognize(true)
                .enableTranslatedNameRecognize(true)
                .enableJapaneseNameRecognize(true)
                .enablePlaceRecognize(true)
                .enableOrganizationRecognize(true);
        return seg.seg(text);
    }

    /**
     * 动态添加自定义词到全局词典（仅本次运行有效）
     *
     * @param word 自定义词
     * @return 是否添加成功
     */
    public static boolean addCustomWord(String word) {
        return CustomDictionary.add(word);
    }

    /**
     * 动态插入带词性和频次的自定义词
     *
     * @param word 词语
     * @param attr 词性及频次，格式如 "nz 1024"
     * @return 是否插入成功
     */
    public static boolean insertCustomWord(String word, String attr) {
        return CustomDictionary.insert(word, attr);
    }

    /**
     * 扫描文本中出现的所有自定义词（基于 AhoCorasick 自动机）
     *
     * @param text 待扫描文本
     * @param callback 回调函数：(begin, end, attribute) → 处理匹配项
     */
    public static void scanCustomWords(String text, CustomWordCallback callback) {
        char[] chars = text.toCharArray();
        CustomDictionary.parseText(chars, (begin, end, value) ->
                callback.onHit(begin, end, new String(chars, begin, end - begin), value)
        );
    }

    @FunctionalInterface
    public interface CustomWordCallback {
        void onHit(int begin, int end, String word, Object attribute);
    }

    /**
     * 提取关键词（TextRank 算法）
     *
     * @param text     文本内容
     * @param topK     返回前 K 个关键词
     * @return 关键词列表
     */
    public static List<String> extractKeywords(String text, int topK) {
        return HanLP.extractKeyword(text, topK);
    }

    /**
     * 自动生成文本摘要（TextRank 句子抽取）
     *
     * @param document 文档全文
     * @param topK     返回前 K 句
     * @return 摘要句子列表
     */
    public static List<String> extractSummary(String document, int topK) {
        return HanLP.extractSummary(document, topK);
    }

    /**
     * 提取高质量短语（基于互信息与信息熵）
     *
     * @param text 文本
     * @param topK 返回前 K 个短语
     * @return 短语列表
     */
    public static List<String> extractPhrases(String text, int topK) {
        return HanLP.extractPhrase(text, topK);
    }

    /**
     * 汉字转拼音（支持多音字）
     *
     * @param text 汉字文本
     * @return Pinyin 对象列表
     */
    public static List<Pinyin> convertToPinyin(String text) {
        return HanLP.convertToPinyinList(text);
    }

    /**
     * 简体中文转繁体中文（支持简繁分歧词）
     *
     * @param simplifiedText 简体文本
     * @return 繁体文本
     */
    public static String toTraditional(String simplifiedText) {
        return HanLP.convertToTraditionalChinese(simplifiedText);
    }

    /**
     * 繁体中文转简体中文
     *
     * @param traditionalText 繁体文本
     * @return 简体文本
     */
    public static String toSimplified(String traditionalText) {
        return HanLP.convertToSimplifiedChinese(traditionalText);
    }

    /**
     * N-最短路分词器（精度较高，速度较慢）
     *
     * @param text 待分词文本
     * @return 分词结果
     */
    public static List<Term> nShortSegment(String text) {
        NShortSegment seg = new NShortSegment();
        seg.enableCustomDictionary(false);
        return seg.seg(text);
    }

    /**
     * 最短路分词器（Dijkstra，速度快，精度良好）
     *
     * @param text 待分词文本
     * @return 分词结果
     */
    public static List<Term> dijkstraSegment(String text) {
        DijkstraSegment seg = new DijkstraSegment();
        seg.enableCustomDictionary(false);
        return seg.seg(text);
    }

    /**
     * 获取分词结果及其在原文中的偏移位置
     *
     * @param text 待分词文本
     * @return 带 offset 的 Term 列表
     */
    public static List<Term> segmentWithOffset(String text) {
        return HanLP.segment(text); // Term.offset 已包含
    }


    /**
     * 优化后的匹配度算法：查询覆盖率 (Query Recall)
     * 计算公式：命中 Query 词的数量 / Query 词的总数量
     */
    public static float calculateOverlap(List<String> docKeywords, Set<String> setQuery) {
        if (docKeywords == null || docKeywords.isEmpty() || setQuery.isEmpty()) {
            return 0.0F;
        }

        Set<String> setDoc = new HashSet<>(docKeywords);

        // 取交集
        Set<String> intersection = new HashSet<>(setQuery);
        intersection.retainAll(setDoc);

        // 返回：交集数量 / 查询词的数量 (范围 0.0 - 1.0)
       float result= (float) intersection.size() / setQuery.size();
        System.out.println(">>> calculateOverlap: docKeywords=" + docKeywords);
        System.out.println(">>> calculateOverlap: setQuery=" + setQuery);
        System.out.println(">>> calculateOverlap: intersection=" + intersection);
        System.out.println(">>> calculateOverlap: overlap=" + result);
        return result;
    }
}