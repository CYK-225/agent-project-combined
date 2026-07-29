package com.cyk.task.DAL.Service;


import com.cyk.DockerTool.ENS.ENSCallBack;
import com.cyk.DockerTool.V1.DockerV1CallBack;
import com.cyk.Service.ICategoriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 外部执行器服务聚合类
 *
 * <p>【核心职责】</p>
 * 本类是一个聚合服务类，统一管理任务调度系统所需的外部执行器Controller。
 * 这些Controller负责与Docker容器交互，执行具体的任务逻辑。
 *
 * <p>【架构位置】</p>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │         OtherModelService                   │
 * ├─────────────────────────────────────────────┤
 * │  - DockerV1Controller (GUI Agent执行器)      │
 * │  - ensController (ENS企业查询执行器)        │
 * └─────────────────────────────────────────────┘
 *                    │
 *                    ▼
 *              Docker容器
 *                    │
 *          ┌────────┴────────┐
 *          │                 │
 *          ▼                 ▼
 *     GUI Agent         ENS Agent
 *    (Python脚本)       (Python脚本)
 * </pre>
 *
 * <p>【执行器说明】</p>
 * <ul>
 *   <li><b>DockerV1Controller</b>：GUI Agent执行器，处理需要图形界面的自动化任务</li>
 *   <li><b>ENSController</b>：ENS企业查询执行器，处理企业信息查询任务</li>
 * </ul>
 *
 * <p>【使用场景】</p>
 * <ul>
 *   <li>任务执行器需要调用Docker容器执行Python脚本</li>
 *   <li>需要获取容器状态或管理容器生命周期</li>
 *   <li>需要处理容器的回调请求</li>
 * </ul>
 *
 * <p>【使用示例】</p>
 * <pre>
 * // 注入OtherModelService
 * {@literal @}Autowired
 * private OtherModelService otherModelService;
 *
 * // 调用ENS执行器
 * Map&lt;String, Object&gt; result = otherModelService.ensController.queryCompany(
 *     "示例公司", "qcc", "pro"
 * );
 *
 * // 调用Docker执行器
 * String response = otherModelService.DockerV1Controller.performAction(
 *     "打开百度搜索"
 * );
 * </pre>
 *
 * <p>【注意事项】</p>
 * <ul>
 *   <li>这些Controller直接操作Docker容器，使用时需确保Docker环境正常</li>
 *   <li>容器执行是异步的，结果通过回调接口返回</li>
 *   <li>建议在调用前检查容器状态</li>
 * </ul>
 *
 * @author system
 * @since 1.0
 * @see DockerV1CallBack GUI Agent执行器
 * @see ENSCallBack ENS企业查询执行器
 */
@Component
@RequiredArgsConstructor
public class OtherModelService {

//    /**
//     * Docker GUI Agent执行器
//     *
//     * <p>负责执行需要图形界面的自动化任务，如：</p>
//     * <ul>
//     *   <li>浏览器自动化操作</li>
//     *   <li>桌面应用交互</li>
//     *   <li>基于视觉的UI操作</li>
//     * </ul>
//     *
//     * @see DockerV1CallBack
//     */
//    public final DockerV1Controller DockerController;
//
//    /**
//     * ENS企业信息查询执行器
//     *
//     * <p>负责执行企业信息查询任务，支持：</p>
//     * <ul>
//     *   <li>企查查（qcc）查询</li>
//     *   <li>天眼查（tianyan）查询</li>
//     *   <li>爱企查（aqc）查询</li>
//     * </ul>
//     *
//     * @see ENSCallBack
//     */
//    public final ENSController ensController;
//

    /**
     * 提示词服务
     */
    public final ICategoriesService iCategoriesService;

}
