# 功能设计
XView是与前台框架无关、面向业务领域的前端界面描述。它通过页面(page)、表格(grid)、表单(form)、操作(action)等少量概念来表达前台的核心交互逻辑。最终使用的前台页面page.yaml可以利用`x:gen-extends`元编程机制根据xview模型来动态生成AMIS页面。

XView模型将前端界面的构造分解为字段级、表单/表格级、页面级。
1. control.xlib按照数据类型、数据域和编辑模式推断单个字段使用的显示控件
2. 表单的layout模型控制页面如何布局，在不改变字段控件的情况下可以调整页面布局
3. 构造页面的使用可以直接引用已定义的表单和表格。

## 增删改查标准页面
一般xxx-web模块在maven打包的时候会执行precompile目录下的代码生成器，根据xmeta来生成web前端的代码,例如nop-auth-web/precompile/gen-page.xgen中
```
<c:script>
// 根据xmeta生成页面文件view.xml和page.yaml
codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope);
</c:script>
```

例如根据NopAuthUser.xmeta模型生成的`_NopAuthUser.view.xml`模型:
```xml
<view ...>
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <grids>
        <grid id="list" x:abstract="true">
            <cols>

                <!--用户名-->
                <col id="userName" mandatory="true" sortable="true"/>
                ..
                <!--生日-->
                <col id="birthday" sortable="true" x:abstract="true"/>
            </cols>
        </grid>
        <grid id="pick-list" x:prototype="list" x:abstract="true"/>
    </grids>

    <forms>
        <form id="view" editMode="view" title="查看-用户" i18n-en:title="View User">
            <layout>
 userName[用户名] nickName[昵称]
 deptId[部门] openId[用户外部标识]
 ...
</layout>
        </form>
        <form id="add" editMode="add" title="新增-用户" i18n-en:title="Add User" x:prototype="edit"/>
        <form id="edit" editMode="update" title="编辑-用户" i18n-en:title="Edit User">
            <layout>...</layout>
        </form>
        <form id="query" editMode="query" title="查询条件" i18n-en:title="Query Condition" x:abstract="true">
            <layout/>
        </form>
        <form id="asideFilter" editMode="query" x:abstract="true" submitOnChange="true">
            <layout/>
        </form>
        <form id="batchUpdate" editMode="update" x:abstract="true" title="修改-用户" i18n-en:title="Update User">
            <layout/>
        </form>
    </forms>

    <pages>
        <crud name="main" grid="list" asideFilterForm="asideFilter" filterForm="query" x:abstract="true">
            ...
        </crud>
        <picker name="picker" grid="pick-list" asideFilterForm="asideFilter" filterForm="query" x:abstract="true">
            ...
        </picker>
        <simple name="add" form="add">
            <api url="@mutation:NopAuthUser__save/id"/>
        </simple>
        <simple name="view" form="view">
            <api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
        </simple>
        <simple name="update" form="edit">
            <initApi url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
            <api url="@mutation:NopAuthUser__update/id?id=$id" withFormData="true"/>
        </simple>
    </pages>
</view>
```
生成的很多节点上都标记了x:abstract="true"，它表示该节点是一个虚拟节点，在派生的模型中必须明确声明该节点，才会保留相应内容。这个设计类似于spring beans xml中的abstract属性的设计，即`x:abstract`表示该节点是作为模板存在的节点。

1. objMeta表示当前xview模型会使用指定XMeta文件中的字段配置
2. controlLib控制字段类型如何到前端的具体控件，一般不需要修改。但是如果我们需要针对Mobile使用不同于普通的Web页面组件库来显示，则可以指定一个针对Mobile显示的控件库。
3. 缺省情况下会生成备选的表格list和pick-list，pick-list是用于弹出选择时使用的列表页面。pick-list上的属性`x:prototype="list"`表示pick-list根据兄弟节点list的结构生成，即选择列表页面与普通列表页面是相同的。在派生的xview模型中通过定制pick-list可以定制选择列表页面。
4. 类似于pick-list与list的关系，新增表单add缺省情况下从edit继承，表示除非特殊定制，新增页面的布局与编辑页面相同。每个表单都有自己对应的编辑模式editMode，这样可以使得新增、修改、选择、查看的时候，同一个字段可以使用不同的控件来显示。
5. main页面设置了filterForm=query, asiderFilterForm=asideFilter。这表示如果query表单会作为main页面的查询条件表单。如果配置了asideFilter表单，则会在main页面上通过左侧的side边栏用于显示一部分查询条件。

## 表格、表单、页面
xview模型把表单和表格看作是可复用的基础对象，然后在page模型中可以直接引用表格和表单。

表单布局使用的DSL参见[layout.md](layout.md)

# 常见功能配置

## 1. 左侧以树形结构展示过滤条件
例如NopAuthUser对应的用户管理页面，它左侧是单位树，点击单位后，会按照单位过滤右侧的用户列表。用户列表已有的查询条件和左侧单位树的查询条件会合并在一起传到后台。

```
<form id="asideFilter" submitOnChange="true">
    <layout>
        ==dept[部门]==
        !deptId
    </layout>
    <cells>
        <cell id="deptId">
            <gen-control>
                <input-tree
                        source="@query:NopAuthDept__findList/value:id,label:deptName,children @TreeChildren(max:5)?filter_parentId=__null"/>
            </gen-control>
        </cell>
    </cells>
</form>
```

只需要增加id=asideFilter的form。submitOnChange表示点击后立刻提交查询。 

## 2. 列表数据具有嵌套的父子关系
例如单位树，父单位-子单位构成Tree结构。

按照前台AMIS组件的要求，只要后台返回的数据中具有children字段，它就会自动按照Tree结构展开。Nop平台为GraphQL增加了Tree结构扩展，可以很容易的通过`@TreeChildren`这个指令来指定Tree数据的递归获取。

```
url: "@query:NopAuthDept__findList/value:id,label:deptName,children @TreeChildren(max:5)?filter_parentId=__null"
```

以上调用表示调用后台的NopAuthDept__findList函数，要求返回children字段，通过@TreeChildren指令指定最多递归返回5层的数据。

`filter_parentId=__null`表示按照parentId=null条件过滤得到根节点列表。

## 3. 把多个已有的页面采用Tab页的形式组织成一个复合页面
参见NopAuthDept.view.xml中部门管理功能所使用的tab页
```xml
    <tabs name="tabsView" tabsMode="vertical" mountOnEnter="true" unmountOnExit="true">
        <tab name="main" page="main" title="@i18n:common.treeView"/>
        <tab name="list" page="list" title="@i18n:common.listView"/>
    </tabs>
```

## 4. 为列表页面增加多个查询条件
参考NopAuthUser.view.xml中，只要在id=query的form中增加字段即可。缺省生成的crud页面会使用filterForm属性来引用query表单

```
<form id="query">
    <layout>
        userName gender nickName phone status
    </layout>
</form>

<pages>
  <crud filterForm="query" ... >
</pages>
```
缺省情况下字段总是采用等于条件进行查询，可以在NopAuthUser.xmeta文件中定制属性设置，指定查询算符。


```
<prop name="userName" allowFilterOp="eq,contains" xui:defaultFilterOp="contains"/>

```
以上条件表示userName允许按照eq和contains两种关系过滤算符进行查询，eq表示相等条件，contains表示包含，通过like来实现。`xui:defaultFilterOp`表示缺省过滤算符采用contains。

所有支持的过滤算符在FilterOp.java类中定义，常用的有eq,ne,gt,ge,lt,le,contains,in,startsWith,endsWith等

## 5. 列表页面没有行操作按钮，希望隐藏操作按钮列
```
 <crud name="xxx">
    <table noOperations="true" />
 </crud>   
```
## 6. 增加一个页面，与缺省的增删改查页面类似，但是列表的查询条件不同
```xml

        <crud name="list" grid="list" x:prototype="main">
            <table x:prototype-override="replace">
                <api url="@query:NopAuthDept__findPage/{@pageSelection}"/>
            </table>
        </crud>

```
`x:prototype`表示从兄弟节点继承。 `x:prototype-override=replace`表示使用本节点覆盖得来的table节点，缺省情况下是合并(merge)而不是完全覆盖。

## 7. 点击按钮，弹出一个对话框，填写完毕后执行后台操作，关闭对话框，并刷新原页面
参见[LitemallGoods.view.xml](https://gitee.com/canonical-entropy/nop-app-mall/blob/master/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml)
```
<crud name="role-users" grid="simple-list">
    <listActions>
        <action id="select-user-button" label="@i18n:common.selectUser">
            <dialog page="select-role-users" size="md" noActions="true">
            </dialog>
        </action>
    </listActions>
</crud>

<crud name="select-role-users" grid="pick-list" title="@i18n:common.selectUser">
    
    <listActions>
        <action id="batch-add-user-button" label="@i18n:common.submit" level="primary"
                batch="true" close="select-role-users" reload="role-users-grid">
            <api url="@mutation:NopAuthRole__addRoleUsers">
                <data>
                    <roleId>$roleId</roleId>
                    <userIds>$ids</userIds>
                </data>
            </api>
        </action>
    </listActions>
</crud>
```

* 在action配置如果具有dialog子节点，则表示使用弹出对话框显示。dialog上的page属性可以直接引用已经定义好的页面。如果是完整路径则对应于外部定义的完整页面，如果是page的名称，则表示引用当前XView模型中定义的页面。
* dialog的noActions=true表示不使用对话框内置的提交、取消按钮。          
* action上batch=true表示是针对批量选择的列表条目的操作。close表示执行完当前操作后会关闭窗口。而reload表示会重新加载指定名称的表格，即role-users页面中的增删改查表格。

## 8. 将子表数据和主表一起提交
```
<form id="edit">
<cells>
  <cell id="products">
        <!-- 可以引用外部view模型中的grid来显示子表 -->
        <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
              grid="ref-edit"/>
    </cell>
</cells>
</form>
```
为子表属性增加一个view配置，指定用哪个表格去编辑该子表的数据。XView模型会自动分析view配置，得到表格字段列表，合并到当前表单所对应的GraphQL请求中。

## 9. 定制列表行上的按钮
```
<crud name="main">
    <!-- bounded-merge表示合并结果在当前模型范围内。基础模型中有，当前模型中没有的子节点，会被自动删除。
         缺省生成的代码中已经定义了row-update-button和row-delete-button，只是配置了x:abstract=true，
         因此这里只要声明id，表示启用继承的按钮即可，可以避免编写重复的代码。
     -->
    <rowActions x:override="bounded-merge">
        <!--
            使用drawer而不是对话框来显示编辑表单
        -->
        <action id="row-update-button" actionType="drawer"/>

        <action id="row-delete-button"/>

    </rowActions>
</crud>
```