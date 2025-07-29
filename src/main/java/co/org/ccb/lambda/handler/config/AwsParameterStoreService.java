package co.org.ccb.lambda.handler.config;

import static co.org.ccb.lambda.handler.util.AppPropertiesLoader.get;

import co.org.ccb.lambda.handler.util.AppPropertiesLoader;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

public class AwsParameterStoreService {

	private String region;

	private String accessKey;

	private String secretKey;

	private SsmClient ssmClient;

	private String authMethod;

	public AwsParameterStoreService() {

		this.region = get("aws.region");
		this.accessKey = get("aws.access.key");
		this.secretKey = get("aws.secret.key");
		this.authMethod = get("aws.ssm.auth.method.ps");

		AppPropertiesLoader.validateProperty("aws.region", region);
		AppPropertiesLoader.validateProperty("aws.access.key", accessKey);
		AppPropertiesLoader.validateProperty("aws.secret.key", secretKey);
		AppPropertiesLoader.validateProperty("aws.ssm.auth.method.ps", authMethod);

		boolean useIamRole = "0".equals(authMethod);

		System.out.println("Usando credenciales " + (useIamRole ? "de IAM Role" : "estáticas") + " Parameter Store");

		this.ssmClient = SsmClient.builder().region(Region.of(region))
				.credentialsProvider(useIamRole ? DefaultCredentialsProvider.create()
						: StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.build();
	}

	/**
	 * Obtiene el valor real desde SSM o variables de entorno, dependiendo del
	 * formato del parámetro.
	 * 
	 * Si el valor comienza con "${", se trata como variable de entorno. Si no, se
	 * asume que es el path en SSM (ej. /dev/traslado-masivo/url-db...).
	 */
	public String getParameter(String parameterReference) {

		if (parameterReference != null && parameterReference.startsWith("${") && parameterReference.endsWith("}")) {
			// Extraer el nombre de la variable de entorno
			String envVarName = parameterReference.substring(2, parameterReference.length() - 1);
			String envValue = System.getenv(envVarName);

			if (envValue == null) {
				throw new RuntimeException("Variable de entorno no encontrada: " + envVarName);
			}

			GetParameterRequest request = GetParameterRequest.builder().name(envValue).withDecryption(true).build();

			GetParameterResponse response = ssmClient.getParameter(request);
			return response.parameter().value();

		} else {
			// Llamar directamente a AWS Parameter Store
			GetParameterRequest request = GetParameterRequest.builder().name(parameterReference).withDecryption(true)
					.build();

			GetParameterResponse response = ssmClient.getParameter(request);
			return response.parameter().value();
		}
	}

}
