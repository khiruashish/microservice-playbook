package org.springframework.lang; import java.lang.annotation.*; @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) public @interface Contract { String value() default ""; }
