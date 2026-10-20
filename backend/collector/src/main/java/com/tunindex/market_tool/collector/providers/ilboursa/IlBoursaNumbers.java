package com.tunindex.market_tool.collector.providers.ilboursa;

import java.math.BigDecimal;

/**
 * Parses the numeric conventions ilboursa uses.
 *
 * <p>Shared deliberately, because the site is not internally consistent and
 * assuming one convention while reading a page that uses the other produces
 * numbers that are wrong by a factor of a hundred rather than failing loudly:
 *
 * <ul>
 *   <li>{@code /marches/societe} - French style: "0,75" and "547 525"
 *   <li>{@code /marches/aaz} - dot decimals with spaced thousands: "218.26"
 *       and "9 422"
 * </ul>
 *
 * <p>Thousands are always separated by a space (ordinary, non-breaking, or
 * narrow no-break), never by a dot or comma. That is what makes both styles
 * safe to read with one rule: strip the spaces, then whichever of "," or "."
 * remains is the decimal separator. A dot is therefore never a thousands
 * separator here, so "1.234" is one-point-two-three-four.
 *
 * <p>"-", "n/a" and blanks mean the figure was not reported. They return null
 * rather than zero: a company that did not publish a profit margin has not
 * published a margin of zero, and the difference decides whether the scorer
 * drops the component or scores it as terrible.
 */
public final class IlBoursaNumbers {

    private IlBoursaNumbers() {
    }

    /** Returns the value, or null when the source did not report one. */
    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }

        String text = normaliseSpaces(raw)
                .replace("%", "")
                .replace("TND", "")
                .replace("DT", "")
                .replace(" ", "")
                .trim();

        if (text.isEmpty()
                || text.equals("-")
                || text.equals("--")
                || text.equalsIgnoreCase("n/a")
                || text.equalsIgnoreCase("ns")) {
            return null;
        }

        // With spaces gone, a comma can only be the decimal mark.
        text = text.replace(",", ".");

        // Anything that is not a plain signed decimal is a label, a footnote
        // marker or markup that leaked through - skipped rather than guessed.
        if (!text.matches("[+-]?\\d+(\\.\\d+)?")) {
            return null;
        }

        try {
            return new BigDecimal(text.startsWith("+") ? text.substring(1) : text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** As {@link #parse}, for counts the site prints with spaced thousands. */
    public static Long parseLong(String raw) {
        BigDecimal value = parse(raw);
        return value == null ? null : value.longValue();
    }

    /** Collapses the several space characters the site mixes into one. */
    public static String normaliseSpaces(String text) {
        return text == null ? "" : text
                .replace(' ', ' ')
                .replace(' ', ' ')
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
