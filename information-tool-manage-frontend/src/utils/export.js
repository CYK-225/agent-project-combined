/**
 * 将 JSON 数据导出为 CSV 文件 (Excel 兼容)
 * @param {Array} data - 数据数组
 * @param {Array} columns - 列配置 [{ title: '列名', key: '字段名', formatter: (val, row) => string }]
 * @param {String} filename - 导出的文件名
 */
export function exportToCsv(data, columns, filename = 'export.csv') {
  if (!data || data.length === 0) {
    return false
  }

  // 1. 构建表头
  const headers = columns.map(col => `"${col.title}"`).join(',')
  
  // 2. 构建数据行
  const rows = data.map(row => {
    return columns.map(col => {
      let val = col.formatter ? col.formatter(row[col.key], row) : row[col.key]
      // 处理 null/undefined，转义内部的双引号，并用双引号包裹以防包含逗号或换行
      val = val === null || val === undefined ? '' : String(val)
      val = val.replace(/"/g, '""')
      return `"${val}"`
    }).join(',')
  })

  // 3. 拼接结果并加入 UTF-8 BOM 防止 Excel 中文乱码
  const csvContent = '\uFEFF' + headers + '\n' + rows.join('\n')
  
  // 4. 创建 Blob 并触发下载
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)
  link.setAttribute('href', url)
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  return true
}
