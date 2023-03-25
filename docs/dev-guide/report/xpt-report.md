# 报表引擎

## 初始化数据
在Excel报表文件中，通过XptWorkbookModel这个Sheet来设置整个报表的初始化逻辑。它对应workbook.xdef元模型中的XptWorkbookModel定义。

一般我们在展开前这个xpl段中执行初始化代码，例如
```
<c:script>
  let entity = {...}
  assign("entity",entity)
</c:script>
```

assign调用将把变量设置到scope上下文中，在报表单元格中就可以使用该变量。否则变量的作用域就是script内部，不会暴露给外部环境。


## 单元格表达式
在单元格中，可以通过EL表达式形式来设置单元格的值，例如 ${entity.name}。

在单元格表达式的执行环境中存在一个全局变量 xptRt，它对应于IXptRuntime类型，提供如下属性和方法

- xptRt.cell : 当前单元格， ExpandedCell类型
- xptRt.row  : 当前行
- xptRt.table: 当前表格
- xptRt.sheet； 当前Excel表单
- xptRt.workbook: 当前Excel工作簿
- xptRt.field(name): 从最近邻的环境对象上获取字段值

一种便捷的写法是写 `*=fieldName`，它等价于 ${xptRt.field(fieldName)}

## 单元格展开
在单元格的批注中可以写如下属性

- expandType:  r表示沿行的方向展开，而c表示沿列的方向展开
- expandExpr:  如果非空，则表达式应该返回一个集合，当前单元格会按照此集合的值展开成多个单元格。
- field: 如果没有指定expandExpr，而指定了field，则按照该字段对当前数据集进行汇总，然后针对分组情况进行展开。
- keepExpandEmpty: 当展开集合返回空时，缺省情况下会把对应单元格及其子单元格都删除。但是如果设置了keepExpandEmpty，则这些未展开的单元格会保留，但值会被设置为null
