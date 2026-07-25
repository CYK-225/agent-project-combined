package org.example.common.openai.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.openai.config.OpenAiAdapterProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

/**
 * API Key 校验拦截器
 * <p>
 * 拦截 /v1/** 路径，校验 Authorization: Bearer sk-xxx
 * 错误格式完全遵循 OpenAI 官方错误响应结构，保证前端兼容
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final OpenAiAdapterProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String remoteAddr = request.getRemoteAddr();

        // ── 每个请求必打：来了就是这条 ──
        log.info("[OpenAI 拦截器] 收到请求: {} {} from {}", method, uri, remoteAddr);

        // OPTIONS 预检放行（CORS）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.info("[OpenAI 拦截器] OPTIONS 预检 → 放行");
            return true;
        }

        // ── 打印所有请求头，方便排查 ──
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            StringBuilder headers = new StringBuilder("[");
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                // Authorization 的值脱敏，只显示前缀
                String value = "Authorization".equalsIgnoreCase(name)
                        ? maskAuth(request.getHeader(name))
                        : request.getHeader(name);
                headers.append(name).append("=").append(value).append("; ");
            }
            headers.append("]");
            log.info("[OpenAI 拦截器] 请求头: {}", headers);
        }

        String authHeader = request.getHeader("Authorization");

        // 1. 缺少 Header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[OpenAI 拦截器] ❌ 缺少 Authorization 头或格式错误: authHeader={}",
                    authHeader == null ? "null" : maskAuth(authHeader));
            reject(response, "Missing or malformed Authorization header",
                    "authentication_error", "invalid_api_key");
            return false;
        }

        String incomingKey = authHeader.substring(7).trim();

        // 2. Key 为空
        if (incomingKey.isEmpty()) {
            log.warn("[OpenAI 拦截器] ❌ Bearer 后面是空字符串");
            reject(response, "Empty API key provided",
                    "authentication_error", "invalid_api_key");
            return false;
        }

        // 3. 比对
        if (!properties.getApiKeys().contains(incomingKey)) {
            log.warn("[OpenAI 拦截器] ❌ API Key 不匹配: incoming={}..., 配置的keys数量={}",
                    incomingKey.substring(0, Math.min(8, incomingKey.length())),
                    properties.getApiKeys().size());
            reject(response, "Invalid API key",
                    "authentication_error", "invalid_api_key");
            return false;
        }

        log.info("[OpenAI 拦截器] ✅ 认证通过: {} {}", method, uri);
        return true;
    }

    /**
     * Authorization 头脱敏：只显示 scheme + key 前4位
     */
    private String maskAuth(String authHeader) {
        if (authHeader == null) return "null";
        if (authHeader.length() <= 12) return authHeader.substring(0, Math.min(7, authHeader.length())) + "***";
        return authHeader.substring(0, 12) + "***";
    }

    /**
     * 返回 OpenAI 格式的错误响应
     */
    private void reject(HttpServletResponse response, String message,
                        String type, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorBody = Map.of(
                "error", Map.of(
                        "message", message,
                        "type", type,
                        "param", "",
                        "code", code
                )
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }
}
