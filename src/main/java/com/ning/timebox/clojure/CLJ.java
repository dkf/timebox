package com.ning.timebox.clojure;

import com.ning.timebox.GuardAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@GuardAnnotation(ClojurePredicator.class)
public @interface CLJ
{
    String value();
}
