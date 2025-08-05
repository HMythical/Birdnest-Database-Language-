import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

public class Egg implements Serializable {
    @Getter @Setter private String name;
    @Getter @Setter private Object value;
    @Getter @Setter private String dataType;
    @Getter @Setter private String[] constraints;
    @Getter @Setter private boolean isEncrypted;
    @Getter @Setter private String creatorId;
    @Getter @Setter private String lastModified;

    public Egg(String name, Object value, String dataType, String[] constraints) {
        this.name = name;
        this.value = value;
        this.dataType = dataType;
        this.constraints = constraints;
        this.isEncrypted = false;
        this.lastModified = java.time.LocalDateTime.now().toString();
    }

    public Egg(String name, Object value) {
        this(name, value, inferDataType(value), new String[0]);
    }

    private static String inferDataType(Object value) {
        if (value instanceof Integer) return "INTEGER";
        if (value instanceof String) return "STRINGLIT";
        if (value instanceof java.util.Date || value instanceof java.time.LocalDateTime) return "MIGRATIONDATE";
        if (value instanceof Boolean) return "FLIGHTMODE";
        return "STRINGLIT"; // default type
    }

    public boolean validateConstraints() {
        if (constraints == null || constraints.length == 0) return true;

        for (String constraint : constraints) {
            switch (constraint.toUpperCase()) {
                case "NOT NULL":
                case "!NULL":
                    if (value == null) return false;
                    break;
                case "UNIQUE":
                case "SOLITARY":
                    // Uniqueness should be checked at the Nest level
                    break;
                case "ROOSTKEY":
                    // Primary key constraint should be checked at the Nest level
                    break;
            }
        }
        return true;
    }

    public boolean matches(String pattern) {
        if (value == null) return false;
        String stringValue = value.toString().toLowerCase();
        return stringValue.contains(pattern.toLowerCase());
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%s)", name, value, dataType);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Egg)) return false;
        Egg other = (Egg) obj;
        return name.equals(other.name) &&
               (value == null ? other.value == null : value.equals(other.value));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, value);
    }
}
