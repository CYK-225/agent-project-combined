package org.example.agent.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SkillLoader {
    /**
     * 根据 skill 名读取
     */
    public static String getSkillContent(String dirPath, String skillName) {
        try {
            Path path = Paths.get(dirPath, skillName + ".md");
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        }

        return null;
    }


    public static String getFileContent(String dirPath, String fileName) {
        Path path = Paths.get(dirPath, fileName);

        if (!Files.exists(path)) {
            throw new RuntimeException("文件 不存在: " + fileName);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取 文件 失败: " + fileName, e);
        }
    }
}
