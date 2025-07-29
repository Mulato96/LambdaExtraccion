package co.org.ccb.lambda.handler.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppPropertiesLoader {

	private static final Properties properties = new Properties();

	static {
		try (InputStream input = AppPropertiesLoader.class.getClassLoader()
				.getResourceAsStream("application.properties")) {
			if (input == null) {
				throw new RuntimeException("No se encontró el archivo application.properties");
			}
			properties.load(input);
		} catch (IOException e) {
			throw new RuntimeException("Error cargando el archivo de configuración", e);
		}
	}

	public static String get(String key) {
		return properties.getProperty(key);
	}

	public static void validateProperty(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"La propiedad requerida '" + name + "' no está definida en application.properties");
		}
	}
}
