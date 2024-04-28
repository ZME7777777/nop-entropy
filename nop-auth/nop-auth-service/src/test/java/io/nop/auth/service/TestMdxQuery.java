package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.auth.dao.entity.NopAuthGroupDept;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.api.core.beans.query.QueryFieldBean.mainField;
import static io.nop.api.core.beans.query.QueryFieldBean.subField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@NopTestConfig(enableActionAuth = "false", initDatabaseSchema = true, localDb = true)
public class TestMdxQuery extends JunitBaseTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ISqlLibManager sqlLibManager;

    void prepareData() {
        NopAuthGroup group = new NopAuthGroup();
        group.setName("g1");
        ormTemplate.save(group);
        group = new NopAuthGroup();
        group.setName("g2");
        ormTemplate.save(group);

        NopAuthGroupDept gd = new NopAuthGroupDept();
        gd.setGroupId(group.getGroupId());
        gd.setDeptId("123");
        ormTemplate.save(gd);
    }

    @Test
    public void testSubTableJoin() {
        prepareData();
        assertTrue(ormTemplate.findAll(SQL.begin().sql("select o from NopAuthGroup o").end()).size() > 0);
        QueryBean query = new QueryBean();
        query.setSourceName(NopAuthGroup.class.getName());
        query.fields(mainField("o"), mainField("groupId"), mainField("name"), subField("deptMappings", "deptId").count().alias("deptCount"));
        query.addOrderField("name", true);

        List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
        System.out.println(list);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).get("deptCount"));
        assertNull(list.get(1).get("deptCount"));
    }

    @Test
    public void testQueryInSqlLib() {
        prepareData();
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("someCondition", false);
        List<Map<String, Object>> list = (List<Map<String, Object>>) sqlLibManager.invoke("test.queryGroupWithDeptCount", null, scope);
        System.out.println(list);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).get("deptCount"));
        assertNull(list.get(1).get("deptCount"));
    }

}