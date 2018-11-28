Implementation
--------------
In my implementation, we consider the following

1- For shared resources, since we have read and write from different threads we have to thread-safe implementations of List, Map and the numbers.

    private final Map<GasType, Double> prices = new ConcurrentHashMap<>();
    private final AtomicInteger sales = new AtomicInteger(0);
    ...

2- We don't lock the GasPump itself, since we don't want to block other resources to monitor the properties (like type and amount) and loose parallelism, instead, we use a [Lock] for each GasPump to keep track of the synchronization easily (we could also use a wrapper/holder class).

    private final Map<GasPump, Lock> pumps = new ConcurrentHashMap<>();
    ...
    this.pumps.put(pump, new ReentrantLock());

3- The customers keep trying to catch the lock in a race. We don't care about the order, and we don't have a queue for them, the early bird catches the worm!

        if (e.getValue().tryLock()) {
            try {
                if (e.getKey().getRemainingAmount() >= amountInLiters) {
                    ...
                }
            } finally {
                e.getValue().unlock();
            }
        } else {
            ...
        }
4- Testing multithreading could be quite tricky. We could use some extra libraries alongside JUnit (or switch to TestNG which has better threading features) but since the case is simple and we want to have full access to the procedure, we use Java executor framework and normal JUnit. I also tried to randomize the test as much as possible.

Tips
-----

From the design perspective and principles, making each customer able to access the resources of the GasStation through different threads is not a very good practice. Because thread creation is very expensive, holds a lot of memory and it's not scalable (one thread per request). Also, it makes the design way more complicated because of synchronization challenges.

Instead, the GasStation has to take care of parallelism using a queue and a thread pool to maximize the efficiency. Depends on the scale, the number of requests, processing time, and the extend systems we can also use other approaches and patterns.

My proposed system is:
1. We keep the track of the list of gas stations and requests on the main thread, and make the decision about the next candidate, events, keep billing and reports all synchronous. In any case, we need to give read access to any other threads, we can use violate modifier which is more efficient than making them atomic using blocking.
1. We run all the buying gas procedures on a thread pool with corresponding order.

[Lock]: https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/ReentrantLock.html