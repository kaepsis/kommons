package dev.kaepsis.kommons.database.annotations;

import dev.kaepsis.kommons.database.enums.ColumnType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String value() default "";
    ColumnType type();

    int length() default 255;
    boolean nullable() default true;

    boolean decimal() default false;
    String decimalValues() default "10,2";

    boolean primaryKey() default false;
    boolean autoincrement() default false;

    String defaultValue() default "";
}
