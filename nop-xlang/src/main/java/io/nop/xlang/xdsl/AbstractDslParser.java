/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.IComponentModel;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.impl.DefaultClassResolver;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.core.type.IRawTypeResolver;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xpl.tags.ImportTagCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT;

public abstract class AbstractDslParser<T extends IComponentModel> extends AbstractResourceParser<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractDslParser.class);

    private IXDslNodeLoader modelLoader = DslNodeLoader.INSTANCE;
    protected XLangCompileTool compileTool;
    private IRawTypeResolver rawTypeResolver = DefaultClassResolver.INSTANCE;
    private boolean intern;
    private List<ImportAsDeclaration> importExprs;
    private IXDefinition xdef;
    private String requiredSchema;

    public String getRequiredSchema() {
        return requiredSchema;
    }

    public void setRequiredSchema(String requiredSchema) {
        this.requiredSchema = requiredSchema;
    }

    public void setModelLoader(IXDslNodeLoader modelLoader) {
        this.modelLoader = modelLoader;
    }

    public void setCompileTool(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    public void setRawTypeResolver(IRawTypeResolver rawTypeResolver) {
        this.rawTypeResolver = rawTypeResolver;
    }

    public IXDslNodeLoader getModelLoader() {
        return modelLoader;
    }

    public XLangCompileTool getCompileTool() {
        return compileTool;
    }

    public IRawTypeResolver getRawTypeResolver() {
        return rawTypeResolver;
    }

    public List<ImportAsDeclaration> getImportExprs() {
        return importExprs;
    }

    public void setImportExprs(List<ImportAsDeclaration> importExprs) {
        this.importExprs = importExprs;
    }

    public boolean isIntern() {
        return intern;
    }

    public void setIntern(boolean intern) {
        this.intern = intern;
    }

    protected String intern(String str) {
        if (str != null)
            str = str.intern();
        return str;
    }

    public IXDefinition getXdef() {
        return xdef;
    }

    public void setXdef(IXDefinition xdef) {
        this.xdef = xdef;
    }

    @Override
    protected T doParseResource(IResource resource) {
        XDslExtendResult extendResult = modelLoader.loadFromResource(resource, getRequiredSchema(),
                XDslExtendPhase.validate);
        compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        setXdef(extendResult.getXdef());

        applyCompileConfig(extendResult.getConfig());
        T parseResult = doParseNode(extendResult.getNode());

        parseResult = runPostParse(parseResult, extendResult);
        return parseResult;
    }

    protected void applyCompileConfig(XNode config) {
        if (config == null)
            return;

        if (config.hasContent())
            throw new NopException(ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT).param(ARG_NODE, config);

        importExprs = new ArrayList<>();
        for (XNode child : config.getChildren()) {
            if (!child.getTagName().equals(XLangConstants.TAG_C_IMPORT))
                throw new NopException(ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT).param(ARG_NODE, config);

            ImportAsDeclaration expr = ImportTagCompiler.INSTANCE.parseTag(child, compileTool.getCompiler(),
                    compileTool.getScope());
            if (expr != null) {
                importExprs.add(expr);
            }
        }

        String configText = config.innerXml();
        compileTool.setConfigText(configText);
    }

    protected T runPostParse(T parseResult, XDslExtendResult extendResult) {

        if (getXdef().getXdefPostParse() != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, XLangConstants.SYS_VAR_DSL_MODEL, parseResult);
            Object ret = getXdef().getXdefPostParse().invoke(scope);
            if (ret != null)
                parseResult = (T) ret;
        }

        if (extendResult.getPostParse() != null) {
            IEvalAction postParse = compileTool.compileTagBody(extendResult.getPostExtends());
            if (postParse != null) {
                IEvalScope scope = XLang.newEvalScope();
                scope.setLocalValue(null, XLangConstants.SYS_VAR_DSL_MODEL, parseResult);
                Object ret = postParse.invoke(scope);
                if (ret != null)
                    parseResult = (T) ret;
            }
        }

        return parseResult;
    }

    public T parseFromNode(XNode node) {
        LOG.debug("nop.core.component.parse-from-node:node={},parser={}", node, getClass());

        long beginTime = CoreMetrics.nanoTime();
        try {
            if (shouldTraceDepends()) {
                T ret = ResourceComponentManager.instance().collectDepends(node.resourcePath(),
                        () -> parseFromNode0(node));
                return ret;
            } else {
                return parseFromNode0(node);
            }
        } catch (NopException e) {
            e.addXplStack(getClass().getSimpleName() + ".parseFromNode(" + node + ")");
            throw e;
        } finally {
            long diff = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.debug("nop.core.component.parse-use-time:tm={}ms,node={},parser={}", CoreMetrics.nanoToMillis(diff),
                    node, getClass());
        }
    }

    protected T parseFromNode0(XNode node) {
        XDslExtendResult extendResult = modelLoader.loadFromNode(node.cloneInstance(), getRequiredSchema(),
                XDslExtendPhase.validate);
        setXdef(extendResult.getXdef());
        compileTool = XLang.newCompileTool();

        applyCompileConfig(extendResult.getConfig());
        T parseResult = doParseNode(extendResult.getNode());

        parseResult = runPostParse(parseResult, extendResult);
        return parseResult;
    }

    protected abstract T doParseNode(XNode node);
}