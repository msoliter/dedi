package com.github.msoliter.iroh.tests;

import org.junit.Assert;
import org.junit.Test;

import com.github.msoliter.iroh.container.annotations.Autowired;
import com.github.msoliter.iroh.container.annotations.Component;
import com.github.msoliter.iroh.container.annotations.Scope;

public class NestedResolutionTest {

    public static int count = 0;
    
    public static class Oobject extends Object {
        
    }
    
    @Component
    public static class Factory {
        
        @Component(scope = Scope.PROTOTYPE)
        public Oobject object() {
            count += 1;
            return new Oobject();
        }
    }

    public static class SuperSuper {
        
        @Autowired
        private Oobject __object;
    }
    
    public static class Super extends SuperSuper {
        
        @Autowired
        private Oobject _object;
    }
    
    public static class Sub extends Super {
         
        @Autowired
        private Oobject object;
    }
    
    @Test
    public void testNestedDependencyResolution() {
        new Sub();
        Assert.assertEquals(3, count);
    }
}
