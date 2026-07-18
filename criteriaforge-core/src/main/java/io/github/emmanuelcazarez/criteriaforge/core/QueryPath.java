package io.github.emmanuelcazarez.criteriaforge.core;

final class QueryPath {

    private QueryPath() {
    }

    static String requireValid(String path, String label) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        var normalized = path.trim();
        for (String segment : normalized.split("\\.", -1)) {
            if (!isJavaIdentifier(segment)) {
                throw new IllegalArgumentException(label + " contains an invalid path: " + path);
            }
        }
        return normalized;
    }

    private static boolean isJavaIdentifier(String segment) {
        if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
            return false;
        }
        for (int index = 1; index < segment.length(); index++) {
            if (!Character.isJavaIdentifierPart(segment.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
