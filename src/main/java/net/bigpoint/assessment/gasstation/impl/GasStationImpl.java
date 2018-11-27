package net.bigpoint.assessment.gasstation.impl;

import com.netflix.spectator.impl.AtomicDouble;
import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Simple implementation for <code>GasStation</code>
 */
public final class GasStationImpl implements GasStation {

    private final Map<GasType, Double> prices = new HashMap<>();

    /**
     * Since we have no access to <code>GasPump</code> class, we have a couple of choices to define a lock:
     * 1) Use the object itself, which is not very vice decision since it blocks the other threads to access getters
     * 2) Use a wrapper class to bring the synchronization in, or in this case we can simple use a map to keep the
     *    related lock.
     *
     * We go with solution 2 since it makes more throughput available
     */
    private final Map<GasPump, Lock> pumps = new ConcurrentHashMap<>();

    /**
     * Since we have read and write from different threads, we can not use normal and volatile field
     * But we can use the Atomic implementation
     */
    private final AtomicInteger sales = new AtomicInteger(0);

    /**
     * Since we have read and write from different threads, we can not use normal and volatile field
     * But we can use the Atomic implementation
     */
    private final AtomicInteger noGas = new AtomicInteger(0);

    /**
     * Since we have read and write from different threads, we can not use normal and volatile field
     * But we can use the Atomic implementation
     */
    private final AtomicInteger tooExpensive = new AtomicInteger(0);

    /**
     * Since we have read and write from different threads, we can not use normal and volatile field
     * But we can use the Atomic implementation
     */
    private AtomicDouble revenue = new AtomicDouble(0);

    @Override
    public void addGasPump(final GasPump pump) {
        if (pump == null) {
            throw new IllegalArgumentException("pump can not be null");
        }
        this.pumps.put(pump, new ReentrantLock());
    }

    @Override
    public Collection<GasPump> getGasPumps() {
        return List.copyOf(this.pumps.keySet());
    }

    @Override
    public double buyGas(
            final GasType type,
            final double amountInLiters,
            final double maxPricePerLiter) throws NotEnoughGasException, GasTooExpensiveException
    {
        // Check the price
        final double pricePerLiter = this.prices.get(type);

        if (pricePerLiter > maxPricePerLiter) {
            tooExpensive.addAndGet(1);
            throw new GasTooExpensiveException();
        }

        do {
            // Get list of pumps that can provide gas
            final List<Map.Entry<GasPump, Lock>> list = this.pumps.entrySet().stream()
                    .filter(p -> p.getKey().getGasType() == type)
                    .collect(Collectors.toList());

            // No pump found!
            if (list.size() == 0) {
                tooExpensive.incrementAndGet();
                throw new IllegalStateException("Gas station does not provide such a fuel right now");
            }

            // There are two possible way the customer fails to use the pump
            // 1) They're all busy
            // 2) None of them has enough gas
            boolean areBusy = false;
            CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
                // Your code here executes after 5 seconds!
            });

            for (Map.Entry<GasPump, Lock> e : list) {
                // The current thread tries to obtain the lock, if it fails it means, it is
                // locked by other thread (customer) already, we monitor it on a loop to catch
                // the first gas pump which is free
                if (e.getValue().tryLock()) {
                    try {
                        // We are in a safe block, nothing else can access the gas pump, we can safely
                        // check the amount and start the pumping
                        if (e.getKey().getRemainingAmount() >= amountInLiters) {
                            e.getKey().pumpGas(amountInLiters);
                            sales.incrementAndGet();
                            final double price = amountInLiters * maxPricePerLiter;
                            revenue.addAndGet(price);
                            return price;
                        }
                    } finally {
                        e.getValue().unlock();
                    }
                } else {
                    // OK, there are some pump that they're busy, we need to wait and see
                    // if they have enough gas for the next round
                    areBusy = true;
                }
            }

            // We check all the pumps, All the pumps fails to provide gas, time for exception!
            if (!areBusy) {
                this.noGas.incrementAndGet();
                throw new NotEnoughGasException();
            }

            // Take a breath and ready for the next round!
            // We may can use notify and await method, but since the consumers (customers) and
            // producers (Gas pump) are not single, the procedure would be not be very clear and
            // does not achieve much
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // We either get the result or exception!
        } while (true);
    }

    @Override
    public double getRevenue() {
        return this.revenue.doubleValue();
    }

    @Override
    public int getNumberOfSales() {
        return this.sales.get();
    }

    @Override
    public int getNumberOfCancellationsNoGas() {
        return this.noGas.get();
    }

    @Override
    public int getNumberOfCancellationsTooExpensive() {
        return this.tooExpensive.get();
    }

    @Override
    public double getPrice(final GasType type) {
        if (type == null) {
            throw new IllegalArgumentException("type can not be null");
        }
        return this.prices.get(type);
    }

    @Override
    public void setPrice(final GasType type, final double price) {
        if (type == null) {
            throw new IllegalArgumentException("type can not be null");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price has to be greater than 0");
        }
        this.prices.put(type, price);
    }

}
