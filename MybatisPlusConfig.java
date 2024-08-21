package com.dekun.kms.system.management.config.mybatisplus;

import java.util.List;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.extension.injector.methods.AlwaysUpdateSomeColumnById;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.baomidou.mybatisplus.extension.injector.methods.LogicDeleteByIdWithFill;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.dekun.common.core.mybatisplus.method.InsertAll;
import com.dekun.common.core.mybatisplus.method.MysqlInsertOrUpdateBath;
import com.dekun.common.core.mybatisplus.method.UpdateBatch;
import com.dekun.framework.mybatisplus.CreateAndUpdateMetaObjectHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;


/**
 * @see com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration
 */
@EnableTransactionManagement(
        proxyTargetClass = true
)
@Configuration
@MapperScan({"${mybatis-plus.mapperPackage}"})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(this.paginationInnerInterceptor());
        interceptor.addInnerInterceptor(this.optimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(-1L);
        return paginationInnerInterceptor;
    }

    @Bean
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new CreateAndUpdateMetaObjectHandler();
    }

    /**
     * 记录增删改的sql语句、参数等信息的插件
     */
    @Bean
    public MyBatisPlugin myBatisPlugin() {
        return new MyBatisPlugin();
    }

    @Bean
    @Primary
    public DefaultSqlInjector sqlInjector() {
        return new DefaultSqlInjector() {
            @Override
            public List<AbstractMethod> getMethodList(Class<?> mapperClass) {
                List<AbstractMethod> methodList = super.getMethodList(mapperClass);
                methodList.add(new InsertBatchSomeColumn());
                methodList.add(new AlwaysUpdateSomeColumnById());
                methodList.add(new LogicDeleteByIdWithFill());
                methodList.add(new InsertAll());
                methodList.add(new UpdateBatch());
                methodList.add(new MysqlInsertOrUpdateBath());
                methodList.add(new TableAllColumnConditionSqlFragment());
                return methodList;
            }
        };
    }
}