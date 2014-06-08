package com.misha.dedi.aspects;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.reflections.Reflections;

import com.misha.dedi.annotations.Autowired;
import com.misha.dedi.annotations.Prototype;
import com.misha.dedi.annotations.Qualifier;
import com.misha.dedi.exceptions.NoSuchQualifierException;
import com.misha.dedi.exceptions.NoZeroArgumentConstructorException;
import com.misha.dedi.exceptions.UnexpectedImplementationCountException;

@Aspect
public class AutowiringAspect {
    
    private final Set<SourceLocation> injected = new HashSet<>();
    
    private final Map<Class<?>, Object> instances = new HashMap<>();
    
    private final Map<String, Class<?>> qualified = new HashMap<>();
    
    private final Map<Class<?>, Class<?>> implementations = new HashMap<>();
    
    private final Reflections reflections = new Reflections("");
    
    private final Logger log = Logger.getLogger("dedi");
    
    public AutowiringAspect() {
        log.info("Initializing the autowiring aspect.");
        
        /**
         * Initialize the set of all qualified types.
         */
        Set<Class<?>> qualifiedTypes = 
            reflections.getTypesAnnotatedWith(Qualifier.class);
        
        for (Class<?> type : qualifiedTypes) {
            Qualifier annotation = type.getAnnotation(Qualifier.class);
            qualified.put(annotation.value(), type);
            log.info("Registered qualifier \"" + annotation.value() + "\" for " + type);
        }
    }
                
    @Pointcut(
        "get(@com.misha.dedi.annotations.Autowired * *) && " +
        "@annotation(annotation)")
    public void autowired(Autowired annotation) { 
        
    }

    @Around("autowired(annotation)")
    public Object resolve(
        Autowired annotation, 
        ProceedingJoinPoint thisJoinPoint) 
        throws Throwable {
        
        /**
         * Don't inject the same field twice, ever.
         */
        if (!injected.contains(thisJoinPoint.getSourceLocation())) {
            injected.add(thisJoinPoint.getSourceLocation());

            /**
             * Figure out which field needs to get injected. If we allow the
             * autowired annotation on things other than fields, then this
             * cast will have to be refactored into a lookup.
             */
            FieldSignature fs = (FieldSignature) thisJoinPoint.getSignature();
            Field field = fs.getField();
            
            /**
             * Change the field's accessibility so that no access exceptions
             * are thrown when we inject the actual value.
             */
            boolean accessibility = field.isAccessible();
            field.setAccessible(true);
            
            /**
             * Get the target field instance, and the type of object that needs
             * to be injected into the field instance.
             */
            Object target = thisJoinPoint.getTarget();
            
            /**
             * Figure out the correct type to use for injection, in case any
             * qualifiers were triggered during static initialization.
             */
            Class<?> type = null;
            
            if (annotation.value().equals("")) {
                type = field.getType();
                
            } else {
                type = qualified.get(annotation.value());
                
                /**
                 * Validate that we got an actual type from the qualified list.
                 */
                if (type == null) {
                    throw new NoSuchQualifierException(annotation.value());
                }
            }
            
            /**
             * However, even if the type is defined by the qualifier, it still
             * might be abstract. In general, the type might be abstract anyway
             * just to hide the implementation details. Resolve the abstract
             * or interface type into an actual implementation.
             */
            if (Modifier.isInterface(type.getModifiers()) || 
                Modifier.isAbstract(type.getModifiers())) {
                
                /**
                 * Finding the implementing type is expensive, so let's check
                 * our implementations cache first.
                 */
                if (implementations.containsKey(type)) {
                    type = implementations.get(type);
                    
                } else {
                    
                    /**
                     * Find all the subtypes of the given type. This might 
                     * take a while...
                     */
                    @SuppressWarnings("unchecked")
                    Set<Class<? extends Object>> subtypes = 
                        reflections.getSubTypesOf((Class<Object>) type);
                    
                    /**
                     * There must be exactly one concrete subtype at this point.
                     */
                    Iterator<Class<?>> subtypesIterator = subtypes.iterator();
                    
                    while (subtypesIterator.hasNext()) {
                        int modifiers = subtypesIterator.next().getModifiers();
                        
                        /**
                         * Not a concrete type, so throw it out.
                         */
                        if (Modifier.isAbstract(modifiers) ||
                            Modifier.isInterface(modifiers)) {
                            
                            subtypesIterator.remove();
                        }
                    }

                    /**
                     * Now our set's size is actually valid - all concrete types.
                     */
                    if (subtypes.size() != 1) {
                        throw new UnexpectedImplementationCountException(type, subtypes.size());
                    }
                    
                    /**
                     * Cache it for later.
                     */
                    Class<?> implementation = subtypes.iterator().next();
                    log.info("Resolved abstract type " + type + " into implementing type " + implementation);
                    implementations.put(type, implementation);
                    type = implementation;
                }
            }

            /**
             * Inject a new instance for the field, checking for the prototype
             * annotation as necessary. The default scoping policy is singleton.
             */
            try {
                if (type.getAnnotation(Prototype.class) != null) {
                    log.info("Instantiating prototype for " + type);
                    field.set(target, type.getConstructor().newInstance());
                    
                } else {
                    if (!instances.containsKey(type)) {
                        log.info("Instantiating singleton for " + type);
                        instances.put(type, type.getConstructor().newInstance());
                    }
                    
                    field.set(target, instances.get(type));
                }

            } catch (NoSuchMethodException e) {
                throw new NoZeroArgumentConstructorException(type);
            }

            /**
             * Reset accessibility to avoid missing future access exceptions.
             */
            field.setAccessible(accessibility); 
        }

        return thisJoinPoint.proceed();
    }   
}