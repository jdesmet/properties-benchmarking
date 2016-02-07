import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
 
public class PropertiesTest {
 
  final private Map<String,String> SOURCEMAP = new HashMap<>();
 
  static class Test<T extends Map<String,String>> {
    final public String name;
    final public Supplier<T> initializer;
    final public Consumer<T> test;
   
    public Test(String name,Supplier<T> initializer,Consumer<T> test) {
      this.name = name;
      this.initializer = initializer;
      this.test = test;
    }
   
    public final static NumberFormat NUMBER = new DecimalFormat("###,###,###");

    static public void printHeaders() {
      System.out.println("Name\tUnlocked\tLocked");
    }
    
    public void test() {
      System.out
        .append(name)
        .append('\t')
        .append(NUMBER.format(measure(this::testUnlocked)))
        .append('\t')
        .append(NUMBER.format(measure(this::testLocked)))
        .println();
    }
    
    static public long measure(Runnable measuredTest) {
      final long startTime = System.currentTimeMillis();
      measuredTest.run();
      final long endTime = System.currentTimeMillis();
      return endTime-startTime;
    }
    
    private void testUnlocked() {
      test.accept(initializer.get());
    }
   
    private void testLocked() {
      final T p = initializer.get();
      synchronized (p) {
        test.accept(p);
      }
    }
  }
 
  final private Set<Test> TESTS = new LinkedHashSet<>();
 
  public PropertiesTest(int size) {
    IntStream.range(0, size).forEach((i)->{SOURCEMAP.put(Integer.toString(i), Integer.toString(i));});
   
    add("putAll->Properties", Properties::new, (p)-> {
      p.putAll(SOURCEMAP);
    });
 
    add("forEach->setProperty->Properties", Properties::new, (p)-> {
      SOURCEMAP.forEach(p::setProperty);
    });
 
    add("stream->setProperty->Properties", Properties::new, (p)-> {
      SOURCEMAP.entrySet().stream().forEach((e)->{p.setProperty(e.getKey(), e.getValue());});
    });
 
    add("loop->setProperty->Properties", Properties::new, (p)-> {
      for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) p.setProperty(e.getKey(), e.getValue());
    });
 
    add("forEach->put->Properties", Properties::new, (p)-> {
      SOURCEMAP.forEach(p::put);
    });
 
    add("stream->put->Properties", Properties::new, (p)-> {
      SOURCEMAP.entrySet().stream().forEach((e)->{p.put(e.getKey(), e.getValue());});
    });
 
    add("loop->put->Properties", Properties::new, (p)-> {
      for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) p.put(e.getKey(), e.getValue());
    });
 
    add("putAll->Map", HashMap::new, (m)-> {
      m.putAll(SOURCEMAP);
    });
 
    add("forEach->put->Map", HashMap::new, (m)-> {
      SOURCEMAP.forEach(m::put);
    });
 
    add("stream->put->Map", HashMap<String,String>::new, (m)-> {
      SOURCEMAP.entrySet().stream().forEach((e)->{m.put(e.getKey(), e.getValue());});
    });
 
    add("loop->put->Map", HashMap<String,String>::new, (m)-> {
      for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) m.put(e.getKey(), e.getValue());
    });
  }
 
  final <T> void add(String name,Supplier<T> initializer,Consumer<T> test) {
    TESTS.add(new Test(name,initializer,test));
  }
 
  public void test() {
    Test.printHeaders();
    TESTS.forEach(Test::test);
  }
 
  public static void main(String[] args) {
    PropertiesTest p;
   
    // warmup
    // You can reformat the printed tables suitable for SO Postings using
    // following web tool: https://ozh.github.io/ascii-tables/
    System.out.println("Warming up (ignore) ...");
    p = new PropertiesTest(100);
    p.test();
    // actual
    p = new PropertiesTest(10000000);
    for (int i = 0; i < 100; i++) {
      System.out.println("Sampling test "+i);
      p.test();
    }
  }
}