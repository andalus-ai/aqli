import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
public class TestArena {
    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            arena.allocateUtf8String("hello");
            System.out.println("allocateUtf8String exists");
        } catch (Throwable t) {}
        try (Arena arena = Arena.ofConfined()) {
            arena.allocateFrom("hello");
            System.out.println("allocateFrom exists");
        } catch (Throwable t) {}
    }
}
