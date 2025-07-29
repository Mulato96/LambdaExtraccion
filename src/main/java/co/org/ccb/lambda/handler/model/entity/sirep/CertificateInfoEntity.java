package co.org.ccb.lambda.handler.model.entity.sirep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Immutable
@AllArgsConstructor
@Table(name = "CC_CERTIFICADOS_CONTROL", schema = "SIREP")
public class CertificateInfoEntity {

  @Id
  @Column(name = "COD_VERIFICACION")
  private String codVerificacion;

  @Column(name = "NUM_RECIBO")
  private String numRecibo;

  @Transient
  private String numMatricula;
}
