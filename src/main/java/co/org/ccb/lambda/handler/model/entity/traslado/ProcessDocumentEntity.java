package co.org.ccb.lambda.handler.model.entity.traslado;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "proceso_documentos", schema = "control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessDocumentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_ctr_documento", nullable = false)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "id_ctr_proceso", nullable = false)
	private ProcessControlEntity processControl;

	@Column(name = "num_matricula", length = 8, nullable = false)
	private String enrollmentNumber;

	@Column(name = "num_unico_doc", nullable = false)
	private String uniqueDocumentNumber;

	@Column(name = "id_estado", length = 4, nullable = false)
	private String statusId;

	@Column(name = "formato")
	private String format;

	@Column(name = "tipo_doc", length = 200, nullable = false)
	private String documentType;

	@Column(name = "fec_gestion")
	private LocalDateTime managementDate;

	@Column(name = "ruta_documento")
	private String documentPath;

	@Column(name = "ruta_destino")
	private String destinationPath;

	@Column(name = "nombre_final", length = 200)
	private String finalName;

	@Column(name = "id_libro", length = 2)
	private String bookId;

	@Column(name = "num_registro", length = 8)
	private String recordNumber;

	@Column(name = "fec_cargues3")
	private LocalDateTime uploadDate;

	@Column(name = "fec_conversion")
	private LocalDateTime conversionDate;

	@Column(name = "ctr_conversion")
	private Short conversionControl;

	@Column(name = "fec_creacion", nullable = false, updatable = false)
	private LocalDateTime creationDate;

	@Column(name = "id_usuario_crea", length = 15, nullable = false)
	private String createdBy;

	@Column(name = "fec_modificacion")
	private LocalDateTime modificationDate;

	@Column(name = "id_usuario_modifica", length = 15)
	private String modifiedBy;
}
