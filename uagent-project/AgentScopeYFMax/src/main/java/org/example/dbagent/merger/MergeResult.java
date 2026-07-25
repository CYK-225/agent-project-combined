package org.example.dbagent.merger;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 合并结果
 */
@Data
@Builder
public class MergeResult {
    /** 是否成功 */
    private boolean success;
    /** 合并后的代码 */
    private String mergedCode;
    /** 更新的块ID列表 */
    private List<String> updatedBlocks;
    /** 保留的块ID列表 */
    private List<String> preservedBlocks;
    /** 变更列表 */
    private List<String> changes;
    /** 错误信息 */
    private String errorMessage;
}
