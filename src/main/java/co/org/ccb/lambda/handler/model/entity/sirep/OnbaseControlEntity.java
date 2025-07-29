package co.org.ccb.lambda.handler.model.entity.sirep;

import java.util.Date;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "RM_CONTROL_SOACHA_ONBASE", schema = "SIREP")
@Immutable
@Getter
@Setter
public class OnbaseControlEntity {

	@Column(name = "NUM_REGISTRO", nullable = false)
	private String numRegistro;

	@Column(name = "NUM_MATRICULA")
	private String numMatricula;

	@Column(name = "NUM_TRAMITE")
	private String numTramite;

	@Column(name = "CTR_DOCUMENTO")
	private Integer ctrDocumento;

	@Column(name = "ANO_DATOS")
	private String anoDatos;

	@Column(name = "ID_LIBRO")
	private String idLibro;

	@Id
	@Column(name = "HANDLE")
	private Integer handle;

	@Column(name = "CTR_PROCESADO")
	private String ctrProcesado;

	@Column(name = "FEC_PROCESADO")
	@Temporal(TemporalType.TIMESTAMP)
	private Date fecProcesado;

	@Column(name = "TXT_ERROR")
	private String txtError;

	@Column(name = "FEC_RENOVA")
	@Temporal(TemporalType.TIMESTAMP)
	private Date fecRenova;

	@Column(name = "NOMBRE_ARCHIVO")
	private String nombreArchivo;
}
