import lombok.Getter;
import lombok.Setter;
public class User {

    @Getter @Setter
    protected String username;
    protected String password;
    protected String host;
    protected String[] permissions;
    protected boolean lock;
    protected String creationDate;
    protected String creator_id;


    public User(String username, String password, String host, String[] permissions, boolean lock, String creationDate, String creator_id) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.permissions = permissions;
        this.lock = lock;
        this.creationDate = creationDate;
        this.creator_id = creator_id;
    }

}