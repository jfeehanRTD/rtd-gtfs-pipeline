import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestPostgresConnection {
    public static void main(String[] args) {
        // Get credentials from environment variables
        String user = System.getenv("POSTGRES_TIES_USER");
        String password = System.getenv("POSTGRES_TIES_PASSWORD");

        // Test different database names
        String[][] tests = {
            {"jdbc:postgresql://localhost:5433/postgres", user, password},
            {"jdbc:postgresql://localhost:5433/ties", user, password},
            {"jdbc:postgresql://127.0.0.1:5433/postgres", user, password},
            {"jdbc:postgresql://127.0.0.1:5433/ties", user, password}
        };

        for (String[] test : tests) {
            String url = test[0];
            String testUser = test[1];
            String testPassword = test[2];

            System.out.println("\n=== Testing: " + url + " (user: " + testUser + ") ===");
            try {
                Class.forName("org.postgresql.Driver");
                Connection conn = DriverManager.getConnection(url, testUser, testPassword);
                System.out.println("✓ Connected successfully!");

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT version()");
                if (rs.next()) {
                    System.out.println("✓ Version: " + rs.getString(1).substring(0, 60) + "...");
                }

                rs = stmt.executeQuery("SELECT current_user, current_database()");
                if (rs.next()) {
                    System.out.println("✓ Current user: " + rs.getString(1) + ", database: " + rs.getString(2));
                }

                conn.close();
                System.out.println("✓ Connection closed - SUCCESS!");
                System.out.println("\n*** THIS CONNECTION WORKS! Use this configuration. ***\n");
                break; // Success, stop testing

            } catch (Exception e) {
                System.out.println("✗ Failed: " + e.getMessage());
            }
        }
    }
}
