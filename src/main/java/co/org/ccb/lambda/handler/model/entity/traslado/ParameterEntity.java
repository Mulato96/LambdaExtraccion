package co.org.ccb.lambda.handler.model.entity.traslado;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Table(name = "ta_parametro", schema = "control")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString
@AttributeOverride(name = "id", column = @Column(name = "id_parametro"))
public class ParameterEntity extends BaseEntity {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Column(name = "tipo", nullable = false, updatable = false)
	private String type;

	@Column(name = "valor", length = 400, nullable = false)
	private String value;

	@Column(name = "nom_parametro", nullable = false, updatable = false)
	private String name;

	@Column(name = "des_parametro", nullable = false, updatable = false)
	private String description;
}
