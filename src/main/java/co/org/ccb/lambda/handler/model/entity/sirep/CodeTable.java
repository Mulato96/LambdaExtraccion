package co.org.ccb.lambda.handler.model.entity.sirep;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TA_CODIGOS", schema = "SIREP")
@Immutable
@Getter
@Setter
public class CodeTable {

	@Id
	@Column(name = "ID_CODIGO", nullable = false)
	private Long idCodigo;

	@Column(name = "NOM_CODIGO")
	private String nomCodigo;

	@Column(name = "CTR_VIGENTE")
	private Integer ctrVigente;

	@Column(name = "ID_TABLA")
	private Integer idTabla;

	@Column(name = "ID_CODIGO_CORTO")
	private String idCodigoCorto;

	@Column(name = "CLASE_JAVA")
	private String claseJava;
}
