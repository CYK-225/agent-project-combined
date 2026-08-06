package org.example.common.easyExcel.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.common.easyExcel.EasyExcelUtils;
import org.example.common.easyExcel.FormExcelHelper;
import org.example.common.easyExcel.annotation.ExcelImport;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * 导入参数解析器。
 * <p>
 * 拦截带 {@link ExcelImport} 注解的 Controller 参数，
 * 自动从请求中获取上传文件，根据参数类型选择解析模式：
 * <ul>
 *   <li>参数是具体 DTO 类 → 调用 {@link FormExcelHelper#readForm}（表单模式）</li>
 *   <li>参数是 {@code List<Map>} → 调用 {@link EasyExcelUtils#readAsMap}（动态模式）</li>
 * </ul>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
public class ExcelImportResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ExcelImport.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        // 1. 从请求中获取上传文件
        var multipartRequest = webRequest.getNativeRequest(MultipartHttpServletRequest.class);
        if (multipartRequest == null || multipartRequest.getFileMap().isEmpty()) {
            throw new IllegalArgumentException("未找到上传文件，请确保请求为 multipart/form-data 且包含文件");
        }
        MultipartFile file = multipartRequest.getFileMap().values().iterator().next();
        var annotation = parameter.getParameterAnnotation(ExcelImport.class);

        // 2. 把上传文件暂存到 request attribute，供 ExcelExportAdvice 复用当模板
        var httpRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        if (httpRequest != null) {
            httpRequest.setAttribute("__EXCEL_IMPORT_FILE__", file);
        }

        // 3. 根据参数类型选择解析模式
        Class<?> paramType = parameter.getParameterType();

        if (paramType == List.class) {
            // 动态模式：List<Map<String, String>>
            log.debug("ExcelImport 动态模式，sheetIndex={}, headRowNumber={}",
                    annotation.sheetIndex(), annotation.headRowNumber());
            return EasyExcelUtils.readAsMap(
                    file.getInputStream(),
                    annotation.sheetIndex(),
                    annotation.headRowNumber()
            );
        } else {
            // 表单模式：具体 DTO 类
            log.debug("ExcelImport 表单模式，目标类型={}", paramType.getSimpleName());
            return FormExcelHelper.readForm(file.getInputStream(), paramType);
        }
    }
}
