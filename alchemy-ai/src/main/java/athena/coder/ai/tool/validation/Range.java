package athena.coder.ai.tool.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    int min() default Integer.MIN_VALUE;

    int max() default Integer.MAX_VALUE;

    String message() default "参数值超出范围";
}