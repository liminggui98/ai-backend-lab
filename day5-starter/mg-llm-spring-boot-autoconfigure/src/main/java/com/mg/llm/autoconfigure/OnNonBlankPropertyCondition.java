package com.mg.llm.autoconfigure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * {@link ConditionalOnNonBlankProperty} 的判定逻辑：所列属性全部存在且非空白才命中。
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
class OnNonBlankPropertyCondition implements Condition {

    /**
     * 判定所列属性是否全部存在且非空白。
     *
     * @return 全部存在且非空白时返回 {@code true}
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnNonBlankProperty.class.getName());
        if (attrs == null) {
            return false;
        }
        String[] names = (String[]) attrs.get("value");
        if (names == null) {
            return false;
        }
        for (String name : names) {
            String value = context.getEnvironment().getProperty(name);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
