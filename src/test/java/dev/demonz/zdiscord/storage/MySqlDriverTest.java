package dev.demonz.zdiscord.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlDriverTest {

    @Test
    void bundledDriverRecognizesMySqlUrls() throws ReflectiveOperationException, SQLException {
        java.sql.Driver driver = loadBundledDriver();

        assertTrue(driver.acceptsURL("jdbc:mysql://localhost:3306/zdiscord"));
        assertFalse(driver.acceptsURL("jdbc:postgresql://localhost/zdiscord"));
    }

    private java.sql.Driver loadBundledDriver() throws ReflectiveOperationException {
        try {
            return (java.sql.Driver) Class.forName("com.mysql.cj.jdbc.Driver")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectionFailure) {
                throw reflectionFailure;
            }
            throw exception;
        }
    }
}
