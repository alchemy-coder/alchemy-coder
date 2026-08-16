package athena.coder.ai.tool.dependency;

import java.util.HashMap;
import java.util.Map;

public class DependencyParser {

    public Dependency parse(String dependency, String explicitVersion, String scope) {
        if (dependency == null || dependency.isBlank()) {
            return new Dependency(null, "", "", "");
        }

        dependency = dependency.trim();
        Map<String, String> parsed = parseIdentifier(dependency, explicitVersion);

        return new Dependency(
                parsed.getOrDefault("groupId", null),
                parsed.getOrDefault("artifactId", dependency),
                parsed.getOrDefault("version", ""),
                (scope != null && !scope.isBlank()) ? scope : "compile"
        );
    }

    private Map<String, String> parseIdentifier(String dependency, String explicitVersion) {
        Map<String, String> result = new HashMap<>();

        if (dependency.contains(":")) {
            String[] parts = dependency.split(":");
            if (parts.length >= 2) {
                result.put("groupId", parts[0].trim());
                result.put("artifactId", parts[1].trim());
                if (parts.length >= 3) result.put("version", parts[2].trim());
            }
        } else if (dependency.contains("@")) {
            int atIndex = dependency.indexOf('@');
            result.put("artifactId", dependency.substring(0, atIndex).trim());
            result.put("version", dependency.substring(atIndex + 1).trim());
        } else if (dependency.contains("==")) {
            int eqIndex = dependency.indexOf("==");
            result.put("artifactId", dependency.substring(0, eqIndex).trim());
            result.put("version", dependency.substring(eqIndex + 2).trim());
        } else {
            result.put("artifactId", dependency);
        }

        if (explicitVersion != null && !explicitVersion.isBlank()) {
            result.put("version", explicitVersion.trim());
        }

        return result;
    }
}