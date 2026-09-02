package com.tunindex.market_tool.collector.services.news;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A genuinely rule-based classifier, not a model: headlines are real French
 * BVMT financial press text (see IlBoursaNewsProvider), and every keyword
 * list below is a fixed, auditable set — the output for a given headline is
 * always the same and traceable back to exactly which words matched. No
 * ML, no external sentiment API, nothing probabilistic.
 */
@Component
public class NewsSentimentClassifier {

    public enum Sentiment {
        POSITIVE, NEGATIVE, NEUTRAL
    }

    public record Classification(Sentiment sentiment, List<String> matchedKeywords) {
    }

    // Stems, not whole words, so "renforce"/"renforcé"/"renforcement" all
    // match a single "renforc" entry — deliberately conservative: only
    // words with an unambiguous financial-press connotation are included.
    private static final String[] POSITIVE_STEMS = {
            "hausse", "croissance", "record", "succès", "renforc", "amélior", "consolide",
            "consolidation", "progress", "dividende except", "augmentation de capital",
            "bénéfice", "profit", "rentabilité", "leadership", "franchit la barre",
            "lance", "innov", "partenariat", "expansion", "développement", "distinction",
            "récompense", "prix décerné", "certifi", "excellent", "performant", "solide",
            "dynamis", "boom", "essor", "réussite", "gagnant",
    };

    private static final String[] NEGATIVE_STEMS = {
            "baisse", "recul", "perte", "chute", "difficult", "risque", "dégrad",
            "suspension", "litige", "amende", "sanction", "fraude", "scandale",
            "licenciement", "restructuration", "faillite", "défaut de paiement",
            "avertissement sur résultat", "révision à la baisse", "décès", "disparition",
            "grève", "contentieux", "poursuite judiciaire", "déficit", "endettement",
            "dévaluation", "ralentissement", "contraction", "fermeture",
    };

    public Classification classify(String headline) {
        if (headline == null || headline.isBlank()) {
            return new Classification(Sentiment.NEUTRAL, List.of());
        }
        String lower = headline.toLowerCase();

        Set<String> positiveHits = new LinkedHashSet<>();
        for (String stem : POSITIVE_STEMS) {
            if (lower.contains(stem)) positiveHits.add(stem);
        }
        Set<String> negativeHits = new LinkedHashSet<>();
        for (String stem : NEGATIVE_STEMS) {
            if (lower.contains(stem)) negativeHits.add(stem);
        }

        Sentiment sentiment;
        if (positiveHits.size() > negativeHits.size()) {
            sentiment = Sentiment.POSITIVE;
        } else if (negativeHits.size() > positiveHits.size()) {
            sentiment = Sentiment.NEGATIVE;
        } else {
            sentiment = Sentiment.NEUTRAL;
        }

        List<String> matched = new ArrayList<>(positiveHits);
        matched.addAll(negativeHits);
        return new Classification(sentiment, matched);
    }
}
