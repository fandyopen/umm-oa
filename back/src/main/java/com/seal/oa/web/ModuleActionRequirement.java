package com.seal.oa.web;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleActionRequirement {

    long moduleId();

    String action() default "access";
}
