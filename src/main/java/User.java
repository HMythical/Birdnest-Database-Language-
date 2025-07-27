public class User {

    protected String username;
    protected String password;
    protected String role;
    protected String[] permissions;


    public User(String username, String password, String role, String[] permissions) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.permissions = permissions;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String[] getPermissions() {
        return permissions;
    }

    public void setPermissions(String[] permissions) {
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("User{");
        sb.append("username='").append(username).append('\'');
        sb.append(", role='").append(role).append('\'');
        sb.append(", permissions=[");
        for (int i = 0; i < permissions.length; i++) {
            sb.append(permissions[i]);
            if (i < permissions.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

}

