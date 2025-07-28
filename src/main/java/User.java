import lombok.Getter;
import lombok.Setter;

public class User {
    @Getter @Setter
    protected String username;

    @Getter @Setter
    protected String password;

    @Getter @Setter
    protected String host;

    @Getter @Setter
    protected String[] permissions;

    @Getter @Setter
    protected boolean lock;

    @Getter @Setter
    protected String creationDate;

    @Getter @Setter
    protected String creator_id;

    @Getter @Setter
    protected String authType;

    @Getter @Setter
    protected String phoneNumber;  // For 2FA

    @Getter @Setter
    protected String authKey;      // For auth_key authentication

    public User(String username, String password, String host, String[] permissions,
                boolean lock, String creationDate, String creator_id,
                String authType, String phoneNumber, String authKey) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.permissions = permissions;
        this.lock = lock;
        this.creationDate = creationDate;
        this.creator_id = creator_id;
        this.authType = authType;
        this.phoneNumber = phoneNumber;
        this.authKey = authKey;
    }

    // Constructor for backward compatibility
    public User(String username, String password, String host, String[] permissions,
                boolean lock, String creationDate, String creator_id) {
        this(username, password, host, permissions, lock, creationDate,
             creator_id, "password", null, null);
    }
}