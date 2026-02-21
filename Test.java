import java.lang.reflect.Method;
public class Test {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("org.shredzone.acme4j.Order", false, ClassLoader.getSystemClassLoader());
        for (Method m : clazz.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
