package co.org.ccb.lambda.handler.model.entity.sirep;

import static java.util.Objects.nonNull;

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
@Table(name = "RM_MATRICULAS_SOACHA", schema = "SIREP")
@Immutable
@Getter
@Setter
public class EnrollmentsEntity {

	@Id
	@Column(name = "NUM_MATRICULA", nullable = false)
	private String numMatricula;

	@Column(name = "FEC_CERT_ACTIVA")
	@Temporal(TemporalType.DATE)
	private Date fecCertActiva;

	@Column(name = "CTR_CERT_ACTIVA")
	private Integer ctrCertActiva;

	@Column(name = "NUM_RECIBO_ACTIVA")
	private String numReciboActiva;

	@Column(name = "TXT_ERROR_ACTIVA")
	private String txtErrorActiva;

	@Column(name = "CTR_LIBROS")
	private Integer ctrLibros;

	@Column(name = "FEC_CERT_LIBROS")
	@Temporal(TemporalType.DATE)
	private Date fecCertLibro;

	@Column(name = "CTR_CERT_LIBROS")
	private Integer ctrCertLibro;

	@Column(name = "NUM_RECIBO_LIBROS")
	private String numReciboLibro;

	@Column(name = "TXT_ERROR_LIBROS")
	private String txtErrorLibro;

	@Column(name = "FEC_CERT_CANCELA")
	@Temporal(TemporalType.DATE)
	private Date fecCertCancela;

	@Column(name = "CTR_CERT_CANCELA")
	private Integer ctrCertCancela;

	@Column(name = "NUM_RECIBO_CANCELA")
	private String numReciboCancela;

	@Column(name = "TXT_ERROR_CANCELA")
	private String txtErrorCancela;

	@Column(name = "CTR_JSON")
	private String ctrJson;

	@Column(name = "FEC_JSON")
	@Temporal(TemporalType.TIMESTAMP)
	private Date fecJson;

	@Column(name = "ID_ORGANIZACION")
	private String idOrganizacion;

	@Column(name = "ID_CATEGORIA")
	private String idCategoria;

	@Column(name = "ID_ESTADO_MAT")
	private String idEstadoMat;

	@Column(name = "FEC_MATRICULA")
	private String fecMatricula;

	@Column(name = "FEC_RENOVA")
	private String fecRenov;

	@Column(name = "ULT_ANO_RENOVA")
	private Integer ultAnoRenov;

	@Column(name = "CTR_ONBASE")
	private Integer ctrOnbase;

	public boolean tieneCertificadoLibro(String numReciboLibro) {
		return this.getCtrLibros() == 1 && this.getCtrCertLibro() == 1 && nonNull(
				this.getNumReciboLibro()) && this.getNumReciboLibro().equals(numReciboLibro);
	}
}
