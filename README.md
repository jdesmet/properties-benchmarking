# properties-benchmarking

## The Problem

The java class [`Properties`](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html) is a thread-safe Class as per it's documentation: 

> This class is thread-safe: multiple threads can share a single Properties object without the need for external synchronization.

Because of that reason, I have the habit of maintaining my Properties in a `HashMap` implementation of `Map`, which is not thread-safe, but much more lightweight. More specifically, implementing thread-safety requires additional locking mechanisms, which will have an impact on performance. I can easily avoid this by simply segregating my property initialization in a static class initializer, which will guarantee it's completion before I use any get calls in instance methods of the same class.

This is so far was just a narrative that leads me to the actual question. Eventually I need to revert to API's that can only accept 'Properties' as a Parameter, an example being [`DriverManager.getConnection(String,Properties)`](https://docs.oracle.com/javase/8/docs/api/java/sql/DriverManager.html#getConnection-java.lang.String-java.util.Properties-). I need to convert my `Map` into `Properties`.

So a first attempt would look like something like this:

```Java
Properties properties = new Properties();
this.propertyMap.forEach((k,v)->{properties.setProperty(k, v);});
connection = DriverManager.getConnection(url, properties);
```

Obvious problem, or maybe not so obvious as I avoided using an actual for-loop, is that I use a repeated call to `Properties.setProperty`. If `Properties` is truly thread safe, then that must mean that each call to `setProperty` is synchronized and has individual lock/unlock mechanisms added to it.

*Would it not be better in such a case that I manually lock the entire `Properties` instance first as in code below?*

```Java
Connection connection;
Properties properties = new Properties();
synchronized (properties) {
  // Properties is implemented as thread-safe. As it adds
  // additional thread locking, it's use is localized to just
  // here to avoid consequential performance issues. We will
  // do a last minute conversion from Map to Properties right here.
  this.propertyMap.forEach((k,v)->{properties.setProperty(k, v);});
  connection = DriverManager.getConnection(url, properties);
}
```

One issue I was possibly expecting is that both manual lock on properties, and the individual calls to `setProperties` might have caused a deadlock, but it seems to run fine as locks within the same thread are re-entrant.

# Conclusions

1. `Properties` *always* benefits from explicitly synchronizing.
2. `Map` *never* benefits from explicitly synchronizing.
3. Within all the `Properties` methods, it is the fastest to use the synchronized version of any of the iteration methods (`forEach`, `stream`, for-loop).
4. Clearly the winner is the non-synchronized version of `Map`. More specifically `for (...) m.put(k,v)` is the fastest.

However, I am left with one conundrum: synchronizing access to a Map, adds an unexplainable amount of overhead to it. Why is that?

## The Research

After many tests, and fixing several outside factors affecting those, I came to a good set of benchmarks. These are my results formatted with the help of [ascii-tables](https://ozh.github.io/ascii-tables/):

|               Name               | Unlocked | Locked |
|----------------------------------|---------:|-------:|
| putAll->Properties               | 7,882    | 4,781  |
| forEach->setProperty->Properties | 17,077   | 1,948  |
| stream->setProperty->Properties  | 11,895   | 1,441  |
| loop->setProperty->Properties    | 17,741   | 1,496  |
| forEach->put->Properties         | 12,095   | 1,743  |
| stream->put->Properties          | 12,213   | 1,795  |
| loop->put->Properties            | 12,180   | 1,586  |
| putAll->Map                      | 1,384    | 13,706 |
| forEach->put->Map                | 879      | 14,645 |
| stream->put->Map                 | 914      | 15,290 |
| loop->put->Map                   | 799      | 1,577  |

These are all results from copying either a `Map` into `Properties`, or a `Map` into `Map`. The conclusions to be made are:

The methods for testing, is in the same order as in the table above, with `p` being a `Properties` and `m` a `Map` as a target:

```Java
p.putAll(SOURCEMAP);
SOURCEMAP.forEach(p::setProperty);
SOURCEMAP.entrySet().stream().forEach((e)->{p.setProperty(e.getKey(), e.getValue());});
for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) p.setProperty(e.getKey(), e.getValue());
SOURCEMAP.forEach(p::put);
SOURCEMAP.entrySet().stream().forEach((e)->{p.put(e.getKey(), e.getValue());});
for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) p.put(e.getKey(), e.getValue());
m.putAll(SOURCEMAP);
SOURCEMAP.forEach(m::put);
SOURCEMAP.entrySet().stream().forEach((e)->{m.put(e.getKey(), e.getValue());});
for (Map.Entry<String,String> e:SOURCEMAP.entrySet()) m.put(e.getKey(), e.getValue());
```

See also the Stack Overflow Posting at http://stackoverflow.com/q/35140487/744133
