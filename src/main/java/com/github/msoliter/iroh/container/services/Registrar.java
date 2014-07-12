/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.msoliter.iroh.container.services;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Stack;

import com.github.msoliter.iroh.container.annotations.Autowired;
import com.github.msoliter.iroh.container.annotations.Component;
import com.github.msoliter.iroh.container.exceptions.DependencyCycleException;
import com.github.msoliter.iroh.container.exceptions.UnexpectedImplementationCountException;
import com.github.msoliter.iroh.container.resolvers.base.DependencyResolver;
import com.github.msoliter.iroh.container.sources.MethodSource;
import com.github.msoliter.iroh.container.sources.TypeSource;
import com.github.msoliter.iroh.container.sources.base.Source;

/**
 * Represents a manager for registration of component types, offering methods
 * for transforming fields into the appropriate source of instances. Internally,
 * it iterates over a constant set of dependency resolvers that implement
 * individual resolution strategies, so a registrar can also be thought of as
 * one big 
 * {@link com.github.msoliter.iroh.container.resolvers.base.DependencyResolver}
 * implementation. However, on top of this implementation, it adds a layer of 
 * error checking that transcends the scope of the dependency resolvers. 
 * Specifically, this is the class responsible for identifying dependency 
 * cycles.
 */
public class Registrar {

    /* The set of dependency resolvers used by the registrar to translate types
     * into instance sources. */
    private final DependencyResolver[] resolvers;

    /* A constant representing the fact that no implementations were found for
     * a particular type. Strictly used for exceptions, and because Effective
     * Java says to prefer using empty collections rather than null values. */
    private static final Collection<Class<?>> noImplementations = 
        Collections.emptyList();
    
    /**
     * Builds a registrar with zero or more dependency resolvers.
     * 
     * @param resolvers The resolver this registrar will use to translate types
     *  into instance sources.
     */
    public Registrar(DependencyResolver... resolvers) {
        this.resolvers = resolvers;
    }
    
    /**
     * Registers a type and the return types of any methods annotated with
     * {@link com.github.msoliter.iroh.container.annotations.Component} for 
     * dependency injection.
     * 
     * @param type The type to be registered for dependency injection, including
     *  all of properly annotated factory methods.
     */
    public void register(Class<?> type) {
        Source typeSource = register(new TypeSource(type));

        for (Method method : type.getMethods()) {
            Component annotation = method.getAnnotation(Component.class);
            
            /**
             * Technically, this check that the annotation exists should not
             * need to happen. Unfortunately, the reflections library Iroh
             * uses sometimes returns stray types generated by javac that are
             * related to the compilation process of component types, but don't
             * otherwise carry the appropriate annotations. This additional
             * check ensures that none of those types are accidentally 
             * registered. This is a known feature (bug?) in the Java 
             * compilation process.
             */
            if (annotation != null) {
                register(new MethodSource(typeSource, method));
            }
        }
        
        /**
         * Check that this type doesn't introduce a dependency cycle into Iroh's
         * resolution mechanics.
         */
        checkForCycles(type);
    }

    /**
     * Resolves a field into a
     * {@link com.github.msoliter.iroh.container.resolvers.sources.base.Source}.
     * 
     * @param field The field to be resolved.
     * @return A source of instances accepted by the field.
     */
    public Source resolve(Field field) {
        
        /**
         * As promised the registrar simply abstracts away the internal 
         * resolution implementations, throwing an error if all of them fail to
         * come up with a source for the field.
         */
        for (DependencyResolver resolver : resolvers) {
            Source source = resolver.resolve(field);
            
            /**
             * Per the dependency resolver contract, a return value of null 
             * means no error occurred, but the resolver failed to resolve the
             * type anyway, so we can move on to the next resolver.
             */
            if (source != null) {
                return source;
            }
        }
        
        /**
         * If no resolver succeeded in generating an instance source, we've got
         * a problem: we need to inject a field but we have no way to get an
         * instance of it!
         */
        throw new UnexpectedImplementationCountException(
            field.getType(), 
            noImplementations);
    }
    
    /**
     * Registers a source of instances with all the internal resolvers.
     * 
     * @param source The source to be registered.
     * @return The argument source, for chaining.
     */
    private Source register(Source source) {
        
        /**
         * As promised, the registrar simply abstracts away internal resolvers
         * by iterating over them during the registration process.
         */
        for (DependencyResolver resolver : resolvers) {
            resolver.register(source);
        }
        
        return source;
    }
    
    /**
     * Initiates a check for dependency cycles on the target type. This method
     * delegates to the correct recursive subcall to 
     * {@link Registrar#checkForCycles(Class, Class, Stack)}.
     * 
     * @param target The type from which to begin checking for dependency 
     *  cycles.
     */
    private void checkForCycles(Class<?> target) {
        Stack<Class<?>> trace = new Stack<>();
        trace.push(target);
        checkForCycles(target, target, trace);
    }
    
    /**
     * A simple recursive cycle-checker implementation that records the current
     * stack trace as an argument parameter for the purpose of producing an
     * accurate error message in the case of an error.
     * 
     * @param target The target type to be checked for cyclic dependencies. This
     *  should not change across recursive calls to the method.
     * @param in The current type, which is an Nth-level dependency for the
     *  target type. It must not contain a dependency of the target type.
     * @param trace The current stack of dependencies, starting with the target
     *  type itself.
     */
    private void checkForCycles(
        Class<?> target, 
        Class<?> in, 
        Stack<Class<?>> trace) {
        
        for (Field field : in.getDeclaredFields()) {  
            if (field.getAnnotation(Autowired.class) != null) {
                Class<?> type = field.getType();
                trace.push(type);
                
                if (type.equals(target)) {
                    throw new DependencyCycleException(trace);       
                    
                } else {
                    checkForCycles(target, type, trace);
                }
                
                trace.pop();
            }
        }    
    }
}