/* Copyright 2014 Misha Soliterman
 * 
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
package com.github.msoliter.iroh.container.resolvers.base;

import java.lang.reflect.Field;

import com.github.msoliter.iroh.container.sources.base.Source;

/**
 * Represents a type capable of resolving fields into sources. The resolver is
 * always informed ahead of time of all the sources in the system.
 */
public interface Resolver {
    
    /**
     * Register a source with this resolver.
     * 
     * @param source The source to register.
     */
    public void register(Source source);

    /**
     * Resolve a field into a source using this resolver's strategy.
     * 
     * @param field The field to be resolved.
     * @return The resolved source, or null if the resolver was unable to find
     *  an acceptable source for the given field.
     */
    public Source resolve(Field field);
}
