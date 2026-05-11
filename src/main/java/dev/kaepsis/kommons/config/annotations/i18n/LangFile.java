package dev.kaepsis.kommons.config.annotations.i18n;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LangFile {

    String baseName();
    String defaultLocale() default "en";
    int version() default 1;

}