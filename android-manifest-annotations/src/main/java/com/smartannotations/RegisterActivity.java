package com.smartannotations;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RegisterActivity {
    
    boolean launcherActivity() default false;
    boolean enabled() default true;
    String screenOrientation() default "unspecified"; 
    String theme() default AnnotationConstants.EXCLUDE_STRING_FIELD_IF_NOT_SET;
    boolean excludeFromRecents() default false;
    
}
