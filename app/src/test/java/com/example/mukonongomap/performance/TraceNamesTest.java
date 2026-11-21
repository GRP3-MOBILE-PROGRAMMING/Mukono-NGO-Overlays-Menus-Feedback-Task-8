package com.example.mukonongomap.performance;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Guards against names Firebase would reject silently. Runs in the Jenkins
 * "Unit Tests" stage, so a bad name fails the build instead of producing a
 * missing trace in the console weeks later.
 */
public class TraceNamesTest {

    @Test
    public void allNamesFollowFirebaseRules() throws Exception {
        Set<String> seen = new HashSet<>();
        int checked = 0;
        for (Field f : TraceNames.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            String value = (String) f.get(null);
            if (!seen.add(f.getName().charAt(0) + ":" + value)) fail("duplicate name " + value);
            if (f.getName().startsWith("A_")) {
                assertTrue(f.getName() + "=" + value, FakePerfTracer.ATTRIBUTE_NAME.matcher(value).matches());
            } else {
                FakePerfTracer.checkTraceOrMetricName(value);
            }
            checked++;
        }
        assertTrue("expected constants to check", checked > 20);
    }

    @Test
    public void networkValuesAreValidAttributeValues() {
        for (NetworkClass n : NetworkClass.values()) {
            assertTrue(n.value().length() <= 100 && !n.value().isEmpty());
        }
    }
}
