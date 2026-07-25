package com.cyk.Controller;

import cn.hutool.json.JSONObject;
import com.cyk.Enity.SearchBuildingsTo;
import com.cyk.Enity.table.BuildingsEntity;
import com.cyk.Enity.table.CompaniesEntity;
import com.cyk.Enity.table.UsersEntity;
import com.cyk.Mapper.BuildingsMapper;
import com.cyk.Mapper.CompaniesMapper;
import com.cyk.Mapper.UsersMapper;
import com.cyk.Service.impl.AllService;
import com.cyk.common.ResultData;
import com.cyk.task.DAL.Mapper.TaskInfoMapper;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;
import static com.cyk.Enity.table.table.CompaniesEntityTableDef.COMPANIES_ENTITY;
import static com.cyk.task.DAL.DO.table.TaskInfoEntityTableDef.TASK_INFO_ENTITY;



@Slf4j
@RestController
@RequestMapping("/api/baidu/map")
public class BaiduMapController {

    @Resource
    private AllService allService;

    @Resource
    private BuildingsMapper buildingsMapper;

    @Resource
    private CompaniesMapper companiesMapper;

    @Resource
    private UsersMapper usersMapper;

    @Resource
    private TaskInfoMapper taskInfoMapper;

    /**
     * 搜索建筑(baidu)   第一步
     *
     * @param searchBuildingsTo
     * @return
     */
    @PostMapping("/search/buildings")
    public JSONObject searchBuildings(@RequestBody SearchBuildingsTo searchBuildingsTo) {
        return allService.getSearchByKeywordsService().searchBuildings(searchBuildingsTo);
    }

    /**
     * 搜索建筑详情(baidu)
     *
     * @param buildingUid
     * @return
     */
    @PostMapping("/search/buildingDetail/{buildingUid}")
    public JSONObject searchBuildingDetail(@PathVariable String buildingUid) {
        return allService.getSearchCompanyByBuildingService().getBuildingDetailByUid(buildingUid);
    }

    /**
     * 获取商户列表(baidu)
     *
     * @param buildingLocation
     * @param raudis
     * @return
     */
    @PostMapping("/search/merchantCount/{buildingLocation}/{raudis}")
    public JSONObject searchMerchantCount(@PathVariable String buildingLocation, @PathVariable String raudis) {

        return allService.getGetMerchantListService().getMerchantCount(buildingLocation, raudis);
    }

    /**
     * 搜索公司列表(点击地图楼宇图片后触发)   第二步
     *
     * @param buildingLocation
     * @param buildingUid
     */
    @PostMapping("/search/companyList/{buildingLocation}/{buildingUid}")
    public ResultData<JSONObject> searchCompanyList(@PathVariable String buildingLocation,
                                                    @PathVariable String buildingUid) {
        // 传入新增的 radius 参数
        return ResultData.success(allService.getSearchCompanyByBuildingService().searchCompanyByBuilding(buildingLocation, buildingUid));
    }
    /**
     * 获取公司列表(点击地图楼宇图片后触发) 第二步搜索后调用
     *
     * @param uid
     */
    @GetMapping("/{uid}")
    public BuildingsEntity getBuildingDetail(@PathVariable("uid") String uid) {
        // 直接使用 MyBatis-Flex 提供的 selectOneById 方法
        return buildingsMapper.selectOneById(uid);
    }

    /**
     * 搜索公司基本信息(ens) - 升级为 SSE 流式输出并自动落库
     *
     * @param companyName
     * @return SseEmitter
     */
    @PostMapping("/search/EnScan/{companyName}")
    public SseEmitter searchEnScan(@PathVariable String companyName) {
        // 0L 表示永不超时
        SseEmitter emitter = new SseEmitter(0L);

        // 使用新线程异步执行，防止阻塞Tomcat主线程，同时维持SSE长连接
        new Thread(() -> {
            try {
                // 1. 发送开始事件
                emitter.send(SseEmitter.event().name("START").data("开始初始化企业信息: " + companyName));

                // 2. 触发原始的 ENS 服务 (内部已集成写库功能)
                Map<String, Object> result = allService.getEnScanApiService().getCompanyInfo(companyName);

                // 3. 发送完成事件与获取到的数据
                emitter.send(SseEmitter.event().name("DONE").data(result));
                emitter.complete();

            } catch (Exception e) {
                // 发生异常时，推送给前端并结束流
                try {
                    emitter.send(SseEmitter.event().name("ERROR").data("获取失败: " + e.getMessage()));
                } catch (Exception ex) {
                    // 忽略发送失败
                }
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }


    /**
     * 一键初始化楼宇(深度搜索) - 第三步
     */
    @PostMapping("/building/init")
    public SseEmitter initBuilding(@RequestBody initBuildingTo initBuildingTo) {
        String clientId = initBuildingTo.getUserId() + "_2";
        return allService.getBuildingInitService().executeDeepInitTask(
                initBuildingTo.getBuildingUid(),
                initBuildingTo.getCompanyList(),
                clientId,
                initBuildingTo.getUserId()
        );
    }

    /**
     * 一键初始化楼宇(ens搜索) - 第三步
     */
    @PostMapping("/building/ensInit")
    public SseEmitter initBuildingEns(@RequestBody initBuildingTo initBuildingEnsTo) {
        // 触发异步爬虫派发逻辑（已移除 emitter 参数）

        String clientId = initBuildingEnsTo.getUserId() + "_1";

        return allService.getBuildingInitService().executeInitTask(
                initBuildingEnsTo.getBuildingUid(),
                initBuildingEnsTo.getCompanyList(),
                initBuildingEnsTo.getUserId(),
                clientId
        );
    }

    @Data
    private static class initBuildingTo {
        private String buildingUid;
        private List<String> companyList;
        private String userId;
    }


    /**
     * 根据公司名列表批量删除任务(取消任务)
     */
    @PostMapping("/deleteTask/companyList")
    public ResultData<Integer> deleteTaskByCompanyList(@RequestBody List<String> companyList) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(TASK_INFO_ENTITY.COMPANY_NAME.in(companyList));
        return ResultData.success(taskInfoMapper.deleteByQuery(wrapper));
    }



    /**
     * 根据楼宇 UID 获取公司详情列表
     * 请求示例: GET /companies/listByBuilding?buildingUid=12345
     * * @param buildingUid 楼宇的唯一标识
     * @return 该楼宇下的公司详情列表
     */
    @GetMapping("/listByBuilding/{buildingUid}")
    public ResultData<List<CompaniesEntity>>  getCompaniesByBuildingUid(@PathVariable("buildingUid") String buildingUid) {
        if (buildingUid == null) return ResultData.error("参数 buildingUid 不能为空");
        log.info("获取公司详情列表，buildingUid: " + buildingUid);
        // 构造查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                // 条件 1：匹配 building_uid
                .where(COMPANIES_ENTITY.BUILDING_UID.eq(buildingUid))
                // 条件 2：过滤掉已逻辑删除的数据 (假设 0 是正常，1 是已删除，请根据实际情况调整)
                .and(COMPANIES_ENTITY.IS_DELETED.eq(0))
                // 可选：可以按创建时间倒序排列
                .orderBy(COMPANIES_ENTITY.CREATE_TIME.desc());

        // 使用 selectListByQuery 查询列表
        return ResultData.success(companiesMapper.selectListByQuery(queryWrapper));
    }
    /**
     * 收藏楼宇
     * 请求示例: POST /buildings/favorite/{userId}/{buildingUId}
     */
    @PostMapping("/favorite/{userId}/{buildingUId}")
    public ResultData<String> favoriteBuilding(@PathVariable String userId, @PathVariable String buildingUId) {
        log.info("收藏楼宇，userId: " + userId + ", buildingUId: " + buildingUId);

        // 1. 获取用户实体
        UsersEntity user = usersMapper.selectOneById(userId);
        if (user == null) {
            return ResultData.error("用户不存在");
        }

        String[] favoriteBuildings = user.getFavoriteBuildings();

        // 2. 将数组转换为 ArrayList 以便操作，若为 null 则初始化空列表
        List<String> list = favoriteBuildings == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(favoriteBuildings));

        // 3. 判断该楼宇是否已收藏
        if (list.contains(buildingUId)) {
            return ResultData.error("该楼宇已收藏");
        }

        // 4. 添加新收藏并转回数组
        list.add(buildingUId);
        String[] newFavorites = list.toArray(new String[0]);

        // 5. 更新数据库 (TypeHandler 会自动将其处理为 PostgreSQL 的数组格式)
        boolean success = UpdateChain.of(UsersEntity.class)
                .set(UsersEntity::getFavoriteBuildings, newFavorites)
                .where(UsersEntity::getId).eq(userId)
                .update();

        if (success) {
            return ResultData.success("收藏成功");
        } else {
            return ResultData.error("收藏失败，请稍后重试");
        }
    }

    /**
     * 获取用户收藏的楼宇列表
     * 请求示例: GET /buildings/favorite/{userId}
     */
    @GetMapping("/favorite/{userId}")
    public ResultData<List<Map<String, Object>>> getFavoriteBuildings(@PathVariable String userId) {
        log.info("获取用户收藏列表，userId: " + userId);

        // 1. 获取用户实体
        UsersEntity user = usersMapper.selectOneById(userId);
        if (user == null) {
            return ResultData.error("用户不存在");
        }

        String[] favoriteBuildings = user.getFavoriteBuildings();

        // 2. 如果没有收藏记录，直接返回空列表
        if (favoriteBuildings == null || favoriteBuildings.length == 0) {
            return ResultData.success(Collections.emptyList());
        }

        // 3. 将数组转换为 List 供 MyBatis-Flex 的 in 条件使用
        List<String> uidList = Arrays.asList(favoriteBuildings);

        // 4. 根据 uid 列表批量查询楼宇详细信息
        List<BuildingsEntity> buildingList = QueryChain.of(BuildingsEntity.class)
                .where(BuildingsEntity::getUid).in(uidList)
                .list();

        // 5. 严格按照前端需要的 5 个核心字段进行组装
        List<Map<String, Object>> resultList = buildingList.stream().map(building -> {
            Map<String, Object> map = new HashMap<>();
            map.put("uid", building.getUid());
            map.put("name", building.getName());
            map.put("address", building.getAddress());
            map.put("location", building.getLocation());
            return map;
        }).collect(Collectors.toList());

        return ResultData.success(resultList);
    }

    /**
     * 取消收藏楼宇
     * 请求示例: DELETE /buildings/favorite/{userId}/{buildingUId}
     */
    @DeleteMapping("/favorite/{userId}/{buildingUId}")
    public ResultData<String> removeFavoriteBuilding(@PathVariable String userId, @PathVariable String buildingUId) {
        log.info("取消收藏楼宇，userId: " + userId + ", buildingUId: " + buildingUId);

        // 1. 获取用户实体
        UsersEntity user = usersMapper.selectOneById(userId);
        if (user == null) {
            return ResultData.error("用户不存在");
        }

        String[] favoriteBuildings = user.getFavoriteBuildings();

        // 2. 如果收藏列表为空，直接返回成功（幂等操作）
        if (favoriteBuildings == null || favoriteBuildings.length == 0) {
            return ResultData.success("取消收藏成功");
        }

        // 3. 将数组转换为 List 进行移除
        List<String> list = new ArrayList<>(Arrays.asList(favoriteBuildings));
        if (!list.contains(buildingUId)) {
            return ResultData.success("取消收藏成功"); // 本来就不在列表中
        }

        // 移除目标 UID
        list.remove(buildingUId);

        // 4. 转回数组
        String[] newFavorites = list.toArray(new String[0]);

        // 5. 更新数据库
        boolean success = UpdateChain.of(UsersEntity.class)
                .set(UsersEntity::getFavoriteBuildings, newFavorites)
                .where(UsersEntity::getId).eq(userId)
                .update();

        if (success) {
            return ResultData.success("取消收藏成功");
        } else {
            return ResultData.error("取消收藏失败，请稍后重试");
        }
    }
}