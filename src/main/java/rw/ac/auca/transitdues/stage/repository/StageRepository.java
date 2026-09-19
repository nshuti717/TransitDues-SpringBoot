package rw.ac.auca.transitdues.stage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;

import java.util.UUID;

public interface StageRepository extends JpaRepository<Stage, UUID> {
}
