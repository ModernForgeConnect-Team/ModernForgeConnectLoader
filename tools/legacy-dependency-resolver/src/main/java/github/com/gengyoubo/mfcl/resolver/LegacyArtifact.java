package github.com.gengyoubo.mfcl.resolver;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;
import javax.inject.Inject;

/** Gradle owns repository resolution. This object records one explicitly requested, hash-pinned JAR. */
public class LegacyArtifact {
    private final String id, requested, sha256, origin, license;
    private final ConfigurableFileCollection files;
    @Inject public LegacyArtifact(ObjectFactory objects, String id, String requested, String sha256, String origin, String license) {
        if (!id.matches("[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("Invalid legacy artifact id: " + id);
        if (!sha256.matches("(?i)[0-9a-f]{64}")) throw new IllegalArgumentException("Expected SHA-256 for " + id);
        if (origin.isBlank() || license.isBlank()) throw new IllegalArgumentException("Record origin and license/distribution notes for " + id);
        this.id = id; this.requested = requested; this.sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        this.origin = origin; this.license = license; this.files = objects.fileCollection();
    }
    @Input public String getId() { return id; }
    @Input public String getRequested() { return requested; }
    @Input public String getSha256() { return sha256; }
    @Input public String getOrigin() { return origin; }
    @Input public String getLicense() { return license; }
    @InputFiles @PathSensitive(PathSensitivity.NONE) public ConfigurableFileCollection getFiles() { return files; }
}
