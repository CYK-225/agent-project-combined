package org.example.agent.client;


import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.agent.PropsConfig;
import org.example.agent.annotation.UfanClient;
import org.example.agent.annotation.UfanParam;
import org.example.agent.utils.ApiSignUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
public class UfanClientProxy implements InvocationHandler {

    private final PropsConfig propsConfig;


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 方法直接放行
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        UfanClient ufanClient = method.getAnnotation(UfanClient.class);
        if (ufanClient == null) {
            throw new RuntimeException("缺少 @UfanClient 注解");
        }
        Map<String, Object> body = Maps.newHashMap();
        Parameter[] parameters = method.getParameters();
        Map<String, Object> bizData = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {

            Object arg = args[i];
            if (arg == null) continue;

            Parameter parameter = parameters[i];
            Class<?> type = parameter.getType();

            if (ToolExecutionContext.class.isAssignableFrom(type)) {
                ToolExecutionContext context = (ToolExecutionContext) arg;
                String userId = context.get("userId", String.class);
                if (userId != null) {
                    body.put("userId", userId);
                }
                continue;
            }
            UfanParam toolParam = parameter.getAnnotation(UfanParam.class);
            String paramName;
            if (toolParam != null && StringUtils.isNotBlank(toolParam.value())) {
                paramName = toolParam.value();
            } else {
                paramName = parameter.getName();
            }
            if (isSimpleType(type)) {
                bizData.put(paramName, arg);
            } else {
                Map map = new ObjectMapper().convertValue(arg, Map.class);
                bizData.putAll(map);
            }
        }
        Long timestamp = new Date().getTime();//时间戳
        String nonce_str = UUID.randomUUID().toString().trim().replaceAll("-", "");//随机字符串
        body.put("nonceStr", nonce_str);
        body.put("appId", propsConfig.getAppId());
        String sign = ApiSignUtils.sign(propsConfig.getAppSecret(), timestamp, body);
        body.put("bizData", bizData);
        body.put("sign", sign);
        body.put("time", timestamp);

        String url = propsConfig.getBaseUrl() + "/api/ai" + ufanClient.value();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(body), headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );
        String responseBody = response.getBody();
        log.info("请求：" + ufanClient.value() + "参数：" + body + "响应：" + responseBody);
        return responseBody;
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()                // 基本类型
                || clazz == String.class         // 字符串
                || Number.class.isAssignableFrom(clazz) // 数字类型，包括 BigDecimal/BigInteger
                || clazz == Boolean.class        // 布尔
                || clazz == Character.class     // 字符
                || Date.class.isAssignableFrom(clazz) // java.util.Date
                || java.time.temporal.Temporal.class.isAssignableFrom(clazz); // java.time.LocalDate/Time等
    }
}