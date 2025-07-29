package co.org.ccb.lambda.handler.model.entity.traslado;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "control_procesos", schema = "control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessControlEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_ctr_proceso", nullable = false)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "id_proceso", nullable = false)
	private ProcessEntity process;

	@Column(name = "num_matricula", length = 8, nullable = false)
	private String enrollmentNumber;

	@Column(name = "id_estado_proceso", nullable = false)
	private Integer processStatusId;

	@Column(name = "tipo_registro")
	private Short recordType;

	@Column(name = "fec_creacion", nullable = false, updatable = false)
	private LocalDateTime creationDate;

	@Column(name = "id_usuario_crea", length = 15, nullable = false)
	private String createdBy;

	@Column(name = "fec_modificacion")
	private LocalDateTime modificationDate;

	@Column(name = "id_usuario_modifica", length = 15)
	private String modifiedBy;
}
