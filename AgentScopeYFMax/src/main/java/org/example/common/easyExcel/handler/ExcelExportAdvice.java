package org.example.common.easyExcel.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.easyExcel.EasyExcelUtils;
import org.example.common.easyExcel.FormExcelHelper;
import org.example.common.easyExcel.annotation.ExcelExport;
import org.example.common.easyExcel.annotation.ExcelImport;
import org.example.common.easyExcel.config.EasyExcelProperties;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 导出响应拦截器。
 * <p>
 * 拦截带 {@link ExcelExport} 注解的 Controller 方法，
 * 将返回值自动写成 Excel 文件下载。
 * 如果配置了 {@code easyexcel.export-path}，还会自动保存一份到本地。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class ExcelExportAdvice implements ResponseBodyAdvice<Object> {

    private final EasyExcelProperties properties;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(ExcelExport.class);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        var annotation = returnType.getMethodAnnotation(ExcelExport.class);
        if (annotation == null) return body;

        try {
            // 先写入 byte[]，同时用于 HTTP 响应和本地保存
            var baos = new ByteArrayOutputStream();
            writeToStream(baos, body, annotation);
            byte[] excelBytes = baos.toByteArray();

            // 1. 写入 HTTP 响应流（浏览器下载）
            var servletResponse = getServletResponse();
            EasyExcelUtils.setExcelResponseHeaders(servletResponse, annotation.fileName());
            servletResponse.getOutputStream().write(excelBytes);
            servletResponse.getOutputStream().flush();

            // 2. 如果配置了本地保存路径，额外存一份
            saveToLocal(excelBytes, annotation.fileName());

            return null; // 已直接写入 response
        } catch (Exception e) {
            log.error("Excel 导出失败", e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把数据写入 OutputStream。
     */
    private void writeToStream(OutputStream outputStream, Object body, ExcelExport annotation) throws Exception {
        MultipartFile importedFile = getImportedFile();
        String templatePath = annotation.template();

        if (importedFile != null) {
            log.debug("ExcelExport: 使用上传文件当模板");
            try (var templateStream = importedFile.getInputStream()) {
                FormExcelHelper.writeForm(templateStream, outputStream, body);
            }
        } else if (!templatePath.isEmpty()) {
            log.debug("ExcelExport: 使用 classpath 模板: {}", templatePath);
            try (var templateStream = getClass().getClassLoader().getResourceAsStream(templatePath)) {
                if (templateStream == null) {
                    throw new IllegalStateException(STR."模板文件未找到: \{templatePath}");
                }
                FormExcelHelper.writeForm(templateStream, outputStream, body);
            }
        } else if (body instanceof List<?> list) {
            log.debug("ExcelExport: 动态模式，{} 行数据", list.size());
            if (!list.isEmpty() && list.getFirst() instanceof Map) {
                @SuppressWarnings("unchecked")
                var mapList = (List<Map<String, Object>>) (List<?>) list;
                EasyExcelUtils.writeDynamic(outputStream, annotation.sheetName(), mapList);
            } else if (!list.isEmpty()) {
                com.alibaba.excel.EasyExcel.write(outputStream, list.getFirst().getClass())
                        .sheet(annotation.sheetName())
                        .doWrite(list);
            } else {
                EasyExcelUtils.writeDynamic(outputStream, annotation.sheetName(), List.of());
            }
        } else {
            log.debug("ExcelExport: 单对象无模板，转动态写入");
            FormExcelHelper.writeForm(new ByteArrayInputStream(new byte[0]), outputStream, body);
        }
    }

    /**
     * 如果配置了 exportPath，把 Excel 字节保存到本地文件。
     * 文件名格式：{fileName}_{时间戳}.xlsx
     */
    private void saveToLocal(byte[] excelBytes, String fileName) {
        String exportPath = properties.getExportPath();
        if (exportPath == null || exportPath.isBlank()) return;

        try {
            Path dir = Path.of(exportPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fullFileName = STR."\{fileName}_\{timestamp}.xlsx";
            Path filePath = dir.resolve(fullFileName);

            Files.write(filePath, excelBytes);
            log.info("Excel 已保存到本地: {}", filePath.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Excel 本地保存失败（不影响 HTTP 导出）: {}", e.getMessage());
        }
    }

    private MultipartFile getImportedFile() {
        var request = getServletRequest();
        Object file = request.getAttribute("__EXCEL_IMPORT_FILE__");
        return file instanceof MultipartFile mf ? mf : null;
    }

    private HttpServletRequest getServletRequest() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("非 Web 请求上下文");
        return attrs.getRequest();
    }

    private HttpServletResponse getServletResponse() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("非 Web 请求上下文");
        return attrs.getResponse();
    }
}
