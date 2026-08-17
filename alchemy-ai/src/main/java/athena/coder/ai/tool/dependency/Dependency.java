package athena.coder.ai.tool.dependency;

import org.jspecify.annotations.NonNull;

public class Dependency {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope;

    public Dependency(String groupId, String artifactId, String version, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getScope() {
        return scope;
    }

    public String getCoordinates() {
        boolean hasVersion = version != null && !version.isBlank();
        if (groupId != null && !groupId.isBlank()) {
            return String.format("%s:%s%s",
                    groupId,
                    artifactId,
                    hasVersion ? ":" + version : "");
        }
        return hasVersion ?
                String.format("%s@%s", artifactId, version) :
                artifactId;
    }

    @Override
    public @NonNull String toString() {
        return getCoordinates();
    }
}