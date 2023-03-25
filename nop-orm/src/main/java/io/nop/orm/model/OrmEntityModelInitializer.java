/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.StdSqlType;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.OrmConstants;
import io.nop.orm.exceptions.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_COL_CODE;
import static io.nop.orm.OrmErrors.ARG_COL_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_OTHER_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_ID;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_REF_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ALIAS_MUST_REF_TO_COLUMN_OR_REFERENCE;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_MODEL_NO_PK;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_DUPLICATE_COL_CODE;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_DUPLICATE_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_DUPLICATE_PROP_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_INVALID_PROP_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_REF_JOIN_NO_CONDITION;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_RELATION_JOIN_IS_EMPTY;
import static io.nop.orm.OrmErrors.ERR_ORM_PROP_ID_IS_RESERVED;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_COLUMN;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_PROP;

public class OrmEntityModelInitializer {
    private final OrmEntityModel entityModel;

    MutableIntArray eagerLoadProps;
    MutableIntArray allPropIds;
    MutableIntArray minLazyLoadProps;
    IEntityPropModel idProp;
    List<OrmColumnModel> pkColumns;
    String[] pkColumnNames;

    int maxPropId;
    OrmColumnModel[] colsByPropId;

    int shardPropId;
    int versionPropId;
    int tenantPropId;

    int nopRevTypePropId;
    int nopRevBeginVerPropId;
    int nopRevEndVerPropId;
    int nopRevExtChangePropId;

    int nopFlowIdPropId;
    int nopFlowStatusPropId;

    int deleteFlagPropId;
    int createrPropId;
    int createTimePropId;
    int updaterPropId;
    int updateTimePropId;

    Map<String, IEntityPropModel> props = new HashMap<>();
    Map<String, OrmColumnModel> colsByCode = new CaseInsensitiveMap<>();

    boolean containsTenantIdInPk;

    public OrmEntityModelInitializer(OrmEntityModel entityModel) {
        this.entityModel = entityModel;
        this.entityModel.setName(entityModel.getName().intern());
        this.entityModel.setTableName(entityModel.getTableName().intern());

        this.entityModel.setQuerySpace(DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace()));

        if (entityModel.getClassName() == null)
            entityModel.setClassName(entityModel.getName());

        initPropIds();
        initColMap();
        addInternalProps();
        initProps();
        initAliases();
        initComputes();
        initComponents();
        initIdProp();
        initRelations();
        checkPropNames();

        if (tenantPropId > 0) {
            this.containsTenantIdInPk = this.pkColumns.contains(colsByPropId[tenantPropId]);
        }
    }

    void initIdProp() {
        if (pkColumns.isEmpty()) {
            throw new OrmException(ERR_ORM_ENTITY_MODEL_NO_PK).source(entityModel).param(ARG_ENTITY_NAME,
                    entityModel.getName());
        }

        if (pkColumns.size() == 1) {
            idProp = pkColumns.get(0);
        } else {
            idProp = new OrmCompositePKModel(entityModel, pkColumns);
        }

        IEntityPropModel prop = props.put(OrmConstants.PROP_ID, idProp);
        if (prop != null && prop != idProp) {
            throw new OrmException(ERR_ORM_PROP_ID_IS_RESERVED).source(prop).param(ARG_ENTITY_NAME,
                    entityModel.getName());
        }
    }

    /**
     * 为每一列分配一个唯一的propId。如果配置文件中已经指定了propId，则以指定的值为准。
     */
    private void initPropIds() {
        IntHashMap<OrmColumnModel> cols = new IntHashMap<>();
        int maxPropId = 0;

        for (OrmColumnModel col : entityModel.getColumns()) {
            col.setOwnerEntityModel(entityModel);
            col.setName(col.getName().intern());
            col.setCode(col.getCode().intern());

            int propId = col.getPropId();
            if (propId <= 0 || propId >= OrmConstants.MAX_PROP_ID)
                throw new OrmException(ERR_ORM_MODEL_INVALID_PROP_ID).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_PROP_ID, propId);

            OrmColumnModel old = cols.put(propId, col);
            if (old != null)
                throw new OrmException(ERR_ORM_MODEL_DUPLICATE_PROP_ID).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_OTHER_PROP_NAME, old.getName()).param(ARG_PROP_ID, propId);
            maxPropId = Math.max(maxPropId, propId);
        }

        Collections.sort(entityModel.getColumns(), Comparator.comparing(OrmColumnModel::getPropId));

        this.maxPropId = maxPropId;
    }

    void initColMap() {
        for (OrmColumnModel col : entityModel.getColumns()) {
            props.put(col.getName(), col);
            addToColByCode(col);
        }
    }

    private void addToPropMap(IEntityPropModel prop) {
        IEntityPropModel old = props.put(prop.getName(), prop);
        if (old != null) {
            throw new OrmException(ERR_ORM_MODEL_DUPLICATE_PROP).source(prop)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop.getName());
        }
    }

    private void addToColByCode(OrmColumnModel col) {
        OrmColumnModel old = colsByCode.put(col.getCode(), col);
        if (old != null) {
            throw new OrmException(ERR_ORM_MODEL_DUPLICATE_COL_CODE).source(col)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                    .param(ARG_OTHER_PROP_NAME, old.getName()).param(ARG_COL_CODE, col.getCode());
        }
    }

    private void addInternalProps() {
        if (entityModel.isUseTenant()) {
            IEntityPropModel col;
            String prop = entityModel.getTenantProp();
            if (prop != null) {
                col = entityModel.getColumn(prop);
                if (col == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop);
            } else {
                col = addColumn(OrmConstants.PROP_NAME_nopTenant, StdSqlType.VARCHAR, 32);
            }
            tenantPropId = col.getColumnPropId();
        }

        if (entityModel.isUseRevision()) {
            nopRevTypePropId = addColumn(OrmConstants.PROP_NAME_nopRevType, StdSqlType.TINYINT, null).getColumnPropId();
            nopRevBeginVerPropId = addColumn(OrmConstants.PROP_NAME_nopRevBeginVer, StdSqlType.BIGINT, null)
                    .getColumnPropId();
            nopRevEndVerPropId = addColumn(OrmConstants.PROP_NAME_nopRevEndVer, StdSqlType.BIGINT, null)
                    .getColumnPropId();
            // 一般不需要使用这个属性，除非在模型中主动添加
            nopRevExtChangePropId = getColPropId(OrmConstants.PROP_NAME_nopRevExtChange);

            // beginVer必须是主键的一部分，否则增加新版本的时候会出现主键冲突
            entityModel.getColumn(OrmConstants.PROP_NAME_nopRevBeginVer).setPrimary(true);
        }

        if (entityModel.isUseShard()) {
            IEntityPropModel col;
            String prop = entityModel.getShardProp();
            if (prop != null) {
                col = entityModel.getColumn(prop);
                if (col == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop);
            } else {
                col = addColumn(OrmConstants.PROP_NAME_nopShard, StdSqlType.VARCHAR, 32);
            }
            shardPropId = col.getColumnPropId();
        }

        if (entityModel.isUseWorkflow()) {
            nopFlowIdPropId = addColumn(OrmConstants.PROP_NAME_nopFlowId, StdSqlType.VARCHAR, 32).getColumnPropId();
            nopFlowStatusPropId = addColumn(OrmConstants.PROP_NAME_nopFlowStatus, StdSqlType.INTEGER, null)
                    .getColumnPropId();
        }

        this.versionPropId = getColPropId(entityModel.getVersionProp());

        this.createrPropId = getColPropId(entityModel.getCreaterProp());
        this.createTimePropId = getColPropId(entityModel.getCreateTimeProp());
        this.updaterPropId = getColPropId(entityModel.getUpdaterProp());
        this.updateTimePropId = getColPropId(entityModel.getUpdateTimeProp());
        this.deleteFlagPropId = getColPropId(entityModel.getDeleteFlagProp());

        if (this.deleteFlagPropId <= 0)
            entityModel.setUseLogicalDelete(false);
    }

    private int getColPropId(String propName) {
        if (propName == null)
            return 0;

        OrmColumnModel col = entityModel.getColumn(propName);
        if (col == null)
            throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, propName);
        return col.getPropId();
    }

    private IEntityPropModel addColumn(String colName, StdSqlType sqlType, Integer precision) {
        if (entityModel.hasColumn(colName))
            return entityModel.getColumn(colName);

        OrmColumnModel col = new OrmColumnModel();
        col.setName(colName);
        col.setPropId(++maxPropId);
        col.setCode(StringHelper.camelCaseToUnderscore(colName, false));
        col.setStdSqlType(sqlType);
        col.setStdDataType(sqlType.getStdDataType());
        col.setPrecision(precision);
        entityModel.addColumn(col);
        colsByCode.put(col.getCode(), col);
        props.put(col.getName(), col);
        return col;
    }

    private void initProps() {
        colsByPropId = new OrmColumnModel[maxPropId + 1];
        pkColumns = new ArrayList<>();

        eagerLoadProps = new MutableIntArray(entityModel.getColumns().size());
        allPropIds = new MutableIntArray(entityModel.getColumns().size());

        minLazyLoadProps = new MutableIntArray();

        for (OrmColumnModel col : entityModel.getColumns()) {
            colsByPropId[col.getColumnPropId()] = col;
            if (col.isPrimary()) {
                pkColumns.add(col);
                minLazyLoadProps.add(col.getPropId());
                eagerLoadProps.add(col.getPropId());
                allPropIds.add(col.getPropId());
            }

            if (col.getDefaultValue() == null) {
                if (OrmConstants.DOMAIN_BOOL_FLAG.equals(col.getBaseDomain())) {
                    col.setDefaultValue("0");
                }
            }
        }

        // 先增加主键列再增加非主键列，确保主键列总是排在最前面，且顺序与pkColumns集合中的顺序一致
        for (OrmColumnModel col : entityModel.getColumns()) {
            if (!col.isPrimary()) {
                if (!col.isLazy()) {
                    eagerLoadProps.add(col.getPropId());
                }
                allPropIds.add(col.getPropId());
            }
        }

        this.pkColumnNames = buildPkNames();

        if (entityModel.isUseTenant()) {
            minLazyLoadProps.add(tenantPropId);
        }

        if (entityModel.isUseShard()) {
            minLazyLoadProps.add(shardPropId);
        }

        if (entityModel.getVersionProp() != null) {
            minLazyLoadProps.add(versionPropId);
        }

        if (this.eagerLoadProps.size() == this.allPropIds.size())
            this.eagerLoadProps = this.allPropIds;
    }

    String[] buildPkNames() {
        String[] ret = new String[pkColumns.size()];
        for (int i = 0, n = ret.length; i < n; i++) {
            ret[i] = pkColumns.get(i).getName().intern();
        }
        return ret;
    }

    void initAliases() {
        List<OrmAliasModel> aliases = entityModel.getAliases();
        for (OrmAliasModel alias : aliases) {
            alias.setOwnerEntityModel(entityModel);
            alias.setName(alias.getName().intern());
            addToPropMap(alias);
        }
    }

    void initComputes() {
        for (OrmComputePropModel compute : entityModel.getComputes()) {
            compute.setOwnerEntityModel(entityModel);
            compute.setName(compute.getName().intern());
            addToPropMap(compute);
        }
    }

    void initRelations() {
        for (OrmReferenceModel ref : entityModel.getRelations()) {
            ref.setOwnerEntityModel(entityModel);
            ref.setName(ref.getName().intern());

            if (ref.getJoin() == null)
                throw new NopException(ERR_ORM_MODEL_REF_JOIN_NO_CONDITION).source(ref)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());

            if (ref.getJoin().isEmpty())
                throw new OrmException(ERR_ORM_MODEL_RELATION_JOIN_IS_EMPTY).source(ref)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
            addToPropMap(ref);
            checkRef(ref);

            if (ref.isToOneRelation()) {
                // 如果关联字段包含主键字段，则是1对1关联
                OrmToOneReferenceModel toOne = (OrmToOneReferenceModel) ref;
                if (ref.getColumns().containsAll(pkColumns)) {
                    toOne.setOneToOne(true);
                }
            }
        }
    }

    void initComponents() {
        for (OrmComponentModel comp : entityModel.getComponents()) {
            comp.setOwnerEntityModel(entityModel);
            comp.setName(comp.getName().intern());
            addToPropMap(comp);
        }
    }

    /**
     * 验证所有列名引用正确，并根据列名解析得到对应的ColumnModel对象并设置到对象上作为缓存。
     */
    void checkPropNames() {
        for (OrmAliasModel alias : entityModel.getAliases()) {
            String propPath = alias.getPropPath();
            String name = StringHelper.firstPart(propPath, '.');
            IEntityPropModel prop = props.get(name);
            if (prop == null)
                throw new OrmException(ERR_ORM_UNKNOWN_PROP).source(alias).param(ARG_ENTITY_NAME, entityModel.getName())
                        .param(ARG_PROP_NAME, name);

            if (!prop.getKind().isColumn() && !prop.getKind().isRelation() && !prop.getKind().isComponent())
                throw new OrmException(ERR_ORM_ALIAS_MUST_REF_TO_COLUMN_OR_REFERENCE).source(alias)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, name);
        }

        for (OrmComponentModel component : entityModel.getComponents()) {
            for (OrmComponentPropModel prop : component.getProps()) {
                String colName = prop.getColumn();
                prop.setColumn(colName.intern());
                prop.setName(prop.getName().intern());

                OrmColumnModel colModel = entityModel.getColumn(colName);
                if (colModel == null) {
                    colModel = colsByCode.get(colName);
                    if (colModel != null) {
                        prop.setColumn(colName);
                    }
                }
                if (colModel == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(prop)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, colName);

                prop.setColumnModel(colModel);
            }
        }

        for (OrmUniqueKeyModel keyModel : entityModel.getUniqueKeys()) {
            List<String> columns = keyModel.getColumns();
            List<OrmColumnModel> cols = new ArrayList<>(columns.size());

            for (int i = 0, n = columns.size(); i < n; i++) {
                String column = columns.get(i);
                OrmColumnModel col = entityModel.getColumn(column);
                if (col == null) {
                    col = colsByCode.get(column);
                    if (col != null) {
                        columns.set(i, col.getName());
                    }
                }
                if (col == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(keyModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, column);

                cols.add(col);
            }
            keyModel.setColumnModels(cols);
        }

        for (OrmIndexModel indexModel : entityModel.getIndexes()) {
            for (OrmIndexColumnModel indexCol : indexModel.getColumns()) {
                String name = indexCol.getName();
                OrmColumnModel col = entityModel.getColumn(name);
                if (col == null) {
                    col = colsByCode.get(name);
                    if (col != null) {
                        indexCol.setName(col.getName());
                    }
                }
                if (col == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_COLUMN).source(indexCol)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, name);
                indexCol.setColumnModel(col);
            }
        }
    }

    void checkRef(OrmReferenceModel ref) {
        List<OrmColumnModel> cols = new ArrayList<>();

        for (OrmJoinOnModel join : ref.getJoin()) {
            // 只检查leftProp。rightProp的检查需要在OrmModel的init函数中进行。
            String leftProp = join.getLeftProp();
            if (leftProp != null) {
                join.setLeftProp(leftProp.intern());

                IEntityPropModel propModel = props.get(leftProp);
                if (propModel == null) {
                    propModel = colsByCode.get(leftProp);
                    if (propModel != null) {
                        join.setLeftProp(propModel.getName());
                    }
                }
                if (propModel == null)
                    throw new OrmException(ERR_ORM_UNKNOWN_PROP).source(ref)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, leftProp);

                if (!propModel.getKind().isColumn() && !propModel.getKind().isId())
                    throw new OrmException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());

                cols.addAll((List) propModel.getColumns());

                join.setLeftPropModel(propModel);
                if (join.getRightProp() == null) {
                    Object rightValue = join.getRightValue();
                    rightValue = propModel.getStdDataType().convert(rightValue, err -> {
                        return new OrmException(err).source(ref).param(ARG_ENTITY_NAME, entityModel.getName())
                                .param(ARG_PROP_NAME, leftProp);
                    });
                    join.setRightValue(rightValue);
                }
            }

            if (join.getLeftProp() == null && join.getRightProp() == null)
                throw new OrmException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
        }

        if (ref.getJoin().size() == 1) {
            OrmJoinOnModel join = ref.getJoin().get(0);
            if (join.getLeftProp() == null || join.getRightProp() == null)
                throw new OrmException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
            ref.setSingleColumnJoin(join);
        }
        ref.setColumns(cols);
    }
}