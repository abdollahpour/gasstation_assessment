package net.bigpoint.assessment.gasstation.impl;

import com.netflix.spectator.impl.AtomicDouble;
import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

/**
 * Testing <code>GasStation</code>
 * @see GasStationImpl
 */
public class GasStationImplTest {

    private static final Logger LOGGER = Logger.getLogger("GasStationImplTest");

    /**
     * Change the default logger a little bit to show the thread ID also, and simplify it
     */
    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);
        final ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(final LogRecord record) {
                return String.format("[%s] %ts: %s%n", record.getThreadID(), record.getMillis(), record.getMessage());
            }
        });
        LOGGER.addHandler(handler);
    }

    /**
     * Testing set gas price. For some implementation of <code>Map</code> it MAY cause
     * <code>ConcurrentModificationException</code> or goes into infinite loop and timeout happens
     * @throws InterruptedException
     */
    @Test
    public void testPrice() throws InterruptedException {
        final GasStation gasStation = new GasStationImpl();

        final ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 0; i < 1000; i ++) {
            final int price = i;
            executor.execute(() -> gasStation.setPrice(GasType.SUPER, price + 1));
            executor.execute(() -> gasStation.setPrice(GasType.DIESEL, price + 2));
            executor.execute(() -> gasStation.setPrice(GasType.REGULAR, price + 3));
        }

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout!");
        }
    }

    @Test(expected = GasTooExpensiveException.class)
    public void testTooExpensiveException() throws NotEnoughGasException, GasTooExpensiveException {
        final GasStation gasStation = new GasStationImpl();

        gasStation.addGasPump(new GasPump(GasType.SUPER, 200));
        gasStation.setPrice(GasType.SUPER, 1000);

        gasStation.buyGas(GasType.SUPER, 100, 500);
    }

    @Test(expected = NotEnoughGasException.class)
    public void testNotEnoughGasException() throws NotEnoughGasException, GasTooExpensiveException {
        final GasStation gasStation = new GasStationImpl();

        gasStation.addGasPump(new GasPump(GasType.SUPER, 100));
        gasStation.setPrice(GasType.SUPER, 10);

        gasStation.buyGas(GasType.SUPER, 200, 10);
    }

    @Test
    public void testGasPumps() {
        final GasStation gasStation = new GasStationImpl();

        gasStation.addGasPump(new GasPump(GasType.SUPER, 1000));
        gasStation.addGasPump(new GasPump(GasType.REGULAR, 1000));
        gasStation.addGasPump(new GasPump(GasType.REGULAR, 2000));
        gasStation.addGasPump(new GasPump(GasType.REGULAR, 3000));
        gasStation.addGasPump(new GasPump(GasType.DIESEL, 1000));
        gasStation.addGasPump(new GasPump(GasType.DIESEL, 2000));

        assertEquals(1, gasStation.getGasPumps().stream()
                .filter(p -> p.getGasType() == GasType.SUPER).count());
        assertEquals(3, gasStation.getGasPumps().stream()
                .filter(p -> p.getGasType() == GasType.REGULAR).count());
        assertEquals(2, gasStation.getGasPumps().stream()
                .filter(p -> p.getGasType() == GasType.DIESEL).count());
    }

    @Test
    public void testFull() throws InterruptedException {
        long m = System.currentTimeMillis();
        final Random random = new Random();

        //final GasStation gasStation = new Station();//
        final GasStation gasStation = new GasStationImpl();

        // Random gas prices 5 <= x < 10
        for (GasType type : GasType.values()) {
            // do what you want
            final int price = random.nextInt(5) + 5;

            gasStation.setPrice(type, price);

            LOGGER.info(format("%s price is %s", type, price));
        }

        // Add x number of gas pump for each type 2 =< x < 5
        final Map<GasType, Double> totalGas = new HashMap<>();
        for (GasType type:GasType.values()) {
            final int count = random.nextInt(3) + 2;

            double total = 0;
            for (int i = 0; i < count; i++) {
                final int amount = random.nextInt(200) + 100;
                gasStation.addGasPump(new GasPump(type, amount));
                total += amount;
            }
            totalGas.put(type, total);

            LOGGER.info(format("%s pumps for %s with total amount of %s liters", count, type, total));
        }

        final int customers = random.nextInt(40)  + 10;
        LOGGER.info(customers + " customers want to use the gas station at same time");

        final ExecutorService executor = Executors.newFixedThreadPool(customers);

        // We keep the records of the sales and compare it with the final results
        final Map<GasType, AtomicDouble> sales = new ConcurrentHashMap<>();
        Stream.of(GasType.values()).forEach(t -> sales.put(t, new AtomicDouble()));

        for (int i = 0; i < customers; i++) {
            executor.execute(() -> {
                try {
                    // Choose a random gas type
                    final GasType type = GasType.values()[random.nextInt(GasType.values().length)];
                    final int amount = random.nextInt(100) + 10;

                    LOGGER.info(format("Try to buy %s", type));
                    gasStation.buyGas(type, amount, 10);
                    LOGGER.info("Refueling finished");

                    sales.get(type).addAndGet(amount);
                } catch (NotEnoughGasException e) {
                    LOGGER.info("Not enough gas");
                } catch (GasTooExpensiveException e) {
                    LOGGER.info("Gas too expensive");
                }
            });
        }

        executor.shutdown();
        if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Timeout!");
        }

        // We have to be sure all the fuel are sold or remain in the pump
        for (GasType type:GasType.values()) {
            assertEquals(totalGas.get(type), sales.get(type).get() + gasStation.getGasPumps().stream()
                                    .filter(p -> p.getGasType() == type)
                                    .mapToDouble(GasPump::getRemainingAmount).sum(), 0);
        }

        LOGGER.info(gasStation.getNumberOfSales() + " sales");
        LOGGER.info(gasStation.getNumberOfCancellationsNoGas() + " no gas");
        LOGGER.info(gasStation.getNumberOfCancellationsTooExpensive() + " too expensive");
        LOGGER.info("Revenue is: " + gasStation.getRevenue());

        LOGGER.info("The whole operation took: " + (System.currentTimeMillis() - m));
    }

}
