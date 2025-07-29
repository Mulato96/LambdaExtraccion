package co.org.ccb.lambda.handler.config;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;

import jakarta.persistence.EntityManagerFactory;

public class PersistenceManager {

	private static final String DB2_DRIVER = "com.ibm.db2.jcc.DB2Driver";
	private static final String POSTGRES_DRIVER = "org.postgresql.Driver";

	private static EntityManagerFactory sirepEntityManagerFactory;
	private static EntityManagerFactory trasladoEntityManagerFactory;

	public static void init(AwsParameterStoreService aws) {
		String urlDbSirep = get("aws.ssm.db.sirep.url");
		String usernameDbSirep = get("aws.ssm.db.sirep.username");
		String passwordDbSirep = get("aws.ssm.db.sirep.password");

		String urlDbTraslado = get("aws.ssm.db.traslado.url");
		String usernameDbTraslado = get("aws.ssm.db.traslado.username");
		String passwordDbTraslado = get("aws.ssm.db.traslado.password");

		sirepEntityManagerFactory = buildEntityManagerFactory("co.org.ccb.lambda.handler.model.entity.sirep",
				aws.getParameter(urlDbSirep), aws.getParameter(usernameDbSirep), aws.getParameter(passwordDbSirep),
				DB2_DRIVER, "org.hibernate.dialect.DB2Dialect");

		trasladoEntityManagerFactory = buildEntityManagerFactory("co.org.ccb.lambda.handler.model.entity.traslado",
				aws.getParameter(urlDbTraslado),
				aws.getParameter(usernameDbTraslado),
				aws.getParameter(passwordDbTraslado), POSTGRES_DRIVER,
				"org.hibernate.dialect.PostgreSQLDialect");
	}

	private static EntityManagerFactory buildEntityManagerFactory(String modelPackage, String url, String username,
			String password, String driverClassName, String hibernateDialect) {

		System.out.println("Valor de parameter store url" + url);
		System.out.println("Valor de parameter store user" + username);

		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
				.applySetting("hibernate.connection.driver_class", driverClassName)
				.applySetting("hibernate.connection.url", url).applySetting("hibernate.connection.username", username)
				.applySetting("hibernate.connection.password", password)
				.applySetting("hibernate.dialect", hibernateDialect).applySetting("hibernate.hbm2ddl.auto", "none")
				.applySetting("hibernate.show_sql", "false").applySetting("hibernate.format_sql", "false").build();

		MetadataSources sources = new MetadataSources(registry);

		if (modelPackage.contains("sirep")) {
			// Entidades Sirep
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.sirep.CodeTable.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.sirep.EnrollmentsEntity.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.sirep.OnbaseControlEntity.class);
		} else {
			// Entidades Traslado
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.traslado.BaseEntity.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.traslado.ParameterEntity.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.traslado.ProcessControlEntity.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.traslado.ProcessDocumentEntity.class);
			sources.addAnnotatedClass(co.org.ccb.lambda.handler.model.entity.traslado.ProcessEntity.class);
		}

		Metadata metadata = sources.getMetadataBuilder().build();

		return metadata.getSessionFactoryBuilder().build();

	}

	public static EntityManagerFactory getSirepEntityManagerFactory() {
		return sirepEntityManagerFactory;
	}

	public static EntityManagerFactory getTrasladoEntityManagerFactory() {
		return trasladoEntityManagerFactory;
	}

	public static void shutdown() {
		if (sirepEntityManagerFactory != null)
			sirepEntityManagerFactory.close();
		if (trasladoEntityManagerFactory != null)
			trasladoEntityManagerFactory.close();
	}
}
