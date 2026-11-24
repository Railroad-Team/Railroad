import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Echo {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("echo: " + reader.readLine());
        System.err.println("to stderr");
    }
}
