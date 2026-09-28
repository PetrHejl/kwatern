package me.hejl.kwatern;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LicensesTest {

    @Test
    void includesEveryLicense() throws Exception {
        String text = Licenses.text();
        for (String expected : List.of(
                "GNU AFFERO GENERAL PUBLIC LICENSE",
                "ICU4J 78.3",
                "UNICODE LICENSE V3",
                "Apache License",
                "BSD 2-Clause License",
                "The Manrope Project Authors",
                "Copyright 2010-2020 Adobe")) {
            assertTrue(text.contains(expected), expected);
        }
        if (Licenses.files().contains("licenses/graalvm/SOURCE.txt")) {
            assertTrue(text.contains("CLASSPATH EXCEPTION"));
            assertTrue(text.contains("https://github.com/oracle/graal/tree/"));
        }
    }
}
