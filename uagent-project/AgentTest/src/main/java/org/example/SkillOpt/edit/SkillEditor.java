package org.example.skillOpt.edit;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 文档编辑器 — 将编辑操作应用到 Markdown skill 文档。
 *
 * @author zhilin
 */
@Slf4j
public class SkillEditor {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(#{1,3}\\s+.+)$", Pattern.MULTILINE);

    private SkillEditor() {
    }

    /**
     * 按顺序应用多个编辑。
     */
    public static String applyEdits(String skillMarkdown, List<SkillEdit> edits) {
        String result = skillMarkdown;
        for (int i = 0; i < edits.size(); i++) {
            SkillEdit edit = edits.get(i);
            result = applyEdit(result, edit);
            log.debug("[SkillEditor] Edit {}/{}: {} on '{}'", i + 1, edits.size(),
                    edit.getType(), edit.getTargetSection());
        }
        return result;
    }

    /**
     * 应用单个编辑到 skill 文档。
     */
    public static String applyEdit(String skillMarkdown, SkillEdit edit) {
        if (skillMarkdown == null) skillMarkdown = "";
        if (edit == null || edit.getType() == null) return skillMarkdown;

        return switch (edit.getType()) {
            case APPEND -> applyAppend(skillMarkdown, edit);
            case INSERT_AFTER -> applyInsertAfter(skillMarkdown, edit);
            case REPLACE -> applyReplace(skillMarkdown, edit);
            case DELETE -> applyDelete(skillMarkdown, edit);
        };
    }

    /**
     * 过滤掉受保护区域的编辑。
     */
    public static List<SkillEdit> filterByProtectedRegions(List<SkillEdit> edits, List<String> protectedRegions) {
        if (protectedRegions == null || protectedRegions.isEmpty()) return edits;
        List<SkillEdit> filtered = new ArrayList<>();
        for (SkillEdit edit : edits) {
            boolean isProtected = protectedRegions.stream()
                    .anyMatch(region -> edit.getTargetSection() != null
                            && edit.getTargetSection().toLowerCase().contains(region.toLowerCase()));
            if (!isProtected) {
                filtered.add(edit);
            } else {
                log.info("[SkillEditor] 跳过受保护区域的编辑: {}", edit.getTargetSection());
            }
        }
        return filtered;
    }

    // ==================== 具体编辑操作 ====================

    private static String applyAppend(String skill, SkillEdit edit) {
        String section = edit.getTargetSection();
        if (section == null || section.isBlank()) {
            // 没有指定 section，追加到末尾
            return skill + "\n\n" + edit.getContent();
        }

        // 找到目标 section，在其内容末尾追加
        int sectionStart = findSectionStart(skill, section);
        if (sectionStart < 0) {
            // section 不存在，追加新 section
            return skill + "\n\n## " + section + "\n\n" + edit.getContent();
        }

        // 找到下一个 section 或文档末尾
        int nextSection = findNextSection(skill, sectionStart);
        if (nextSection < 0) nextSection = skill.length();

        String before = stripTrailing(skill.substring(0, nextSection));
        String after = nextSection < skill.length() ? skill.substring(nextSection) : "";
        return before + "\n\n" + edit.getContent() + "\n" + after;
    }

    private static String applyInsertAfter(String skill, SkillEdit edit) {
        String target = edit.getTargetSection();
        if (target == null || target.isBlank()) return skill;

        // 搜索包含 target 文本的行
        Pattern linePattern = Pattern.compile("(" + Pattern.quote(target) + ".*\\n?)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = linePattern.matcher(skill);
        if (matcher.find()) {
            int insertPos = matcher.end();
            return skill.substring(0, insertPos) + edit.getContent() + "\n" + skill.substring(insertPos);
        }

        // 未找到匹配行，追加到末尾
        return skill + "\n\n" + edit.getContent();
    }

    private static String applyReplace(String skill, SkillEdit edit) {
        String section = edit.getTargetSection();
        if (section == null || section.isBlank()) return skill;

        int sectionStart = findSectionStart(skill, section);
        if (sectionStart < 0) {
            // section 不存在，新建
            return skill + "\n\n## " + section + "\n\n" + edit.getContent();
        }

        int nextSection = findNextSection(skill, sectionStart);
        String before = skill.substring(0, sectionStart);
        String after = nextSection >= 0 ? skill.substring(nextSection) : "";

        // 保留 section 标题
        Matcher titleMatcher = Pattern.compile("^(#{1,3}\\s+.+\\n?)").matcher(skill.substring(sectionStart));
        String title = titleMatcher.find() ? titleMatcher.group(1) : "## " + section + "\n";

        return before + title + edit.getContent() + "\n" + after;
    }

    private static String applyDelete(String skill, SkillEdit edit) {
        String section = edit.getTargetSection();
        if (section == null || section.isBlank()) return skill;

        int sectionStart = findSectionStart(skill, section);
        if (sectionStart < 0) return skill;

        int nextSection = findNextSection(skill, sectionStart);
        String before = stripTrailing(skill.substring(0, sectionStart));
        String after = nextSection >= 0 ? skill.substring(nextSection) : "";

        if (before.isEmpty()) return after;
        if (after.isEmpty()) return before;
        return before + "\n\n" + after;
    }

    // ==================== 辅助方法 ====================

    private static int findSectionStart(String skill, String sectionName) {
        Pattern sectionHeader = Pattern.compile(
                "^#{1,3}\\s+" + Pattern.quote(sectionName) + "\\s*$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = sectionHeader.matcher(skill);
        if (matcher.find()) {
            // 返回 section 标题行的起始位置
            return matcher.start();
        }
        return -1;
    }

    private static int findNextSection(String skill, int fromIndex) {
        String sub = skill.substring(fromIndex);
        Matcher matcher = SECTION_PATTERN.matcher(sub);
        // 跳过第一个匹配（当前 section 标题本身）
        if (matcher.find()) {
            if (matcher.find()) {
                return fromIndex + matcher.start();
            }
        }
        return -1;
    }

    private static String stripTrailing(String s) {
        return s.replaceAll("[\\s]+$", "");
    }
}
