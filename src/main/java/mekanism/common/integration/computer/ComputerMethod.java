package mekanism.common.integration.computer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ComputerMethod {

    /**
     * Name to use for the method instead of the actual internal java name.
     */
    String nameOverride() default "";
}