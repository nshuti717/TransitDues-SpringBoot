package rw.ac.auca.transitdues.registration;

import java.util.UUID;

/**
 * View-model for one <option> on the self-registration stage dropdown: the
 * label already carries the "x of y places used" / "(full)" text so the
 * template has no counting logic of its own.
 */
public record StageOption(UUID id, String label, boolean full) {
}
