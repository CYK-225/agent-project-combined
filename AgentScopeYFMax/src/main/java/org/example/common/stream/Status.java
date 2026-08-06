package org.example.common.stream;

public enum Status {
    THINK("思考"),
    TOOL("工具"),
    DIALOGUE("对话");


    private final String description;

    Status(String description) {
        this.description = description;
    }

    public String getStartTag(String detail) {
        return STR."<\{this.description}开始>:\{detail}\n";
    }

    public String getEndTag(String detail) {
        return STR."<\{this.description}结束>:\{detail}\n";
    }

    public String getErrorTag(String error) {
        return STR."<对话异常>:\{error}\n";
    }
}