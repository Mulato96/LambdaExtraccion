package co.org.ccb.lambda.handler.util;

public enum ConstantsEnum {

	CONTENT_TYPE("Content-Type"), APPLICATION_JSON("application/json");

	private final String value;

	ConstantsEnum(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
