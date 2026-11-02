package com.mg.llm.autoconfigure;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求「属性存在且非空白」才装配，与 Boot 原生
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} 的区别：
 * 后者在属性为空字符串（例如 {@code ${VAR:}} 缺省）时也会命中，导致没配 key 也创建 client。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnNonBlankPropertyCondition.class)
public @interface ConditionalOnNonBlankProperty {

    /**
     * 需要全部非空白的属性名。
     */
    String[] value();
}
