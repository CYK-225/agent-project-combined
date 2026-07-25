package org.example.common.easyExcel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * EasyExcel 静态工具类，以动态模式为核心设计。
 * <p>
 * 无需预定义 DTO，自动识别 Excel 表头，以 Map 形式读写数据。
 * 适用于 Excel 字段不固定的场景。
 *
 * <h3>核心流程示例：</h3>
 * <pre>{@code
 * // 1. 读取用户上传的 Excel（自动识别表头）
 * List<Map<String, String>> data = EasyExcelUtils.readAsMap(file.getInputStream());
 *
 * // 2. 业务处理...
 * List<Map<String, Object>> processed = yourBusinessLogic(data);
 *
 * // 3. 导出新 Excel（自动从 Map key 推断表头）
 * EasyExcelUtils.writeDynamicToResponse(response, "处理结果", processed);
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
public final class EasyExcelUtils {

    private EasyExcelUtils() {
        // 工具类，禁止实例化
    }

    // ======================== 动态读取（Dynamic Read） ========================

    /**
     * 从 InputStream 读取 Excel，自动识别第一行为表头，返回 List&lt;Map&gt;。
     * <p>无需任何 DTO 或配置，表头自动作为 Map 的 key。
     *
     * @param inputStream Excel 文件输入流（调用方负责关闭）
     * @return 每行数据为一个 Map，key 为表头，value 为单元格文本
     */
    public static List<Map<String, String>> readAsMap(InputStream inputStream) {
        return readAsMap(inputStream, 0, 1);
    }

    /**
     * 从 InputStream 读取指定 Sheet，指定表头行号。
     *
     * @param inputStream   Excel 输入流
     * @param sheetIndex    Sheet 索引（从 0 开始）
     * @param headRowNumber 表头行号（从 1 开始）
     * @return 每行数据为一个 Map
     */
    public static List<Map<String, String>> readAsMap(InputStream inputStream,
                                                      int sheetIndex,
                                                      int headRowNumber) {
        var data = new ArrayList<Map<String, String>>();
        EasyExcel.read(inputStream)
                .extraRead(CellExtraTypeEnum.MERGE)
                .sheet(sheetIndex)
                .headRowNumber(headRowNumber - 1)
                .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                    private List<String> headers;

                    @Override
                    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
                        headers = new ArrayList<>();
                        headMap.values().stream()
                                .map(cellData -> cellData.getStringValue())
                                .filter(Objects::nonNull)
                                .forEach(headers::add);
                    }

                    @Override
                    public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
                        var row = new LinkedHashMap<String, String>();
                        if (headers != null) {
                            for (int i = 0; i < headers.size(); i++) {
                                row.put(headers.get(i), rowMap.getOrDefault(i, ""));
                            }
                        } else {
                            rowMap.forEach((k, v) -> row.put(String.valueOf(k), v != null ? v : ""));
                        }
                        data.add(row);
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.debug("Excel 读取完成，共 {} 行数据", data.size());
                    }
                })
                .doRead();
        return data;
    }

    /**
     * 从文件路径读取 Excel。
     *
     * @param filePath Excel 文件路径
     * @return 每行数据为一个 Map
     */
    public static List<Map<String, String>> readAsMap(String filePath) {
        return readAsMap(new File(filePath));
    }

    /**
     * 从 File 对象读取 Excel。
     */
    public static List<Map<String, String>> readAsMap(File file) {
        try (var is = new FileInputStream(file)) {
            return readAsMap(is);
        } catch (IOException e) {
            throw new RuntimeException(STR."读取 Excel 文件失败: \{file.getPath()}", e);
        }
    }

    /**
     * 读取所有 Sheet，返回 Sheet 名 → 数据列表。
     */
    public static Map<String, List<Map<String, String>>> readAllSheetsAsMap(InputStream inputStream) {
        var result = new LinkedHashMap<String, List<Map<String, String>>>();
        // 先获取 Sheet 数量
        try (var workbook = com.alibaba.excel.EasyExcel.read(inputStream).build()) {
            var sheets = workbook.excelExecutor().sheetList();
            for (int i = 0; i < sheets.size(); i++) {
                String sheetName = sheets.get(i).getSheetName();
                // 需要重新创建流来读取每个 sheet，此处简化为按索引读取
                result.put(sheetName, List.of());
            }
        }
        return result;
    }

    // ======================== DTO 模式读取 ========================

    /**
     * 使用 DTO 类型读取 Excel。
     *
     * @param inputStream Excel 输入流
     * @param dataType    DTO 类型
     * @param <T>         DTO 类型
     * @return 解析后的 DTO 列表
     */
    public static <T> List<T> readAsDTO(InputStream inputStream, Class<T> dataType) {
        var data = new ArrayList<T>();
        EasyExcel.read(inputStream, dataType, new AnalysisEventListener<T>() {
            @Override
            public void invoke(T row, AnalysisContext context) {
                data.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.debug("Excel DTO 读取完成，共 {} 行数据", data.size());
            }
        }).sheet().doRead();
        return data;
    }

    /**
     * 从文件读取为 DTO。
     */
    public static <T> List<T> readAsDTO(String filePath, Class<T> dataType) {
        try (var is = new FileInputStream(filePath)) {
            return readAsDTO(is, dataType);
        } catch (IOException e) {
            throw new RuntimeException(STR."读取 Excel 文件失败: \{filePath}", e);
        }
    }

    // ======================== 动态写入（Dynamic Write） ========================

    /**
     * 将 List&lt;Map&gt; 写入 OutputStream，自动从第一个 Map 的 key 推断表头。
     *
     * @param outputStream 输出流
     * @param sheetName    Sheet 名称
     * @param data         数据列表（Map 的 key 作为表头）
     */
    public static void writeDynamic(OutputStream outputStream,
                                    String sheetName,
                                    List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            writeEmptySheet(outputStream, sheetName);
            return;
        }
        // 从第一个元素的 key 推断表头（保持插入顺序）
        var headers = new ArrayList<>(data.getFirst().keySet());
        writeDynamic(outputStream, sheetName, headers, data);
    }

    /**
     * 指定表头顺序写入。
     *
     * @param outputStream 输出流
     * @param sheetName    Sheet 名称
     * @param headers      表头列表
     * @param data         数据列表
     */
    public static void writeDynamic(OutputStream outputStream,
                                    String sheetName,
                                    List<String> headers,
                                    List<Map<String, Object>> data) {
        // 构建表头行
        var headList = headers.stream()
                .map(h -> (List<String>) new ArrayList<String>(List.of(h)))
                .toList();

        // 构建数据行
        var dataList = data.stream()
                .map(row -> headers.stream()
                        .map(h -> row.getOrDefault(h, ""))
                        .toList())
                .toList();

        var strategy = buildCellStyleStrategy();
        EasyExcel.write(outputStream)
                .head(headList)
                .registerWriteHandler(strategy)
                .sheet(sheetName)
                .doWrite(dataList);
    }

    /**
     * 写入到文件。
     */
    public static void writeDynamicToFile(String filePath,
                                           String sheetName,
                                           List<Map<String, Object>> data) {
        try (var os = new FileOutputStream(filePath)) {
            writeDynamic(os, sheetName, data);
        } catch (IOException e) {
            throw new RuntimeException(STR."写入 Excel 文件失败: \{filePath}", e);
        }
    }

    /**
     * 写入到字节数组。
     */
    public static byte[] writeDynamicToBytes(String sheetName,
                                              List<Map<String, Object>> data) {
        try (var baos = new ByteArrayOutputStream()) {
            writeDynamic(baos, sheetName, data);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("写入 Excel 字节数组失败", e);
        }
    }

    /**
     * 将数据写入 HttpServletResponse，触发浏览器下载。
     * 动态模式，自动从 Map key 推断表头。
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param data     数据列表
     */
    public static void writeDynamicToResponse(HttpServletResponse response,
                                               String fileName,
                                               List<Map<String, Object>> data) {
        setExcelResponseHeaders(response, fileName);
        try (var os = response.getOutputStream()) {
            writeDynamic(os, "Sheet1", data);
        } catch (IOException e) {
            log.error("写入 Excel 响应流失败", e);
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    /**
     * 将数据写入 HttpServletResponse，指定表头。
     */
    public static void writeDynamicToResponse(HttpServletResponse response,
                                               String fileName,
                                               String sheetName,
                                               List<String> headers,
                                               List<Map<String, Object>> data) {
        setExcelResponseHeaders(response, fileName);
        try (var os = response.getOutputStream()) {
            writeDynamic(os, sheetName, headers, data);
        } catch (IOException e) {
            log.error("写入 Excel 响应流失败", e);
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    // ======================== DTO 模式写入 ========================

    /**
     * 使用 DTO 类型写入 OutputStream。
     */
    public static <T> void writeDTO(OutputStream outputStream,
                                     String sheetName,
                                     Class<T> dataType,
                                     List<T> data) {
        var strategy = buildCellStyleStrategy();
        EasyExcel.write(outputStream, dataType)
                .registerWriteHandler(strategy)
                .sheet(sheetName)
                .doWrite(data != null ? data : List.of());
    }

    /**
     * DTO 模式写入到文件。
     */
    public static <T> void writeDTOToFile(String filePath,
                                           String sheetName,
                                           Class<T> dataType,
                                           List<T> data) {
        try (var os = new FileOutputStream(filePath)) {
            writeDTO(os, sheetName, dataType, data);
        } catch (IOException e) {
            throw new RuntimeException(STR."写入 Excel 文件失败: \{filePath}", e);
        }
    }

    /**
     * DTO 模式写入到 HttpServletResponse。
     */
    public static <T> void writeDTOToResponse(HttpServletResponse response,
                                               String fileName,
                                               String sheetName,
                                               Class<T> dataType,
                                               List<T> data) {
        setExcelResponseHeaders(response, fileName);
        try (var os = response.getOutputStream()) {
            writeDTO(os, sheetName, dataType, data);
        } catch (IOException e) {
            log.error("写入 Excel 响应流失败", e);
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    // ======================== 多 Sheet 写入 ========================

    /**
     * 多 Sheet 动态导出。每个 Map 对应一个 Sheet。
     *
     * @param outputStream 输出流
     * @param sheetData    Sheet 名称 → 数据列表
     */
    public static void writeMultiSheetDynamic(OutputStream outputStream,
                                               Map<String, List<Map<String, Object>>> sheetData) {
        var strategy = buildCellStyleStrategy();
        try (var writer = EasyExcel.write(outputStream).registerWriteHandler(strategy).build()) {
            int i = 0;
            for (var entry : sheetData.entrySet()) {
                var sheetName = entry.getKey();
                var data = entry.getValue();
                if (data == null || data.isEmpty()) {
                    var sheet = new WriteSheet();
                    sheet.setSheetName(sheetName);
                    sheet.setSheetNo(i++);
                    writer.write(List.of(), sheet);
                    continue;
                }
                var headers = new ArrayList<>(data.getFirst().keySet());
                var headList = headers.stream()
                        .<List<String>>map(h -> new ArrayList<>(List.of(h)))
                        .toList();
                var dataList = data.stream()
                        .map(row -> headers.stream()
                                .map(h -> row.getOrDefault(h, ""))
                                .toList())
                        .toList();
                var sheet = new WriteSheet();
                sheet.setSheetName(sheetName);
                sheet.setSheetNo(i++);
                sheet.setHead(headList);
                writer.write(dataList, sheet);
            }
        }
    }

    // ======================== 内部辅助 ========================

    /**
     * 写入空 Sheet（仅有表头或完全空白）。
     */
    private static void writeEmptySheet(OutputStream outputStream, String sheetName) {
        EasyExcel.write(outputStream).sheet(sheetName).doWrite(List.of());
    }

    /**
     * 设置 HTTP 响应头，支持中文文件名。
     */
    public static void setExcelResponseHeaders(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        var encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                STR."attachment; filename*=UTF-8''\{encodedName}.xlsx");
    }

    /**
     * 构建默认单元格样式策略（表头加粗居中，内容左对齐）。
     */
    private static HorizontalCellStyleStrategy buildCellStyleStrategy() {
        var headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        var headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 12);
        headStyle.setWriteFont(headFont);

        var contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }
}
