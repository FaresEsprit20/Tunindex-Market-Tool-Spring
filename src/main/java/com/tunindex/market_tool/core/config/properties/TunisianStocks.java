package com.tunindex.market_tool.core.config.properties;

import com.tunindex.market_tool.domain.entities.enums.OwnershipType;

import java.util.HashMap;
import java.util.Map;

public class TunisianStocks {

    public static final Map<String, StockInfo> STOCKS = new HashMap<>();

    static {
        // Government-owned
        STOCKS.put("STB", new StockInfo("STB", "S.T.B", "/equities/societe-tunisienne-de-banque", OwnershipType.GOVERNMENT));
        STOCKS.put("BH", new StockInfo("BH", "BH Bank", "/equities/banque-de-lhabitat", OwnershipType.GOVERNMENT));
        STOCKS.put("BNA", new StockInfo("BNA", "BNA", "/equities/banque-nationale-agricole", OwnershipType.GOVERNMENT));

        // Mixed ownership
        STOCKS.put("BIAT", new StockInfo("BIAT", "BIAT", "/equities/banque-inter.-arabe-de-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("UIB", new StockInfo("UIB", "UIB", "/equities/union-internationale-de-banque", OwnershipType.PRIVATE));
        STOCKS.put("ATB", new StockInfo("ATB", "ATB", "/equities/arab-tunisian-bank", OwnershipType.PRIVATE));
        STOCKS.put("UBCI", new StockInfo("UBCI", "Union Bancaire pour le Commerce et l'Industrie", "/equities/u.b.c.i", OwnershipType.PRIVATE));

        // Private sector (default)
        STOCKS.put("AB", new StockInfo("AB", "AMEN BANK", "/equities/amen-bank", OwnershipType.PRIVATE));
        STOCKS.put("AL", new StockInfo("AL", "AIR LIQUIDE Tun", "/equities/air-liquide-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("ARTES", new StockInfo("ARTES", "Automobile Reseau Tunisien Et Service", "/equities/artes-renault", OwnershipType.PRIVATE));
        STOCKS.put("AST", new StockInfo("AST", "ASTREE SA", "/equities/com.-dassur.et-de-reassur.", OwnershipType.PRIVATE));
        STOCKS.put("ATL", new StockInfo("ATL", "ATL", "/equities/arab-tunisian-lease", OwnershipType.PRIVATE));
        STOCKS.put("BS", new StockInfo("BS", "ATTIJARI BANK", "/equities/banque-attijari-de-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("BT", new StockInfo("BT", "BT", "/equities/banque-de-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("BTEI", new StockInfo("BTEI", "BTEI", "/equities/bq-de-tunisie-et-des-emirats", OwnershipType.PRIVATE));
        STOCKS.put("CC", new StockInfo("CC", "Carthage Cement", "/equities/carthage-cement", OwnershipType.PRIVATE));
        STOCKS.put("CIL", new StockInfo("CIL", "CIL", "/equities/compagnie-int.-de-leasing", OwnershipType.PRIVATE));
        STOCKS.put("ICF", new StockInfo("ICF", "ICF", "/equities/soc.-des-ind.-chimiu.-du-fluor", OwnershipType.PRIVATE));
        STOCKS.put("MGR", new StockInfo("MGR", "Societe Tunisienne des Marches de Gros", "/equities/sotumag", OwnershipType.PRIVATE));
        STOCKS.put("BHL", new StockInfo("BHL", "BH Leasing", "/equities/modern-leasing", OwnershipType.PRIVATE));
        STOCKS.put("MNP", new StockInfo("MNP", "Societe Nouvelle Maison de la Ville de Tunis", "/equities/monoprix", OwnershipType.PRIVATE));
        STOCKS.put("NAKL", new StockInfo("NAKL", "Ennakl Automobiles", "/equities/ennakl-automobiles", OwnershipType.PRIVATE));
        STOCKS.put("PLTU", new StockInfo("PLTU", "PLACEMENT DE TUNISIE", "/equities/placements-de-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("POULA", new StockInfo("POULA", "POULINA GROUP HLD", "/equities/poulina-group-holding", OwnershipType.PRIVATE));
        STOCKS.put("SCB", new StockInfo("SCB", "Les Ciments de Bizerte", "/equities/ciments-de-bizerte", OwnershipType.PRIVATE));
        STOCKS.put("SFBT", new StockInfo("SFBT", "SFBT", "/equities/sfbt", OwnershipType.PRIVATE));
        STOCKS.put("SIAM", new StockInfo("SIAM", "STE Ind d'appareillage Et De Materiels Elec", "/equities/siame", OwnershipType.PRIVATE));
        STOCKS.put("SIMP", new StockInfo("SIMP", "SIMPAR", "/equities/soc.-immob.-et-de-part.", OwnershipType.PRIVATE));
        STOCKS.put("SITS", new StockInfo("SITS", "SITS", "/equities/soc.-immob.-tuniso-seoud.", OwnershipType.PRIVATE));
        STOCKS.put("SMG", new StockInfo("SMG", "MAGASIN GENERAL", "/equities/magazin-gneral", OwnershipType.PRIVATE));
        STOCKS.put("SOKNA", new StockInfo("SOKNA", "ESSOUKNA", "/equities/societe-essoukna", OwnershipType.PRIVATE));
        STOCKS.put("SOMOC", new StockInfo("SOMOC", "SOMOCER", "/equities/societe-moderne-de-ceramique", OwnershipType.PRIVATE));
        STOCKS.put("SOTE", new StockInfo("SOTE", "STE Tunisienne d'entreprises De Telecommunications", "/equities/sotetel", OwnershipType.PRIVATE));
        STOCKS.put("SPDI", new StockInfo("SPDI", "SPDIT-SICAF", "/equities/spdit", OwnershipType.PRIVATE));
        STOCKS.put("STAR", new StockInfo("STAR", "STAR", "/equities/star", OwnershipType.PRIVATE));
        STOCKS.put("STIP", new StockInfo("STIP", "Societe Tunisienne des Industries de Pneumatiques", "/equities/soc.-tun.-des-ind.-de-pneumatiques", OwnershipType.PRIVATE));
        STOCKS.put("STPIL", new StockInfo("STPIL", "SOTRAPIL", "/equities/sotrapil", OwnershipType.PRIVATE));
        STOCKS.put("TINV", new StockInfo("TINV", "TUN INVEST - SICAR", "/equities/tuninvest", OwnershipType.PRIVATE));
        STOCKS.put("TJL", new StockInfo("TJL", "ATTIJARI LEASING", "/equities/attijari-leasing", OwnershipType.PRIVATE));
        STOCKS.put("TLNET", new StockInfo("TLNET", "TELNET", "/equities/telnet-holding", OwnershipType.PRIVATE));
        STOCKS.put("TLS", new StockInfo("TLS", "TUNISIE LEASING", "/equities/tunisie-leasing", OwnershipType.PRIVATE));
        STOCKS.put("TPR", new StockInfo("TPR", "TPR", "/equities/soc.-tun.-profiles-aluminium", OwnershipType.PRIVATE));
        STOCKS.put("TRE", new StockInfo("TRE", "Tunis Re", "/equities/soc.-tun.-de-reassurance", OwnershipType.PRIVATE));
        STOCKS.put("WIFAK", new StockInfo("WIFAK", "EL WIFACK LEASING", "/equities/el-wifack-leasing", OwnershipType.PRIVATE));
        STOCKS.put("STVR", new StockInfo("STVR", "Societe Tunisienne De Verreries", "/equities/soc-tunisienne-de-verreries", OwnershipType.PRIVATE));
        STOCKS.put("BHASS", new StockInfo("BHASS", "BH Assurance", "/equities/salim", OwnershipType.PRIVATE));
        STOCKS.put("LNDOR", new StockInfo("LNDOR", "Land Or", "/equities/land-or", OwnershipType.PRIVATE));
        STOCKS.put("NBL", new StockInfo("NBL", "New Body Li", "/equities/new-body-li", OwnershipType.PRIVATE));
        STOCKS.put("OTH", new StockInfo("OTH", "One Tech Ho", "/equities/one-tech-ho", OwnershipType.PRIVATE));
        STOCKS.put("STPAP", new StockInfo("STPAP", "Societe Tunisienne Industrielle Du Papier Et Du Ca", "/equities/sotipapier", OwnershipType.PRIVATE));
        STOCKS.put("SOTEM", new StockInfo("SOTEM", "Sotemail", "/equities/sotemail", OwnershipType.PRIVATE));
        STOCKS.put("SAH", new StockInfo("SAH", "Sah", "/equities/sah", OwnershipType.PRIVATE));
        STOCKS.put("HANL", new StockInfo("HANL", "Hannibal Lease", "/equities/hannibal-lease", OwnershipType.PRIVATE));
        STOCKS.put("CITY", new StockInfo("CITY", "City Cars", "/equities/city-cars", OwnershipType.PRIVATE));
        STOCKS.put("ECYCL", new StockInfo("ECYCL", "Euro-Cycles", "/equities/euro-cycles", OwnershipType.PRIVATE));
        STOCKS.put("MPBS", new StockInfo("MPBS", "Manufacture de Panneaux Bois du Sud", "/equities/mpbs", OwnershipType.PRIVATE));
        STOCKS.put("BL", new StockInfo("BL", "Best Lease", "/equities/best-lease", OwnershipType.PRIVATE));
        STOCKS.put("DH", new StockInfo("DH", "Societe Delice Holding", "/equities/societe-delice-holding", OwnershipType.PRIVATE));
        STOCKS.put("PLAST", new StockInfo("PLAST", "OfficePlast", "/equities/officeplast", OwnershipType.PRIVATE));
        STOCKS.put("UMED", new StockInfo("UMED", "Unite de Fabrication de Medicaments", "/equities/unimed-sa", OwnershipType.PRIVATE));
        STOCKS.put("SAMAA", new StockInfo("SAMAA", "Atelier Meuble Interieurs", "/equities/atelier-meuble-interieurs", OwnershipType.PRIVATE));
        STOCKS.put("ASSMA", new StockInfo("ASSMA", "Ste Assurances Magrebia", "/equities/ste-assurances-magrebia", OwnershipType.PRIVATE));
        STOCKS.put("SMART", new StockInfo("SMART", "Smart Tunisie", "/equities/smart-tunisie", OwnershipType.PRIVATE));
        STOCKS.put("STAS", new StockInfo("STAS", "Societe Tunisienne D Automobiles", "/equities/societe-tunisienne-d-automobiles", OwnershipType.PRIVATE));
        STOCKS.put("AMV", new StockInfo("AMV", "Assurances Maghrebia Vie", "/equities/assurances-maghrebia-vie", OwnershipType.PRIVATE));
    }

    public static StockInfo getStock(String symbol) {
        return STOCKS.get(symbol);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StockInfo {
        private String symbol;
        private String name;
        private String url;
        private OwnershipType ownershipType;

        public StockInfo(String symbol, String name, String url) {
            this(symbol, name, url, OwnershipType.PRIVATE);
        }
    }
}