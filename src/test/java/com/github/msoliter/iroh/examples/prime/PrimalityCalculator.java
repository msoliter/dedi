package com.github.msoliter.iroh.examples.prime;

import java.util.Iterator;

import com.github.msoliter.iroh.container.annotations.Autowired;
import com.github.msoliter.iroh.container.annotations.Component;

@Component
public class PrimalityCalculator {

    @Autowired
    private LowerBoundCalculator lowerBoundCalculator;
    
    @Autowired
    private RemainderCalculator remainderCalculator;
    
    public boolean isPrime(final Long candidate) {
        Iterator<Long> dividends = new Iterator<Long>() {

            private Long current = 
                lowerBoundCalculator.calculateLowerBound(candidate);
            
            @Override
            public boolean hasNext() {
                return current > 1;
            }

            @Override
            public Long next() {
                current -= 1;
                return current + 1;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        
        while (dividends.hasNext()) {
            if (remainderCalculator.getRemainder(candidate, dividends.next()) == 0) {
                return false;
            }
        }
        
        return true;
    }
}
