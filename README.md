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

Conclusion is now that the methods are really pretty much irrelevant. I can still see that `HashMap` is overall a bit better performing than `Properties` but not enough to make me choose performance over readability.

As per the suggestion of @Holger, I would propose to just use the `Properties.putAll(other)` method. It is fast, and performs about as well as the others.

However, if you do use this in a multithreaded environment, I would suggest using `HashMap`, and carefully consider concurrent access. Usually that is easily accomplishable without the need of overly locking/synchronize.

I think that was a good lesson learned.

Below are a bit more normalized measurements. I cheated a little by eyeballing and picking a sample without outliers instead of trying to average/median over multiple samples. Just for avoiding the complexity (and additional work) of doing so, or having to acquire an external tool. Results are formatted with the help of [ascii-tables](https://ozh.github.io/ascii-tables/):

|               Name               | Unlocked | Locked |
|----------------------------------|---------:|-------:|
| putAll->Properties               | 1,349    | 1,552  |
| forEach->setProperty->Properties | 2,080    | 1,743  |
| stream->setProperty->Properties  | 1,659    | 1,539  |
| loop->setProperty->Properties    | 1,351    | 1,358  |
| forEach->put->Properties         | 1,372    | 1,650  |
| stream->put->Properties          | 1,300    | 1,646  |
| loop->put->Properties            | 1,739    | 1,415  |
| putAll->Map                      | 803      | 849    |
| forEach->put->Map                | 1,001    | 1,010  |
| stream->put->Map                 | 1,106    | 1,021  |
| loop->put->Map                   | 969      | 1,014  |


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

See the source code for more detailed information.
