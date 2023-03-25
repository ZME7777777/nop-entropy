package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [6:2:0:0]/nop/schema/task/task.xdef <p>
 * 支持异步执行的轻量化任务引擎。持久化状态为可选特性，如果在步骤上配置了saveState，则可以从任意步骤中断并恢复执行。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskFlowModel extends io.nop.task.model.TaskExecutableModel {
    
    /**
     *  
     * xml name: defaultSaveState
     * 
     */
    private boolean _defaultSaveState  = false;
    
    /**
     *  
     * xml name: restartable
     * 
     */
    private boolean _restartable  = true;
    
    /**
     *  
     * xml name: version
     * 
     */
    private long _version  = 0L;
    
    /**
     * 
     * xml name: defaultSaveState
     *  
     */
    
    public boolean isDefaultSaveState(){
      return _defaultSaveState;
    }

    
    public void setDefaultSaveState(boolean value){
        checkAllowChange();
        
        this._defaultSaveState = value;
           
    }

    
    /**
     * 
     * xml name: restartable
     *  
     */
    
    public boolean isRestartable(){
      return _restartable;
    }

    
    public void setRestartable(boolean value){
        checkAllowChange();
        
        this._restartable = value;
           
    }

    
    /**
     * 
     * xml name: version
     *  
     */
    
    public long getVersion(){
      return _version;
    }

    
    public void setVersion(long value){
        checkAllowChange();
        
        this._version = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("defaultSaveState",this.isDefaultSaveState());
        out.put("restartable",this.isRestartable());
        out.put("version",this.getVersion());
    }
}
 // resume CPD analysis - CPD-ON