package rw.ac.auca.transitdues.operator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;
import rw.ac.auca.transitdues.stage.domain.Stage;

@Entity
@Table(name = "operator", indexes = {
        @Index(name = "idx_operator_stage_id", columnList = "stage_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Operator extends BaseEntity {

    @Column(nullable = false)
    @NotBlank
    private String fullName;

    @Column(nullable = false)
    @Pattern(regexp = "^(07[0-9]{8})$", message = "Phone number must be a valid Rwandan phone number (e.g. 07XXXXXXXX)")
    private String phoneNumber;

    @Column(nullable = false, unique = true)
    @NotBlank
    private String plateNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;
}
