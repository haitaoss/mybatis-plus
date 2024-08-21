package com.dekun.kms.system.management.config.mybatisplus;

import static java.util.stream.Collectors.joining;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;

/**
 * 生成所有 mapper 的 table 中所有列的sql片段
 * <pre class="code">
 * @TableName("user_table")
 * public class UserTableEntity {
 *     @TableId
 *     private Long id;
 *
 *     @TableField("user_name")
 *     private String userName;
 *
 *     @TableField("pwd")
 *     @AutoGenerateColumnConditionContext(needGenerate=false)
 *     private String pwd;
 * }
 *
 * public interface UserMapper extends com.baomidou.mybatisplus.core.mapper.Mapper<UserTableEntity> {
 *      List<UserTableEntity> list(Map query);
 * }
 *
 * UserMapper.xml
 *      <?xml version="1.0" encoding="UTF-8" ?>
 *      <mapper namespace="UserMapper">
 *          <select id="list" resultType="UserTableEntity">
 *              select *
 *              from user_table user_table
 *              where 1 = 1
 *              <include refid="all_column_condition"/>
 *          </select>
 *      </mapper>
 *
 * </pre>
 * 说明：
 * 最终生成的 all_column_condition 是放在内存的，生成的语句如下
 * <pre class="code">
 *      <sql id="all_column_condition">
 *         <if test="param1.id != null and param1.id != ''">
 *             AND user_table.id ${param1.id}
 *         </if>
 *         <if test="param1.userName != null and param1.userName != ''">
 *             AND user_table.user_name ${param1.userName}
 *         </if>
 *     </sql>
 * </pre>
 * <p>
 * 1. @AutoGenerateColumnConditionContext(needGenerate=false)作用：all_column_condition 的内容不包含该字段的 if 标签
 * 2. 表的别名必须是 @TableName("USER_TABLE") 的小写：like " select * from USER_TABLE user_table "
 * 3. 需要手动写 where 1=1 然后在引用 <include refid="all_column_condition"/>
 * 4. 参数必须放在接口的第一个参数，因为固定取得是 param1
 * 5. 如果参数的类型不是 Map 类型，必须把表所有的字段都写到 Vo 中，否则会出现找不到字段的错误（看 ObjectPropertyAccessor ）
 *
 * @author Chen Haitao
 */
public class TableAllColumnConditionSqlFragment extends AbstractMethod {

    /**
     * mybatis xml sql 标签的ID
     */
    private final String sqlId = "all_column_condition";
    /**
     * 参数名(param1 是 mybatis 默认设置的参数名)
     *
     * @see org.apache.ibatis.reflection.ParamNameResolver#getNamedParams(Object[])
     */
    private final String prefix = "param1";

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 设置一个 1=1 的条件，为了便于观察是否植入了这段代码
        String sqlFlag = String.format(" and '%s' = '%s'", sqlId, sqlId);
        // tableInfo 全部字段的 and 条件
        String allSqlWhere = getAllColumnCondition(tableInfo);
        String script = String.format("<sql id=\"%s\"> %s %s </sql>", sqlId, sqlFlag, allSqlWhere);

        // 解析成 XNode
        XPathParser parser = new XPathParser(script, false, configuration.getVariables());
        XNode sqlNode = parser.evalNode("/sql");

        // 存到 mybatis 的 configuration 中
        String id = builderAssistant.applyCurrentNamespace(sqlId, false);
        builderAssistant.getConfiguration()
                .getSqlFragments()
                .put(id, sqlNode);

        return null;
    }

    public String getAllColumnCondition(TableInfo tableInfo) {
        boolean ignoreLogicDelFiled = true;
        List<TableColumInfo> tableColumInfos = tableInfo.getFieldList()
                .stream()
                .filter(tableFieldInfo -> {
                    // 通过这个注解控制是否生成 条件 sql。没有设置注解 或者 注解的needGenerate值是true 才会生成
                    AutoGenerateColumnConditionContext annotation = tableFieldInfo.getField().getAnnotation(AutoGenerateColumnConditionContext.class);
                    return Optional.ofNullable(annotation)
                            .map(AutoGenerateColumnConditionContext::needGenerate)
                            .orElse(true);
                })
                .map(TableColumInfo::new)
                .collect(Collectors.toList());
        tableColumInfos.add(new TableColumInfo(tableInfo));

        String filedSqlScript = tableColumInfos
                .stream()
                .peek(item -> item.setParamPrefix(prefix))
                .peek(item -> item.setTableName(tableInfo.getTableName()))
                .map(this::getIfFragment)
                .filter(Objects::nonNull)
                .collect(joining(NEWLINE));

        return filedSqlScript;
    }

    public String getIfFragment(final TableColumInfo tableColumInfo) {
        // eg: tableName=user_table, paramPrefix=param1, property=userName, column=user_name
        final String tableName = tableColumInfo.getTableName();
        final String paramPrefix = tableColumInfo.getParamPrefix();
        final String property = tableColumInfo.getProperty();
        final String column = tableColumInfo.getColumn();

        // user_table.userName
        String leftExpression = String.format("%s.%s", tableName, column);
        // param1.userName
        String rightExpression = String.format("%s['%s']", paramPrefix, property);

        // AND user_name ${param1.userName}
        String sqlScript = " AND " + String.format("%s ${%s}", leftExpression, rightExpression);

        // <if test="param1.userName != null and param1.userName != ''"> AND user_name ${param1.userName} </if>
        return SqlScriptUtils.convertIf(
                sqlScript,
                String.format("%s != null and %s != ''", rightExpression, rightExpression),
                false
        );
    }

    @Getter
    @Setter
    public static class TableColumInfo {
        private final String column;
        private final String property;
        private String paramPrefix;
        private String tableName;

        public TableColumInfo(TableFieldInfo tableFieldInfo) {
            this.column = tableFieldInfo.getColumn();
            this.property = tableFieldInfo.getProperty();
        }

        public TableColumInfo(TableInfo tableInfo) {
            this.column = tableInfo.getKeyColumn();
            this.property = tableInfo.getKeyProperty();
        }

        public void setTableName(final String tableName) {
            this.tableName = tableName.toLowerCase();
        }
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AutoGenerateColumnConditionContext {
        boolean needGenerate() default true;
    }
}