import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.MemorySegment;
public class TestArena {
    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ms1 = arena.allocateFrom("hello");
            int[] arr = {1,2,3};
            MemorySegment ms2 = arena.allocateFrom(ValueLayout.JAVA_INT, arr);
            String str = ms1.getString(0);
            System.out.println("Success: " + str);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
