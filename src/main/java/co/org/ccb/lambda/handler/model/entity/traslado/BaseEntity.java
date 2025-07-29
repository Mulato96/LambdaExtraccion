package co.org.ccb.lambda.handler.model.entity.traslado;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

@Data
@MappedSuperclass
public class BaseEntity implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(name = "id_usuario_crea", updatable = false)
	private String userCreator;

	@CreationTimestamp
	@Column(name = "fec_creacion", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "id_usuario_modifica", updatable = false)
	private String userModification;

	@UpdateTimestamp
	@Column(name = "fec_modificacion")
	private LocalDateTime updatedAt;
}
