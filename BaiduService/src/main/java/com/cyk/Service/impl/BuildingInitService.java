package com.cyk.Service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import com.cyk.Utils.SseEmitterManager;
import com.cyk.task.DAL.Controller.DTO.CreateTaskTO;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BuildingInitService {



    // 1. 移除原来的 enScanApiService，注入强大的异步任务调度器
    @Resource
    private CustomTaskScheduler customTaskScheduler;

    @Resource
    private SseEmitterManager sseEmitterManager;
     /**
     * 执行楼宇企业深度初始化任务 (纯后台分发，不管理连接)
     */
    public SseEmitter executeDeepInitTask(String buildingUid, List<String> companyList,String clientId,String userId) {
        SseEmitter sseEmitter = sseEmitterManager.createEmitter(clientId,0L);
        try {
            log.info("开始执行深度初始化任务: buildingUid={}", buildingUid);

            // 循环调用 Task 系统同时提交任务
            for (int i = 0; i < companyList.size(); i++) {

                String companyName = companyList.get(i);

                if (companyName == null || companyName.trim().isEmpty()) {
                    continue;
                }
                try {
                    // 派发 ENS 任务
                    CreateTaskTO ensTaskTO = CreateTaskTO.builder()
                            .companyName(companyName)
                            .taskType("ENS")
                            .isUpdate(false)
                            .configName("18018219545")
                            .userId(userId)
                            .build();
                    Long taskId = customTaskScheduler.startTask(ensTaskTO);
                    sseEmitterManager.bindTask(String.valueOf(taskId), clientId);

                    // 派发 AI 任务
                    CreateTaskTO aiTaskTO = CreateTaskTO.builder()
                            .companyName(companyName)
                            .taskType("AI")
                            .isUpdate(false)
                            .configName("18018219545")
                            .userId(userId)
                            .build();

                    Long taskId1 = customTaskScheduler.startTask(aiTaskTO);
                    sseEmitterManager.bindTask(String.valueOf(taskId1), clientId);

                    Map<String, Object> payload = Map.of(
                            "company", companyName,
                            "status", "成功"
                    );


                    sseEmitterManager.sendMessageByTaskId(clientId, payload);

                    log.info("第{}家公司【{}】任务派发成功，已加入队列排队", i + 1,companyName,"任务id:" + taskId);


                } catch (Exception e) {
                    log.error("派发企业采集任务失败: {}", companyName, e);
                    // 🚨 如果入队失败，通过映射好的通道直接通知前端该企业失败
                    Map<String, Object> payload = Map.of(
                            "company", companyName,
                            "status", "失败",
                            "error", "任务派发异常: " + e.getMessage()
                    );
                    sseEmitterManager.sendMessageByTaskId(clientId, payload);
                }
            }
            log.info("=== 楼宇【{}】所有企业初始化任务下发完毕 ===", buildingUid);

        } catch (Exception e) {
            log.error("楼宇初始化全局异常", e);
        }
        return sseEmitter;
    }
    public SseEmitter executeInitTask(String buildingUid, List<String> companyList,String userId,String clientId) {
        SseEmitter sseEmitter = sseEmitterManager.createEmitter(clientId,0L);

        try {
            log.info("开始执行初始化任务: buildingUid={}", buildingUid);
            for (int i = 0; i < companyList.size(); i++) {
                String companyName = companyList.get(i);
                if (companyName == null || companyName.trim().isEmpty()) {
                    continue;
                }

                try {
                    CreateTaskTO taskTO = CreateTaskTO.builder()
                            .companyName(companyName)
                            .taskType("ENS")
                            .isUpdate(false)
                            .configName("18018219545")
                            .userId(userId)
                            .build();
                    Long taskId = customTaskScheduler.startTask(taskTO);
                    log.info("第{}家公司【{}】任务入队成功", i + 1, companyName);
                    log.info("任务id:" + taskId);
                    Map<String, Object> payload = Map.of(
                            "company", companyName,
                            "status", "成功"
                    );

                    sseEmitterManager.bindTask(String.valueOf(taskId), clientId);
                    sseEmitterManager.sendMessageByTaskId(clientId, payload);
                } catch (Exception e) {
                    log.error("初始化任务失败: {}", companyName, e);
                    // 🚨 如果入队失败，通过映射好的通道直接通知前端该企业失败
                    Map<String, Object> payload = Map.of(
                            "company", companyName,
                            "status", "失败",
                            "error", "任务入队异常: " + e.getMessage()
                    );

                    sseEmitterManager.sendMessageByTaskId(clientId, payload);
                }
            }
            log.info("=== 楼宇【{}】所有企业初始化任务下发完毕 ===", buildingUid);
        } catch (Exception e) {
            log.error("楼宇初始化全局异常", e);
        }
        return sseEmitter;
    }
}