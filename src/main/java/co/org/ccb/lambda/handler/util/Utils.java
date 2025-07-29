package co.org.ccb.lambda.handler.util;

import java.lang.reflect.Field;

public class Utils {

	public static void setPrivateField(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException("Error setting field " + fieldName, e);
		}
	}
}
