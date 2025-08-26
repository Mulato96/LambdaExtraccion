package co.org.ccb.lambda.handler.model.entity.traslado;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "procesos", schema = "control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_proceso", nullable = false)
	private Integer id;

	@Column(name = "cnt_matriculas", nullable = false)
	private Integer enrollmentCount;

	@Column(name = "fec_creacion", nullable = false, updatable = false)
	private LocalDateTime creationDate;

	@Column(name = "id_usuario_crea", length = 15, nullable = false)
	private String createdBy;

	@Column(name = "fec_modificacion")
	private LocalDateTime modificationDate;

	@Column(name = "id_usuario_modifica", length = 15)
	private String modifiedBy;
}
