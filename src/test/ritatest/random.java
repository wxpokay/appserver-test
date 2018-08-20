package app.test;
import java.util.Random;

/**
 * Created by ritawu on 17/3/30.
 */

public class random {
    public static void main(String[] args) {
        Random rand = new Random();

        final int A = 'A', z = 'Z';

        for (int i = 0; i < 80; i++) {
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 28) {
                int number = rand.nextInt(z + 1);
                if (number >= A) {
                    sb.append((char) number);
                }
            }

            System.out.println(sb.toString());
        }
    }
}

