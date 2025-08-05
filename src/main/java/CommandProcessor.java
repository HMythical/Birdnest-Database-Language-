import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {
    private UserDatabase userDatabase;
    private User currentUser;
    private Timer debugTimer;
    private Encryptor encryptor = new Encryptor();
    private Tree currentTree;

    // Add new command patterns
    private static final Pattern BRANCH_CREATE_PATTERN = Pattern.compile(
        "CREATE\\s+BRANCH\\s+'([^']+)'\\s+IN\\s+'([^']+)'",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BRANCH_MOVE_PATTERN = Pattern.compile(
        "MOVE\\s+BRANCH\\s+'([^']+)'\\s+TO\\s+'([^']+)'",
        Pattern.CASE_INSENSITIVE
    );

    public CommandProcessor(UserDatabase userDatabase) {
        this.userDatabase = userDatabase;
        this.debugTimer = new Timer(true); // Create daemon timer
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    // Add new method to set current tree context
    public void setCurrentTree(Tree tree) {
        this.currentTree = tree;
    }

    public String processCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Error: Empty command";
        }

        try {
            // Check for branch-related commands first
            Matcher branchCreateMatcher = BRANCH_CREATE_PATTERN.matcher(command);
            if (branchCreateMatcher.matches()) {
                return processCreateBranchCommand(
                    branchCreateMatcher.group(1),
                    branchCreateMatcher.group(2)
                );
            }

            Matcher branchMoveMatcher = BRANCH_MOVE_PATTERN.matcher(command);
            if (branchMoveMatcher.matches()) {
                return processMoveBranchCommand(
                    branchMoveMatcher.group(1),
                    branchMoveMatcher.group(2)
                );
            }

            // Process existing commands
            List<String> tokens = parseCommand(command);
            String[] tokenArray = tokens.toArray(new String[0]);
            validateTokens(tokenArray);

            switch (tokenArray[0].toUpperCase()) {
                case "HATCH":
                    return processHatchCommand(tokenArray);
                case "DROP":
                    return processDropCommand(tokenArray);
                case "GRANT":
                    return processGrantCommand(tokenArray);
                case "REVOKE":
                    return processRevokeCommand(tokenArray);
                case "CREATE":
                    if (tokenArray[1].equalsIgnoreCase("ROLE")) {
                        return processCreateRoleCommand(tokenArray);
                    }
                    if (tokenArray[1].equalsIgnoreCase("BRANCH")) {
                        return processCreateBranchCommand(tokenArray[2], tokenArray[4]);
                    }
                case "INIT":
                    if (tokenArray[1].equalsIgnoreCase("DEBUG")) {
                        return processDebugCommand(tokenArray);
                    }
                default:
                    return "Unknown command";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private List<String> parseCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '\'') {
                inQuotes = !inQuotes;
                currentToken.append(c);
            } else if (c == ' ' && !inQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private void validateTokens(String[] tokens) {
        String command = tokens[0].toUpperCase();

        switch (command) {
            case "HATCH":
                if (tokens.length != 6) {
                    throw new IllegalArgumentException("Invalid HATCH command format. Expected: HATCH NEW CHICK 'username'@'host' RECOGNITION 'auth_type'");
                }
                if (!tokens[1].equalsIgnoreCase("NEW") || !tokens[2].equalsIgnoreCase("CHICK")) {
                    throw new IllegalArgumentException("Invalid HATCH command syntax");
                }
                // Validate username@host format
                String userHost = tokens[3];
                if (!userHost.contains("@") || !userHost.startsWith("'") || !userHost.contains("'@'")) {
                    throw new IllegalArgumentException("Invalid username@host format. Expected: 'username'@'host'");
                }
                if (!tokens[4].equalsIgnoreCase("RECOGNITION")) {
                    throw new IllegalArgumentException("Invalid HATCH command syntax: Expected RECOGNITION keyword");
                }
                if (!tokens[5].startsWith("'") || !tokens[5].endsWith("'")) {
                    throw new IllegalArgumentException("Invalid auth_type format. Expected: 'auth_type'");
                }
                break;

            case "DROP":
                if (tokens.length < 3) {
                    throw new IllegalArgumentException("Invalid DROP command format. Expected: DROP CHICK 'username' ['reason']");
                }
                if (!tokens[1].equalsIgnoreCase("CHICK")) {
                    throw new IllegalArgumentException("Invalid DROP command syntax");
                }
                break;

            case "GRANT":
                if (tokens.length < 9) {
                    throw new IllegalArgumentException("Invalid GRANT command format. Expected: GRANT CHICK username PERMISSION permission_type TO nest DURATION duration");
                }
                if (!tokens[1].equalsIgnoreCase("CHICK") || !tokens[3].equalsIgnoreCase("PERMISSION") ||
                    !tokens[5].equalsIgnoreCase("TO") || !tokens[7].equalsIgnoreCase("DURATION")) {
                    throw new IllegalArgumentException("Invalid GRANT command syntax");
                }
                break;

            case "REVOKE":
                if (tokens.length < 7) {
                    throw new IllegalArgumentException("Invalid REVOKE command format. Expected: REVOKE PERMISSION permission_type FROM 'username' DURATION duration");
                }
                if (!tokens[1].equalsIgnoreCase("PERMISSION") || !tokens[3].equalsIgnoreCase("FROM") ||
                    !tokens[5].equalsIgnoreCase("DURATION")) {
                    throw new IllegalArgumentException("Invalid REVOKE command syntax");
                }
                break;

            case "CREATE":
                if (tokens.length < 4 || !tokens[1].equalsIgnoreCase("ROLE")) {
                    throw new IllegalArgumentException("Invalid CREATE ROLE command format. Expected: CREATE ROLE 'role_name' hierarchy_number");
                }
                break;

            case "INIT":
                if (tokens.length < 3 || !tokens[1].equalsIgnoreCase("DEBUG") || !tokens[2].equalsIgnoreCase("USER")) {
                    throw new IllegalArgumentException("Invalid INIT DEBUG command format. Expected: INIT DEBUG USER [timeLength]");
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private String processHatchCommand(String[] tokens) {
        // Format: HATCH NEW CHICK 'username'@'host' RECOGNITION 'auth_type'
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        String userHost = tokens[3];
        String username = userHost.substring(1, userHost.indexOf("'@'"));
        String host = userHost.substring(userHost.indexOf("'@'") + 2, userHost.length() - 1);
        String authType = tokens[5].substring(1, tokens[5].length() - 1).toLowerCase();

        // Validate the user doesn't already exist
        if (userDatabase.getUser(username) != null) {
            throw new IllegalArgumentException("User already exists: " + username);
        }

        String password = null;
        String authKey = null;
        String phoneNumber = null;

        switch (authType) {
            case "password":
                // Generate a temporary password that the user must change on first login
                password = encryptor.hashPassword(generateTempPassword());
                break;

            case "auth_key":
                // Generate an auth key for the user
                authKey = encryptor.generateAuthKey();
                break;

            case "2fa":
                // For 2FA, we need to:
                // 1. Generate a temporary password
                // 2. Get the user's phone number (this would typically be done through UI interaction)
                // 3. Send verification code
                password = encryptor.hashPassword(generateTempPassword());
                // Note: In a real implementation, you would need to prompt for the phone number
                // and handle the verification process through the UI
                phoneNumber = "+1234567890"; // This should come from user input
                String verificationCode = encryptor.initiate2FA(phoneNumber);
                // In a real implementation, you would wait for the user to enter the code
                break;

            default:
                throw new IllegalArgumentException("Invalid authentication type. Expected: password, auth_key, or 2fa");
        }

        User newUser = new User(
            username,
            password,
            host,
            new String[]{"BASE_USER"},
            false,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            currentUser.getUsername(),
            authType,
            phoneNumber,
            authKey
        );

        try {
            userDatabase.addUser(newUser);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create user: " + username + ". Error: " + e.getMessage());
        }

        StringBuilder response = new StringBuilder()
            .append("Successfully created new user: ").append(username)
            .append(" with host: ").append(host)
            .append("\nAuthentication type: ").append(authType);

        if (authKey != null) {
            response.append("\nGenerated auth key (save this, it will only be shown once): \n")
                   .append(authKey);
        }
        if (password != null) {
            response.append("\nTemporary password (must be changed on first login): ")
                   .append(generateTempPassword());
        }
        if (phoneNumber != null) {
            response.append("\nVerification code has been sent to: ").append(phoneNumber);
        }

        return response.toString();
    }

    private String processDropCommand(String[] tokens) {
        // Format: DROP CHICK 'username' ['reason']
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        String username = tokens[2].replace("'", "");
        if (!userDatabase.getUsers().containsKey(username)) {
            throw new IllegalArgumentException("User not found");
        }

        userDatabase.removeUser(username);
        return "Successfully dropped user: " + username;
    }

    private String processGrantCommand(String[] tokens) {
        // Format: GRANT CHICK username PERMISSION permission_type TO nest DURATION duration
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        String username = tokens[2];
        String permissionType = tokens[4];
        String targetNest = tokens[6];
        int duration = parseDuration(tokens[8]);

        User user = userDatabase.getUser(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        String[] currentPermissions = user.getPermissions();
        String[] newPermissions = Arrays.copyOf(currentPermissions, currentPermissions.length + 1);
        newPermissions[currentPermissions.length] = permissionType;

        userDatabase.modifyPermissions(username, newPermissions);
        return "Successfully granted " + permissionType + " to " + username;
    }

    private String processRevokeCommand(String[] tokens) {
        // Format: REVOKE PERMISSION permission_type FROM 'username' DURATION duration
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        String permissionType = tokens[2];
        String username = tokens[4].replace("'", "");
        int duration = parseDuration(tokens[6]);

        User user = userDatabase.getUser(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        String[] currentPermissions = user.getPermissions();
        String[] newPermissions = Arrays.stream(currentPermissions)
            .filter(p -> !p.equals(permissionType))
            .toArray(String[]::new);

        userDatabase.modifyPermissions(username, newPermissions);
        return "Successfully revoked " + permissionType + " from " + username;
    }

    private String processCreateRoleCommand(String[] tokens) {
        // Format: CREATE ROLE 'role_name' hierarchy_number
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        String roleName = tokens[2].replace("'", "");
        int hierarchyLevel = Integer.parseInt(tokens[3]);

        // Add role to database (you might want to extend UserDatabase to handle roles)
        return "Successfully created role: " + roleName + " with hierarchy level " + hierarchyLevel;
    }

    private String processDebugCommand(String[] tokens) {
        // Format: INIT DEBUG USER [timeLength]
        if (!hasAdminPermissions()) {
            throw new SecurityException("FAULTYPERMISSIONEXCEPTION: Admin privileges required");
        }

        int timeLength = tokens.length > 3 ? Integer.parseInt(tokens[3]) : 60; // Default 60 seconds
        debugTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Debug session ended for user: " + currentUser.getUsername());
            }
        }, timeLength * 1000L);

        return "Debug mode initialized for " + timeLength + " seconds";
    }

    private String processCreateBranchCommand(String branchName, String parentPath) {
        if (currentTree == null) {
            return "Error: No tree context set";
        }

        if (!hasAdminPermissions()) {
            return "Error: Admin permissions required to create branches";
        }

        try {
            Branch newBranch = currentTree.createBranch(parentPath, branchName, currentUser.getUsername());
            return String.format("Successfully created branch '%s' in '%s'", branchName, parentPath);
        } catch (Exception e) {
            return "Error creating branch: " + e.getMessage();
        }
    }

    private String processMoveBranchCommand(String branchPath, String newParentPath) {
        if (currentTree == null) {
            return "Error: No tree context set";
        }

        if (!hasAdminPermissions()) {
            return "Error: Admin permissions required to move branches";
        }

        try {
            Branch branch = currentTree.getBranchByPath(branchPath);
            if (branch == null) {
                return "Error: Branch not found: " + branchPath;
            }

            Branch newParent = currentTree.getBranchByPath(newParentPath);
            if (newParent == null) {
                return "Error: New parent branch not found: " + newParentPath;
            }

            // Remove from old parent
            Branch oldParent = branch.getParentBranch();
            oldParent.removeSubBranch(branch.getName());

            // Add to new parent
            branch.setParentBranch(newParent);
            newParent.addSubBranch(branch);

            // Update branch index
            currentTree.getBranchIndex().remove(branchPath);
            currentTree.getBranchIndex().put(branch.getFullPath(), branch);

            return String.format("Successfully moved branch '%s' to '%s'", branchPath, newParentPath);
        } catch (Exception e) {
            return "Error moving branch: " + e.getMessage();
        }
    }

    private boolean hasAdminPermissions() {
        if (currentUser == null) return false;
        return Arrays.asList(currentUser.getPermissions()).contains("ADMIN+");
    }

    private String generateTempPassword() {
        // Generate a random 12-character password
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return password.toString();
    }

    private int parseDuration(String duration) {
        try {
            return Integer.parseInt(duration);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format. Expected a number.");
        }
    }

    public void shutdown() {
        try {
            // Cancel any running debug timers
            if (debugTimer != null) {
                debugTimer.cancel();
                debugTimer.purge();
            }

            // Clear current user and tree context
            currentUser = null;
            currentTree = null;

            // Shutdown the user database to ensure proper cleanup of resources
            if (userDatabase != null) {
                userDatabase.shutdown();
            }
        } catch (Exception e) {
            // Log the error but don't rethrow since we're shutting down
            System.err.println("Error during CommandProcessor shutdown: " + e.getMessage());
        }
    }
}
