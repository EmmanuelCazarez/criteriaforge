package io.github.emmanuelcazarez.criteriaforge.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DocumentationContractTest {

    private static final List<String> REQUIRED_DOCUMENTS = List.of(
        "README.md",
        "docs/query-language.md",
        "docs/security.md",
        "docs/architecture.md",
        "docs/branching.md",
        "CONTRIBUTING.md",
        "CHANGELOG.md",
        "SECURITY.md");

    private static final Pattern MARKDOWN_LINK =
        Pattern.compile("!?\\[[^]]*]\\(([^)]+)\\)");

    @Test
    void providesEveryPublicDocumentAndResolvableRelativeLink() throws IOException {
        var root = reactorRoot();
        var missing = REQUIRED_DOCUMENTS.stream()
            .filter(document -> Files.notExists(root.resolve(document)))
            .toList();
        assertThat(missing).as("missing public documentation").isEmpty();

        for (var document : REQUIRED_DOCUMENTS) {
            var source = root.resolve(document);
            var matcher = MARKDOWN_LINK.matcher(Files.readString(source));
            while (matcher.find()) {
                var target = matcher.group(1).split("#", 2)[0];
                if (target.isBlank()
                        || target.startsWith("http://")
                        || target.startsWith("https://")
                        || target.startsWith("mailto:")) {
                    continue;
                }
                assertThat(source.getParent().resolve(target).normalize())
                    .as("relative link %s in %s", target, document)
                    .exists();
            }
        }
    }

    @Test
    void publishesCopyableCoordinatesWithoutPrivateMaterial() throws IOException {
        var root = reactorRoot();
        var readme = Files.readString(root.resolve("README.md"));

        assertThat(readme)
            .contains("io.github.emmanuelcazarez")
            .contains("criteriaforge-spring-boot-starter")
            .contains("0.1.0");

        for (var document : REQUIRED_DOCUMENTS) {
            assertThat(Files.readString(root.resolve(document)))
                .as("publishable content in %s", document)
                .doesNotContainIgnoringCase("coppel")
                .doesNotContain("com.coppel")
                .doesNotContain("-----BEGIN PRIVATE KEY-----")
                .doesNotContain("/Users/");
        }
    }

    private static Path reactorRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("criteriaforge-core"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CriteriaForge reactor root was not found");
    }
}
