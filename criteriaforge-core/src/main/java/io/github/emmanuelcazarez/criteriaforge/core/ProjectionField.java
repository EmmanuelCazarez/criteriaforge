package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/**
 * Selects one public query field and places its value at an output path.
 *
 * <p>The source is resolved through the entity query policy. The output path
 * controls only the shape of the projected response.</p>
 */
public record ProjectionField(String source, String output) {

    public ProjectionField {
        source = QueryPath.requireValid(source, "projection source");
        output = QueryPath.requireValid(output, "projection output");
    }

    public static ProjectionField of(String source) {
        return new ProjectionField(source, source);
    }

    public static ProjectionField aliased(String source, String output) {
        return new ProjectionField(source, output);
    }

    public boolean isAliased() {
        return !Objects.equals(source, output);
    }
}
