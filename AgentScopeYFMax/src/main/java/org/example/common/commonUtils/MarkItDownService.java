package org.example.common.commonUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * MarkItDown 统一解析服务
 * 融合了环境检测、CLI 调用与资源管理
 */
@Slf4j
@Service
public class MarkItDownService {

    // 从配置文件读取 markitdown 可执行路径（支持多环境配置）
    // 例如开发环境：markitdown，生产环境：/usr/local/bin/markitdown
    @Value("${app.markitdown.path:markitdown}")
    private String executablePath;

    // 解析超时时间（分钟）
    @Value("${app.markitdown.timeout:5}")
    private int timeoutMinutes;

    private boolean isAvailable = false;

    /**
     * 服务启动时自动检测环境
     */
    @PostConstruct
    public void init() {
        log.info("正在检测 MarkItDown 环境: {}", executablePath);
        try {
            Process process = new ProcessBuilder(executablePath, "--version").start();
            if (process.waitFor() == 0) {
                isAvailable = true;
                log.info("MarkItDown 环境检测成功，组件已就绪。");
            } else {
                log.warn("MarkItDown 运行异常，文档解析功能将不可用。");
            }
        } catch (Exception e) {
            log.error("未找到 MarkItDown 环境，请检查服务器是否安装 Python 且已配置 PATH。错误: {}", e.getMessage());
            isAvailable = false;
        }
    }

    /**
     * 核心解析方法
     * @param sourceFile 需要解析的源文件（PDF, Docx 等）
     * @return 解析后的 Markdown 字符串
     */
    public String parse(File sourceFile) {
        if (!isAvailable) {
            throw new RuntimeException("当前服务器环境不支持 MarkItDown 解析，请检查配置。");
        }

        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("源文件不存在");
        }

        // 使用 UUID 创建临时的输出文件，防止并发解析时文件名冲突
        Path tempOutputPath = null;
        try {
            tempOutputPath = Files.createTempFile("mid_out_", ".md");
            String outputAbsPath = tempOutputPath.toAbsolutePath().toString();
            String inputAbsPath = sourceFile.getAbsolutePath();

            // 构建命令：markitdown input.pdf -o output.md
            ProcessBuilder pb = new ProcessBuilder(executablePath, inputAbsPath, "-o", outputAbsPath);
            pb.redirectErrorStream(true); // 合并错误流到标准输出

            Process process = pb.start();

            // 读取输出（防止进程阻塞）
            StringBuilder outputLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLog.append(line).append("\n");
                }
            }

            // 等待执行结束并设置超时
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("文档解析超时（" + timeoutMinutes + "分钟）");
            }

            if (process.exitValue() != 0) {
                log.error("MarkItDown 执行失败: {}", outputLog);
                throw new RuntimeException("命令行执行失败，错误代码: " + process.exitValue());
            }

            // 读取生成的 Markdown 内容
            return new String(Files.readAllBytes(tempOutputPath));

        } catch (Exception e) {
            log.error("文档解析过程中发生异常: {}", e.getMessage());
            throw new RuntimeException("解析失败: " + e.getMessage(), e);
        } finally {
            // 无论成功失败，必须清理临时输出文件
            if (tempOutputPath != null) {
                try {
                    Files.deleteIfExists(tempOutputPath);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 对外暴露当前服务是否可用
     */
    public boolean isReady() {
        return isAvailable;
    }
}