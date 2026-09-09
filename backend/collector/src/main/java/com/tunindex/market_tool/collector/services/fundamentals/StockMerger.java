package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries forward values a fresh scrape did not manage to collect.
 *
 * <p>Saving a stock replaces its row: the newly scraped entity is given the
 * existing row's id and written over it. That is correct when the scrape is
 * complete, and destructive when it is not - and it is often not. A symbol's
 * data is assembled from several pages, and the supplementary ones are allowed
 * to fail so that one missing page does not discard the whole company. The
 * cost of that leniency is that a transient failure on the statistics page
 * produces a stock whose price-to-book is null, which then overwrites a
 * perfectly good stored value.
 *
 * <p>That is exactly what happened on a run where fifteen symbols failed
 * outright: price-to-book went from complete to nineteen blanks, not because
 * the figure had changed but because a page timed out. Data the app had
 * yesterday should not disappear because a website was slow today.
 *
 * <p>So a null in the incoming record means "this scrape did not observe it",
 * never "this value is now absent". Only nulls are filled; every figure the
 * new scrape did collect wins, including one that legitimately changed.
 *
 * <p>Done reflectively over the embedded blocks because those carry upwards of
 * forty fields between them and an explicit field-by-field merge would fall
 * silently out of date the first time someone adds one - which is the same
 * class of bug this exists to prevent.
 */
@Service
@Slf4j
public class StockMerger {

    /**
     * Fills nulls in {@code incoming} from {@code existing}, in place.
     *
     * @return the names of the fields carried forward, for logging
     */
    public List<String> carryForward(Stock incoming, Stock existing) {
        List<String> carried = new ArrayList<>();
        if (incoming == null || existing == null) {
            return carried;
        }

        mergeBlock(incoming.getPriceData(), existing.getPriceData(), "price", carried);
        mergeBlock(incoming.getVolumeData(), existing.getVolumeData(), "volume", carried);
        mergeBlock(incoming.getFundamentalData(), existing.getFundamentalData(), "fundamental", carried);
        mergeBlock(incoming.getRatiosData(), existing.getRatiosData(), "ratios", carried);
        mergeBlock(incoming.getTechnicalData(), existing.getTechnicalData(), "technical", carried);
        mergeBlock(incoming.getAnalystData(), existing.getAnalystData(), "analyst", carried);
        mergeBlock(incoming.getCalculatedValues(), existing.getCalculatedValues(), "calculated", carried);

        return carried;
    }

    /**
     * Copies every readable property that is null on the target and set on the
     * source.
     */
    private void mergeBlock(Object target, Object source, String blockName, List<String> carried) {
        if (target == null || source == null || !target.getClass().equals(source.getClass())) {
            // A block the new scrape did not build at all is handled by the
            // caller, which keeps the existing one wholesale.
            return;
        }

        for (Method getter : target.getClass().getMethods()) {
            if (!isGetter(getter)) {
                continue;
            }
            String property = propertyName(getter);
            Method setter = findSetter(target.getClass(), property, getter.getReturnType());
            if (setter == null) {
                continue;
            }

            try {
                if (getter.invoke(target) != null) {
                    // The scrape observed a value - it wins, even if it changed.
                    continue;
                }
                Object previous = getter.invoke(source);
                if (previous != null) {
                    setter.invoke(target, previous);
                    carried.add(blockName + "." + property);
                }
            } catch (Exception e) {
                log.debug("Could not carry forward {}.{}: {}", blockName, property, e.getMessage());
            }
        }
    }

    private boolean isGetter(Method method) {
        return method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && !method.getDeclaringClass().equals(Object.class)
                && (method.getName().startsWith("get") || method.getName().startsWith("is"))
                && !method.getName().equals("getClass");
    }

    private String propertyName(Method getter) {
        String name = getter.getName();
        return name.startsWith("is") ? name.substring(2) : name.substring(3);
    }

    private Method findSetter(Class<?> type, String property, Class<?> valueType) {
        try {
            return type.getMethod("set" + property, valueType);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
