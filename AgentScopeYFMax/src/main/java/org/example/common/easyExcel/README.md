# EasyExcel 注解驱动工具

基于 Alibaba EasyExcel + Apache POI 封装的 Excel 导入导出工具，通过注解驱动实现零样板代码。

## 快速开始

### 1. 添加依赖

父 pom 的 `dependencyManagement` 已管理版本，子模块只需：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
</dependency>
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
</dependency>
```

### 2. 配置导出路径（可选）

在 `application.yml` 中配置，导出时会额外保存一份到本地：

```yaml
easyexcel:
  export-path: C:/Users/陈元科/Desktop/excel-output/
```

不配置则只走浏览器下载，不存本地。

---

## 核心用法

### `@ExcelImport` — 参数注解，自动解析上传的 Excel

放在 Controller 方法参数上，框架自动从请求中获取文件并解析。**不需要写 `MultipartFile`**。

**表单模式** — 参数是 DTO 类，通过 `@ExcelCell` 注解读取指定单元格：

```java
@PostMapping("/import")
public Result handle(@ExcelImport ApplicationFormDTO form) {
    // form 已经有值了
    String name = form.getName();
    return Result.ok(form);
}
```

**动态模式** — 参数是 `List<Map<String, String>>`，自动识别表头：

```java
@PostMapping("/import")
public Result handle(@ExcelImport List<Map<String, String>> rows) {
    rows.forEach(row -> System.out.println(row.get("姓名")));
    return Result.ok(rows);
}
```

### `@ExcelExport` — 方法注解，自动把返回值写成 Excel 下载

放在 Controller 方法上，返回值自动变成 Excel 文件下载。

```java
@ExcelExport(fileName = "数据报表")
@PostMapping("/export")
public List<Map<String, Object>> export() {
    return dataService.query();
}
```

### 组合使用 — 一个接口完成导入+导出

`@ExcelImport` 和 `@ExcelExport` 可以同时使用，上传文件会自动被复用为导出模板：

```java
@ExcelExport(fileName = "审核结果")
@PostMapping("/process")
public ApplicationFormDTO process(@ExcelImport ApplicationFormDTO form) {
    // 判断字段
    if (form.getName().isBlank()) throw new IllegalArgumentException("姓名不能为空");
    // 修改字段
    form.setName(form.getName().trim());
    form.setPhone(form.getPhone().replaceAll("[^0-9]", ""));
    // 筛选子表
    form.setWorkExperiences(
        form.getWorkExperiences().stream()
            .filter(w -> w.getCompany() != null)
            .toList()
    );
    // return 自动写成 Excel 下载
    return form;
}
```

---

## 注解说明

### `@ExcelImport`

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `sheetIndex` | 0 | 读取的 Sheet 索引 |
| `headRowNumber` | 1 | 表头行号（动态模式有效） |

参数类型决定解析模式：
- 具体 DTO 类 → 表单模式（`@ExcelCell` 注解驱动）
- `List<Map<String, String>>` → 动态模式（自动识别表头）

### `@ExcelExport`

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `fileName` | 必填 | 下载文件名（不含 .xlsx） |
| `template` | "" | classpath 模板路径 |
| `sheetName` | "Sheet1" | Sheet 名称 |
| `autoWidth` | true | 是否自动列宽 |

模板优先级：
1. 同方法有 `@ExcelImport` → 用上传文件当模板
2. `template` 非空 → 用 classpath 模板
3. 都没有 → 动态生成

### `@ExcelCell`

DTO 字段上的注解，标记该字段在 Excel 表单中的单元格位置：

```java
@Data
public class UserDTO {
    @ExcelCell(cellRef = "C4")
    private String name;

    @ExcelCell(cellRef = "E4")
    private String gender;
}
```

---

## 文件结构

```
easyExcel/
├── annotation/
│   ├── ExcelCell.java        字段注解，映射到单元格位置
│   ├── ExcelExport.java      方法注解，自动导出 Excel
│   └── ExcelImport.java      参数注解，自动解析上传文件
├── config/
│   ├── EasyExcelAutoConfiguration.java   注册解析器
│   └── EasyExcelProperties.java          yml 配置类
├── demo/
│   └── ApplicationFormService.java       业务处理示例
├── dto/
│   ├── ApplicationFormDTO.java           应聘登记表 DTO
│   ├── OnboardingFormDTO.java            入职信息登记表 DTO
│   ├── WorkExperienceDTO.java            工作经历子 DTO
│   ├── EducationDTO.java                 教育背景子 DTO
│   ├── TrainingDTO.java                  培训经历子 DTO
│   └── FamilyMemberDTO.java              家庭成员子 DTO
├── handler/
│   ├── ExcelExportAdvice.java            导出拦截器（ResponseBodyAdvice）
│   └── ExcelImportResolver.java          导入解析器（HandlerMethodArgumentResolver）
├── controller/
│   └── EasyExcelTestController.java      测试接口
├── EasyExcelUtils.java                   通用静态工具类
└── FormExcelHelper.java                  表单式 Excel 读写工具
```

---

## 工具类

不想用注解时，可以直接用静态工具类：

### FormExcelHelper — 表单式读写

```java
// 读取
ApplicationFormDTO form = FormExcelHelper.readForm(inputStream, ApplicationFormDTO.class);

// 写入（基于模板）
FormExcelHelper.writeForm(templateStream, outputPath, form);

// 写入到 byte[]
byte[] bytes = FormExcelHelper.writeFormToBytes(templateStream, form);
```

### EasyExcelUtils — 通用动态读写

```java
// 动态读取（任意 Excel，自动识别表头）
List<Map<String, String>> data = EasyExcelUtils.readAsMap(inputStream);

// 动态写入
EasyExcelUtils.writeDynamicToResponse(response, "报表", dataList);

// DTO 模式读写
List<UserDTO> users = EasyExcelUtils.readAsDTO(inputStream, UserDTO.class);
EasyExcelUtils.writeDTOToFile("/tmp/users.xlsx", "Sheet1", UserDTO.class, users);
```

---

## 测试接口

| 接口 | 说明 |
|------|------|
| `POST /api/excel/import` | 纯导入，上传 Excel 返回 JSON |
| `POST /api/excel/process` | 导入+导出，字段判断/修改/筛选 |
| `POST /api/excel/review` | 完整审核流程，调用 Service 处理 |
| `POST /api/excel/dynamic-import` | 动态导入任意 Excel |
| `POST /api/excel/dynamic-export` | POST JSON → 下载 Excel |

测试工具（CoolRequest / Postman）：
- Body 类型选 **form-data**
- 添加字段：Key = `file`，Type 切成 **file**，Value 选 Excel 文件
